package dev.mtib.localtranscribe.core

import android.content.Context
import dev.mtib.localtranscribe.core.asr.TranscriptionEngine
import dev.mtib.localtranscribe.core.audio.AudioSource
import dev.mtib.localtranscribe.core.session.RecordingMeta
import dev.mtib.localtranscribe.core.session.RecordingRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import dev.mtib.localtranscribe.core.audio.WavWriter
import kotlin.math.abs

/**
 * Single source of truth for an in-progress recording, shared by the phone foreground service and
 * the Android Auto car app. Both feed it an [AudioSource]; it drives the [TranscriptionEngine],
 * writes the WAV, exposes live UI state, and persists the finished session.
 */
object RecordingController {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val engine = TranscriptionEngine()

    private var repository: RecordingRepository? = null
    private var audioSource: AudioSource? = null
    private var wavWriter: WavWriter? = null
    private var currentId: String? = null
    private var startedAt: Long = 0L
    private var ticker: Job? = null

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    private val _isPreparing = MutableStateFlow(false)
    val isPreparing: StateFlow<Boolean> = _isPreparing.asStateFlow()

    private val _elapsedMs = MutableStateFlow(0L)
    val elapsedMs: StateFlow<Long> = _elapsedMs.asStateFlow()

    private val _level = MutableStateFlow(0f)
    val level: StateFlow<Float> = _level.asStateFlow()

    private val _waveform = MutableStateFlow<List<Float>>(emptyList())
    val waveform: StateFlow<List<Float>> = _waveform.asStateFlow()

    val committed: StateFlow<String> get() = engine.committed
    val partial: StateFlow<String> get() = engine.partial

    private const val WAVEFORM_BARS = 96

    val isModelLoaded: Boolean get() = engine.isLoaded

    /** Loads the ASR models (extracting bundled assets on first run). Suspends; run before recording. */
    suspend fun prepare(context: Context) = withContext(Dispatchers.Default) {
        engine.ensureLoaded(context.applicationContext)
    }

    @Synchronized
    fun start(context: Context, source: AudioSource, origin: String) {
        if (_isRecording.value || _isPreparing.value) return
        _isPreparing.value = true
        val app = context.applicationContext
        engine.ensureLoaded(app)
        this.origin = origin
        val repo = repository ?: RecordingRepository(app).also { repository = it }

        val now = System.currentTimeMillis()
        val id = repo.newId(now)
        currentId = id
        startedAt = now
        wavWriter = WavWriter(repo.audioFile(id), TranscriptionEngine.SAMPLE_RATE)
        _waveform.value = emptyList()
        _level.value = 0f
        _elapsedMs.value = 0L

        engine.begin(scope)
        audioSource = source
        _isRecording.value = true
        _isPreparing.value = false
        source.start(::onAudio)

        ticker = scope.launch {
            while (_isRecording.value) {
                _elapsedMs.value = System.currentTimeMillis() - startedAt
                delay(100)
            }
        }
    }

    private fun onAudio(samples: ShortArray, count: Int) {
        wavWriter?.write(samples, count)

        var peak = 0
        val floats = FloatArray(count)
        for (i in 0 until count) {
            val s = samples[i].toInt()
            if (abs(s) > peak) peak = abs(s)
            floats[i] = s / 32768f
        }
        val lvl = (peak / 32768f).coerceIn(0f, 1f)
        _level.value = lvl
        _waveform.value = (_waveform.value + lvl).takeLast(WAVEFORM_BARS)

        engine.submit(floats)
    }

    /** Stops capture, decodes the tail, persists the session, and returns its id (or null if none). */
    suspend fun stop(): String? {
        if (!_isRecording.value) return null
        _isRecording.value = false
        ticker?.cancel()
        audioSource?.stop()
        audioSource = null
        engine.finish()
        wavWriter?.close()
        wavWriter = null
        _level.value = 0f

        val id = currentId ?: return null
        val repo = repository ?: return null
        val transcript = engine.committed.value.trim()
        repo.save(
            RecordingMeta(
                id = id,
                createdAt = startedAt,
                durationMs = System.currentTimeMillis() - startedAt,
                sampleRate = TranscriptionEngine.SAMPLE_RATE,
                origin = origin,
            ),
            transcript,
        )
        currentId = null
        return id
    }

    private var origin: String = "phone"

    fun launchStop(onDone: (String?) -> Unit = {}) {
        scope.launch {
            val id = stop()
            onDone(id)
        }
    }
}
