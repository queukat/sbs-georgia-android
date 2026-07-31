package com.queukat.sbsgeorgia.domain.usecase

import com.queukat.sbsgeorgia.domain.model.DeclarationFormConfig
import com.queukat.sbsgeorgia.domain.model.DeclarationFormField
import com.queukat.sbsgeorgia.domain.model.DeclarationInclusion
import com.queukat.sbsgeorgia.domain.model.FxRateSource
import com.queukat.sbsgeorgia.domain.model.IncomeEntry
import com.queukat.sbsgeorgia.domain.model.IncomeSourceType
import com.queukat.sbsgeorgia.domain.model.MonthlyDeclarationRecord
import com.queukat.sbsgeorgia.domain.model.ReminderConfig
import com.queukat.sbsgeorgia.domain.model.SmallBusinessStatusConfig
import com.queukat.sbsgeorgia.domain.model.TaxpayerProfile
import com.queukat.sbsgeorgia.domain.repository.DeclarationFormConfigRepository
import com.queukat.sbsgeorgia.domain.repository.IncomeRepository
import com.queukat.sbsgeorgia.domain.repository.MonthlyDeclarationRepository
import com.queukat.sbsgeorgia.domain.repository.SettingsRepository
import com.queukat.sbsgeorgia.domain.service.GeorgiaTaxBusinessCalendar
import com.queukat.sbsgeorgia.domain.service.MonthlyDeclarationPlanner
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class PaymentHelperUseCaseTest {
    @Test
    fun `payment helper uses saved declaration destination field`() = runTest {
        val month = YearMonth.of(2026, 3)
        val settingsRepository = PaymentTestSettingsRepository()
        val incomeRepository = PaymentTestIncomeRepository(month)
        val monthlyRepository = PaymentTestMonthlyRepository()
        val planner =
            MonthlyDeclarationPlanner(
                clock = fixedClock,
                businessCalendar = GeorgiaTaxBusinessCalendar()
            )
        val observeMonthDetail =
            ObserveMonthDetailUseCase(
                settingsRepository = settingsRepository,
                incomeRepository = incomeRepository,
                monthlyDeclarationRepository = monthlyRepository,
                planner = planner
            )
        val useCase =
            ObservePaymentHelperUseCase(
                settingsRepository = settingsRepository,
                declarationFormConfigRepository =
                PaymentTestDeclarationFormConfigRepository(
                    DeclarationFormConfig(
                        monthlyIncomeField = DeclarationFormField.MONTHLY_POS_INCOME
                    )
                ),
                observeMonthDetailUseCase = observeMonthDetail
            )

        val data = useCase(month).first()

        assertEquals(
            listOf(
                DeclarationFormField.CUMULATIVE_INCOME,
                DeclarationFormField.MONTHLY_POS_INCOME
            ),
            data.copyBundle?.declarationValues?.map { it.field }
        )
    }

    private companion object {
        val fixedClock: Clock =
            Clock.fixed(Instant.parse("2026-04-02T10:00:00Z"), ZoneOffset.UTC)
    }
}

private class PaymentTestSettingsRepository : SettingsRepository {
    override fun observeTaxpayerProfile(): Flow<TaxpayerProfile?> =
        flowOf(TaxpayerProfile(registrationId = "123456789", displayName = "Test taxpayer"))

    override fun observeStatusConfig(): Flow<SmallBusinessStatusConfig?> = flowOf(
        SmallBusinessStatusConfig(
            effectiveDate = LocalDate.of(2026, 1, 1),
            defaultTaxRatePercent = BigDecimal("1.0")
        )
    )

    override fun observeReminderConfig(): Flow<ReminderConfig?> = flowOf(null)

    override suspend fun upsertTaxpayerProfile(profile: TaxpayerProfile) = Unit

    override suspend fun upsertStatusConfig(config: SmallBusinessStatusConfig) = Unit

    override suspend fun upsertReminderConfig(config: ReminderConfig) = Unit
}

private class PaymentTestDeclarationFormConfigRepository(private val config: DeclarationFormConfig) :
    DeclarationFormConfigRepository {
    override fun observeConfig(): Flow<DeclarationFormConfig?> = flowOf(config)

    override suspend fun upsertConfig(config: DeclarationFormConfig) = Unit
}

private class PaymentTestIncomeRepository(month: YearMonth) : IncomeRepository {
    private val entry =
        IncomeEntry(
            id = 1L,
            sourceType = IncomeSourceType.MANUAL,
            incomeDate = month.atDay(10),
            originalAmount = BigDecimal("100.00"),
            originalCurrency = "GEL",
            sourceCategory = "Services",
            note = "",
            declarationInclusion = DeclarationInclusion.INCLUDED,
            gelEquivalent = BigDecimal("100.00"),
            rateSource = FxRateSource.NONE,
            manualFxOverride = false,
            createdAtEpochMillis = 1L,
            updatedAtEpochMillis = 1L
        )

    override fun observeAll(): Flow<List<IncomeEntry>> = flowOf(listOf(entry))

    override fun observeByMonth(yearMonth: YearMonth): Flow<List<IncomeEntry>> =
        flowOf(listOf(entry).filter { YearMonth.from(it.incomeDate) == yearMonth })

    override suspend fun getById(id: Long): IncomeEntry? = entry.takeIf { it.id == id }

    override suspend fun upsert(entry: IncomeEntry): Long = entry.id

    override suspend fun deleteById(id: Long) = Unit
}

private class PaymentTestMonthlyRepository : MonthlyDeclarationRepository {
    override fun observeAll(): Flow<List<MonthlyDeclarationRecord>> = flowOf(emptyList())

    override fun observeByMonth(yearMonth: YearMonth): Flow<MonthlyDeclarationRecord?> = flowOf(null)

    override suspend fun upsert(record: MonthlyDeclarationRecord) = Unit
}
