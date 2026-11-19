package com.bizbite.printer.features.creditDebitNotes.utils

import com.bizbite.printer.features.creditDebitNotes.types.Money
import java.math.BigDecimal
import java.math.RoundingMode
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

object CreditDebitMoney {
    fun parseRupees(value: JsonElement?): Money? = value.decimalOrNull()?.let(::fromRupees)

    fun parseRupees(value: String?): Money? = value?.let { runCatching { fromRupees(BigDecimal(it)) }.getOrNull() }

    fun parseRupees(value: Double?): Money? = value?.let {
        runCatching { fromRupees(BigDecimal.valueOf(it)) }.getOrNull()
    }

    fun parsePaise(value: JsonElement?): Money? = value.decimalOrNull()?.let { decimal ->
        runCatching { Money(decimal.setScale(0, RoundingMode.UNNECESSARY).longValueExact()) }.getOrNull()
    }

    fun formatInr(money: Money): String = money.formatInr()

    fun toJsonRupees(money: Money): JsonPrimitive = JsonPrimitive(money.decimalRupees())

    private fun fromRupees(value: BigDecimal): Money = Money(
        value.movePointRight(2).setScale(0, RoundingMode.HALF_UP).longValueExact()
    )

    private fun JsonElement?.decimalOrNull(): BigDecimal? =
        (this as? JsonPrimitive)?.contentOrNull?.let { runCatching { BigDecimal(it) }.getOrNull() }
}
