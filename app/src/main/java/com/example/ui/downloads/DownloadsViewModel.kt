package com.example.ui.downloads

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.AppDatabase
import com.example.data.local.DownloadTaskEntity
import com.example.data.repository.DownloadRepository
import com.example.domain.model.DownloadStatus
import com.example.downloader.DownloadManager
import com.example.storage.MediaStoreHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

enum class DownloadFilter {
    ALL,
    ACTIVE,
    COMPLETED,
    FAILED
}

class DownloadsViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = DownloadRepository(AppDatabase.getInstance(application).downloadTaskDao())
    private val downloadManager = DownloadManager.getInstance(application)

    private val _selectedFilter = MutableStateFlow(DownloadFilter.ALL)
    val selectedFilter: StateFlow<DownloadFilter> = _selectedFilter.asStateFlow()

    val tasks: StateFlow<List<DownloadTaskEntity>> = combine(
        repository.allTasks,
        _selectedFilter
    ) { allTasks, filter ->
        when (filter) {
            DownloadFilter.ALL -> allTasks
            DownloadFilter.ACTIVE -> allTasks.filter {
                it.status == DownloadStatus.DOWNLOADING || it.status == DownloadStatus.QUEUED
            }
            DownloadFilter.COMPLETED -> allTasks.filter { it.status == DownloadStatus.COMPLETED }
            DownloadFilter.FAILED -> allTasks.filter {
                it.status == DownloadStatus.FAILED || it.status == DownloadStatus.CANCELLED
            }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val activeCount: StateFlow<Int> = repository.allTasks.combine(_selectedFilter) { allTasks, _ ->
        allTasks.count { it.status == DownloadStatus.DOWNLOADING || it.status == DownloadStatus.QUEUED }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = 0
    )

    fun setFilter(filter: DownloadFilter) {
        _selectedFilter.update { filter }
    }

    fun cancel(taskId: String) {
        downloadManager.cancelDownload(taskId)
    }

    fun retry(taskId: String) {
        downloadManager.retryDownload(taskId)
    }

    fun delete(taskId: String) {
        downloadManager.deleteDownload(taskId)
    }

    fun clearFinished() {
        downloadManager.clearFinished()
    }

    fun openDownloadedFile(context: Context, task: DownloadTaskEntity) {
        MediaStoreHelper.openFile(context, task.filePath, task.contentUri)
    }
}
