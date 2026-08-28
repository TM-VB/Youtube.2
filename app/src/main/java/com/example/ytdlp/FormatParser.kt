package com.example.ytdlp

import com.example.domain.model.FormatInfo
import com.example.domain.model.FormatOption
import com.yausername.youtubedl_android.mapper.VideoFormat

/**
 * Parses raw yt-dlp VideoFormat objects into strongly-typed domain FormatInfo models
 * and provides backward-compatibility helpers for FormatOption.
 */
object FormatParser {

    private val AUDIO_EXTENSIONS = setOf("m4a", "mp3", "opus", "aac", "flac", "wav", "ogg")

    fun parseFormats(rawFormats: List<VideoFormat>?): List<FormatInfo> {
        if (rawFormats.isNullOrEmpty()) return emptyList()

        val parsedList = mutableListOf<FormatInfo>()

        for (format in rawFormats) {
            val formatId = format.formatId?.trim() ?: continue
            if (formatId.isEmpty()) continue

            val ext = format.ext?.trim()?.lowercase() ?: "mp4"
            val formatNote = format.formatNote?.trim()

            // Skip storyboard, mhtml, internal/unsupported artifacts
            if (ext == "mhtml" || formatNote?.contains("storyboard", ignoreCase = true) == true) {
                continue
            }

            val vcodec = format.vcodec?.trim()
            val acodec = format.acodec?.trim()
            val width = format.width
            val height = format.height
            val fps = format.fps.toDouble()
            val fileSize = format.fileSize
            val fileSizeApprox = format.fileSizeApproximate
            val tbr = format.tbr.toDouble()
            val abr = format.abr.toDouble()

            val isAudioNone = acodec.isNullOrBlank() || acodec == "none"
            val isVideoNone = vcodec.isNullOrBlank() || vcodec == "none"

            var hasVideo = !isVideoNone
            var hasAudio = !isAudioNone

            // Fallback for containers where codecs are unpopulated
            if (!hasVideo && !hasAudio) {
                if (AUDIO_EXTENSIONS.contains(ext) || formatNote?.contains("audio", ignoreCase = true) == true) {
                    hasAudio = true
                } else {
                    hasVideo = true
                    hasAudio = true
                }
            }

            val resolution = when {
                height > 0 -> "${height}p"
                width > 0 -> "${width}x${height}"
                !hasVideo && hasAudio -> "Audio"
                else -> format.format ?: "${ext.uppercase()} stream"
            }

            val bitrate = when {
                tbr > 0 -> tbr
                abr > 0 -> abr
                else -> null
            }

            parsedList.add(
                FormatInfo(
                    formatId = formatId,
                    formatNote = formatNote,
                    extension = ext,
                    resolution = resolution,
                    width = if (width > 0) width else null,
                    height = if (height > 0) height else null,
                    fps = if (fps > 0) fps else null,
                    videoCodec = if (!isVideoNone) vcodec else null,
                    audioCodec = if (!isAudioNone) acodec else null,
                    audioChannels = if (format.asr > 0) 2 else null,
                    bitrate = bitrate,
                    filesize = if (fileSize > 0) fileSize else null,
                    filesizeApprox = if (fileSizeApprox > 0) fileSizeApprox else null,
                    vcodec = if (!isVideoNone) vcodec else null,
                    acodec = if (!isAudioNone) acodec else null,
                    dynamicRange = null,
                    protocol = null,
                    container = ext,
                    hasVideo = hasVideo,
                    hasAudio = hasAudio
                )
            )
        }

        return parsedList
    }

    /**
     * Legacy helper to categorize FormatOption lists.
     */
    fun getCategorizedFormats(formats: List<FormatOption>): Triple<List<FormatOption>, List<FormatOption>, List<FormatOption>> {
        val videoFormats = formats.filter { it.isVideoOnly || it.isCombined }
            .sortedByDescending { it.height ?: 0 }
        val audioFormats = formats.filter { it.isAudioOnly }
            .sortedByDescending { it.bitrate ?: 0.0 }
        return Triple(videoFormats, audioFormats, formats)
    }
}
