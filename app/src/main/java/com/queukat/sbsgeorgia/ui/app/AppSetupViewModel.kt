package com.queukat.sbsgeorgia.ui.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.queukat.sbsgeorgia.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class AppSetupViewModel @Inject constructor(
    settingsRepository: SettingsRepository,
) : ViewModel() {
    val uiState = combine(
        settingsRepository.observeTaxpayerProfile(),
        settingsRepository.observeStatusConfig(),
    ) { profile, config ->
        AppSetupUiState(
            needsOnboarding = profile == null ||
                config == null ||
                profile.registrationId.isBlank() ||
                profile.displayName.isBlank(),
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        AppSetupUiState(),
    )
}

data class AppSetupUiState(
    val needsOnboarding: Boolean = true,
)
