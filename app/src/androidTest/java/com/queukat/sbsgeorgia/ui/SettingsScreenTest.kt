package com.queukat.sbsgeorgia.ui

import android.content.Context
import android.content.res.Configuration
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.queukat.sbsgeorgia.domain.model.DeclarationFormField
import com.queukat.sbsgeorgia.domain.model.ThemeMode
import com.queukat.sbsgeorgia.ui.help.HelpFaqDialog
import com.queukat.sbsgeorgia.ui.help.QuickStartGuideDialog
import com.queukat.sbsgeorgia.ui.settings.SettingsScreen
import com.queukat.sbsgeorgia.ui.settings.SettingsUiState
import com.queukat.sbsgeorgia.ui.theme.SbsGeorgiaTheme
import java.time.LocalDate
import java.util.Locale
import org.junit.Assert.assertEquals
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

        composeRule.onNodeWithTag("settings-registration-id-field").performTextInput("123456789")
        composeRule.onNodeWithTag("settings-display-name-field").performTextInput("Jane Doe")
        composeRule.onNodeWithTag("settings-tax-rate-field").performTextClearance()
        composeRule.onNodeWithTag("settings-tax-rate-field").performTextInput("1.0")
        composeRule.onNodeWithTag("settings-save-button").performClick()

        assertTrue(saveClicked)
        composeRule.onNodeWithTag("settings-display-name-field").assertTextContains("Jane Doe")
    }

    @Test
    fun declarationFieldSettingsUseDefaultFieldsAndAllowPosSelection() {
        var uiState by mutableStateOf(SettingsUiState())
        var selectedMonthlyField: DeclarationFormField? = null

        composeRule.setContent {
            SbsGeorgiaTheme(themeMode = ThemeMode.SYSTEM) {
                SettingsScreen(
                    innerPadding = PaddingValues(),
                    uiState = uiState,
                    snackbarHostState = SnackbarHostState(),
                    notificationPermissionGranted = true,
                    onIncludeCumulativeIncomeChanged = {
                        uiState = uiState.copy(includeCumulativeIncomeField = it)
                    },
                    onIncludeMonthlyIncomeChanged = {
                        uiState = uiState.copy(includeMonthlyIncomeField = it)
                    },
                    onMonthlyIncomeFieldChanged = {
                        selectedMonthlyField = it
                        uiState = uiState.copy(monthlyIncomeField = it)
                    },
                    onSave = {}
                )
            }
        }

        composeRule.onNodeWithTag("settings-declaration-field-15-switch").assertIsOn()
        composeRule.onNodeWithTag("settings-declaration-field-20-option")
            .performScrollTo()
            .assertIsDisplayed()

        composeRule.onNodeWithTag("settings-declaration-field-19-option")
            .performScrollTo()
            .performClick()

        assertEquals(DeclarationFormField.MONTHLY_POS_INCOME, selectedMonthlyField)

        composeRule.onNodeWithTag("settings-declaration-monthly-income-switch")
            .performScrollTo()
            .performClick()

        composeRule.onAllNodesWithTag("settings-declaration-field-18-option").assertCountEquals(0)
        composeRule.onAllNodesWithTag("settings-declaration-field-19-option").assertCountEquals(0)
        composeRule.onAllNodesWithTag("settings-declaration-field-20-option").assertCountEquals(0)
        composeRule.onAllNodesWithTag("settings-declaration-field-21-option").assertCountEquals(0)
    }

    @Test
    fun helpFaqDialogIsDisplayed() {
        composeRule.setContent {
            SbsGeorgiaTheme(themeMode = ThemeMode.SYSTEM) {
                HelpFaqDialog(
                    onDismiss = {},
                    onSendFeedback = {}
                )
            }
        }

        composeRule.onNodeWithTag("help-faq-root").assertIsDisplayed()
    }

    @Test
    fun russianHelpUsesLocalizedVisibleCtaAndStatusTerms() {
        val russianContext = localizedContext(Locale.forLanguageTag("ru"))

        composeRule.setContent {
            CompositionLocalProvider(
                LocalContext provides russianContext,
                LocalConfiguration provides russianContext.resources.configuration
            ) {
                SbsGeorgiaTheme(themeMode = ThemeMode.SYSTEM) {
                    HelpFaqDialog(
                        onDismiss = {},
                        onSendFeedback = {}
                    )
                }
            }
        }

        composeRule
            .onNodeWithText("Добавить доход", substring = true)
            .performScrollTo()
            .assertIsDisplayed()
        composeRule
            .onNodeWithText("Импорт выписки TBC", substring = true)
            .performScrollTo()
            .assertIsDisplayed()
        composeRule
            .onNodeWithText("Настройки", substring = true)
            .performScrollTo()
            .assertIsDisplayed()
        composeRule
            .onNodeWithText("черновика", substring = true)
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun quickStartGuideDialogIsDisplayed() {
        composeRule.setContent {
            SbsGeorgiaTheme(themeMode = ThemeMode.SYSTEM) {
                QuickStartGuideDialog(onDismiss = {})
            }
        }

        composeRule.onNodeWithTag("quick-start-progress").assertIsDisplayed()
    }

    private fun localizedContext(locale: Locale): Context {
        val appContext = ApplicationProvider.getApplicationContext<Context>()
        val configuration = Configuration(appContext.resources.configuration).apply {
            setLocale(locale)
        }
        return appContext.createConfigurationContext(configuration)
    }
}
