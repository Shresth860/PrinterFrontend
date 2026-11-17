package com.bizbite.printer.data

import com.bizbite.printer.data.remote.CounterBillingRequest
import com.bizbite.printer.data.remote.LoginInitResponse
import com.bizbite.printer.data.remote.OrderItemRequest
import com.bizbite.printer.data.remote.PrintModeRequest
import com.bizbite.printer.data.remote.VariantSelection
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BbnContractTest {
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    @Test fun loginInitSupportsOtpAndDevDirectSessionShapes() {
        val otp = json.decodeFromString<LoginInitResponse>("""{"success":true,"role":"Seller","reqId":"provider-id","message":"sent"}""")
        assertTrue(otp.success)
        assertEquals("provider-id", otp.reqId)
        assertNull(otp.token)

        val direct = json.decodeFromString<LoginInitResponse>("""{"success":true,"token":"session","user":{"id":"u1","role":"Seller"},"profile":{},"isAgreed":true}""")
        assertEquals("session", direct.token)
        assertNotNull(direct.user)
    }

    @Test fun counterBillingSerializesOnlyBackendAcceptedFieldsAndVariantName() {
        val request = CounterBillingRequest(
            customerName = "Walk-in", customerPhone = "", items = listOf(
                OrderItemRequest(productId = "p1", quantity = 2, variant = VariantSelection("Large"))
            ), paymentMethod = "CASH", orderType = "takeaway", manualDiscountType = "PERCENT", manualDiscountValue = 10.0
        )
        val body = json.encodeToString(request)
        assertTrue(body.contains("\"customer_name\""))
        assertTrue(body.contains("\"product_id\""))
        assertTrue(body.contains("\"variant\":{\"name\":\"Large\"}"))
        assertTrue(body.contains("\"manual_discount_type\":\"PERCENT\""))
        assertFalse(body.contains("total_amount"))
        assertFalse(body.contains("unit_price"))
    }

    @Test fun printConfirmRequestIsPerDocument() {
        assertEquals("{\"mode\":\"invoice\"}", json.encodeToString(PrintModeRequest("invoice")))
        assertEquals("{\"mode\":\"kot\"}", json.encodeToString(PrintModeRequest("kot")))
    }
}
