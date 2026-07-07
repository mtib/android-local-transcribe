package dev.mtib.localtranscribe.core.asr

import android.content.Context
import java.io.File

/**
 * Copies the bundled ONNX model assets out of the APK into [Context.getFilesDir] once, so
 * onnxruntime can memory-map the 620 MB encoder from a real file path instead of holding it in RAM.
 * Guarded by a version marker; bump [VERSION] when the bundled models change.
 */
object ModelInstaller {
    private const val VERSION = 1
    private const val ASSET_ROOT = "models"

    data class Models(
        val parakeetEncoder: String,
        val parakeetDecoder: String,
        val parakeetJoiner: String,
        val parakeetTokens: String,
        val vad: String,
    )

    @Volatile
    private var cached: Models? = null

    fun ensureInstalled(context: Context): Models {
        cached?.let { return it }
        synchronized(this) {
            cached?.let { return it }
            val root = File(context.filesDir, "$ASSET_ROOT/v$VERSION")
            val marker = File(root, ".ready")
            if (!marker.exists()) {
                root.deleteRecursively()
                copyAssetDir(context, ASSET_ROOT, root)
                marker.writeText("ok")
            }
            val parakeet = File(root, "parakeet")
            val vad = File(root, "vad")
            return Models(
                parakeetEncoder = File(parakeet, "encoder.int8.onnx").absolutePath,
                parakeetDecoder = File(parakeet, "decoder.int8.onnx").absolutePath,
                parakeetJoiner = File(parakeet, "joiner.int8.onnx").absolutePath,
                parakeetTokens = File(parakeet, "tokens.txt").absolutePath,
                vad = File(vad, "silero_vad.onnx").absolutePath,
            ).also { cached = it }
        }
    }

    private fun copyAssetDir(context: Context, assetPath: String, dest: File) {
        val children = context.assets.list(assetPath) ?: emptyArray()
        if (children.isEmpty()) {
            // It's a file: copy it.
            dest.parentFile?.mkdirs()
            context.assets.open(assetPath).use { input ->
                dest.outputStream().use { output -> input.copyTo(output, bufferSize = 1 shl 20) }
            }
            return
        }
        dest.mkdirs()
        for (child in children) {
            copyAssetDir(context, "$assetPath/$child", File(dest, child))
        }
    }
}
