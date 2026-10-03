package com.queukat.sbsgeorgia.ui.common

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.queukat.sbsgeorgia.R
import com.queukat.sbsgeorgia.domain.model.MonthlyDeclarationSnapshot

/** A user attestation, not an assertion that the app verified other accounts or tax eligibility. */
@Composable
fun rememberDeclarationCoverage(snapshot: MonthlyDeclarationSnapshot?): MutableState<Boolean> = rememberSaveable(
    snapshot?.period?.incomeMonth,
    snapshot?.graph15CumulativeGel,
    snapshot?.graph20TotalGel,
    snapshot?.estimatedTaxAmountGel,
    snapshot?.unresolvedFxCount,
    snapshot?.reviewNeeded
) { mutableStateOf(false) }

@Composable
fun DeclarationCoverageConfirmation(confirmed: Boolean, onConfirmed: (Boolean) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Checkbox(
            checked = confirmed,
            onCheckedChange = onConfirmed,
            modifier = Modifier.testTag("declaration-confirm-coverage")
        )
        Text(stringResource(R.string.declaration_confirm_coverage))
    }
}
