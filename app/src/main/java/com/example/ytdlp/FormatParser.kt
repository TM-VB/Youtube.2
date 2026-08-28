package com.example.ytdlp

import com.example.domain.model.FormatOption
import com.yausername.youtubedl_android.mapper.VideoFormat

object FormatParser {

    fun parseFormats(rawFormats: List<VideoFormat>?): List<FormatOption> {
        if (rawFormats.isNullOrEmpty()) return emptyList()

        val parsedList = mutableListOf<FormatOption>()

        for (format in rawFormats) {
            val formatId = format.formatId ?: continue
            val ext = format.ext ?: "mp4"
            val vcodec = format.vcodec
            val acodec = format.acodec
            val width = format.width
            val height = format.height
            val fps = format.fps.toDouble()
            val fileSize = format.fileSize
            val tbr = format.tbr.toDouble()
            val abr = format.abr.toDouble()

            val isAudioNone = acodec == null || acodec == "none"
            val isVideoNone = vcodec == null || vcodec == "none"

            val isAudioOnly = !isAudioNone && isVideoNone
            val isVideoOnly = isAudioNone && !isVideoNone
            val isCombined = !isAudioNone && !isVideoNone

            // Skip storyboards / mhtml / internal formats
            if (ext == "mhtml" || format.formatNote?.contains("storyboard", ignoreCase = true) == true) {
                continue
            }

            val resolution = when {
                height > 0 -> "${height}p"
                width > 0 -> "${width}x${height}"
                isAudioOnly -> "Audio"
                else -> format.format ?: "Unknown"
            }

            val bitrate = when {
                tbr > 0 -> tbr
                abr > 0 -> abr
                else -> null
            }

            parsedList.add(
                FormatOption(
                    formatId = formatId,
                    ext = ext,
                    resolution = resolution,
                    width = if (width > 0) width else null,
                    height = if (height > 0) height else null,
                    fps = if (fps > 0) fps else null,
                    vcodec = if (!isVideoNone) vcodec else null,
                    acodec = if (!isAudioNone) acodec else null,
                    fileSize = fileSize,
                    bitrate = bitrate,
                    isAudioOnly = isAudioOnly,
                    isVideoOnly = isVideoOnly,
                    isCombined = isCombined,
                    note = format.formatNote ?: ""
                )
            )
        }

        return parsedList
    }

    /**
     * Groups formats into convenient categorized lists.
     */
    fun getCategorizedFormats(formats: List<FormatOption>): Triple<List<FormatOption>, List<FormatOption>, List<FormatOption>> {
        val videoFormats = formats.filter { !it.isAudioOnly }
            .sortedWith(
                compareByDescending<FormatOption> { it.height ?: 0 }
                    .thenByDescending { it.bitrate ?: 0.0 }
            )
            // Deduplicate by height & extension to offer best options first
            .distinctBy { "${it.height ?: 0}_${it.ext}" }

        val audioFormats = formats.filter { it.isAudioOnly }
            .sortedByDescending { it.bitrate ?: 0.0 }
            .distinctBy { "${it.ext}_${(it.bitrate ?: 0.0).toInt()}" }

        val allFormats = formats.sortedByDescending { it.height ?: 0 }

        return Triple(videoFormats, audioFormats, allFormats)
    }
}
