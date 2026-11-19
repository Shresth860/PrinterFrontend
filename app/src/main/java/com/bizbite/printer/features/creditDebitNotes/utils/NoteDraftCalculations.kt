package com.bizbite.printer.features.creditDebitNotes.utils

import com.bizbite.printer.features.creditDebitNotes.types.InvoiceItemSnapshot
import com.bizbite.printer.features.creditDebitNotes.types.Money
import com.bizbite.printer.features.creditDebitNotes.types.CreditDebitNote
import com.bizbite.printer.features.creditDebitNotes.types.NoteStatus
import com.bizbite.printer.features.creditDebitNotes.types.NoteType
import java.math.BigDecimal
import java.math.RoundingMode
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull

data class DraftLineInput(
    val item: InvoiceItemSnapshot,
    val quantity: BigDecimal,
    /** Rupee delta for Rate Difference, otherwise the explicit override, if any. */
    val rateRupees: BigDecimal? = null,
    val taxPercent: BigDecimal? = null
)

data class DraftTotals(val subtotal: Money, val tax: Money, val total: Money)

object NoteDraftCalculations {
    fun creditCeiling(invoiceQuantity: BigDecimal?, previouslyAdjusted: BigDecimal): BigDecimal =
        ((invoiceQuantity ?: BigDecimal.ZERO) - previouslyAdjusted).max(BigDecimal.ZERO)

    fun quantityAllowed(quantity: BigDecimal, ceiling: BigDecimal): Boolean =
        quantity > BigDecimal.ZERO && quantity <= ceiling

    fun previouslyAdjustedQuantity(notes: List<CreditDebitNote>, orderId: String, itemId: String): BigDecimal = notes.asSequence()
        .filter { it.type == NoteType.CREDIT && it.status != NoteStatus.CANCELLED && it.invoiceId == orderId }
        .flatMap { it.items.asSequence() }
        .mapNotNull { element ->
            val obj = element as? JsonObject ?: return@mapNotNull null
            val id = obj["item_id"]?.jsonPrimitive?.contentOrNull
            if (id != itemId) null else obj["quantity"]?.jsonPrimitive?.contentOrNull?.toBigDecimalOrNull()
        }.fold(BigDecimal.ZERO, BigDecimal::add)

    fun totals(lines: List<DraftLineInput>, rateIsDelta: Boolean): DraftTotals {
        var subtotal = 0L
        var tax = 0L
        lines.forEach { line ->
            val rate = if (rateIsDelta) {
                line.rateRupees ?: throw IllegalArgumentException("Rate difference unavailable for ${line.item.name ?: "item"}.")
            } else line.rateRupees ?: line.item.rate?.decimalRupees()
                ?: throw IllegalArgumentException("Rate unavailable for ${line.item.name ?: "item"}.")
            require(line.quantity > BigDecimal.ZERO)
            val lineSubtotal = toPaise(line.quantity.multiply(rate))
            val taxPercent = line.taxPercent ?: line.item.taxPercent
                ?: throw IllegalArgumentException("GST unavailable for ${line.item.name ?: "item"}.")
            val lineTax = toPaise(BigDecimal.valueOf(lineSubtotal).movePointLeft(2)
                .multiply(taxPercent).movePointLeft(2))
            subtotal = Math.addExact(subtotal, lineSubtotal)
            tax = Math.addExact(tax, lineTax)
        }
        return DraftTotals(Money(subtotal), Money(tax), Money(Math.addExact(subtotal, tax)))
    }

    fun toPaise(rupees: BigDecimal): Long = rupees.setScale(2, RoundingMode.HALF_UP)
        .movePointRight(2).longValueExact()
}
