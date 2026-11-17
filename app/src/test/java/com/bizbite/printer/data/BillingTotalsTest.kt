package com.bizbite.printer.data

import com.bizbite.printer.data.menu.MenuItem
import com.bizbite.printer.data.remote.AddonDto
import com.bizbite.printer.data.remote.VariantDto
import com.bizbite.printer.ui.billing.CartItem
import com.bizbite.printer.ui.billing.DiscountType
import com.bizbite.printer.ui.billing.calculateBillTotals
import org.junit.Assert.assertEquals
import org.junit.Test

class BillingTotalsTest {
    private fun cart(vararg rows: Pair<Double, Int>) = rows.mapIndexed { index, (price, qty) ->
        CartItem(MenuItem("$index", "Item $index", price, "test"), qty)
    }

    @Test fun subtotalSumsPriceTimesQuantity() {
        val result = calculateBillTotals(cart(100.0 to 2, 50.0 to 1), DiscountType.FLAT, 0.0)
        assertEquals(250.0, result.subtotal, 0.001)
        assertEquals(12.5, result.gst, 0.001)
        assertEquals(263.0, result.grandTotal, 0.001)
    }

    @Test fun flatDiscountReducesTaxableAmount() {
        val result = calculateBillTotals(cart(200.0 to 1), DiscountType.FLAT, 30.0)
        assertEquals(30.0, result.discount, 0.001)
        assertEquals(8.5, result.gst, 0.001)
        assertEquals(179.0, result.grandTotal, 0.001)
    }

    @Test fun percentDiscountAndRoundToNearestRupee() {
        val result = calculateBillTotals(cart(199.0 to 1), DiscountType.PERCENT, 10.0)
        assertEquals(19.9, result.discount, 0.001)
        assertEquals(8.955, result.gst, 0.001)
        assertEquals(188.0, result.grandTotal, 0.001)
    }

    @Test fun discountsAreCappedAtSubtotalAndPercentAtOneHundred() {
        val flat = calculateBillTotals(cart(99.0 to 1), DiscountType.FLAT, 500.0)
        val percent = calculateBillTotals(cart(99.0 to 1), DiscountType.PERCENT, 150.0)
        assertEquals(99.0, flat.discount, 0.001)
        assertEquals(0.0, flat.grandTotal, 0.001)
        assertEquals(flat, percent)
    }

    @Test fun variantOfferAndAddonAreIncludedInPreviewSubtotal() {
        val item = MenuItem("v", "Variant item", 100.0, "c", variants = listOf(VariantDto(name = "Large", priceDelta = 150.0, offerPrice = 140.0)), addons = listOf(AddonDto("a", "Cheese", 20.0)))
        val result = calculateBillTotals(listOf(CartItem(item, quantity = 2, variantName = "Large", addons = item.addons)), DiscountType.FLAT, 0.0)
        assertEquals(320.0, result.subtotal, 0.001)
        assertEquals(16.0, result.gst, 0.001)
        assertEquals(336.0, result.grandTotal, 0.001)
    }
}
