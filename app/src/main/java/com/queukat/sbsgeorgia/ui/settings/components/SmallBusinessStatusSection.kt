package com.queukat.sbsgeorgia.ui.settings.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.queukat.sbsgeorgia.R
import com.queukat.sbsgeorgia.ui.common.AppSection
import com.queukat.sbsgeorgia.ui.common.DatePickerField
import com.queukat.sbsgeorgia.ui.common.DecimalField
import com.queukat.sbsgeorgia.ui.settings.SettingsUiState
import java.time.LocalDate

@Composable
internal fun SmallBusinessStatusSection(
    uiState: SettingsUiState,
    onCertificateNumberChanged: (String) -> Unit,
    onCertificateIssuedDateChanged: (String) -> Unit,
    onEffectiveDateChanged: (LocalDate) -> Unit,
    onTaxRateChanged: (String) -> Unit,
) {
    AppSection(title = stringResource(R.string.settings_section_small_business_status)) {
        OutlinedTextField(
            value = uiState.certificateNumber,
            onValueChange = onCertificateNumberChanged,
            label = { Text(stringResource(R.string.onboarding_certificate_number)) },
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = uiState.certificateIssuedDate,
            onValueChange = onCertificateIssuedDateChanged,
            label = { Text(stringResource(R.string.onboarding_certificate_issued_date)) },
            supportingText = { Text(stringResource(R.string.onboarding_optional_iso_date_hint)) },
            modifier = Modifier.fillMaxWidth(),
        )
        DatePickerField(
            label = stringResource(R.string.settings_effective_date),
            value = uiState.effectiveDate,
            onValueChange = onEffectiveDateChanged,
            testTag = "settings-effective-date-field",
        )
        DecimalField(
            label = stringResource(R.string.settings_tax_rate),
            value = uiState.taxRatePercent,
            onValueChange = onTaxRateChanged,
            testTag = "settings-tax-rate-field",
        )
    }
}
