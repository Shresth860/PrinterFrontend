package com.bizbite.printer.printer

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.util.Log
import android.util.Base64
import android.graphics.BitmapFactory
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

@Singleton
class ReceiptRenderer @Inject constructor() {
    fun render(payload: String, paperWidthMm: Int, copies: Int = 1, autoCut: Boolean = true, cashDrawer: Boolean = false): ByteArray {
        require(paperWidthMm == 58 || paperWidthMm == 80) { "Paper width must be 58 or 80 mm." }
        require(copies in 1..10)
        val root = kotlinx.serialization.json.Json.parseToJsonElement(payload)
        val blocks = when (root) {
            is JsonArray -> root
            is JsonObject -> root["blocks"] as? JsonArray
                ?: throw IllegalArgumentException("Receipt payload must contain a blocks array.")
            else -> throw IllegalArgumentException("Receipt payload must be an object or block array.")
        }
        val width = if (paperWidthMm == 58) 384 else 576
        val builder = EscPosBuilder().initialize()
        repeat(copies) {
            blocks.forEach { block -> renderBlock(builder, block, width, paperWidthMm) }
            builder.feed(3)
            if (cashDrawer) builder.cashDrawerPulse()
            if (autoCut) builder.cut()
        }
        return builder.build()
    }

    fun wrapText(text: String, paperWidthMm: Int): List<String> {
        val charsPerLine = if (paperWidthMm == 58) 32 else 48
        return text.split('\n').flatMap { paragraph ->
            if (paragraph.isEmpty()) listOf("")
            else {
                val lines = mutableListOf("")
                paragraph.split(Regex("\\s+")).forEach { originalWord ->
                    var word = originalWord
                    if (word.length > charsPerLine) {
                        if (lines.last().isNotEmpty()) lines.add("")
                        else lines.removeAt(lines.lastIndex)
                        while (word.length > charsPerLine) {
                            lines.add(word.take(charsPerLine))
                            word = word.drop(charsPerLine)
                        }
                    }
                    if (word.isNotEmpty()) {
                        val current = lines.last()
                        if (current.isEmpty()) lines[lines.lastIndex] = word
                        else if (current.length + word.length + 1 <= charsPerLine) lines[lines.lastIndex] = "$current $word"
                        else lines.add(word)
                    }
                }
                lines
            }
        }
    }

    private fun renderBlock(builder: EscPosBuilder, element: JsonElement, width: Int, paperWidthMm: Int) {
        val block = element as? JsonObject ?: return
        val type = block.string("type")?.uppercase() ?: return
        val value = block.string("text") ?: block.string("value").orEmpty()
        when (type) {
            "TEXT" -> {
                builder.align(alignment(block.string("align")))
                    .bold(block.boolean("bold"))
                    .size(block.int("width", 1), block.int("height", 1))
                if (value.any { it.code > 127 }) {
                    builder.raster(renderTextBitmap(value, width)).newline()
                } else {
                    wrapText(value, paperWidthMm).forEach { builder.text(it).newline() }
                }
                builder.bold(false).size().align(EscPosBuilder.Alignment.LEFT)
            }
            "TWO_COLUMN" -> {
                val left = block.string("left").orEmpty()
                val right = block.string("right").orEmpty()
                val columns = if (paperWidthMm == 58) 32 else 48
                val leftWidth = columns * 2 / 3
                val leftLines = wrapText(left, paperWidthMm).ifEmpty { listOf("") }
                val rightLines = wrapText(right, paperWidthMm).ifEmpty { listOf("") }
                repeat(maxOf(leftLines.size, rightLines.size)) { index ->
                    val l = leftLines.getOrElse(index) { "" }.take(leftWidth).padEnd(leftWidth)
                    val r = rightLines.getOrElse(index) { "" }.take(columns - leftWidth).padStart(columns - leftWidth)
                    appendText(builder, l + r, width)
                }
            }
            "ITEM_ROW" -> {
                val columns = if (paperWidthMm == 58) 32 else 48
                val quantity = block.string("quantity").orEmpty()
                val name = block.string("name").orEmpty()
                val amount = block.string("amount").orEmpty()
                val amountWidth = minOf(amount.length, columns / 3)
                val qtyWidth = minOf(quantity.length + 1, columns / 4)
                val nameWidth = columns - qtyWidth - amountWidth
                val nameLines = wrapText(name, paperWidthMm)
                nameLines.forEachIndexed { index, line ->
                    val qty = if (index == 0) "$quantity " else " ".repeat(qtyWidth)
                    val price = if (index == 0) amount.takeLast(amountWidth).padStart(amountWidth) else " ".repeat(amountWidth)
                    appendText(builder, qty.take(qtyWidth).padEnd(qtyWidth) + line.take(nameWidth).padEnd(nameWidth) + price, width)
                }
            }
            "DIVIDER" -> builder.text("-".repeat(if (paperWidthMm == 58) 32 else 48)).newline()
            "IMAGE" -> {
                val encoded = block.string("data") ?: value
                val image = runCatching {
                    val bytes = Base64.decode(encoded, Base64.DEFAULT)
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                }.getOrNull()
                if (image == null) {
                    Log.w(TAG, "Skipped image receipt block with invalid bitmap data.")
                } else {
                    try {
                        val scaledHeight = (image.height.toLong() * width / image.width).toInt().coerceAtLeast(1)
                        val scaled = Bitmap.createScaledBitmap(image, width, scaledHeight, true)
                        builder.raster(scaled).newline()
                        if (scaled !== image) scaled.recycle()
                    } finally {
                        image.recycle()
                    }
                }
            }
            "QR" -> builder.align(EscPosBuilder.Alignment.CENTER).qr(value).newline().align(EscPosBuilder.Alignment.LEFT)
            "BARCODE" -> {
                val format = block.string("format")?.uppercase()
                if (format == "EAN13") builder.barcodeEan13(value)
                else builder.barcodeCode128(value)
            }
            "FEED" -> builder.feed(block.int("lines", 1).coerceIn(0, 20))
            else -> Log.w(TAG, "Skipped unsupported receipt block type: $type")
        }
    }

    private fun appendText(builder: EscPosBuilder, text: String, width: Int) {
        if (text.any { it.code > 127 }) builder.raster(renderTextBitmap(text, width)).newline()
        else builder.text(text).newline()
    }

    private fun renderTextBitmap(text: String, width: Int): Bitmap {
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 24f
            typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.NORMAL)
        }
        val layout = StaticLayout.Builder.obtain(text, 0, text.length, paint, width)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setIncludePad(false)
            .build()
        val bitmap = Bitmap.createBitmap(width, maxOf(layout.height + 8, 24), Bitmap.Config.ARGB_8888)
        Canvas(bitmap).apply {
            drawColor(Color.WHITE)
            save()
            translate(0f, 4f)
            layout.draw(this)
            restore()
        }
        return bitmap
    }

    private fun JsonObject.string(key: String): String? =
        (this[key] as? JsonPrimitive)?.contentOrNull

    private fun JsonObject.int(key: String, default: Int): Int =
        (this[key] as? JsonPrimitive)?.contentOrNull?.toIntOrNull() ?: default

    private fun JsonObject.boolean(key: String): Boolean =
        (this[key] as? JsonPrimitive)?.contentOrNull?.toBooleanStrictOrNull() ?: false

    private fun alignment(value: String?) = when (value?.uppercase()) {
        "CENTER" -> EscPosBuilder.Alignment.CENTER
        "RIGHT" -> EscPosBuilder.Alignment.RIGHT
        else -> EscPosBuilder.Alignment.LEFT
    }

    private companion object { const val TAG = "BizBiteReceipt" }
}
