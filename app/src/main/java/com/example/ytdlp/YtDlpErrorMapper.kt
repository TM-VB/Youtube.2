package com.example.ytdlp

import com.example.domain.model.DownloadError
import kotlinx.coroutines.CancellationException

/**
 * Maps yt-dlp, network, and runtime exceptions into typed DownloadError instances.
 */
object YtDlpErrorMapper {

    fun map(throwable: Throwable): DownloadError {
        if (throwable is CancellationException) {
            return DownloadError.Cancelled("Video analysis was cancelled.", throwable.message)
        }

        val rawMsg = throwable.message?.trim().orEmpty()
        val lower = rawMsg.lowercase()

        return when {
            lower.contains("is not a valid url") ||
                lower.contains("invalid url") ||
                lower.contains("unsupported url") ||
                lower.contains("no suitable extractor") -> {
                DownloadError.InvalidUrl("Please enter a valid video URL", rawMsg)
            }

            lower.contains("private video") ||
                lower.contains("this video is private") ||
                lower.contains("members-only") -> {
                DownloadError.PrivateVideo("Private videos are not supported", rawMsg)
            }

            lower.contains("sign in to confirm") ||
                lower.contains("sign in") ||
                lower.contains("login required") ||
                lower.contains("this video requires authentication") -> {
                DownloadError.SigninRequired("This video requires login or authentication.", rawMsg)
            }

            lower.contains("not made this video available in your country") ||
                lower.contains("geo-restricted") ||
                lower.contains("georestricted") ||
                lower.contains("geographic") ||
                lower.contains("not available in your region") ||
                lower.contains("blocked in your country") -> {
                DownloadError.GeoRestricted("This video is not available in your region.", rawMsg)
            }

            lower.contains("video unavailable") ||
                lower.contains("this video is unavailable") ||
                lower.contains("removed by the user") ||
                lower.contains("removed by the uploader") ||
                lower.contains("not found") ||
                lower.contains("does not exist") -> {
                DownloadError.VideoUnavailable("Video is unavailable", rawMsg)
            }

            lower.contains("network error") ||
                lower.contains("connection refused") ||
                lower.contains("timed out") ||
                lower.contains("timeout") ||
                lower.contains("temporary failure in name resolution") ||
                lower.contains("unable to download webpage") ||
                lower.contains("no address associated with hostname") -> {
                DownloadError.NetworkError("Network error. Check your connection.", rawMsg)
            }

            lower.contains("no video formats found") ||
                lower.contains("requested format is not available") ||
                lower.contains("no formats found") -> {
                DownloadError.NoFormats("No downloadable formats were found.", rawMsg)
            }

            else -> {
                val cleanSummary = if (rawMsg.isNotEmpty()) {
                    rawMsg.lines().firstOrNull { it.isNotBlank() } ?: "yt-dlp error"
                } else {
                    "yt-dlp engine encountered an error"
                }
                DownloadError.YtDlpError(
                    msg = cleanSummary,
                    detail = rawMsg.ifEmpty { throwable.javaClass.simpleName }
                )
            }
        }
    }
}
