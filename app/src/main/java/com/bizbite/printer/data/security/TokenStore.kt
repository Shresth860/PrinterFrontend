package com.bizbite.printer.data.security

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TokenStore @Inject constructor(@ApplicationContext context: Context) {
    private val preferences = EncryptedSharedPreferences.create(
        context,
        "auth_tokens",
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

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
    }
    @Synchronized fun clear() { preferences.edit().clear().apply() }

    private companion object {
        const val ACCESS = "access"
        const val REFRESH = "refresh"
        const val ACCESS_EXPIRES_AT = "access_expires_at"
    }
}
