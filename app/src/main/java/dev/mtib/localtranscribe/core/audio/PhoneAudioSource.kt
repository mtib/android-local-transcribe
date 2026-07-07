package dev.mtib.localtranscribe.core.audio

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlin.math.max

/** Captures the phone microphone via [AudioRecord] on a background thread. */
class PhoneAudioSource(private val sampleRate: Int = 16000) : AudioSource {
    private var record: AudioRecord? = null
    private var thread: Thread? = null
    @Volatile private var running = false

    @SuppressLint("MissingPermission")
    override fun start(onAudio: (ShortArray, Int) -> Unit) {
        val minBuffer = AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        val bufferSize = max(minBuffer, sampleRate / 5 * 2)
        val rec = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufferSize,
        )
        check(rec.state == AudioRecord.STATE_INITIALIZED) { "AudioRecord failed to initialize" }
        record = rec
        rec.startRecording()
        running = true
        thread = Thread({
            val buffer = ShortArray(sampleRate / 10) // ~100 ms
            while (running) {
                val n = rec.read(buffer, 0, buffer.size)
                if (n > 0) onAudio(buffer, n)
            }
        }, "PhoneAudioSource").also { it.start() }
    }

    override fun stop() {
        running = false
        thread?.join(1000)
        thread = null
        record?.run {
            runCatching { stop() }
            release()
        }
        record = null
    }
}
