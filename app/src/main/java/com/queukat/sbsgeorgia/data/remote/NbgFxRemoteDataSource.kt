package com.queukat.sbsgeorgia.data.remote

import com.queukat.sbsgeorgia.data.io.InputLimits
import com.queukat.sbsgeorgia.data.io.readBytesLimited
import com.queukat.sbsgeorgia.di.IoDispatcher
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

@Singleton
class NbgFxRemoteDataSource internal constructor(
    private val json: Json,
    private val ioDispatcher: CoroutineDispatcher,
    private val openConnection: (URL) -> HttpURLConnection
) : OfficialFxRemoteDataSource {
    @Inject
    constructor(json: Json, @IoDispatcher ioDispatcher: CoroutineDispatcher) :
        this(json, ioDispatcher, { it.openConnection() as HttpURLConnection })

    override suspend fun fetchDailyRates(date: LocalDate): OfficialFxRemoteResult = withContext(ioDispatcher) {
        suspendCancellableCoroutine { continuation ->
            val connection = openConnection(URL("$BASE_URL?date=$date")).apply {
                requestMethod = "GET"
                connectTimeout = 15_000
                readTimeout = 15_000
                setRequestProperty("Accept", "application/json")
            }
            // The import timeout must also stop blocking socket I/O, not just the coroutine.
            continuation.invokeOnCancellation { connection.disconnect() }
            try {
                val result = when (val statusCode = connection.responseCode) {
                    HttpURLConnection.HTTP_OK -> {
                        val body = connection.inputStream.use {
                            it.readBytesLimited(InputLimits.FX_RESPONSE_BYTES).toString(Charsets.UTF_8)
                        }
                        parseNbgDailyRates(json, body, date)
                    }
                    HttpURLConnection.HTTP_NOT_FOUND -> OfficialFxRemoteResult.NotFound
                    else -> OfficialFxRemoteResult.Error("NBG returned HTTP $statusCode.")
                }
                continuation.resume(result)
            } catch (exception: IOException) {
                continuation.resume(endpointError(exception))
            } catch (exception: IllegalArgumentException) {
                continuation.resume(endpointError(exception))
            } finally {
                connection.disconnect()
            }
        }
    }

    private fun endpointError(exception: Exception): OfficialFxRemoteResult.Error = OfficialFxRemoteResult.Error(
        exception.message ?: "Failed to reach NBG FX endpoint."
    )

    private companion object {
        const val BASE_URL = "https://nbg.gov.ge/gw/api/ct/monetarypolicy/currencies/en/json/"
    }
}
