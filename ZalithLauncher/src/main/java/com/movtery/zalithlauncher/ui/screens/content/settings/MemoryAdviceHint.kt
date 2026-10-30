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

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.movtery.zalithlauncher.R
import com.movtery.zalithlauncher.game.performance.HardwareProfile
import com.movtery.zalithlauncher.game.performance.MemoryAdvisor
import com.movtery.zalithlauncher.setting.AllSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Shows the memory recommendation for the current device under the memory allocation slider.
 *
 * The hardware profile is read once on a background dispatcher. When detection fails the
 * hint stays hidden instead of showing a guessed value.
 */
@Composable
fun MemoryAdviceHint(modifier: Modifier = Modifier) {
    val context = LocalContext.current

    val profile by produceState<HardwareProfile?>(initialValue = null) {
        value = withContext(Dispatchers.IO) {
            runCatching { HardwareProfile.detect(context.applicationContext) }.getOrNull()
        }
    }

    val detected = profile ?: return
    val advice = remember(detected) { MemoryAdvisor.advise(detected) }
    val selected = AllSettings.ramAllocation.state ?: advice.recommendedMb

    Column(modifier = modifier.fillMaxWidth().padding(top = 4.dp)) {
        Text(
            text = stringResource(
                R.string.nova_memory_advice_recommended,
                advice.recommendedMb,
                advice.maxMb
            ),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = stringResource(
                R.string.nova_memory_advice_device,
                detected.deviceName,
                detected.cpuCores,
                detected.totalRamMb,
                detected.glEsMajor,
                detected.glEsMinor
            ),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (advice.isAboveSafeLimit(selected)) {
            Text(
                text = stringResource(R.string.nova_memory_advice_above_limit),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}
