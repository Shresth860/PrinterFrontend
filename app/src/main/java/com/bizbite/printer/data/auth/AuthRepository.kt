package com.bizbite.printer.data.auth

import com.bizbite.printer.data.remote.BbnApi
import com.bizbite.printer.data.remote.BbnHttpException
import com.bizbite.printer.data.remote.LoginAppRequest
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

data class LoginResult(val token: String, val isAgreed: Boolean, val role: String?, val user: com.bizbite.printer.data.remote.BbnUser?, val sellerKey: String? = null)

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
        val profile = response.profile as? kotlinx.serialization.json.JsonObject
        val sellerKey = response.user?.id?.takeIf(String::isNotBlank)
            ?: sequenceOf("seller_id", "sellerId", "seller", "_id", "id")
                .mapNotNull { profile?.get(it)?.let { value ->
                    when (value) {
                        is kotlinx.serialization.json.JsonPrimitive -> value.contentOrNull
                        is kotlinx.serialization.json.JsonObject -> value["id"]?.jsonPrimitive?.contentOrNull
                            ?: value["_id"]?.jsonPrimitive?.contentOrNull
                        else -> null
                    }
                }?.takeIf(String::isNotBlank) }.firstOrNull()
        return LoginResult(response.token ?: error("Login response did not include a session token."), response.isAgreed ?: (role == "Staff"), role, response.user, sellerKey)
    }
}

private fun com.bizbite.printer.data.remote.ApiEnvelope.requireSuccess() {
    if (!success) throw BbnHttpException(200, message?.takeIf(String::isNotBlank) ?: "The server rejected the request.")
}
