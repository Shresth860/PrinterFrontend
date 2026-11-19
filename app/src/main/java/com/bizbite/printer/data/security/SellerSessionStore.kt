package com.bizbite.printer.data.security

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Seller JWT storage is intentionally isolated from the printer-device TokenStore. */
@Singleton
class SellerSessionStore @Inject constructor(@ApplicationContext context: Context) {
    private val prefs = EncryptedSharedPreferences.create(
        context,
        "bbn_seller_session",
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )
    @Synchronized fun token(): String? = prefs.getString(KEY_TOKEN, null)
    @Synchronized fun save(token: String) { prefs.edit().putString(KEY_TOKEN, token).apply() }
    @Synchronized fun clear() { prefs.edit().remove(KEY_TOKEN).apply() }
    private companion object { const val KEY_TOKEN = "seller_jwt" }
}

@Singleton
class SellerIdentityStore @Inject constructor(@ApplicationContext context: Context) {
    private val prefs = EncryptedSharedPreferences.create(
        context, "bbn_seller_identity", MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV, EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )
    @Synchronized fun save(id: String?, role: String?, phone: String?, name: String?) {
        prefs.edit().putString("id", id).putString("role", role).putString("phone", phone).putString("name", name).apply()
    }
    @Synchronized fun id(): String? = prefs.getString("id", null)
    @Synchronized fun role(): String? = prefs.getString("role", null)
    @Synchronized fun clear() { prefs.edit().clear().apply() }
}

/** Non-secret owner marker used to avoid silently reusing a device pairing after a seller switch. */
@Singleton
class PairedSellerStore @Inject constructor(@ApplicationContext context: Context) {
    private val prefs = context.getSharedPreferences("paired_seller_owner", Context.MODE_PRIVATE)
    @Synchronized fun sellerKey(): String? = prefs.getString(KEY, null)
    @Synchronized fun save(sellerKey: String) { prefs.edit().putString(KEY, sellerKey).apply() }
    @Synchronized fun clear() { prefs.edit().remove(KEY).apply() }
    private companion object { const val KEY = "paired_for_seller" }
}
