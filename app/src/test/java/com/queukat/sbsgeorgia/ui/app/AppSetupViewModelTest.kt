package com.queukat.sbsgeorgia.ui.app

import com.queukat.sbsgeorgia.domain.model.DeclarationFormConfig
import com.queukat.sbsgeorgia.domain.model.ReminderConfig
import com.queukat.sbsgeorgia.domain.model.SmallBusinessStatusConfig
import com.queukat.sbsgeorgia.domain.model.TaxpayerProfile
import com.queukat.sbsgeorgia.domain.model.ThemeMode
import com.queukat.sbsgeorgia.domain.repository.AppPreferencesRepository
import com.queukat.sbsgeorgia.domain.repository.QuickStartGuideState
import com.queukat.sbsgeorgia.domain.repository.SettingsRepository
import com.queukat.sbsgeorgia.testing.MainDispatcherRule
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AppSetupViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun existingConfiguredUserDoesNotSeeQuickStartGuide() = runTest {
        val settingsRepository =
            SetupFakeSettingsRepository(
                initialProfile =
                TaxpayerProfile(
                    registrationId = "123456789",
                    displayName = "Test Entrepreneur"
                ),
                initialStatusConfig =
                SmallBusinessStatusConfig(
                    effectiveDate = LocalDate.of(2026, 1, 1),
                    defaultTaxRatePercent = BigDecimal("1.0")
                )
            )
        val preferencesRepository = FakeAppPreferencesRepository()
        val viewModel = AppSetupViewModel(settingsRepository, preferencesRepository)

        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.initialized)
        assertFalse(viewModel.uiState.value.needsOnboarding)
        assertFalse(viewModel.uiState.value.shouldShowQuickStartGuide)
        assertEquals(
            QuickStartGuideState(
                initialized = true,
                dismissed = true
            ),
            preferencesRepository.quickStartGuideState.value
        )
    }

    @Test
    fun newUserSeesQuickStartGuideAfterCompletingSetupAndCanDismissIt() = runTest {
        val settingsRepository = SetupFakeSettingsRepository()
        val preferencesRepository = FakeAppPreferencesRepository()
        val viewModel = AppSetupViewModel(settingsRepository, preferencesRepository)

        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.initialized)
        assertTrue(viewModel.uiState.value.needsOnboarding)
        assertFalse(viewModel.uiState.value.shouldShowQuickStartGuide)
        assertEquals(
            QuickStartGuideState(
                initialized = true,
                dismissed = false
            ),
            preferencesRepository.quickStartGuideState.value
        )

        settingsRepository.upsertSettings(
            TaxpayerProfile(
                registrationId = "123456789",
                displayName = "Test Entrepreneur"
            ),
            SmallBusinessStatusConfig(
                effectiveDate = LocalDate.of(2026, 1, 1),
                defaultTaxRatePercent = BigDecimal("1.0")
            ),
            requireNotNull(settingsRepository.observeReminderConfig().first())
        )
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.needsOnboarding)
        assertTrue(viewModel.uiState.value.shouldShowQuickStartGuide)

        viewModel.dismissQuickStartGuide()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.shouldShowQuickStartGuide)
        assertTrue(preferencesRepository.quickStartGuideState.value.dismissed)
    }

    @Test
    fun incompleteRestoredProfileKeepsOnboardingActive() = runTest {
        val settingsRepository =
            SetupFakeSettingsRepository(
                initialProfile = TaxpayerProfile(registrationId = "test-registration", displayName = " "),
                initialStatusConfig =
                SmallBusinessStatusConfig(
                    effectiveDate = LocalDate.of(2026, 1, 1),
                    defaultTaxRatePercent = BigDecimal("1.0")
                )
            )
        val preferencesRepository = FakeAppPreferencesRepository()
        val viewModel = AppSetupViewModel(settingsRepository, preferencesRepository)

        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.needsOnboarding)
        assertFalse(viewModel.uiState.value.shouldShowQuickStartGuide)
        assertEquals(
            QuickStartGuideState(initialized = true, dismissed = false),
            preferencesRepository.quickStartGuideState.value
        )
    }
}

private class FakeAppPreferencesRepository : AppPreferencesRepository {
    val quickStartGuideState = MutableStateFlow(QuickStartGuideState())

    override fun observeQuickStartGuideState(): Flow<QuickStartGuideState> = quickStartGuideState

    override suspend fun initializeQuickStartGuide(hasCompletedSetup: Boolean) {
        if (quickStartGuideState.value.initialized) return
        quickStartGuideState.value =
            QuickStartGuideState(
                initialized = true,
                dismissed = hasCompletedSetup
            )
    }

    override suspend fun markQuickStartGuideDismissed() {
        quickStartGuideState.value =
            quickStartGuideState.value.copy(
                initialized = true,
                dismissed = true
            )
    }
}

private class SetupFakeSettingsRepository(
    initialProfile: TaxpayerProfile? = null,
    initialStatusConfig: SmallBusinessStatusConfig? = null,
    initialReminderConfig: ReminderConfig? =
        ReminderConfig(
            declarationReminderDays = listOf(10, 13, 15),
            paymentReminderDays = listOf(10, 13, 15),
            declarationRemindersEnabled = true,
            paymentRemindersEnabled = true,
            defaultReminderTime = LocalTime.of(9, 0),
            themeMode = ThemeMode.SYSTEM
        )
) : SettingsRepository {
    private val taxpayerProfile = MutableStateFlow(initialProfile)
    private val statusConfig = MutableStateFlow(initialStatusConfig)
    private val reminderConfig = MutableStateFlow(initialReminderConfig)

    override fun observeTaxpayerProfile(): Flow<TaxpayerProfile?> = taxpayerProfile

    override fun observeStatusConfig(): Flow<SmallBusinessStatusConfig?> = statusConfig

    override fun observeReminderConfig(): Flow<ReminderConfig?> = reminderConfig

    override suspend fun upsertReminderConfig(config: ReminderConfig) {
        reminderConfig.value = config
    }

    override suspend fun upsertSettings(
        profile: TaxpayerProfile,
        config: SmallBusinessStatusConfig,
        reminders: ReminderConfig,
        formConfig: DeclarationFormConfig?
    ) {
        taxpayerProfile.value = profile
        statusConfig.value = config
        upsertReminderConfig(reminders)
    }
}
