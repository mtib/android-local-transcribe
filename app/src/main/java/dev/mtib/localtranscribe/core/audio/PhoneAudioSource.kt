package dev.mtib.localtranscribe.core.audio

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlin.math.max

/**
 * Captures the phone microphone via [AudioRecord] on a background thread.
 *
 * Uses the `VOICE_RECOGNITION` source and pins input to the built-in mic so that a connected car /
 * Bluetooth headset does not hijack or degrade capture (e.g. routing through a low-quality SCO mic).
 */
class PhoneAudioSource(
    private val context: Context,
    private val sampleRate: Int = 16000,
) : AudioSource {
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
        val rec = createRecord(bufferSize)
        check(rec.state == AudioRecord.STATE_INITIALIZED) { "AudioRecord failed to initialize" }
        preferBuiltInMic(rec)
        record = rec
        rec.startRecording()
        running = true
        thread = Thread({
            val buffer = ShortArray(sampleRate / 10) // ~100 ms
            while (running) {
                val n = rec.read(buffer, 0, buffer.size)
                when {
                    n > 0 -> onAudio(buffer, n)
                    n < 0 -> break // ERROR_DEAD_OBJECT / mic lost — end capture cleanly
                }
            }
        }, "PhoneAudioSource").also { it.start() }
    }

    @SuppressLint("MissingPermission")
    private fun createRecord(bufferSize: Int): AudioRecord {
        val voice = AudioRecord(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufferSize,
        )
        if (voice.state == AudioRecord.STATE_INITIALIZED) return voice
        voice.release()
        return AudioRecord(
            MediaRecorder.AudioSource.MIC,
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufferSize,
        )
    }

    private fun preferBuiltInMic(rec: AudioRecord) {
        val manager = context.getSystemService(AudioManager::class.java) ?: return
        val builtIn = manager.getDevices(AudioManager.GET_DEVICES_INPUTS)
            .firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_MIC }
        if (builtIn != null) runCatching { rec.setPreferredDevice(builtIn) }
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
