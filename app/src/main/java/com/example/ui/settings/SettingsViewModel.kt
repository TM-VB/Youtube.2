package com.example.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.DownloadVideosApplication
import com.example.downloader.ffmpeg.FFmpegStatus
import com.example.python.PythonStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class SettingsUiState(
    val pythonStatus: PythonStatus,
    val ffmpegStatus: FFmpegStatus,
    val storagePath: String = "Downloads/DownloadVideos",
    val primaryAbi: String
)

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as DownloadVideosApplication
    private val container = app.container

    private val _uiState = MutableStateFlow(
        SettingsUiState(
            pythonStatus = container.pythonRuntimeManager.getStatus(),
            ffmpegStatus = container.ffmpegManager.getStatus(),
            primaryAbi = container.ffmpegManager.primaryAbi
        )
    )
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    fun refreshStatus() {
        _uiState.value = SettingsUiState(
            pythonStatus = container.pythonRuntimeManager.getStatus(),
            ffmpegStatus = container.ffmpegManager.getStatus(),
            primaryAbi = container.ffmpegManager.primaryAbi
        )
    }

    fun cleanTempStorage() {
        viewModelScope.launch {
            container.storageManager.cleanTempFiles()
            refreshStatus()
        }
    }
}
