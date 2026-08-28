package com.example.ui

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.example.R
import com.example.ui.downloader.DownloaderScreen
import com.example.ui.downloader.DownloaderViewModel
import com.example.ui.downloads.DownloadsScreen
import com.example.ui.downloads.DownloadsViewModel

@Composable
fun MainScreen(
    downloaderViewModel: DownloaderViewModel,
    downloadsViewModel: DownloadsViewModel
) {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    val activeCount by downloadsViewModel.activeCount.collectAsState()

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                modifier = Modifier.testTag("bottom_navigation_bar")
            ) {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = {
                        Icon(
                            imageVector = Icons.Default.Download,
                            contentDescription = stringResource(id = R.string.tab_downloader)
                        )
                    },
                    label = { Text(stringResource(id = R.string.tab_downloader)) },
                    modifier = Modifier.testTag("tab_downloader")
                )

                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = {
                        BadgedBox(
                            badge = {
                                if (activeCount > 0) {
                                    Badge { Text("$activeCount") }
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.VideoLibrary,
                                contentDescription = stringResource(id = R.string.tab_downloads)
                            )
                        }
                    },
                    label = { Text(stringResource(id = R.string.tab_downloads)) },
                    modifier = Modifier.testTag("tab_downloads")
                )
            }
        }
    ) { innerPadding ->
        when (selectedTab) {
            0 -> DownloaderScreen(
                viewModel = downloaderViewModel,
                onDownloadStarted = {
                    selectedTab = 1
                },
                modifier = Modifier.padding(innerPadding)
            )
            1 -> DownloadsScreen(
                viewModel = downloadsViewModel,
                modifier = Modifier.padding(innerPadding)
            )
        }
    }
}
