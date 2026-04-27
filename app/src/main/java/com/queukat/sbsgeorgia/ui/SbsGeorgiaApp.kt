package com.queukat.sbsgeorgia.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberDecoratedNavEntries
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.queukat.sbsgeorgia.R
import com.queukat.sbsgeorgia.startup.StartupTiming
import com.queukat.sbsgeorgia.ui.app.AppSetupViewModel
import com.queukat.sbsgeorgia.ui.app.AppThemeViewModel
import com.queukat.sbsgeorgia.ui.charts.ChartsRoute
import com.queukat.sbsgeorgia.ui.home.HomeRoute
import com.queukat.sbsgeorgia.ui.importstatement.ImportStatementRoute
import com.queukat.sbsgeorgia.ui.manualentry.ManualEntryRoute
import com.queukat.sbsgeorgia.ui.fxoverride.FxOverrideRoute
import com.queukat.sbsgeorgia.ui.monthdetails.MonthDetailRoute
import com.queukat.sbsgeorgia.ui.months.MonthsRoute
import com.queukat.sbsgeorgia.ui.payment.PaymentHelperRoute
import com.queukat.sbsgeorgia.ui.workflow.WorkflowStatusRoute
import com.queukat.sbsgeorgia.ui.navigation.ChartsDestination
import com.queukat.sbsgeorgia.ui.navigation.FxOverrideDestination
import com.queukat.sbsgeorgia.ui.navigation.HomeDestination
import com.queukat.sbsgeorgia.ui.navigation.ImportStatementDestination
import com.queukat.sbsgeorgia.ui.navigation.ManualEntryDestination
import com.queukat.sbsgeorgia.ui.navigation.MonthDetailDestination
import com.queukat.sbsgeorgia.ui.navigation.MonthsDestination
import com.queukat.sbsgeorgia.ui.navigation.PaymentHelperDestination
import com.queukat.sbsgeorgia.ui.navigation.SettingsDestination
import com.queukat.sbsgeorgia.ui.navigation.TopLevelDestination
import com.queukat.sbsgeorgia.ui.navigation.WorkflowStatusDestination
import com.queukat.sbsgeorgia.ui.navigation.rememberAppNavigationState
import com.queukat.sbsgeorgia.ui.common.BrandLaunchSplash
import com.queukat.sbsgeorgia.ui.common.sbsNavigationBarItemColors
import com.queukat.sbsgeorgia.ui.help.QuickStartGuideDialog
import com.queukat.sbsgeorgia.ui.onboarding.OnboardingRoute
import com.queukat.sbsgeorgia.ui.settings.SettingsRoute
import com.queukat.sbsgeorgia.ui.theme.SbsGeorgiaTheme
import java.time.YearMonth
import kotlinx.coroutines.delay

@Composable
fun SbsGeorgiaApp() {
    val appThemeViewModel: AppThemeViewModel = hiltViewModel()
    val appSetupViewModel: AppSetupViewModel = hiltViewModel()
    val themeMode by appThemeViewModel.themeMode.collectAsStateWithLifecycle()
    val appSetupUiState by appSetupViewModel.uiState.collectAsStateWithLifecycle()
    val navigationState = rememberAppNavigationState()
    var showBrandSplash by rememberSaveable { mutableStateOf(true) }

    SbsGeorgiaTheme(themeMode = themeMode) {
        LaunchedEffect(Unit) {
            StartupTiming.mark("SbsGeorgiaApp.themeReady")
            if (showBrandSplash) {
                delay(900)
                showBrandSplash = false
            }
        }
        LaunchedEffect(appSetupUiState.needsOnboarding) {
            StartupTiming.mark(
                if (appSetupUiState.needsOnboarding) {
                    "SbsGeorgiaApp.onboardingReady"
                } else {
                    "SbsGeorgiaApp.mainNavReady"
                },
            )
        }
        if (showBrandSplash) {
            BrandLaunchSplash()
            return@SbsGeorgiaTheme
        }
        if (appSetupUiState.needsOnboarding) {
            OnboardingRoute()
            return@SbsGeorgiaTheme
        }
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            bottomBar = {
                if (navigationState.shouldShowBottomBar) {
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surface,
                    ) {
                        TopLevelDestination.entries.forEach { destination ->
                            NavigationBarItem(
                                selected = navigationState.currentTopLevelDestination == destination,
                                onClick = { navigationState.selectTopLevel(destination) },
                                icon = {
                                    Icon(
                                        imageVector = when (destination) {
                                            TopLevelDestination.Home -> Icons.Outlined.Home
                                            TopLevelDestination.Months -> Icons.Outlined.CalendarMonth
                                            TopLevelDestination.Settings -> Icons.Outlined.Settings
                                        },
                                        contentDescription = null,
                                    )
                                },
                                label = {
                                    Text(
                                        text = when (destination) {
                                            TopLevelDestination.Home -> stringResource(R.string.nav_home)
                                            TopLevelDestination.Months -> stringResource(R.string.nav_months)
                                            TopLevelDestination.Settings -> stringResource(R.string.nav_settings)
                                        },
                                    )
                                },
                                colors = sbsNavigationBarItemColors(),
                            )
                        }
                    }
                }
            },
        ) { innerPadding ->
            val entryDecorators = listOf(
                rememberSaveableStateHolderNavEntryDecorator<NavKey>(),
                rememberViewModelStoreNavEntryDecorator(),
            )
            val appEntryProvider = entryProvider<NavKey> {
                entry<HomeDestination> {
                    HomeRoute(
                        innerPadding = innerPadding,
                        onOpenMonths = navigationState::openMonths,
                        onOpenCharts = navigationState::openCharts,
                        onAddIncome = { navigationState.openManualEntry() },
                        onImportStatement = navigationState::openImportStatement,
                        onOpenSettings = navigationState::openSettings,
                    )
                }
                entry<ChartsDestination> {
                    ChartsRoute(
                        innerPadding = innerPadding,
                        onBack = navigationState::pop,
                    )
                }
                entry<MonthsDestination> {
                    MonthsRoute(
                        innerPadding = innerPadding,
                        onMonthClick = navigationState::openMonthDetails,
                        onAddIncome = { navigationState.openManualEntry() },
                        onImportStatement = navigationState::openImportStatement,
                    )
                }
                entry<MonthDetailDestination> { destination ->
                    val yearMonth = YearMonth.parse(destination.yearMonth)
                    MonthDetailRoute(
                        innerPadding = innerPadding,
                        yearMonth = yearMonth,
                        onBack = navigationState::pop,
                        onAddIncome = { navigationState.openManualEntry(initialDate = yearMonth.atDay(1)) },
                        onEditEntry = { entryId -> navigationState.openManualEntry(entryId = entryId) },
                        onOpenPaymentHelper = navigationState::openPaymentHelper,
                        onOpenFxOverride = navigationState::openFxOverride,
                        onOpenWorkflowStatus = navigationState::openWorkflowStatus,
                    )
                }
                entry<ManualEntryDestination> { destination ->
                    ManualEntryRoute(
                        innerPadding = innerPadding,
                        entryId = destination.entryId,
                        initialDate = destination.initialDate?.let(java.time.LocalDate::parse),
                        onBack = navigationState::pop,
                    )
                }
                entry<ImportStatementDestination> {
                    ImportStatementRoute(
                        innerPadding = innerPadding,
                        onBack = navigationState::pop,
                    )
                }
                entry<PaymentHelperDestination> { destination ->
                    PaymentHelperRoute(
                        innerPadding = innerPadding,
                        yearMonth = YearMonth.parse(destination.yearMonth),
                        onBack = navigationState::pop,
                    )
                }
                entry<FxOverrideDestination> { destination ->
                    FxOverrideRoute(
                        innerPadding = innerPadding,
                        entryId = destination.entryId,
                        onBack = navigationState::pop,
                    )
                }
                entry<WorkflowStatusDestination> { destination ->
                    WorkflowStatusRoute(
                        innerPadding = innerPadding,
                        yearMonth = YearMonth.parse(destination.yearMonth),
                        onBack = navigationState::pop,
                    )
                }
                entry<SettingsDestination> {
                    SettingsRoute(innerPadding = innerPadding)
                }
            }
            val homeEntries = rememberDecoratedNavEntries(
                backStack = navigationState.homeBackStack,
                entryDecorators = entryDecorators,
                entryProvider = appEntryProvider,
            )
            val monthsEntries = rememberDecoratedNavEntries(
                backStack = navigationState.monthsBackStack,
                entryDecorators = entryDecorators,
                entryProvider = appEntryProvider,
            )
            val settingsEntries = rememberDecoratedNavEntries(
                backStack = navigationState.settingsBackStack,
                entryDecorators = entryDecorators,
                entryProvider = appEntryProvider,
            )

            val currentEntries = when (navigationState.currentTopLevelDestination) {
                TopLevelDestination.Home -> homeEntries
                TopLevelDestination.Months -> monthsEntries
                TopLevelDestination.Settings -> settingsEntries
            }

            NavDisplay(
                entries = currentEntries,
                onBack = navigationState::pop,
            )
        }
        if (appSetupUiState.shouldShowQuickStartGuide) {
            QuickStartGuideDialog(
                onDismiss = appSetupViewModel::dismissQuickStartGuide,
            )
        }
    }
}
