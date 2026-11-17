package com.bizbite.printer.data.printing

import com.bizbite.printer.data.remote.SellerOrderDto
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put

/** Maps the server-created Order snapshot into the existing ReceiptRenderer block format. */
@Singleton
class SellerReceiptMapper @Inject constructor() {
    fun map(order: SellerOrderDto, mode: String, number: String): String {
        require(mode == "invoice" || mode == "kot")
        val blocks = buildJsonArray {
            fun text(value: String, bold: Boolean = false, align: String = "LEFT") {
                add(buildJsonObject { put("type", "TEXT"); put("text", value); put("bold", bold); put("align", align) })
            }
            text(if (mode == "invoice") "TAX INVOICE" else "KITCHEN ORDER TICKET", true, "CENTER")
            text(number, true, "CENTER")
            text("Order: ${order.id.orEmpty()}")
            text("${order.orderType ?: "Order"} · ${order.createdAt.orEmpty()}")
            if (order.tableNumber != null) text("Table: ${order.tableNumber}")
            if (mode == "invoice" && !order.customerName.isNullOrBlank()) text("Customer: ${order.customerName}")
            if (mode == "invoice" && !order.customerPhone.isNullOrBlank()) text("Phone: ${order.customerPhone}")
            add(buildJsonObject { put("type", "DIVIDER") })
            order.items.forEach { element ->
                val item = element as? JsonObject ?: return@forEach
                val quantity = item.text("quantity")?.toIntOrNull() ?: 1
                val unit = item.text("price")?.toDoubleOrNull() ?: 0.0
                val variant = item["variant"]?.let { runCatching { it.jsonObject.text("name") }.getOrNull() }?.let { " ($it)" }.orEmpty()
                val addons = (item["addons"] as? JsonArray)?.mapNotNull { addon -> runCatching { addon.jsonObject.text("name") }.getOrNull() }.orEmpty()
                val instruction = item.text("instruction")?.takeIf(String::isNotBlank)?.let { " · Note: $it" }.orEmpty()
                val name = (item.text("name") ?: "Product") + variant + (if (addons.isEmpty()) "" else " + ${addons.joinToString()}") + instruction
                add(buildJsonObject {
                    put("type", "ITEM_ROW"); put("quantity", quantity.toString()); put("name", name)
                    put("amount", "₹${"%.2f".format(java.util.Locale.US, unit * quantity)}")
                })
            }
            if (mode == "invoice") {
                add(buildJsonObject { put("type", "DIVIDER") })
                add(buildJsonObject { put("type", "TWO_COLUMN"); put("left", "Subtotal"); put("right", "₹${order.subtotal ?: 0.0}") })
                add(buildJsonObject { put("type", "TWO_COLUMN"); put("left", "Discount"); put("right", "₹${order.discountAmount ?: 0.0}") })
                add(buildJsonObject { put("type", "TWO_COLUMN"); put("left", "GST"); put("right", "₹${order.taxAmount ?: 0.0}") })
                add(buildJsonObject { put("type", "TWO_COLUMN"); put("left", "TOTAL"); put("right", "₹${order.totalAmount ?: 0.0}") })
            }
        }
        return buildJsonObject { put("blocks", blocks) }.toString()
    }

    private fun JsonObject.text(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull
}
