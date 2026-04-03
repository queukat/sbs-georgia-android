package com.queukat.sbsgeorgia.ui.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

sealed interface AppDestination : NavKey

@Serializable
data object HomeDestination : AppDestination

@Serializable
data object MonthsDestination : AppDestination

@Serializable
data object SettingsDestination : AppDestination

@Serializable
data object ChartsDestination : AppDestination

@Serializable
data class MonthDetailDestination(
    val yearMonth: String,
) : AppDestination

@Serializable
data class ManualEntryDestination(
    val entryId: Long? = null,
) : AppDestination

@Serializable
data class PaymentHelperDestination(
    val yearMonth: String,
) : AppDestination

@Serializable
data class FxOverrideDestination(
    val entryId: Long,
) : AppDestination

@Serializable
data class WorkflowStatusDestination(
    val yearMonth: String,
) : AppDestination

@Serializable
data object ImportStatementDestination : AppDestination
