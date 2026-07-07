package dev.mtib.localtranscribe

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.k2fsa.sherpa.onnx.WaveReader
import dev.mtib.localtranscribe.core.asr.TranscriptionEngine
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Proves the bundled Parakeet model transcribes on-device with no network. */
@RunWith(AndroidJUnit4::class)
class TranscriptionEngineTest {

    @Test
    fun transcribesBundledEnglishClip() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val engine = TranscriptionEngine()
        engine.ensureLoaded(context)

        val wave = WaveReader.readWave(context.assets, "models/parakeet/test_wavs/en.wav")
        val text = engine.transcribeSamples(wave.samples).trim()

        assertTrue("Expected a non-empty transcript, got: '$text'", text.isNotEmpty())
        engine.release()
    }
}
