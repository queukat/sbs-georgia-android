package com.queukat.sbsgeorgia.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.queukat.sbsgeorgia.R
import com.queukat.sbsgeorgia.domain.model.DashboardSummary
import com.queukat.sbsgeorgia.domain.model.DeclarationInclusion
import com.queukat.sbsgeorgia.domain.model.FilingWindow
import com.queukat.sbsgeorgia.domain.model.FxRateSource
import com.queukat.sbsgeorgia.domain.model.IncomeEntry
import com.queukat.sbsgeorgia.domain.model.IncomeSourceType
import com.queukat.sbsgeorgia.domain.model.MonthlyCurrencyTotal
import com.queukat.sbsgeorgia.domain.model.MonthlyDeclarationPeriod
import com.queukat.sbsgeorgia.domain.model.MonthlyDeclarationRecord
import com.queukat.sbsgeorgia.domain.model.MonthlyDeclarationSnapshot
import com.queukat.sbsgeorgia.domain.model.MonthlyWorkflowStatus
import com.queukat.sbsgeorgia.domain.model.ThemeMode
import com.queukat.sbsgeorgia.domain.usecase.buildDeclarationCopyBundle
import com.queukat.sbsgeorgia.ui.home.HomeDuePeriodQuickAccess
import com.queukat.sbsgeorgia.ui.home.HomeScreen
import com.queukat.sbsgeorgia.ui.home.HomeUiState
import com.queukat.sbsgeorgia.ui.monthdetails.MonthDetailScreen
import com.queukat.sbsgeorgia.ui.monthdetails.MonthDetailUiState
import com.queukat.sbsgeorgia.ui.months.MonthsMonthItemUiState
import com.queukat.sbsgeorgia.ui.months.MonthsScreen
import com.queukat.sbsgeorgia.ui.months.MonthsUiState
import com.queukat.sbsgeorgia.ui.months.MonthsYearSection
import com.queukat.sbsgeorgia.ui.theme.SbsGeorgiaTheme
import com.queukat.sbsgeorgia.ui.workflow.WorkflowStatusScreen
import com.queukat.sbsgeorgia.ui.workflow.WorkflowStatusUiState
import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
    fun monthsChartsActionIsVisibleAndInvokesNavigation() {
        var chartsOpened = false

        composeRule.setContent {
            SbsGeorgiaTheme(themeMode = ThemeMode.SYSTEM) {
                MonthsScreen(
                    innerPadding = PaddingValues(),
                    uiState = MonthsUiState(),
                    onMonthClick = {},
                    onSettleMonth = {},
                    onAddIncome = {},
                    onImportStatement = {},
                    onOpenCharts = { chartsOpened = true }
                )
            }
        }

        composeRule
            .onNodeWithTag("months-open-charts-button")
            .assertIsDisplayed()
            .performClick()

        composeRule.runOnIdle {
            assertEquals(true, chartsOpened)
        }
    }

    @Test
    fun monthPrimaryActionsUseTheSameHeight() {
        val snapshot = sampleSnapshot(unresolvedFxCount = 0)

        composeRule.setContent {
            SbsGeorgiaTheme(themeMode = ThemeMode.SYSTEM) {
                MonthsScreen(
                    innerPadding = PaddingValues(),
                    uiState =
                    MonthsUiState(
                        sections =
                        listOf(
                            MonthsYearSection(
                                year = 2026,
                                items =
                                listOf(
                                    MonthsMonthItemUiState(
                                        snapshot = snapshot,
                                        canQuickSettleMonth = true,
                                        monthAlreadySettled = false
                                    )
                                )
                            )
                        )
                    ),
                    onMonthClick = {},
                    onSettleMonth = {},
                    onAddIncome = {},
                    onImportStatement = {},
                    onOpenCharts = {}
                )
            }
        }

        val openMonth = composeRule.onNodeWithTag("months-open-month-button-2026-03")
        val completeMonth = composeRule.onNodeWithTag("months-complete-month-button-2026-03")
        completeMonth.performScrollTo()

        assertEquals(
            openMonth.fetchSemanticsNode().boundsInRoot.height,
            completeMonth.fetchSemanticsNode().boundsInRoot.height,
            1f
        )
    }

    @Test
    fun filingStatusMatchesOpenMonthHeightOnNarrowScreen() {
        assertMonthActionHeightMatchesOpenMonth(
            item =
            MonthsMonthItemUiState(
                snapshot = sampleSnapshot(unresolvedFxCount = 0),
                canQuickSettleMonth = false,
                monthAlreadySettled = false,
                filingOpensOn = LocalDate.of(2026, 12, 15)
            ),
            trailingActionTag = "months-filing-status-2026-03"
        )
    }

    @Test
    fun closedStatusMatchesOpenMonthHeightOnNarrowScreen() {
        assertMonthActionHeightMatchesOpenMonth(
            item =
            MonthsMonthItemUiState(
                snapshot = sampleSnapshot(unresolvedFxCount = 0),
                canQuickSettleMonth = false,
                monthAlreadySettled = true
            ),
            trailingActionTag = "months-closed-status-2026-03"
        )
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
                        uiState =
                        MonthsUiState(
                            sections =
                            listOf(
                                MonthsYearSection(
                                    year = 2026,
                                    items =
                                    listOf(
                                        MonthsMonthItemUiState(
                                            snapshot = snapshot,
                                            canQuickSettleMonth = true,
                                            monthAlreadySettled = false
                                        )
                                    )
                                )
                            )
                        ),
                        onMonthClick = { selectedMonth = it },
                        onSettleMonth = {},
                        onAddIncome = {},
                        onImportStatement = {},
                        onOpenCharts = {}
                    )
                } else {
                    MonthDetailScreen(
                        innerPadding = PaddingValues(),
                        uiState =
                        MonthDetailUiState(
                            yearMonth = selectedMonth,
                            snapshot = snapshot,
                            entries = listOf(sampleUnresolvedEntry())
                        ),
                        snackbarHostState = androidx.compose.material3.SnackbarHostState(),
                        onBack = { selectedMonth = null },
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
        }

        composeRule.onNodeWithText("Open month").performClick()

        composeRule.onNodeWithText("Declaration summary").assertIsDisplayed()
        composeRule.onNodeWithText("March 2026").assertIsDisplayed()
    }

    @Test
    fun homeDuePeriodQuickAccessNavigatesToMonthDetails() {
        val snapshot = sampleSnapshot(unresolvedFxCount = 0)
        val summary =
            DashboardSummary(
                taxpayerName = "Demo taxpayer",
                registrationId = "123456789",
                setupComplete = true,
                ytdIncomeGel = BigDecimal("350.00"),
                unresolvedFxCount = 0,
                unsettledMonthsCount = 1,
                paidTaxAmountGel = BigDecimal.ZERO,
                paymentMismatchMonthsCount = 0,
                currentDuePeriod = snapshot,
                nextReminderDay = null
            )
        var selectedMonth: YearMonth? by mutableStateOf(null)

        composeRule.setContent {
            SbsGeorgiaTheme(themeMode = ThemeMode.SYSTEM) {
                if (selectedMonth == null) {
                    HomeScreen(
                        innerPadding = PaddingValues(),
                        uiState =
                        HomeUiState(
                            summary = summary,
                            duePeriodQuickAccess =
                            HomeDuePeriodQuickAccess(
                                snapshot = snapshot,
                                copyBundle =
                                buildDeclarationCopyBundle(
                                    snapshot = snapshot,
                                    registrationId = summary.registrationId,
                                    yearMonth = snapshot.period.incomeMonth
                                ),
                                canCopyDeclarationValues = true,
                                canCopyPaymentText = true,
                                canQuickSettleMonth = true,
                                monthAlreadySettled = false,
                                filingOpensOn = null
                            )
                        ),
                        onOpenMonths = {},
                        onOpenDueMonth = { selectedMonth = it },
                        onAddIncome = {},
                        onImportStatement = {},
                        onOpenSettings = {},
                        onSettleCurrentDuePeriod = {}
                    )
                } else {
                    MonthDetailScreen(
                        innerPadding = PaddingValues(),
                        uiState =
                        MonthDetailUiState(
                            yearMonth = selectedMonth,
                            snapshot = snapshot,
                            entries = emptyList()
                        ),
                        snackbarHostState = androidx.compose.material3.SnackbarHostState(),
                        onBack = { selectedMonth = null },
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
        }

        composeRule
            .onNodeWithTag("home-field-20-copy")
            .performScrollTo()
            .assertIsDisplayed()
        composeRule
            .onNodeWithTag("home-copy-payment-text-button")
            .performScrollTo()
            .assertIsDisplayed()
        composeRule
            .onNodeWithTag("home-copy-all-text-button")
            .performScrollTo()
            .assertIsDisplayed()
        composeRule
            .onNodeWithTag("home-share-telegram-button")
            .performScrollTo()
            .assertIsDisplayed()
        composeRule
            .onNodeWithTag("home-open-due-month-button")
            .performScrollTo()
            .performClick()

        composeRule.onNodeWithText("Declaration summary").assertIsDisplayed()
        composeRule.onNodeWithText("March 2026").assertIsDisplayed()
    }

    @Test
    fun homeQuickSettleShowsConfirmationBeforeCallback() {
        val snapshot = sampleSnapshot(unresolvedFxCount = 0)
        val summary =
            DashboardSummary(
                taxpayerName = "Demo taxpayer",
                registrationId = "123456789",
                setupComplete = true,
                ytdIncomeGel = BigDecimal("350.00"),
                unresolvedFxCount = 0,
                unsettledMonthsCount = 1,
                paidTaxAmountGel = BigDecimal.ZERO,
                paymentMismatchMonthsCount = 0,
                currentDuePeriod = snapshot,
                nextReminderDay = null
            )
        var settleCalls = 0

        composeRule.setContent {
            SbsGeorgiaTheme(themeMode = ThemeMode.SYSTEM) {
                HomeScreen(
                    innerPadding = PaddingValues(),
                    uiState =
                    HomeUiState(
                        summary = summary,
                        duePeriodQuickAccess =
                        HomeDuePeriodQuickAccess(
                            snapshot = snapshot,
                            copyBundle =
                            buildDeclarationCopyBundle(
                                snapshot = snapshot,
                                registrationId = summary.registrationId,
                                yearMonth = snapshot.period.incomeMonth
                            ),
                            canCopyDeclarationValues = true,
                            canCopyPaymentText = true,
                            canQuickSettleMonth = true,
                            monthAlreadySettled = false,
                            filingOpensOn = null
                        )
                    ),
                    onOpenMonths = {},
                    onOpenDueMonth = {},
                    onAddIncome = {},
                    onImportStatement = {},
                    onOpenSettings = {},
                    onSettleCurrentDuePeriod = { settleCalls += 1 }
                )
            }
        }

        composeRule
            .onNodeWithTag("home-close-due-month-button")
            .performScrollTo()
            .performClick()

        composeRule.runOnIdle {
            assertEquals(0, settleCalls)
        }
        composeRule
            .onNodeWithTag("home-complete-month-confirm-dialog")
            .assertIsDisplayed()
        composeRule
            .onNodeWithTag("home-confirm-complete-month-button")
            .performClick()
        composeRule.runOnIdle {
            assertEquals(1, settleCalls)
        }
    }

    @Test
    fun monthsQuickSettleShowsConfirmationBeforeCallback() {
        val snapshot = sampleSnapshot(unresolvedFxCount = 0)
        var settledMonth: YearMonth? = null

        composeRule.setContent {
            SbsGeorgiaTheme(themeMode = ThemeMode.SYSTEM) {
                MonthsScreen(
                    innerPadding = PaddingValues(),
                    uiState =
                    MonthsUiState(
                        sections =
                        listOf(
                            MonthsYearSection(
                                year = 2026,
                                items =
                                listOf(
                                    MonthsMonthItemUiState(
                                        snapshot = snapshot,
                                        canQuickSettleMonth = true,
                                        monthAlreadySettled = false
                                    )
                                )
                            )
                        )
                    ),
                    onMonthClick = {},
                    onSettleMonth = { settledMonth = it },
                    onAddIncome = {},
                    onImportStatement = {},
                    onOpenCharts = {}
                )
            }
        }

        composeRule
            .onNodeWithTag("months-complete-month-button-2026-03")
            .performScrollTo()
            .performClick()

        composeRule.runOnIdle {
            assertNull(settledMonth)
        }
        composeRule
            .onNodeWithTag("months-complete-month-confirm-dialog")
            .assertIsDisplayed()
        composeRule
            .onNodeWithTag("months-confirm-complete-month-button")
            .performClick()
        composeRule.runOnIdle {
            assertEquals(YearMonth.of(2026, 3), settledMonth)
        }
    }

    @Test
    fun unresolvedFxStateIsVisibleInMonthDetails() {
        val snapshot = sampleSnapshot()
        var resolveCalls = 0

        composeRule.setContent {
            SbsGeorgiaTheme(themeMode = ThemeMode.SYSTEM) {
                MonthDetailScreen(
                    innerPadding = PaddingValues(),
                    uiState =
                    MonthDetailUiState(
                        yearMonth = YearMonth.of(2026, 3),
                        snapshot = snapshot,
                        entries = listOf(sampleUnresolvedEntry())
                    ),
                    snackbarHostState = androidx.compose.material3.SnackbarHostState(),
                    onBack = {},
                    onAddIncome = {},
                    onEditEntry = {},
                    onOpenFxOverride = {},
                    onOpenWorkflowStatus = {},
                    onOpenPaymentHelper = {},
                    onDeleteEntry = {},
                    onResolveOfficialRates = { resolveCalls += 1 },
                    onToggleZeroPrepared = {}
                )
            }
        }

        composeRule
            .onNodeWithTag("month-detail-next-resolve-fx-button")
            .assertIsDisplayed()
            .performClick()
        composeRule.runOnIdle {
            assertEquals(1, resolveCalls)
        }
        composeRule
            .onAllNodesWithText("Declaration and payment values")
            .assertCountEquals(0)
    }

    @Test
    fun filedMonthShowsPaymentHelperAsNextAction() {
        val month = YearMonth.of(2026, 3)
        val snapshot =
            sampleSnapshot(
                workflowStatus = MonthlyWorkflowStatus.FILED,
                unresolvedFxCount = 0,
                record =
                MonthlyDeclarationRecord(
                    yearMonth = month,
                    workflowStatus = MonthlyWorkflowStatus.FILED,
                    zeroDeclarationPrepared = false,
                    declarationFiledDate = LocalDate.of(2026, 4, 10)
                )
            )
        var openedPaymentMonth: YearMonth? = null

        composeRule.setContent {
            SbsGeorgiaTheme(themeMode = ThemeMode.SYSTEM) {
                MonthDetailScreen(
                    innerPadding = PaddingValues(),
                    uiState =
                    MonthDetailUiState(
                        yearMonth = month,
                        snapshot = snapshot,
                        entries = emptyList(),
                        isFilingWindowOpen = true
                    ),
                    snackbarHostState = androidx.compose.material3.SnackbarHostState(),
                    onBack = {},
                    onAddIncome = {},
                    onEditEntry = {},
                    onOpenFxOverride = {},
                    onOpenWorkflowStatus = {},
                    onOpenPaymentHelper = { openedPaymentMonth = it },
                    onDeleteEntry = {},
                    onResolveOfficialRates = {},
                    onToggleZeroPrepared = {}
                )
            }
        }

        composeRule
            .onNodeWithTag("month-detail-next-prepare-payment-button")
            .assertIsDisplayed()
            .performClick()

        composeRule.runOnIdle {
            assertEquals(month, openedPaymentMonth)
        }
    }

    @Test
    fun filedZeroTaxMonthDoesNotOfferPaymentHelper() {
        val month = YearMonth.of(2026, 3)
        val snapshot =
            sampleSnapshot(
                workflowStatus = MonthlyWorkflowStatus.FILED,
                unresolvedFxCount = 0,
                estimatedTaxAmountGel = BigDecimal.ZERO,
                record =
                MonthlyDeclarationRecord(
                    yearMonth = month,
                    workflowStatus = MonthlyWorkflowStatus.FILED,
                    zeroDeclarationPrepared = true,
                    declarationFiledDate = LocalDate.of(2026, 4, 10)
                )
            )

        composeRule.setContent {
            SbsGeorgiaTheme(themeMode = ThemeMode.SYSTEM) {
                MonthDetailScreen(
                    innerPadding = PaddingValues(),
                    uiState =
                    MonthDetailUiState(
                        yearMonth = month,
                        snapshot = snapshot,
                        entries = emptyList(),
                        isFilingWindowOpen = true
                    ),
                    snackbarHostState = androidx.compose.material3.SnackbarHostState(),
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
            .onAllNodesWithTag("month-detail-next-prepare-payment-button")
            .assertCountEquals(0)
    }

    @Test
    fun overdueZeroMonthOffersFilingCompletionWithoutPaymentLanguage() {
        val snapshot =
            sampleSnapshot(
                workflowStatus = MonthlyWorkflowStatus.OVERDUE,
                unresolvedFxCount = 0,
                estimatedTaxAmountGel = BigDecimal.ZERO
            ).copy(zeroDeclarationSuggested = true)
        val context = InstrumentationRegistry.getInstrumentation().targetContext

        composeRule.setContent {
            SbsGeorgiaTheme(themeMode = ThemeMode.SYSTEM) {
                MonthsScreen(
                    innerPadding = PaddingValues(),
                    uiState =
                    MonthsUiState(
                        sections =
                        listOf(
                            MonthsYearSection(
                                year = 2026,
                                items =
                                listOf(
                                    MonthsMonthItemUiState(
                                        snapshot = snapshot,
                                        canQuickSettleMonth = true,
                                        monthAlreadySettled = false,
                                        paymentRequired = false
                                    )
                                )
                            )
                        )
                    ),
                    onMonthClick = {},
                    onSettleMonth = {},
                    onAddIncome = {},
                    onImportStatement = {},
                    onOpenCharts = {}
                )
            }
        }

        composeRule
            .onNodeWithText(context.getString(R.string.months_mark_zero_declaration_filed))
            .performScrollTo()
            .assertIsDisplayed()
            .performClick()
        composeRule
            .onNodeWithText(context.getString(R.string.months_zero_complete_confirm_body))
            .assertIsDisplayed()
        composeRule
            .onAllNodesWithText(context.getString(R.string.months_complete_confirm_body))
            .assertCountEquals(0)
    }

    @Test
    fun workflowStatusTitleUsesLocalizedMonthName() {
        composeRule.setContent {
            SbsGeorgiaTheme(themeMode = ThemeMode.SYSTEM) {
                WorkflowStatusScreen(
                    innerPadding = PaddingValues(),
                    uiState =
                    WorkflowStatusUiState(
                        yearMonth = YearMonth.of(2026, 3),
                        dueDate = LocalDate.of(2026, 4, 15),
                        derivedStatus = MonthlyWorkflowStatus.READY_TO_FILE,
                        baseStatus = MonthlyWorkflowStatus.READY_TO_FILE,
                        editableStatuses = listOf(MonthlyWorkflowStatus.FILED)
                    ),
                    onBack = {},
                    onStatusChanged = {},
                    onZeroDeclarationPreparedChanged = {},
                    onDeclarationFiledDateChanged = {},
                    onClearDeclarationFiledDate = {},
                    onPaymentSentDateChanged = {},
                    onClearPaymentSentDate = {},
                    onPaymentCreditedDateChanged = {},
                    onClearPaymentCreditedDate = {},
                    onPaymentAmountChanged = {},
                    onNotesChanged = {},
                    onSave = {}
                )
            }
        }

        composeRule.onNodeWithText("March 2026").assertIsDisplayed()
        composeRule.onAllNodesWithText("2026-03").assertCountEquals(0)
    }

    @Test
    fun taxPaymentMismatchIsVisibleInMonthsList() {
        val snapshot =
            sampleSnapshot(
                workflowStatus = MonthlyWorkflowStatus.SETTLED,
                unresolvedFxCount = 0,
                record =
                MonthlyDeclarationRecord(
                    yearMonth = YearMonth.of(2026, 3),
                    workflowStatus = MonthlyWorkflowStatus.SETTLED,
                    zeroDeclarationPrepared = false,
                    declarationFiledDate = LocalDate.of(2026, 4, 10),
                    paymentSentDate = LocalDate.of(2026, 4, 10),
                    paymentCreditedDate = LocalDate.of(2026, 4, 10),
                    paymentAmountGel = BigDecimal("1.00")
                )
            )

        composeRule.setContent {
            SbsGeorgiaTheme(themeMode = ThemeMode.SYSTEM) {
                MonthsScreen(
                    innerPadding = PaddingValues(),
                    uiState =
                    MonthsUiState(
                        sections =
                        listOf(
                            MonthsYearSection(
                                year = 2026,
                                items =
                                listOf(
                                    MonthsMonthItemUiState(
                                        snapshot = snapshot,
                                        canQuickSettleMonth = false,
                                        monthAlreadySettled = true
                                    )
                                )
                            )
                        )
                    ),
                    onMonthClick = {},
                    onSettleMonth = {},
                    onAddIncome = {},
                    onImportStatement = {},
                    onOpenCharts = {}
                )
            }
        }

        composeRule
            .onNodeWithTag("snapshot-tax-payment-mismatch")
            .performScrollTo()
            .assertIsDisplayed()
    }

    private fun sampleSnapshot(
        workflowStatus: MonthlyWorkflowStatus = MonthlyWorkflowStatus.DRAFT,
        unresolvedFxCount: Int = 1,
        estimatedTaxAmountGel: BigDecimal = BigDecimal("3.50"),
        record: MonthlyDeclarationRecord? = null
    ): MonthlyDeclarationSnapshot {
        val month = YearMonth.of(2026, 3)
        return MonthlyDeclarationSnapshot(
            period =
            MonthlyDeclarationPeriod(
                incomeMonth = month,
                filingWindow =
                FilingWindow(
                    start = LocalDate.of(2026, 4, 1),
                    endInclusive = LocalDate.of(2026, 4, 15),
                    dueDate = LocalDate.of(2026, 4, 15)
                ),
                inScope = true,
                outOfScope = false
            ),
            workflowStatus = workflowStatus,
            graph20TotalGel = BigDecimal("350.00"),
            graph15CumulativeGel = BigDecimal("350.00"),
            originalCurrencyTotals = listOf(MonthlyCurrencyTotal("USD", BigDecimal("125.50"))),
            estimatedTaxAmountGel = estimatedTaxAmountGel,
            unresolvedFxCount = unresolvedFxCount,
            zeroDeclarationSuggested = false,
            zeroDeclarationPrepared = false,
            reviewNeeded = false,
            setupRequired = false,
            record = record
        )
    }

    private fun assertMonthActionHeightMatchesOpenMonth(item: MonthsMonthItemUiState, trailingActionTag: String) {
        composeRule.setContent {
            SbsGeorgiaTheme(themeMode = ThemeMode.SYSTEM) {
                Box(
                    modifier =
                    Modifier
                        .width(320.dp)
                        .fillMaxHeight()
                ) {
                    MonthsScreen(
                        innerPadding = PaddingValues(),
                        uiState =
                        MonthsUiState(
                            sections =
                            listOf(
                                MonthsYearSection(
                                    year = 2026,
                                    items = listOf(item)
                                )
                            )
                        ),
                        onMonthClick = {},
                        onSettleMonth = {},
                        onAddIncome = {},
                        onImportStatement = {},
                        onOpenCharts = {}
                    )
                }
            }
        }

        val openMonth = composeRule.onNodeWithTag("months-open-month-button-2026-03")
        val trailingAction = composeRule.onNodeWithTag(trailingActionTag)
        trailingAction.performScrollTo()

        assertEquals(
            openMonth.fetchSemanticsNode().boundsInRoot.height,
            trailingAction.fetchSemanticsNode().boundsInRoot.height,
            1f
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
        updatedAtEpochMillis = 1L
    )
}
