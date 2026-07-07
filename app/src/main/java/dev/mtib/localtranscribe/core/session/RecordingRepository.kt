package dev.mtib.localtranscribe.core.session

import android.content.Context
import kotlinx.serialization.json.Json
import java.io.File

/** File-backed store of recordings under filesDir/recordings/<id>/{audio.wav,transcript.txt,meta.json}. */
class RecordingRepository(context: Context) {
    private val root = File(context.filesDir, "recordings").apply { mkdirs() }
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    fun sessionDir(id: String): File = File(root, id).apply { mkdirs() }
    fun audioFile(id: String): File = File(sessionDir(id), "audio.wav")
    fun transcriptFile(id: String): File = File(sessionDir(id), "transcript.txt")
    private fun metaFile(id: String): File = File(sessionDir(id), "meta.json")

    fun newId(now: Long): String = "rec-$now"

    fun save(meta: RecordingMeta, transcript: String) {
        metaFile(meta.id).writeText(json.encodeToString(RecordingMeta.serializer(), meta))
        transcriptFile(meta.id).writeText(transcript)
    }

    fun get(id: String): RecordingSession? {
        val metaFile = metaFile(id)
        if (!metaFile.exists()) return null
        val meta = runCatching {
            json.decodeFromString(RecordingMeta.serializer(), metaFile.readText())
        }.getOrNull() ?: return null
        val transcript = transcriptFile(id).let { if (it.exists()) it.readText() else "" }
        return RecordingSession(meta, transcript)
    }

    fun list(): List<RecordingSession> =
        (root.listFiles { f -> f.isDirectory }?.toList() ?: emptyList())
            .mapNotNull { get(it.name) }
            .sortedByDescending { it.meta.createdAt }

    fun delete(id: String) {
        sessionDir(id).deleteRecursively()
    }
}
