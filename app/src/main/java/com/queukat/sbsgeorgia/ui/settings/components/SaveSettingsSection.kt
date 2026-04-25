package com.queukat.sbsgeorgia.ui.settings.components

import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.queukat.sbsgeorgia.R
import com.queukat.sbsgeorgia.ui.common.AppSection

@Composable
internal fun SaveSettingsSection(
    errorMessage: String?,
    isActionEnabled: Boolean,
    onSave: () -> Unit,
) {
    AppSection(title = stringResource(R.string.settings_section_save)) {
        errorMessage?.let {
            Text(it, color = MaterialTheme.colorScheme.error)
        }
        Button(
            onClick = onSave,
            enabled = isActionEnabled,
            modifier = Modifier.testTag("settings-save-button"),
        ) {
            Text(stringResource(R.string.settings_save))
        }
    }
}
