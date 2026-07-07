package dev.mtib.localtranscribe.core.export

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import dev.mtib.localtranscribe.core.session.RecordingRepository
import dev.mtib.localtranscribe.core.session.RecordingSession
import java.io.File

/** Builds Android share-sheet intents for a recording's audio and/or transcript. */
object ShareHelper {

    enum class Mode { TEXT, AUDIO, BOTH }

    fun shareIntent(context: Context, repo: RecordingRepository, session: RecordingSession, mode: Mode): Intent {
        val authority = "${context.packageName}.fileprovider"
        val uris = ArrayList<Uri>()
        if (mode == Mode.AUDIO || mode == Mode.BOTH) {
            val audio = repo.audioFile(session.id)
            if (audio.exists()) uris.add(FileProvider.getUriForFile(context, authority, audio))
        }
        if (mode == Mode.TEXT || mode == Mode.BOTH) {
            val txt = repo.transcriptFile(session.id)
            if (txt.exists()) uris.add(FileProvider.getUriForFile(context, authority, txt))
        }

        val base = if (uris.size > 1) Intent(Intent.ACTION_SEND_MULTIPLE) else Intent(Intent.ACTION_SEND)
        base.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        when (mode) {
            Mode.TEXT -> {
                base.type = "text/plain"
                base.putExtra(Intent.EXTRA_TEXT, session.transcript)
                if (uris.isNotEmpty()) base.putExtra(Intent.EXTRA_STREAM, uris.first())
            }
            Mode.AUDIO -> {
                base.type = "audio/wav"
                if (uris.isNotEmpty()) base.putExtra(Intent.EXTRA_STREAM, uris.first())
            }
            Mode.BOTH -> {
                base.type = "*/*"
                base.putExtra(Intent.EXTRA_TEXT, session.transcript)
                if (uris.size > 1) {
                    base.putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
                } else if (uris.size == 1) {
                    base.putExtra(Intent.EXTRA_STREAM, uris.first())
                }
            }
        }
        return Intent.createChooser(base, "Share recording")
    }
}
