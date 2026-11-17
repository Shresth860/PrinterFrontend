package com.bizbite.printer.printer

import android.graphics.Bitmap
import java.io.ByteArrayOutputStream

class EscPosBuilder {
    private val output = ByteArrayOutputStream()

    fun initialize() = command(0x1B, 0x40)
    fun bold(enabled: Boolean) = command(0x1B, 0x45, if (enabled) 1 else 0)
    fun align(value: Alignment) = command(0x1B, 0x61, value.code)
    fun size(width: Int = 1, height: Int = 1): EscPosBuilder {
        require(width in 1..8 && height in 1..8)
        return command(0x1D, 0x21, ((width - 1) shl 4) or (height - 1))
    }
    fun feed(lines: Int = 1): EscPosBuilder {
        require(lines in 0..255)
        return command(0x1B, 0x64, lines)
    }
    fun cut() = command(0x1D, 0x56, 0x41, 0x10)
    fun cashDrawerPulse() = command(0x1B, 0x70, 0x00, 25, 250)
    fun text(value: String): EscPosBuilder {
        output.write(value.toByteArray(Charsets.UTF_8))
        return this
    }
    fun newline() = command(0x0A)

    fun qr(value: String, moduleSize: Int = 6): EscPosBuilder {
        require(moduleSize in 1..16)
        val data = value.toByteArray(Charsets.UTF_8)
        val storeLength = data.size + 3
        val pL = storeLength and 0xFF
        val pH = (storeLength shr 8) and 0xFF
        command(0x1D, 0x28, 0x6B, 0x04, 0x00, 0x31, 0x41, 0x32, 0x00)
        command(0x1D, 0x28, 0x6B, 0x03, 0x00, 0x31, 0x43, moduleSize)
        command(0x1D, 0x28, 0x6B, pL, pH, 0x31, 0x50, 0x30)
        output.write(data)
        command(0x1D, 0x28, 0x6B, 0x03, 0x00, 0x31, 0x51, 0x30)
        return this
    }

    fun barcodeCode128(value: String, height: Int = 80): EscPosBuilder {
        require(value.isNotEmpty() && value.length <= 250)
        require(height in 1..255)
        command(0x1D, 0x68, height)
        command(0x1D, 0x77, 2)
        val data = ("{B$value").toByteArray(Charsets.US_ASCII)
        command(0x1D, 0x6B, 0x49, data.size)
        output.write(data)
        return newline()
    }

    fun barcodeEan13(value: String, height: Int = 80): EscPosBuilder {
        require(value.length == 12 && value.all(Char::isDigit)) { "EAN-13 requires 12 data digits." }
        require(height in 1..255)
        command(0x1D, 0x68, height)
        command(0x1D, 0x77, 2)
        command(0x1D, 0x6B, 0x02)
        output.write(value.toByteArray(Charsets.US_ASCII))
        command(0x00)
        return newline()
    }

    fun raster(bitmap: Bitmap, chunkHeight: Int = 128): EscPosBuilder {
        require(chunkHeight in 1..255)
        var top = 0
        val errors = FloatArray(bitmap.width + 2)
        val nextErrors = FloatArray(bitmap.width + 2)
        while (top < bitmap.height) {
            val rows = minOf(chunkHeight, bitmap.height - top)
            val bytesPerRow = (bitmap.width + 7) / 8
            command(
                0x1D, 0x76, 0x30, 0x00,
                bytesPerRow and 0xFF, (bytesPerRow shr 8) and 0xFF,
                rows and 0xFF, (rows shr 8) and 0xFF
            )
            for (row in top until top + rows) {
                var packed = 0
                var bit = 7
                for (column in 0 until bitmap.width) {
                    val pixel = bitmap.getPixel(column, row)
                    val gray = (android.graphics.Color.red(pixel) * 299 +
                        android.graphics.Color.green(pixel) * 587 +
                        android.graphics.Color.blue(pixel) * 114) / 1000
                    val adjusted = (gray + errors[column + 1]).coerceIn(0f, 255f)
                    if (adjusted < 128) packed = packed or (1 shl bit)
                    val quantizationError = adjusted - if (adjusted < 128) 0f else 255f
                    errors[column + 2] += quantizationError * 7f / 16f
                    nextErrors[column] += quantizationError * 3f / 16f
                    nextErrors[column + 1] += quantizationError * 5f / 16f
                    nextErrors[column + 2] += quantizationError / 16f
                    bit--
                    if (bit < 0) {
                        command(packed)
                        packed = 0
                        bit = 7
                    }
                }
                if (bit != 7) command(packed)
                System.arraycopy(nextErrors, 0, errors, 0, errors.size)
                nextErrors.fill(0f)
            }
            top += rows
        }
        return this
    }

    fun build(): ByteArray = output.toByteArray()

    private fun command(vararg values: Int): EscPosBuilder {
        values.forEach { output.write(it and 0xFF) }
        return this
    }

    enum class Alignment(val code: Int) { LEFT(0), CENTER(1), RIGHT(2) }
}
