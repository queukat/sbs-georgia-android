package com.queukat.sbsgeorgia.ui.settings.components

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.queukat.sbsgeorgia.R
import com.queukat.sbsgeorgia.domain.service.ReminderType
import com.queukat.sbsgeorgia.ui.common.AppSection
import com.queukat.sbsgeorgia.ui.settings.SettingsUiState

@Composable
internal fun ReminderSettingsSection(
    uiState: SettingsUiState,
    notificationPermissionGranted: Boolean,
    testReminderType: ReminderType,
    testReminderDelaySeconds: Long,
    onDefaultReminderTimeChanged: (String) -> Unit,
    onDeclarationReminderDaysChanged: (String) -> Unit,
    onPaymentReminderDaysChanged: (String) -> Unit,
    onDeclarationEnabledChanged: (Boolean) -> Unit,
    onPaymentEnabledChanged: (Boolean) -> Unit,
    onRequestNotificationPermission: () -> Unit,
    onTestReminderTypeChanged: (ReminderType) -> Unit,
    onTestReminderDelayChanged: (Long) -> Unit,
    onScheduleTestReminder: (ReminderType, Long) -> Unit
) {
    val reminderDelayOptions = listOf(5L, 15L, 30L)

    AppSection(title = stringResource(R.string.settings_section_reminder_preferences)) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !notificationPermissionGranted
        ) {
            Text(
                stringResource(R.string.settings_notifications_blocked),
                color = MaterialTheme.colorScheme.error
            )
            Button(
                onClick = onRequestNotificationPermission,
                modifier = Modifier.testTag("settings-notification-permission-button")
            ) {
                Text(stringResource(R.string.settings_grant_notification_permission))
            }
        }
        OutlinedTextField(
            value = uiState.defaultReminderTime,
            onValueChange = onDefaultReminderTimeChanged,
            label = { Text(stringResource(R.string.settings_default_reminder_time)) },
            modifier =
            Modifier
                .fillMaxWidth()
                .testTag("settings-default-reminder-time-field"),
            supportingText = { Text(stringResource(R.string.settings_default_reminder_time_hint)) }
        )
        OutlinedTextField(
            value = uiState.declarationReminderDays,
            onValueChange = onDeclarationReminderDaysChanged,
            label = { Text(stringResource(R.string.settings_declaration_reminder_days)) },
            modifier = Modifier.fillMaxWidth(),
            supportingText = { Text(stringResource(R.string.settings_reminder_days_hint)) }
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(stringResource(R.string.settings_enable_declaration_reminders))
            Switch(
                checked = uiState.declarationRemindersEnabled,
                onCheckedChange = onDeclarationEnabledChanged
            )
        }
        HorizontalDivider()
        OutlinedTextField(
            value = uiState.paymentReminderDays,
            onValueChange = onPaymentReminderDaysChanged,
            label = { Text(stringResource(R.string.settings_payment_reminder_days)) },
            modifier = Modifier.fillMaxWidth(),
            supportingText = { Text(stringResource(R.string.settings_reminder_days_hint)) }
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(stringResource(R.string.settings_enable_payment_reminders))
            Switch(
                checked = uiState.paymentRemindersEnabled,
                onCheckedChange = onPaymentEnabledChanged
            )
        }
        HorizontalDivider()
        Text(
            stringResource(R.string.settings_test_notifications_body),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            stringResource(R.string.settings_test_notification_type),
            style = MaterialTheme.typography.labelMedium
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ReminderType.entries.forEach { type ->
                FilterChip(
                    selected = testReminderType == type,
                    onClick = { onTestReminderTypeChanged(type) },
                    label = {
                        Text(
                            stringResource(
                                when (type) {
                                    ReminderType.DECLARATION ->
                                        R.string.settings_test_notification_type_declaration
                                    ReminderType.PAYMENT ->
                                        R.string.settings_test_notification_type_payment
                                }
                            )
                        )
                    }
                )
            }
        }
        Text(
            stringResource(R.string.settings_test_notification_delay),
            style = MaterialTheme.typography.labelMedium
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            reminderDelayOptions.forEach { delaySeconds ->
                FilterChip(
                    selected = testReminderDelaySeconds == delaySeconds,
                    onClick = { onTestReminderDelayChanged(delaySeconds) },
                    label = {
                        Text(
                            stringResource(
                                R.string.settings_test_notification_delay_seconds,
                                delaySeconds
                            )
                        )
                    }
                )
            }
        }
        Button(
            onClick = { onScheduleTestReminder(testReminderType, testReminderDelaySeconds) },
            enabled = !uiState.isSaving && !uiState.isDataOperationInProgress,
            modifier = Modifier.testTag("settings-test-notification-button")
        ) {
            Text(stringResource(R.string.settings_send_test_notification))
        }
    }
}
