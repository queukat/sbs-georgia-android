package com.queukat.sbsgeorgia.ui.navigation

import androidx.compose.runtime.mutableStateListOf
import androidx.navigation3.runtime.NavKey
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppNavigationStateTest {
    @Test
    fun preservesNestedBackStacksAcrossTopLevelSwitches() {
        val navigationState = createNavigationState()

        navigationState.openMonthDetails(YearMonth.of(2026, 3))
        navigationState.selectTopLevel(TopLevelDestination.Settings)
        navigationState.selectTopLevel(TopLevelDestination.Months)

        assertEquals(TopLevelDestination.Months, navigationState.currentTopLevelDestination)
        assertEquals(MonthDetailDestination("2026-03"), navigationState.currentBackStack.last())
        assertFalse(navigationState.shouldShowBottomBar)
    }

    @Test
    fun popStopsAtTopLevelRoot() {
        val navigationState = createNavigationState()

        navigationState.openManualEntry()
        navigationState.pop()
        navigationState.pop()

        assertEquals(HomeDestination, navigationState.currentBackStack.single())
        assertTrue(navigationState.shouldShowBottomBar)
    }

    @Test
    fun openSettingsSwitchesToSettingsTopLevel() {
        val navigationState = createNavigationState()

        navigationState.openSettings()

        assertEquals(TopLevelDestination.Settings, navigationState.currentTopLevelDestination)
        assertEquals(SettingsDestination, navigationState.currentBackStack.single())
    }

    private fun createNavigationState(): AppNavigationState = AppNavigationState(
        homeBackStack = mutableStateListOf<NavKey>(HomeDestination),
        monthsBackStack = mutableStateListOf<NavKey>(MonthsDestination),
        settingsBackStack = mutableStateListOf<NavKey>(SettingsDestination),
    )
}
