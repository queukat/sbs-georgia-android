package com.queukat.sbsgeorgia.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.queukat.sbsgeorgia.domain.model.ThemeMode
import com.queukat.sbsgeorgia.ui.manualentry.ManualEntryScreen
import com.queukat.sbsgeorgia.ui.manualentry.ManualEntryUiState
import com.queukat.sbsgeorgia.ui.theme.SbsGeorgiaTheme
import java.time.LocalDate
import org.junit.Before
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ManualEntryScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Before
    fun skipOnTvDevices() {
        assumePhoneLikeComposeTestDevice()
    }

    @Test
    fun manualEntryCreateFlowCallsSaveWithUpdatedState() {
        var uiState by mutableStateOf(
            ManualEntryUiState(
                incomeDate = LocalDate.of(2026, 4, 2),
            ),
        )
        var saveClicked = false

        composeRule.setContent {
            SbsGeorgiaTheme(themeMode = ThemeMode.SYSTEM) {
                ManualEntryScreen(
                    innerPadding = PaddingValues(),
                    uiState = uiState,
                    onBack = {},
                    onDateChanged = { uiState = uiState.copy(incomeDate = it) },
                    onAmountChanged = { uiState = uiState.copy(amount = it) },
                    onCurrencyChanged = { uiState = uiState.copy(currency = it) },
                    onCategoryChanged = { uiState = uiState.copy(sourceCategory = it) },
                    onNoteChanged = { uiState = uiState.copy(note = it) },
                    onIncludedChanged = { uiState = uiState.copy(declarationIncluded = it) },
                    onSave = { saveClicked = true },
                )
            }
        }

        composeRule.onNodeWithTag("manual-entry-amount-field").performTextInput("125.50")
        composeRule.onNodeWithTag("manual-entry-category-field").performTextClearance()
        composeRule.onNodeWithTag("manual-entry-category-field").performTextInput("Consulting")
        composeRule.onNodeWithTag("manual-entry-save-button").performClick()

        assertTrue(saveClicked)
        composeRule.onNodeWithTag("manual-entry-category-field").assertTextContains("Consulting")
    }

    @Test
    fun manualEntryEditFlowShowsEditTitle() {
        composeRule.setContent {
            SbsGeorgiaTheme(themeMode = ThemeMode.SYSTEM) {
                ManualEntryScreen(
                    innerPadding = PaddingValues(),
                    uiState = ManualEntryUiState(
                        entryId = 42L,
                        incomeDate = LocalDate.of(2026, 4, 2),
                        amount = "200",
                    ),
                    onBack = {},
                    onDateChanged = {},
                    onAmountChanged = {},
                    onCurrencyChanged = {},
                    onCategoryChanged = {},
                    onNoteChanged = {},
                    onIncludedChanged = {},
                    onSave = {},
                )
            }
        }

        composeRule.onNodeWithText("Edit income entry").assertIsDisplayed()
    }
}
