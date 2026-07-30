package com.queukat.sbsgeorgia.ui.settings.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.queukat.sbsgeorgia.R
import com.queukat.sbsgeorgia.ui.common.AppSection
import com.queukat.sbsgeorgia.ui.common.SbsSecondaryButton

@Composable
internal fun HelpFeedbackSection(onOpenHelpFaq: () -> Unit) {
    AppSection(title = stringResource(R.string.settings_section_help_feedback)) {
        SbsSecondaryButton(
            label = stringResource(R.string.settings_open_help_faq),
            onClick = onOpenHelpFaq,
            modifier =
            Modifier
                .fillMaxWidth()
                .testTag("settings-open-help-button"),
            leadingIcon = Icons.AutoMirrored.Outlined.HelpOutline
        )
    }
}
