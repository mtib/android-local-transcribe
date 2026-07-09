package dev.mtib.localtranscribe.phone

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import dev.mtib.localtranscribe.LocalTranscribeApp
import dev.mtib.localtranscribe.R
import dev.mtib.localtranscribe.core.RecordingController
import dev.mtib.localtranscribe.core.audio.PhoneAudioSource
import dev.mtib.localtranscribe.phone.ui.formatDuration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Hosts microphone recording as a foreground service so capture survives screen-off or another app
 * (e.g. a projected car UI) taking over. The notification shows live status and a Stop action so a
 * recording can be monitored and stopped without returning to the app.
 */
class RecordingService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startRecording()
            ACTION_STOP -> stopRecording()
        }
        return START_NOT_STICKY
    }

    private fun startRecording() {
        startForegroundCompat(buildNotification("Starting…"))
        acquireWakeLock()
        scope.launch {
            RecordingController.start(this@RecordingService, PhoneAudioSource(applicationContext), "phone")
        }
        scope.launch {
            val manager = getSystemService(NotificationManager::class.java)
            while (true) {
                manager.notify(NOTIF_ID, buildNotification(statusText()))
                RecordingWidgetProvider.update(this@RecordingService)
                delay(1000)
            }
        }
    }

    private fun statusText(): String {
        if (RecordingController.isPreparing.value) return "Loading model…"
        val elapsed = formatDuration(RecordingController.elapsedMs.value)
        val committed = RecordingController.committed.value
        val partial = RecordingController.partial.value
        val tail = (committed + if (partial.isBlank()) "" else " $partial").trim()
        val snippet = if (tail.isBlank()) "Listening…" else tail.takeLast(80)
        return "$elapsed  •  $snippet"
    }

    private fun stopRecording() {
        RecordingController.launchStop {
            releaseWakeLock()
            RecordingWidgetProvider.update(this)
            stopForegroundCompat()
            stopSelf()
        }
    }

    private fun buildNotification(text: String): Notification {
        val contentIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_OPEN_ACTIVE, true)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val stopIntent = PendingIntent.getService(
            this, 1,
            Intent(this, RecordingService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, LocalTranscribeApp.RECORDING_CHANNEL_ID)
            .setContentTitle("Recording")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(contentIntent)
            .addAction(0, "Stop", stopIntent)
            .build()
    }

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        val pm = getSystemService(PowerManager::class.java)
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "LocalTranscribe::recording").apply {
            setReferenceCounted(false)
            acquire(MAX_WAKELOCK_MS)
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
    }

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

    override fun onDestroy() {
        releaseWakeLock()
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val NOTIF_ID = 1
        private const val MAX_WAKELOCK_MS = 6L * 60 * 60 * 1000
        const val ACTION_START = "dev.mtib.localtranscribe.START"
        const val ACTION_STOP = "dev.mtib.localtranscribe.STOP"

        fun start(context: Context) {
            context.startForegroundService(
                Intent(context, RecordingService::class.java).setAction(ACTION_START),
            )
        }

        fun stop(context: Context) {
            context.startService(
                Intent(context, RecordingService::class.java).setAction(ACTION_STOP),
            )
        }
    }
}
