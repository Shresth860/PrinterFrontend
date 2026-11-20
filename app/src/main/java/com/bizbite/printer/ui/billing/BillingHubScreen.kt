package com.bizbite.printer.ui.billing

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.bizbite.printer.data.security.SellerIdentityStore
import com.bizbite.printer.features.creditDebitNotes.permissions.SellerNoteRolePolicy
import com.bizbite.printer.features.creditDebitNotes.types.*
import com.bizbite.printer.features.creditDebitNotes.utils.CreditDebitNoteFilters
import com.bizbite.printer.features.creditDebitNotes.viewmodels.CreditDebitNotesViewModel
import com.bizbite.printer.features.creditDebitNotes.viewmodels.CreateNoteViewModel
import com.bizbite.printer.ui.screens.SellerOrderHistoryScreen
import kotlinx.serialization.json.*

private enum class BillingTab(val title: String) { COUNTER("Counter Billing"), HISTORY("Order History"), NOTES("Credit/Debit Notes") }

@Composable
fun BillingHubScreen(onOpenPrintSettings: () -> Unit = {}) {
    var tab by remember { mutableStateOf(BillingTab.COUNTER) }
    var placeholder by remember { mutableStateOf<String?>(null) }
    var invoiceFilter by remember { mutableStateOf<String?>(null) }
    var createSelection by remember { mutableStateOf<Pair<String, NoteType>?>(null) }
    var createdNote by remember { mutableStateOf<Pair<CreditDebitNote, DataSource?>?>(null) }
    val identity: SellerIdentityStore = hiltViewModel<BillingHubIdentityViewModel>().identity
    val permissions = remember(identity.role()) { SellerNoteRolePolicy.permissions(identity.role()) }
    Column(Modifier.fillMaxSize()) {
        if (createSelection != null) {
            val (orderId, noteType) = createSelection!!
            Box(Modifier.weight(1f).fillMaxWidth()) {
                CreateCreditDebitNoteScreen(
                    orderId = orderId,
                    type = noteType,
                    canCreate = permissions.canCreateNotes,
                    canOverrideTax = permissions.canOverrideNoteTax,
                    onBack = { createSelection = null },
                    onOpenNoteDetail = { note, source -> createdNote = note to source; createSelection = null; tab = BillingTab.NOTES }
                )
            }
        } else if (placeholder != null) {
            Column(Modifier.weight(1f).fillMaxWidth()) {
                TextButton(onClick = { placeholder = null }) { Text("Back to billing") }
                Text(placeholder!!, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(20.dp))
                Text("This screen is a Phase 2 navigation placeholder.", modifier = Modifier.padding(horizontal = 20.dp))
            }
        } else {
            ScrollableTabRow(selectedTabIndex = tab.ordinal) {
                BillingTab.entries.forEach { item -> Tab(selected = item == tab, onClick = { tab = item }, text = { Text(item.title) }) }
            }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when (tab) {
                    BillingTab.COUNTER -> BillingScreen(onOpenHistory = { tab = BillingTab.HISTORY }, onOpenPrintSettings = onOpenPrintSettings)
                    BillingTab.HISTORY -> SellerOrderHistoryScreen(
                        onCreditNote = { createSelection = it to NoteType.CREDIT },
                        onDebitNote = { createSelection = it to NoteType.DEBIT },
                        onFullCancellation = { placeholder = "Full Invoice Cancellation" },
                        onViewNotes = { invoiceFilter = it; tab = BillingTab.NOTES },
                        onPaymentHistory = { placeholder = "Payment/Refund History" },
                        canCreateNotes = permissions.canCreateNotes,
                        canFullCancel = permissions.canFullCancelInvoice
                    )
                    BillingTab.NOTES -> CreditDebitNotesScreen(initialInvoiceId = invoiceFilter, onCancelPlaceholder = { }, openNote = createdNote)
                }
            }
        }
    }
}

@dagger.hilt.android.lifecycle.HiltViewModel
class BillingHubIdentityViewModel @javax.inject.Inject constructor(val identity: SellerIdentityStore) : androidx.lifecycle.ViewModel()

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun CreditDebitNotesScreen(
    initialInvoiceId: String?, onCancelPlaceholder: () -> Unit, openNote: Pair<CreditDebitNote, DataSource?>? = null,
    vm: CreditDebitNotesViewModel = hiltViewModel()
) {
    val state by vm.state.collectAsState()
    var search by remember { mutableStateOf("") }
    var customer by remember { mutableStateOf("") }
    var invoice by remember { mutableStateOf("") }
    var reason by remember { mutableStateOf("") }
    var from by remember { mutableStateOf("") }
    var to by remember { mutableStateOf("") }
    var type by remember { mutableStateOf<NoteType?>(null) }
    var status by remember { mutableStateOf<NoteStatus?>(null) }
    LaunchedEffect(Unit) { vm.setInitialInvoiceFilter(initialInvoiceId); vm.refresh() }
    LaunchedEffect(openNote?.first?.id) { openNote?.let { vm.openDetail(it.first, it.second) } }
    LaunchedEffect(search, customer, invoice, reason, from, to, type, status, initialInvoiceId) {
        vm.updateFilters(CreditDebitNoteFilters(search, type, status, customer, initialInvoiceId, invoice, reason, from, to))
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item { Text("Credit/Debit Notes", style = MaterialTheme.typography.titleLarge) }
        item { TextButton(onClick = vm::refresh, enabled = !state.loading && !state.refreshing) { Text(if (state.refreshing) "Refreshing…" else "Refresh notes") } }
        item { Text("Filters loaded notes par lagte hain", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        item { OutlinedTextField(search, { search = it }, label = { Text("Search notes") }, modifier = Modifier.fillMaxWidth(), singleLine = true) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(from, { from = it }, label = { Text("From YYYY-MM-DD") }, modifier = Modifier.weight(1f), singleLine = true)
                OutlinedTextField(to, { to = it }, label = { Text("To YYYY-MM-DD") }, modifier = Modifier.weight(1f), singleLine = true)
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(type == null, { type = null }, label = { Text("All types") })
                FilterChip(type == NoteType.CREDIT, { type = if (type == NoteType.CREDIT) null else NoteType.CREDIT }, label = { Text("Credit") })
                FilterChip(type == NoteType.DEBIT, { type = if (type == NoteType.DEBIT) null else NoteType.DEBIT }, label = { Text("Debit") })
            }
        }
        item {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(status == null, { status = null }, label = { Text("All status") })
                listOf(NoteStatus.ISSUED, NoteStatus.PARTIALLY_SETTLED, NoteStatus.SETTLED, NoteStatus.CANCELLED).forEach { value ->
                    FilterChip(status == value, { status = if (status == value) null else value }, label = { Text(value.name.replace('_', ' '), maxLines = 1) })
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(customer, { customer = it }, label = { Text("Customer") }, modifier = Modifier.weight(1f), singleLine = true)
                OutlinedTextField(invoice, { invoice = it }, label = { Text("Invoice no.") }, modifier = Modifier.weight(1f), singleLine = true)
            }
        }
        item { OutlinedTextField(reason, { reason = it }, label = { Text("Reason") }, modifier = Modifier.fillMaxWidth(), singleLine = true) }
        if (state.offline) item { Text("Offline · showing session-cached notes", color = MaterialTheme.colorScheme.tertiary) }
        state.source?.takeIf { it == DataSource.MOCK }?.let { item { Text("Simulation / Mock", color = MaterialTheme.colorScheme.tertiary) } }
        state.error?.let { message -> item { Text(message, color = MaterialTheme.colorScheme.error); TextButton(onClick = vm::refresh) { Text("Retry") } } }
        if (state.loading) item { CircularProgressIndicator() }
        else if (state.visibleNotes.isEmpty()) item { Text(if (state.notes.isEmpty()) "No notes found." else "No notes match these filters.") }
        items(state.visibleNotes, key = { it.id ?: it.number ?: it.hashCode().toString() }) { note ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("${note.number ?: "Note"} · ${note.type?.apiValue?.uppercase() ?: "Unknown type"}", style = MaterialTheme.typography.titleMedium)
                    Text("Invoice: ${note.invoiceNumber ?: note.invoiceId ?: "—"}")
                    Text("${note.customerName ?: "Customer unavailable"} · ${maskPhone(note.customerPhone)}")
                    Text("${note.createdAt ?: "Date unavailable"} · ${note.status.name}")
                    Text("${(note.total ?: note.amount)?.formatInr() ?: "Amount unavailable"}")
                    Row {
                        TextButton(onClick = { vm.openDetail(note) }) { Text("View detail") }
                        TextButton(onClick = onCancelPlaceholder, enabled = false) { Text("Cancel (Phase 4)") }
                    }
                }
            }
        }
        if (state.hasMore) item { OutlinedButton(onClick = vm::loadMore, modifier = Modifier.fillMaxWidth()) { Text("Load more") } }
    }
    state.selectedNote?.let { note ->
        ModalBottomSheet(onDismissRequest = vm::closeDetail) {
            Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(note.number ?: "Credit/Debit Note", style = MaterialTheme.typography.titleLarge)
                if (state.detailLoading) CircularProgressIndicator()
                state.detailError?.let { Text(it, color = MaterialTheme.colorScheme.error); TextButton(onClick = vm::retryDetail) { Text("Retry") } }
                Text("Source: ${if (state.selectedSource == DataSource.REAL) "BBN backend" else "Simulation / Mock"}")
                Text("Type: ${note.type?.apiValue ?: "Unavailable"} · Status: ${note.rawStatus ?: note.status.name}")
                Text("Invoice: ${note.invoiceNumber ?: note.invoiceId ?: "Unavailable"}")
                Text("Customer: ${note.customerName ?: "Unavailable"} · ${maskPhone(note.customerPhone)}")
                Text("Date: ${note.createdAt ?: "Unavailable"}")
                Text("Reason: ${note.reason ?: "Unavailable"}")
                Text("Description: ${note.description ?: "Unavailable"}")
                Text("Subtotal: ${note.subtotal?.formatInr() ?: "Unavailable"} · GST: ${note.tax?.formatInr() ?: "Unavailable"}")
                Text("Total: ${(note.total ?: note.amount)?.formatInr() ?: "Unavailable"}")
                TextButton(onClick = {}, enabled = false) { Text("Print note (PDF/print support pending)") }
                Text("Items")
                if (note.items.isEmpty()) Text("Item details unavailable") else note.items.forEach { item ->
                    val raw = item as? JsonObject
                    val details = raw?.entries?.joinToString(" · ") { (key, value) -> "$key: ${value.toString()}" }
                    Text(details?.takeIf(String::isNotBlank) ?: "Item details unavailable")
                }
                state.simulatedSettlement?.let { mock ->
                    Text("Simulation / Mock · Settlement and timeline", color = MaterialTheme.colorScheme.tertiary)
                    val settlement = mock.settlement
                    if (settlement != null) Text("Refunded ${settlement.refunded.formatInr()} · Pending ${settlement.pendingRefund.formatInr()} · Applied ${settlement.applied.formatInr()}")
                    mock.timeline.forEach { Text("${it.event} · ${it.at ?: "Date unavailable"}") }
                    if (settlement == null && mock.timeline.isEmpty()) Text("Simulation / Mock · Settlement details unavailable")
                }
                state.simulatedPayments?.let { payment ->
                    Text("Simulation / Mock · Payment info", color = MaterialTheme.colorScheme.tertiary)
                    Text("Method: ${payment.paymentMethod ?: "Unavailable"} · Status: ${payment.paymentStatus ?: "Unavailable"}")
                    Text("Paid: ${payment.paid?.formatInr() ?: "Unavailable"} · Due: ${payment.due?.formatInr() ?: "Unavailable"}")
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

private fun maskPhone(value: String?): String {
    val digits = value.orEmpty().filter(Char::isDigit)
    return if (digits.isEmpty()) "Phone unavailable" else "•••• ${digits.takeLast(4)}"
}
