package com.example.ui.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.DownloadVideosApplication
import com.example.data.local.DownloadTaskEntity
import com.example.domain.model.CutSettings
import com.example.domain.model.DownloadError
import com.example.domain.model.FormatInfo
import com.example.domain.model.TimeRange
import com.example.downloader.engine.CategorizedFormats
import com.example.ytdlp.YtDlpErrorMapper
import com.example.ytdlp.YtDlpLogger
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * ViewModel for Home screen state management.
 * Coordinates video extraction via yt-dlp, format categorization, and quality presets.
 */
class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as DownloadVideosApplication
    private val container = app.container

    private val _urlInput = MutableStateFlow("")
    val urlInput: StateFlow<String> = _urlInput.asStateFlow()

    private val _uiState = MutableStateFlow<HomeUiState>(HomeUiState.Idle)
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private var analysisJob: Job? = null
    private var currentProcessId: String? = null

    val recentTasks: StateFlow<List<DownloadTaskEntity>> =
        container.downloadRepository.allTasks
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun onUrlChange(newUrl: String) {
        _urlInput.value = newUrl
        if (_uiState.value is HomeUiState.Error) {
            _uiState.value = HomeUiState.Idle
        }
    }

    fun analyzeUrl() {
        val url = _urlInput.value.trim()
        if (url.isEmpty()) {
            _uiState.value = HomeUiState.Error(
                DownloadError.InvalidUrl("Please enter a valid video URL", "URL cannot be empty")
            )
            return
        }

        // Cancel any pending extraction
        analysisJob?.cancel()

        val processId = "analyze_${System.currentTimeMillis()}"
        currentProcessId = processId

        analysisJob = viewModelScope.launch {
            _uiState.value = HomeUiState.Analyzing(processId)

            val result = container.videoExtractor.extractInfo(url, processId)
            result.fold(
                onSuccess = { videoInfo ->
                    val categorized = container.formatProvider.categorize(videoInfo.formats)
                    val defaultFormat = container.formatProvider.getBestQuality(videoInfo.formats)

                    // Choose initial active tab depending on available format types
                    val initialTab = when {
                        categorized.videoAndAudioFormats.isNotEmpty() -> FormatTab.VIDEO_AND_AUDIO
                        categorized.videoOnlyFormats.isNotEmpty() -> FormatTab.VIDEO_ONLY
                        categorized.audioOnlyFormats.isNotEmpty() -> FormatTab.AUDIO_ONLY
                        else -> FormatTab.ALL
                    }

                    _uiState.value = HomeUiState.Ready(
                        videoInfo = videoInfo,
                        categorizedFormats = categorized,
                        selectedFormat = defaultFormat,
                        activeTab = initialTab,
                        activePreset = QualityPreset.BEST_QUALITY
                    )
                },
                onFailure = { throwable ->
                    val error = if (throwable is DownloadError) {
                        throwable
                    } else {
                        YtDlpErrorMapper.map(throwable)
                    }
                    _uiState.value = HomeUiState.Error(error)
                }
            )
        }
    }

    fun cancelAnalysis() {
        val procId = currentProcessId
        analysisJob?.cancel()
        analysisJob = null

        if (procId != null) {
            viewModelScope.launch {
                container.videoExtractor.cancel(procId)
            }
        }

        _uiState.value = HomeUiState.Idle
    }

    fun selectFormat(format: FormatInfo) {
        val current = _uiState.value as? HomeUiState.Ready ?: return
        YtDlpLogger.logFormatSelected(format.formatId, false)
        _uiState.value = current.copy(
            selectedFormat = format,
            activePreset = null,
            isManualInputEnabled = false
        )
    }

    fun selectTab(tab: FormatTab) {
        val current = _uiState.value as? HomeUiState.Ready ?: return
        _uiState.value = current.copy(activeTab = tab)
    }

    fun selectQualityPreset(preset: QualityPreset) {
        val current = _uiState.value as? HomeUiState.Ready ?: return
        val formats = current.videoInfo.formats

        val matchedFormat: FormatInfo? = when (preset) {
            QualityPreset.BEST_QUALITY -> container.formatProvider.getBestQuality(formats)
            QualityPreset.BEST_VIDEO -> container.formatProvider.getBestVideo(formats)
            QualityPreset.P1080 -> container.formatProvider.findByHeight(formats, 1080)
            QualityPreset.P720 -> container.formatProvider.findByHeight(formats, 720)
            QualityPreset.P480 -> container.formatProvider.findByHeight(formats, 480)
            QualityPreset.BEST_AUDIO -> container.formatProvider.getBestAudio(formats)
        }

        if (matchedFormat != null) {
            val targetTab = when {
                preset == QualityPreset.BEST_AUDIO -> FormatTab.AUDIO_ONLY
                matchedFormat.isVideoAndAudio -> FormatTab.VIDEO_AND_AUDIO
                matchedFormat.isVideoOnly -> FormatTab.VIDEO_ONLY
                else -> current.activeTab
            }

            YtDlpLogger.logFormatSelected(matchedFormat.formatId, false)
            _uiState.value = current.copy(
                selectedFormat = matchedFormat,
                activePreset = preset,
                activeTab = targetTab,
                isManualInputEnabled = false
            )
        } else {
            // Keep preset indicator even if exact preset height resolution isn't available
            _uiState.value = current.copy(activePreset = preset)
        }
    }

    fun toggleManualInput(enabled: Boolean) {
        val current = _uiState.value as? HomeUiState.Ready ?: return
        _uiState.value = current.copy(isManualInputEnabled = enabled)
    }

    fun onManualFormatInputChange(input: String) {
        val current = _uiState.value as? HomeUiState.Ready ?: return
        _uiState.value = current.copy(manualFormatInput = input)
    }

    fun applyManualFormatId() {
        val current = _uiState.value as? HomeUiState.Ready ?: return
        val manualId = current.manualFormatInput.trim()
        if (manualId.isEmpty()) return

        val existing = container.formatProvider.findByFormatId(current.videoInfo.formats, manualId)
        val formatToUse = existing ?: FormatInfo(
            formatId = manualId,
            formatNote = "Manual Custom ID",
            extension = "mp4",
            resolution = manualId,
            hasVideo = !manualId.contains("audio"),
            hasAudio = !manualId.contains("video")
        )

        YtDlpLogger.logFormatSelected(manualId, true)
        _uiState.value = current.copy(
            selectedFormat = formatToUse,
            activePreset = null
        )
    }

    fun resetAnalysis() {
        _uiState.value = HomeUiState.Idle
    }

    fun updateCutSettings(cutSettings: CutSettings) {
        val current = _uiState.value as? HomeUiState.Ready ?: return
        _uiState.value = current.copy(cutSettings = cutSettings)
    }

    fun startDownload(onNavigateToDownloads: () -> Unit) {
        val current = _uiState.value as? HomeUiState.Ready ?: return
        val format = current.selectedFormat ?: return

        val startTime = current.cutSettings.startTime
        val endTime = current.cutSettings.endTime
        val timeRange = if (current.cutSettings.enabled && !startTime.isNullOrBlank() && !endTime.isNullOrBlank()) {
            TimeRange(
                startTime = startTime,
                endTime = endTime,
                cutMode = current.cutSettings.mode
            )
        } else null

        container.downloadManager.startDownload(
            url = current.videoInfo.webpageUrl,
            title = current.videoInfo.title,
            thumbnailUrl = current.videoInfo.thumbnail,
            formatId = format.formatId,
            formatDescription = format.displayTitle,
            isAudioOnly = format.isAudioOnly,
            timeRange = timeRange
        )

        _uiState.value = HomeUiState.Idle
        _urlInput.value = ""
        onNavigateToDownloads()
    }

    fun clearInput() {
        _urlInput.value = ""
        _uiState.value = HomeUiState.Idle
    }
}
