package com.queukat.sbsgeorgia.ui.settings.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import com.queukat.sbsgeorgia.R
import com.queukat.sbsgeorgia.ui.common.AppSection
import com.queukat.sbsgeorgia.ui.settings.SettingsUiState

@Composable
internal fun TaxpayerSettingsSection(
    uiState: SettingsUiState,
    onRegistrationIdChanged: (String) -> Unit,
    onDisplayNameChanged: (String) -> Unit,
    onLegalFormChanged: (String) -> Unit,
    onRegistrationDateChanged: (String) -> Unit,
    onLegalAddressChanged: (String) -> Unit,
    onActivityTypeChanged: (String) -> Unit
) {
    AppSection(title = stringResource(R.string.settings_section_taxpayer_profile)) {
        OutlinedTextField(
            value = uiState.registrationId,
            onValueChange = onRegistrationIdChanged,
            label = { Text(stringResource(R.string.settings_registration_id)) },
            modifier =
            Modifier
                .fillMaxWidth()
                .testTag("settings-registration-id-field"),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
        )
        OutlinedTextField(
            value = uiState.displayName,
            onValueChange = onDisplayNameChanged,
            label = { Text(stringResource(R.string.settings_display_name)) },
            modifier =
            Modifier
                .fillMaxWidth()
                .testTag("settings-display-name-field")
        )
        OutlinedTextField(
            value = uiState.legalForm,
            onValueChange = onLegalFormChanged,
            label = { Text(stringResource(R.string.onboarding_legal_form)) },
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = uiState.registrationDate,
            onValueChange = onRegistrationDateChanged,
            label = { Text(stringResource(R.string.onboarding_registration_date)) },
            supportingText = { Text(stringResource(R.string.onboarding_optional_iso_date_hint)) },
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = uiState.legalAddress,
            onValueChange = onLegalAddressChanged,
            label = { Text(stringResource(R.string.onboarding_legal_address)) },
            modifier = Modifier.fillMaxWidth(),
            minLines = 2
        )
        OutlinedTextField(
            value = uiState.activityType,
            onValueChange = onActivityTypeChanged,
            label = { Text(stringResource(R.string.onboarding_activity_type)) },
            modifier = Modifier.fillMaxWidth()
        )
    }
}
