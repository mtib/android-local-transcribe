package dev.mtib.localtranscribe.core.audio

/**
 * A microphone abstraction. Implementations deliver 16 kHz mono PCM16 frames via [onAudio];
 * the passed array may be reused, so callers must consume it synchronously.
 */
interface AudioSource {
    fun start(onAudio: (samples: ShortArray, count: Int) -> Unit)
    fun stop()
}
