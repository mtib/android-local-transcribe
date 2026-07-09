package dev.mtib.localtranscribe.core.asr

import android.content.Context
import dev.mtib.localtranscribe.BuildConfig
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.OfflineTransducerModelConfig
import com.k2fsa.sherpa.onnx.SileroVadModelConfig
import com.k2fsa.sherpa.onnx.Vad
import com.k2fsa.sherpa.onnx.VadModelConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * On-device speech-to-text: Parakeet-TDT v3 ([OfflineRecognizer]) segmented by Silero [Vad].
 * Audio capture calls [submit] from any thread; a dedicated coroutine runs VAD + decoding so
 * inference never blocks the microphone. Each speech segment is decoded once, on finalize, into
 * [committed]. [pending] is true while speech is being captured but not yet committed, so the UI
 * can show an unobtrusive indicator instead of a churning live preview.
 */
class TranscriptionEngine {
    private var recognizer: OfflineRecognizer? = null
    private var vad: Vad? = null

    private val _committed = MutableStateFlow("")
    val committed: StateFlow<String> = _committed.asStateFlow()

    private val _pending = MutableStateFlow(false)
    val pending: StateFlow<Boolean> = _pending.asStateFlow()

    private var channel: Channel<FloatArray>? = null
    private var job: Job? = null

    val isLoaded: Boolean get() = recognizer != null

    /** Loads the models from disk (extracting bundled assets on first call). Heavy; call off the UI thread. */
    @Synchronized
    fun ensureLoaded(context: Context) {
        if (recognizer != null) return
        val m = ModelInstaller.ensureInstalled(context)
        val threads = Runtime.getRuntime().availableProcessors().coerceIn(2, 4)
        recognizer = OfflineRecognizer(
            config = OfflineRecognizerConfig(
                featConfig = FeatureConfig(sampleRate = SAMPLE_RATE, featureDim = 80),
                modelConfig = OfflineModelConfig(
                    transducer = OfflineTransducerModelConfig(
                        encoder = m.parakeetEncoder,
                        decoder = m.parakeetDecoder,
                        joiner = m.parakeetJoiner,
                    ),
                    tokens = m.parakeetTokens,
                    modelType = "nemo_transducer",
                    numThreads = threads,
                    provider = BuildConfig.ASR_PROVIDER,
                ),
                decodingMethod = "greedy_search",
            ),
        )
        vad = Vad(
            config = VadModelConfig(
                sileroVadModelConfig = SileroVadModelConfig(
                    model = m.vad,
                    threshold = 0.5f,
                    minSilenceDuration = 0.25f,
                    minSpeechDuration = 0.25f,
                    windowSize = WINDOW_SIZE,
                    maxSpeechDuration = 5.0f,
                ),
                sampleRate = SAMPLE_RATE,
                numThreads = 1,
            ),
        )
    }

    /** Starts a fresh transcription session; launches the inference consumer on [scope]. */
    fun begin(scope: CoroutineScope) {
        val v = vad ?: error("Engine not loaded")
        v.reset()
        v.clear()
        _committed.value = ""
        _pending.value = false
        val ch = Channel<FloatArray>(capacity = Channel.UNLIMITED)
        channel = ch
        job = scope.launch(Dispatchers.Default) { consume(ch) }
    }

    /** Feeds 16 kHz mono float samples [-1, 1]. Safe to call from the audio thread. */
    fun submit(samples: FloatArray) {
        channel?.trySend(samples)
    }

    /** Signals end of audio and waits for the tail to be decoded. */
    suspend fun finish() {
        channel?.close()
        job?.join()
        channel = null
        job = null
    }

    private suspend fun consume(ch: Channel<FloatArray>) {
        val v = vad ?: return
        var carry = FloatArray(0)

        fun process(block: FloatArray) {
            val combined = if (carry.isEmpty()) block else carry + block
            var offset = 0
            while (offset + WINDOW_SIZE <= combined.size) {
                v.acceptWaveform(combined.copyOfRange(offset, offset + WINDOW_SIZE))
                offset += WINDOW_SIZE
            }
            carry = if (offset < combined.size) combined.copyOfRange(offset, combined.size) else FloatArray(0)
            drainSegments(v)
            _pending.value = v.isSpeechDetected()
        }

        for (block in ch) {
            process(block)
            while (true) {
                val more = ch.tryReceive().getOrNull() ?: break
                process(more)
            }
        }

        if (carry.isNotEmpty()) {
            v.acceptWaveform(carry + FloatArray(WINDOW_SIZE - carry.size))
        }
        v.flush()
        drainSegments(v)
        _pending.value = false
    }

    private fun drainSegments(v: Vad) {
        while (!v.empty()) {
            val segment = v.front()
            v.pop()
            val text = decode(segment.samples).trim()
            if (text.isNotEmpty()) {
                val current = _committed.value
                _committed.value = if (current.isEmpty()) text else "$current $text"
            }
        }
    }

    /** One-shot decode of a whole clip (16 kHz mono). Used by tests and any batch path. */
    fun transcribeSamples(samples: FloatArray): String = decode(samples)

    private fun decode(samples: FloatArray): String {
        if (samples.isEmpty()) return ""
        val r = recognizer ?: return ""
        val stream = r.createStream()
        return try {
            stream.acceptWaveform(samples, SAMPLE_RATE)
            r.decode(stream)
            r.getResult(stream).text
        } finally {
            stream.release()
        }
    }

    fun release() {
        recognizer?.release()
        vad?.release()
        recognizer = null
        vad = null
    }

    companion object {
        const val SAMPLE_RATE = 16000
        const val WINDOW_SIZE = 512
    }
}
