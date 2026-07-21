package com.queukat.sbsgeorgia.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.queukat.sbsgeorgia.domain.model.DeclarationInclusion
import com.queukat.sbsgeorgia.domain.model.StatementMoney
import com.queukat.sbsgeorgia.domain.model.ThemeMode
import com.queukat.sbsgeorgia.ui.importstatement.ImportStatementImportSuccessUiState
import com.queukat.sbsgeorgia.ui.importstatement.ImportStatementRowUiState
import com.queukat.sbsgeorgia.ui.importstatement.ImportStatementScreen
import com.queukat.sbsgeorgia.ui.importstatement.ImportStatementUiState
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
class ImportStatementScreenTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Before
    fun skipOnTvDevices() {
        assumePhoneLikeComposeTestDevice()
    }

    @Test
    fun importPreviewHappyPathAllowsCorrectionAndImport() {
        var uiState by mutableStateOf(
            ImportStatementUiState(
                sourceFileName = "tbc-statement.pdf",
                rows = listOf(sampleRow()),
                selectedIncomeCount = 1,
                canImport = true
            )
        )
        var importClicked = false

        composeRule.setContent {
            TestImportStatementScreen(
                uiState = uiState,
                onIncludeAsTaxableChanged = { fingerprint, included ->
                    uiState =
                        uiState.copy(
                            rows =
                            uiState.rows.map { row ->
                                if (row.transactionFingerprint == fingerprint) {
                                    row.copy(
                                        finalInclusion =
                                        if (included) {
                                            DeclarationInclusion.INCLUDED
                                        } else {
                                            DeclarationInclusion.EXCLUDED
                                        }
                                    )
                                } else {
                                    row
                                }
                            }
                        )
                },
                onAmountChanged = { fingerprint, amount ->
                    uiState =
                        uiState.copy(
                            rows =
                            uiState.rows.map { row ->
                                if (row.transactionFingerprint == fingerprint) {
                                    row.copy(amount = amount)
                                } else {
                                    row
                                }
                            }
                        )
                },
                onCurrencyChanged = { fingerprint, currency ->
                    uiState =
                        uiState.copy(
                            rows =
                            uiState.rows.map { row ->
                                if (row.transactionFingerprint == fingerprint) {
                                    row.copy(currency = currency)
                                } else {
                                    row
                                }
                            }
                        )
                },
                onSourceCategoryChanged = { fingerprint, category ->
                    uiState =
                        uiState.copy(
                            rows =
                            uiState.rows.map { row ->
                                if (row.transactionFingerprint == fingerprint) {
                                    row.copy(sourceCategory = category)
                                } else {
                                    row
                                }
                            }
                        )
                },
                onImportApproved = { importClicked = true }
            )
        }

        composeRule.onNodeWithTag(
            "import-selected-file"
        ).assertTextContains("tbc-statement.pdf", substring = true)
        composeRule.onNodeWithTag("import-review-summary").assertIsDisplayed()
        composeRule.onNodeWithTag("import-summary-will-import").assertTextContains("1")
        composeRule.onAllNodesWithTag("import-amount-tx-1").assertCountEquals(0)

        composeRule.onNodeWithTag("import-row-toggle-tx-1").performClick()
        composeRule.onNodeWithTag("import-amount-tx-1").performTextClearance()
        composeRule.onNodeWithTag("import-amount-tx-1").performTextInput("130.00")
        composeRule.onNodeWithTag("import-currency-tx-1").performTextClearance()
        composeRule.onNodeWithTag("import-currency-tx-1").performTextInput("EUR")
        composeRule.onNodeWithTag("import-category-tx-1").performTextClearance()
        composeRule.onNodeWithTag("import-category-tx-1").performTextInput("Edited category")
        composeRule.onNodeWithTag("import-statement-import-button").performClick()

        assertTrue(importClicked)
        composeRule.onNodeWithTag("import-amount-tx-1").assertTextContains("130.00")
        composeRule.onNodeWithTag("import-currency-tx-1").assertTextContains("EUR")
        composeRule.onNodeWithTag("import-category-tx-1").assertTextContains("Edited category")
    }

    @Test
    fun filtersShowProblemRowsAndDuplicatesSeparately() {
        var excludePendingReviewClicked = false
        val uiState =
            ImportStatementUiState(
                sourceFileName = "tbc-statement.pdf",
                rows =
                listOf(
                    sampleRow(),
                    sampleRow(
                        fingerprint = "tx-2",
                        suggestedInclusion = DeclarationInclusion.REVIEW_REQUIRED,
                        finalInclusion = DeclarationInclusion.EXCLUDED
                    ),
                    sampleRow(
                        fingerprint = "tx-3",
                        finalInclusion = DeclarationInclusion.EXCLUDED,
                        isTaxPaymentCandidate = true,
                        reviewDecisionMade = true
                    ),
                    sampleRow(
                        fingerprint = "tx-4",
                        finalInclusion = DeclarationInclusion.EXCLUDED,
                        duplicate = true
                    ),
                    sampleRow(
                        fingerprint = "tx-5",
                        finalInclusion = DeclarationInclusion.EXCLUDED,
                        isTaxPaymentCandidate = true,
                        duplicate = true
                    )
                ),
                selectedIncomeCount = 1,
                detectedTaxPaymentCount = 1,
                canImport = true
            )

        composeRule.setContent {
            TestImportStatementScreen(
                uiState = uiState,
                onExcludePendingReviewRows = {
                    excludePendingReviewClicked = true
                }
            )
        }

        composeRule.onNodeWithTag("import-summary-needs-review").assertTextContains("1")
        composeRule.onNodeWithTag("import-review-pending-decisions").assertTextContains("1")
        composeRule.onNodeWithTag("import-review-exclude-pending").performClick()
        composeRule.runOnIdle {
            assertTrue(excludePendingReviewClicked)
        }
        composeRule.onNodeWithTag("import-filter-tax-payments").assertIsDisplayed()
        composeRule.onNodeWithTag("import-row-tx-3").assertIsDisplayed()
        composeRule.onAllNodesWithTag("import-row-tx-2").assertCountEquals(0)
        composeRule.onAllNodesWithTag("import-row-tx-5").assertCountEquals(0)
        composeRule.onAllNodesWithTag("import-row-tx-1").assertCountEquals(0)

        composeRule.onNodeWithTag("import-filter-needs-review").performClick()
        composeRule.onNodeWithTag("import-row-tx-2").assertIsDisplayed()
        composeRule.onAllNodesWithTag("import-row-tx-3").assertCountEquals(0)

        composeRule.onNodeWithTag("import-summary-tax-payments").performClick()
        composeRule.onNodeWithTag("import-row-tx-3").assertIsDisplayed()

        composeRule.onNodeWithTag("import-filter-will-import").performClick()
        composeRule.onNodeWithTag("import-row-tx-1").assertIsDisplayed()
        composeRule.onAllNodesWithTag("import-row-tx-2").assertCountEquals(0)

        composeRule.onNodeWithTag("import-filter-duplicates").performClick()
        composeRule.onNodeWithTag("import-row-tx-4").assertIsDisplayed()
    }

    @Test
    fun successStateOffersMonthAndMonthsNavigation() {
        var openedMonth: YearMonth? = null
        var openedMonths = false

        composeRule.setContent {
            TestImportStatementScreen(
                uiState =
                ImportStatementUiState(
                    importSuccess =
                    ImportStatementImportSuccessUiState(
                        importedIncomeCount = 3,
                        storedTransactionCount = 4,
                        skippedDuplicateCount = 1,
                        excludedCount = 2,
                        targetMonth = YearMonth.of(2026, 3),
                        detailMessage = "Imported 3 income rows."
                    )
                ),
                onOpenMonth = { openedMonth = it },
                onOpenMonths = { openedMonths = true }
            )
        }

        composeRule.onNodeWithTag("import-success-state").assertIsDisplayed()
        composeRule.onNodeWithTag("import-success-open-month").performClick()
        composeRule.runOnIdle {
            assertEquals(YearMonth.of(2026, 3), openedMonth)
        }

        composeRule.onNodeWithTag("import-success-open-months").performClick()
        composeRule.runOnIdle {
            assertTrue(openedMonths)
        }
    }

    @androidx.compose.runtime.Composable
    private fun TestImportStatementScreen(
        uiState: ImportStatementUiState,
        onIncludeAsTaxableChanged: (String, Boolean) -> Unit = { _, _ -> },
        onAmountChanged: (String, String) -> Unit = { _, _ -> },
        onCurrencyChanged: (String, String) -> Unit = { _, _ -> },
        onSourceCategoryChanged: (String, String) -> Unit = { _, _ -> },
        onExcludePendingReviewRows: () -> Unit = {},
        onImportApproved: () -> Unit = {},
        onOpenMonth: (YearMonth) -> Unit = {},
        onOpenMonths: () -> Unit = {}
    ) {
        SbsGeorgiaTheme(themeMode = ThemeMode.SYSTEM) {
            ImportStatementScreen(
                innerPadding = PaddingValues(),
                uiState = uiState,
                snackbarHostState = SnackbarHostState(),
                onBack = {},
                onPickPdf = {},
                onIncludeAsTaxableChanged = onIncludeAsTaxableChanged,
                onDateChanged = { _, _ -> },
                onAmountChanged = onAmountChanged,
                onCurrencyChanged = onCurrencyChanged,
                onSourceCategoryChanged = onSourceCategoryChanged,
                onExcludePendingReviewRows = onExcludePendingReviewRows,
                onImportApproved = onImportApproved,
                onOpenMonth = onOpenMonth,
                onOpenMonths = onOpenMonths
            )
        }
    }

    private fun sampleRow(
        fingerprint: String = "tx-1",
        suggestedInclusion: DeclarationInclusion = DeclarationInclusion.INCLUDED,
        finalInclusion: DeclarationInclusion = DeclarationInclusion.INCLUDED,
        isTaxPaymentCandidate: Boolean = false,
        duplicate: Boolean = false
    ): ImportStatementRowUiState = ImportStatementRowUiState(
        transactionFingerprint = fingerprint,
        incomeDate = LocalDate.of(2026, 3, 15),
        description = "FOR SOFTWARE SERVICES",
        additionalInformation = "Invoice 001",
        paidOut = StatementMoney(BigDecimal("0.00"), "USD"),
        paidIn = StatementMoney(BigDecimal("125.50"), "USD"),
        balance = StatementMoney(BigDecimal("1240.75"), "USD"),
        suggestedInclusion = suggestedInclusion,
        finalInclusion = finalInclusion,
        amount = "125.50",
        currency = "USD",
        sourceCategory = "Software services",
        isTaxPaymentCandidate = isTaxPaymentCandidate,
        duplicate = duplicate
    )
}
