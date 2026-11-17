package com.bizbite.printer.data

import com.bizbite.printer.data.auth.BbnAuthRepository
import com.bizbite.printer.data.remote.BbnApi
import com.bizbite.printer.data.remote.BbnUser
import com.bizbite.printer.data.remote.LoginAppRequest
import com.bizbite.printer.data.remote.LoginResponse
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BbnAuthRepositoryTest {
    private val api = mockk<BbnApi>()
    private val repository = BbnAuthRepository(api)

    @Test fun loginUsesAppEndpointContractAndMapsAgreement() = runBlocking {
        val request = LoginAppRequest(identifier = "9876543210", pin = "2468")
        coEvery { api.loginApp(request) } returns LoginResponse(
            success = true,
            token = "seller-jwt",
            isAgreed = false,
            user = BbnUser("seller-user", "Store Owner", "Seller", "9876543210")
        )

        val result = repository.login("9876543210", "2468")

        assertEquals("seller-jwt", result.token)
        assertFalse(result.isAgreed)
        assertEquals("Seller", result.role)
        coVerify(exactly = 1) { api.loginApp(request) }
    }

    @Test fun loginRejectsUnsupportedRoles() = runBlocking {
        coEvery { api.loginApp(any()) } returns LoginResponse(
            success = true,
            token = "customer-jwt",
            isAgreed = true,
            user = BbnUser("customer", "Customer", "Customer", "9876543210")
        )

        val failure = org.junit.Assert.assertThrows(IllegalArgumentException::class.java) {
            runBlocking { repository.login("9876543210", "2468") }
        }
        assertTrue(failure.message!!.contains("Seller and Staff"))
    }
}
