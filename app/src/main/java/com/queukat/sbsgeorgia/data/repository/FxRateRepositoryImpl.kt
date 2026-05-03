package com.queukat.sbsgeorgia.data.repository

import com.queukat.sbsgeorgia.data.local.FxRateDao
import com.queukat.sbsgeorgia.data.local.FxRateEntity
import com.queukat.sbsgeorgia.data.remote.OfficialFxRemoteDataSource
import com.queukat.sbsgeorgia.data.remote.OfficialFxRemoteResult
import com.queukat.sbsgeorgia.domain.model.FxRate
import com.queukat.sbsgeorgia.domain.model.FxRateSource
import com.queukat.sbsgeorgia.domain.repository.FxRateFetchResult
import com.queukat.sbsgeorgia.domain.repository.FxRateRepository
import java.math.BigDecimal
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FxRateRepositoryImpl
@Inject
constructor(
    private val fxRateDao: FxRateDao,
    private val remoteDataSource: OfficialFxRemoteDataSource
) : FxRateRepository {
    override suspend fun getBestRate(rateDate: LocalDate, currencyCode: String): FxRate? =
        fxRateDao.getBestRate(rateDate, currencyCode.uppercase())?.toDomain()

    override suspend fun fetchOfficialRate(rateDate: LocalDate, currencyCode: String): FxRateFetchResult {
        val normalizedCode = currencyCode.uppercase()
        fxRateDao.getRate(rateDate, normalizedCode, manualOverride = false)?.let { cached ->
            return FxRateFetchResult.Success(cached.toDomain())
        }

        return when (val result = remoteDataSource.fetchDailyRates(rateDate)) {
            is OfficialFxRemoteResult.Success -> {
                result.rates.forEach { remoteRate ->
                    fxRateDao.upsert(
                        FxRateEntity(
                            rateDate = rateDate,
                            currencyCode = remoteRate.currencyCode,
                            units = remoteRate.units,
                            rateToGel = remoteRate.rateToGel,
                            source = FxRateSource.OFFICIAL_NBG_JSON,
                            manualOverride = false
                        )
                    )
                }
                val matchedRate = fxRateDao.getRate(
                    rateDate,
                    normalizedCode,
                    manualOverride = false
                )
                if (matchedRate == null) {
                    FxRateFetchResult.NotFound
                } else {
                    FxRateFetchResult.Success(matchedRate.toDomain())
                }
            }
            OfficialFxRemoteResult.NotFound -> FxRateFetchResult.NotFound
            is OfficialFxRemoteResult.Error -> FxRateFetchResult.Error(result.message)
        }
    }

    override suspend fun upsertManualOverride(
        rateDate: LocalDate,
        currencyCode: String,
        units: Int,
        rateToGel: BigDecimal
    ): FxRate {
        val entity =
            FxRateEntity(
                rateDate = rateDate,
                currencyCode = currencyCode.uppercase(),
                units = units.coerceAtLeast(1),
                rateToGel = rateToGel,
                source = FxRateSource.MANUAL_OVERRIDE,
                manualOverride = true
            )
        fxRateDao.upsert(entity)
        return checkNotNull(
            fxRateDao.getRate(rateDate, entity.currencyCode, manualOverride = true)
        ).toDomain()
    }
}

private fun FxRateEntity.toDomain(): FxRate = FxRate(
    rateDate = rateDate,
    currencyCode = currencyCode,
    units = units,
    rateToGel = rateToGel,
    source = source,
    manualOverride = manualOverride
)
