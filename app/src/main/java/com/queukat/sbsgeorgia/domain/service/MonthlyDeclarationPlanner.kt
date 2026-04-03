package com.queukat.sbsgeorgia.domain.service

import com.queukat.sbsgeorgia.domain.model.DeclarationInclusion
import com.queukat.sbsgeorgia.domain.model.DashboardSummary
import com.queukat.sbsgeorgia.domain.model.FilingWindow
import com.queukat.sbsgeorgia.domain.model.IncomeEntry
import com.queukat.sbsgeorgia.domain.model.MonthlyCurrencyTotal
import com.queukat.sbsgeorgia.domain.model.MonthlyDeclarationPeriod
import com.queukat.sbsgeorgia.domain.model.MonthlyDeclarationRecord
import com.queukat.sbsgeorgia.domain.model.MonthlyDeclarationSnapshot
import com.queukat.sbsgeorgia.domain.model.MonthlyWorkflowStatus
import com.queukat.sbsgeorgia.domain.model.ReminderConfig
import com.queukat.sbsgeorgia.domain.model.SmallBusinessStatusConfig
import com.queukat.sbsgeorgia.domain.model.TaxpayerProfile
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Clock
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MonthlyDeclarationPlanner @Inject constructor(
    private val clock: Clock,
    private val businessCalendar: GeorgiaTaxBusinessCalendar,
) {
    fun buildYearSnapshots(
        year: Int,
        profile: TaxpayerProfile?,
        config: SmallBusinessStatusConfig?,
        entries: List<IncomeEntry>,
        records: List<MonthlyDeclarationRecord>,
    ): List<MonthlyDeclarationSnapshot> {
        val now = LocalDate.now(clock)
        val currentYearMonth = YearMonth.now(clock)
        val lastMonth = when {
            year < currentYearMonth.year -> 12
            year == currentYearMonth.year -> currentYearMonth.monthValue
            else -> 0
        }
        if (lastMonth == 0) return emptyList()

        val recordMap = records.associateBy { it.yearMonth }
        val entriesByMonth = entries
            .filter { it.incomeDate.year == year }
            .groupBy { YearMonth.from(it.incomeDate) }

        var cumulative = BigDecimal.ZERO
        val snapshots = mutableListOf<MonthlyDeclarationSnapshot>()

        for (monthNumber in 1..lastMonth) {
            val yearMonth = YearMonth.of(year, monthNumber)
            val period = declarationPeriodFor(yearMonth, config)
            val rawMonthEntries = entriesByMonth[yearMonth].orEmpty()
            val includedEntries = rawMonthEntries.filter { it.declarationInclusion == DeclarationInclusion.INCLUDED }
            val reviewEntries = rawMonthEntries.filter { it.declarationInclusion == DeclarationInclusion.REVIEW_REQUIRED }
            val inScopeIncludedEntries = when {
                period.outOfScope -> emptyList()
                config == null -> includedEntries
                else -> includedEntries.filter { !it.incomeDate.isBefore(config.effectiveDate) }
            }
            val hasBeforeEffectiveDateEntries = config != null &&
                rawMonthEntries.any { it.incomeDate.isBefore(config.effectiveDate) && YearMonth.from(it.incomeDate) == yearMonth }

            val originalTotals = inScopeIncludedEntries
                .groupBy { it.originalCurrency.uppercase() }
                .map { (currencyCode, monthEntries) ->
                    MonthlyCurrencyTotal(
                        currencyCode = currencyCode,
                        amount = monthEntries.fold(BigDecimal.ZERO) { acc, entry -> acc + entry.originalAmount },
                    )
                }
                .sortedBy { it.currencyCode }

            var unresolvedFxCount = 0
            var graph20 = BigDecimal.ZERO
            inScopeIncludedEntries.forEach { entry ->
                val gelAmount = resolveGelEquivalent(entry)
                if (gelAmount == null) {
                    unresolvedFxCount += 1
                } else {
                    graph20 += gelAmount
                }
            }

            cumulative += graph20

            val record = recordMap[yearMonth]
            val estimatedTax = config?.defaultTaxRatePercent?.let { taxRate ->
                graph20.multiply(taxRate).divide(ONE_HUNDRED, 2, RoundingMode.HALF_UP)
            }

            val effectiveStatus = deriveWorkflowStatus(
                baseStatus = record?.workflowStatus ?: MonthlyWorkflowStatus.DRAFT,
                period = period,
                referenceDate = now,
            )

            snapshots += MonthlyDeclarationSnapshot(
                period = period,
                workflowStatus = effectiveStatus,
                graph20TotalGel = graph20.setScale(2, RoundingMode.HALF_UP),
                graph15CumulativeGel = cumulative.setScale(2, RoundingMode.HALF_UP),
                originalCurrencyTotals = originalTotals,
                estimatedTaxAmountGel = estimatedTax,
                unresolvedFxCount = unresolvedFxCount,
                zeroDeclarationSuggested = !period.outOfScope && inScopeIncludedEntries.isEmpty() && reviewEntries.isEmpty(),
                zeroDeclarationPrepared = record?.zeroDeclarationPrepared ?: false,
                reviewNeeded = profile == null || config == null || hasBeforeEffectiveDateEntries || reviewEntries.isNotEmpty() || period.outOfScope,
                setupRequired = profile == null || config == null,
                record = record,
            )
        }

        return snapshots
    }

    fun buildDashboardSummary(
        profile: TaxpayerProfile?,
        config: SmallBusinessStatusConfig?,
        reminders: ReminderConfig?,
        snapshots: List<MonthlyDeclarationSnapshot>,
    ): DashboardSummary {
        val now = LocalDate.now(clock)
        val dueIncomeMonth = YearMonth.from(now.minusMonths(1))
        val nextReminderDay = reminders?.declarationReminderDays?.sorted()?.firstOrNull { it >= now.dayOfMonth }
            ?: reminders?.declarationReminderDays?.sorted()?.firstOrNull()

        return DashboardSummary(
            taxpayerName = profile?.displayName,
            registrationId = profile?.registrationId,
            setupComplete = profile != null && config != null,
            ytdIncomeGel = snapshots.lastOrNull()?.graph15CumulativeGel ?: BigDecimal.ZERO,
            unresolvedFxCount = snapshots.sumOf { it.unresolvedFxCount },
            unsettledMonthsCount = snapshots.count {
                !it.period.outOfScope &&
                    it.workflowStatus !in terminalStatuses &&
                    (it.graph20TotalGel > BigDecimal.ZERO || it.zeroDeclarationSuggested || it.zeroDeclarationPrepared)
            },
            currentDuePeriod = snapshots.firstOrNull { it.period.incomeMonth == dueIncomeMonth },
            nextReminderDay = nextReminderDay,
        )
    }

    fun declarationPeriodFor(
        incomeMonth: YearMonth,
        config: SmallBusinessStatusConfig?,
    ): MonthlyDeclarationPeriod {
        val filingMonth = incomeMonth.plusMonths(1)
        val rawDueDate = filingMonth.atDay(15)
        val filingWindow = FilingWindow(
            start = filingMonth.atDay(1),
            endInclusive = filingMonth.atDay(15),
            dueDate = businessCalendar.adjustToNextBusinessDay(rawDueDate),
        )
        val effectiveMonth = config?.effectiveDate?.let { YearMonth.from(it) }
        val outOfScope = effectiveMonth?.let { incomeMonth.isBefore(it) } ?: false
        return MonthlyDeclarationPeriod(
            incomeMonth = incomeMonth,
            filingWindow = filingWindow,
            inScope = !outOfScope,
            outOfScope = outOfScope,
        )
    }

    fun deriveWorkflowStatus(
        baseStatus: MonthlyWorkflowStatus,
        period: MonthlyDeclarationPeriod,
        referenceDate: LocalDate = LocalDate.now(clock),
    ): MonthlyWorkflowStatus {
        if (baseStatus == MonthlyWorkflowStatus.SETTLED || baseStatus == MonthlyWorkflowStatus.PAYMENT_CREDITED) {
            return baseStatus
        }
        if (referenceDate.isAfter(period.filingWindow.dueDate) && baseStatus in overdueBaseStatuses) {
            return MonthlyWorkflowStatus.OVERDUE
        }
        return baseStatus
    }

    fun isFilingWindowOpen(
        period: MonthlyDeclarationPeriod,
        referenceDate: LocalDate = LocalDate.now(clock),
    ): Boolean = !referenceDate.isBefore(period.filingWindow.start)

    fun allowedTransitions(status: MonthlyWorkflowStatus): Set<MonthlyWorkflowStatus> = when (status) {
        MonthlyWorkflowStatus.DRAFT -> setOf(MonthlyWorkflowStatus.READY_TO_FILE)
        MonthlyWorkflowStatus.READY_TO_FILE -> setOf(MonthlyWorkflowStatus.FILED, MonthlyWorkflowStatus.DRAFT)
        MonthlyWorkflowStatus.FILED -> setOf(MonthlyWorkflowStatus.TAX_PAYMENT_PENDING, MonthlyWorkflowStatus.READY_TO_FILE)
        MonthlyWorkflowStatus.TAX_PAYMENT_PENDING -> setOf(MonthlyWorkflowStatus.PAYMENT_SENT, MonthlyWorkflowStatus.FILED)
        MonthlyWorkflowStatus.PAYMENT_SENT -> setOf(MonthlyWorkflowStatus.PAYMENT_CREDITED, MonthlyWorkflowStatus.TAX_PAYMENT_PENDING)
        MonthlyWorkflowStatus.PAYMENT_CREDITED -> setOf(MonthlyWorkflowStatus.SETTLED)
        MonthlyWorkflowStatus.SETTLED -> emptySet()
        MonthlyWorkflowStatus.OVERDUE -> setOf(
            MonthlyWorkflowStatus.READY_TO_FILE,
            MonthlyWorkflowStatus.FILED,
            MonthlyWorkflowStatus.TAX_PAYMENT_PENDING,
            MonthlyWorkflowStatus.PAYMENT_SENT,
            MonthlyWorkflowStatus.PAYMENT_CREDITED,
            MonthlyWorkflowStatus.SETTLED,
        )
    }

    private fun resolveGelEquivalent(entry: IncomeEntry): BigDecimal? = when {
        entry.gelEquivalent != null -> entry.gelEquivalent
        entry.originalCurrency.equals("GEL", ignoreCase = true) -> entry.originalAmount
        else -> null
    }

    private companion object {
        val ONE_HUNDRED: BigDecimal = BigDecimal("100")
        val overdueBaseStatuses = setOf(
            MonthlyWorkflowStatus.DRAFT,
            MonthlyWorkflowStatus.READY_TO_FILE,
            MonthlyWorkflowStatus.FILED,
            MonthlyWorkflowStatus.TAX_PAYMENT_PENDING,
        )
        val terminalStatuses = setOf(
            MonthlyWorkflowStatus.PAYMENT_SENT,
            MonthlyWorkflowStatus.PAYMENT_CREDITED,
            MonthlyWorkflowStatus.SETTLED,
        )
    }
}
