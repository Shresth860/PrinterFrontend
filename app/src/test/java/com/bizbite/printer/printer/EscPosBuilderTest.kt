package com.bizbite.printer.printer

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class EscPosBuilderTest {
    @Test
    fun emitsExpectedInitializationAndFormattingBytes() {
        val actual = EscPosBuilder()
            .initialize()
            .bold(true)
            .align(EscPosBuilder.Alignment.CENTER)
            .size(2, 2)
            .feed(2)
            .cut()
            .build()

        assertArrayEquals(
            byteArrayOf(
                0x1B, 0x40,
                0x1B, 0x45, 0x01,
                0x1B, 0x61, 0x01,
                0x1D, 0x21, 0x11,
                0x1B, 0x64, 0x02,
                0x1D, 0x56, 0x41, 0x10
            ),
            actual
        )
    }

    @Test
    fun writesQrStoragePayloadLengthAndBytes() {
        val bytes = EscPosBuilder().qr("A").build()
        val index = bytes.indexOfSequence(byteArrayOf(0x1D, 0x28, 0x6B, 0x04, 0x00, 0x31, 0x50, 0x30))
        assertEquals(true, index >= 0)
        assertEquals('A'.code.toByte(), bytes[index + 8])
    }

    private fun ByteArray.indexOfSequence(sequence: ByteArray): Int =
        indices.firstOrNull { start ->
            start + sequence.size <= size && sequence.indices.all { this[start + it] == sequence[it] }
        } ?: -1
}
