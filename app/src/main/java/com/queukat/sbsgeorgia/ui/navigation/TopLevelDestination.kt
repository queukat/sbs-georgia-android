package com.queukat.sbsgeorgia.ui.navigation

enum class TopLevelDestination(val root: AppDestination) {
    Home(
        root = HomeDestination
    ),
    Months(
        root = MonthsDestination
    ),
    Settings(
        root = SettingsDestination
    )
}
