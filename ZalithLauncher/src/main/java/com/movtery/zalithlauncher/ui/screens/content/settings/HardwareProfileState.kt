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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.platform.LocalContext
import com.movtery.zalithlauncher.game.performance.HardwareProfile
import com.movtery.zalithlauncher.game.performance.HardwareProfileCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Returns the device hardware profile, detecting it once on a background dispatcher.
 *
 * Returns `null` while detection is still running or when it failed; callers should hide
 * device dependent hints instead of showing guessed values.
 */
@Composable
fun rememberHardwareProfile(): HardwareProfile? {
    val context = LocalContext.current

    val profile by produceState(initialValue = HardwareProfileCache.peek()) {
        if (value == null) {
            value = withContext(Dispatchers.IO) {
                runCatching { HardwareProfileCache.get(context.applicationContext) }.getOrNull()
            }
        }
    }

    return profile
}
