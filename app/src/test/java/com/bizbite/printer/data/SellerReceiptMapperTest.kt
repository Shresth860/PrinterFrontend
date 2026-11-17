package com.bizbite.printer.data

import com.bizbite.printer.data.printing.SellerReceiptMapper
import com.bizbite.printer.data.remote.SellerOrderDto
import kotlinx.serialization.json.Json
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SellerReceiptMapperTest {
    @Test fun invoiceAndKotMapTheServerOrderSnapshotIndependently() {
        val order = Json { ignoreUnknownKeys = true }.decodeFromString<SellerOrderDto>("""{
            "_id":"order-1","order_type":"dine-in","table_number":"5","customer_name":"Guest",
            "subtotal":100,"discount_amount":10,"tax_amount":5,"total_amount":95,
            "items":[{"name":"Paneer Wrap","price":100,"quantity":1,"variant":{"name":"Large"},"addons":[{"name":"Cheese","price":10}],"instruction":"Mild"}]
        }""")
        val mapper = SellerReceiptMapper()
        val invoice = mapper.map(order, "invoice", "INV-0001")
        val kot = mapper.map(order, "kot", "17")
        assertTrue(invoice.contains("INV-0001"))
        assertTrue(invoice.contains("Paneer Wrap"))
        assertTrue(invoice.contains("₹95.0"))
        assertTrue(kot.contains("KITCHEN ORDER TICKET"))
        assertTrue(kot.contains("Paneer Wrap"))
        assertFalse(kot.contains("TOTAL"))
    }
}
