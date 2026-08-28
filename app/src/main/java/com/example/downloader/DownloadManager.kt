package com.example.downloader

import android.content.Context
import com.example.data.local.AppDatabase
import com.example.data.local.DownloadTaskEntity
import com.example.data.repository.DownloadRepository
import com.example.domain.model.DownloadStatus
import com.example.domain.model.TimeRange
import com.example.service.DownloadForegroundService
import com.example.ytdlp.YtDlpEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class DownloadManager private constructor(private val context: Context) {

    private val repository = DownloadRepository(AppDatabase.getInstance(context).downloadTaskDao())
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val activeJobs = ConcurrentHashMap<String, Job>()

    fun hasActiveDownloads(): Boolean = activeJobs.isNotEmpty()

    fun startDownload(
        url: String,
        title: String,
        thumbnailUrl: String?,
        formatId: String,
        formatDescription: String,
        isAudioOnly: Boolean,
        timeRange: TimeRange?
    ): String {
        val taskId = UUID.randomUUID().toString()

        val entity = DownloadTaskEntity(
            id = taskId,
            url = url,
            title = title,
            thumbnailUrl = thumbnailUrl,
            formatId = formatId,
            formatDescription = formatDescription,
            startTime = timeRange?.startTime,
            endTime = timeRange?.endTime,
            cutMode = timeRange?.cutMode?.id ?: "none",
            status = DownloadStatus.QUEUED,
            progress = 0f
        )

        val job = scope.launch {
            repository.insertTask(entity)
            executeDownload(taskId, entity, isAudioOnly, timeRange)
        }

        activeJobs[taskId] = job
        return taskId
    }

    private suspend fun executeDownload(
        taskId: String,
        task: DownloadTaskEntity,
        isAudioOnly: Boolean,
        timeRange: TimeRange?
    ) {
        // Update to DOWNLOADING
        val downloadingTask = task.copy(status = DownloadStatus.DOWNLOADING)
        repository.updateTask(downloadingTask)
        DownloadForegroundService.startOrUpdate(context, taskId, task.title, 0)

        val result = YtDlpEngine.download(
            context = context,
            url = task.url,
            title = task.title,
            formatId = task.formatId,
            isAudioOnly = isAudioOnly,
            timeRange = timeRange,
            processId = taskId
        ) { progressUpdate ->
            scope.launch {
                val current = repository.getTaskByIdSync(taskId)
                if (current != null && current.status == DownloadStatus.DOWNLOADING) {
                    val progressInt = progressUpdate.progress.toInt()
                    val etaFormatted = if (progressUpdate.etaSeconds > 0) {
                        val m = progressUpdate.etaSeconds / 60
                        val s = progressUpdate.etaSeconds % 60
                        String.format("%02d:%02d", m, s)
                    } else ""

                    repository.updateTask(
                        current.copy(
                            progress = progressUpdate.progress,
                            downloadSpeed = progressUpdate.speedText,
                            eta = etaFormatted
                        )
                    )
                    DownloadForegroundService.startOrUpdate(context, taskId, task.title, progressInt)
                }
            }
        }

        activeJobs.remove(taskId)

        result.fold(
            onSuccess = { (uri, path) ->
                val completedTask = repository.getTaskByIdSync(taskId)?.copy(
                    status = DownloadStatus.COMPLETED,
                    progress = 100f,
                    contentUri = uri?.toString(),
                    filePath = path,
                    downloadSpeed = "",
                    eta = "",
                    completedAt = System.currentTimeMillis()
                )
                if (completedTask != null) {
                    repository.updateTask(completedTask)
                }
                DownloadForegroundService.stop(context, task.title)
            },
            onFailure = { error ->
                val isCancelled = error.message?.contains("destroy", ignoreCase = true) == true ||
                        error.message?.contains("interrupted", ignoreCase = true) == true ||
                        error.message?.contains("cancel", ignoreCase = true) == true

                val finalStatus = if (isCancelled) DownloadStatus.CANCELLED else DownloadStatus.FAILED
                val errorTask = repository.getTaskByIdSync(taskId)?.copy(
                    status = finalStatus,
                    downloadSpeed = "",
                    eta = "",
                    errorMessage = error.localizedMessage ?: "Download error"
                )
                if (errorTask != null) {
                    repository.updateTask(errorTask)
                }
                DownloadForegroundService.stop(context, task.title)
            }
        )
    }

    fun cancelDownload(taskId: String) {
        YtDlpEngine.cancel(taskId)
        activeJobs[taskId]?.cancel()
        activeJobs.remove(taskId)

        scope.launch {
            val task = repository.getTaskByIdSync(taskId)
            if (task != null && task.status == DownloadStatus.DOWNLOADING) {
                repository.updateTask(
                    task.copy(
                        status = DownloadStatus.CANCELLED,
                        downloadSpeed = "",
                        eta = ""
                    )
                )
            }
        }
    }

    fun retryDownload(taskId: String) {
        scope.launch {
            val task = repository.getTaskByIdSync(taskId) ?: return@launch
            val isAudioOnly = task.formatDescription.contains("Audio", ignoreCase = true)
            val timeRange = if (!task.startTime.isNullOrBlank() && !task.endTime.isNullOrBlank()) {
                TimeRange(task.startTime, task.endTime)
            } else null

            val resetTask = task.copy(
                status = DownloadStatus.QUEUED,
                progress = 0f,
                errorMessage = null,
                downloadSpeed = "",
                eta = ""
            )
            repository.updateTask(resetTask)

            val job = scope.launch {
                executeDownload(taskId, resetTask, isAudioOnly, timeRange)
            }
            activeJobs[taskId] = job
        }
    }

    fun deleteDownload(taskId: String) {
        cancelDownload(taskId)
        scope.launch {
            repository.deleteTask(taskId)
        }
    }

    fun clearFinished() {
        scope.launch {
            repository.clearFinishedTasks()
        }
    }

    companion object {
        @Volatile
        private var INSTANCE: DownloadManager? = null

        fun getInstance(context: Context): DownloadManager {
            return INSTANCE ?: synchronized(this) {
                val instance = DownloadManager(context.applicationContext)
                INSTANCE = instance
                instance
            }
        }
    }
}
