package com.bizbite.printer.features.creditDebitNotes.mocks

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

@Singleton
class CreditDebitMockDataSource @Inject constructor(
    @ApplicationContext private val context: Context,
    private val json: Json
) {
    fun load(name: String): JsonObject {
        require(name.matches(Regex("[a-z-]+"))) { "Invalid mock fixture name." }
        return context.assets.open("features/creditDebitNotes/mocks/$name.json").bufferedReader().use {
            json.parseToJsonElement(it.readText()).jsonObject
        }
    }
}
