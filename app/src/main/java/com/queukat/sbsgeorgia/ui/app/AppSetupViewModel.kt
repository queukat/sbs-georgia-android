package com.queukat.sbsgeorgia.ui.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.queukat.sbsgeorgia.domain.repository.AppPreferencesRepository
import com.queukat.sbsgeorgia.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class AppSetupViewModel
@Inject
constructor(
    settingsRepository: SettingsRepository,
    private val appPreferencesRepository: AppPreferencesRepository
) : ViewModel() {
    init {
        viewModelScope.launch {
            combine(
                settingsRepository.observeTaxpayerProfile(),
                settingsRepository.observeStatusConfig(),
                appPreferencesRepository.observeQuickStartGuideState()
            ) { profile, config, guideState ->
                val needsOnboarding =
                    profile == null ||
                        config == null ||
                        profile.registrationId.isBlank() ||
                        profile.displayName.isBlank()
                if (!guideState.initialized) {
                    appPreferencesRepository.initializeQuickStartGuide(
                        hasCompletedSetup = !needsOnboarding
                    )
                }
            }.collect {}
        }
    }

    val uiState =
        combine(
            settingsRepository.observeTaxpayerProfile(),
            settingsRepository.observeStatusConfig(),
            appPreferencesRepository.observeQuickStartGuideState()
        ) { profile, config, guideState ->
            val needsOnboarding =
                profile == null ||
                    config == null ||
                    profile.registrationId.isBlank() ||
                    profile.displayName.isBlank()
            AppSetupUiState(
                needsOnboarding = needsOnboarding,
                shouldShowQuickStartGuide =
                !needsOnboarding &&
                    guideState.initialized &&
                    !guideState.dismissed
            )
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            AppSetupUiState()
        )

    fun dismissQuickStartGuide() {
        viewModelScope.launch {
            appPreferencesRepository.markQuickStartGuideDismissed()
        }
    }
}

data class AppSetupUiState(val needsOnboarding: Boolean = true, val shouldShowQuickStartGuide: Boolean = false)
