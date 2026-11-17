package com.bizbite.printer.data.menu

import com.bizbite.printer.data.remote.AddonDto
import com.bizbite.printer.data.remote.BbnApi
import com.bizbite.printer.data.remote.CounterBillingRequest
import com.bizbite.printer.data.remote.OrderItemRequest
import com.bizbite.printer.data.remote.PrintModeRequest
import com.bizbite.printer.data.remote.ProductDto
import com.bizbite.printer.data.remote.SellerOrderDto
import com.bizbite.printer.data.remote.TableDto
import com.bizbite.printer.data.remote.VariantSelection
import javax.inject.Inject
import javax.inject.Singleton

data class Category(val id: String, val name: String)
data class MenuItem(val id: String, val name: String, val price: Double, val categoryId: String, val imageUrl: String? = null, val taxPercent: Double = 5.0, val variants: List<com.bizbite.printer.data.remote.VariantDto> = emptyList(), val addons: List<AddonDto> = emptyList(), val isCombo: Boolean = false, val foodType: String? = null)
data class SellerTable(val id: String, val number: String, val capacity: Int?)
data class BillLine(val item: MenuItem, val quantity: Int, val variantName: String? = null, val addons: List<AddonDto> = emptyList(), val instruction: String? = null)

interface MenuRepository {
    suspend fun categories(): List<Category>
    suspend fun items(): List<MenuItem>
    suspend fun tables(): List<SellerTable>
    fun request(lines: List<BillLine>, customerName: String, customerPhone: String, paymentMethod: String, orderType: String, tableId: String?, discountType: String?, discountValue: Double, code: String?): CounterBillingRequest
    suspend fun createBill(request: CounterBillingRequest): SellerOrderDto
}

@Singleton
class BbnMenuRepository @Inject constructor(private val api: BbnApi) : MenuRepository {
    override suspend fun categories(): List<Category> {
        val response = api.categories()
        response.check()
        return response.categories.filter { it.isActive }.sortedWith(compareBy({ it.displayOrder }, { it.createdAt }))
            .map { Category(it.id, it.name) }
    }

    override suspend fun items(): List<MenuItem> {
        val response = api.products()
        response.check()
        return response.products.filter { it.isAvailable && !it.isDeleted }.map(ProductDto::toMenuItem)
    }

    override suspend fun tables(): List<SellerTable> {
        val response = api.tables()
        response.check()
        return response.tables.filter(TableDto::isActive).sortedBy { it.tableNumber }.map { SellerTable(it.id, it.tableNumber, it.capacity) }
    }

    override suspend fun createBill(request: CounterBillingRequest): SellerOrderDto {
        val response = api.counterBilling(request)
        response.check()
        val order = response.order ?: error("Bill response did not include an order.")
        require(!order.id.isNullOrBlank()) { "The bill response omitted the order ID. Its outcome needs reconciliation in seller order history." }
        return order
    }

    override fun request(lines: List<BillLine>, customerName: String, customerPhone: String, paymentMethod: String, orderType: String, tableId: String?, discountType: String?, discountValue: Double, code: String?) = CounterBillingRequest(
            customerName = customerName.ifBlank { "Walk-in Customer" }, customerPhone = customerPhone,
            items = lines.map { line -> OrderItemRequest(
                productId = line.item.id, quantity = line.quantity,
                variant = line.variantName?.let(::VariantSelection), addons = line.addons.map { com.bizbite.printer.data.remote.AddonSelection(it.id, it.name) }, instruction = line.instruction
            ) }, paymentMethod = paymentMethod, orderType = orderType, tableId = tableId,
            manualDiscountType = discountType, manualDiscountValue = discountValue, couponCode = code?.takeIf(String::isNotBlank)
        )
}

private fun ProductDto.toMenuItem() = MenuItem(
    id, name, (offerPrice?.takeIf { it > 0 } ?: price), categoryId.orEmpty(), image ?: imageUrl,
    taxPercent, variants, addons, isCombo, foodType
)

private fun com.bizbite.printer.data.remote.ApiEnvelope.check() {
    if (!success) error(message?.takeIf(String::isNotBlank) ?: "The BBN server rejected the request.")
}
