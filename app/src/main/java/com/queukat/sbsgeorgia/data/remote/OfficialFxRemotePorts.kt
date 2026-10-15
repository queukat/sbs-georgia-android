package com.queukat.sbsgeorgia.data.remote

import java.math.BigDecimal
import java.time.LocalDate

data class RemoteFxRate(val currencyCode: String, val units: Int, val rateToGel: BigDecimal)

sealed interface OfficialFxRemoteResult {
    data class Success(val rates: List<RemoteFxRate>) : OfficialFxRemoteResult

    data object NotFound : OfficialFxRemoteResult

    data class Error(val message: String) : OfficialFxRemoteResult
}

interface OfficialFxRemoteDataSource {
    suspend fun fetchDailyRates(date: LocalDate): OfficialFxRemoteResult
}
