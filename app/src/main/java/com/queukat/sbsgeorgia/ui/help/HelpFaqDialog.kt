@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.queukat.sbsgeorgia.ui.help

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.queukat.sbsgeorgia.R
import com.queukat.sbsgeorgia.ui.common.AppSection
import com.queukat.sbsgeorgia.ui.common.SbsTopAppBar

@Composable
fun HelpFaqDialog(
    onDismiss: () -> Unit,
    onViewQuickStartGuide: () -> Unit,
    onRateApp: () -> Unit,
    onSendFeedback: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties =
        DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false
        )
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Scaffold(
                containerColor = MaterialTheme.colorScheme.background,
                topBar = {
                    SbsTopAppBar(
                        title = stringResource(R.string.help_title),
                        onBack = onDismiss
                    )
                }
            ) { contentPadding ->
                Column(
                    modifier =
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(
                            start = 16.dp,
                            end = 16.dp,
                            top = contentPadding.calculateTopPadding() + 8.dp,
                            bottom = contentPadding.calculateBottomPadding() + 24.dp
                        ),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    AppSection(
                        title = stringResource(R.string.help_section_intro),
                        modifier = Modifier.testTag("help-faq-root")
                    ) {
                        Text(
                            text = stringResource(R.string.help_intro_body),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    AppSection(title = stringResource(R.string.help_section_quick_actions)) {
                        Button(
                            onClick = onViewQuickStartGuide,
                            modifier =
                            Modifier
                                .fillMaxWidth()
                                .testTag("help-view-quick-start-button")
                        ) {
                            Text(stringResource(R.string.help_view_quick_start_again))
                        }
                        Button(onClick = onRateApp, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.help_rate_app))
                        }
                        Button(onClick = onSendFeedback, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.help_send_feedback))
                        }
                        Text(
                            text = stringResource(R.string.help_feedback_public_note),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    faqItems().forEach { item ->
                        AppSection(title = stringResource(item.questionRes)) {
                            Text(
                                text = stringResource(item.answerRes),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun faqItems(): List<HelpFaqItem> = listOf(
    HelpFaqItem(
        questionRes = R.string.help_faq_add_income_question,
        answerRes = R.string.help_faq_add_income_answer
    ),
    HelpFaqItem(
        questionRes = R.string.help_faq_gel_conversion_question,
        answerRes = R.string.help_faq_gel_conversion_answer
    ),
    HelpFaqItem(
        questionRes = R.string.help_faq_missing_rate_question,
        answerRes = R.string.help_faq_missing_rate_answer
    ),
    HelpFaqItem(
        questionRes = R.string.help_faq_monthly_declarations_question,
        answerRes = R.string.help_faq_monthly_declarations_answer
    ),
    HelpFaqItem(
        questionRes = R.string.help_faq_reminders_question,
        answerRes = R.string.help_faq_reminders_answer
    ),
    HelpFaqItem(
        questionRes = R.string.help_faq_backup_question,
        answerRes = R.string.help_faq_backup_answer
    ),
    HelpFaqItem(
        questionRes = R.string.help_faq_feedback_question,
        answerRes = R.string.help_faq_feedback_answer
    )
)

private data class HelpFaqItem(@param:StringRes val questionRes: Int, @param:StringRes val answerRes: Int)
