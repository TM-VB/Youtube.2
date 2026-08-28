package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.domain.model.DownloadStatus

@Entity(tableName = "download_tasks")
data class DownloadTaskEntity(
    @PrimaryKey val id: String,
    val url: String,
    val title: String,
    val thumbnailUrl: String?,
    val formatId: String,
    val formatDescription: String,
    val startTime: String?,
    val endTime: String?,
    val cutMode: String,
    val status: DownloadStatus,
    val progress: Float = 0f,
    val downloadSpeed: String = "",
    val downloadedSize: String = "",
    val totalSize: String = "",
    val eta: String = "",
    val filePath: String? = null,
    val contentUri: String? = null,
    val errorMessage: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val completedAt: Long? = null
)
