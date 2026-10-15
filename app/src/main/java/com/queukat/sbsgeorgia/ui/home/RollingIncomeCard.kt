package com.queukat.sbsgeorgia.ui.home

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.queukat.sbsgeorgia.R
import com.queukat.sbsgeorgia.domain.model.RollingIncomeSummary
import com.queukat.sbsgeorgia.ui.common.AppSection
import com.queukat.sbsgeorgia.ui.common.DecimalField
import com.queukat.sbsgeorgia.ui.common.KeyValueRow
import com.queukat.sbsgeorgia.ui.common.formatAmount
import com.queukat.sbsgeorgia.ui.common.formatIsoDate
import com.queukat.sbsgeorgia.ui.common.formatMonthYear

@Composable
fun RollingIncomeCard(summary: RollingIncomeSummary) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val defaultEstimate = summary.monthlyMedianGel?.toPlainString().orEmpty()
    var estimate by rememberSaveable(defaultEstimate, summary.asOf.toString()) { mutableStateOf(defaultEstimate) }
    val expectedIncome = estimate.trim().replace(',', '.').toBigDecimalOrNull()?.takeIf { it.signum() >= 0 }
    val projection = expectedIncome?.let(summary::projectNextMonth)
    val uriHandler = LocalUriHandler.current
    val firstMonth = summary.months.first().month
    val currentMonth = summary.months.last().month
    AppSection(title = stringResource(R.string.rolling_title)) {
        Text(
            formatAmount(summary.knownTotalGel, "GEL"),
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.testTag("rolling-income-total")
        )
        Text(stringResource(R.string.rolling_period, firstMonth.atDay(1).formatIsoDate(), summary.asOf.formatIsoDate()))
        val warning = when {
            summary.thresholdExceeded -> R.string.rolling_exceeded
            projection != null && projection > RollingIncomeSummary.REGISTRATION_THRESHOLD ->
                R.string.rolling_forecast_exceeded
            summary.warningNeeded -> R.string.rolling_near
            projection != null && projection >= RollingIncomeSummary.WARNING_LEVEL -> R.string.rolling_forecast_near
            else -> null
        }
        warning?.let {
            Text(
                stringResource(it),
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.testTag("rolling-income-warning")
            )
        }
        if (summary.unresolvedCount > 0) {
            Text(stringResource(R.string.rolling_incomplete, summary.unresolvedCount))
        } else if (!summary.thresholdExceeded) {
            KeyValueRow(stringResource(R.string.rolling_distance), formatAmount(summary.distanceToThresholdGel, "GEL"))
        }
        projection?.let {
            KeyValueRow(
                stringResource(R.string.rolling_next_month, currentMonth.plusMonths(1).formatMonthYear()),
                formatAmount(it, "GEL")
            )
        }
        Text(stringResource(R.string.rolling_scope), style = MaterialTheme.typography.bodySmall)
        TextButton(onClick = { expanded = !expanded }, modifier = Modifier.testTag("rolling-income-details")) {
            Text(stringResource(if (expanded) R.string.rolling_hide else R.string.rolling_details))
        }
        if (expanded) {
            Text(
                stringResource(
                    R.string.rolling_eleven,
                    firstMonth.atDay(1).formatIsoDate(),
                    currentMonth.atDay(1).minusDays(1).formatIsoDate(),
                    formatAmount(summary.completedElevenGel, "GEL")
                )
            )
            summary.months.forEach { month ->
                KeyValueRow(
                    month.month.formatMonthYear(),
                    if (month.entryCount == 0) {
                        stringResource(R.string.rolling_no_entries)
                    } else {
                        formatAmount(month.knownIncomeGel, "GEL")
                    }
                )
            }
            Text(
                stringResource(
                    R.string.rolling_leaving,
                    firstMonth.formatMonthYear(),
                    formatAmount(summary.leavingNextMonthGel, "GEL")
                )
            )
            DecimalField(
                label = stringResource(R.string.rolling_expected),
                value = estimate,
                onValueChange = { estimate = it },
                testTag = "rolling-income-estimate"
            )
            if (estimate.isNotBlank() && expectedIncome == null) {
                Text(stringResource(R.string.rolling_invalid_estimate), color = MaterialTheme.colorScheme.error)
            }
            Text(
                stringResource(
                    if (defaultEstimate.isEmpty()) R.string.rolling_no_forecast else R.string.rolling_median
                )
            )
            Text(stringResource(R.string.rolling_scenario))
            Text(stringResource(R.string.rolling_rule))
            TextButton(onClick = { uriHandler.openUri("https://matsne.gov.ge/en/document/view/1043717") }) {
                Text(stringResource(R.string.rolling_source))
            }
        }
    }
}
