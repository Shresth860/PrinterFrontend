package com.bizbite.printer.features.creditDebitNotes.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bizbite.printer.data.remote.BbnHttpException
import com.bizbite.printer.data.security.SellerIdentityStore
import com.bizbite.printer.features.creditDebitNotes.services.CreditDebitNotesRepository
import com.bizbite.printer.features.creditDebitNotes.store.CreditDebitNotesCache
import com.bizbite.printer.features.creditDebitNotes.types.*
import com.bizbite.printer.features.creditDebitNotes.utils.NoteDraftCalculations
import com.bizbite.printer.features.creditDebitNotes.utils.CreateSubmissionGate
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.IOException
import java.math.BigDecimal
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class CreateNoteUiState(
    val orderId: String,
    val type: NoteType,
    val invoice: InvoiceSnapshot? = null,
    val notes: List<CreditDebitNote> = emptyList(),
    val loading: Boolean = true,
    val offline: Boolean = false,
    val saving: Boolean = false,
    val statusUnknown: Boolean = false,
    val statusChecking: Boolean = false,
    val error: String? = null,
    val fieldErrors: Map<String, String> = emptyMap(),
    val reason: String? = null,
    val description: String = "",
    val quantities: Map<String, String> = emptyMap(),
    val rateOverrides: Map<String, String> = emptyMap(),
    val gstOverrides: Map<String, String> = emptyMap(),
    val taxOverrideEnabled: Boolean = false,
    val taxTreatmentLabel: String = "Commercial-only",
    val priorNotesExpanded: Boolean = false,
    val discountLevel: String = "Invoice",
    val discountType: String = "Fixed",
    val discountApplyTo: String = "Selected items",
    val discountValue: String = "",
    val discountReason: String = "",
    val discountDescription: String = "",
    val outcomeNote: CreditDebitNote? = null,
    val outcomeSource: DataSource? = null
) {
    val reasons: List<String> get() = when (type) {
        NoteType.CREDIT -> listOf("Sales Return", "Damage Goods", "Goods Not Supplied", "Rate Difference", "Discount Allowed", "Other")
        NoteType.DEBIT -> listOf("Additional Charges", "Undercharge Invoice", "Rate Difference", "Additional Quantity", "Other")
    }
    val priorNotes: List<CreditDebitNote> get() = notes.filter { it.invoiceId == orderId }
        .sortedByDescending { it.createdAt.orEmpty() }
    val previousNote: CreditDebitNote? get() = priorNotes.firstOrNull { it.type == type && it.status != NoteStatus.CANCELLED }
    val activeReason: String? get() = reason
}

@HiltViewModel
class CreateNoteViewModel @Inject constructor(
    private val repository: CreditDebitNotesRepository,
    private val identity: SellerIdentityStore,
    private val cache: CreditDebitNotesCache
) : ViewModel() {
    private val createGuard = CreateSubmissionGate()
    private val mutable = MutableStateFlow(CreateNoteUiState(orderId = "", type = NoteType.CREDIT))
    val state = mutable.asStateFlow()
    private var baselineNoteIds: Set<String> = emptySet()
    private var intentId: String? = null

    fun start(orderId: String, type: NoteType) {
        if (mutable.value.orderId == orderId && mutable.value.type == type && mutable.value.invoice != null) return
        mutable.value = CreateNoteUiState(orderId = orderId, type = type)
        loadForm()
    }

    fun loadForm() {
        val current = mutable.value
        if (current.orderId.isBlank()) return
        viewModelScope.launch {
            mutable.value = mutable.value.copy(loading = true, error = null, offline = false)
            try {
                val invoice = repository.orderDetail(current.orderId)
                val notes = repository.listNotes()
                if (notes.source == DataSource.REAL) cache.put(identity.id(), notes.value.notes)
                baselineNoteIds = notes.value.notes.mapNotNull { it.id }.toSet()
                mutable.value = mutable.value.copy(
                    invoice = invoice.value,
                    notes = notes.value.notes,
                    loading = false,
                    offline = invoice.source != DataSource.REAL || notes.source != DataSource.REAL,
                    error = null
                )
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) {
                mutable.value = mutable.value.copy(loading = false, offline = true, error = failure.message ?: "Invoice and notes could not be refreshed. Check the connection and retry.")
            }
        }
    }

    fun reason(value: String?) { mutable.value = mutable.value.copy(reason = value, fieldErrors = mutable.value.fieldErrors - "reason") }
    fun description(value: String) { mutable.value = mutable.value.copy(description = value, fieldErrors = mutable.value.fieldErrors - "description") }
    fun quantity(itemId: String, value: String) { mutable.value = mutable.value.copy(quantities = mutable.value.quantities + (itemId to value), fieldErrors = mutable.value.fieldErrors - itemId) }
    fun rate(itemId: String, value: String) { mutable.value = mutable.value.copy(rateOverrides = mutable.value.rateOverrides + (itemId to value), fieldErrors = mutable.value.fieldErrors - "rate:$itemId") }
    fun gst(itemId: String, value: String) { mutable.value = mutable.value.copy(gstOverrides = mutable.value.gstOverrides + (itemId to value), fieldErrors = mutable.value.fieldErrors - "gst:$itemId") }
    fun enableTaxOverride(enabled: Boolean) { mutable.value = mutable.value.copy(taxOverrideEnabled = enabled) }
    fun taxTreatment(value: String) { mutable.value = mutable.value.copy(taxTreatmentLabel = value) }
    fun togglePriorNotes() { mutable.value = mutable.value.copy(priorNotesExpanded = !mutable.value.priorNotesExpanded) }
    fun setDiscount(level: String? = null, type: String? = null, applyTo: String? = null, value: String? = null, reason: String? = null, description: String? = null) {
        mutable.value = mutable.value.copy(
            discountLevel = level ?: mutable.value.discountLevel,
            discountType = type ?: mutable.value.discountType,
            discountApplyTo = applyTo ?: mutable.value.discountApplyTo,
            discountValue = value ?: mutable.value.discountValue,
            discountReason = reason ?: mutable.value.discountReason,
            discountDescription = description ?: mutable.value.discountDescription
        )
    }

    fun save() {
        if (!createGuard.begin()) return
        val current = mutable.value
        if (current.offline || current.loading || current.saving || current.statusUnknown || current.outcomeNote != null || current.invoice == null) {
            createGuard.releaseKnownFailure()
            return
        }
        val validation = validate(current)
        if (validation.isNotEmpty()) {
            mutable.value = current.copy(fieldErrors = validation, saving = false)
            createGuard.releaseKnownFailure()
            return
        }
        val draftItems = try { draftItems(current) } catch (_: Exception) {
            mutable.value = current.copy(fieldErrors = mapOf("form" to "Entered amount is outside the supported range."))
            createGuard.releaseKnownFailure()
            return
        }
        val reasonText = current.reason!!
        val remarks = if (reasonText == OTHER_REASON) current.description.trim() else null
        val intent = UUID.randomUUID().toString()
        intentId = intent
        baselineNoteIds = current.notes.mapNotNull { it.id }.toSet()
        mutable.value = current.copy(saving = true, error = null, fieldErrors = emptyMap(), statusUnknown = false)
        viewModelScope.launch {
            try {
                val result = repository.createNote(CreateNoteCommand(
                    clientRequestId = intent,
                    orderId = current.orderId,
                    type = current.type,
                    reason = reasonText,
                    remarks = remarks,
                    items = draftItems
                ))
                val refreshed = runCatching { repository.listNotes() }.getOrNull()
                if (refreshed?.source == DataSource.REAL) cache.put(identity.id(), refreshed.value.notes)
                mutable.value = mutable.value.copy(
                    saving = false,
                    notes = refreshed?.value?.notes ?: mutable.value.notes,
                    outcomeNote = result.value,
                    outcomeSource = result.source,
                    error = null
                )
                intentId = null
                // Keep the form locked after an issue; caller opens the created note detail.
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                when {
                    failure is BbnHttpException && failure.statusCode == 409 -> {
                        mutable.value = mutable.value.copy(saving = false, error = "The invoice notes changed while this form was open. Review the refreshed form before saving again.")
                        createGuard.releaseKnownFailure()
                        loadForm()
                    }
                    failure is BbnHttpException && failure.statusCode in 400..499 -> {
                        mutable.value = mutable.value.copy(saving = false, fieldErrors = mapValidationError(failure.message), error = failure.message)
                        createGuard.releaseKnownFailure()
                    }
                    isUnknownOutcome(failure) -> {
                        mutable.value = mutable.value.copy(saving = false, statusUnknown = true, error = "Status unknown. Check invoice notes before trying another create.")
                        createGuard.markUnknown()
                    }
                    else -> {
                        mutable.value = mutable.value.copy(saving = false, fieldErrors = mapValidationError(failure.message.orEmpty()), error = failure.message ?: "Note could not be created.")
                        createGuard.releaseKnownFailure()
                    }
                }
            }
        }
    }

    fun checkStatus() {
        if (!mutable.value.statusUnknown || mutable.value.statusChecking) return
        mutable.value = mutable.value.copy(statusChecking = true, error = null)
        viewModelScope.launch {
            try {
                val refreshed = repository.listNotes()
                if (refreshed.source == DataSource.REAL) cache.put(identity.id(), refreshed.value.notes)
                else {
                    mutable.value = mutable.value.copy(statusChecking = false, statusUnknown = true,
                        error = "Simulation / Mock notes cannot confirm whether the backend created this note.")
                    return@launch
                }
                val current = mutable.value
                val candidates = refreshed.value.notes.filter { note ->
                    note.id != null && note.id !in baselineNoteIds && note.invoiceId == current.orderId && note.type == current.type &&
                        (current.reason == null || note.reason.equals(current.reason, ignoreCase = true))
                }
                if (candidates.size == 1) {
                    mutable.value = current.copy(
                        notes = refreshed.value.notes, statusChecking = false, statusUnknown = false,
                        outcomeNote = candidates.single(), outcomeSource = refreshed.source,
                        error = "Note found. Review the created note before starting another."
                    )
                    intentId = null
                    createGuard.resolveUnknown()
                } else {
                    mutable.value = current.copy(
                        notes = refreshed.value.notes, statusChecking = false, statusUnknown = true,
                        error = if (candidates.isEmpty()) "No newly created note is visible yet. Check status again before any new create."
                        else "Multiple new notes match this invoice. A fresh create remains locked; review the notes list."
                    )
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) {
                mutable.value = mutable.value.copy(statusChecking = false, statusUnknown = true, error = "Could not confirm note status. Retry Check status; do not create again.")
            }
        }
    }

    private fun validate(s: CreateNoteUiState): Map<String, String> = buildMap {
        if (s.reason.isNullOrBlank()) put("reason", "Select a reason.")
        if (s.reason == OTHER_REASON && s.description.isBlank()) put("description", "Description is required for Other.")
        val invoice = s.invoice ?: return@buildMap
        if (invoice.number.isNullOrBlank()) put("form", "Invoice number not generated yet. Return to Order History and print the invoice first.")
        val selected = invoice.items.filter { item -> item.id != null && (s.quantities[item.id]?.toBigDecimalOrNull() ?: BigDecimal.ZERO) > BigDecimal.ZERO }
        if (selected.isEmpty()) put("items", "Select at least one item and enter a quantity.")
        val additional = s.type == NoteType.DEBIT && s.reason == ADDITIONAL_QUANTITY
        val rateDifference = s.reason == RATE_DIFFERENCE
        selected.forEach { item ->
            val id = item.id ?: return@forEach
            val qty = s.quantities[id]?.toBigDecimalOrNull() ?: BigDecimal.ZERO
            val prior = NoteDraftCalculations.previouslyAdjustedQuantity(s.notes, s.orderId, id)
            val ceiling = when {
                s.type == NoteType.CREDIT -> NoteDraftCalculations.creditCeiling(item.quantity, prior)
                s.type == NoteType.DEBIT && !additional -> item.quantity ?: BigDecimal.ZERO
                else -> null
            }
            if (ceiling != null && !NoteDraftCalculations.quantityAllowed(qty, ceiling)) {
                put(id, "Maximum available quantity is ${ceiling.stripTrailingZeros().toPlainString()} (Estimate).")
            }
            if ((additional || rateDifference) && (s.rateOverrides[id]?.toBigDecimalOrNull()?.signum() ?: 0) <= 0) {
                put("rate:$id", if (rateDifference) "Enter the rate difference." else "Enter a rate for additional quantity.")
            }
            if (additional && (s.gstOverrides[id]?.toBigDecimalOrNull()?.signum() ?: -1) < 0) put("gst:$id", "Enter GST percent for additional quantity.")
            if (additional && s.gstOverrides[id]?.toBigDecimalOrNull() == null) put("gst:$id", "Enter GST percent for additional quantity.")
            if (!rateDifference && !additional && item.rate == null) put("rate:$id", "Item rate unavailable.")
            if (s.taxOverrideEnabled && !additional && s.gstOverrides[id]?.toBigDecimalOrNull() == null) put("gst:$id", "Enter GST percent or turn the override off.")
        }
    }

    private fun draftItems(s: CreateNoteUiState): List<NoteDraftItem> {
        val additional = s.type == NoteType.DEBIT && s.reason == ADDITIONAL_QUANTITY
        val rateDifference = s.reason == RATE_DIFFERENCE
        val overrideTax = s.taxOverrideEnabled
        return s.invoice?.items.orEmpty().mapNotNull { item ->
            val id = item.id ?: return@mapNotNull null
            val quantity = s.quantities[id]?.toBigDecimalOrNull()?.takeIf { it > BigDecimal.ZERO } ?: return@mapNotNull null
            val rate = when {
                additional || rateDifference -> s.rateOverrides[id]?.toBigDecimalOrNull()?.let { Money(NoteDraftCalculations.toPaise(it)) }
                else -> null
            }
            val tax = when {
                additional -> s.gstOverrides[id]?.toBigDecimalOrNull()
                overrideTax -> s.gstOverrides[id]?.toBigDecimalOrNull()
                else -> null
            }
            NoteDraftItem(id, quantity, rate, tax)
        }
    }

    private fun isUnknownOutcome(failure: Exception): Boolean = when (failure) {
        is BbnHttpException -> failure.statusCode >= 500
        is IOException -> true
        else -> failure is java.util.concurrent.TimeoutException
    }

    private fun mapValidationError(message: String?): Map<String, String> {
        val value = message.orEmpty()
        val match = Regex("Invalid quantity for (.+)|(.+): only ([0-9.]+) quantity left to credit", RegexOption.IGNORE_CASE).find(value)
        return when {
            match != null -> mapOf("items" to value)
            value.contains("Reason", true) -> mapOf("reason" to value)
            value.contains("Select at least one item", true) -> mapOf("items" to value)
            value.isBlank() -> emptyMap()
            else -> mapOf("form" to value)
        }
    }

    companion object {
        const val OTHER_REASON = "Other"
        const val RATE_DIFFERENCE = "Rate Difference"
        const val ADDITIONAL_QUANTITY = "Additional Quantity"
    }
}
