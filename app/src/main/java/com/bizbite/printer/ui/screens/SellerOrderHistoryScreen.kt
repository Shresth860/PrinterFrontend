package com.bizbite.printer.ui.screens

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import com.bizbite.printer.data.remote.BbnApi
import com.bizbite.printer.data.remote.BbnHttpException
import com.bizbite.printer.data.remote.PrintModeRequest
import com.bizbite.printer.data.remote.SellerOrderDto
import com.bizbite.printer.data.printing.SellerReceiptMapper
import com.bizbite.printer.domain.model.JobStatus
import com.bizbite.printer.domain.repository.PrinterRepository
import com.bizbite.printer.service.PrintForegroundService
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull

data class SellerHistoryState(val orders: List<SellerOrderDto> = emptyList(), val loading: Boolean = false, val page: Int = 1, val hasMore: Boolean = true, val error: String? = null, val selected: SellerOrderDto? = null, val printMessage: String? = null)

@HiltViewModel
class SellerOrderHistoryViewModel @Inject constructor(
    private val api: BbnApi,
    private val printer: PrinterRepository,
    private val receiptMapper: SellerReceiptMapper,
    @ApplicationContext private val context: Context
) : ViewModel() {
    private val mutable = MutableStateFlow(SellerHistoryState())
    private val confirming = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
    val state = mutable.asStateFlow()
    init {
        viewModelScope.launch {
            printer.jobs.collect { jobs ->
                jobs.filter { it.id.startsWith(PRINT_PREFIX) && it.status == JobStatus.SUCCESS }.forEach { job ->
                    val key = job.id.removePrefix(PRINT_PREFIX)
                    val split = key.lastIndexOf(':')
                    if (split <= 0 || !confirming.add(job.id)) return@forEach
                    viewModelScope.launch { confirmAfterWrite(key.substring(0, split), key.substring(split + 1), job.id) }
                }
            }
        }
    }
    fun loadNext() {
        val s = mutable.value
        if (s.loading || !s.hasMore) return
        viewModelScope.launch {
            mutable.value = s.copy(loading = true, error = null)
            try {
                val response = api.orders(s.page, 20)
                if (!response.success) error(response.message ?: "Could not load seller orders.")
                mutable.value = mutable.value.copy(orders = s.orders + response.orders, page = s.page + 1, hasMore = response.orders.size == 20, loading = false)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { mutable.value = mutable.value.copy(loading = false, error = if (failure is BbnHttpException) failure.message else "Could not load seller orders. Check your connection and retry.") }
        }
    }
    fun refresh() {
        mutable.value = mutable.value.copy(orders = emptyList(), page = 1, hasMore = true, error = null)
        loadNext()
    }
    fun select(order: SellerOrderDto) {
        if (order.id.isNullOrBlank()) { mutable.value = mutable.value.copy(selected = order); return }
        viewModelScope.launch {
            try {
                val response = api.order(order.id)
                if (!response.success) error(response.message ?: "Could not load order details.")
                mutable.value = mutable.value.copy(selected = response.data ?: response.order ?: order)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { mutable.value = mutable.value.copy(error = if (failure is BbnHttpException) failure.message else "Could not load order details. Check your connection and retry.") }
        }
    }
    fun closeDetails() { mutable.value = mutable.value.copy(selected = null) }

    fun printDocument(order: SellerOrderDto, mode: String) {
        if (mode != "invoice" && mode != "kot") return
        val orderId = order.id ?: return
        if ((mode == "invoice" && order.invoicePrintedAt != null) || (mode == "kot" && order.kotPrintedAt != null)) {
            mutable.value = mutable.value.copy(printMessage = "$mode is already confirmed by BBN.")
            return
        }
        viewModelScope.launch {
            mutable.value = mutable.value.copy(printMessage = null, error = null)
            try {
                if (!printer.isPaired()) error("Pair the printer account in Settings before printing.")
                val jobId = "$PRINT_PREFIX$orderId:$mode"
                val existing = printer.jobs.first().firstOrNull { it.id == jobId }
                when (existing?.status) {
                    JobStatus.SUCCESS -> { confirmAfterWrite(orderId, mode, jobId); return@launch }
                    JobStatus.PENDING, JobStatus.PRINTING -> {
                        if (!PrintForegroundService.start(context, PrintForegroundService.ACTION_PRINT_QUEUE)) error("Print job exists, but Android could not start the print service.")
                        mutable.value = mutable.value.copy(printMessage = "$mode is already queued; it will not be duplicated.")
                        return@launch
                    }
                    JobStatus.FAILED -> {
                        if (existing.errorCode == "POSSIBLE_PARTIAL") error("The previous write may be partial. Inspect the printer output in Printer job history before retrying.")
                    }
                    else -> Unit
                }
                val reservation = api.reservePrintNumbers(orderId, PrintModeRequest(mode))
                if (!reservation.success) error(reservation.message ?: "Print numbers could not be reserved.")
                val numbers = reservation.numbers ?: error("Print number response was empty.")
                val number = if (mode == "invoice") numbers.invoiceNumber ?: error("Invoice number is missing.") else numbers.kotNumber?.toString() ?: error("KOT number is missing.")
                printer.enqueueSellerPrint(jobId, receiptMapper.map(order, mode, number))
                if (!PrintForegroundService.start(context, PrintForegroundService.ACTION_PRINT_QUEUE)) error("Print job queued, but Android could not start the print service.")
                mutable.value = mutable.value.copy(printMessage = "$mode queued. BBN will be confirmed only after the transport write succeeds.")
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { mutable.value = mutable.value.copy(printMessage = if (failure is BbnHttpException) failure.message else failure.message?.takeIf { it.contains("printer", true) || it.contains("print", true) || it.contains("paired", true) } ?: "Print could not start. The order remains unconfirmed.") }
        }
    }

    private suspend fun confirmAfterWrite(orderId: String, mode: String, jobId: String) {
        while (true) {
            try {
                val result = api.confirmPrint(orderId, PrintModeRequest(mode))
                if (!result.success) error(result.message ?: "Print confirmation failed.")
                val current = mutable.value.selected
                if (current?.id == orderId) {
                    val detail = api.order(orderId)
                    mutable.value = mutable.value.copy(selected = detail.data ?: detail.order ?: current, printMessage = "$mode transport write succeeded and BBN confirmation was recorded.")
                }
                return
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) {
                mutable.value = mutable.value.copy(printMessage = "$mode transport write succeeded; BBN confirmation is pending and will retry safely.")
                delay(30_000)
            }
        }
    }

    private companion object { const val PRINT_PREFIX = "bbn-print:" }
}

@Composable
fun SellerOrderHistoryScreen(
    viewModel: SellerOrderHistoryViewModel = hiltViewModel(),
    onCreditNote: (String) -> Unit = {}, onDebitNote: (String) -> Unit = {},
    onFullCancellation: (String) -> Unit = {}, onViewNotes: (String) -> Unit = {},
    onPaymentHistory: (String) -> Unit = {}, canCreateNotes: Boolean = true, canFullCancel: Boolean = false
) {
    val state by viewModel.state.collectAsState()
    LaunchedEffect(Unit) { if (state.orders.isEmpty()) viewModel.loadNext() }
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Seller order history", style = MaterialTheme.typography.titleLarge)
        TextButton(onClick = viewModel::refresh, enabled = !state.loading) { Text("Refresh orders") }
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        state.printMessage?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        state.selected?.let { order ->
            TextButton(onClick = viewModel::closeDetails) { Text("Back to orders") }
            Text("Order ${order.id.orEmpty()}")
            Text("Customer: ${order.customerName ?: "Walk-in Customer"} · ${order.customerPhone ?: ""}")
            Text("Payment: ${order.paymentMethod ?: "—"} · ${order.paymentStatus ?: "—"}")
            Text("Order status: ${order.deliveryStatus ?: "—"} · Type: ${order.orderType ?: "—"}${order.tableNumber?.let { " · Table $it" } ?: ""}")
            Text("Invoice: ${order.invoiceNumber ?: "—"}    KOT: ${order.kotNumber ?: "—"}")
            Text("Subtotal: ${order.subtotal ?: 0.0}    Discount: ${order.discountAmount ?: 0.0}")
            Text("Tax: ${order.taxAmount ?: 0.0}    Total: ${order.totalAmount ?: 0.0}")
            Text("Invoice print: ${order.invoicePrintedAt ?: "Not confirmed"}")
            Text("KOT print: ${order.kotPrintedAt ?: "Not confirmed"}")
            Text("A successful transport write does not verify that paper physically emerged.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (order.invoicePrintedAt == null) OutlinedButton(onClick = { viewModel.printDocument(order, "invoice") }) { Text("Print invoice") }
                if (order.kotPrintedAt == null) OutlinedButton(onClick = { viewModel.printDocument(order, "kot") }) { Text("Print KOT") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { order.id?.let(onCreditNote) }, enabled = canCreateNotes && !order.id.isNullOrBlank()) { Text("Credit Note") }
                OutlinedButton(onClick = { order.id?.let(onDebitNote) }, enabled = canCreateNotes && !order.id.isNullOrBlank()) { Text("Debit Note") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { order.id?.let(onFullCancellation) }, enabled = canFullCancel && !order.id.isNullOrBlank()) { Text("Full Cancellation") }
                OutlinedButton(onClick = { order.id?.let(onViewNotes) }, enabled = !order.id.isNullOrBlank()) { Text("View Notes") }
            }
            OutlinedButton(onClick = { order.id?.let(onPaymentHistory) }, enabled = !order.id.isNullOrBlank()) { Text("Payment/Refund History") }
            order.items.forEach { element ->
                val item = element as? JsonObject ?: return@forEach
                val name = item.str("name") ?: item["product_id"]?.let { product -> runCatching { product.jsonObject.str("name") }.getOrNull() } ?: "Product"
                val quantity = item.str("quantity") ?: "1"
                val price = item.str("price") ?: "—"
                val variant = item["variant"]?.let { runCatching { it.jsonObject.str("name") }.getOrNull() }
                val addons = (item["addons"] as? JsonArray)?.mapNotNull { addon -> runCatching { addon.jsonObject.str("name") }.getOrNull() }.orEmpty()
                Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Text("$quantity × $name${variant?.let { " ($it)" } ?: ""} · ₹$price each")
                    if (addons.isNotEmpty()) Text("Add-ons: ${addons.joinToString()}")
                    item.str("instruction")?.takeIf(String::isNotBlank)?.let { Text("Note: $it") }
                }
            }
        } ?: run {
            if (state.orders.isEmpty() && state.loading) CircularProgressIndicator()
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(state.orders, key = { it.id ?: it.hashCode().toString() }) { order ->
                    Card(Modifier.fillMaxWidth().clickable { viewModel.select(order) }) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(order.invoiceNumber ?: "Order ${order.id?.takeLast(8).orEmpty()}")
                                Text("₹${order.totalAmount ?: 0.0}")
                            }
                            Text("${order.customerName ?: "Walk-in Customer"} · ${order.orderType ?: "Order"} · ${order.createdAt.orEmpty()}")
                            Text("${order.paymentMethod ?: "—"} · ${order.paymentStatus ?: "—"} · ${order.deliveryStatus ?: "—"}")
                            Text("Invoice: ${if (order.invoicePrintedAt == null) "Pending" else "Printed"} · KOT: ${if (order.kotPrintedAt == null) "Pending" else "Printed"}")
                        }
                    }
                }
            }
            if (state.loading) CircularProgressIndicator()
            else if (state.orders.isEmpty()) Text("No seller orders found.")
            else if (state.hasMore) Button(onClick = viewModel::loadNext) { Text("Load more") }
        }
    }
}

private fun JsonObject.str(key: String): String? = this[key]?.jsonPrimitive?.contentOrNull
