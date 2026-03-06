package app.webcodex.codex.service

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
import app.webcodex.codex.MainActivity
import app.webcodex.codex.R

class CodexConnectionService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                createNotificationChannel()
                val notification = buildForegroundNotification(
                    title = getString(R.string.notification_connected_title),
                    text = getString(R.string.notification_connected_text)
                )
                startForeground(NOTIFICATION_ID, notification)
            }
            ACTION_STOP -> {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
            ACTION_UPDATE_WORKING -> {
                val notification = buildForegroundNotification(
                    title = getString(R.string.notification_working_title),
                    text = getString(R.string.notification_working_text)
                )
                (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                    .notify(NOTIFICATION_ID, notification)
            }
            ACTION_UPDATE_READY -> {
                val notification = buildForegroundNotification(
                    title = getString(R.string.notification_connected_title),
                    text = getString(R.string.notification_connected_text)
                )
                (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                    .notify(NOTIFICATION_ID, notification)
            }
            ACTION_TURN_COMPLETE -> {
                createNotificationChannel()
                showTurnCompleteNotification()
            }
        }
        return START_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notification_channel_description)
                setShowBadge(false)
            }
            nm.createNotificationChannel(channel)
            val completeChannel = NotificationChannel(
                CHANNEL_TURN_COMPLETE,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = getString(R.string.notification_channel_description)
            }
            nm.createNotificationChannel(completeChannel)
        }
    }

    private fun showTurnCompleteNotification() {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(this, CHANNEL_TURN_COMPLETE)
            .setContentTitle(getString(R.string.notification_turn_complete_title))
            .setContentText(getString(R.string.notification_turn_complete_text))
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .notify(NOTIFICATION_TURN_COMPLETE_ID, notification)
    }

    private fun buildForegroundNotification(title: String, text: String): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_SINGLE_TOP },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    companion object {
        const val ACTION_START = "app.webcodex.codex.ACTION_START"
        const val ACTION_STOP = "app.webcodex.codex.ACTION_STOP"
        const val ACTION_UPDATE_WORKING = "app.webcodex.codex.ACTION_UPDATE_WORKING"
        const val ACTION_UPDATE_READY = "app.webcodex.codex.ACTION_UPDATE_READY"
        const val ACTION_TURN_COMPLETE = "app.webcodex.codex.ACTION_TURN_COMPLETE"
        private const val CHANNEL_ID = "codex_connection"
        private const val CHANNEL_TURN_COMPLETE = "codex_turn_complete"
        private const val NOTIFICATION_ID = 1001
        private const val NOTIFICATION_TURN_COMPLETE_ID = 1002

        fun start(context: Context) {
            context.startForegroundService(Intent(context, CodexConnectionService::class.java).apply {
                action = ACTION_START
            })
        }

        fun stop(context: Context) {
            context.startService(Intent(context, CodexConnectionService::class.java).apply {
                action = ACTION_STOP
            })
        }

        fun updateWorking(context: Context) {
            context.startService(Intent(context, CodexConnectionService::class.java).apply {
                action = ACTION_UPDATE_WORKING
            })
        }

        fun updateReady(context: Context) {
            context.startService(Intent(context, CodexConnectionService::class.java).apply {
                action = ACTION_UPDATE_READY
            })
        }

        fun showTurnComplete(context: Context) {
            context.startService(Intent(context, CodexConnectionService::class.java).apply {
                action = ACTION_TURN_COMPLETE
            })
        }
    }
}
