package dev.mtib.localtranscribe.phone

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import dev.mtib.localtranscribe.R
import dev.mtib.localtranscribe.core.RecordingController
import dev.mtib.localtranscribe.phone.ui.formatDuration

/** Home-screen widget: mic to start when idle, red stop-square while recording. */
class RecordingWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetIds.forEach { render(context, manager, it) }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        val editor = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
        appWidgetIds.forEach { editor.remove(styleKey(it)); editor.remove(colorKey(it)) }
        editor.apply()
    }

    companion object {
        const val STYLE_CARD = "card"
        const val STYLE_ICON = "icon"
        const val DEFAULT_ICON_COLOR = 0xFF75E3BE.toInt()
        private const val PREFS = "widget_prefs"

        private fun styleKey(id: Int) = "style_$id"
        private fun colorKey(id: Int) = "color_$id"

        fun setStyle(context: Context, id: Int, style: String) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putString(styleKey(id), style).apply()
        }

        fun setIconColor(context: Context, id: Int, color: Int) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putInt(colorKey(id), color).apply()
        }

        private fun getStyle(context: Context, id: Int): String =
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(styleKey(id), STYLE_CARD) ?: STYLE_CARD

        private fun getIconColor(context: Context, id: Int): Int =
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getInt(colorKey(id), DEFAULT_ICON_COLOR)

        /** Refresh every placed widget from the current recording state. Safe from any thread. */
        fun update(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            manager.getAppWidgetIds(ComponentName(context, RecordingWidgetProvider::class.java))
                .forEach { render(context, manager, it) }
        }

        private fun render(context: Context, manager: AppWidgetManager, id: Int) {
            manager.updateAppWidget(id, buildViews(context, id))
        }

        private fun buildViews(context: Context, id: Int): RemoteViews {
            val style = getStyle(context, id)
            val recording = RecordingController.isRecording.value
            val iconRes = if (recording) R.drawable.ic_widget_stop else R.drawable.ic_shortcut_mic

            return if (style == STYLE_ICON) {
                RemoteViews(context.packageName, R.layout.widget_recording_icon).apply {
                    setImageViewResource(R.id.widget_icon, iconRes)
                    // Tint the idle mic with the chosen color; keep the stop glyph its native red.
                    if (!recording) setInt(R.id.widget_icon, "setColorFilter", getIconColor(context, id))
                    setOnClickPendingIntent(R.id.widget_root, actionIntent(context, recording))
                }
            } else {
                RemoteViews(context.packageName, R.layout.widget_recording).apply {
                    setImageViewResource(R.id.widget_icon, iconRes)
                    setInt(
                        R.id.widget_root, "setBackgroundResource",
                        if (recording) R.drawable.widget_bg_recording else R.drawable.widget_bg,
                    )
                    setTextViewText(R.id.widget_label, label(context, recording))
                    if (recording) {
                        setViewVisibility(R.id.widget_duration, View.VISIBLE)
                        setTextViewText(R.id.widget_duration, formatDuration(RecordingController.elapsedMs.value))
                        setViewVisibility(R.id.widget_sub, View.VISIBLE)
                        setTextViewText(R.id.widget_sub, snippet())
                    } else {
                        setViewVisibility(R.id.widget_duration, View.GONE)
                        setViewVisibility(R.id.widget_sub, View.GONE)
                    }
                    setOnClickPendingIntent(R.id.widget_root, actionIntent(context, recording))
                }
            }
        }

        private fun label(context: Context, recording: Boolean): String = when {
            recording -> "Recording"
            RecordingController.isPreparing.value -> "Loading model…"
            RecordingController.isFinalizing.value -> "Finalizing…"
            else -> context.getString(R.string.widget_idle_label)
        }

        private fun snippet(): String {
            val committed = RecordingController.committed.value.trim()
            val pending = RecordingController.pending.value
            return when {
                committed.isNotEmpty() -> committed.takeLast(60) + if (pending) " …" else ""
                pending -> "Listening…"
                else -> "…"
            }
        }

        private fun actionIntent(context: Context, recording: Boolean): PendingIntent =
            if (recording) {
                PendingIntent.getService(
                    context, 101,
                    Intent(context, RecordingService::class.java).setAction(RecordingService.ACTION_STOP),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                )
            } else {
                PendingIntent.getActivity(
                    context, 102,
                    Intent(context, MainActivity::class.java)
                        .setAction(MainActivity.ACTION_NEW_RECORDING)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                )
            }
    }
}
