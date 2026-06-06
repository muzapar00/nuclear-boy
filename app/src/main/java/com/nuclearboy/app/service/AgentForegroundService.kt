package com.nuclearboy.app.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.nuclearboy.app.MainActivity

/**
 * Foreground service that keeps the AI agent alive during background processing.
 * Started when AI begins thinking, stopped when response is ready.
 */
class AgentForegroundService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.getStringExtra("action") ?: "thinking"
        val projectName = intent?.getStringExtra("project") ?: ""
        android.util.Log.e("NuclearBoy", "[FGService] onStartCommand — action=$action, project=$projectName")

        if (action == "stop") {
            android.util.Log.e("NuclearBoy", "[FGService] onStartCommand — stopping foreground service")
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        createChannel()
        val notification = buildNotification(projectName, action)
        try {
            ServiceCompat.startForeground(
                this, NOTIFICATION_ID, notification,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
                    android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC else 0
            )
            android.util.Log.e("NuclearBoy", "[FGService] onStartCommand — foreground started, action=$action")
        } catch (e: Exception) {
            android.util.Log.e("NuclearBoy", "[FGService] onStartCommand — startForeground FAILED: ${e.message}")
            stopSelf()
            return START_NOT_STICKY
        }
        return START_NOT_STICKY
    }

    private fun buildNotification(project: String, action: String) = NotificationCompat.Builder(this, CHANNEL_ID)
        .setSmallIcon(android.R.drawable.ic_dialog_info)
        .setContentTitle(if (project.isNotEmpty()) "核弹男孩 · $project" else "核弹男孩")
        .setContentText(if (action == "ready") "回复已就绪" else "AI 正在思考…")
        .setOngoing(action == "thinking")
        .setContentIntent(if (action == "ready") openAppIntent() else null)
        .setPriority(NotificationCompat.PRIORITY_LOW)
        .build()

    private fun openAppIntent(): PendingIntent {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(CHANNEL_ID, "AI 处理状态", NotificationManager.IMPORTANCE_LOW)
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(ch)
        }
    }

    companion object {
        private const val CHANNEL_ID = "nb_agent_fg"
        private const val NOTIFICATION_ID = 4202

        fun start(context: Context, project: String? = null) {
            android.util.Log.e("NuclearBoy", "[FGService] companion start — project=$project")
            val intent = Intent(context, AgentForegroundService::class.java).apply {
                putExtra("action", "thinking")
                putExtra("project", project ?: "")
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun update(context: Context, summary: String, project: String? = null) {
            android.util.Log.e("NuclearBoy", "[FGService] companion update — summary=${summary.take(80)}, project=$project")
            val intent = Intent(context, AgentForegroundService::class.java).apply {
                putExtra("action", "ready")
                putExtra("project", project ?: "")
            }
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                android.util.Log.e("NuclearBoy", "[FGService] companion update FAILED — ${e.message}")
                // Service might already be stopped
            }
        }

        fun stop(context: Context) {
            android.util.Log.e("NuclearBoy", "[FGService] companion stop")
            val intent = Intent(context, AgentForegroundService::class.java).apply {
                putExtra("action", "stop")
            }
            context.startService(intent)
        }
    }
}
