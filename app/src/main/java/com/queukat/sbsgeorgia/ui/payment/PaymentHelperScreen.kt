@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.queukat.sbsgeorgia.ui.payment

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.queukat.sbsgeorgia.R
import com.queukat.sbsgeorgia.ui.common.AppSection
import com.queukat.sbsgeorgia.ui.common.KeyValueRow
import com.queukat.sbsgeorgia.ui.common.SbsTopAppBar
import com.queukat.sbsgeorgia.ui.common.SnapshotSummary
import com.queukat.sbsgeorgia.ui.common.copyPlainTextToClipboard
import com.queukat.sbsgeorgia.ui.common.formatAmount
import com.queukat.sbsgeorgia.ui.common.formatMonthYear
import java.time.YearMonth
import kotlinx.coroutines.launch

@Composable
fun PaymentHelperRoute(
    innerPadding: PaddingValues,
    yearMonth: YearMonth,
    onBack: () -> Unit,
) {
    val viewModel: PaymentHelperViewModel = hiltViewModel()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(yearMonth) {
        viewModel.initialize(yearMonth)
    }

    PaymentHelperScreen(
        innerPadding = innerPadding,
        uiState = uiState,
        onBack = onBack,
    )
}

@Composable
fun PaymentHelperScreen(
    innerPadding: PaddingValues,
    uiState: PaymentHelperUiState,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    val data = uiState.data
    val treasuryCodeLabel = stringResource(R.string.payment_helper_treasury_code)
    val paymentCommentLabel = stringResource(R.string.payment_helper_comment)
    val taxAmountLabel = stringResource(R.string.payment_helper_tax_amount)
    val treasuryCodeCopiedMessage = stringResource(R.string.common_copied_template, treasuryCodeLabel)
    val paymentCommentCopiedMessage = stringResource(R.string.common_copied_template, paymentCommentLabel)
    val taxAmountCopiedMessage = stringResource(R.string.common_copied_template, taxAmountLabel)

    fun copy(label: String, value: String, copiedMessage: String) {
        context.copyPlainTextToClipboard(label, value)
        if (value.isNotBlank()) {
            coroutineScope.launch {
                snackbarHostState.showSnackbar(copiedMessage)
            }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            SbsTopAppBar(
                title = data?.incomeMonth?.formatMonthYear() ?: stringResource(R.string.payment_helper_title),
                onBack = onBack,
            )
        },
        snackbarHost = {
            SnackbarHost(hostState = snackbarHostState)
        },
    ) { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(
                    start = 16.dp,
                    end = 16.dp,
                    top = contentPadding.calculateTopPadding() + 8.dp,
                    bottom = contentPadding.calculateBottomPadding() + 16.dp,
                ),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            AppSection(title = stringResource(R.string.payment_helper_section_readiness)) {
                Text(
                    stringResource(
                        when (uiState.readinessState) {
                            PaymentHelperReadinessState.LOADING -> R.string.payment_helper_loading
                            PaymentHelperReadinessState.MONTH_UNAVAILABLE -> R.string.payment_helper_month_unavailable
                            PaymentHelperReadinessState.OUT_OF_SCOPE -> R.string.payment_helper_out_of_scope
                            PaymentHelperReadinessState.REVIEW_REQUIRED -> R.string.payment_helper_review_required
                            PaymentHelperReadinessState.UNRESOLVED_FX -> R.string.payment_helper_unresolved_fx
                            PaymentHelperReadinessState.ZERO_DECLARATION -> R.string.payment_helper_zero_declaration
                            PaymentHelperReadinessState.READY -> R.string.payment_helper_ready
                        },
                    ),
                )
            }
            if (data != null) {
                data.snapshot?.let { snapshot ->
                    AppSection(title = stringResource(R.string.payment_helper_section_summary)) {
                        SnapshotSummary(snapshot = snapshot)
                    }
                }
                AppSection(title = stringResource(R.string.payment_helper_section_bank_details)) {
                    KeyValueRow(treasuryCodeLabel, data.treasuryCode)
                    KeyValueRow(
                        paymentCommentLabel,
                        if (data.comment.isBlank()) stringResource(R.string.payment_helper_complete_settings_first) else data.comment,
                    )
                    KeyValueRow(taxAmountLabel, formatAmount(data.estimatedTaxAmountGel, "GEL"))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedButton(
                            onClick = {
                                copy(
                                    label = treasuryCodeLabel,
                                    value = data.treasuryCode,
                                    copiedMessage = treasuryCodeCopiedMessage,
                                )
                            },
                        ) {
                            Text(stringResource(R.string.common_copy_code))
                        }
                        OutlinedButton(
                            onClick = {
                                copy(
                                    label = paymentCommentLabel,
                                    value = data.comment,
                                    copiedMessage = paymentCommentCopiedMessage,
                                )
                            },
                        ) {
                            Text(stringResource(R.string.common_copy_comment))
                        }
                        OutlinedButton(
                            onClick = {
                                copy(
                                    label = taxAmountLabel,
                                    value = data.estimatedTaxAmountGel.toPlainString(),
                                    copiedMessage = taxAmountCopiedMessage,
                                )
                            },
                        ) {
                            Text(stringResource(R.string.common_copy_amount))
                        }
                    }
                }
            }
        }
    }
}
