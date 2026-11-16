/*
 * AetherLite Launcher
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

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.movtery.zalithlauncher.R
import com.movtery.zalithlauncher.setting.AllSettings
import com.movtery.zalithlauncher.setting.enums.AetherRenderMode
import com.movtery.zalithlauncher.ui.components.SimpleListItem
import com.movtery.zalithlauncher.ui.components.TitleAndSummary
import com.movtery.zalithlauncher.ui.screens.content.settings.layouts.CardPosition
import com.movtery.zalithlauncher.ui.screens.content.settings.layouts.SettingsCard
import com.movtery.zalithlauncher.utils.animation.getAnimateTween

/**
 * Selects the EGL policy used by AetherRender.
 *
 * The card explains each mode individually because the difference is real but narrow:
 * only EGL config selection and surface hints change, never Minecraft's chunk renderer.
 * The value is read on the next launch, so the note says so explicitly.
 */
@Composable
fun AetherRenderModeCard(
    modifier: Modifier = Modifier,
    position: CardPosition = CardPosition.Middle
) {
    val selected = AllSettings.aetherRenderMode.state
    var expanded by remember { mutableStateOf(false) }

    SettingsCard(
        modifier = modifier.fillMaxWidth(),
        position = position
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(all = 16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded },
                verticalAlignment = Alignment.CenterVertically
            ) {
                TitleAndSummary(
                    modifier = Modifier.weight(1f),
                    title = stringResource(R.string.aether_render_mode_title),
                    summary = stringResource(R.string.aether_render_mode_summary)
                )
                val rotation by animateFloatAsState(
                    targetValue = if (expanded) -180f else 0f,
                    animationSpec = getAnimateTween()
                )
                Icon(
                    modifier = Modifier
                        .padding(start = 8.dp)
                        .size(24.dp)
                        .rotate(rotation),
                    painter = painterResource(R.drawable.ic_arrow_drop_down_rounded),
                    contentDescription = stringResource(
                        if (expanded) R.string.generic_collapse else R.string.generic_expand
                    )
                )
            }

            Text(
                modifier = Modifier.alpha(0.7f),
                text = stringResource(R.string.settings_element_selected, stringResource(selected.nameRes)),
                style = MaterialTheme.typography.labelSmall
            )

            AnimatedVisibility(
                modifier = Modifier.fillMaxWidth(),
                visible = expanded,
                enter = expandVertically(animationSpec = getAnimateTween()),
                exit = shrinkVertically(animationSpec = getAnimateTween()) + fadeOut()
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    AetherRenderMode.entries.forEach { mode ->
                        SimpleListItem(
                            modifier = Modifier.fillMaxWidth(),
                            selected = mode == selected,
                            itemName = stringResource(mode.nameRes),
                            summary = {
                                Text(
                                    modifier = Modifier.alpha(0.7f),
                                    text = stringResource(modeDescriptionRes(mode)),
                                    style = MaterialTheme.typography.labelSmall
                                )
                            },
                            onClick = {
                                if (mode != AllSettings.aetherRenderMode.state) {
                                    AllSettings.aetherRenderMode.save(mode)
                                }
                                expanded = false
                            }
                        )
                    }
                }
            }

            Text(
                modifier = Modifier.alpha(0.65f),
                text = stringResource(R.string.aether_render_mode_note),
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}

private fun modeDescriptionRes(mode: AetherRenderMode) = when (mode) {
    AetherRenderMode.NATIVE -> R.string.aether_render_mode_native_desc
    AetherRenderMode.BALANCED -> R.string.aether_render_mode_balanced_desc
    AetherRenderMode.LITE -> R.string.aether_render_mode_lite_desc
    AetherRenderMode.PERFORMANCE -> R.string.aether_render_mode_performance_desc
    AetherRenderMode.COMPATIBILITY -> R.string.aether_render_mode_compatibility_desc
}
