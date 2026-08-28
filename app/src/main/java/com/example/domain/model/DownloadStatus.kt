package com.example.domain.model

enum class DownloadStatus {
    IDLE,
    QUEUED,
    PREPARING,
    ANALYZING,
    DOWNLOADING,
    PROCESSING_FFMPEG,
    COMPLETED,
    FAILED,
    CANCELLED
}
