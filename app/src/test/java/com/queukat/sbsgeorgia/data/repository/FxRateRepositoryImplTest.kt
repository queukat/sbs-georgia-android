package com.queukat.sbsgeorgia.data.repository

import com.queukat.sbsgeorgia.data.local.FxRateDao
import com.queukat.sbsgeorgia.data.local.FxRateEntity
import com.queukat.sbsgeorgia.data.remote.OfficialFxRemoteDataSource
import com.queukat.sbsgeorgia.data.remote.OfficialFxRemoteResult
import com.queukat.sbsgeorgia.data.remote.RemoteFxRate
import com.queukat.sbsgeorgia.domain.model.FxRateSource
import com.queukat.sbsgeorgia.domain.repository.FxRateFetchResult
import java.math.BigDecimal
import java.time.LocalDate
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FxRateRepositoryImplTest {
    @Test
    fun oneDailyRequestCachesAllCurrenciesTogetherAndPreservesManualOverride() = runTest {
        val date = LocalDate.of(2026, 3, 15)
        val dao = MemoryRates()
        var requests = 0
        val remote = object : OfficialFxRemoteDataSource {
            override suspend fun fetchDailyRates(date: LocalDate): OfficialFxRemoteResult {
                requests += 1
                return OfficialFxRemoteResult.Success(
                    listOf(RemoteFxRate("USD", 1, BigDecimal("2.7")), RemoteFxRate("JPY", 100, BigDecimal("1.7")))
                )
            }
        }
        val repository = FxRateRepositoryImpl(dao, remote)
        repository.upsertManualOverride(date, "USD", 1, BigDecimal("2.8"))
        val usd = repository.fetchOfficialRate(date, "usd") as FxRateFetchResult.Success
        val jpy = repository.fetchOfficialRate(date, "JPY") as FxRateFetchResult.Success
        assertEquals(1, requests)
        assertEquals(1, dao.batches)
        assertEquals(BigDecimal("2.7"), usd.rate.rateToGel)
        assertEquals(100, jpy.rate.units)
        assertEquals(date, jpy.rate.rateDate)
        assertEquals(FxRateSource.MANUAL_OVERRIDE, repository.getBestRate(date, "USD")?.source)
        assertNull(repository.getBestRate(date.plusDays(1), "USD"))
    }

    @Test
    fun invalidRemoteResponseDoesNotWriteCache() = runTest {
        val dao = MemoryRates()
        val remote = object : OfficialFxRemoteDataSource {
            override suspend fun fetchDailyRates(date: LocalDate) = OfficialFxRemoteResult.Error("Invalid response")
        }
        val result = FxRateRepositoryImpl(dao, remote).fetchOfficialRate(LocalDate.of(2026, 3, 15), "USD")
        assertTrue(result is FxRateFetchResult.Error)
        assertEquals(0, dao.batches)
        assertTrue(dao.getAll().isEmpty())
    }

    private class MemoryRates : FxRateDao {
        private val rows = mutableListOf<FxRateEntity>()
        var batches = 0
        override suspend fun getAll() = rows.toList()
        override suspend fun getBestRate(rateDate: LocalDate, currencyCode: String) =
            getRate(rateDate, currencyCode, true) ?: getRate(rateDate, currencyCode, false)
        override suspend fun getRate(rateDate: LocalDate, currencyCode: String, manualOverride: Boolean) = rows.find {
            it.rateDate == rateDate &&
                it.currencyCode == currencyCode &&
                it.manualOverride == manualOverride
        }
        override suspend fun upsert(entity: FxRateEntity): Long {
            rows.removeAll {
                it.rateDate == entity.rateDate &&
                    it.currencyCode == entity.currencyCode &&
                    it.manualOverride == entity.manualOverride
            }
            rows.add(entity)
            return rows.size.toLong()
        }
        override suspend fun insertAll(entities: List<FxRateEntity>) {
            batches += 1
            entities.forEach { upsert(it) }
        }
        override suspend fun clear() = rows.clear()
    }
}
