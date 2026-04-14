package com.queukat.sbsgeorgia.data.repository

import android.content.SharedPreferences
import com.queukat.sbsgeorgia.domain.repository.AppPreferencesRepository
import com.queukat.sbsgeorgia.domain.repository.QuickStartGuideState
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

@Singleton
class AppPreferencesRepositoryImpl @Inject constructor(
    private val sharedPreferences: SharedPreferences,
) : AppPreferencesRepository {
    private val quickStartGuideState = MutableStateFlow(readQuickStartGuideState())

    override fun observeQuickStartGuideState(): Flow<QuickStartGuideState> = quickStartGuideState

    override suspend fun initializeQuickStartGuide(hasCompletedSetup: Boolean) {
        if (quickStartGuideState.value.initialized) return
        updateQuickStartGuideState(
            QuickStartGuideState(
                initialized = true,
                dismissed = hasCompletedSetup,
            ),
        )
    }

    override suspend fun markQuickStartGuideDismissed() {
        if (quickStartGuideState.value.dismissed && quickStartGuideState.value.initialized) return
        updateQuickStartGuideState(
            QuickStartGuideState(
                initialized = true,
                dismissed = true,
            ),
        )
    }

    private fun updateQuickStartGuideState(state: QuickStartGuideState) {
        sharedPreferences.edit()
            .putBoolean(KEY_QUICK_START_INITIALIZED, state.initialized)
            .putBoolean(KEY_QUICK_START_DISMISSED, state.dismissed)
            .apply()
        quickStartGuideState.value = state
    }

    private fun readQuickStartGuideState(): QuickStartGuideState = QuickStartGuideState(
        initialized = sharedPreferences.getBoolean(KEY_QUICK_START_INITIALIZED, false),
        dismissed = sharedPreferences.getBoolean(KEY_QUICK_START_DISMISSED, false),
    )

    private companion object {
        const val KEY_QUICK_START_INITIALIZED = "quick_start_guide_initialized"
        const val KEY_QUICK_START_DISMISSED = "quick_start_guide_dismissed"
    }
}
