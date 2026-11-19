package com.bizbite.printer.features.creditDebitNotes.services

import com.bizbite.printer.data.remote.BbnApi
import com.bizbite.printer.data.remote.BbnHttpException
import com.bizbite.printer.features.creditDebitNotes.adapters.CreditDebitAdapters
import com.bizbite.printer.features.creditDebitNotes.config.CreditDebitEndpointSettings
import com.bizbite.printer.features.creditDebitNotes.mocks.CreditDebitMockDataSource
import com.bizbite.printer.features.creditDebitNotes.types.CreateNoteCommand
import com.bizbite.printer.features.creditDebitNotes.types.CreditDebitNote
import com.bizbite.printer.features.creditDebitNotes.types.CustomerCreditBalance
import com.bizbite.printer.features.creditDebitNotes.types.DataSource
import com.bizbite.printer.features.creditDebitNotes.types.FeatureResult
import com.bizbite.printer.features.creditDebitNotes.types.InvoiceSnapshot
import com.bizbite.printer.features.creditDebitNotes.types.NoteEligibility
import com.bizbite.printer.features.creditDebitNotes.types.NoteListPage
import com.bizbite.printer.features.creditDebitNotes.types.NoteType
import com.bizbite.printer.features.creditDebitNotes.types.PdfMetadata
import com.bizbite.printer.features.creditDebitNotes.types.RefundSnapshot
import com.bizbite.printer.features.creditDebitNotes.types.Money
import com.bizbite.printer.features.creditDebitNotes.utils.CreditDebitMoney
import java.math.BigDecimal
import javax.inject.Inject
import javax.inject.Singleton
import javax.inject.Named
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

@Singleton
class BbnCreditDebitNotesRepository @Inject constructor(
    private val api: BbnApi,
    @Named("bbnCreateApi") private val createApi: BbnApi,
    private val mocks: CreditDebitMockDataSource,
    private val settings: CreditDebitEndpointSettings
) : CreditDebitNotesRepository {
    override suspend fun listNotes(): FeatureResult<NoteListPage> {
        if (settings.current.notesList.useMock) return mock("note-list") { CreditDebitAdapters.noteList(it) }
        val response = api.creditDebitNotes()
        requireSuccessful(response)
        return FeatureResult(CreditDebitAdapters.noteList(response), DataSource.REAL)
    }

    override suspend fun noteDetail(noteId: String): FeatureResult<CreditDebitNote> {
        require(noteId.isNotBlank())
        if (settings.current.noteDetail.useMock) return mock("note-detail") { CreditDebitAdapters.noteResponse(it) }
        val response = api.creditDebitNote(noteId)
        requireSuccessful(response)
        return FeatureResult(CreditDebitAdapters.noteResponse(response), DataSource.REAL)
    }

    override suspend fun noteDetailSimulation() = mock("note-detail") { CreditDebitAdapters.noteResponse(it) }

    override suspend fun createNote(command: CreateNoteCommand): FeatureResult<CreditDebitNote> {
        require(command.orderId.isNotBlank())
        require(command.reason.isNotBlank())
        require(command.items.isNotEmpty())
        require(command.items.all { it.itemId.isNotBlank() && it.quantity > BigDecimal.ZERO })
        if (settings.current.createNote.useMock) {
            return mock("create-note") { CreditDebitAdapters.noteResponse(it) }
        }
        val body = buildCreateBody(command)
        val response = createApi.createCreditDebitNote(command.type.apiValue, body)
        requireSuccessful(response)
        return FeatureResult(
            CreditDebitAdapters.noteResponse(response),
            DataSource.REAL,
            warnings = listOf("Backend contract has no client_request_id; UUID is retained locally and is not sent.")
        )
    }

    override suspend fun cancelNote(noteId: String, reason: String): FeatureResult<CreditDebitNote> {
        require(noteId.isNotBlank())
        require(reason.isNotBlank()) { "Cancellation reason is required." }
        if (settings.current.cancelNote.useMock) {
            return mock("cancel-note") { CreditDebitAdapters.noteResponse(it) }
        }
        val response = api.cancelCreditDebitNote(noteId)
        requireSuccessful(response)
        return FeatureResult(
            CreditDebitAdapters.noteResponse(response),
            DataSource.REAL,
            warnings = listOf("Backend cancel contract has no reason or client_request_id field; reason is not recorded by this endpoint.")
        )
    }

    override suspend fun orderDetail(orderId: String): FeatureResult<InvoiceSnapshot> {
        require(orderId.isNotBlank())
        if (settings.current.orderDetail.useMock) return mock("invoice-payments") { CreditDebitAdapters.invoicePayments(it) }
        val response = api.order(orderId)
        if (!response.success) throw BbnHttpException(200, response.message ?: "Order detail request failed.")
        val order = response.data ?: response.order ?: error("Order detail response omitted both data and order.")
        return FeatureResult(
            CreditDebitAdapters.invoice(order),
            DataSource.REAL,
            warnings = listOf("Paid, due, Razorpay payment ID and payment ledger are absent from the app's existing order DTO; those values remain unknown.")
        )
    }

    override suspend fun eligibilityEstimate(orderId: String): FeatureResult<NoteEligibility> {
        require(orderId.isNotBlank())
        if (settings.current.eligibility.useMock) {
            return mock("eligibility") { CreditDebitAdapters.eligibility(it) }
        }
        val invoiceResult = orderDetail(orderId)
        val notesResult = listNotes()
        val invoice = invoiceResult.value
        val total = invoice.total
        val issuedCredit = notesResult.value.notes
            .asSequence()
            .filter { note ->
                note.type == NoteType.CREDIT && note.status != com.bizbite.printer.features.creditDebitNotes.types.NoteStatus.CANCELLED &&
                    (note.invoiceId == orderId || (invoice.number != null && note.invoiceNumber == invoice.number))
            }
            .mapNotNull { it.amount ?: it.total ?: it.correctAmount }
            .fold(Money.ZERO) { sum, amount -> sum + amount }
        val remaining = total?.let { Money((it.paise - issuedCredit.paise).coerceAtLeast(0L)) }
        val source = if (invoiceResult.source == DataSource.MOCK || notesResult.source == DataSource.MOCK) DataSource.MOCK else DataSource.REAL
        val warnings = buildList {
            add("Remaining credit is a client estimate from invoice total and non-cancelled credit notes; backend validation is final.")
            add("Remaining item quantities and refund eligibility are unknown because the real note-item response and payment ledger are incomplete.")
            addAll(invoiceResult.warnings)
        }
        return FeatureResult(
            NoteEligibility(canCredit = remaining?.isPositive, remainingCredit = remaining, isEstimate = true),
            source,
            simulationLabel = if (source == DataSource.MOCK) "Simulation / Mock • Estimate" else "Estimate",
            warnings = warnings
        )
    }

    override suspend fun invoicePaymentsMock() = mock("invoice-payments") { CreditDebitAdapters.invoicePayments(it) }
    override suspend fun eligibilityMock() = mock("eligibility") { CreditDebitAdapters.eligibility(it) }
    override suspend fun fullCancellationMock() = mock("full-cancellation") { CreditDebitAdapters.noteResponse(it) }
    override suspend fun razorpayRefundMock() = mock("razorpay-refund") { CreditDebitAdapters.refund(it) }
    override suspend fun manualRefundMock() = mock("manual-refund") { CreditDebitAdapters.refund(it) }
    override suspend fun creditApplicationMock() = mock("credit-application") { CreditDebitAdapters.noteResponse(it) }
    override suspend fun refundStatusMock() = mock("refund-status") { CreditDebitAdapters.refund(it) }
    override suspend fun notePdfMock() = mock("note-pdf") { CreditDebitAdapters.pdf(it) }
    override suspend fun customerCreditBalanceMock() = mock("customer-credit-balance") { CreditDebitAdapters.customerBalance(it) }
    override suspend fun reauthApprovalMock() = mock("audit-reauth") { CreditDebitAdapters.noteResponse(it) }

    private fun buildCreateBody(command: CreateNoteCommand): JsonObject = buildJsonObject {
        put("order_id", command.orderId)
        put("reason", command.reason.trim())
        command.remarks?.takeIf(String::isNotBlank)?.let { put("remarks", it.trim()) }
        put("items", buildJsonArray {
            command.items.forEach { item ->
                add(buildJsonObject {
                    put("item_id", item.itemId)
                    put("quantity", JsonPrimitive(item.quantity.stripTrailingZeros()))
                    item.rate?.let { put("rate", CreditDebitMoney.toJsonRupees(it)) }
                    item.taxPercent?.let { put("tax_percent", JsonPrimitive(it.stripTrailingZeros())) }
                })
            }
        })
    }

    private suspend fun <T> mock(name: String, transform: (JsonObject) -> T): FeatureResult<T> {
        val fixture = mocks.load(name)
        return FeatureResult(transform(fixture), DataSource.MOCK, simulationLabel = "Simulation / Mock")
    }

    private fun requireSuccessful(response: JsonObject) {
        if (!CreditDebitAdapters.success(response)) {
            throw IllegalStateException(CreditDebitAdapters.message(response) ?: "Credit/debit note request failed.")
        }
    }
}
