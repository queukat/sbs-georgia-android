package com.queukat.sbsgeorgia.ui

import android.content.Context
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.queukat.sbsgeorgia.R
import com.queukat.sbsgeorgia.domain.model.DeclarationInclusion
import com.queukat.sbsgeorgia.domain.model.FilingWindow
import com.queukat.sbsgeorgia.domain.model.FxRate
import com.queukat.sbsgeorgia.domain.model.FxRateSource
import com.queukat.sbsgeorgia.domain.model.IncomeEntry
import com.queukat.sbsgeorgia.domain.model.IncomeSourceType
import com.queukat.sbsgeorgia.domain.model.MonthlyCurrencyTotal
import com.queukat.sbsgeorgia.domain.model.MonthlyDeclarationPeriod
import com.queukat.sbsgeorgia.domain.model.MonthlyDeclarationSnapshot
import com.queukat.sbsgeorgia.domain.model.MonthlyWorkflowStatus
import com.queukat.sbsgeorgia.domain.model.ThemeMode
import com.queukat.sbsgeorgia.ui.monthdetails.MonthDetailScreen
import com.queukat.sbsgeorgia.ui.monthdetails.MonthDetailUiState
import com.queukat.sbsgeorgia.ui.theme.SbsGeorgiaTheme
import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MonthDetailFxUiTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val appContext: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun skipOnTvDevices() {
        assumePhoneLikeComposeTestDevice()
    }

    @Test
    fun appliedFxSectionShowsOfficialAndManualRatesSeparately() {
        val month = YearMonth.of(2026, 6)
        val officialEntry =
            incomeEntry(
                id = 1L,
                date = LocalDate.of(2026, 6, 5),
                currency = "USD",
                rateSource = FxRateSource.OFFICIAL_NBG_JSON
            )
        val manualEntry =
            incomeEntry(
                id = 2L,
                date = LocalDate.of(2026, 6, 8),
                currency = "EUR",
                rateSource = FxRateSource.MANUAL_OVERRIDE
            )

        composeRule.setContent {
            SbsGeorgiaTheme(themeMode = ThemeMode.SYSTEM) {
                MonthDetailScreen(
                    innerPadding = PaddingValues(),
                    uiState =
                    MonthDetailUiState(
                        yearMonth = month,
                        snapshot = snapshot(month),
                        entries = listOf(officialEntry, manualEntry),
                        fxRateDetails =
                        mapOf(
                            1L to
                                FxRate(
                                    rateDate = officialEntry.incomeDate,
                                    currencyCode = "USD",
                                    units = 1,
                                    rateToGel = BigDecimal("2.6647"),
                                    source = FxRateSource.OFFICIAL_NBG_JSON,
                                    manualOverride = false
                                ),
                            2L to
                                FxRate(
                                    rateDate = manualEntry.incomeDate,
                                    currencyCode = "EUR",
                                    units = 1,
                                    rateToGel = BigDecimal("3.10"),
                                    source = FxRateSource.MANUAL_OVERRIDE,
                                    manualOverride = true
                                )
                        )
                    ),
                    onBack = {},
                    onAddIncome = {},
                    onEditEntry = {},
                    onOpenFxOverride = {},
                    onOpenWorkflowStatus = {},
                    onOpenPaymentHelper = {},
                    onDeleteEntry = {},
                    onResolveOfficialRates = {},
                    onToggleZeroPrepared = {}
                )
            }
        }

        composeRule
            .onNodeWithTag("month-detail-applied-fx")
            .performScrollTo()
            .assertIsDisplayed()
        composeRule
            .onNodeWithText(fxRateValue(units = 1, currency = "USD", rate = "2.6647"))
            .assertIsDisplayed()
        composeRule
            .onNodeWithText(fxRateValue(units = 1, currency = "EUR", rate = "3.1"))
            .assertIsDisplayed()
        composeRule
            .onNodeWithText(
                appliedFxLabel(
                    date = "2026-06-05",
                    sourceRes = R.string.fx_rate_source_official_nbg_json
                )
            ).assertIsDisplayed()
        composeRule
            .onNodeWithText(
                appliedFxLabel(
                    date = "2026-06-08",
                    sourceRes = R.string.fx_rate_source_manual_override
                )
            ).assertIsDisplayed()
    }

    @Test
    fun appliedFxSectionExcludesEntriesOutsideTheDeclaration() {
        val month = YearMonth.of(2026, 6)
        val excludedEntry =
            incomeEntry(
                id = 1L,
                date = LocalDate.of(2026, 6, 5),
                currency = "EUR",
                rateSource = FxRateSource.MANUAL_OVERRIDE,
                inclusion = DeclarationInclusion.EXCLUDED
            )

        composeRule.setContent {
            SbsGeorgiaTheme(themeMode = ThemeMode.SYSTEM) {
                MonthDetailScreen(
                    innerPadding = PaddingValues(),
                    uiState =
                    MonthDetailUiState(
                        yearMonth = month,
                        snapshot = snapshot(month),
                        entries = listOf(excludedEntry),
                        fxRateDetails =
                        mapOf(
                            excludedEntry.id to
                                FxRate(
                                    rateDate = excludedEntry.incomeDate,
                                    currencyCode = "EUR",
                                    units = 1,
                                    rateToGel = BigDecimal("3.10"),
                                    source = FxRateSource.MANUAL_OVERRIDE,
                                    manualOverride = true
                                )
                        )
                    ),
                    onBack = {},
                    onAddIncome = {},
                    onEditEntry = {},
                    onOpenFxOverride = {},
                    onOpenWorkflowStatus = {},
                    onOpenPaymentHelper = {},
                    onDeleteEntry = {},
                    onResolveOfficialRates = {},
                    onToggleZeroPrepared = {}
                )
            }
        }

        composeRule.onAllNodesWithTag("month-detail-applied-fx").assertCountEquals(0)
    }

    @Test
    fun appliedFxSectionOrdersOfficialBeforeManualForTheSameCurrencyAndDate() {
        val month = YearMonth.of(2026, 6)
        val date = LocalDate.of(2026, 6, 5)
        val manualEntry =
            incomeEntry(
                id = 1L,
                date = date,
                currency = "USD",
                rateSource = FxRateSource.MANUAL_OVERRIDE
            )
        val officialEntry =
            incomeEntry(
                id = 2L,
                date = date,
                currency = "USD",
                rateSource = FxRateSource.OFFICIAL_NBG_JSON
            )

        composeRule.setContent {
            SbsGeorgiaTheme(themeMode = ThemeMode.SYSTEM) {
                MonthDetailScreen(
                    innerPadding = PaddingValues(),
                    uiState =
                    MonthDetailUiState(
                        yearMonth = month,
                        snapshot = snapshot(month),
                        entries = listOf(manualEntry, officialEntry),
                        fxRateDetails =
                        mapOf(
                            manualEntry.id to
                                FxRate(
                                    rateDate = date,
                                    currencyCode = "USD",
                                    units = 1,
                                    rateToGel = BigDecimal("2.70"),
                                    source = FxRateSource.MANUAL_OVERRIDE,
                                    manualOverride = true
                                ),
                            officialEntry.id to
                                FxRate(
                                    rateDate = date,
                                    currencyCode = "USD",
                                    units = 1,
                                    rateToGel = BigDecimal("2.6647"),
                                    source = FxRateSource.OFFICIAL_NBG_JSON,
                                    manualOverride = false
                                )
                        )
                    ),
                    onBack = {},
                    onAddIncome = {},
                    onEditEntry = {},
                    onOpenFxOverride = {},
                    onOpenWorkflowStatus = {},
                    onOpenPaymentHelper = {},
                    onDeleteEntry = {},
                    onResolveOfficialRates = {},
                    onToggleZeroPrepared = {}
                )
            }
        }

        val officialTop =
            composeRule
                .onNodeWithText(
                    appliedFxLabel(
                        date = "2026-06-05",
                        sourceRes = R.string.fx_rate_source_official_nbg_json
                    )
                ).fetchSemanticsNode()
                .boundsInRoot
                .top
        val manualTop =
            composeRule
                .onNodeWithText(
                    appliedFxLabel(
                        date = "2026-06-05",
                        sourceRes = R.string.fx_rate_source_manual_override
                    )
                ).fetchSemanticsNode()
                .boundsInRoot
                .top

        assertTrue(officialTop < manualTop)
    }

    private fun fxRateValue(units: Int, currency: String, rate: String): String =
        appContext.getString(R.string.month_detail_fx_rate_value, units, currency, rate)

    private fun appliedFxLabel(date: String, sourceRes: Int): String = appContext.getString(
        R.string.month_detail_applied_fx_label,
        date,
        appContext.getString(sourceRes)
    )

    private fun incomeEntry(
        id: Long,
        date: LocalDate,
        currency: String,
        rateSource: FxRateSource,
        inclusion: DeclarationInclusion = DeclarationInclusion.INCLUDED
    ): IncomeEntry = IncomeEntry(
        id = id,
        sourceType = IncomeSourceType.MANUAL,
        incomeDate = date,
        originalAmount = BigDecimal("10.00"),
        originalCurrency = currency,
        sourceCategory = "Services",
        note = "",
        declarationInclusion = inclusion,
        gelEquivalent = BigDecimal("26.65"),
        rateSource = rateSource,
        manualFxOverride = rateSource == FxRateSource.MANUAL_OVERRIDE,
        createdAtEpochMillis = 1L,
        updatedAtEpochMillis = 1L
    )

    private fun snapshot(month: YearMonth): MonthlyDeclarationSnapshot = MonthlyDeclarationSnapshot(
        period =
        MonthlyDeclarationPeriod(
            incomeMonth = month,
            filingWindow =
            FilingWindow(
                start = month.plusMonths(1).atDay(1),
                endInclusive = month.plusMonths(1).atDay(15),
                dueDate = month.plusMonths(1).atDay(15)
            ),
            inScope = true,
            outOfScope = false
        ),
        workflowStatus = MonthlyWorkflowStatus.DRAFT,
        graph20TotalGel = BigDecimal("57.65"),
        graph15CumulativeGel = BigDecimal("57.65"),
        originalCurrencyTotals =
        listOf(
            MonthlyCurrencyTotal("USD", BigDecimal("10.00")),
            MonthlyCurrencyTotal("EUR", BigDecimal("10.00"))
        ),
        estimatedTaxAmountGel = BigDecimal("0.58"),
        unresolvedFxCount = 0,
        zeroDeclarationSuggested = false,
        zeroDeclarationPrepared = false,
        reviewNeeded = false,
        setupRequired = false,
        record = null
    )
}
