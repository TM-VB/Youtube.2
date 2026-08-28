package com.example.downloader

import android.content.Context
import com.example.data.local.AppDatabase
import com.example.data.local.DownloadTaskEntity
import com.example.data.repository.DownloadRepository
import com.example.domain.model.CutMode
import com.example.domain.model.DownloadRequest
import com.example.domain.model.DownloadStatus
import com.example.domain.model.TimeRange
import com.example.downloader.engine.YtDlpDownloadEngine
import com.example.downloader.ffmpeg.FFmpegManager
import com.example.service.DownloadForegroundService
import com.example.storage.MediaStoreHelper
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
    private val downloadEngine = YtDlpDownloadEngine(context, FFmpegManager(context))

    fun hasActiveDownloads(): Boolean = activeJobs.isNotEmpty()

    fun startDownload(request: DownloadRequest): String {
        val taskId = request.id

        val entity = DownloadTaskEntity(
            id = taskId,
            url = request.url,
            title = request.title,
            thumbnailUrl = request.thumbnailUrl,
            formatId = request.formatSelector,
            formatDescription = request.formatDescription,
            startTime = request.startTime,
            endTime = request.endTime,
            cutMode = request.cutMode.id,
            status = DownloadStatus.QUEUED,
            progress = 0f
        )

        val timeRange = if (request.hasTimeTrim) {
            TimeRange(
                startTime = request.startTime!!,
                endTime = request.endTime!!,
                cutMode = request.cutMode
            )
        } else null

        val job = scope.launch {
            repository.insertTask(entity)
            executeDownload(taskId, entity, request.isAudioOnly, timeRange)
        }

        activeJobs[taskId] = job
        return taskId
    }

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
        // Storage space check
        if (!MediaStoreHelper.hasEnoughStorageSpace(context)) {
            val failedTask = task.copy(
                status = DownloadStatus.FAILED,
                errorMessage = "Insufficient storage space on device (less than 50MB available)."
            )
            repository.updateTask(failedTask)
            activeJobs.remove(taskId)
            return
        }

        // Set to PREPARING
        val preparingTask = task.copy(status = DownloadStatus.PREPARING)
        repository.updateTask(preparingTask)

        // Set to DOWNLOADING
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
        downloadEngine.let {
            scope.launch { it.cancel(taskId) }
        }
        activeJobs[taskId]?.cancel()
        activeJobs.remove(taskId)

        scope.launch {
            val task = repository.getTaskByIdSync(taskId)
            if (task != null && (task.status == DownloadStatus.DOWNLOADING || task.status == DownloadStatus.QUEUED || task.status == DownloadStatus.PREPARING)) {
                repository.updateTask(
                    task.copy(
                        status = DownloadStatus.CANCELLED,
                        downloadSpeed = "",
                        eta = ""
                    )
                )
                DownloadForegroundService.stop(context, task.title)
            }
        }
    }

    fun retryDownload(taskId: String) {
        scope.launch {
            val task = repository.getTaskByIdSync(taskId) ?: return@launch
            val isAudioOnly = task.formatDescription.contains("Audio", ignoreCase = true)
            val cutMode = if (task.cutMode.equals("precise", ignoreCase = true)) CutMode.PRECISE_CUT else CutMode.FAST_CUT
            val timeRange = if (!task.startTime.isNullOrBlank() && !task.endTime.isNullOrBlank()) {
                TimeRange(task.startTime, task.endTime, cutMode)
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
