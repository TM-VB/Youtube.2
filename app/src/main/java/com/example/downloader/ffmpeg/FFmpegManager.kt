package com.example.downloader.ffmpeg

import android.content.Context
import android.os.Build
import com.example.domain.model.CutMode
import com.example.domain.model.DownloadError
import com.example.downloader.engine.MediaProcessor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Robust manager for FFmpeg integration on Android.
 * Handles ABI selection, binary locating, argument-array execution without shell injection,
 * real progress tracking, and graceful process cancellation.
 */
class FFmpegManager(private val context: Context) : MediaProcessor {

    private val runningProcesses = ConcurrentHashMap<String, Process>()

    val supportedAbis: List<String>
        get() = Build.SUPPORTED_ABIS.toList()

    val primaryAbi: String
        get() = Build.SUPPORTED_ABIS.firstOrNull() ?: "unknown"

    /**
     * Resolves the FFmpeg binary file from youtubedl-android or native lib directories.
     */
    fun getFFmpegBinary(): File? {
        // 1. Try resolving via embedded youtubedl FFmpeg instance
        try {
            val ffmpegClass = Class.forName("com.yausername.ffmpeg.FFmpeg")
            val getInstanceMethod = ffmpegClass.getMethod("getInstance")
            val ffmpegInstance = getInstanceMethod.invoke(null)
            try {
                val initMethod = ffmpegClass.getMethod("init", Context::class.java)
                initMethod.invoke(ffmpegInstance, context.applicationContext)
            } catch (_: Throwable) {}

            val binDirField = ffmpegClass.getDeclaredField("binDir").apply { isAccessible = true }
            val binDir = binDirField.get(ffmpegInstance) as? File
            if (binDir != null) {
                val binary = File(binDir, "ffmpeg")
                if (binary.exists()) {
                    if (!binary.canExecute()) binary.setExecutable(true)
                    return binary
                }
            }
        } catch (_: Throwable) {}

        // 2. Check nativeLibraryDir for bundled native libraries
        val nativeDir = File(context.applicationInfo.nativeLibraryDir)
        val possibleNames = listOf("libffmpeg.so", "ffmpeg.so", "ffmpeg")
        for (name in possibleNames) {
            val file = File(nativeDir, name)
            if (file.exists()) {
                if (!file.canExecute()) file.setExecutable(true)
                return file
            }
        }

        // 3. Check internal app directories
        val candidates = listOf(
            File(context.filesDir, "usr/bin/ffmpeg"),
            File(context.filesDir, "bin/ffmpeg"),
            File(context.noBackupFilesDir, "usr/bin/ffmpeg"),
            File(context.noBackupFilesDir, "bin/ffmpeg")
        )
        for (candidate in candidates) {
            if (candidate.exists()) {
                if (!candidate.canExecute()) candidate.setExecutable(true)
                return candidate
            }
        }

        return null
    }

    fun getStatus(): FFmpegStatus {
        val binary = getFFmpegBinary()
        val available = binary != null && binary.exists()
        val executable = binary?.canExecute() == true

        return FFmpegStatus(
            isAvailable = available,
            binaryPath = binary?.absolutePath,
            detectedAbi = primaryAbi,
            isExecutable = executable,
            errorMessage = if (!available) "FFmpeg binary not found for ABI $primaryAbi" else null
        )
    }

    fun buildCutArgs(
        binaryPath: String,
        inputFile: File,
        outputFile: File,
        startTime: String,
        endTime: String,
        mode: CutMode
    ): List<String> {
        return when (mode) {
            CutMode.FAST_CUT -> listOf(
                binaryPath,
                "-y",
                "-ss", startTime,
                "-to", endTime,
                "-i", inputFile.absolutePath,
                "-c", "copy",
                "-avoid_negative_ts", "make_zero",
                outputFile.absolutePath
            )
            CutMode.PRECISE_CUT -> listOf(
                binaryPath,
                "-y",
                "-ss", startTime,
                "-to", endTime,
                "-i", inputFile.absolutePath,
                "-c:v", "libx264",
                "-crf", "22",
                "-preset", "veryfast",
                "-c:a", "aac",
                outputFile.absolutePath
            )
        }
    }

    fun buildMergeArgs(
        binaryPath: String,
        videoFile: File,
        audioFile: File,
        outputFile: File
    ): List<String> {
        return listOf(
            binaryPath,
            "-y",
            "-i", videoFile.absolutePath,
            "-i", audioFile.absolutePath,
            "-c:v", "copy",
            "-c:a", "aac",
            outputFile.absolutePath
        )
    }

    override suspend fun cutMedia(
        inputFile: File,
        outputFile: File,
        startTime: String,
        endTime: String,
        mode: CutMode,
        onProgress: (Float) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        if (!inputFile.exists()) {
            return@withContext Result.failure(
                DownloadError.FfmpegError("Input file does not exist: ${inputFile.path}")
            )
        }

        val binary = getFFmpegBinary()
        if (binary == null || !binary.exists()) {
            return@withContext Result.failure(
                DownloadError.FfmpegError("FFmpeg binary not available for processing.")
            )
        }

        val totalDuration = calculateDurationSeconds(startTime, endTime)
        val command = buildCutArgs(binary.absolutePath, inputFile, outputFile, startTime, endTime, mode)
        val processId = UUID.randomUUID().toString()

        executeCommand(processId, command, totalDuration, onProgress).map { outputFile }
    }

    override suspend fun mergeAudioVideo(
        videoFile: File,
        audioFile: File,
        outputFile: File
    ): Result<File> = withContext(Dispatchers.IO) {
        if (!videoFile.exists() || !audioFile.exists()) {
            return@withContext Result.failure(
                DownloadError.FfmpegError("Source media files do not exist.")
            )
        }

        val binary = getFFmpegBinary()
        if (binary == null || !binary.exists()) {
            return@withContext Result.failure(
                DownloadError.FfmpegError("FFmpeg binary not available for merging.")
            )
        }

        val command = buildMergeArgs(binary.absolutePath, videoFile, audioFile, outputFile)
        val processId = UUID.randomUUID().toString()

        executeCommand(processId, command, 0.0) {}.map { outputFile }
    }

    suspend fun executeCommand(
        processId: String,
        arguments: List<String>,
        totalDurationSeconds: Double,
        onProgress: (Float) -> Unit
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val processBuilder = ProcessBuilder(arguments)
            processBuilder.redirectErrorStream(true)
            val process = processBuilder.start()
            runningProcesses[processId] = process

            val reader = BufferedReader(InputStreamReader(process.inputStream))
            var line: String?
            val outputLog = StringBuilder()

            while (reader.readLine().also { line = it } != null) {
                val currentLine = line ?: break
                outputLog.appendLine(currentLine)

                val progress = parseProgress(currentLine, totalDurationSeconds)
                if (progress != null) {
                    onProgress(progress)
                }
            }

            val exitCode = process.waitFor()
            runningProcesses.remove(processId)

            if (exitCode == 0) {
                onProgress(100f)
                Result.success(Unit)
            } else {
                Result.failure(
                    DownloadError.FfmpegError(
                        msg = "FFmpeg process failed with exit code $exitCode",
                        detail = outputLog.takeLast(1000).toString()
                    )
                )
            }
        } catch (e: Exception) {
            runningProcesses.remove(processId)
            Result.failure(
                DownloadError.FfmpegError(
                    msg = "FFmpeg execution error: ${e.message}",
                    detail = e.stackTraceToString()
                )
            )
        }
    }

    fun cancel(processId: String) {
        runningProcesses.remove(processId)?.let { process ->
            try {
                process.destroy()
            } catch (_: Exception) {}
        }
    }

    companion object {
        private val TIME_REGEX = Regex("""time=(\d{2}:\d{2}:\d{2}(?:\.\d+)?)""")
        private val SPEED_REGEX = Regex("""speed=\s*(\S+x?)""")
        private val OUT_TIME_MS_REGEX = Regex("""out_time_ms=(\d+)""")

        fun parseTimeSeconds(timeStr: String): Double? {
            val parts = timeStr.trim().split(":")
            if (parts.size != 3) return null
            val hours = parts[0].toDoubleOrNull() ?: return null
            val minutes = parts[1].toDoubleOrNull() ?: return null
            val seconds = parts[2].toDoubleOrNull() ?: return null
            return hours * 3600.0 + minutes * 60.0 + seconds
        }

        fun parseProgress(line: String, totalDurationSeconds: Double): Float? {
            if (totalDurationSeconds <= 0.0) return null

            val msMatch = OUT_TIME_MS_REGEX.find(line)
            if (msMatch != null) {
                val ms = msMatch.groupValues[1].toDoubleOrNull()
                if (ms != null && ms > 0) {
                    val sec = ms / 1_000_000.0
                    val pct = (sec / totalDurationSeconds * 100.0).toFloat()
                    return pct.coerceIn(0f, 100f)
                }
            }

            val timeMatch = TIME_REGEX.find(line)
            if (timeMatch != null) {
                val sec = parseTimeSeconds(timeMatch.groupValues[1])
                if (sec != null) {
                    val pct = (sec / totalDurationSeconds * 100.0).toFloat()
                    return pct.coerceIn(0f, 100f)
                }
            }
            return null
        }

        fun parseSpeed(line: String): String? {
            val match = SPEED_REGEX.find(line)
            return match?.groupValues?.get(1)?.trim()
        }

        fun calculateDurationSeconds(startTime: String, endTime: String): Double {
            val start = parseTimeSeconds(startTime) ?: 0.0
            val end = parseTimeSeconds(endTime) ?: 0.0
            return (end - start).coerceAtLeast(1.0)
        }
    }
}
