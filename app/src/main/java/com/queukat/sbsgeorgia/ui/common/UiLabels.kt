package com.queukat.sbsgeorgia.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.queukat.sbsgeorgia.R
import com.queukat.sbsgeorgia.domain.model.FxRateSource
import com.queukat.sbsgeorgia.domain.model.MonthlyWorkflowStatus

@Composable
fun workflowStatusLabel(status: MonthlyWorkflowStatus): String = stringResource(
    when (status) {
        MonthlyWorkflowStatus.DRAFT -> R.string.workflow_status_draft
        MonthlyWorkflowStatus.READY_TO_FILE -> R.string.workflow_status_ready_to_file
        MonthlyWorkflowStatus.FILED -> R.string.workflow_status_filed
        MonthlyWorkflowStatus.TAX_PAYMENT_PENDING -> R.string.workflow_status_tax_payment_pending
        MonthlyWorkflowStatus.PAYMENT_SENT -> R.string.workflow_status_payment_sent
        MonthlyWorkflowStatus.PAYMENT_CREDITED -> R.string.workflow_status_payment_credited
        MonthlyWorkflowStatus.SETTLED -> R.string.workflow_status_settled
        MonthlyWorkflowStatus.OVERDUE -> R.string.workflow_status_overdue
    },
)

@Composable
fun fxRateSourceLabel(source: FxRateSource): String = stringResource(
    when (source) {
        FxRateSource.NONE -> R.string.fx_rate_source_none
        FxRateSource.OFFICIAL_NBG_JSON -> R.string.fx_rate_source_official_nbg_json
        FxRateSource.MANUAL_OVERRIDE -> R.string.fx_rate_source_manual_override
    },
)
