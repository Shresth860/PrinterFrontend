package com.bizbite.printer.ui.billing

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.content.Context
import com.bizbite.printer.data.remote.BbnApi
import com.bizbite.printer.data.remote.BbnHttpException
import com.bizbite.printer.data.auth.AutoPairManager
import com.bizbite.printer.data.auth.AutoPairState
import com.bizbite.printer.data.security.BillingAttemptStore
import com.bizbite.printer.data.security.SellerIdentityStore
import com.bizbite.printer.data.remote.PrintModeRequest
import com.bizbite.printer.data.printing.SellerReceiptMapper
import com.bizbite.printer.domain.model.JobStatus
import com.bizbite.printer.domain.repository.PrinterRepository
import com.bizbite.printer.service.PrintForegroundService
import dagger.hilt.android.qualifiers.ApplicationContext
import com.bizbite.printer.data.menu.BillLine
import com.bizbite.printer.data.menu.Category
import com.bizbite.printer.data.menu.MenuItem
import com.bizbite.printer.data.menu.MenuRepository
import com.bizbite.printer.data.menu.SellerTable
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.coroutines.delay

data class BillingUiState(
    val categories: List<Category> = emptyList(), val products: List<MenuItem> = emptyList(), val cart: List<CartItem> = emptyList(),
    val tables: List<SellerTable> = emptyList(), val selectedCategory: String? = null, val query: String = "", val customerName: String = "", val customerPhone: String = "", val note: String = "",
    val discountType: DiscountType = DiscountType.FLAT, val discountInput: String = "", val couponCode: String = "", val orderType: OrderType = OrderType.TAKEAWAY,
    val paymentMode: PaymentMode = PaymentMode.CASH, val selectedTable: String? = null, val loading: Boolean = true, val processing: Boolean = false, val error: String? = null, val printStatus: String? = null, val billOutcomeUnknown: Boolean = false,
    val pairingState: AutoPairState = AutoPairState.NotPaired, val printRetryAvailable: Boolean = false
) {
    val totals get() = calculateBillTotals(cart, discountType, discountInput.toDoubleOrNull() ?: 0.0)
}

@kotlinx.serialization.Serializable
private data class PendingBillingPrint(
    val order: com.bizbite.printer.data.remote.SellerOrderDto,
    val mode: String,
    val sellerKey: String? = null
)

@HiltViewModel
class BillingViewModel @Inject constructor(
    private val repository: MenuRepository,
    private val api: BbnApi,
    private val printerRepository: PrinterRepository,
    private val receiptMapper: SellerReceiptMapper,
    private val attemptStore: BillingAttemptStore,
    private val autoPairManager: AutoPairManager,
    private val sellerIdentity: SellerIdentityStore,
    @ApplicationContext private val context: Context
) : ViewModel() {
    private val confirming = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
    private val pendingPrint = kotlinx.coroutines.flow.MutableStateFlow(
        attemptStore.pendingPrintJson()?.let { runCatching { Json.decodeFromString<PendingBillingPrint>(it) }.getOrNull() }
    )
    private val mutableState = MutableStateFlow(BillingUiState(
        billOutcomeUnknown = !attemptStore.requestJson().isNullOrBlank(),
        printRetryAvailable = pendingPrint.value != null
    ))
    val state = mutableState.asStateFlow()

    init {
        refreshMenu()
        viewModelScope.launch {
            autoPairManager.state.collect { pairing ->
                mutableState.value = mutableState.value.copy(pairingState = pairing)
            }
        }
        pendingPrint.value?.let {
            mutableState.value = mutableState.value.copy(
                printStatus = "Order ${it.order.id ?: "created"} is waiting for printer setup. Retry print when setup is ready.",
                printRetryAvailable = true
            )
        }
        viewModelScope.launch {
            printerRepository.jobs.collect { jobs ->
                jobs.filter { it.id.startsWith(SELLER_PRINT_PREFIX) && it.status == JobStatus.SUCCESS }.forEach { job ->
                    val key = job.id.removePrefix(SELLER_PRINT_PREFIX)
                    val split = key.lastIndexOf(':')
                    if (split <= 0 || !confirming.add(job.id)) return@forEach
                    viewModelScope.launch {
                        val mode = key.substring(split + 1)
                        while (true) {
                            try {
                                val response = api.confirmPrint(key.substring(0, split), PrintModeRequest(mode))
                                if (!response.success) error(response.message ?: "Print confirmation failed.")
                                mutableState.value = mutableState.value.copy(printStatus = "$mode transport write completed and backend confirmation recorded.")
                                break
                            } catch (cancelled: CancellationException) { throw cancelled }
                            catch (failure: Exception) {
                                mutableState.value = mutableState.value.copy(printStatus = "$mode transport write completed; backend confirmation pending. Will retry safely.")
                                delay(30_000)
                            }
                        }
                    }
                }
            }
        }
    }
    fun refreshMenu() = viewModelScope.launch {
        mutableState.value = mutableState.value.copy(loading = true, error = null)
        try {
            val categories = repository.categories()
            val products = repository.items()
            val tables = repository.tables()
            val current = mutableState.value
            val restored = if (current.billOutcomeUnknown && current.cart.isEmpty()) restoreAttempt(current, products, tables) else current
            mutableState.value = restored.copy(categories = categories, products = products, tables = tables, loading = false)
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: Exception) { mutableState.value = mutableState.value.copy(loading = false, error = if (failure is BbnHttpException) failure.message else "Could not load seller menu. Check your connection and retry.") }
    }
    fun clearError() { mutableState.value = mutableState.value.copy(error = null) }
    fun resolveUnknownAttemptAfterHistoryCheck() {
        attemptStore.clear()
        mutableState.value = mutableState.value.copy(billOutcomeUnknown = false, error = null)
    }
    fun query(value: String) { mutableState.value = mutableState.value.copy(query = value) }
    fun category(value: String?) { mutableState.value = mutableState.value.copy(selectedCategory = value) }
    fun add(item: MenuItem, variantName: String? = null, addons: List<com.bizbite.printer.data.remote.AddonDto> = emptyList()) {
        val current = mutableState.value
        if (current.billOutcomeUnknown) return
        val found = current.cart.firstOrNull { it.item.id == item.id && it.variantName == variantName && it.addons.map { a -> a.id }.toSet() == addons.map { a -> a.id }.toSet() }
        mutableState.value = current.copy(cart = if (found == null) current.cart + CartItem(item, variantName = variantName, addons = addons) else current.cart.map { if (it.key == found.key) it.copy(quantity = it.quantity + 1) else it })
    }
    fun changeQuantity(key: String, delta: Int) { if (mutableState.value.billOutcomeUnknown) return; mutableState.value = mutableState.value.copy(cart = mutableState.value.cart.mapNotNull { if (it.key != key) it else it.copy(quantity = it.quantity + delta).takeIf { row -> row.quantity > 0 } }) }
    fun customerName(v: String) { if (!mutableState.value.billOutcomeUnknown) mutableState.value = mutableState.value.copy(customerName = v) }
    fun customerPhone(v: String) { if (!mutableState.value.billOutcomeUnknown) mutableState.value = mutableState.value.copy(customerPhone = v.filter(Char::isDigit).take(10)) }
    fun note(v: String) { if (!mutableState.value.billOutcomeUnknown) mutableState.value = mutableState.value.copy(note = v) }
    fun discount(v: String) { if (!mutableState.value.billOutcomeUnknown) mutableState.value = mutableState.value.copy(discountInput = v.filter { it.isDigit() || it == '.' }) }
    fun coupon(v: String) { if (!mutableState.value.billOutcomeUnknown) mutableState.value = mutableState.value.copy(couponCode = v.take(80)) }
    fun discountType(v: DiscountType) { if (!mutableState.value.billOutcomeUnknown) mutableState.value = mutableState.value.copy(discountType = v) }
    fun orderType(v: OrderType) { if (!mutableState.value.billOutcomeUnknown) mutableState.value = mutableState.value.copy(orderType = v, selectedTable = if (v == OrderType.TAKEAWAY) null else mutableState.value.selectedTable) }
    fun payment(v: PaymentMode) { if (!mutableState.value.billOutcomeUnknown) mutableState.value = mutableState.value.copy(paymentMode = v) }
    fun table(v: String) { if (!mutableState.value.billOutcomeUnknown) mutableState.value = mutableState.value.copy(selectedTable = v) }

    fun generate(mode: String, onMessage: (String) -> Unit) {
        val s = mutableState.value
        if (s.billOutcomeUnknown) { onMessage("A previous bill request may have reached BBN. Check Seller order history before submitting another bill."); return }
        if (pendingPrint.value != null) { onMessage("A bill is already created and waiting to print. Use Retry print instead of creating another bill."); return }
        if (s.processing || s.cart.isEmpty() || (s.orderType == OrderType.DINE_IN && s.selectedTable == null)) return
        if (s.discountType == DiscountType.PERCENT && (s.discountInput.toDoubleOrNull() ?: 0.0) > 100.0) { onMessage("discount_percent"); return }
        if (s.discountType == DiscountType.FLAT && (s.discountInput.toDoubleOrNull() ?: 0.0) > s.totals.subtotal) { onMessage("discount_flat"); return }
        if (!hasValidatedNetwork()) { onMessage("Connect to the internet before generating a bill. The cart has been kept."); return }
        mutableState.value = s.copy(processing = true, error = null)
        viewModelScope.launch {
            try {
                val request = repository.request(
                    s.cart.map { BillLine(it.item, it.quantity, it.variantName, it.addons, s.note.takeIf(String::isNotBlank)) },
                    s.customerName, s.customerPhone,
                    if (s.paymentMode == PaymentMode.UPI) "UPI_MANUAL" else "CASH",
                    if (s.orderType == OrderType.DINE_IN) "dine-in" else "takeaway",
                    s.selectedTable, s.discountType.name, s.discountInput.toDoubleOrNull() ?: 0.0, s.couponCode
                )
                attemptStore.save(Json.encodeToString(request))
                val created = repository.createBill(request)
                attemptStore.clear()
                pendingPrint.value = PendingBillingPrint(created, mode, sellerIdentity.id())
                attemptStore.savePendingPrint(Json.encodeToString(pendingPrint.value!!))
                mutableState.value = mutableState.value.copy(cart = emptyList(), discountInput = "", couponCode = "", processing = false, printStatus = "Order ${created.id ?: "created"}; server subtotal ₹${created.subtotal ?: 0.0}, discount ₹${created.discountAmount ?: 0.0}, tax ₹${created.taxAmount ?: 0.0}, total ₹${created.totalAmount ?: 0.0}; preparing $mode print.")
                try {
                    mutableState.value = mutableState.value.copy(pairingState = AutoPairState.Pairing, printStatus = "Printer setup in progress, thoda wait karein.")
                    val pairing = autoPairManager.ensurePaired()
                    if (pairing is AutoPairState.Failed && !printerRepository.isPaired()) error(pairing.reason)
                    val orderId = created.id ?: error("Bill was created but the response omitted the order ID required for print tracking.")
                    val reserved = api.reservePrintNumbers(orderId, PrintModeRequest(mode))
                    if (!reserved.success) error(reserved.message ?: "Print numbers could not be reserved.")
                    val numbers = reserved.numbers ?: error("Print number response did not include numbers.")
                    if (!printerRepository.isPaired()) error("Order created. Pair the printer account in Settings before printing.")
                    val docModes = if (mode == "both") listOf("invoice", "kot") else listOf(mode)
                    for (docMode in docModes) {
                        val number = if (docMode == "invoice") numbers.invoiceNumber ?: "INV-${numbers.invoiceSeq ?: ""}" else numbers.kotNumber?.toString() ?: ""
                        val jobId = "$SELLER_PRINT_PREFIX$orderId:$docMode"
                        printerRepository.enqueueSellerPrint(jobId, receiptMapper.map(created, docMode, number))
                    }
                    if (!PrintForegroundService.start(context, PrintForegroundService.ACTION_PRINT_QUEUE)) {
                        error("Order created and print jobs queued, but Android could not start the print service.")
                    }
                    pendingPrint.value = null
                    attemptStore.clearPendingPrint()
                    mutableState.value = mutableState.value.copy(printRetryAvailable = false, processing = false)
                    mutableState.value = mutableState.value.copy(printStatus = "Order ${created.id} created. Waiting for physical printer result.")
                    onMessage("Bill created: ${created.id}. Server total ₹${created.totalAmount ?: 0.0}. Print job queued.")
                } catch (failure: Exception) {
                    val safeMessage = if (failure is BbnHttpException) failure.message else failure.message?.takeIf { it.contains("printer", true) || it.contains("paired", true) } ?: "Printing could not start; the order remains created and unconfirmed."
                    mutableState.value = mutableState.value.copy(processing = false, printRetryAvailable = true)
                    mutableState.value = mutableState.value.copy(printStatus = "Order ${created.id} created. $safeMessage")
                    onMessage("Bill created: ${created.id}, server total ₹${created.totalAmount ?: 0.0}. ${failure.message ?: "Printing could not start."}")
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) {
                val message = if (failure is BbnHttpException) failure.message else "The bill result is uncertain because the connection ended. Check seller order history before retrying."
                val unknown = failure !is BbnHttpException || failure.statusCode >= 500
                if (!unknown) attemptStore.clear()
                mutableState.value = mutableState.value.copy(processing = false, error = message, billOutcomeUnknown = unknown)
                onMessage(message)
            }
        }
    }

    fun retryPrint(onMessage: (String) -> Unit) {
        val pending = pendingPrint.value ?: return
        if (mutableState.value.processing) return
        if (pending.sellerKey.isNullOrBlank() || pending.sellerKey != sellerIdentity.id()) {
            mutableState.value = mutableState.value.copy(printRetryAvailable = false, printStatus = "Sign in to the original seller account to retry this saved bill's print.")
            return
        }
        mutableState.value = mutableState.value.copy(processing = true, printRetryAvailable = false, printStatus = "Printer setup in progress, thoda wait karein.")
        viewModelScope.launch {
            try {
                val pairing = autoPairManager.ensurePaired()
                if (pairing is AutoPairState.Failed && !printerRepository.isPaired()) error(pairing.reason)
                val orderId = pending.order.id ?: error("Saved bill is missing its order ID.")
                val reserved = api.reservePrintNumbers(orderId, PrintModeRequest(pending.mode))
                if (!reserved.success) error(reserved.message ?: "Print numbers could not be reserved.")
                val numbers = reserved.numbers ?: error("Print number response did not include numbers.")
                val docModes = if (pending.mode == "both") listOf("invoice", "kot") else listOf(pending.mode)
                val existingJobs = printerRepository.jobs.first().associateBy { it.id }
                for (docMode in docModes) {
                    val jobId = "$SELLER_PRINT_PREFIX$orderId:$docMode"
                    val existing = existingJobs[jobId]
                    if (existing == null || existing.status == JobStatus.FAILED || existing.status == JobStatus.CANCELLED) {
                        val number = if (docMode == "invoice") numbers.invoiceNumber ?: "INV-${numbers.invoiceSeq ?: ""}" else numbers.kotNumber?.toString() ?: ""
                        printerRepository.enqueueSellerPrint(jobId, receiptMapper.map(pending.order, docMode, number))
                    }
                }
                if (!PrintForegroundService.start(context, PrintForegroundService.ACTION_PRINT_QUEUE)) {
                    error("Order $orderId is saved; Android could not start the print service.")
                }
                pendingPrint.value = null
                attemptStore.clearPendingPrint()
                mutableState.value = mutableState.value.copy(processing = false, printRetryAvailable = false, printStatus = "Order $orderId created. Waiting for physical printer result.")
                onMessage("Bill created: $orderId. Print job queued.")
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                mutableState.value = mutableState.value.copy(processing = false, printRetryAvailable = true, printStatus = "Order ${pending.order.id ?: "created"} is saved. ${failure.message ?: "Printer setup failed."}")
                onMessage("Order ${pending.order.id ?: "created"} is already billed. Retry print; do not create another bill.")
            }
        }
    }

    private fun restoreAttempt(state: BillingUiState, products: List<MenuItem>, tables: List<SellerTable>): BillingUiState {
        val request = attemptStore.requestJson()?.let { runCatching { Json.decodeFromString<com.bizbite.printer.data.remote.CounterBillingRequest>(it) }.getOrNull() } ?: return state
        val cart = request.items.mapNotNull { line ->
            val item = products.firstOrNull { it.id == line.productId || it.id == line.comboId } ?: return@mapNotNull null
            val addons = line.addons.mapNotNull { addon -> item.addons.firstOrNull { it.id == addon.addonId || it.name == addon.name } }
            CartItem(item, line.quantity, line.variant?.name, addons)
        }
        return state.copy(
            cart = cart, customerName = request.customerName, customerPhone = request.customerPhone.takeUnless { it == "0000000000" }.orEmpty(),
            selectedTable = request.tableId?.takeIf { id -> tables.any { it.id == id } },
            orderType = if (request.orderType == "dine-in") OrderType.DINE_IN else OrderType.TAKEAWAY,
            paymentMode = if (request.paymentMethod == "UPI_MANUAL") PaymentMode.UPI else PaymentMode.CASH,
            discountType = if (request.manualDiscountType == "PERCENT") DiscountType.PERCENT else DiscountType.FLAT,
            discountInput = if (request.manualDiscountValue > 0) request.manualDiscountValue.toString() else "",
            couponCode = request.couponCode ?: request.discountCode.orEmpty(),
            note = request.items.firstOrNull()?.instruction.orEmpty(),
            error = if (cart.size != request.items.size) "Some products from the uncertain request are no longer available in the current menu." else state.error
        )
    }

    private fun hasValidatedNetwork(): Boolean {
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? android.net.ConnectivityManager ?: return false
        val network = manager.activeNetwork ?: return false
        val capabilities = manager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            capabilities.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    private companion object { const val SELLER_PRINT_PREFIX = "bbn-print:" }
}
