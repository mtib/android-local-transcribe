package dev.mtib.localtranscribe.phone

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.mtib.localtranscribe.R
import dev.mtib.localtranscribe.phone.ui.LocalTranscribeTheme

/** Shown when a Recording widget is added / reconfigured: pick Card, or Icon-only + a color. */
class RecordingWidgetConfigActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setResult(RESULT_CANCELED)

        val id = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID,
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID
        if (id == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        fun done() {
            RecordingWidgetProvider.update(this)
            setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id))
            finish()
        }

        setContent {
            LocalTranscribeTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    ConfigScreen(
                        onCard = {
                            RecordingWidgetProvider.setStyle(this, id, RecordingWidgetProvider.STYLE_CARD)
                            done()
                        },
                        onIcon = { color ->
                            RecordingWidgetProvider.setStyle(this, id, RecordingWidgetProvider.STYLE_ICON)
                            RecordingWidgetProvider.setIconColor(this, id, color)
                            done()
                        },
                    )
                }
            }
        }
    }
}

private val ICON_COLORS = listOf(
    0xFF75E3BE, // mint
    0xFFFFFFFF, // white
    0xFFAFA2FF, // violet
    0xFFFFC857, // amber
    0xFF6EC1E4, // sky
    0xFFFF6B6B, // coral
)

@Composable
private fun ConfigScreen(onCard: () -> Unit, onIcon: (Int) -> Unit) {
    Column(
        Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.systemBars).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(stringResource(R.string.widget_style_title), style = MaterialTheme.typography.headlineSmall)

        Button(onClick = onCard, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.widget_style_card))
        }

        Text(stringResource(R.string.widget_style_icon_color), style = MaterialTheme.typography.bodyMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            ICON_COLORS.forEach { argb ->
                ColorSwatch(color = Color(argb.toInt()), onClick = { onIcon(argb.toInt()) })
            }
        }
    }
}

@Composable
private fun ColorSwatch(color: Color, onClick: () -> Unit) {
    androidx.compose.foundation.layout.Box(
        Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(color)
            .clickable(onClick = onClick),
    )
}
