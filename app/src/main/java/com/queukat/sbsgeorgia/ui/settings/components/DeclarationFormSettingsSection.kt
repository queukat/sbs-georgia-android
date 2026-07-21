package com.queukat.sbsgeorgia.ui.settings.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.queukat.sbsgeorgia.R
import com.queukat.sbsgeorgia.domain.model.DeclarationFormField
import com.queukat.sbsgeorgia.domain.model.MONTHLY_INCOME_FIELDS
import com.queukat.sbsgeorgia.ui.common.AppSection
import com.queukat.sbsgeorgia.ui.common.declarationFormFieldLabel
import com.queukat.sbsgeorgia.ui.settings.SettingsUiState

@Composable
internal fun DeclarationFormSettingsSection(
    uiState: SettingsUiState,
    onIncludeCumulativeIncomeChanged: (Boolean) -> Unit,
    onIncludeMonthlyIncomeChanged: (Boolean) -> Unit,
    onMonthlyIncomeFieldChanged: (DeclarationFormField) -> Unit
) {
    AppSection(title = stringResource(R.string.settings_section_declaration_fields)) {
        FieldVisibilitySwitch(
            label = declarationFormFieldLabel(DeclarationFormField.CUMULATIVE_INCOME),
            checked = uiState.includeCumulativeIncomeField,
            testTag = "settings-declaration-field-15-switch",
            onCheckedChange = onIncludeCumulativeIncomeChanged
        )
        FieldVisibilitySwitch(
            label = stringResource(R.string.settings_monthly_income_field_enabled),
            checked = uiState.includeMonthlyIncomeField,
            testTag = "settings-declaration-monthly-income-switch",
            onCheckedChange = onIncludeMonthlyIncomeChanged
        )
        if (uiState.includeMonthlyIncomeField) {
            Text(
                text = stringResource(R.string.settings_monthly_income_destination),
                style = MaterialTheme.typography.labelLarge
            )
            Text(
                text = stringResource(R.string.settings_monthly_income_single_field_notice),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall
            )
            MONTHLY_INCOME_FIELDS.forEach { field ->
                val selected = uiState.monthlyIncomeField == field
                Row(
                    modifier =
                    Modifier
                        .fillMaxWidth()
                        .clickable { onMonthlyIncomeFieldChanged(field) }
                        .testTag("settings-declaration-field-${field.fieldNumber}-option"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    RadioButton(
                        selected = selected,
                        onClick = { onMonthlyIncomeFieldChanged(field) }
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(declarationFormFieldLabel(field))
                        Text(
                            text = declarationFormFieldDescription(field),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FieldVisibilitySwitch(
    label: String,
    checked: Boolean,
    testTag: String,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(text = label, modifier = Modifier.weight(1f))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.testTag(testTag)
        )
    }
}

@Composable
private fun declarationFormFieldDescription(field: DeclarationFormField): String = stringResource(
    when (field) {
        DeclarationFormField.CUMULATIVE_INCOME ->
            R.string.settings_declaration_field_15_description
        DeclarationFormField.MONTHLY_CASH_REGISTER_INCOME ->
            R.string.settings_declaration_field_18_description
        DeclarationFormField.MONTHLY_POS_INCOME ->
            R.string.settings_declaration_field_19_description
        DeclarationFormField.MONTHLY_NON_CASH_INCOME ->
            R.string.settings_declaration_field_20_description
        DeclarationFormField.MONTHLY_OTHER_INCOME ->
            R.string.settings_declaration_field_21_description
    }
)
