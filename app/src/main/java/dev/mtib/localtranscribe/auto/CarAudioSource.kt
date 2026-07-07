package dev.mtib.localtranscribe.auto

import androidx.car.app.CarContext
import androidx.car.app.media.CarAudioRecord
import dev.mtib.localtranscribe.core.audio.AudioSource
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Captures the vehicle microphone via the Car App Library's [CarAudioRecord] (16 kHz mono PCM16). */
class CarAudioSource(private val carContext: CarContext) : AudioSource {
    private var record: CarAudioRecord? = null
    private var thread: Thread? = null
    @Volatile private var running = false

    override fun start(onAudio: (ShortArray, Int) -> Unit) {
        val rec = CarAudioRecord.create(carContext)
        record = rec
        rec.startRecording()
        running = true
        thread = Thread({
            val bytes = ByteArray(CarAudioRecord.AUDIO_CONTENT_BUFFER_SIZE)
            val shorts = ShortArray(bytes.size / 2)
            while (running) {
                val n = rec.read(bytes, 0, bytes.size)
                if (n < 0) break
                if (n == 0) continue
                val sampleCount = n / 2
                ByteBuffer.wrap(bytes, 0, n).order(ByteOrder.LITTLE_ENDIAN)
                    .asShortBuffer().get(shorts, 0, sampleCount)
                onAudio(shorts, sampleCount)
            }
        }, "CarAudioSource").also { it.start() }
    }

    override fun stop() {
        running = false
        thread?.join(1000)
        thread = null
        record?.runCatching { stopRecording() }
        record = null
    }
}
