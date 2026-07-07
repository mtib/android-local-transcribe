package dev.mtib.localtranscribe.core.translate

/**
 * Seam for a future offline translation step. Parakeet transcribes multilingual speech in the
 * spoken language; a real translator (e.g. a bundled NMT ONNX model) can replace [Identity] later
 * without touching the recording pipeline.
 */
interface Translator {
    fun translate(text: String): String

    object Identity : Translator {
        override fun translate(text: String): String = text
    }
}
