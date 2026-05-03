package com.queukat.sbsgeorgia.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.queukat.sbsgeorgia.domain.model.DeclarationInclusion
import com.queukat.sbsgeorgia.domain.model.ThemeMode
import com.queukat.sbsgeorgia.ui.importstatement.ImportStatementRowUiState
import com.queukat.sbsgeorgia.ui.importstatement.ImportStatementScreen
import com.queukat.sbsgeorgia.ui.importstatement.ImportStatementUiState
import com.queukat.sbsgeorgia.ui.theme.SbsGeorgiaTheme
import java.time.LocalDate
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
                rows =
                listOf(
                    ImportStatementRowUiState(
                        transactionFingerprint = "tx-1",
                        incomeDate = LocalDate.of(2026, 3, 15),
                        description = "FOR SOFTWARE SERVICES",
                        additionalInformation = "Invoice 001",
                        paidOutLabel = "0.00 USD",
                        paidInLabel = "125.50 USD",
                        balanceLabel = "1240.75 USD",
                        suggestedInclusion = DeclarationInclusion.INCLUDED,
                        finalInclusion = DeclarationInclusion.INCLUDED,
                        amount = "125.50",
                        currency = "USD",
                        sourceCategory = "Software services",
                        duplicate = false
                    )
                ),
                selectedIncomeCount = 1
            )
        )
        var importClicked = false

        composeRule.setContent {
            SbsGeorgiaTheme(themeMode = ThemeMode.SYSTEM) {
                ImportStatementScreen(
                    innerPadding = PaddingValues(),
                    uiState = uiState,
                    snackbarHostState = SnackbarHostState(),
                    onBack = {},
                    onPickPdf = {},
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
                    onDateChanged = { _, _ -> },
                    onAmountChanged = { fingerprint, amount ->
                        uiState =
                            uiState.copy(
                                rows =
                                uiState.rows.map { row ->
                                    if (row.transactionFingerprint ==
                                        fingerprint
                                    ) {
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
                                    if (row.transactionFingerprint ==
                                        fingerprint
                                    ) {
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
                                    if (row.transactionFingerprint ==
                                        fingerprint
                                    ) {
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
        }

        composeRule.onNodeWithTag(
            "import-selected-file"
        ).assertTextContains("tbc-statement.pdf", substring = true)
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
}
