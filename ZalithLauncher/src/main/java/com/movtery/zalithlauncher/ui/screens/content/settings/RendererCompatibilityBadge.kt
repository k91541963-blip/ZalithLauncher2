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
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.movtery.zalithlauncher.R
import com.movtery.zalithlauncher.game.performance.RendererCompatibility
import com.movtery.zalithlauncher.game.performance.RendererSupport
import com.movtery.zalithlauncher.game.renderer.RendererInterface

/**
 * Small badge telling whether a renderer is expected to work on this device.
 *
 * The check only covers what the launcher can verify locally (OpenGL ES level, GPU family,
 * 32-bit address space). Anything else is shown as "not verified" instead of a false
 * "compatible".
 */
@Composable
fun RendererCompatibilityBadge(
    renderer: RendererInterface,
    modifier: Modifier = Modifier
) {
    val profile = rememberHardwareProfile() ?: return
    val assessment = remember(profile, renderer) {
        RendererCompatibility.assess(profile, renderer)
    }

    val labelRes = when (assessment.support) {
        RendererSupport.SUPPORTED -> R.string.nova_renderer_supported
        RendererSupport.UNKNOWN -> R.string.nova_renderer_unverified
        RendererSupport.UNSUPPORTED -> R.string.nova_renderer_unsupported
    }
    val color = when (assessment.support) {
        RendererSupport.SUPPORTED -> MaterialTheme.colorScheme.primary
        RendererSupport.UNKNOWN -> MaterialTheme.colorScheme.tertiary
        RendererSupport.UNSUPPORTED -> MaterialTheme.colorScheme.error
    }

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = "•",
            style = MaterialTheme.typography.labelSmall,
            color = color
        )
        Text(
            text = stringResource(labelRes),
            style = MaterialTheme.typography.labelSmall,
            color = color
        )
    }
}
