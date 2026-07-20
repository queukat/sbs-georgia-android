package com.queukat.sbsgeorgia.ui.settings.components

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import com.queukat.sbsgeorgia.R
import com.queukat.sbsgeorgia.domain.service.ReminderType
import com.queukat.sbsgeorgia.ui.common.ActionFlowRow
import com.queukat.sbsgeorgia.ui.common.AppSection
import com.queukat.sbsgeorgia.ui.settings.SettingsUiState
import com.queukat.sbsgeorgia.ui.settings.parseReminderDays
import java.time.LocalTime

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
    val declarationDays = parseReminderDays(uiState.declarationReminderDays)
    val paymentDays = parseReminderDays(uiState.paymentReminderDays)
    val isReminderTimeValid =
        uiState.defaultReminderTime.isNotBlank() &&
            runCatching { LocalTime.parse(uiState.defaultReminderTime) }.isSuccess
    var showNotificationTest by rememberSaveable { mutableStateOf(false) }

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
            onValueChange = { onDefaultReminderTimeChanged(normalizeReminderTimeInput(it)) },
            label = { Text(stringResource(R.string.settings_default_reminder_time)) },
            modifier =
            Modifier
                .fillMaxWidth()
                .testTag("settings-default-reminder-time-field"),
            isError = !isReminderTimeValid,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            supportingText = {
                Text(
                    stringResource(
                        if (isReminderTimeValid) {
                            R.string.settings_default_reminder_time_hint
                        } else {
                            R.string.settings_default_reminder_time_error_hint
                        }
                    )
                )
            },
            singleLine = true
        )
        Text(
            stringResource(R.string.settings_reminder_time_presets),
            style = MaterialTheme.typography.labelMedium
        )
        ActionFlowRow {
            listOf("09:00", "12:00", "18:00").forEach { time ->
                FilterChip(
                    selected = uiState.defaultReminderTime == time,
                    onClick = { onDefaultReminderTimeChanged(time) },
                    label = { Text(time) },
                    modifier = Modifier.testTag("settings-reminder-time-$time")
                )
            }
        }
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
        ReminderDayPicker(
            title = stringResource(R.string.settings_declaration_reminder_days),
            selectedDays = declarationDays.orEmpty(),
            enabled = uiState.declarationRemindersEnabled,
            invalid = declarationDays == null,
            testTagPrefix = "settings-declaration-day",
            onSelectedDaysChanged = {
                onDeclarationReminderDaysChanged(it.joinToString(","))
            }
        )
        HorizontalDivider()
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
        ReminderDayPicker(
            title = stringResource(R.string.settings_payment_reminder_days),
            selectedDays = paymentDays.orEmpty(),
            enabled = uiState.paymentRemindersEnabled,
            invalid = paymentDays == null,
            testTagPrefix = "settings-payment-day",
            onSelectedDaysChanged = {
                onPaymentReminderDaysChanged(it.joinToString(","))
            }
        )
        HorizontalDivider()
        TextButton(
            onClick = { showNotificationTest = !showNotificationTest },
            modifier = Modifier.testTag("settings-toggle-notification-test")
        ) {
            Text(
                stringResource(
                    if (showNotificationTest) {
                        R.string.settings_hide_notification_test
                    } else {
                        R.string.settings_show_notification_test
                    }
                )
            )
        }
        if (showNotificationTest) {
            Text(
                stringResource(R.string.settings_test_notifications_body),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                stringResource(R.string.settings_test_notification_type),
                style = MaterialTheme.typography.labelMedium
            )
            ActionFlowRow {
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
            ActionFlowRow {
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
}

@Composable
private fun ReminderDayPicker(
    title: String,
    selectedDays: List<Int>,
    enabled: Boolean,
    invalid: Boolean,
    testTagPrefix: String,
    onSelectedDaysChanged: (List<Int>) -> Unit
) {
    Text(title, style = MaterialTheme.typography.labelMedium)
    Text(
        stringResource(R.string.settings_reminder_days_hint),
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    if (invalid) {
        Text(
            stringResource(R.string.settings_reminder_days_invalid_selection),
            color = MaterialTheme.colorScheme.error
        )
    }
    ActionFlowRow {
        for (day in 1..15) {
            FilterChip(
                selected = day in selectedDays,
                onClick = {
                    onSelectedDaysChanged(toggleDay(selectedDays, day))
                },
                enabled = enabled,
                label = { Text(day.toString()) },
                modifier = Modifier.testTag("$testTagPrefix-$day")
            )
        }
    }
}

private fun toggleDay(selectedDays: List<Int>, day: Int): List<Int> = if (day in selectedDays) {
    selectedDays - day
} else {
    (selectedDays + day).distinct().sorted()
}

private fun normalizeReminderTimeInput(value: String): String {
    val digits = value.filter(Char::isDigit).take(4)
    return when {
        digits.length <= 2 -> digits
        digits.length == 3 -> "0${digits.first()}:${digits.drop(1)}"
        else -> "${digits.take(2)}:${digits.drop(2)}"
    }
}
