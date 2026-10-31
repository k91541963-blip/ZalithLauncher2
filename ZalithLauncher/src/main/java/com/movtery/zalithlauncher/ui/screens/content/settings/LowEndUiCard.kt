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

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.movtery.zalithlauncher.R
import com.movtery.zalithlauncher.setting.AllSettings
import com.movtery.zalithlauncher.ui.screens.content.settings.layouts.CardPosition
import com.movtery.zalithlauncher.ui.screens.content.settings.layouts.SwitchSettingsCard
import com.movtery.zalithlauncher.utils.animation.TransitionAnimationType

/**
 * Switch for the low-end interface mode.
 *
 * Turning it on shortens launcher animations, disables page transitions and removes the
 * background blur; turning it off restores the default animation speed and transition.
 * Only launcher chrome is affected: the game itself and its renderer are untouched.
 */
@Composable
fun LowEndUiCard(modifier: Modifier = Modifier) {
    SwitchSettingsCard(
        modifier = modifier,
        position = CardPosition.Middle,
        unit = AllSettings.lowEndUi,
        title = stringResource(R.string.nova_low_end_ui_title),
        summary = stringResource(R.string.nova_low_end_ui_summary),
        onCheckedChange = { enabled -> applyLowEndUi(enabled) }
    )
}

private fun applyLowEndUi(enabled: Boolean) {
    if (enabled) {
        AllSettings.launcherAnimateSpeed.save(10)
        AllSettings.launcherSwapAnimateType.save(TransitionAnimationType.CLOSE)
        AllSettings.backgroundBlur.save(0)
    } else {
        AllSettings.launcherAnimateSpeed.save(5)
        AllSettings.launcherSwapAnimateType.save(TransitionAnimationType.JELLY_BOUNCE)
    }
}
