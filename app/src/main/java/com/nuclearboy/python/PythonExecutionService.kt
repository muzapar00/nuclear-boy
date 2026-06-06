package com.nuclearboy.python

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.nuclearboy.app.MainActivity

/**
 * Foreground service for long-running Python tasks.
 * Keeps the process alive when executing scripts that take > 10 seconds.
 */
class PythonExecutionService : Service() {

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val taskName = intent?.getStringExtra(EXTRA_TASK_NAME) ?: "正在执行任务…"

        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("☢️ NUCLEAR BOY")
            .setContentText(taskName)
            .setSmallIcon(android.R.drawable.ic_menu_manage)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        startForeground(NOTIFICATION_ID, notification)

        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Python 执行",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "长时间 Python 任务执行通知"
                setShowBadge(false)
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    /**
     * Start the foreground service with a descriptive task name.
     */
    companion object {
        private const val CHANNEL_ID = "python_execution_channel"
        private const val NOTIFICATION_ID = 1001
        private const val EXTRA_TASK_NAME = "task_name"

        fun start(context: Context, taskName: String) {
            val intent = Intent(context, PythonExecutionService::class.java).apply {
                putExtra(EXTRA_TASK_NAME, taskName)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, PythonExecutionService::class.java))
        }
    }
}
