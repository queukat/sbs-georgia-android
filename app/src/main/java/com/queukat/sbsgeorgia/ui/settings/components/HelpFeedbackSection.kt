package com.queukat.sbsgeorgia.ui.settings.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.queukat.sbsgeorgia.R
import com.queukat.sbsgeorgia.ui.common.AppSection

@Composable
internal fun HelpFeedbackSection(
    onOpenHelpFaq: () -> Unit,
    onViewQuickStart: () -> Unit,
    onRateApp: () -> Unit,
    onSendFeedback: () -> Unit
) {
    AppSection(title = stringResource(R.string.settings_section_help_feedback)) {
        Text(
            stringResource(R.string.settings_help_feedback_body),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        SettingsActionButton(
            label = stringResource(R.string.settings_open_help_faq),
            body = stringResource(R.string.settings_open_help_faq_body),
            onClick = onOpenHelpFaq,
            enabled = true,
            testTag = "settings-open-help-button"
        )
        SettingsActionButton(
            label = stringResource(R.string.settings_view_quick_start),
            body = stringResource(R.string.settings_view_quick_start_body),
            onClick = onViewQuickStart,
            enabled = true,
            testTag = "settings-view-quick-start-button"
        )
        SettingsActionButton(
            label = stringResource(R.string.settings_rate_app),
            body = stringResource(R.string.settings_rate_app_body),
            onClick = onRateApp,
            enabled = true,
            testTag = "settings-rate-app-button"
        )
        SettingsActionButton(
            label = stringResource(R.string.settings_send_feedback),
            body = stringResource(R.string.settings_send_feedback_body),
            onClick = onSendFeedback,
            enabled = true,
            testTag = "settings-send-feedback-button"
        )
    }
}
