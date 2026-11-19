package com.bizbite.printer.ui.billing

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.bizbite.printer.features.creditDebitNotes.types.*
import com.bizbite.printer.features.creditDebitNotes.utils.DraftLineInput
import com.bizbite.printer.features.creditDebitNotes.utils.NoteDraftCalculations
import com.bizbite.printer.features.creditDebitNotes.viewmodels.CreateNoteViewModel
import java.math.BigDecimal

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateCreditDebitNoteScreen(
    orderId: String,
    type: NoteType,
    canCreate: Boolean,
    canOverrideTax: Boolean,
    onBack: () -> Unit,
    onOpenNoteDetail: (CreditDebitNote, DataSource?) -> Unit,
    viewModel: CreateNoteViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val notesForInvoice = state.notes.filter { it.invoiceId == state.orderId }
    var reasonExpanded by remember { mutableStateOf(false) }
    var taxTreatmentExpanded by remember { mutableStateOf(false) }
    var priorDialog by remember { mutableStateOf(false) }
    LaunchedEffect(orderId, type) { viewModel.start(orderId, type) }
    LaunchedEffect(state.outcomeNote?.id) { state.outcomeNote?.let { onOpenNoteDetail(it, state.outcomeSource) } }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        TextButton(onClick = onBack) { Text("Back") }
        Text("Create ${if (type == NoteType.CREDIT) "Credit" else "Debit"} Note", style = MaterialTheme.typography.titleLarge)
        if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (state.offline) {
            Text("Offline or data is incomplete. This form is read-only until invoice and notes refresh.", color = MaterialTheme.colorScheme.error)
            TextButton(onClick = viewModel::loadForm) { Text("Retry refresh") }
        }
        state.error?.let { Text(it, color = if (state.statusUnknown) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.error) }
        state.statusUnknown.let { unknown ->
            if (unknown) Button(onClick = viewModel::checkStatus, enabled = !state.statusChecking) {
                Text(if (state.statusChecking) "Checking…" else "Check status")
            }
        }
        state.invoice?.let { invoice ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Invoice ${invoice.number ?: "unavailable"}", style = MaterialTheme.typography.titleMedium)
                    Text("Customer: ${invoice.customerName ?: "unavailable"} · ${maskPhone(invoice.customerPhone)}")
                    Text("Total: ${invoice.total?.formatInr() ?: "unavailable"}")
                    Text("Paid: ${invoice.paid?.formatInr() ?: "unavailable"} · Due: ${invoice.due?.formatInr() ?: "unavailable"}")
                    Text("Note number: Auto (server)")
                    Text("Previous note: ${state.previousNote?.number ?: "First Note"}")
                    TextButton(onClick = { priorDialog = true }, enabled = state.priorNotes.isNotEmpty()) { Text("View prior notes (${state.priorNotes.size})") }
                }
            }
        }

        ExposedDropdownMenuBox(expanded = reasonExpanded, onExpandedChange = { reasonExpanded = it }) {
            OutlinedTextField(
                value = state.reason.orEmpty(), onValueChange = {}, readOnly = true,
                label = { Text("Reason") }, isError = state.fieldErrors.containsKey("reason"),
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(reasonExpanded) },
                modifier = Modifier.fillMaxWidth().menuAnchor()
            )
            ExposedDropdownMenu(expanded = reasonExpanded, onDismissRequest = { reasonExpanded = false }) {
                state.reasons.forEach { reason -> DropdownMenuItem(text = { Text(reason) }, onClick = { viewModel.reason(reason); reasonExpanded = false }) }
            }
        }
        state.fieldErrors["reason"]?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        if (state.reason == CreateNoteViewModel.OTHER_REASON) {
            OutlinedTextField(state.description, viewModel::description, label = { Text("Description (required)") }, modifier = Modifier.fillMaxWidth(), isError = state.fieldErrors.containsKey("description"))
            state.fieldErrors["description"]?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }

        Text("Items", style = MaterialTheme.typography.titleMedium)
        state.invoice?.items.orEmpty().forEach { item ->
            val id = item.id
            val adjusted = if (id == null) BigDecimal.ZERO else NoteDraftCalculations.previouslyAdjustedQuantity(state.notes, state.orderId, id)
            val available = item.quantity?.subtract(adjusted)?.max(BigDecimal.ZERO)
            val qtyText = id?.let { state.quantities[it] }.orEmpty()
            val qty = qtyText.toBigDecimalOrNull() ?: BigDecimal.ZERO
            val isRateDifference = state.reason == CreateNoteViewModel.RATE_DIFFERENCE
            val isAdditionalQuantity = type == NoteType.DEBIT && state.reason == CreateNoteViewModel.ADDITIONAL_QUANTITY
            val rateText = id?.let { state.rateOverrides[it] }.orEmpty()
            val taxText = id?.let { state.gstOverrides[it] }.orEmpty()
            val lineTotal = remember(item, qtyText, rateText, taxText, state.reason) {
                runCatching {
                    if (qty <= BigDecimal.ZERO) null else NoteDraftCalculations.totals(
                        listOf(DraftLineInput(item, qty, rateText.toBigDecimalOrNull(), taxText.toBigDecimalOrNull())),
                        rateIsDelta = isRateDifference
                    )
                }.getOrNull()
            }
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(item.name ?: "Item name unavailable", style = MaterialTheme.typography.titleSmall)
                    Text("Invoice qty: ${item.quantity?.stripTrailingZeros()?.toPlainString() ?: "unavailable"}")
                    Text("Previously adjusted: ${adjusted.stripTrailingZeros().toPlainString()} · Estimate")
                    Text("Available qty: ${available?.stripTrailingZeros()?.toPlainString() ?: "unavailable"}")
                    Text("Rate: ${item.rate?.formatInr() ?: "unavailable"} · GST: ${item.taxPercent?.toPlainString()?.plus("%") ?: "unavailable"}")
                    if (id == null) Text("Item ID unavailable; this item cannot be submitted.", color = MaterialTheme.colorScheme.error)
                    else OutlinedTextField(
                        value = qtyText, onValueChange = { value -> if (value.matches(Regex("^\\d*(\\.\\d{0,3})?$"))) viewModel.quantity(id, value) },
                        label = { Text("Selected qty") }, isError = state.fieldErrors.containsKey(id), singleLine = true, modifier = Modifier.fillMaxWidth()
                    )
                    if (id != null) state.fieldErrors[id]?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    if (isRateDifference || isAdditionalQuantity) {
                        OutlinedTextField(rateText, { value -> if (value.matches(Regex("^\\d*(\\.\\d{0,2})?$"))) id?.let { viewModel.rate(it, value) } },
                            label = { Text(if (isRateDifference) "Rate difference (delta per unit)" else "Rate (required)") },
                            isError = id?.let { state.fieldErrors.containsKey("rate:$it") } == true, singleLine = true, modifier = Modifier.fillMaxWidth())
                        id?.let { state.fieldErrors["rate:$it"]?.let { message -> Text(message, color = MaterialTheme.colorScheme.error) } }
                    }
                    if (isAdditionalQuantity || (state.taxOverrideEnabled && canOverrideTax)) {
                        OutlinedTextField(taxText, { value -> if (value.matches(Regex("^\\d*(\\.\\d{0,2})?$"))) id?.let { viewModel.gst(it, value) } },
                            label = { Text("GST percent${if (isAdditionalQuantity) " (required)" else " override"}") },
                            isError = id?.let { state.fieldErrors.containsKey("gst:$it") } == true, singleLine = true, modifier = Modifier.fillMaxWidth())
                        id?.let { state.fieldErrors["gst:$it"]?.let { message -> Text(message, color = MaterialTheme.colorScheme.error) } }
                    }
                    Text("Line total: ${lineTotal?.total?.formatInr() ?: "unavailable"}${if (lineTotal != null) " · Estimate" else ""}")
                }
            }
        }
        state.fieldErrors["items"]?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        if (canOverrideTax) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("GST override (reason required)")
                Switch(checked = state.taxOverrideEnabled, onCheckedChange = viewModel::enableTaxOverride, enabled = !state.offline && !state.saving && state.reason != null)
            }
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Discount Adjustment", style = MaterialTheme.typography.titleMedium)
                Text("Backend support pending, saved nahi hoga. Discount is excluded from totals.", color = MaterialTheme.colorScheme.tertiary)
                Text("Existing invoice discount details are unavailable here; double-discount eligibility cannot be verified.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(state.discountLevel, { viewModel.setDiscount(level = it) }, label = { Text("Level") }, modifier = Modifier.weight(1f))
                    OutlinedTextField(state.discountType, { viewModel.setDiscount(type = it) }, label = { Text("Type") }, modifier = Modifier.weight(1f))
                }
                OutlinedTextField(state.discountApplyTo, { viewModel.setDiscount(applyTo = it) }, label = { Text("Apply to") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(state.discountValue, { viewModel.setDiscount(value = it) }, label = { Text("Value") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(state.discountReason, { viewModel.setDiscount(reason = it) }, label = { Text("Discount reason") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(state.discountDescription, { viewModel.setDiscount(description = it) }, label = { Text("Description") }, modifier = Modifier.fillMaxWidth())
            }
        }
        ExposedDropdownMenuBox(expanded = taxTreatmentExpanded, onExpandedChange = { taxTreatmentExpanded = it }) {
            OutlinedTextField(state.taxTreatmentLabel, {}, readOnly = true, label = { Text("Tax treatment (display only)") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(taxTreatmentExpanded) }, modifier = Modifier.fillMaxWidth().menuAnchor())
            ExposedDropdownMenu(expanded = taxTreatmentExpanded, onDismissRequest = { taxTreatmentExpanded = false }) {
                listOf("Commercial-only", "GST reversal").forEach { option -> DropdownMenuItem(text = { Text(option) }, onClick = { viewModel.taxTreatment(option); taxTreatmentExpanded = false }) }
            }
        }
        Text("Changing this selector does not save tax treatment. App mein save karne se GST liability auto kam nahi hoti.", color = MaterialTheme.colorScheme.error)

        val selectedInputs = state.invoice?.items.orEmpty().mapNotNull { item ->
            val id = item.id ?: return@mapNotNull null
            val quantity = state.quantities[id]?.toBigDecimalOrNull()?.takeIf { it > BigDecimal.ZERO } ?: return@mapNotNull null
            DraftLineInput(item, quantity, state.rateOverrides[id]?.toBigDecimalOrNull(), state.gstOverrides[id]?.toBigDecimalOrNull())
        }
        val isRateDifference = state.reason == CreateNoteViewModel.RATE_DIFFERENCE
        val totals = selectedInputs.takeIf { it.isNotEmpty() }
            ?.let { runCatching { NoteDraftCalculations.totals(it, isRateDifference) }.getOrNull() }
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Totals · Estimate", style = MaterialTheme.typography.titleMedium)
                Text("Subtotal: ${totals?.subtotal?.formatInr() ?: "unavailable"}")
                Text("GST: ${totals?.tax?.formatInr() ?: "unavailable"}")
                Text("Estimated total: ${totals?.total?.formatInr() ?: "unavailable"}")
                Text("Final amount is calculated by BBN backend.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        state.fieldErrors["form"]?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Button(onClick = viewModel::save, enabled = canCreate && !state.loading && !state.offline && !state.saving && !state.statusUnknown && state.outcomeNote == null, modifier = Modifier.fillMaxWidth()) {
            Text(if (state.saving) "Saving…" else "Save / Issue")
        }
        OutlinedButton(onClick = viewModel::save, enabled = canCreate && !state.loading && !state.offline && !state.saving && !state.statusUnknown && state.outcomeNote == null, modifier = Modifier.fillMaxWidth()) {
            Text(if (state.saving) "Saving…" else "Print & Save")
        }
        Text("Print & Save creates one note only. PDF/printing is not connected in Phase 3.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(20.dp))
    }
    if (priorDialog) AlertDialog(
        onDismissRequest = { priorDialog = false },
        title = { Text("Prior invoice notes") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (notesForInvoice.isEmpty()) Text("No prior notes found.")
            notesForInvoice.forEach { Text("${it.number ?: "Note number unavailable"} · ${it.type?.apiValue?.uppercase() ?: "Type unavailable"} · ${it.status.name} · ${(it.total ?: it.amount)?.formatInr() ?: "Amount unavailable"}") }
        } },
        confirmButton = { TextButton(onClick = { priorDialog = false }) { Text("Close") } }
    )
}

private fun maskPhone(phone: String?): String {
    val digits = phone.orEmpty().filter(Char::isDigit)
    return if (digits.isEmpty()) "Phone unavailable" else "•••• ${digits.takeLast(4)}"
}
