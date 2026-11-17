package com.bizbite.printer.ui.billing

import com.bizbite.printer.data.menu.MenuItem

data class CartItem(val item: MenuItem, val quantity: Int = 1, val variantName: String? = null, val addons: List<com.bizbite.printer.data.remote.AddonDto> = emptyList()) { val key get() = item.id + (variantName?.let { ":$it" } ?: "") + addons.mapNotNull { it.id }.sorted().joinToString(prefix = ":", separator = ",") }
data class BillTotals(val subtotal: Double, val discount: Double, val gst: Double, val grandTotal: Double)
enum class DiscountType { FLAT, PERCENT }
enum class OrderType { TAKEAWAY, DINE_IN }
enum class PaymentMode { CASH, UPI }

fun calculateBillTotals(items: List<CartItem>, discountType: DiscountType, discountInput: Double, gstRate: Double = 5.0): BillTotals {
    fun effectivePrice(line: CartItem): Double = line.variantName?.let { variant ->
        line.item.variants.firstOrNull { it.name == variant }?.let { it.offerPrice ?: it.priceDelta }
    } ?: line.item.price
    fun lineAmount(line: CartItem) = (effectivePrice(line) + line.addons.sumOf { it.price }) * line.quantity
    val subtotal = items.sumOf(::lineAmount).coerceAtLeast(0.0)
    val rawDiscount = if (discountType == DiscountType.PERCENT) subtotal * discountInput.coerceIn(0.0, 100.0) / 100.0 else discountInput.coerceAtLeast(0.0)
    val discount = rawDiscount.coerceAtMost(subtotal)
    val undiscountedTax = items.sumOf { lineAmount(it) * (if (it.item.taxPercent == 5.0) gstRate else it.item.taxPercent).coerceAtLeast(0.0) / 100.0 }
    val gst = if (subtotal == 0.0) 0.0 else undiscountedTax * ((subtotal - discount) / subtotal)
    return BillTotals(subtotal, discount, gst, kotlin.math.floor(subtotal - discount + gst + 0.5))
}

fun formatRupees(value: Double): String = "Rs ${"%,.2f".format(java.util.Locale("en", "IN"), value)}"
