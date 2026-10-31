/*
 * NovaLauncher
 * Fork of Zalith Launcher 2, original Copyright (C) 2025-2026 MovTery and contributors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/gpl-3.0.txt>.
 */

package com.movtery.zalithlauncher.ui.screens.content.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.movtery.zalithlauncher.R
import com.movtery.zalithlauncher.game.performance.PerformancePreset
import com.movtery.zalithlauncher.game.performance.PerformancePresets
import com.movtery.zalithlauncher.setting.AllSettings
import com.movtery.zalithlauncher.ui.components.SimpleAlertDialog
import com.movtery.zalithlauncher.ui.screens.content.settings.layouts.CardPosition
import com.movtery.zalithlauncher.ui.screens.content.settings.layouts.SettingsCard

/**
 * Lets the user apply one of the built-in [PerformancePresets].
 *
 * Applying a preset only writes the values it declares (memory, JVM arguments, resolution
 * scale) and leaves the renderer alone. The dialog states exactly what will be overwritten,
 * because these values are shared with the manual settings below.
 */
@Composable
fun PerformancePresetCard(modifier: Modifier = Modifier) {
    val profile = rememberHardwareProfile() ?: return
    val presets = remember(profile) { PerformancePresets.forProfile(profile) }
    var showDialog by remember { mutableStateOf(false) }

    SettingsCard(
        modifier = modifier,
        position = CardPosition.Middle,
        title = stringResource(R.string.nova_preset_title),
        summary = stringResource(R.string.nova_preset_summary),
        onClick = { showDialog = true }
    )

    if (showDialog) {
        SimpleAlertDialog(
            title = stringResource(R.string.nova_preset_title),
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = stringResource(R.string.nova_preset_note),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    for (preset in presets) {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = stringResource(presetTitleRes(preset.id)),
                                style = MaterialTheme.typography.titleSmall
                            )
                            Text(
                                text = stringResource(presetDescriptionRes(preset.id)),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = stringResource(
                                    R.string.nova_preset_values,
                                    preset.ramMb ?: 0,
                                    preset.resolutionRatio ?: 100
                                ),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                            TextButton(
                                modifier = Modifier.fillMaxWidth(),
                                onClick = {
                                    applyPreset(preset)
                                    showDialog = false
                                }
                            ) {
                                Text(text = stringResource(R.string.nova_preset_apply))
                            }
                        }
                    }
                }
            },
            confirmText = stringResource(R.string.generic_close),
            onDismiss = { showDialog = false }
        )
    }
}

private fun applyPreset(preset: PerformancePreset) {
    preset.ramMb?.let { AllSettings.ramAllocation.save(it) }
    AllSettings.jvmArgs.save(preset.jvmArgs)
    preset.resolutionRatio?.let { AllSettings.resolutionRatio.save(it) }
    preset.rendererId?.let { AllSettings.renderer.save(it) }
}

private fun presetTitleRes(id: String) = when (id) {
    PerformancePresets.ID_LOW_END -> R.string.nova_preset_low_end_title
    PerformancePresets.ID_BALANCED -> R.string.nova_preset_balanced_title
    PerformancePresets.ID_QUALITY -> R.string.nova_preset_quality_title
    else -> R.string.nova_preset_title
}

private fun presetDescriptionRes(id: String) = when (id) {
    PerformancePresets.ID_LOW_END -> R.string.nova_preset_low_end_description
    PerformancePresets.ID_BALANCED -> R.string.nova_preset_balanced_description
    PerformancePresets.ID_QUALITY -> R.string.nova_preset_quality_description
    else -> R.string.nova_preset_summary
}
