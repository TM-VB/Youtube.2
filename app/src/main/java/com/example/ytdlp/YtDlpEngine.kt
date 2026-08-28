package com.example.ytdlp

import android.content.Context
import android.net.Uri
import com.example.domain.model.CutMode
import com.example.domain.model.TimeRange
import com.example.domain.model.VideoMetadata
import com.example.ffmpeg.FFmpegManager
import com.example.storage.MediaStoreHelper
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLException
import com.yausername.youtubedl_android.YoutubeDLRequest
import java.io.File
import java.util.regex.Pattern

data class ProgressUpdate(
    val progress: Float,
    val etaSeconds: Long,
    val speedText: String,
    val rawLine: String
)

object YtDlpEngine {

    private var isInitialized = false
    private val SPEED_PATTERN = Pattern.compile("""(\d+(?:\.\d+)?\s*(?:[kKMGT]?i?[bB])/s)""")
    private val SIZE_PATTERN = Pattern.compile("""of\s+(?:~)?\s*(\d+(?:\.\d+)?\s*(?:[kKMGT]?i?[bB]))""")

    fun init(context: Context): Result<Unit> {
        if (isInitialized) return Result.success(Unit)
        return try {
            YoutubeDL.getInstance().init(context.applicationContext)
            FFmpegManager.init(context.applicationContext)
            isInitialized = true
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun isReady(): Boolean = isInitialized

    /**
     * Extracts video metadata and available formats.
     */
    fun fetchVideoInfo(url: String): Result<VideoMetadata> {
        val trimmedUrl = url.trim()
        if (!trimmedUrl.startsWith("http://") && !trimmedUrl.startsWith("https://")) {
            return Result.failure(IllegalArgumentException("Video is invalid or unavailable."))
        }

        return try {
            val request = YoutubeDLRequest(trimmedUrl).apply {
                addOption("--no-playlist")
                addOption("--no-warnings")
                addOption("--socket-timeout", "20")
            }

            val info = YoutubeDL.getInstance().getInfo(request)
            val title = info.title?.trim().orEmpty().ifEmpty { "Video" }
            val parsedFormats = FormatParser.parseFormats(info.formats)

            val metadata = VideoMetadata(
                id = info.id.orEmpty(),
                title = title,
                uploader = info.uploader.orEmpty(),
                durationSeconds = info.duration,
                thumbnailUrl = info.thumbnail,
                webpageUrl = info.webpageUrl ?: trimmedUrl,
                formats = parsedFormats
            )
            Result.success(metadata)
        } catch (e: YoutubeDLException) {
            val msg = e.message.orEmpty().lowercase()
            val friendly = when {
                msg.contains("private video") -> "This video is private."
                msg.contains("sign in") -> "This video requires login or authentication."
                msg.contains("unavailable") || msg.contains("not found") -> "Video is invalid or unavailable."
                msg.contains("connection") || msg.contains("network") -> "Network error connecting to video server."
                else -> "Video is invalid or unavailable."
            }
            Result.failure(Exception(friendly, e))
        } catch (e: Exception) {
            Result.failure(Exception("Video is invalid or unavailable.", e))
        }
    }

    /**
     * Downloads video according to options, time range, and format.
     */
    fun download(
        context: Context,
        url: String,
        title: String,
        formatId: String,
        isAudioOnly: Boolean,
        timeRange: TimeRange?,
        processId: String,
        onProgress: (ProgressUpdate) -> Unit
    ): Result<Pair<Uri?, String?>> {
        return try {
            val workDir = File(MediaStoreHelper.getTempDownloadDir(context), processId)
            if (!workDir.exists()) workDir.mkdirs()

            val outputPattern = "${workDir.absolutePath}/%(title)s.%(ext)s"
            val request = YoutubeDLRequest(url.trim()).apply {
                addOption("-o", outputPattern)
                addOption("--no-playlist")
                addOption("--no-mtime")
                addOption("--concurrent-fragments", "4")

                // Format selection logic
                if (isAudioOnly) {
                    addOption("-f", if (formatId.isNotBlank()) formatId else "bestaudio/best")
                    addOption("-x") // extract audio
                } else {
                    if (formatId.isNotBlank()) {
                        // If standard single video format or user selected specific ID
                        if (formatId.contains("+")) {
                            addOption("-f", formatId)
                        } else {
                            // Merge with best audio so the video has sound
                            addOption("-f", "$formatId+bestaudio/best")
                        }
                    } else {
                        addOption("-f", "bestvideo+bestaudio/best")
                    }
                    addOption("--merge-output-format", "mp4")
                }

                // Time trimming section
                if (timeRange != null && timeRange.startTime.isNotBlank() && timeRange.endTime.isNotBlank()) {
                    addOption("--download-sections", "*${timeRange.startTime}-${timeRange.endTime}")
                    if (timeRange.cutMode == CutMode.PRECISE_CUT) {
                        addOption("--force-keyframes-at-cuts")
                    }
                }
            }

            YoutubeDL.getInstance().execute(request, processId) { progress, etaInSeconds, line ->
                val speed = extractSpeed(line)
                onProgress(
                    ProgressUpdate(
                        progress = progress.coerceIn(0f, 100f),
                        etaSeconds = etaInSeconds,
                        speedText = speed,
                        rawLine = line.orEmpty()
                    )
                )
            }

            // Locate final downloaded file in workDir
            val downloadedFiles = workDir.listFiles()?.filter {
                it.isFile && !it.name.endsWith(".part") && !it.name.endsWith(".ytdl")
            }

            val finalFile = downloadedFiles?.maxByOrNull { it.lastModified() }
            if (finalFile == null || !finalFile.exists() || finalFile.length() == 0L) {
                return Result.failure(Exception("Download failed: File not found or empty."))
            }

            // Copy to Public Downloads directory via MediaStore
            val result = MediaStoreHelper.saveToPublicDownloads(context, finalFile, title)

            // Clean up temporary work dir
            try {
                finalFile.delete()
                workDir.deleteRecursively()
            } catch (_: Exception) {}

            Result.success(result)
        } catch (e: YoutubeDLException) {
            Result.failure(e)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun cancel(processId: String) {
        try {
            YoutubeDL.getInstance().destroyProcessById(processId)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun updateEngine(context: Context): Result<String> {
        return try {
            val status = YoutubeDL.getInstance().updateYoutubeDL(context)
            Result.success(status?.name ?: "Updated")
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun extractSpeed(line: String?): String {
        if (line.isNullOrBlank()) return ""
        val matcher = SPEED_PATTERN.matcher(line)
        return if (matcher.find()) {
            matcher.group(1).orEmpty()
        } else ""
    }
}
