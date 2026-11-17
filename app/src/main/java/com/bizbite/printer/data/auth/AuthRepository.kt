package com.bizbite.printer.data.auth

import com.bizbite.printer.data.remote.BbnApi
import com.bizbite.printer.data.remote.BbnHttpException
import com.bizbite.printer.data.remote.LoginAppRequest
import javax.inject.Inject
import javax.inject.Singleton

data class LoginResult(val token: String, val isAgreed: Boolean, val role: String?, val user: com.bizbite.printer.data.remote.BbnUser?)

interface AuthRepository {
    suspend fun login(phone: String, pin: String): LoginResult
}

@Singleton
class BbnAuthRepository @Inject constructor(private val api: BbnApi) : AuthRepository {
    override suspend fun login(phone: String, pin: String): LoginResult {
        val response = api.loginApp(LoginAppRequest(identifier = phone, pin = pin))
        response.requireSuccess()
        val role = response.user?.role
        require(role == "Seller" || role == "Staff") { "This app supports Seller and Staff accounts only." }
        return LoginResult(response.token ?: error("Login response did not include a session token."), response.isAgreed ?: (role == "Staff"), role, response.user)
    }
}

private fun com.bizbite.printer.data.remote.ApiEnvelope.requireSuccess() {
    if (!success) throw BbnHttpException(200, message?.takeIf(String::isNotBlank) ?: "The server rejected the request.")
}
