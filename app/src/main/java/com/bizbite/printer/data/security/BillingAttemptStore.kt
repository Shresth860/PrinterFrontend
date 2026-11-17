package com.bizbite.printer.data.security

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Encrypted record used to prevent a blind second counter-billing POST after an ambiguous timeout. */
@Singleton
class BillingAttemptStore @Inject constructor(@ApplicationContext context: Context) {
    private val prefs = EncryptedSharedPreferences.create(
        context, "bbn_billing_attempt", MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV, EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )
    @Synchronized fun requestJson(): String? = prefs.getString(REQUEST, null)
    @Synchronized fun save(requestJson: String) { prefs.edit().putString(REQUEST, requestJson).putLong(CREATED_AT, System.currentTimeMillis()).apply() }
    @Synchronized fun clear() { prefs.edit().clear().apply() }
    private companion object { const val REQUEST = "request_json"; const val CREATED_AT = "created_at" }
}
