package com.bizbite.printer.printer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ReceiptRendererTest {
    private val renderer = ReceiptRenderer()

    @Test
    fun wrapsWordsAtPaperWidthCharacterLimit() {
        assertEquals(
            listOf("Chicken rice with extra spicy", "sauce"),
            renderer.wrapText("Chicken rice with extra spicy sauce", 58)
        )
        assertEquals(
            listOf("Chicken rice with extra spicy sauce"),
            renderer.wrapText("Chicken rice with extra spicy sauce", 80)
        )
    }

    @Test
    fun breaksLongWordsAndKeepsExplicitNewlines() {
        val longWord = "a".repeat(33)
        assertEquals(listOf("a".repeat(32), "a", "second"), renderer.wrapText("$longWord\nsecond", 58))
    }

    @Test
    fun rejectsUnsupportedPaperWidth() {
        assertThrows(IllegalArgumentException::class.java) {
            renderer.render("""{"blocks":[]}""", 72)
        }
    }
}
