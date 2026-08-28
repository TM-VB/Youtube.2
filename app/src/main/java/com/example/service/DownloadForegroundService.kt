package com.example.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.downloader.DownloadManager

class DownloadForegroundService : Service() {

    private lateinit var notificationManager: NotificationManager

    override fun onCreate() {
        super.onCreate()
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        val taskId = intent?.getStringExtra(EXTRA_TASK_ID)

        if (action == ACTION_CANCEL && !taskId.isNullOrBlank()) {
            DownloadManager.getInstance(applicationContext).cancelDownload(taskId)
            stopForegroundIfIdle()
            return START_NOT_STICKY
        }

        val title = intent?.getStringExtra(EXTRA_TITLE) ?: getString(R.string.app_name)
        val progress = intent?.getIntExtra(EXTRA_PROGRESS, 0) ?: 0
        val isFinished = intent?.getBooleanExtra(EXTRA_FINISHED, false) ?: false

        if (isFinished) {
            val completedNotification = buildCompletedNotification(title)
            notificationManager.notify(NOTIFICATION_ID, completedNotification)
            stopForeground(STOP_FOREGROUND_DETACH)
            stopSelf()
        } else {
            val notification = buildProgressNotification(title, progress, taskId)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        }

        return START_NOT_STICKY
    }

    private fun stopForegroundIfIdle() {
        if (!DownloadManager.getInstance(applicationContext).hasActiveDownloads()) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Video download progress"
                setShowBadge(false)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun buildProgressNotification(title: String, progress: Int, taskId: String?): android.app.Notification {
        val contentIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val cancelIntent = Intent(this, DownloadForegroundService::class.java).apply {
            action = ACTION_CANCEL
            putExtra(EXTRA_TASK_ID, taskId)
        }
        val cancelPendingIntent = PendingIntent.getService(
            this, 1, cancelIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(title)
            .setContentText("${getString(R.string.status_downloading)} $progress%")
            .setProgress(100, progress, progress == 0)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, getString(R.string.btn_cancel), cancelPendingIntent)
            .build()
    }

    private fun buildCompletedNotification(title: String): android.app.Notification {
        val contentIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle(title)
            .setContentText(getString(R.string.notification_completed))
            .setProgress(0, 0, false)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val CHANNEL_ID = "download_videos_channel"
        const val NOTIFICATION_ID = 1001
        const val ACTION_CANCEL = "com.example.service.ACTION_CANCEL"
        const val EXTRA_TASK_ID = "extra_task_id"
        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_PROGRESS = "extra_progress"
        const val EXTRA_FINISHED = "extra_finished"

        fun startOrUpdate(context: Context, taskId: String, title: String, progress: Int) {
            val intent = Intent(context, DownloadForegroundService::class.java).apply {
                putExtra(EXTRA_TASK_ID, taskId)
                putExtra(EXTRA_TITLE, title)
                putExtra(EXTRA_PROGRESS, progress)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context, title: String) {
            val intent = Intent(context, DownloadForegroundService::class.java).apply {
                putExtra(EXTRA_TITLE, title)
                putExtra(EXTRA_FINISHED, true)
            }
            context.startService(intent)
        }
    }
}
