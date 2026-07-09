package dev.mtib.localtranscribe.phone

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.systemBars
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.mtib.localtranscribe.R
import dev.mtib.localtranscribe.phone.ui.LocalTranscribeTheme

/** Shown when a Recording widget is added (or reconfigured): pick Card or Icon-only style. */
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

        setContent {
            LocalTranscribeTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    ConfigScreen { style ->
                        RecordingWidgetProvider.setStyle(this, id, style)
                        RecordingWidgetProvider.update(this)
                        setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id))
                        finish()
                    }
                }
            }
        }
    }
}

@Composable
private fun ConfigScreen(onPick: (String) -> Unit) {
    Column(
        Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.systemBars).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(stringResource(R.string.widget_style_title), style = MaterialTheme.typography.headlineSmall)
        Button(onClick = { onPick(RecordingWidgetProvider.STYLE_CARD) }, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.widget_style_card))
        }
        OutlinedButton(onClick = { onPick(RecordingWidgetProvider.STYLE_ICON) }, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.widget_style_icon))
        }
    }
}
