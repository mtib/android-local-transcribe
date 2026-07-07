package dev.mtib.localtranscribe.core.session

import kotlinx.serialization.Serializable

@Serializable
data class RecordingMeta(
    val id: String,
    val createdAt: Long,
    val durationMs: Long,
    val sampleRate: Int,
    val origin: String,
)

/** A recording on disk: metadata plus the (lazily loaded) transcript text. */
data class RecordingSession(
    val meta: RecordingMeta,
    val transcript: String,
) {
    val id: String get() = meta.id
}
