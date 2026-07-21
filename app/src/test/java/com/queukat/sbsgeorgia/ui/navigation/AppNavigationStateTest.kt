package com.queukat.sbsgeorgia.ui.navigation

import androidx.compose.runtime.mutableStateListOf
import androidx.navigation3.runtime.NavKey
import java.time.LocalDate
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

    @Test
    fun openMonthsReturnsToRootAfterImportOpenedFromMonths() {
        val navigationState = createNavigationState()

        navigationState.selectTopLevel(TopLevelDestination.Months)
        navigationState.openImportStatement()
        navigationState.openMonths()

        assertEquals(TopLevelDestination.Months, navigationState.currentTopLevelDestination)
        assertEquals(MonthsDestination, navigationState.currentBackStack.single())
        assertTrue(navigationState.shouldShowBottomBar)
    }

    @Test
    fun selectingTopLevelDestinationPreservesThatDestinationNestedStack() {
        val navigationState = createNavigationState()

        navigationState.openCharts()
        navigationState.selectTopLevel(TopLevelDestination.Settings)
        navigationState.selectTopLevel(TopLevelDestination.Home)

        assertEquals(TopLevelDestination.Home, navigationState.currentTopLevelDestination)
        assertEquals(listOf(HomeDestination, ChartsDestination), navigationState.currentBackStack)
        assertFalse(navigationState.shouldShowBottomBar)
    }

    @Test
    fun navigationCommandsRouteToExpectedDestinationWithArguments() {
        val navigationState = createNavigationState()
        val incomeMonth = YearMonth.of(2026, 3)

        navigationState.openMonthDetails(incomeMonth)
        navigationState.openManualEntry(entryId = 17, initialDate = LocalDate.of(2026, 3, 4))
        navigationState.openFxOverride(entryId = 17)
        navigationState.openWorkflowStatus(incomeMonth)
        navigationState.openPaymentHelper(incomeMonth)

        assertEquals(TopLevelDestination.Months, navigationState.currentTopLevelDestination)
        assertEquals(
            listOf(
                MonthsDestination,
                MonthDetailDestination("2026-03"),
                ManualEntryDestination(entryId = 17, initialDate = "2026-03-04"),
                FxOverrideDestination(entryId = 17),
                WorkflowStatusDestination(yearMonth = "2026-03"),
                PaymentHelperDestination(yearMonth = "2026-03")
            ),
            navigationState.currentBackStack
        )
        assertFalse(navigationState.shouldShowBottomBar)
    }

    @Test
    fun importStatementRoutesWithinCurrentTopLevelAndPopRevealsPreviousDestination() {
        val navigationState = createNavigationState()

        navigationState.selectTopLevel(TopLevelDestination.Settings)
        navigationState.openImportStatement()
        navigationState.pop()

        assertEquals(TopLevelDestination.Settings, navigationState.currentTopLevelDestination)
        assertEquals(SettingsDestination, navigationState.currentBackStack.single())
        assertTrue(navigationState.shouldShowBottomBar)
    }

    private fun createNavigationState(): AppNavigationState = AppNavigationState(
        homeBackStack = mutableStateListOf<NavKey>(HomeDestination),
        monthsBackStack = mutableStateListOf<NavKey>(MonthsDestination),
        settingsBackStack = mutableStateListOf<NavKey>(SettingsDestination)
    )
}
