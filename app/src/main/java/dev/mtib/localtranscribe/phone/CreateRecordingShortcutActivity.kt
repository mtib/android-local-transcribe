package dev.mtib.localtranscribe.phone

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import dev.mtib.localtranscribe.R

/**
 * Legacy `CREATE_SHORTCUT` entry so "New recording" shows up in launchers' widget/shortcut picker.
 * Returns a pinnable shortcut that launches straight into a recording.
 */
class CreateRecordingShortcutActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (intent?.action == Intent.ACTION_CREATE_SHORTCUT) {
            val launch = Intent(this, MainActivity::class.java)
                .setAction(MainActivity.ACTION_NEW_RECORDING)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            val shortcut = ShortcutInfoCompat.Builder(this, "new_recording")
                .setShortLabel(getString(R.string.shortcut_new_long))
                .setLongLabel(getString(R.string.shortcut_new_long))
                .setIcon(IconCompat.createWithResource(this, R.drawable.ic_shortcut_mic))
                .setIntent(launch)
                .build()
            setResult(RESULT_OK, ShortcutManagerCompat.createShortcutResultIntent(this, shortcut))
        } else {
            setResult(RESULT_CANCELED)
        }
        finish()
    }
}
