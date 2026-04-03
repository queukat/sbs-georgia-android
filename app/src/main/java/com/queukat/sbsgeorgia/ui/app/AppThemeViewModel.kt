package com.queukat.sbsgeorgia.ui.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.queukat.sbsgeorgia.domain.model.ThemeMode
import com.queukat.sbsgeorgia.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class AppThemeViewModel @Inject constructor(
    settingsRepository: SettingsRepository,
) : ViewModel() {
    val themeMode: StateFlow<ThemeMode> = settingsRepository.observeReminderConfig()
        .map { it?.themeMode ?: ThemeMode.SYSTEM }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ThemeMode.SYSTEM)
}
