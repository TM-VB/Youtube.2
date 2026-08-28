package com.example.ui.home

import com.example.domain.model.CutSettings
import com.example.domain.model.DownloadError
import com.example.domain.model.FormatInfo
import com.example.domain.model.VideoInfo
import com.example.downloader.engine.CategorizedFormats

enum class FormatTab(val label: String) {
    VIDEO_AND_AUDIO("Video + Audio"),
    VIDEO_ONLY("Video Only"),
    AUDIO_ONLY("Audio Only"),
    ALL("All Formats")
}

enum class QualityPreset(val label: String) {
    BEST_QUALITY("Best Quality"),
    BEST_VIDEO("Best Video"),
    P1080("1080p"),
    P720("720p"),
    P480("480p"),
    BEST_AUDIO("Best Audio")
}

/**
 * UI State for the Home screen following Clean Architecture and MVI state patterns.
 */
sealed interface HomeUiState {
    data object Idle : HomeUiState

    data class Analyzing(val processId: String) : HomeUiState

    data class Ready(
        val videoInfo: VideoInfo,
        val categorizedFormats: CategorizedFormats,
        val selectedFormat: FormatInfo? = null,
        val activeTab: FormatTab = FormatTab.VIDEO_AND_AUDIO,
        val activePreset: QualityPreset? = QualityPreset.BEST_QUALITY,
        val isManualInputEnabled: Boolean = false,
        val manualFormatInput: String = "",
        val cutSettings: CutSettings = CutSettings()
    ) : HomeUiState {
        val visibleFormats: List<FormatInfo>
            get() = when (activeTab) {
                FormatTab.VIDEO_AND_AUDIO -> categorizedFormats.videoAndAudioFormats
                FormatTab.VIDEO_ONLY -> categorizedFormats.videoOnlyFormats
                FormatTab.AUDIO_ONLY -> categorizedFormats.audioOnlyFormats
                FormatTab.ALL -> categorizedFormats.allFormats
            }
    }

    data class Error(val error: DownloadError) : HomeUiState
}
