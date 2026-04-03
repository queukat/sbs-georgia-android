package com.queukat.sbsgeorgia.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.queukat.sbsgeorgia.domain.model.DeclarationInclusion
import com.queukat.sbsgeorgia.domain.model.FilingWindow
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
import com.queukat.sbsgeorgia.ui.months.MonthsMonthItemUiState
import com.queukat.sbsgeorgia.ui.months.MonthsScreen
import com.queukat.sbsgeorgia.ui.months.MonthsUiState
import com.queukat.sbsgeorgia.ui.months.MonthsYearSection
import com.queukat.sbsgeorgia.ui.theme.SbsGeorgiaTheme
import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MonthsFlowTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Before
    fun skipOnTvDevices() {
        assumePhoneLikeComposeTestDevice()
    }

    @Test
    fun monthsListNavigatesToMonthDetails() {
        val snapshot = sampleSnapshot()
        var selectedMonth: YearMonth? by mutableStateOf(null)

        composeRule.setContent {
            SbsGeorgiaTheme(themeMode = ThemeMode.SYSTEM) {
                if (selectedMonth == null) {
                    MonthsScreen(
                        innerPadding = PaddingValues(),
                        uiState = MonthsUiState(
                            sections = listOf(
                                MonthsYearSection(
                                    year = 2026,
                                    items = listOf(
                                        MonthsMonthItemUiState(
                                            snapshot = snapshot,
                                            canQuickMarkDeclarationFiled = true,
                                            declarationAlreadyFiled = false,
                                        ),
                                    ),
                                ),
                            ),
                        ),
                        onMonthClick = { selectedMonth = it },
                        onMarkDeclarationFiled = {},
                        onAddIncome = {},
                        onImportStatement = {},
                    )
                } else {
                    MonthDetailScreen(
                        innerPadding = PaddingValues(),
                        uiState = MonthDetailUiState(
                            yearMonth = selectedMonth,
                            snapshot = snapshot,
                            entries = listOf(sampleUnresolvedEntry()),
                        ),
                        snackbarHostState = androidx.compose.material3.SnackbarHostState(),
                        onBack = { selectedMonth = null },
                        onAddIncome = {},
                        onEditEntry = {},
                        onOpenPaymentHelper = {},
                        onOpenFxOverride = {},
                        onOpenWorkflowStatus = {},
                        onDeleteEntry = {},
                        onResolveOfficialRates = {},
                        onToggleZeroPrepared = {},
                    )
                }
            }
        }

        composeRule.onNodeWithText("Open month").performClick()

        composeRule.onNodeWithText("Declaration summary").assertIsDisplayed()
        composeRule.onNodeWithText("March 2026").assertIsDisplayed()
    }

    @Test
    fun unresolvedFxStateIsVisibleInMonthDetails() {
        val snapshot = sampleSnapshot()

        composeRule.setContent {
            SbsGeorgiaTheme(themeMode = ThemeMode.SYSTEM) {
                MonthDetailScreen(
                    innerPadding = PaddingValues(),
                    uiState = MonthDetailUiState(
                        yearMonth = YearMonth.of(2026, 3),
                        snapshot = snapshot,
                        entries = listOf(sampleUnresolvedEntry()),
                    ),
                    snackbarHostState = androidx.compose.material3.SnackbarHostState(),
                    onBack = {},
                    onAddIncome = {},
                    onEditEntry = {},
                    onOpenPaymentHelper = {},
                    onOpenFxOverride = {},
                    onOpenWorkflowStatus = {},
                    onDeleteEntry = {},
                    onResolveOfficialRates = {},
                    onToggleZeroPrepared = {},
                )
            }
        }

        composeRule.onNodeWithTag("month-detail-unresolved-fx-message")
            .performScrollTo()
            .assertIsDisplayed()
    }

    private fun sampleSnapshot(): MonthlyDeclarationSnapshot {
        val month = YearMonth.of(2026, 3)
        return MonthlyDeclarationSnapshot(
            period = MonthlyDeclarationPeriod(
                incomeMonth = month,
                filingWindow = FilingWindow(
                    start = LocalDate.of(2026, 4, 1),
                    endInclusive = LocalDate.of(2026, 4, 15),
                    dueDate = LocalDate.of(2026, 4, 15),
                ),
                inScope = true,
                outOfScope = false,
            ),
            workflowStatus = MonthlyWorkflowStatus.DRAFT,
            graph20TotalGel = BigDecimal("350.00"),
            graph15CumulativeGel = BigDecimal("350.00"),
            originalCurrencyTotals = listOf(MonthlyCurrencyTotal("USD", BigDecimal("125.50"))),
            estimatedTaxAmountGel = BigDecimal("3.50"),
            unresolvedFxCount = 1,
            zeroDeclarationSuggested = false,
            zeroDeclarationPrepared = false,
            reviewNeeded = false,
            setupRequired = false,
            record = null,
        )
    }

    private fun sampleUnresolvedEntry(): IncomeEntry = IncomeEntry(
        id = 1L,
        sourceType = IncomeSourceType.MANUAL,
        incomeDate = LocalDate.of(2026, 3, 15),
        originalAmount = BigDecimal("125.50"),
        originalCurrency = "USD",
        sourceCategory = "Software services",
        note = "Invoice 001",
        declarationInclusion = DeclarationInclusion.INCLUDED,
        gelEquivalent = null,
        rateSource = FxRateSource.NONE,
        manualFxOverride = false,
        createdAtEpochMillis = 1L,
        updatedAtEpochMillis = 1L,
    )
}
