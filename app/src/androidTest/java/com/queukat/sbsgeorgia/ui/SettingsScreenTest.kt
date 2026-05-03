package com.queukat.sbsgeorgia.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.queukat.sbsgeorgia.domain.model.ThemeMode
import com.queukat.sbsgeorgia.ui.settings.SettingsScreen
import com.queukat.sbsgeorgia.ui.settings.SettingsUiState
import com.queukat.sbsgeorgia.ui.theme.SbsGeorgiaTheme
import java.time.LocalDate
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Before
    fun skipOnTvDevices() {
        assumePhoneLikeComposeTestDevice()
    }

    @Test
    fun settingsHappyPathUpdatesFieldsAndInvokesSave() {
        var uiState by mutableStateOf(
            SettingsUiState(
                effectiveDate = LocalDate.of(2026, 3, 7),
                themeMode = ThemeMode.SYSTEM
            )
        )
        var saveClicked = false

        composeRule.setContent {
            SbsGeorgiaTheme(themeMode = ThemeMode.SYSTEM) {
                SettingsScreen(
                    innerPadding = PaddingValues(),
                    uiState = uiState,
                    snackbarHostState = SnackbarHostState(),
                    notificationPermissionGranted = true,
                    onRegistrationIdChanged = { uiState = uiState.copy(registrationId = it) },
                    onDisplayNameChanged = { uiState = uiState.copy(displayName = it) },
                    onEffectiveDateChanged = { uiState = uiState.copy(effectiveDate = it) },
                    onTaxRateChanged = { uiState = uiState.copy(taxRatePercent = it) },
                    onDefaultReminderTimeChanged = {
                        uiState =
                            uiState.copy(defaultReminderTime = it)
                    },
                    onDeclarationReminderDaysChanged = {
                        uiState =
                            uiState.copy(declarationReminderDays = it)
                    },
                    onPaymentReminderDaysChanged = {
                        uiState =
                            uiState.copy(paymentReminderDays = it)
                    },
                    onDeclarationEnabledChanged = {
                        uiState =
                            uiState.copy(declarationRemindersEnabled = it)
                    },
                    onPaymentEnabledChanged = {
                        uiState = uiState.copy(paymentRemindersEnabled = it)
                    },
                    onThemeModeChanged = { uiState = uiState.copy(themeMode = it) },
                    onRequestNotificationPermission = {},
                    onSave = { saveClicked = true }
                )
            }
        }

        composeRule.onNodeWithTag("settings-registration-id-field").performTextInput("306449082")
        composeRule.onNodeWithTag("settings-display-name-field").performTextInput("Jane Doe")
        composeRule.onNodeWithTag("settings-tax-rate-field").performTextClearance()
        composeRule.onNodeWithTag("settings-tax-rate-field").performTextInput("1.0")
        composeRule.onNodeWithTag("settings-save-button").performClick()

        assertTrue(saveClicked)
        composeRule.onNodeWithTag("settings-display-name-field").assertTextContains("Jane Doe")
    }

    @Test
    fun helpAndFaqCanBeOpenedFromSettings() {
        composeRule.setContent {
            SbsGeorgiaTheme(themeMode = ThemeMode.SYSTEM) {
                SettingsScreen(
                    innerPadding = PaddingValues(),
                    uiState = SettingsUiState(),
                    snackbarHostState = SnackbarHostState(),
                    notificationPermissionGranted = true,
                    onSave = {}
                )
            }
        }

        composeRule.onNodeWithTag("settings-open-help-button").performScrollTo().performClick()
        composeRule.onNodeWithTag("help-faq-root").assertIsDisplayed()
    }

    @Test
    fun quickStartGuideCanBeOpenedAgainFromSettings() {
        composeRule.setContent {
            SbsGeorgiaTheme(themeMode = ThemeMode.SYSTEM) {
                SettingsScreen(
                    innerPadding = PaddingValues(),
                    uiState = SettingsUiState(),
                    snackbarHostState = SnackbarHostState(),
                    notificationPermissionGranted = true,
                    onSave = {}
                )
            }
        }

        composeRule.onNodeWithTag(
            "settings-view-quick-start-button"
        ).performScrollTo().performClick()
        composeRule.onNodeWithTag("quick-start-progress").assertIsDisplayed()
    }
}
