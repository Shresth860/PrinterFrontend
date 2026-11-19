package com.bizbite.printer.data.security

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

@Singleton
class TokenStore @Inject constructor(@ApplicationContext context: Context) {
    private val preferences = EncryptedSharedPreferences.create(
        context,
        "auth_tokens",
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    private val generation = MutableStateFlow(0L)
    private val authInvalidationGeneration = MutableStateFlow(0L)
    val changes = generation.asStateFlow()
    val authInvalidations = authInvalidationGeneration.asStateFlow()

    @Synchronized fun accessToken(): String? = preferences.getString(ACCESS, null)
    @Synchronized fun refreshToken(): String? = preferences.getString(REFRESH, null)
    @Synchronized fun accessTokenExpiresAtMillis(): Long? =
        if (preferences.contains(ACCESS_EXPIRES_AT)) preferences.getLong(ACCESS_EXPIRES_AT, 0L) else null

    @Synchronized fun isAccessTokenExpired(nowEpochMillis: Long = System.currentTimeMillis()): Boolean =
        accessTokenExpiresAtMillis()?.let { it <= nowEpochMillis } == true

    @Synchronized fun save(access: String, refresh: String, expiresAtEpochMillis: Long) {
        preferences.edit()
            .putString(ACCESS, access)
            .putString(REFRESH, refresh)
            .putLong(ACCESS_EXPIRES_AT, expiresAtEpochMillis)
            .apply()
        generation.value += 1
    }
    @Synchronized fun clear() {
        preferences.edit().clear().apply()
        generation.value += 1
    }
    @Synchronized fun clearAfterAuthFailure() {
        preferences.edit().clear().apply()
        generation.value += 1
        authInvalidationGeneration.value += 1
    }

    private companion object {
        const val ACCESS = "access"
        const val REFRESH = "refresh"
        const val ACCESS_EXPIRES_AT = "access_expires_at"
    }
}
