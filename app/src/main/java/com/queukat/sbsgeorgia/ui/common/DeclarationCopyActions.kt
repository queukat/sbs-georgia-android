@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.queukat.sbsgeorgia.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.queukat.sbsgeorgia.R

@Composable
fun DeclarationCopyActions(
    canCopyAll: Boolean,
    canCopyBankText: Boolean,
    testTagPrefix: String,
    onCopyAll: () -> Unit,
    onCopyBankText: () -> Unit,
    onShareAll: (() -> Unit)? = null
) {
    val uriHandler = LocalUriHandler.current
    var browserUnavailable by androidx.compose.runtime.remember {
        androidx.compose.runtime.mutableStateOf(false)
    }
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        SbsPrimaryButton(
            label = stringResource(R.string.month_detail_copy_all_text),
            onClick = onCopyAll,
            enabled = canCopyAll,
            leadingIcon = Icons.Outlined.ContentCopy,
            modifier =
            Modifier
                .fillMaxWidth()
                .testTag("$testTagPrefix-copy-all-text-button")
        )
        TextButton(
            onClick = {
                browserUnavailable = runCatching {
                    uriHandler.openUri("https://eservices.rs.ge/Login.aspx")
                }.isFailure
            },
            modifier = Modifier.fillMaxWidth().testTag("$testTagPrefix-open-rs-button")
        ) {
            Text(stringResource(R.string.declaration_open_rs))
        }
        if (browserUnavailable) {
            Text(stringResource(R.string.declaration_browser_unavailable))
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SbsSecondaryButton(
                label = stringResource(R.string.declaration_copy_bank_text),
                onClick = onCopyBankText,
                enabled = canCopyBankText,
                leadingIcon = Icons.Outlined.ContentCopy,
                modifier =
                Modifier
                    .weight(1f)
                    .testTag("$testTagPrefix-copy-payment-text-button")
            )
            onShareAll?.let { share ->
                val shareLabel = stringResource(R.string.declaration_copy_share_telegram)
                TooltipBox(
                    positionProvider =
                    TooltipDefaults.rememberTooltipPositionProvider(
                        TooltipAnchorPosition.Above
                    ),
                    tooltip = { PlainTooltip { Text(shareLabel) } },
                    state = rememberTooltipState()
                ) {
                    OutlinedIconButton(
                        onClick = share,
                        enabled = canCopyAll,
                        shape = RoundedCornerShape(8.dp),
                        modifier =
                        Modifier
                            .size(48.dp)
                            .testTag("$testTagPrefix-share-telegram-button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.Send,
                            contentDescription = shareLabel
                        )
                    }
                }
            }
        }
    }
}
