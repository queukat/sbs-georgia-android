package com.queukat.sbsgeorgia.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.queukat.sbsgeorgia.R
import com.queukat.sbsgeorgia.domain.model.DeclarationFormField
import com.queukat.sbsgeorgia.domain.usecase.DeclarationCopyValue

@Composable
fun DeclarationCopyValues(
    values: List<DeclarationCopyValue>,
    enabled: Boolean,
    testTagPrefix: String,
    onCopy: (label: String, value: String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        values.forEach { copyValue ->
            val label = declarationFormFieldLabel(copyValue.field)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text =
                        stringResource(
                            R.string.declaration_field_number,
                            copyValue.field.fieldNumber
                        ),
                        style = MaterialTheme.typography.titleSmall
                    )
                    Text(
                        text = declarationFormFieldName(copyValue.field),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = copyValue.value,
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.End
                    )
                    IconButton(
                        onClick = { onCopy(label, copyValue.value) },
                        enabled = enabled,
                        modifier =
                        Modifier.testTag(
                            "$testTagPrefix-field-${copyValue.field.fieldNumber}-copy"
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.ContentCopy,
                            contentDescription =
                            stringResource(R.string.declaration_field_copy, label)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun declarationFormFieldLabel(field: DeclarationFormField): String = stringResource(
    when (field) {
        DeclarationFormField.CUMULATIVE_INCOME -> R.string.declaration_field_15_label
        DeclarationFormField.MONTHLY_CASH_REGISTER_INCOME ->
            R.string.declaration_field_18_label
        DeclarationFormField.MONTHLY_POS_INCOME -> R.string.declaration_field_19_label
        DeclarationFormField.MONTHLY_NON_CASH_INCOME -> R.string.declaration_field_20_label
        DeclarationFormField.MONTHLY_OTHER_INCOME -> R.string.declaration_field_21_label
    }
)

@Composable
private fun declarationFormFieldName(field: DeclarationFormField): String = stringResource(
    when (field) {
        DeclarationFormField.CUMULATIVE_INCOME -> R.string.declaration_field_15_name
        DeclarationFormField.MONTHLY_CASH_REGISTER_INCOME -> R.string.declaration_field_18_name
        DeclarationFormField.MONTHLY_POS_INCOME -> R.string.declaration_field_19_name
        DeclarationFormField.MONTHLY_NON_CASH_INCOME -> R.string.declaration_field_20_name
        DeclarationFormField.MONTHLY_OTHER_INCOME -> R.string.declaration_field_21_name
    }
)
