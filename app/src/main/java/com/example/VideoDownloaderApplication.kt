package com.example

import android.app.Application
import com.example.ytdlp.YtDlpEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class VideoDownloaderApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        // Initialize yt-dlp & FFmpeg embedded environment in IO dispatcher
        CoroutineScope(Dispatchers.IO).launch {
            try {
                YtDlpEngine.init(applicationContext)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
