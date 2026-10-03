package com.queukat.sbsgeorgia.data.remote

import java.math.BigDecimal
import java.time.LocalDate
import java.time.format.DateTimeParseException
import java.util.Locale
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

internal fun parseNbgDailyRates(json: Json, body: String, requestedDate: LocalDate): OfficialFxRemoteResult = try {
    val root = json.parseToJsonElement(body)
    val groups = when (root) {
        is JsonArray -> root
        is JsonObject -> root["items"] as? JsonArray ?: errorInvalidResponse()
        else -> errorInvalidResponse()
    }
    if (groups.isEmpty()) {
        OfficialFxRemoteResult.NotFound
    } else {
        require(groups.size == 1) { "Expected one daily rate group." }
        val group = groups.single() as? JsonObject ?: errorInvalidResponse()
        val effectiveDate = requireNotNull(group.text("date")).asRateDate()
        // NBG itself carries the last valid rate over weekends/holidays for the requested date.
        // This is not a client-side lookup of a neighbouring day.
        require(!effectiveDate.isAfter(requestedDate)) { "Rate is not yet valid on the requested date." }
        val currencies = group["currencies"] as? JsonArray ?: errorInvalidResponse()
        val rates = currencies.map { item ->
            parseCurrency(item as? JsonObject ?: errorInvalidResponse(), effectiveDate)
        }
        require(rates.map { it.currencyCode }.distinct().size == rates.size) { "Duplicate currency codes." }
        if (rates.isEmpty()) OfficialFxRemoteResult.NotFound else OfficialFxRemoteResult.Success(rates)
    }
} catch (_: IllegalArgumentException) {
    OfficialFxRemoteResult.Error("NBG returned an invalid exchange-rate response.")
}

private fun parseCurrency(currency: JsonObject, effectiveDate: LocalDate): RemoteFxRate {
    val code = requireNotNull(currency.text("code")).uppercase(Locale.ROOT)
    require(code.matches(Regex("[A-Z]{3}"))) { "Invalid currency code." }
    val units = (currency.text("quantity") ?: currency.text("units"))?.toIntOrNull()
    require(units != null && units > 0) { "Missing or invalid currency units." }
    val rateText = currency.text("rate") ?: currency.text("rateFormated")
    val rate = rateText?.replace(",", "")?.toBigDecimalOrNull()
    require(rate != null && rate > BigDecimal.ZERO) { "Missing or invalid exchange rate." }
    require(requireNotNull(currency.text("validFromDate")).asRateDate() == effectiveDate) {
        "Currency validity does not match the daily rate group."
    }
    return RemoteFxRate(code, units, rate)
}

private fun JsonObject.text(key: String): String? = (get(key) as? JsonPrimitive)?.contentOrNull

private fun String.asRateDate(): LocalDate = try {
    LocalDate.parse(take(10))
} catch (_: DateTimeParseException) {
    errorInvalidResponse()
}

private fun errorInvalidResponse(): Nothing = throw IllegalArgumentException("Invalid NBG response structure.")
