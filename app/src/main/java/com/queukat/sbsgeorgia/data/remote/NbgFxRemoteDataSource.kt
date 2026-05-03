package com.queukat.sbsgeorgia.data.remote

import com.queukat.sbsgeorgia.di.IoDispatcher
import java.io.IOException
import java.math.BigDecimal
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

data class RemoteFxRate(val currencyCode: String, val units: Int, val rateToGel: BigDecimal)

sealed interface OfficialFxRemoteResult {
    data class Success(val rates: List<RemoteFxRate>) : OfficialFxRemoteResult

    data object NotFound : OfficialFxRemoteResult

    data class Error(val message: String) : OfficialFxRemoteResult
}

interface OfficialFxRemoteDataSource {
    suspend fun fetchDailyRates(date: LocalDate): OfficialFxRemoteResult
}

@Singleton
class NbgFxRemoteDataSource
@Inject
constructor(
    private val json: Json,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : OfficialFxRemoteDataSource {
    override suspend fun fetchDailyRates(date: LocalDate): OfficialFxRemoteResult = withContext(ioDispatcher) {
        val url = URL("$BASE_URL?date=$date")
        val connection =
            (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 15_000
                readTimeout = 15_000
                setRequestProperty("Accept", "application/json")
            }

        try {
            when (val statusCode = connection.responseCode) {
                HttpURLConnection.HTTP_OK -> {
                    val body = connection.inputStream.bufferedReader().use { it.readText() }
                    val rates = parseRates(body)
                    if (rates.isEmpty()) {
                        OfficialFxRemoteResult.NotFound
                    } else {
                        OfficialFxRemoteResult.Success(rates)
                    }
                }
                HttpURLConnection.HTTP_NOT_FOUND -> OfficialFxRemoteResult.NotFound
                else -> OfficialFxRemoteResult.Error("NBG returned HTTP $statusCode.")
            }
        } catch (exception: IOException) {
            endpointError(exception)
        } catch (exception: SerializationException) {
            endpointError(exception)
        } finally {
            connection.disconnect()
        }
    }

    private fun parseRates(body: String): List<RemoteFxRate> {
        val root = json.parseToJsonElement(body)
        val dailyGroups =
            when (root) {
                is JsonArray -> root
                is JsonObject -> listOfNotNull(root["items"]).flatMap { it.jsonArray }
                else -> emptyList()
            }

        return dailyGroups
            .flatMap { dailyGroup ->
                dailyGroup.jsonObject["currencies"]?.jsonArray.orEmpty()
            }.mapNotNull(::parseRate)
    }

    private fun parseRate(element: JsonElement): RemoteFxRate? {
        val currency = element.jsonObject
        val currencyCode =
            currency["code"]?.jsonPrimitive?.contentOrNull?.uppercase() ?: return null
        val units =
            currency["quantity"]?.jsonPrimitive?.intOrNull
                ?: currency["units"]?.jsonPrimitive?.intOrNull
                ?: 1
        val rateToGel =
            currency["rate"]?.jsonPrimitive?.contentOrNull?.toBigDecimalOrNull()
                ?: currency["rateFormated"]?.jsonPrimitive?.contentOrNull?.toBigDecimalOrNull()
                ?: return null

        return RemoteFxRate(
            currencyCode = currencyCode,
            units = units,
            rateToGel = rateToGel
        )
    }

    private fun String.toBigDecimalOrNull(): BigDecimal? = runCatching {
        BigDecimal(replace(",", ""))
    }.getOrNull()

    private fun endpointError(exception: Exception): OfficialFxRemoteResult.Error = OfficialFxRemoteResult.Error(
        exception.message ?: "Failed to reach NBG FX endpoint."
    )

    private companion object {
        const val BASE_URL = "https://nbg.gov.ge/gw/api/ct/monetarypolicy/currencies/en/json/"
    }
}
