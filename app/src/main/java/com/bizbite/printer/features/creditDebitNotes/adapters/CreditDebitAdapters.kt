package com.bizbite.printer.features.creditDebitNotes.adapters

import com.bizbite.printer.data.remote.SellerOrderDto
import com.bizbite.printer.features.creditDebitNotes.types.CreditDebitNote
import com.bizbite.printer.features.creditDebitNotes.types.CustomerCreditBalance
import com.bizbite.printer.features.creditDebitNotes.types.InvoiceItemSnapshot
import com.bizbite.printer.features.creditDebitNotes.types.InvoiceSnapshot
import com.bizbite.printer.features.creditDebitNotes.types.NoteEligibility
import com.bizbite.printer.features.creditDebitNotes.types.NoteListPage
import com.bizbite.printer.features.creditDebitNotes.types.NoteSettlement
import com.bizbite.printer.features.creditDebitNotes.types.NoteStatus
import com.bizbite.printer.features.creditDebitNotes.types.NoteTimelineEvent
import com.bizbite.printer.features.creditDebitNotes.types.NoteType
import com.bizbite.printer.features.creditDebitNotes.types.Money
import com.bizbite.printer.features.creditDebitNotes.types.PreviousNote
import com.bizbite.printer.features.creditDebitNotes.types.RefundSnapshot
import com.bizbite.printer.features.creditDebitNotes.utils.CreditDebitMoney
import java.math.BigDecimal
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

object CreditDebitAdapters {
    fun noteList(response: JsonObject): NoteListPage {
        val notes = response.array("notes").mapNotNull { it as? JsonObject }.map(::note)
        val pagination = response.obj("pagination")
        return NoteListPage(
            notes = notes,
            page = pagination?.int("page"),
            limit = pagination?.int("limit"),
            total = pagination?.int("total"),
            pages = pagination?.int("pages")
        )
    }

    fun noteResponse(response: JsonObject): CreditDebitNote =
        response.obj("note")?.let(::note) ?: note(response)

    fun note(element: JsonObject): CreditDebitNote {
        val order = element.obj("order_id") ?: element.obj("order")
        val previous = element.obj("previous_note")
        val settlement = element.obj("settlement")?.let(::settlement)
        val timeline = element.array("timeline").mapNotNull { it as? JsonObject }.map { event ->
            NoteTimelineEvent(
                event = event.string("event").orEmpty(),
                at = event.string("at"),
                actorId = event.string("actor_id"),
                reason = event.string("reason")
            )
        }
        val rawStatus = element.string("status")
        val amount = CreditDebitMoney.parsePaise(element["amount_paise"])
            ?: CreditDebitMoney.parseRupees(element["total_amount"])
        return CreditDebitNote(
            id = element.string("note_id") ?: element.string("_id") ?: element.string("id"),
            number = element.string("note_number"),
            type = when ((element.string("type") ?: element.string("note_type"))?.lowercase()) {
                "credit" -> NoteType.CREDIT
                "debit" -> NoteType.DEBIT
                else -> null
            },
            status = noteStatus(rawStatus),
            rawStatus = rawStatus,
            invoiceId = element.string("invoice_id") ?: element.string("order_id") ?: order?.string("_id") ?: order?.string("id"),
            invoiceNumber = element.string("invoice_number") ?: element.string("against_invoice_number")
                ?: element.obj("invoice")?.string("number") ?: order?.string("invoice_number"),
            customerName = element.obj("customer")?.string("name") ?: order?.string("customer_name"),
            customerPhone = element.obj("customer")?.string("phone") ?: order?.string("customer_phone"),
            previousNote = PreviousNote(
                id = previous?.string("id") ?: element.string("previous_note_id"),
                number = previous?.string("note_number") ?: element.string("previous_note_number")
            ),
            reason = element.string("reason"),
            description = element.string("description") ?: element.string("remarks"),
            subtotal = CreditDebitMoney.parsePaise(element["subtotal_paise"]) ?: CreditDebitMoney.parseRupees(element["subtotal"]),
            tax = CreditDebitMoney.parsePaise(element["tax_amount_paise"]) ?: CreditDebitMoney.parseRupees(element["tax_amount"]),
            total = CreditDebitMoney.parsePaise(element["total_amount_paise"]) ?: CreditDebitMoney.parseRupees(element["total_amount"]),
            correctAmount = CreditDebitMoney.parsePaise(element["correct_amount_paise"]) ?: CreditDebitMoney.parseRupees(element["correct_amount"]),
            amount = amount,
            taxTreatment = element.string("tax_treatment"),
            items = element.array("items"),
            settlement = settlement,
            timeline = timeline,
            createdAt = element.string("createdAt") ?: element.string("created_at"),
            cancelledAt = element.string("cancelled_at")
        )
    }

    fun invoice(order: SellerOrderDto): InvoiceSnapshot = InvoiceSnapshot(
        id = order.id,
        number = order.invoiceNumber,
        customerName = order.customerName,
        customerPhone = order.customerPhone,
        total = CreditDebitMoney.parseRupees(order.totalAmount),
        paymentMethod = order.paymentMethod,
        paymentStatus = order.paymentStatus,
        items = order.items.mapNotNull { item ->
            val obj = item as? JsonObject ?: return@mapNotNull null
            InvoiceItemSnapshot(
                id = obj.string("_id"),
                name = obj.string("name"),
                quantity = obj.decimal("quantity"),
                rate = CreditDebitMoney.parseRupees(obj["price"]),
                taxPercent = obj.decimal("tax_percent"),
                raw = obj
            )
        }
    )

    fun invoicePayments(response: JsonObject): InvoiceSnapshot {
        val invoice = response.obj("invoice")
        return InvoiceSnapshot(
            id = invoice?.string("id"),
            number = invoice?.string("number"),
            customerId = invoice?.string("customer_id"),
            total = CreditDebitMoney.parsePaise(invoice?.get("total_paise")),
            paid = CreditDebitMoney.parsePaise(response["paid_paise"]),
            due = CreditDebitMoney.parsePaise(response["due_paise"])
        )
    }

    fun eligibility(response: JsonObject): NoteEligibility {
        val eligibility = response.obj("eligibility") ?: response
        val quantities = eligibility.array("remaining_qty").mapNotNull { element ->
            val item = element as? JsonObject ?: return@mapNotNull null
            val id = item.string("invoice_item_id") ?: return@mapNotNull null
            val quantity = item.decimal("quantity") ?: return@mapNotNull null
            id to quantity
        }.toMap()
        return NoteEligibility(
            canCredit = eligibility.boolean("can_credit"),
            canDebit = eligibility.boolean("can_debit"),
            canFullCancel = eligibility.boolean("can_full_cancel"),
            remainingCredit = CreditDebitMoney.parsePaise(eligibility["remaining_credit_paise"]),
            refundEligible = CreditDebitMoney.parsePaise(eligibility["refund_eligible_paise"]),
            reasonCodes = eligibility.array("reason_codes").mapNotNull { (it as? JsonPrimitive)?.contentOrNull },
            remainingQuantity = quantities,
            isEstimate = true
        )
    }

    fun refund(response: JsonObject): RefundSnapshot {
        val refund = response.obj("refund") ?: response
        return RefundSnapshot(
            id = refund.string("id"), noteId = refund.string("note_id"), status = refund.string("status"),
            method = refund.string("method"), amount = CreditDebitMoney.parsePaise(refund["amount_paise"]),
            providerRefundId = refund.string("provider_refund_id"), paymentId = refund.string("razorpay_payment_id"),
            reference = refund.string("reference"), createdAt = refund.string("created_at"), updatedAt = refund.string("updated_at")
        )
    }

    fun customerBalance(response: JsonObject): CustomerCreditBalance = CustomerCreditBalance(
        customerId = response.string("customer_id"),
        available = CreditDebitMoney.parsePaise(response["available_credit_paise"]),
        credits = response.array("credits").mapNotNull { it as? JsonObject }.map(::note)
    )

    fun pdf(response: JsonObject): com.bizbite.printer.features.creditDebitNotes.types.PdfMetadata {
        val pdf = response.obj("pdf")
        return com.bizbite.printer.features.creditDebitNotes.types.PdfMetadata(
            contentType = pdf?.string("content_type"), fileName = pdf?.string("file_name"), downloadUrl = pdf?.string("download_url")
        )
    }

    fun success(response: JsonObject): Boolean = response.boolean("success") == true
    fun message(response: JsonObject): String? = response.string("message")

    private fun settlement(obj: JsonObject) = NoteSettlement(
        refunded = CreditDebitMoney.parsePaise(obj["refunded_paise"]) ?: Money.ZERO,
        pendingRefund = CreditDebitMoney.parsePaise(obj["pending_refund_paise"]) ?: Money.ZERO,
        manualRefund = CreditDebitMoney.parsePaise(obj["manual_refund_paise"]) ?: Money.ZERO,
        applied = CreditDebitMoney.parsePaise(obj["applied_paise"]) ?: Money.ZERO,
        remaining = CreditDebitMoney.parsePaise(obj["remaining_paise"]) ?: Money.ZERO
    )

    private fun noteStatus(raw: String?): NoteStatus = when (raw?.trim()?.lowercase()) {
        "draft" -> NoteStatus.DRAFT
        "issued" -> NoteStatus.ISSUED
        "partially settled" -> NoteStatus.PARTIALLY_SETTLED
        "settled" -> NoteStatus.SETTLED
        "cancelled", "canceled" -> NoteStatus.CANCELLED
        else -> NoteStatus.UNKNOWN
    }

    private fun JsonObject.string(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull
    private fun JsonObject.int(key: String): Int? = string(key)?.toIntOrNull()
    private fun JsonObject.boolean(key: String): Boolean? = (this[key] as? JsonPrimitive)?.contentOrNull?.toBooleanStrictOrNull()
    private fun JsonObject.decimal(key: String): BigDecimal? = string(key)?.let { runCatching { BigDecimal(it) }.getOrNull() }
    private fun JsonObject.obj(key: String): JsonObject? = this[key] as? JsonObject
    private fun JsonObject.array(key: String): JsonArray = (this[key] as? JsonArray) ?: JsonArray(emptyList())
}
