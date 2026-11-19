package com.bizbite.printer.features.creditDebitNotes.types

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import java.math.BigDecimal
import java.util.UUID

enum class NoteType(val apiValue: String) { CREDIT("credit"), DEBIT("debit") }
enum class NoteStatus { DRAFT, ISSUED, PARTIALLY_SETTLED, SETTLED, CANCELLED, UNKNOWN }
enum class DataSource { REAL, MOCK }

data class Money(val paise: Long) {
    operator fun plus(other: Money) = Money(Math.addExact(paise, other.paise))
    operator fun minus(other: Money) = Money(Math.subtractExact(paise, other.paise))
    val isPositive: Boolean get() = paise > 0

    fun decimalRupees(): BigDecimal = BigDecimal.valueOf(paise, 2)
    fun formatInr(): String = "₹${decimalRupees().setScale(2).toPlainString()}"

    companion object { val ZERO = Money(0) }
}

data class PreviousNote(val id: String? = null, val number: String? = null)
data class NoteSettlement(
    val refunded: Money = Money.ZERO,
    val pendingRefund: Money = Money.ZERO,
    val manualRefund: Money = Money.ZERO,
    val applied: Money = Money.ZERO,
    val remaining: Money = Money.ZERO
)
data class NoteTimelineEvent(
    val event: String,
    val at: String? = null,
    val actorId: String? = null,
    val reason: String? = null
)
data class CreditDebitNote(
    val id: String? = null,
    val number: String? = null,
    val type: NoteType? = null,
    val status: NoteStatus = NoteStatus.UNKNOWN,
    val rawStatus: String? = null,
    val invoiceId: String? = null,
    val invoiceNumber: String? = null,
    val customerName: String? = null,
    val customerPhone: String? = null,
    val previousNote: PreviousNote = PreviousNote(),
    val reason: String? = null,
    val description: String? = null,
    val subtotal: Money? = null,
    val tax: Money? = null,
    val total: Money? = null,
    val correctAmount: Money? = null,
    val amount: Money? = null,
    val taxTreatment: String? = null,
    val items: JsonArray = JsonArray(emptyList()),
    val settlement: NoteSettlement? = null,
    val timeline: List<NoteTimelineEvent> = emptyList(),
    val createdAt: String? = null,
    val cancelledAt: String? = null
)

data class NoteListPage(
    val notes: List<CreditDebitNote> = emptyList(),
    val page: Int? = null,
    val limit: Int? = null,
    val total: Int? = null,
    val pages: Int? = null
)

data class InvoiceItemSnapshot(
    val id: String? = null,
    val name: String? = null,
    val quantity: BigDecimal? = null,
    val rate: Money? = null,
    val taxPercent: BigDecimal? = null,
    val raw: JsonObject
)

data class InvoiceSnapshot(
    val id: String? = null,
    val number: String? = null,
    val customerId: String? = null,
    val customerName: String? = null,
    val customerPhone: String? = null,
    val total: Money? = null,
    val paid: Money? = null,
    val due: Money? = null,
    val paymentMethod: String? = null,
    val paymentStatus: String? = null,
    val items: List<InvoiceItemSnapshot> = emptyList()
)

data class NoteDraftItem(
    val itemId: String,
    val quantity: BigDecimal,
    val rate: Money? = null,
    val taxPercent: BigDecimal? = null
)

/** clientRequestId is local-only: Section 3 confirms the current create API has no idempotency field. */
data class CreateNoteCommand(
    val clientRequestId: String = UUID.randomUUID().toString(),
    val orderId: String,
    val type: NoteType,
    val reason: String,
    val remarks: String? = null,
    val items: List<NoteDraftItem>
)

data class FeatureResult<T>(
    val value: T,
    val source: DataSource,
    val simulationLabel: String? = if (source == DataSource.MOCK) "Simulation / Mock" else null,
    val warnings: List<String> = emptyList()
)

data class NoteEligibility(
    val canCredit: Boolean? = null,
    val canDebit: Boolean? = null,
    val canFullCancel: Boolean? = null,
    val remainingCredit: Money? = null,
    val refundEligible: Money? = null,
    val reasonCodes: List<String> = emptyList(),
    val remainingQuantity: Map<String, BigDecimal> = emptyMap(),
    val isEstimate: Boolean = true
)

data class RefundSnapshot(
    val id: String? = null,
    val noteId: String? = null,
    val status: String? = null,
    val method: String? = null,
    val amount: Money? = null,
    val providerRefundId: String? = null,
    val paymentId: String? = null,
    val reference: String? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null
)

data class CustomerCreditBalance(val customerId: String? = null, val available: Money? = null, val credits: List<CreditDebitNote> = emptyList())
data class PdfMetadata(val contentType: String? = null, val fileName: String? = null, val downloadUrl: String? = null)

data class CreateNoteApiBody(val json: JsonObject)
