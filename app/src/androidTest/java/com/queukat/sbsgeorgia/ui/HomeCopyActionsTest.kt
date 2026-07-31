package com.queukat.sbsgeorgia.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.queukat.sbsgeorgia.domain.model.DashboardSummary
import com.queukat.sbsgeorgia.domain.model.FilingWindow
import com.queukat.sbsgeorgia.domain.model.MonthlyDeclarationPeriod
import com.queukat.sbsgeorgia.domain.model.MonthlyDeclarationSnapshot
import com.queukat.sbsgeorgia.domain.model.MonthlyWorkflowStatus
import com.queukat.sbsgeorgia.domain.model.ThemeMode
import com.queukat.sbsgeorgia.domain.usecase.buildDeclarationCopyBundle
import com.queukat.sbsgeorgia.ui.home.HomeDuePeriodQuickAccess
import com.queukat.sbsgeorgia.ui.home.HomeScreen
import com.queukat.sbsgeorgia.ui.home.HomeUiState
import com.queukat.sbsgeorgia.ui.theme.SbsGeorgiaTheme
import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HomeCopyActionsTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Before
    fun skipOnTvDevices() {
        assumePhoneLikeComposeTestDevice()
    }

    @Test
    fun aggregateCopyActionsUseStablePrimaryAndSecondaryRowsOnNarrowScreen() {
        composeRule.setContent {
            SbsGeorgiaTheme(themeMode = ThemeMode.SYSTEM) {
                Box(
                    modifier =
                    androidx.compose.ui.Modifier
                        .width(320.dp)
                        .fillMaxHeight()
                ) {
                    HomeScreen(
                        innerPadding = PaddingValues(),
                        uiState = homeStateWithCopyActions(),
                        onOpenMonths = {},
                        onOpenDueMonth = {},
                        onAddIncome = {},
                        onImportStatement = {},
                        onOpenSettings = {},
                        onSettleCurrentDuePeriod = {}
                    )
                }
            }
        }

        val copyAll = composeRule.onNodeWithTag("home-copy-all-text-button")
        val copyBankText = composeRule.onNodeWithTag("home-copy-payment-text-button")
        val share = composeRule.onNodeWithTag("home-share-telegram-button")
        share.performScrollTo()

        val copyAllBounds = copyAll.fetchSemanticsNode().boundsInRoot
        val copyBankTextBounds = copyBankText.fetchSemanticsNode().boundsInRoot
        val shareBounds = share.fetchSemanticsNode().boundsInRoot

        assertTrue(copyAllBounds.width > copyBankTextBounds.width)
        assertTrue(copyBankTextBounds.width > shareBounds.width)
        assertEquals(copyBankTextBounds.top, shareBounds.top, 1f)
        assertTrue(copyAllBounds.bottom <= copyBankTextBounds.top)
    }

    private fun homeStateWithCopyActions(): HomeUiState {
        val month = YearMonth.of(2026, 3)
        val snapshot =
            MonthlyDeclarationSnapshot(
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
                workflowStatus = MonthlyWorkflowStatus.READY_TO_FILE,
                graph20TotalGel = BigDecimal("350.00"),
                graph15CumulativeGel = BigDecimal("350.00"),
                originalCurrencyTotals = emptyList(),
                estimatedTaxAmountGel = BigDecimal("3.50"),
                unresolvedFxCount = 0,
                zeroDeclarationSuggested = false,
                zeroDeclarationPrepared = false,
                reviewNeeded = false,
                setupRequired = false,
                record = null
            )
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

        return HomeUiState(
            summary = summary,
            duePeriodQuickAccess =
            HomeDuePeriodQuickAccess(
                snapshot = snapshot,
                copyBundle =
                buildDeclarationCopyBundle(
                    snapshot = snapshot,
                    registrationId = summary.registrationId,
                    yearMonth = month
                ),
                canCopyDeclarationValues = true,
                canCopyPaymentText = true,
                canQuickSettleMonth = false,
                monthAlreadySettled = false,
                filingOpensOn = null
            )
        )
    }
}
