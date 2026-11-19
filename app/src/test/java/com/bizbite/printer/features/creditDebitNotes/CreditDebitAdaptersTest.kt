package com.bizbite.printer.features.creditDebitNotes

import com.bizbite.printer.features.creditDebitNotes.adapters.CreditDebitAdapters
import com.bizbite.printer.features.creditDebitNotes.types.NoteStatus
import com.bizbite.printer.features.creditDebitNotes.types.NoteType
import com.bizbite.printer.features.creditDebitNotes.utils.CreditDebitMoney
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CreditDebitAdaptersTest {
    @Test fun rupeeValuesConvertToExactPaiseWithoutFloatingPoint() {
        assertEquals(10L, CreditDebitMoney.parseRupees("0.10")?.paise)
        assertEquals(1L, CreditDebitMoney.parseRupees("0.01")?.paise)
        assertEquals("₹1234.56", CreditDebitMoney.parseRupees("1234.56")?.formatInr())
    }

    @Test fun mapsRealNoteFieldsAndPreservesUnknownStatusAsUnknown() {
        val response = Json.parseToJsonElement(
            """{"success":true,"notes":[{"_id":"n1","note_type":"credit","note_number":"CN-1","status":"issued","total_amount":10.25,"order_id":{"_id":"o1","invoice_number":"INV-1"}},{"_id":"n2","status":"awaiting_review","total_amount":2.50}]}"""
        ).jsonObject

        val page = CreditDebitAdapters.noteList(response)

        assertEquals(2, page.notes.size)
        assertEquals(NoteType.CREDIT, page.notes.first().type)
        assertEquals(NoteStatus.ISSUED, page.notes.first().status)
        assertEquals(1025L, page.notes.first().amount?.paise)
        assertEquals("INV-1", page.notes.first().invoiceNumber)
        assertEquals(NoteStatus.UNKNOWN, page.notes.last().status)
        assertNull(page.notes.last().type)
    }

    @Test fun mapsMockPaiseAndSettlementStatus() {
        val response = Json.parseToJsonElement(
            """{"success":true,"note":{"note_id":"n3","type":"credit","status":"Partially Settled","amount_paise":12345,"settlement":{"refunded_paise":1000,"pending_refund_paise":200,"manual_refund_paise":300,"applied_paise":400,"remaining_paise":10445}}}"""
        ).jsonObject

        val note = CreditDebitAdapters.noteResponse(response)

        assertEquals(NoteStatus.PARTIALLY_SETTLED, note.status)
        assertEquals(12345L, note.amount?.paise)
        assertEquals(10445L, note.settlement?.remaining?.paise)
    }
}
