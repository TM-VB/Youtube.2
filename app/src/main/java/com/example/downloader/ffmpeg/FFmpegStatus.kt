package com.example.downloader.ffmpeg

/**
 * Status information for the embedded FFmpeg binary.
 */
data class FFmpegStatus(
    val isAvailable: Boolean,
    val binaryPath: String?,
    val detectedAbi: String,
    val isExecutable: Boolean,
    val errorMessage: String? = null
)
