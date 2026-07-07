package dev.mtib.localtranscribe.phone.ui

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val dateFormat = SimpleDateFormat("MMM d, yyyy · HH:mm", Locale.getDefault())

fun formatTimestamp(epochMs: Long): String = dateFormat.format(Date(epochMs))

fun formatDuration(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}
