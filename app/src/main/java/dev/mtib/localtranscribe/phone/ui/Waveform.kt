package dev.mtib.localtranscribe.phone.ui

import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap

/** Simple voice-activity waveform: one vertical bar per recent amplitude sample. */
@Composable
fun Waveform(levels: List<Float>, modifier: Modifier = Modifier) {
    val color = MaterialTheme.colorScheme.primary
    Canvas(modifier = modifier) {
        if (levels.isEmpty()) return@Canvas
        val n = levels.size
        val gap = 3f
        val barWidth = ((size.width - gap * (n - 1)) / n).coerceAtLeast(1f)
        val midY = size.height / 2f
        levels.forEachIndexed { i, level ->
            val h = (level.coerceIn(0f, 1f) * size.height).coerceAtLeast(2f)
            val x = i * (barWidth + gap) + barWidth / 2f
            drawLine(
                color = color,
                start = Offset(x, midY - h / 2f),
                end = Offset(x, midY + h / 2f),
                strokeWidth = barWidth,
                cap = StrokeCap.Round,
            )
        }
    }
}
