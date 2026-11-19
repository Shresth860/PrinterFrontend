package com.bizbite.printer.features.creditDebitNotes.utils

import com.bizbite.printer.features.creditDebitNotes.types.CreditDebitNote
import com.bizbite.printer.features.creditDebitNotes.types.NoteStatus
import com.bizbite.printer.features.creditDebitNotes.types.NoteType
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class CreditDebitNoteFilters(
    val search: String = "",
    val type: NoteType? = null,
    val status: NoteStatus? = null,
    val customer: String = "",
    val invoiceId: String? = null,
    val invoiceNumber: String = "",
    val reason: String = "",
    val dateFrom: String = "",
    val dateTo: String = ""
)

object CreditDebitNoteFiltering {
    fun apply(
        notes: List<CreditDebitNote>,
        filters: CreditDebitNoteFilters,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): List<CreditDebitNote> {
        val from = filters.dateFrom.toLocalDateOrNull()
        val through = filters.dateTo.toLocalDateOrNull()
        val search = filters.search.trim().lowercase()
        val customer = filters.customer.trim().lowercase()
        val invoice = filters.invoiceNumber.trim().lowercase()
        val reason = filters.reason.trim().lowercase()
        return notes.filter { note ->
            val date = note.createdAt.toDateOrNull(zoneId)
            val matchesSearch = search.isBlank() || listOfNotNull(
                note.number, note.invoiceNumber, note.customerName, note.customerPhone, note.reason
            ).any { it.lowercase().contains(search) }
            val matchesDate = (from == null || (date != null && !date.isBefore(from))) &&
                (through == null || (date != null && !date.isAfter(through)))
            val matchesCustomer = customer.isBlank() || listOfNotNull(note.customerName, note.customerPhone).any { it.lowercase().contains(customer) }
            val matchesInvoice = invoice.isBlank() || note.invoiceNumber?.lowercase()?.contains(invoice) == true
            val matchesInvoiceId = filters.invoiceId.isNullOrBlank() || note.invoiceId == filters.invoiceId
            val matchesReason = reason.isBlank() || note.reason?.lowercase()?.contains(reason) == true
            matchesSearch && matchesDate && matchesCustomer && matchesInvoice && matchesInvoiceId && matchesReason &&
                (filters.type == null || note.type == filters.type) &&
                (filters.status == null || note.status == filters.status)
        }
    }

    private fun String.toLocalDateOrNull(): LocalDate? = takeIf(String::isNotBlank)?.let {
        runCatching { LocalDate.parse(it) }.getOrNull()
    }

    private fun String?.toDateOrNull(zoneId: ZoneId): LocalDate? = this?.let { value ->
        runCatching { Instant.parse(value).atZone(zoneId).toLocalDate() }.getOrNull()
            ?: runCatching { LocalDate.parse(value.take(10)) }.getOrNull()
    }
}
