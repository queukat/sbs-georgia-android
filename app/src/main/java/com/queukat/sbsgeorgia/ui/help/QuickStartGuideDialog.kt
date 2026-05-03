@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.queukat.sbsgeorgia.ui.help

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
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
fun QuickStartGuideDialog(onDismiss: () -> Unit) {
    val steps = quickStartSteps()
    var currentStepIndex by rememberSaveable { mutableStateOf(0) }
    val currentStep = steps[currentStepIndex]
    val isLastStep = currentStepIndex == steps.lastIndex

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
                        title = stringResource(R.string.quick_start_title),
                        actions = {
                            TextButton(onClick = onDismiss) {
                                Text(stringResource(R.string.quick_start_skip))
                            }
                        }
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
                    Text(
                        text =
                        stringResource(
                            R.string.quick_start_progress,
                            currentStepIndex + 1,
                            steps.size
                        ),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.testTag("quick-start-progress")
                    )
                    AppSection(title = stringResource(currentStep.titleRes)) {
                        Text(
                            text = stringResource(currentStep.bodyRes),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        currentStep.bulletResIds.forEach { bulletResId ->
                            Text(
                                text = "\u2022 ${stringResource(bulletResId)}",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                    QuickStartIndicatorRow(
                        stepCount = steps.size,
                        currentStepIndex = currentStepIndex
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (currentStepIndex > 0) {
                            OutlinedButton(onClick = { currentStepIndex -= 1 }) {
                                Text(stringResource(R.string.quick_start_back))
                            }
                        } else {
                            Box(modifier = Modifier.size(88.dp))
                        }
                        Button(
                            onClick = {
                                if (isLastStep) {
                                    onDismiss()
                                } else {
                                    currentStepIndex += 1
                                }
                            },
                            modifier =
                            Modifier.testTag(
                                if (isLastStep) {
                                    "quick-start-done-button"
                                } else {
                                    "quick-start-next-button"
                                }
                            )
                        ) {
                            Text(
                                stringResource(
                                    if (isLastStep) {
                                        R.string.quick_start_done
                                    } else {
                                        R.string.quick_start_next
                                    }
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickStartIndicatorRow(stepCount: Int, currentStepIndex: Int) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center
    ) {
        repeat(stepCount) { index ->
            Box(
                modifier =
                Modifier
                    .padding(horizontal = 4.dp)
                    .size(10.dp)
                    .background(
                        color =
                        if (index == currentStepIndex) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        },
                        shape = CircleShape
                    )
            )
        }
    }
}

private fun quickStartSteps(): List<QuickStartStep> = listOf(
    QuickStartStep(
        titleRes = R.string.quick_start_step_income_title,
        bodyRes = R.string.quick_start_step_income_body,
        bulletResIds =
        listOf(
            R.string.quick_start_step_income_bullet_one,
            R.string.quick_start_step_income_bullet_two
        )
    ),
    QuickStartStep(
        titleRes = R.string.quick_start_step_fx_title,
        bodyRes = R.string.quick_start_step_fx_body,
        bulletResIds =
        listOf(
            R.string.quick_start_step_fx_bullet_one,
            R.string.quick_start_step_fx_bullet_two
        )
    ),
    QuickStartStep(
        titleRes = R.string.quick_start_step_workflow_title,
        bodyRes = R.string.quick_start_step_workflow_body,
        bulletResIds =
        listOf(
            R.string.quick_start_step_workflow_bullet_one,
            R.string.quick_start_step_workflow_bullet_two
        )
    )
)

private data class QuickStartStep(
    @param:StringRes val titleRes: Int,
    @param:StringRes val bodyRes: Int,
    val bulletResIds: List<Int>
)
