package com.queukat.sbsgeorgia.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.queukat.sbsgeorgia.domain.model.ExtractionConfidence
import com.queukat.sbsgeorgia.domain.model.OnboardingDocumentType
import com.queukat.sbsgeorgia.domain.model.OnboardingImportPreview
import com.queukat.sbsgeorgia.domain.model.ParsedDateField
import com.queukat.sbsgeorgia.domain.model.ParsedTextField
import com.queukat.sbsgeorgia.domain.model.ThemeMode
import com.queukat.sbsgeorgia.ui.onboarding.OnboardingScreen
import com.queukat.sbsgeorgia.ui.onboarding.OnboardingUiState
import com.queukat.sbsgeorgia.ui.theme.SbsGeorgiaTheme
import java.time.LocalDate
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OnboardingScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Before
    fun skipOnTvDevices() {
        assumePhoneLikeComposeTestDevice()
    }

    @Test
    fun firstStepShowsSetupChoicesWithoutProfileForm() {
        composeRule.setContent {
            SbsGeorgiaTheme(themeMode = ThemeMode.SYSTEM) {
                TestOnboardingScreen(uiState = OnboardingUiState())
            }
        }

        composeRule.onNodeWithTag("onboarding-step-method").assertIsDisplayed()
        composeRule.onNodeWithTag("onboarding-method-import-button").assertIsDisplayed()
        composeRule.onNodeWithTag("onboarding-method-restore-button").assertIsDisplayed()
        composeRule.onNodeWithTag("onboarding-method-manual-button").assertIsDisplayed()
        composeRule.onAllNodesWithTag("onboarding-display-name-field").assertCountEquals(0)
        composeRule.onNodeWithTag("onboarding-primary-action-button").assertIsNotEnabled()
    }

    @Test
    fun manualSetupContinuesToRequiredFieldsAndFinishes() {
        var completeClicked = false

        composeRule.setContent {
            SbsGeorgiaTheme(themeMode = ThemeMode.SYSTEM) {
                TestOnboardingScreen(
                    uiState =
                    OnboardingUiState(
                        displayName = "Jane Doe",
                        registrationId = "123456789",
                        effectiveDate = LocalDate.of(2026, 3, 7),
                        taxRatePercent = "1.0"
                    ),
                    onComplete = { completeClicked = true }
                )
            }
        }

        composeRule.onNodeWithTag("onboarding-method-manual-button").performClick().assertIsSelected()
        composeRule.onNodeWithTag("onboarding-primary-action-button").performClick()

        composeRule.onNodeWithTag("onboarding-step-details").assertIsDisplayed()
        composeRule.onNodeWithTag("onboarding-display-name-field").assertIsDisplayed()

        composeRule.onNodeWithTag("onboarding-primary-action-button").performClick()
        composeRule.runOnIdle {
            assertTrue(completeClicked)
        }
    }

    @Test
    fun importPreviewStartsOnReviewAndContinueAppliesFields() {
        var applyClicked = false

        composeRule.setContent {
            SbsGeorgiaTheme(themeMode = ThemeMode.SYSTEM) {
                TestOnboardingScreen(
                    uiState = OnboardingUiState(preview = samplePreview()),
                    onApplyPreview = { applyClicked = true }
                )
            }
        }

        composeRule.onNodeWithTag("onboarding-step-review").assertIsDisplayed()
        composeRule.onNodeWithText("registry-extract.pdf").assertIsDisplayed()

        composeRule.onNodeWithTag("onboarding-primary-action-button").performClick()

        composeRule.onNodeWithTag("onboarding-step-details").assertIsDisplayed()
        composeRule.runOnIdle {
            assertTrue(applyClicked)
        }
    }

    @Test
    fun fieldErrorsAppearNearFieldsAndInBottomSummary() {
        val error = "Taxpayer display name is required."

        composeRule.setContent {
            SbsGeorgiaTheme(themeMode = ThemeMode.SYSTEM) {
                TestOnboardingScreen(
                    uiState =
                    OnboardingUiState(
                        errorMessage = error,
                        displayNameError = error
                    )
                )
            }
        }

        composeRule.onNodeWithTag("onboarding-error-summary").assertIsDisplayed()
        composeRule.onNodeWithTag("onboarding-method-manual-button").performClick()
        composeRule.onNodeWithTag("onboarding-primary-action-button").performClick()
        composeRule.onAllNodesWithText(error).assertCountEquals(2)
    }

    @Composable
    private fun TestOnboardingScreen(
        uiState: OnboardingUiState,
        onApplyPreview: () -> Unit = {},
        onComplete: () -> Unit = {}
    ) {
        OnboardingScreen(
            innerPadding = PaddingValues(),
            uiState = uiState,
            onImportRegistryExtract = {},
            onImportCertificate = {},
            onRestoreBackup = {},
            onApplyPreview = onApplyPreview,
            onDisplayNameChanged = {},
            onLegalFormChanged = {},
            onRegistrationIdChanged = {},
            onRegistrationDateChanged = {},
            onLegalAddressChanged = {},
            onActivityTypeChanged = {},
            onCertificateNumberChanged = {},
            onCertificateIssuedDateChanged = {},
            onEffectiveDateChanged = {},
            onTaxRatePercentChanged = {},
            onComplete = onComplete
        )
    }

    private fun samplePreview(): OnboardingImportPreview = OnboardingImportPreview(
        sourceFileName = "registry-extract.pdf",
        sourceFingerprint = "registry-demo",
        documentType = OnboardingDocumentType.REGISTRY_EXTRACT,
        displayName =
        ParsedTextField(
            value = "Jane Doe",
            confidence = ExtractionConfidence.CONFIDENT
        ),
        registrationId =
        ParsedTextField(
            value = "123456789",
            confidence = ExtractionConfidence.CONFIDENT
        ),
        registrationDate =
        ParsedDateField(
            value = LocalDate.of(2026, 3, 7),
            confidence = ExtractionConfidence.CONFIDENT
        )
    )
}
