package com.bizbite.printer.features.creditDebitNotes.services

import com.bizbite.printer.features.creditDebitNotes.types.CreateNoteCommand
import com.bizbite.printer.features.creditDebitNotes.types.CreditDebitNote
import com.bizbite.printer.features.creditDebitNotes.types.CustomerCreditBalance
import com.bizbite.printer.features.creditDebitNotes.types.FeatureResult
import com.bizbite.printer.features.creditDebitNotes.types.InvoiceSnapshot
import com.bizbite.printer.features.creditDebitNotes.types.NoteEligibility
import com.bizbite.printer.features.creditDebitNotes.types.NoteListPage
import com.bizbite.printer.features.creditDebitNotes.types.PdfMetadata
import com.bizbite.printer.features.creditDebitNotes.types.RefundSnapshot

interface CreditDebitNotesRepository {
    suspend fun listNotes(): FeatureResult<NoteListPage>
    suspend fun noteDetail(noteId: String): FeatureResult<CreditDebitNote>
    suspend fun noteDetailSimulation(): FeatureResult<CreditDebitNote>
    suspend fun createNote(command: CreateNoteCommand): FeatureResult<CreditDebitNote>
    suspend fun cancelNote(noteId: String, reason: String): FeatureResult<CreditDebitNote>
    suspend fun orderDetail(orderId: String): FeatureResult<InvoiceSnapshot>
    suspend fun eligibilityEstimate(orderId: String): FeatureResult<NoteEligibility>
    suspend fun invoicePaymentsMock(): FeatureResult<InvoiceSnapshot>
    suspend fun eligibilityMock(): FeatureResult<NoteEligibility>
    suspend fun fullCancellationMock(): FeatureResult<CreditDebitNote>
    suspend fun razorpayRefundMock(): FeatureResult<RefundSnapshot>
    suspend fun manualRefundMock(): FeatureResult<RefundSnapshot>
    suspend fun creditApplicationMock(): FeatureResult<CreditDebitNote>
    suspend fun refundStatusMock(): FeatureResult<RefundSnapshot>
    suspend fun notePdfMock(): FeatureResult<PdfMetadata>
    suspend fun customerCreditBalanceMock(): FeatureResult<CustomerCreditBalance>
    suspend fun reauthApprovalMock(): FeatureResult<CreditDebitNote>
}
