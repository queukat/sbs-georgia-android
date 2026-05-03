package com.queukat.sbsgeorgia.ui.settings.components

import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.queukat.sbsgeorgia.R
import com.queukat.sbsgeorgia.domain.model.ThemeMode
import com.queukat.sbsgeorgia.ui.common.AppSection

@Composable
internal fun AppearanceSettingsSection(selectedThemeMode: ThemeMode, onThemeModeChanged: (ThemeMode) -> Unit) {
    AppSection(title = stringResource(R.string.settings_section_appearance)) {
        FlowRow {
            ThemeMode.entries.forEach { mode ->
                FilterChip(
                    selected = selectedThemeMode == mode,
                    onClick = { onThemeModeChanged(mode) },
                    label = {
                        Text(
                            when (mode) {
                                ThemeMode.SYSTEM -> stringResource(R.string.theme_system)
                                ThemeMode.LIGHT -> stringResource(R.string.theme_light)
                                ThemeMode.DARK -> stringResource(R.string.theme_dark)
                            }
                        )
                    }
                )
            }
        }
    }
}
