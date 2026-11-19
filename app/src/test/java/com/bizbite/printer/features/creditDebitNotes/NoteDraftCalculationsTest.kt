package com.bizbite.printer.features.creditDebitNotes

import com.bizbite.printer.features.creditDebitNotes.types.InvoiceItemSnapshot
import com.bizbite.printer.features.creditDebitNotes.types.Money
import com.bizbite.printer.features.creditDebitNotes.utils.CreateSubmissionGate
import com.bizbite.printer.features.creditDebitNotes.utils.DraftLineInput
import com.bizbite.printer.features.creditDebitNotes.utils.NoteDraftCalculations
import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NoteDraftCalculationsTest {
    @Test fun creditQuantityIsCappedByPriorEstimatedAdjustments() {
        val ceiling = NoteDraftCalculations.creditCeiling(BigDecimal("5"), BigDecimal("2.5"))
        assertEquals(BigDecimal("2.5"), ceiling)
        assertTrue(NoteDraftCalculations.quantityAllowed(BigDecimal("2.5"), ceiling))
        assertFalse(NoteDraftCalculations.quantityAllowed(BigDecimal("2.51"), ceiling))
    }

    @Test fun rateDifferenceUsesOnlyEnteredDelta() {
        val item = InvoiceItemSnapshot(id = "item", name = "Tea", quantity = BigDecimal("2"), rate = Money(10_000), taxPercent = BigDecimal.ZERO, raw = kotlinx.serialization.json.JsonObject(emptyMap()))
        val totals = NoteDraftCalculations.totals(listOf(DraftLineInput(item, BigDecimal("2"), BigDecimal("5"))), rateIsDelta = true)
        assertEquals(1_000L, totals.subtotal.paise)
        assertEquals(0L, totals.tax.paise)
        assertEquals(1_000L, totals.total.paise)
    }

    @Test fun taxAndTotalsRoundToWholePaise() {
        val item = InvoiceItemSnapshot(id = "item", quantity = BigDecimal.ONE, rate = Money(5), taxPercent = BigDecimal("10"), raw = kotlinx.serialization.json.JsonObject(emptyMap()))
        val totals = NoteDraftCalculations.totals(listOf(DraftLineInput(item, BigDecimal.ONE)), rateIsDelta = false)
        assertEquals(5L, totals.subtotal.paise)
        assertEquals(1L, totals.tax.paise)
        assertEquals(6L, totals.total.paise)
    }

    @Test fun createGateRejectsDoubleTapAndKeepsTimeoutOutcomeLockedUntilResolution() {
        val gate = CreateSubmissionGate()
        assertTrue(gate.begin())
        assertFalse(gate.begin())
        gate.markUnknown()
        gate.releaseKnownFailure()
        assertFalse(gate.begin())
        assertTrue(gate.isUnknown())
        gate.resolveUnknown()
        assertFalse(gate.isLocked())
        assertTrue(gate.begin())
    }
}
