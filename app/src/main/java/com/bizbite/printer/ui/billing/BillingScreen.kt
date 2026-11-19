package com.bizbite.printer.ui.billing

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.bizbite.printer.R
import com.bizbite.printer.data.menu.Category
import com.bizbite.printer.data.menu.MenuItem
import com.bizbite.printer.data.menu.SellerTable
import com.bizbite.printer.ui.billing.components.BillSummary
import com.bizbite.printer.ui.billing.components.CartItemRow
import com.bizbite.printer.ui.billing.components.ProductCard
import com.bizbite.printer.ui.billing.components.TablePicker
import com.bizbite.printer.ui.components.AppSearchField
import com.bizbite.printer.ui.components.PrimaryButton
import com.bizbite.printer.ui.components.SectionHeader
import com.bizbite.printer.ui.theme.BizBiteTheme
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

@Composable
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
fun BillingScreen(
    viewModel: BillingViewModel = hiltViewModel(),
    onOpenHistory: () -> Unit = {},
    onOpenPrintSettings: () -> Unit = {}
) {
    val state by viewModel.state.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var showBill by remember { mutableStateOf(false) }
    fun showBillMessage(message: String, closeSheet: Boolean = false) {
        scope.launch {
            val resource = when (message) {
                "discount_percent" -> R.string.discount_percent_error
                "discount_flat" -> R.string.discount_flat_error
                "bill_generated" -> R.string.bill_generated_message
                else -> null
            }
            val safeMessage = if (message.startsWith("Bill created:")) message.substringBefore(". ") + "." else message
            snackbar.showSnackbar(resource?.let(context::getString) ?: safeMessage)
            if (closeSheet && message.startsWith("Bill created:")) showBill = false
        }
    }
    val visible = state.products.filter { (state.selectedCategory == null || it.categoryId == state.selectedCategory) && it.name.contains(state.query, true) }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= 600.dp
        Scaffold(snackbarHost = { SnackbarHost(snackbar) }, bottomBar = {
            if (!wide) PrimaryButton(
                stringResource(R.string.view_bill, state.cart.sumOf { it.quantity }, formatRupees(state.totals.grandTotal)),
                { showBill = true },
                Modifier.fillMaxWidth().padding(12.dp),
                enabled = state.cart.isNotEmpty() && !state.billOutcomeUnknown,
                icon = R.drawable.ic_billing
            )
        }) { padding ->
            Row(Modifier.fillMaxSize().padding(padding).padding(16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Column(Modifier.weight(if (wide) 0.62f else 1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SectionHeader(stringResource(R.string.billing_tab))
                    if (state.loading) Text(stringResource(R.string.loading_menu), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    state.error?.let { error ->
                        Text(error, color = MaterialTheme.colorScheme.error)
                        TextButton(onClick = viewModel::refreshMenu, modifier = Modifier.height(48.dp)) { Text(stringResource(R.string.retry_load_menu)) }
                    }
                    AppSearchField(state.query, viewModel::query, stringResource(R.string.search_menu))
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(state.selectedCategory == null, { viewModel.category(null) }, label = { Text(stringResource(R.string.all_category)) })
                        state.categories.forEach { category -> FilterChip(state.selectedCategory == category.id, { viewModel.category(category.id) }, label = { Text(category.name) }) }
                    }
                    LazyVerticalGrid(columns = GridCells.Adaptive(155.dp), modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        if (!state.loading && visible.isEmpty()) item { Text(stringResource(R.string.no_products_match), color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        items(visible, key = { it.id }) { item -> ProductCard(item, onAdd = { variant, addons -> viewModel.add(item, variant, addons) }) }
                    }
                }
                if (wide) {
                    Card(Modifier.weight(0.38f).fillMaxSize(), shape = MaterialTheme.shapes.medium, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)) {
                        BillContent(state, viewModel, onOpenHistory, onOpenPrintSettings, enabled = !state.processing, onGenerate = { mode -> viewModel.generate(mode) { msg -> showBillMessage(msg) } }, onRetryPrint = { viewModel.retryPrint { msg -> showBillMessage(msg) } }, modifier = Modifier.padding(16.dp).verticalScroll(rememberScrollState()))
                    }
                }
            }
        }
    }
    if (showBill) ModalBottomSheet(onDismissRequest = { showBill = false }) {
        BillContent(state, viewModel, onOpenHistory, onOpenPrintSettings, enabled = !state.processing, onGenerate = { mode -> viewModel.generate(mode) { msg -> showBillMessage(msg, closeSheet = true) } }, onRetryPrint = { viewModel.retryPrint { msg -> showBillMessage(msg) } }, modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp))
    }
}

@Composable
private fun BillContent(
    state: BillingUiState,
    vm: BillingViewModel,
    onOpenHistory: () -> Unit,
    onOpenPrintSettings: () -> Unit,
    enabled: Boolean,
    onGenerate: (String) -> Unit,
    onRetryPrint: () -> Unit,
    modifier: Modifier = Modifier
) {
    var mode by remember { mutableStateOf("both") }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionHeader(stringResource(R.string.current_bill))
        state.printStatus?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        if (state.billOutcomeUnknown) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer), shape = MaterialTheme.shapes.medium) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.bill_outcome_unknown), color = MaterialTheme.colorScheme.error)
                    TextButton(onClick = onOpenHistory, modifier = Modifier.height(48.dp)) { Text(stringResource(R.string.open_seller_order_history)) }
                    TextButton(onClick = vm::resolveUnknownAttemptAfterHistoryCheck, modifier = Modifier.height(48.dp)) { Text(stringResource(R.string.allow_new_bill_after_history_check)) }
                }
            }
        }
        if (state.cart.isEmpty()) Text(stringResource(R.string.cart_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
        state.cart.forEach { item -> CartItemRow(item, { delta -> vm.changeQuantity(item.key, delta) }, { vm.changeQuantity(item.key, -item.quantity) }) }
        OutlinedInput(state.customerName, vm::customerName, stringResource(R.string.customer_name))
        OutlinedInput(state.customerPhone, vm::customerPhone, stringResource(R.string.phone_label))
        Text(stringResource(R.string.walk_in_phone_note), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        OutlinedInput(state.note, vm::note, stringResource(R.string.special_note), minLines = 2)
        OutlinedInput(state.couponCode, vm::coupon, stringResource(R.string.coupon_discount_code))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            FilterChip(state.discountType == DiscountType.FLAT, { vm.discountType(DiscountType.FLAT) }, label = { Text(stringResource(R.string.rupees_short)) })
            FilterChip(state.discountType == DiscountType.PERCENT, { vm.discountType(DiscountType.PERCENT) }, label = { Text(stringResource(R.string.discount_percent_symbol)) })
            androidx.compose.material3.OutlinedTextField(state.discountInput, vm::discount, Modifier.weight(1f), label = { Text(stringResource(R.string.discount)) }, singleLine = true)
        }
        SimpleDropdown(stringResource(R.string.order_type), state.orderType.name, listOf("TAKEAWAY", "DINE_IN")) { vm.orderType(OrderType.valueOf(it)) }
        if (state.orderType == OrderType.DINE_IN) TablePicker(state.tables, state.selectedTable, vm::table)
        SimpleDropdown(stringResource(R.string.payment_mode), state.paymentMode.name, listOf("CASH", "UPI")) { vm.payment(PaymentMode.valueOf(it)) }
        ReceiptPreview(state)
        SectionHeader(stringResource(R.string.print_settings), actionText = stringResource(R.string.open_action), onAction = onOpenPrintSettings)
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("invoice" to R.string.invoice, "kot" to R.string.kot, "both" to R.string.both).forEach { (value, label) ->
                FilterChip(selected = mode == value, onClick = { mode = value }, label = { Text(stringResource(label)) })
            }
        }
        BillSummary(state.totals)
        if (state.printRetryAvailable) {
            PrimaryButton(stringResource(R.string.retry_print_only), onRetryPrint, Modifier.fillMaxWidth(), enabled = enabled)
        } else {
            PrimaryButton(
                stringResource(R.string.print_now),
                { onGenerate(mode) },
                Modifier.fillMaxWidth(),
                enabled = enabled && !state.billOutcomeUnknown && state.cart.isNotEmpty() && (state.orderType != OrderType.DINE_IN || state.selectedTable != null)
            )
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun OutlinedInput(value: String, onValueChange: (String) -> Unit, label: String, minLines: Int = 1) {
    androidx.compose.material3.OutlinedTextField(value, onValueChange, Modifier.fillMaxWidth(), label = { Text(label) }, singleLine = minLines == 1, minLines = minLines, shape = MaterialTheme.shapes.small)
}

@Composable
private fun ReceiptPreview(state: BillingUiState) {
    Card(shape = MaterialTheme.shapes.medium, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)) {
        Column(Modifier.fillMaxWidth().padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(painterResource(R.drawable.ic_printer), contentDescription = stringResource(R.string.login_logo_description), tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
            Text(stringResource(R.string.brand_name), style = MaterialTheme.typography.titleMedium)
            androidx.compose.material3.HorizontalDivider()
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(stringResource(R.string.receipt_date), color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(DateFormat.getDateTimeInstance().format(Date()), style = MaterialTheme.typography.bodySmall)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(stringResource(R.string.receipt_order_number), color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(stringResource(R.string.receipt_order_pending), style = MaterialTheme.typography.bodySmall)
            }
            state.cart.forEach { line ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(stringResource(R.string.receipt_line_item, line.quantity, line.item.name), style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                    Text(formatRupees(line.item.price * line.quantity), style = MaterialTheme.typography.bodySmall)
                }
            }
            androidx.compose.material3.HorizontalDivider()
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(stringResource(R.string.receipt_total), style = MaterialTheme.typography.titleSmall)
                Text(formatRupees(state.totals.grandTotal), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            }
            Text(stringResource(R.string.receipt_thank_you), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun SimpleDropdown(label: String, selected: String, values: List<String>, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    @Composable fun display(value: String): String = stringResource(when (value) {
        "TAKEAWAY" -> R.string.takeaway
        "DINE_IN" -> R.string.dine_in
        "CASH" -> R.string.cash
        else -> R.string.upi
    })
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        TextButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth().height(48.dp)) { Text(display(selected)) }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            values.forEach { value -> DropdownMenuItem(text = { Text(display(value)) }, onClick = { onSelect(value); expanded = false }) }
        }
    }
}

@Preview(name = "Billing Light")
@Composable
private fun BillingLightPreview() = BizBiteTheme(darkTheme = false) {
    BillingPreviewContent()
}

@Preview(name = "Billing Dark")
@Composable
private fun BillingDarkPreview() = BizBiteTheme(darkTheme = true) {
    BillingPreviewContent()
}

@Composable
private fun BillingPreviewContent() {
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionHeader(stringResource(R.string.billing_tab))
        AppSearchField("", {}, stringResource(R.string.search_menu))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(true, {}, label = { Text(stringResource(R.string.all_category)) })
            FilterChip(false, {}, label = { Text(stringResource(R.string.category_example)) })
        }
        ProductCard(MenuItem("preview", "Paneer Tikka", 220.0, "category"), { _, _ -> })
        PrimaryButton(stringResource(R.string.view_bill, 1, formatRupees(231.0)), {}, Modifier.fillMaxWidth())
    }
}
