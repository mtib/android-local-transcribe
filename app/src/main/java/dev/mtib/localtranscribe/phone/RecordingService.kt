package dev.mtib.localtranscribe.phone

import android.app.Notification
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import dev.mtib.localtranscribe.LocalTranscribeApp
import dev.mtib.localtranscribe.R
import dev.mtib.localtranscribe.core.RecordingController
import dev.mtib.localtranscribe.core.audio.PhoneAudioSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Hosts phone-microphone recording as a foreground service so capture survives screen-off. */
class RecordingService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                startForegroundCompat(buildNotification())
                scope.launch {
                    RecordingController.start(this@RecordingService, PhoneAudioSource(), "phone")
                }
            }
            ACTION_STOP -> {
                RecordingController.launchStop {
                    stopForegroundCompat()
                    stopSelf()
                }
            }
        }
        return START_NOT_STICKY
    }

    private fun buildNotification(): Notification =
        NotificationCompat.Builder(this, LocalTranscribeApp.RECORDING_CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText("Recording — audio stays on this device")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(true)
            .build()

    private fun startForegroundCompat(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        } else {
            startForeground(NOTIF_ID, notification)
        }
    }

    private fun stopForegroundCompat() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION") stopForeground(true)
        }
    }

    companion object {
        private const val NOTIF_ID = 1
        const val ACTION_START = "dev.mtib.localtranscribe.START"
        const val ACTION_STOP = "dev.mtib.localtranscribe.STOP"

        fun start(context: Context) {
            val intent = Intent(context, RecordingService::class.java).setAction(ACTION_START)
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, RecordingService::class.java).setAction(ACTION_STOP)
            context.startService(intent)
        }
    }
}
