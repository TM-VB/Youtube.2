package com.example.storage

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import com.example.domain.util.FileNameSanitizer
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

object MediaStoreHelper {

    fun getTempDownloadDir(context: Context): File {
        val dir = File(context.cacheDir, "ytdlp_downloads")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    fun getMimeType(fileName: String): String {
        val ext = fileName.substringAfterLast('.', "").lowercase()
        return when (ext) {
            "mp4" -> "video/mp4"
            "mkv" -> "video/x-matroska"
            "webm" -> "video/webm"
            "mp3" -> "audio/mpeg"
            "m4a" -> "audio/mp4"
            "opus" -> "audio/opus"
            "ogg" -> "audio/ogg"
            "wav" -> "audio/wav"
            "flac" -> "audio/flac"
            else -> MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "video/mp4"
        }
    }

    /**
     * Copies a completed download file to the public Downloads/DownloadVideos directory using MediaStore.
     * Returns the MediaStore content Uri string, or null on error.
     */
    fun saveToPublicDownloads(context: Context, sourceFile: File, rawTitle: String): Pair<Uri?, String?> {
        val extension = sourceFile.extension.ifBlank { "mp4" }
        val displayName = FileNameSanitizer.sanitize(rawTitle, extension)
        val mimeType = getMimeType(displayName)

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
                    put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                    put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/DownloadVideos")
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }

                val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                val uri = context.contentResolver.insert(collection, values)

                if (uri != null) {
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        FileInputStream(sourceFile).use { input ->
                            input.copyTo(out)
                        }
                    }

                    values.clear()
                    values.put(MediaStore.MediaColumns.IS_PENDING, 0)
                    context.contentResolver.update(uri, values, null, null)

                    val publicPath = "${Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)}/DownloadVideos/$displayName"
                    return Pair(uri, publicPath)
                }
            } else {
                val publicDir = File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                    "DownloadVideos"
                )
                if (!publicDir.exists()) {
                    publicDir.mkdirs()
                }
                val destFile = File(publicDir, displayName)
                FileInputStream(sourceFile).use { input ->
                    FileOutputStream(destFile).use { output ->
                        input.copyTo(output)
                    }
                }
                val uri = Uri.fromFile(destFile)
                return Pair(uri, destFile.absolutePath)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Fallback: use sourceFile in app cache
        return Pair(null, sourceFile.absolutePath)
    }

    /**
     * Opens downloaded file with system video or audio player
     */
    fun openFile(context: Context, filePath: String?, contentUriStr: String?) {
        try {
            val uri: Uri = when {
                !contentUriStr.isNullOrBlank() -> Uri.parse(contentUriStr)
                !filePath.isNullOrBlank() -> {
                    val file = File(filePath)
                    if (file.exists()) {
                        FileProvider.getUriForFile(
                            context,
                            "${context.packageName}.provider",
                            file
                        )
                    } else return
                }
                else -> return
            }

            val mimeType = getMimeType(filePath ?: "video.mp4")
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mimeType)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(Intent.createChooser(intent, "Open with").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Shares the downloaded media file with other apps via system share sheet
     */
    fun shareFile(context: Context, filePath: String?, contentUriStr: String?) {
        try {
            val uri: Uri = when {
                !contentUriStr.isNullOrBlank() -> Uri.parse(contentUriStr)
                !filePath.isNullOrBlank() -> {
                    val file = File(filePath)
                    if (file.exists()) {
                        FileProvider.getUriForFile(
                            context,
                            "${context.packageName}.provider",
                            file
                        )
                    } else return
                }
                else -> return
            }

            val mimeType = getMimeType(filePath ?: "video.mp4")
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = mimeType
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(shareIntent, "Share video").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun hasEnoughStorageSpace(context: Context, requiredBytes: Long = 50 * 1024 * 1024L): Boolean {
        return try {
            val stat = android.os.StatFs(context.cacheDir.path)
            val available = stat.availableBlocksLong * stat.blockSizeLong
            available >= requiredBytes
        } catch (_: Exception) {
            true
        }
    }
}
