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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
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
import com.movtery.zalithlauncher.game.performance.GpuInfoReader
import com.movtery.zalithlauncher.game.renderer.aether.AetherGpuProfiles
import com.movtery.zalithlauncher.game.renderer.aether.AetherTuning
import com.movtery.zalithlauncher.setting.AllSettings
import com.movtery.zalithlauncher.ui.components.SimpleListItem
import com.movtery.zalithlauncher.ui.components.TitleAndSummary
import com.movtery.zalithlauncher.ui.screens.content.settings.layouts.CardPosition
import com.movtery.zalithlauncher.ui.screens.content.settings.layouts.SettingsCard
import com.movtery.zalithlauncher.utils.animation.getAnimateTween
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * AetherRender 3 — core tuning preset picker.
 *
 * The card only offers presets whose environment variables are read by the bundled
 * NG-GL4ES core, and it prints the exact variable names of the current selection so the
 * user can see that nothing imaginary is being toggled. Applies on the next launch.
 */
@Composable
fun AetherRenderTuningCard(
    modifier: Modifier = Modifier,
    position: CardPosition = CardPosition.Middle
) {
    val selected = AllSettings.aetherRenderTuning.state
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
                    title = stringResource(R.string.aether_tuning_title),
                    summary = stringResource(R.string.aether_tuning_summary)
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

            // Show what the preset really sets; "core default" shows the note only.
            if (selected.envKeys.isNotEmpty()) {
                Text(
                    modifier = Modifier.alpha(0.7f),
                    text = selected.envKeys.joinToString("  "),
                    style = MaterialTheme.typography.labelSmall
                )
            }

            AnimatedVisibility(
                modifier = Modifier.fillMaxWidth(),
                visible = expanded,
                enter = expandVertically(animationSpec = getAnimateTween()),
                exit = shrinkVertically(animationSpec = getAnimateTween()) + fadeOut()
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    AetherTuning.entries.forEach { tuning ->
                        SimpleListItem(
                            modifier = Modifier.fillMaxWidth(),
                            selected = tuning == selected,
                            itemName = stringResource(tuning.nameRes),
                            summary = {
                                Text(
                                    modifier = Modifier.alpha(0.7f),
                                    text = stringResource(tuning.summaryRes),
                                    style = MaterialTheme.typography.labelSmall
                                )
                            },
                            onClick = {
                                if (tuning != AllSettings.aetherRenderTuning.state) {
                                    AllSettings.aetherRenderTuning.save(tuning)
                                }
                                expanded = false
                            }
                        )
                    }
                }
            }

            Text(
                modifier = Modifier.alpha(0.65f),
                text = stringResource(R.string.aether_tuning_note),
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}

/**
 * AetherRender 3 — GPU recommendation.
 *
 * Detects the Mali model from sysfs (real driver data), derives a conservative preset and
 * only writes it when the user taps the button. When no model can be read the card says so
 * instead of guessing.
 */
@Composable
fun AetherGpuRecommendationCard(
    modifier: Modifier = Modifier,
    position: CardPosition = CardPosition.Middle
) {
    val model by produceState<String?>(initialValue = null) {
        value = withContext(Dispatchers.IO) {
            runCatching { GpuInfoReader.readSysfsModel() }.getOrNull()
        }
    }

    val preset = remember(model) { AetherGpuProfiles.recommend(model) }
    var applied by remember { mutableStateOf(false) }
    val tuningName = stringResource(preset.tuning.nameRes)

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
            TitleAndSummary(
                modifier = Modifier.fillMaxWidth(),
                title = stringResource(R.string.aether_gpu_title),
                summary = stringResource(R.string.aether_gpu_summary)
            )

            Text(
                modifier = Modifier.alpha(0.7f),
                text = preset.model
                    ?.let { stringResource(R.string.aether_gpu_detected, it) }
                    ?: stringResource(R.string.aether_gpu_unknown),
                style = MaterialTheme.typography.labelSmall
            )

            Text(
                modifier = Modifier.alpha(0.65f),
                text = stringResource(preset.noteRes),
                style = MaterialTheme.typography.labelSmall
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    enabled = preset.model != null && !applied,
                    onClick = {
                        AllSettings.aetherRenderMode.save(preset.mode)
                        AllSettings.aetherRenderTuning.save(preset.tuning)
                        applied = true
                    }
                ) {
                    Text(text = stringResource(R.string.aether_gpu_apply))
                }
                if (applied) {
                    Text(
                        modifier = Modifier
                            .padding(start = 8.dp)
                            .alpha(0.7f),
                        text = stringResource(
                            R.string.aether_gpu_applied,
                            stringResource(preset.mode.nameRes),
                            tuningName
                        ),
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }
    }
}
