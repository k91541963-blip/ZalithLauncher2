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

import android.text.format.DateUtils
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.movtery.zalithlauncher.R
import com.movtery.zalithlauncher.game.renderer.recovery.RendererRecovery
import com.movtery.zalithlauncher.game.renderer.recovery.RendererRecoveryStore
import com.movtery.zalithlauncher.setting.AllSettings
import com.movtery.zalithlauncher.ui.components.TitleAndSummary
import com.movtery.zalithlauncher.ui.screens.content.settings.layouts.CardPosition
import com.movtery.zalithlauncher.ui.screens.content.settings.layouts.SettingsCard

/**
 * AetherRender 3 — crash recovery card.
 *
 * Shows two real facts read from disk: the recovery event produced by the last failed
 * launch, and the renderer that last finished a launch successfully. Nothing is shown
 * when neither file exists, so a fresh install does not get an empty card.
 */
@Composable
fun RendererRecoveryCard(
    modifier: Modifier = Modifier,
    position: CardPosition = CardPosition.Middle
) {
    val context = LocalContext.current
    val store = remember(context) { RendererRecoveryStore.forContext(context) }
    // Bumped after every user action so the card re-reads the files.
    var revision by remember { mutableIntStateOf(0) }

    val event = remember(revision) { store.event() }
    val lastGood = remember(revision) { store.latestLastGood() }

    if (event == null && lastGood == null) return

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
                title = stringResource(R.string.renderer_recovery_title),
                summary = stringResource(R.string.renderer_recovery_summary)
            )

            event?.let { failed ->
                Text(
                    text = if (failed.toName != null) {
                        stringResource(
                            R.string.renderer_recovery_switched, failed.fromName, failed.toName
                        )
                    } else {
                        stringResource(R.string.renderer_recovery_no_fallback, failed.fromName)
                    },
                    style = MaterialTheme.typography.bodySmall
                )
                if (failed.versionName.isNotBlank()) {
                    Text(
                        modifier = Modifier.alpha(0.7f),
                        text = stringResource(R.string.renderer_recovery_version, failed.versionName),
                        style = MaterialTheme.typography.labelSmall
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(
                        onClick = {
                            store.dismissEvent()
                            revision++
                        }
                    ) {
                        Text(text = stringResource(R.string.renderer_recovery_dismiss))
                    }
                    if (failed.switched) {
                        TextButton(
                            onClick = {
                                AllSettings.renderer.save(
                                    RendererRecovery.uniqueIdentifierOf(
                                        RendererRecovery.AETHER_RENDER_ID
                                    ).orEmpty()
                                )
                                store.dismissEvent()
                                revision++
                            }
                        ) {
                            Text(text = stringResource(R.string.renderer_recovery_restore_aether))
                        }
                    }
                }
            }

            lastGood?.let { good ->
                Text(
                    modifier = Modifier.alpha(0.7f),
                    text = stringResource(
                        R.string.renderer_recovery_last_good,
                        good.versionName,
                        good.rendererName.ifBlank { good.rendererId }
                    ),
                    style = MaterialTheme.typography.labelSmall
                )
                if (good.at > 0) {
                    Text(
                        modifier = Modifier.alpha(0.55f),
                        text = DateUtils.getRelativeTimeSpanString(
                            good.at, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS
                        ).toString(),
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }
    }
}
