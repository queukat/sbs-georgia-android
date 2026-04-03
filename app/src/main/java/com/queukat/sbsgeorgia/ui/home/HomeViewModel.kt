package com.queukat.sbsgeorgia.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.queukat.sbsgeorgia.domain.usecase.ObserveDashboardSummaryUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class HomeViewModel @Inject constructor(
    observeDashboardSummaryUseCase: ObserveDashboardSummaryUseCase,
) : ViewModel() {
    val uiState = observeDashboardSummaryUseCase()
        .map { HomeUiState(summary = it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())
}
