package com.queukat.sbsgeorgia.data.remote

import java.math.BigDecimal
import java.time.LocalDate
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NbgFxResponseParserTest {
    private val requestedDate = LocalDate.of(2026, 3, 15)
    private val json = Json

    @Test
    fun acceptsOfficialWeekendCarryForwardAndCurrencyUnits() {
        val result = parse(body()) as OfficialFxRemoteResult.Success
        assertEquals(RemoteFxRate("USD", 1, BigDecimal("2.729")), result.rates[0])
        assertEquals(RemoteFxRate("JPY", 100, BigDecimal("1.7116")), result.rates[1])
    }

    @Test
    fun acceptsRatesEffectiveOnRequestedDay() {
        assertTrue(parse(body().replace("2026-03-14", "2026-03-15")) is OfficialFxRemoteResult.Success)
    }

    @Test
    fun acceptsWrappedResponse() {
        assertTrue(parse("{\"items\":${body()}}") is OfficialFxRemoteResult.Success)
    }

    @Test
    fun publicationTimestampDoesNotReplaceEffectiveDate() {
        // NBG can correct a published record after its effective day.
        assertTrue(parse(body().replace("2026-03-13", "2026-03-16")) is OfficialFxRemoteResult.Success)
    }

    @Test
    fun rejectsFutureAndMismatchedEffectiveDates() {
        assertError(body().replace("2026-03-14", "2026-03-16"))
        assertError(body().replace("\"validFromDate\":\"2026-03-14", "\"validFromDate\":\"2026-03-13"))
        assertError(body().replace("2026-03-14", "invalid-date"))
    }

    @Test
    fun rejectsInvalidUnitsRatesCodesAndDuplicateCurrencies() {
        assertError(body().replace("\"quantity\":100", "\"quantity\":0"))
        assertError(body().replace("\"quantity\":100,", ""))
        assertError(body().replace("1.7116", "-1.7116"))
        assertError(body().replace("1.7116", "0"))
        assertError(body().replace("JPY", "NOT_A_CURRENCY"))
        assertError(body().replace("JPY", "USD"))
    }

    @Test
    fun malformedStructureReturnsErrorInsteadOfThrowingOrGuessing() {
        listOf("not json", "{}", "[null]", "[[]]", "{\"items\":{}}", "[{},{}]").forEach(::assertError)
        assertError(body().replace("\"code\":\"USD\"", "\"code\":{}"))
        assertError(body().replace("\"validFromDate\":\"2026-03-14T00:00:00.000Z\",", ""))
    }

    @Test
    fun emptyResponseIsNotFound() {
        assertEquals(OfficialFxRemoteResult.NotFound, parse("[]"))
        assertEquals(
            OfficialFxRemoteResult.NotFound,
            parse("[{\"date\":\"2026-03-14T00:00:00.000Z\",\"currencies\":[]}]")
        )
    }

    private fun parse(body: String) = parseNbgDailyRates(json, body, requestedDate)

    private fun assertError(body: String) {
        assertTrue(parse(body) is OfficialFxRemoteResult.Error)
    }

    private fun body() = """
        [{"date":"2026-03-14T00:00:00.000Z","currencies":[
          {"code":"USD","quantity":1,"rate":2.729,"validFromDate":"2026-03-14T00:00:00.000Z",
           "date":"2026-03-13T17:01:05.536Z"},
          {"code":"JPY","quantity":100,"rate":1.7116,"validFromDate":"2026-03-14T00:00:00.000Z",
           "date":"2026-03-13T17:01:05.536Z"}
        ]}]
    """.trimIndent()
}
