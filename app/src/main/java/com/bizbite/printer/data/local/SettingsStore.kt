package com.bizbite.printer.data.local

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore by preferencesDataStore(name = "printer_settings")

@Singleton
class SettingsStore @Inject constructor(@ApplicationContext private val context: Context) {
    private val copiesKey = intPreferencesKey("default_copies")
    private val autoCutKey = booleanPreferencesKey("auto_cut")
    private val cashDrawerKey = booleanPreferencesKey("cash_drawer")

    val copies: Flow<Int> = context.settingsDataStore.data.map { it[copiesKey] ?: 1 }
    val autoCut: Flow<Boolean> = context.settingsDataStore.data.map { it[autoCutKey] ?: true }
    val cashDrawer: Flow<Boolean> = context.settingsDataStore.data.map { it[cashDrawerKey] ?: false }

    suspend fun setCopies(value: Int) { context.settingsDataStore.edit { it[copiesKey] = value.coerceIn(1, 10) } }
    suspend fun setAutoCut(value: Boolean) { context.settingsDataStore.edit { it[autoCutKey] = value } }
    suspend fun setCashDrawer(value: Boolean) { context.settingsDataStore.edit { it[cashDrawerKey] = value } }
}
