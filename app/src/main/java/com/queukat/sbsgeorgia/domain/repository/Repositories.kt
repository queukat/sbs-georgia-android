package com.queukat.sbsgeorgia.domain.repository

import com.queukat.sbsgeorgia.domain.model.ApprovedImportedStatementRow
import com.queukat.sbsgeorgia.domain.model.ConfirmImportedStatementResult
import com.queukat.sbsgeorgia.domain.model.FxRate
import com.queukat.sbsgeorgia.domain.model.ImportedStatementImportInfo
import com.queukat.sbsgeorgia.domain.model.IncomeEntry
import com.queukat.sbsgeorgia.domain.model.MonthlyDeclarationRecord
import com.queukat.sbsgeorgia.domain.model.ReminderConfig
import com.queukat.sbsgeorgia.domain.model.SmallBusinessStatusConfig
import com.queukat.sbsgeorgia.domain.model.TaxpayerProfile
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.flow.Flow

data class QuickStartGuideState(val initialized: Boolean = false, val dismissed: Boolean = false)

interface AppPreferencesRepository {
    fun observeQuickStartGuideState(): Flow<QuickStartGuideState>

    suspend fun initializeQuickStartGuide(hasCompletedSetup: Boolean)

    suspend fun markQuickStartGuideDismissed()
}

interface SettingsRepository {
    fun observeTaxpayerProfile(): Flow<TaxpayerProfile?>

    fun observeStatusConfig(): Flow<SmallBusinessStatusConfig?>

    fun observeReminderConfig(): Flow<ReminderConfig?>

    suspend fun upsertTaxpayerProfile(profile: TaxpayerProfile)

    suspend fun upsertStatusConfig(config: SmallBusinessStatusConfig)

    suspend fun upsertReminderConfig(config: ReminderConfig)
}

interface IncomeRepository {
    fun observeAll(): Flow<List<IncomeEntry>>

    fun observeByMonth(yearMonth: YearMonth): Flow<List<IncomeEntry>>

    suspend fun getById(id: Long): IncomeEntry?

    suspend fun upsert(entry: IncomeEntry): Long

    suspend fun deleteById(id: Long)
}

interface MonthlyDeclarationRepository {
    fun observeAll(): Flow<List<MonthlyDeclarationRecord>>

    fun observeByMonth(yearMonth: YearMonth): Flow<MonthlyDeclarationRecord?>

    suspend fun upsert(record: MonthlyDeclarationRecord)
}

interface FxRateRepository {
    suspend fun getBestRate(rateDate: LocalDate, currencyCode: String): FxRate?

    suspend fun fetchOfficialRate(rateDate: LocalDate, currencyCode: String): FxRateFetchResult

    suspend fun upsertManualOverride(
        rateDate: LocalDate,
        currencyCode: String,
        units: Int,
        rateToGel: java.math.BigDecimal
    ): FxRate
}

sealed interface FxRateFetchResult {
    data class Success(val rate: FxRate) : FxRateFetchResult

    data object NotFound : FxRateFetchResult

    data class Error(val message: String) : FxRateFetchResult
}

interface StatementImportRepository {
    suspend fun hasStatementFingerprint(sourceFingerprint: String): Boolean

    suspend fun getStatementImportInfo(sourceFingerprint: String): ImportedStatementImportInfo?

    suspend fun hasTransactionFingerprint(transactionFingerprint: String): Boolean

    suspend fun confirmImport(
        sourceFileName: String,
        sourceFingerprint: String,
        rows: List<ApprovedImportedStatementRow>,
        importedAtEpochMillis: Long
    ): ConfirmImportedStatementResult
}
