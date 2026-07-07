package dev.mtib.localtranscribe.core.audio

import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Streams 16-bit mono PCM into a WAV file, patching the RIFF/data sizes on [close]. */
class WavWriter(file: File, private val sampleRate: Int = 16000) {
    private val access = RandomAccessFile(file, "rw")
    private var dataBytes = 0L

    init {
        access.setLength(0)
        writeHeader(0)
    }

    /** Appends [count] little-endian PCM16 samples from [samples]. */
    fun write(samples: ShortArray, count: Int) {
        val buffer = ByteBuffer.allocate(count * 2).order(ByteOrder.LITTLE_ENDIAN)
        for (i in 0 until count) buffer.putShort(samples[i])
        access.write(buffer.array())
        dataBytes += count * 2L
    }

    fun close() {
        access.seek(0)
        writeHeader(dataBytes)
        access.close()
    }

    private fun writeHeader(dataBytes: Long) {
        val byteRate = sampleRate * 2
        access.writeBytes("RIFF")
        access.writeInt(intLe((36 + dataBytes).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()))
        access.writeBytes("WAVE")
        access.writeBytes("fmt ")
        access.writeInt(intLe(16))
        access.writeShort(shortLe(1))       // PCM
        access.writeShort(shortLe(1))       // mono
        access.writeInt(intLe(sampleRate))
        access.writeInt(intLe(byteRate))
        access.writeShort(shortLe(2))       // block align
        access.writeShort(shortLe(16))      // bits per sample
        access.writeBytes("data")
        access.writeInt(intLe(dataBytes.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()))
    }

    private fun intLe(value: Int): Int = Integer.reverseBytes(value)
    private fun shortLe(value: Int): Int = java.lang.Short.reverseBytes(value.toShort()).toInt()
}
