package dev.mtib.localtranscribe.auto

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.view.Surface
import androidx.car.app.AppManager
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.SurfaceCallback
import androidx.car.app.SurfaceContainer
import androidx.car.app.model.Action
import androidx.car.app.model.ActionStrip
import androidx.car.app.model.Template
import androidx.car.app.navigation.model.NavigationTemplate
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import dev.mtib.localtranscribe.core.RecordingController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Android Auto screen. A [NavigationTemplate] gives us a drawing [Surface] (via
 * [SurfaceCallback]) on which we render the live voice-activity waveform and scrolling transcript,
 * plus a single Start/Stop action. History browsing is intentionally omitted for the car.
 */
class TranscribeScreen(carContext: CarContext) : Screen(carContext), SurfaceCallback {

    private var surface: Surface? = null
    private var visibleArea = Rect()
    private var renderJob: Job? = null

    private val bgPaint = Paint().apply { color = 0xFF091013.toInt() }
    private val wavePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF75E3BE.toInt()
        strokeCap = Paint.Cap.ROUND
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFE6ECF2.toInt()
        textSize = 38f
    }
    private val partialPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFAFA2FF.toInt()
        textSize = 38f
    }
    private val hintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFA8B3BF.toInt()
        textSize = 34f
    }

    init {
        carContext.getCarService(AppManager::class.java).setSurfaceCallback(this)
        lifecycleScope.launch { RecordingController.isRecording.collect { invalidate() } }
        lifecycleScope.launch { RecordingController.isPreparing.collect { invalidate() } }
    }

    override fun onGetTemplate(): Template {
        val recording = RecordingController.isRecording.value
        val preparing = RecordingController.isPreparing.value
        val title = when {
            preparing -> "Loading…"
            recording -> "Stop"
            else -> "Start"
        }
        val action = Action.Builder()
            .setTitle(title)
            .setOnClickListener { toggle() }
            .build()
        return NavigationTemplate.Builder()
            .setActionStrip(ActionStrip.Builder().addAction(action).build())
            .build()
    }

    private fun toggle() {
        if (RecordingController.isRecording.value || RecordingController.isPreparing.value) {
            RecordingController.launchStop()
        } else {
            ensureMicThenStart()
        }
    }

    private fun ensureMicThenStart() {
        val granted = ContextCompat.checkSelfPermission(
            carContext, Manifest.permission.RECORD_AUDIO,
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) {
            startRecording()
        } else {
            carContext.requestPermissions(listOf(Manifest.permission.RECORD_AUDIO)) { grantedList, _ ->
                if (Manifest.permission.RECORD_AUDIO in grantedList) startRecording()
            }
        }
    }

    private fun startRecording() {
        lifecycleScope.launch(Dispatchers.Default) {
            runCatching { RecordingController.start(carContext, CarAudioSource(carContext), "auto") }
        }
    }

    override fun onSurfaceAvailable(surfaceContainer: SurfaceContainer) {
        surface = surfaceContainer.surface
        startRenderLoop()
    }

    override fun onVisibleAreaChanged(visibleArea: Rect) {
        this.visibleArea = visibleArea
    }

    override fun onStableAreaChanged(stableArea: Rect) {
        if (visibleArea.isEmpty) visibleArea = stableArea
    }

    override fun onSurfaceDestroyed(surfaceContainer: SurfaceContainer) {
        renderJob?.cancel()
        renderJob = null
        surface = null
    }

    private fun startRenderLoop() {
        renderJob?.cancel()
        renderJob = lifecycleScope.launch(Dispatchers.Default) {
            while (isActive) {
                drawFrame()
                delay(80)
            }
        }
    }

    private fun drawFrame() {
        val s = surface ?: return
        val canvas: Canvas = try {
            s.lockCanvas(null)
        } catch (e: Exception) {
            return
        } ?: return
        try {
            val w = canvas.width.toFloat()
            val h = canvas.height.toFloat()
            canvas.drawRect(0f, 0f, w, h, bgPaint)

            val padL = (if (visibleArea.left > 0) visibleArea.left else 0) + 24f
            val padT = (if (visibleArea.top > 0) visibleArea.top else 0) + 20f
            val padR = 24f
            val contentW = w - padL - padR

            val waveH = (h * 0.32f)
            drawWaveform(canvas, RecordingController.waveform.value, padL, padT, contentW, waveH)

            val recording = RecordingController.isRecording.value
            val preparing = RecordingController.isPreparing.value
            val committed = RecordingController.committed.value
            val partial = RecordingController.partial.value

            val textTop = padT + waveH + 24f
            if (!recording && !preparing && committed.isEmpty()) {
                canvas.drawText("Tap Start to transcribe", padL, textTop + 40f, hintPaint)
            } else {
                drawTranscript(canvas, committed, partial, padL, textTop, contentW, h - textTop - 20f)
            }
        } finally {
            runCatching { s.unlockCanvasAndPost(canvas) }
        }
    }

    private fun drawWaveform(canvas: Canvas, levels: List<Float>, x: Float, y: Float, w: Float, h: Float) {
        if (levels.isEmpty()) return
        val n = levels.size
        val gap = 4f
        val barWidth = ((w - gap * (n - 1)) / n).coerceAtLeast(2f)
        wavePaint.strokeWidth = barWidth
        val midY = y + h / 2f
        levels.forEachIndexed { i, level ->
            val barH = (level.coerceIn(0f, 1f) * h).coerceAtLeast(3f)
            val bx = x + i * (barWidth + gap) + barWidth / 2f
            canvas.drawLine(bx, midY - barH / 2f, bx, midY + barH / 2f, wavePaint)
        }
    }

    private fun drawTranscript(
        canvas: Canvas,
        committed: String,
        partial: String,
        x: Float,
        y: Float,
        w: Float,
        h: Float,
    ) {
        val lineHeight = 48f
        val maxLines = (h / lineHeight).toInt().coerceAtLeast(1)
        val full = buildString {
            append(committed)
            if (partial.isNotBlank()) {
                if (isNotEmpty()) append(' ')
                append(partial)
            }
        }
        val lines = wrap(full, textPaint, w)
        val visible = if (lines.size > maxLines) lines.subList(lines.size - maxLines, lines.size) else lines
        var ty = y + lineHeight
        val partialStart = committed.length
        var cursor = 0
        for (line in visible) {
            // Colour a line violet if it belongs entirely to the partial tail.
            val paint = if (cursor >= partialStart && partial.isNotBlank()) partialPaint else textPaint
            canvas.drawText(line, x, ty, paint)
            cursor += line.length + 1
            ty += lineHeight
        }
    }

    private fun wrap(text: String, paint: Paint, maxWidth: Float): List<String> {
        if (text.isBlank()) return emptyList()
        val words = text.split(' ')
        val lines = ArrayList<String>()
        val current = StringBuilder()
        for (word in words) {
            val candidate = if (current.isEmpty()) word else "$current $word"
            if (paint.measureText(candidate) <= maxWidth || current.isEmpty()) {
                current.clear()
                current.append(candidate)
            } else {
                lines.add(current.toString())
                current.clear()
                current.append(word)
            }
        }
        if (current.isNotEmpty()) lines.add(current.toString())
        return lines
    }
}
