package com.queukat.sbsgeorgia.ui.common

import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.queukat.sbsgeorgia.R
import com.queukat.sbsgeorgia.domain.model.MonthlyDeclarationSnapshot

@Composable
fun SnapshotSummary(snapshot: MonthlyDeclarationSnapshot) {
    KeyValueRow(
        stringResource(R.string.snapshot_graph_20),
        formatAmount(snapshot.graph20TotalGel, "GEL")
    )
    KeyValueRow(
        stringResource(R.string.snapshot_graph_15_cumulative),
        formatAmount(snapshot.graph15CumulativeGel, "GEL")
    )
    KeyValueRow(
        stringResource(R.string.snapshot_due_date),
        snapshot.period.filingWindow.dueDate
            .formatIsoDate()
    )
    KeyValueRow(
        stringResource(R.string.snapshot_status),
        workflowStatusLabel(snapshot.workflowStatus)
    )
    snapshot.estimatedTaxAmountGel?.let {
        KeyValueRow(stringResource(R.string.snapshot_estimated_tax), formatAmount(it, "GEL"))
    }
    snapshot.paidTaxAmountGel?.let {
        KeyValueRow(stringResource(R.string.snapshot_paid_tax), formatAmount(it, "GEL"))
    }
    snapshot.taxPaymentDifferenceGel?.takeIf { snapshot.taxPaymentMismatch }?.let { difference ->
        Text(
            text =
            stringResource(
                if (snapshot.taxPaymentUnderpaid) {
                    R.string.snapshot_tax_underpaid
                } else {
                    R.string.snapshot_tax_overpaid
                },
                formatAmount(difference.abs(), "GEL")
            ),
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.testTag("snapshot-tax-payment-mismatch")
        )
    }
    if (snapshot.originalCurrencyTotals.isNotEmpty()) {
        FlowRow {
            snapshot.originalCurrencyTotals.forEach { total ->
                SimpleChip("${total.currencyCode}: ${total.amount.toPlainString()}")
            }
        }
    }
    if (snapshot.unresolvedFxCount > 0) {
        Text(stringResource(R.string.snapshot_unresolved_fx_entries, snapshot.unresolvedFxCount))
    }
    FlowRow {
        if (snapshot.zeroDeclarationSuggested) {
            SimpleChip(stringResource(R.string.snapshot_zero_declaration_month))
        }
        if (snapshot.zeroDeclarationPrepared) {
            SimpleChip(stringResource(R.string.snapshot_zero_declaration_prepared))
        }
        if (snapshot.period.outOfScope) {
            SimpleChip(stringResource(R.string.snapshot_out_of_scope))
        }
        if (snapshot.reviewNeeded) {
            SimpleChip(stringResource(R.string.snapshot_review_needed))
        }
        if (snapshot.taxPaymentMismatch) {
            SimpleChip(
                label = stringResource(R.string.snapshot_tax_mismatch),
                containerColor = MaterialTheme.colorScheme.errorContainer,
                labelColor = MaterialTheme.colorScheme.onErrorContainer
            )
        }
    }
}
