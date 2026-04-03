package com.queukat.sbsgeorgia.ui.app

import com.queukat.sbsgeorgia.domain.model.ReminderConfig
import com.queukat.sbsgeorgia.domain.model.SmallBusinessStatusConfig
import com.queukat.sbsgeorgia.domain.model.TaxpayerProfile
import com.queukat.sbsgeorgia.domain.model.ThemeMode
import com.queukat.sbsgeorgia.domain.repository.SettingsRepository
import com.queukat.sbsgeorgia.testing.MainDispatcherRule
import java.time.LocalTime
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AppThemeViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun defaultsToSystemThemeWithoutPersistedConfig() = runTest {
        val repository = FakeSettingsRepository()
        val viewModel = AppThemeViewModel(repository)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.themeMode.collect {}
        }
        advanceUntilIdle()

        assertEquals(ThemeMode.SYSTEM, viewModel.themeMode.value)
    }

    @Test
    fun reflectsPersistedThemeChangesIncludingRestore() = runTest {
        val repository = FakeSettingsRepository()
        val viewModel = AppThemeViewModel(repository)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.themeMode.collect {}
        }

        repository.upsertReminderConfig(
            ReminderConfig(
                declarationReminderDays = listOf(10, 13, 15),
                paymentReminderDays = listOf(10, 13, 15),
                declarationRemindersEnabled = true,
                paymentRemindersEnabled = true,
                defaultReminderTime = LocalTime.of(9, 0),
                themeMode = ThemeMode.DARK,
            ),
        )
        advanceUntilIdle()
        assertEquals(ThemeMode.DARK, viewModel.themeMode.value)

        repository.upsertReminderConfig(
            ReminderConfig(
                declarationReminderDays = listOf(10, 13, 15),
                paymentReminderDays = listOf(10, 13, 15),
                declarationRemindersEnabled = true,
                paymentRemindersEnabled = true,
                defaultReminderTime = LocalTime.of(9, 0),
                themeMode = ThemeMode.LIGHT,
            ),
        )
        advanceUntilIdle()
        assertEquals(ThemeMode.LIGHT, viewModel.themeMode.value)
    }
}

private class FakeSettingsRepository : SettingsRepository {
    private val taxpayerProfile = MutableStateFlow<TaxpayerProfile?>(null)
    private val statusConfig = MutableStateFlow<SmallBusinessStatusConfig?>(null)
    private val reminderConfig = MutableStateFlow<ReminderConfig?>(null)

    override fun observeTaxpayerProfile(): Flow<TaxpayerProfile?> = taxpayerProfile

    override fun observeStatusConfig(): Flow<SmallBusinessStatusConfig?> = statusConfig

    override fun observeReminderConfig(): Flow<ReminderConfig?> = reminderConfig

    override suspend fun upsertTaxpayerProfile(profile: TaxpayerProfile) {
        taxpayerProfile.value = profile
    }

    override suspend fun upsertStatusConfig(config: SmallBusinessStatusConfig) {
        statusConfig.value = config
    }

    override suspend fun upsertReminderConfig(config: ReminderConfig) {
        reminderConfig.value = config
    }
}
