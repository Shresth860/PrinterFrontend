package com.bizbite.printer.features.creditDebitNotes.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bizbite.printer.data.security.SellerIdentityStore
import com.bizbite.printer.features.creditDebitNotes.permissions.SellerNotePermissions
import com.bizbite.printer.features.creditDebitNotes.permissions.SellerNoteRolePolicy
import com.bizbite.printer.features.creditDebitNotes.services.CreditDebitNotesRepository
import com.bizbite.printer.features.creditDebitNotes.store.CreditDebitNotesCache
import com.bizbite.printer.features.creditDebitNotes.types.CreditDebitNote
import com.bizbite.printer.features.creditDebitNotes.types.DataSource
import com.bizbite.printer.features.creditDebitNotes.types.NoteListPage
import com.bizbite.printer.features.creditDebitNotes.types.InvoiceSnapshot
import com.bizbite.printer.features.creditDebitNotes.utils.CreditDebitNoteFilters
import com.bizbite.printer.features.creditDebitNotes.utils.CreditDebitNoteFiltering
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class CreditDebitNotesUiState(
    val notes: List<CreditDebitNote> = emptyList(),
    val loading: Boolean = false,
    val refreshing: Boolean = false,
    val offline: Boolean = false,
    val error: String? = null,
    val source: DataSource? = null,
    val filters: CreditDebitNoteFilters = CreditDebitNoteFilters(),
    val page: Int = 1,
    val pageSize: Int = 15,
    val selectedNote: CreditDebitNote? = null,
    val selectedSource: DataSource? = null,
    val simulatedSettlement: CreditDebitNote? = null,
    val simulatedPayments: InvoiceSnapshot? = null,
    val detailLoading: Boolean = false,
    val detailError: String? = null
) {
    val filteredNotes: List<CreditDebitNote> get() = CreditDebitNoteFiltering.apply(notes, filters)
    val visibleNotes: List<CreditDebitNote> get() = filteredNotes.take(page * pageSize)
    val hasMore: Boolean get() = visibleNotes.size < filteredNotes.size
}

@HiltViewModel
class CreditDebitNotesViewModel @Inject constructor(
    private val repository: CreditDebitNotesRepository,
    private val identity: SellerIdentityStore,
    private val cache: CreditDebitNotesCache
) : ViewModel() {
    private val sellerAtCreation = identity.id()
    private val mutable = MutableStateFlow(
        CreditDebitNotesUiState(notes = cache.get(sellerAtCreation))
    )
    val state = mutable.asStateFlow()
    val permissions: SellerNotePermissions = SellerNoteRolePolicy.permissions(identity.role())

    fun refresh() {
        if (mutable.value.loading || mutable.value.refreshing) return
        val existing = mutable.value.notes
        mutable.value = mutable.value.copy(
            loading = existing.isEmpty(),
            refreshing = existing.isNotEmpty(),
            error = null,
            offline = false
        )
        viewModelScope.launch {
            try {
                ensureSameSeller()
                val response: com.bizbite.printer.features.creditDebitNotes.types.FeatureResult<NoteListPage> = repository.listNotes()
                ensureSameSeller()
                cache.put(sellerAtCreation, response.value.notes)
                mutable.value = mutable.value.copy(
                    notes = response.value.notes,
                    loading = false,
                    refreshing = false,
                    offline = false,
                    source = response.source,
                    error = null,
                    page = 1
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                val cached = cache.get(identity.id())
                mutable.value = mutable.value.copy(
                    notes = mutable.value.notes.ifEmpty { cached },
                    loading = false,
                    refreshing = false,
                    offline = cached.isNotEmpty() || mutable.value.notes.isNotEmpty(),
                    error = failure.message?.takeIf(String::isNotBlank) ?: "Could not load notes. Check your connection and retry."
                )
            }
        }
    }

    fun setInitialInvoiceFilter(invoiceId: String?) {
        if (!invoiceId.isNullOrBlank() && mutable.value.filters.invoiceId != invoiceId) {
            mutable.value = mutable.value.copy(filters = mutable.value.filters.copy(invoiceId = invoiceId), page = 1)
        }
    }

    fun updateFilters(filters: CreditDebitNoteFilters) {
        mutable.value = mutable.value.copy(filters = filters, page = 1)
    }

    fun clearFilters() = updateFilters(CreditDebitNoteFilters())

    fun loadMore() {
        val current = mutable.value
        if (current.hasMore) mutable.value = current.copy(page = current.page + 1)
    }

    fun openDetail(note: CreditDebitNote, sourceOverride: DataSource? = null) {
        if (sourceOverride == DataSource.MOCK) {
            mutable.value = mutable.value.copy(selectedNote = note, selectedSource = DataSource.MOCK, simulatedSettlement = null, simulatedPayments = null, detailError = null)
            return
        }
        val id = note.id
        if (id.isNullOrBlank()) {
            mutable.value = mutable.value.copy(selectedNote = note, selectedSource = mutable.value.source, detailError = null)
            return
        }
        loadDetail(id)
    }

    fun retryDetail() {
        mutable.value.selectedNote?.id?.let(::loadDetail)
    }

    private fun loadDetail(noteId: String) {
        mutable.value = mutable.value.copy(detailLoading = true, detailError = null, simulatedSettlement = null, simulatedPayments = null)
        viewModelScope.launch {
            try {
                ensureSameSeller()
                val result = repository.noteDetail(noteId)
                val supplement = if (result.source == DataSource.REAL &&
                    (result.value.settlement == null || result.value.timeline.isEmpty())
                ) repository.noteDetailSimulation().value else null
                val payments = if (result.source == DataSource.REAL) runCatching { repository.invoicePaymentsMock().value }.getOrNull() else null
                ensureSameSeller()
                mutable.value = mutable.value.copy(
                    selectedNote = result.value,
                    selectedSource = result.source,
                    simulatedSettlement = supplement,
                    simulatedPayments = payments,
                    detailLoading = false,
                    detailError = null
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                mutable.value = mutable.value.copy(
                    detailLoading = false,
                    detailError = failure.message?.takeIf(String::isNotBlank) ?: "Could not load note details. Retry."
                )
            }
        }
    }

    fun closeDetail() {
        mutable.value = mutable.value.copy(selectedNote = null, selectedSource = null, simulatedSettlement = null, simulatedPayments = null, detailError = null)
    }

    private fun ensureSameSeller() {
        if (sellerAtCreation.isNullOrBlank() || identity.id() != sellerAtCreation) {
            cache.clear()
            mutable.value = CreditDebitNotesUiState()
            error("Seller session changed. Reload notes after signing in again.")
        }
    }
}
