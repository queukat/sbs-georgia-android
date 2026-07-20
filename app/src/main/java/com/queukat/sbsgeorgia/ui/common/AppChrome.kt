@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.queukat.sbsgeorgia.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.FlowRowScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBarItemColors
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarColors
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.queukat.sbsgeorgia.R

@Composable
fun sbsTopAppBarColors(): TopAppBarColors = TopAppBarDefaults.topAppBarColors(
    containerColor = MaterialTheme.colorScheme.surface,
    scrolledContainerColor = MaterialTheme.colorScheme.surface,
    navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
    titleContentColor = MaterialTheme.colorScheme.onSurface,
    actionIconContentColor = MaterialTheme.colorScheme.primary
)

@Composable
fun sbsNavigationBarItemColors(): NavigationBarItemColors = NavigationBarItemDefaults.colors(
    selectedIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
    selectedTextColor = MaterialTheme.colorScheme.onSecondaryContainer,
    indicatorColor = MaterialTheme.colorScheme.secondaryContainer,
    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
)

@Composable
fun SbsTopAppBar(title: String, onBack: (() -> Unit)? = null, actions: @Composable RowScope.() -> Unit = {}) {
    TopAppBar(
        title = { Text(title) },
        navigationIcon = {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.common_back)
                    )
                }
            }
        },
        actions = actions,
        colors = sbsTopAppBarColors()
    )
}

@Composable
fun SbsScreenScaffold(
    innerPadding: PaddingValues,
    title: String? = null,
    onBack: (() -> Unit)? = null,
    topActions: @Composable RowScope.() -> Unit = {},
    snackbarHostState: SnackbarHostState? = null,
    bottomAction: (@Composable () -> Unit)? = null,
    contentPadding: PaddingValues = sbsDefaultContentPadding(),
    content: @Composable (PaddingValues) -> Unit
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            if (title != null) {
                SbsTopAppBar(
                    title = title,
                    onBack = onBack,
                    actions = topActions
                )
            }
        },
        snackbarHost = {
            snackbarHostState?.let { SnackbarHost(hostState = it) }
        },
        bottomBar = {
            bottomAction?.invoke()
        }
    ) { scaffoldPadding ->
        content(
            combinedScreenPadding(
                outerPadding = innerPadding,
                scaffoldPadding = scaffoldPadding,
                contentPadding = contentPadding
            )
        )
    }
}

@Composable
fun StickyPrimaryAction(
    label: String,
    onClick: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    isLoading: Boolean = false,
    statusMessage: String? = null,
    statusTone: SbsStatusTone = SbsStatusTone.Error,
    testTag: String? = null
) {
    SbsStickyActionContainer(
        isLoading = isLoading,
        statusMessage = statusMessage,
        statusTone = statusTone
    ) {
        SbsPrimaryButton(
            label = label,
            onClick = onClick,
            enabled = enabled,
            modifier =
            modifier
                .fillMaxWidth()
                .then(if (testTag != null) Modifier.testTag(testTag) else Modifier)
        )
    }
}

@Composable
fun SbsStickyActionContainer(
    modifier: Modifier = Modifier,
    isLoading: Boolean = false,
    statusMessage: String? = null,
    statusTone: SbsStatusTone = SbsStatusTone.Error,
    statusModifier: Modifier = Modifier,
    content: @Composable FlowRowScope.() -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 3.dp
    ) {
        Column(
            modifier =
            modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (isLoading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            statusMessage?.let {
                StatusBanner(message = it, tone = statusTone, modifier = statusModifier)
            }
            ActionFlowRow(content = content)
        }
    }
}

@Composable
fun ActionFlowRow(
    modifier: Modifier = Modifier,
    horizontalArrangement: Arrangement.Horizontal = Arrangement.spacedBy(12.dp),
    verticalArrangement: Arrangement.Vertical = Arrangement.spacedBy(8.dp),
    content: @Composable FlowRowScope.() -> Unit
) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = horizontalArrangement,
        verticalArrangement = verticalArrangement,
        content = content
    )
}

@Composable
fun StatusBanner(message: String, tone: SbsStatusTone, modifier: Modifier = Modifier) {
    Text(
        text = message,
        color =
        when (tone) {
            SbsStatusTone.Info -> MaterialTheme.colorScheme.primary
            SbsStatusTone.Error -> MaterialTheme.colorScheme.error
            SbsStatusTone.Success -> MaterialTheme.colorScheme.primary
        },
        modifier = modifier
    )
}

enum class SbsStatusTone {
    Info,
    Error,
    Success
}

@Composable
fun sbsDefaultContentPadding(): PaddingValues = PaddingValues(
    start = 16.dp,
    top = 8.dp,
    end = 16.dp,
    bottom = 16.dp
)

@Composable
private fun combinedScreenPadding(
    outerPadding: PaddingValues,
    scaffoldPadding: PaddingValues,
    contentPadding: PaddingValues
): PaddingValues {
    val layoutDirection = LocalLayoutDirection.current
    return PaddingValues(
        start =
        outerPadding.calculateStartPadding(layoutDirection) +
            scaffoldPadding.calculateStartPadding(layoutDirection) +
            contentPadding.calculateStartPadding(layoutDirection),
        top =
        outerPadding.calculateTopPadding() +
            scaffoldPadding.calculateTopPadding() +
            contentPadding.calculateTopPadding(),
        end =
        outerPadding.calculateEndPadding(layoutDirection) +
            scaffoldPadding.calculateEndPadding(layoutDirection) +
            contentPadding.calculateEndPadding(layoutDirection),
        bottom =
        outerPadding.calculateBottomPadding() +
            scaffoldPadding.calculateBottomPadding() +
            contentPadding.calculateBottomPadding()
    )
}
