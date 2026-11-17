package com.bizbite.printer.di

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BbnAuthInterceptorPolicyTest {
    @Test
    fun appLoginAndOtpEndpointsDoNotReceiveSellerAuthorization() {
        assertFalse(shouldAttachBbnAuthorization("/api/users/login/app"))
        assertFalse(shouldAttachBbnAuthorization("/api/users/login/init"))
        assertFalse(shouldAttachBbnAuthorization("/api/users/verify-otp"))
        assertFalse(shouldAttachBbnAuthorization("/api/users/login"))
        assertTrue(shouldAttachBbnAuthorization("/api/menu-categories/get"))
    }

    @Test
    fun onlyUnauthorizedRequestsWithAuthorizationLogOut() {
        assertFalse(shouldLogoutBbnSession(401, null))
        assertTrue(shouldLogoutBbnSession(401, "Bearer session"))
        assertFalse(shouldLogoutBbnSession(429, "Bearer session"))
    }
}
