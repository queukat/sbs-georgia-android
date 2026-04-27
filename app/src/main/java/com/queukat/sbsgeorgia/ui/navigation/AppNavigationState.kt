package com.queukat.sbsgeorgia.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberNavBackStack
import java.time.LocalDate
import java.time.YearMonth

interface AppNavigator {
    val currentTopLevelDestination: TopLevelDestination
    val shouldShowBottomBar: Boolean
    fun selectTopLevel(destination: TopLevelDestination)
    fun openMonths()
    fun openSettings()
    fun openCharts()
    fun openMonthDetails(yearMonth: YearMonth)
    fun openManualEntry(entryId: Long? = null, initialDate: LocalDate? = null)
    fun openImportStatement()
    fun openPaymentHelper(yearMonth: YearMonth)
    fun openFxOverride(entryId: Long)
    fun openWorkflowStatus(yearMonth: YearMonth)
    fun pop()
}

@Stable
class AppNavigationState internal constructor(
    internal val homeBackStack: MutableList<NavKey>,
    internal val monthsBackStack: MutableList<NavKey>,
    internal val settingsBackStack: MutableList<NavKey>,
    initialTopLevelDestination: TopLevelDestination = TopLevelDestination.Home,
) : AppNavigator {
    override var currentTopLevelDestination: TopLevelDestination by mutableStateOf(initialTopLevelDestination)
        private set

    val currentBackStack: MutableList<NavKey>
        get() = backStackFor(currentTopLevelDestination)

    override val shouldShowBottomBar: Boolean
        get() = currentBackStack.lastOrNull() == currentTopLevelDestination.root

    override fun selectTopLevel(destination: TopLevelDestination) {
        currentTopLevelDestination = destination
        ensureRoot(destination)
    }

    override fun openMonths() {
        selectTopLevel(TopLevelDestination.Months)
    }

    override fun openSettings() {
        selectTopLevel(TopLevelDestination.Settings)
    }

    override fun openCharts() {
        if (homeBackStack.lastOrNull() != ChartsDestination) {
            homeBackStack.add(ChartsDestination)
        }
        currentTopLevelDestination = TopLevelDestination.Home
    }

    override fun openMonthDetails(yearMonth: YearMonth) {
        selectTopLevel(TopLevelDestination.Months)
        monthsBackStack.add(MonthDetailDestination(yearMonth = yearMonth.toString()))
    }

    override fun openManualEntry(entryId: Long?, initialDate: LocalDate?) {
        currentBackStack.add(
            ManualEntryDestination(
                entryId = entryId,
                initialDate = initialDate?.toString(),
            ),
        )
    }

    override fun openImportStatement() {
        currentBackStack.add(ImportStatementDestination)
    }

    override fun openPaymentHelper(yearMonth: YearMonth) {
        currentBackStack.add(PaymentHelperDestination(yearMonth = yearMonth.toString()))
    }

    override fun openFxOverride(entryId: Long) {
        currentBackStack.add(FxOverrideDestination(entryId = entryId))
    }

    override fun openWorkflowStatus(yearMonth: YearMonth) {
        currentBackStack.add(WorkflowStatusDestination(yearMonth = yearMonth.toString()))
    }

    override fun pop() {
        if (currentBackStack.size > 1) {
            currentBackStack.removeAt(currentBackStack.lastIndex)
        }
    }

    private fun ensureRoot(destination: TopLevelDestination) {
        val backStack = backStackFor(destination)
        if (backStack.isEmpty()) {
            backStack.add(destination.root)
        }
    }

    private fun backStackFor(destination: TopLevelDestination): MutableList<NavKey> = when (destination) {
        TopLevelDestination.Home -> homeBackStack
        TopLevelDestination.Months -> monthsBackStack
        TopLevelDestination.Settings -> settingsBackStack
    }
}

@Composable
fun rememberAppNavigationState(): AppNavigationState {
    val homeBackStack = rememberNavBackStack(HomeDestination)
    val monthsBackStack = rememberNavBackStack(MonthsDestination)
    val settingsBackStack = rememberNavBackStack(SettingsDestination)
    return remember(homeBackStack, monthsBackStack, settingsBackStack) {
        AppNavigationState(
            homeBackStack = homeBackStack,
            monthsBackStack = monthsBackStack,
            settingsBackStack = settingsBackStack,
        )
    }
}
