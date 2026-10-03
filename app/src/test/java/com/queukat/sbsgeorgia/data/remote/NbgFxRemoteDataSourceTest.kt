package com.queukat.sbsgeorgia.data.remote

import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NbgFxRemoteDataSourceTest {
    private val date = LocalDate.of(2026, 3, 15)

    @Test
    fun sendsOneGetForExactDateAndDisconnects() = runBlocking {
        val connection = TestConnection()
        var calls = 0
        val source = NbgFxRemoteDataSource(Json, Dispatchers.IO) { url ->
            assertEquals("date=2026-03-15", url.query)
            calls += 1
            connection
        }
        assertEquals(OfficialFxRemoteResult.NotFound, source.fetchDailyRates(date))
        assertEquals(1, calls)
        assertEquals("GET", connection.requestMethod)
        assertTrue(connection.disconnected)
    }

    @Test
    fun httpFailureAndBadJsonRemainVisibleErrors() = runBlocking {
        val httpFailure = TestConnection(status = 503)
        assertTrue(source(httpFailure).fetchDailyRates(date) is OfficialFxRemoteResult.Error)
        val badJson = TestConnection(body = "{broken")
        assertTrue(source(badJson).fetchDailyRates(date) is OfficialFxRemoteResult.Error)
        assertTrue(httpFailure.disconnected && badJson.disconnected)
    }

    @Test
    fun cancellationDisconnectsWhileBlockingReadIsPending() = runBlocking {
        val started = CountDownLatch(1)
        val released = CountDownLatch(1)
        val connection = object : TestConnection() {
            override fun getInputStream(): InputStream {
                started.countDown()
                check(released.await(5, TimeUnit.SECONDS)) { "Cancellation did not disconnect the connection." }
                throw IOException("Disconnected")
            }

            override fun disconnect() {
                super.disconnect()
                released.countDown()
            }
        }
        val request = async(Dispatchers.Default) { source(connection).fetchDailyRates(date) }
        assertTrue(started.await(5, TimeUnit.SECONDS))
        request.cancelAndJoin()
        assertTrue(connection.disconnected)
        assertTrue(request.isCancelled)
    }

    private fun source(connection: HttpURLConnection) = NbgFxRemoteDataSource(Json, Dispatchers.IO) { connection }

    private open class TestConnection(private val status: Int = HTTP_OK, private val body: String = "[]") :
        HttpURLConnection(URL("https://nbg.gov.ge/")) {
        @Volatile var disconnected = false

        override fun connect() = Unit
        override fun usingProxy() = false
        override fun disconnect() {
            disconnected = true
        }
        override fun getResponseCode() = status
        override fun getInputStream(): InputStream = ByteArrayInputStream(body.toByteArray())
    }
}
