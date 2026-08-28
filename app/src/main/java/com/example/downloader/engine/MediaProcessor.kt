package com.example.downloader.engine

import com.example.domain.model.CutMode
import java.io.File

/**
 * Interface for media processing tasks (such as FFmpeg section cutting and audio/video merging).
 */
interface MediaProcessor {
    suspend fun cutMedia(
        inputFile: File,
        outputFile: File,
        startTime: String,
        endTime: String,
        mode: CutMode,
        onProgress: (Float) -> Unit
    ): Result<File>

    suspend fun mergeAudioVideo(
        videoFile: File,
        audioFile: File,
        outputFile: File
    ): Result<File>
}
