package com.example

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import com.example.ui.MainScreen
import com.example.ui.downloader.DownloaderViewModel
import com.example.ui.downloads.DownloadsViewModel
import com.example.ui.theme.VideoDownloaderTheme

class MainActivity : ComponentActivity() {

    private val downloaderViewModel: DownloaderViewModel by viewModels()
    private val downloadsViewModel: DownloadsViewModel by viewModels()

    private val requestNotificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { _ ->
            // Notification permission result handled
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        requestNotificationPermissionIfNeeded()
        handleSharedIntent(intent)

        setContent {
            VideoDownloaderTheme {
                MainScreen(
                    downloaderViewModel = downloaderViewModel,
                    downloadsViewModel = downloadsViewModel
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleSharedIntent(intent)
    }

    private fun handleSharedIntent(intent: Intent?) {
        if (intent?.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)
            if (!sharedText.isNullOrBlank()) {
                val url = extractUrl(sharedText)
                if (url.isNotBlank()) {
                    downloaderViewModel.onUrlChanged(url)
                    downloaderViewModel.analyzeUrl()
                }
            }
        }
    }

    private fun extractUrl(text: String): String {
        val words = text.split("\\s+".toRegex())
        return words.firstOrNull { it.startsWith("http://") || it.startsWith("https://") } ?: text.trim()
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
}
