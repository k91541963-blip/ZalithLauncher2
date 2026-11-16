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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.movtery.zalithlauncher.R
import com.movtery.zalithlauncher.game.performance.GpuFamily
import com.movtery.zalithlauncher.game.performance.GpuInfo
import com.movtery.zalithlauncher.game.performance.GpuInfoCache
import com.movtery.zalithlauncher.game.performance.GpuInfoReader
import com.movtery.zalithlauncher.game.performance.MaliTier
import com.movtery.zalithlauncher.game.performance.ThermalInfo
import com.movtery.zalithlauncher.game.performance.ThermalInfoReader
import com.movtery.zalithlauncher.game.renderer.renderers.AetherRenderer
import com.movtery.zalithlauncher.setting.AllSettings
import com.movtery.zalithlauncher.ui.components.TitleAndSummary
import com.movtery.zalithlauncher.ui.screens.content.settings.layouts.CardPosition
import com.movtery.zalithlauncher.ui.screens.content.settings.layouts.SettingsCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Read-only device panel: real GPU strings, thermal snapshot and the AetherRender state.
 *
 * The EGL probe and the sysfs reads run on [Dispatchers.IO] and only once per process
 * unless the user taps refresh. Missing values are shown as "unknown"; nothing is
 * estimated except the clearly labelled Mali name-based class.
 */
@Composable
fun DeviceInfoCard(
    modifier: Modifier = Modifier,
    position: CardPosition = CardPosition.Top
) {
    val context = LocalContext.current
    val profile = rememberHardwareProfile()

    var gpuInfo by remember { mutableStateOf(GpuInfoCache.peek()) }
    var thermalInfo by remember { mutableStateOf<ThermalInfo?>(null) }
    var refreshing by remember { mutableStateOf(false) }
    var refreshKey by remember { mutableStateOf(0) }

    LaunchedEffect(refreshKey) {
        refreshing = true
        gpuInfo = withContext(Dispatchers.IO) {
            if (refreshKey == 0) GpuInfoCache.get() else GpuInfoCache.refresh()
        }
        thermalInfo = withContext(Dispatchers.IO) {
            ThermalInfoReader.read(context.applicationContext)
        }
        refreshing = false
    }

    val rendererSelected = AllSettings.renderer.state == AetherRenderer.getUniqueIdentifier()

    SettingsCard(
        modifier = modifier.fillMaxWidth(),
        position = position
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(all = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TitleAndSummary(
                    modifier = Modifier.weight(1f),
                    title = stringResource(R.string.aether_device_info_title),
                    summary = stringResource(R.string.aether_device_info_summary)
                )
                IconButton(
                    enabled = !refreshing,
                    onClick = { refreshKey++ }
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_refresh),
                        contentDescription = stringResource(R.string.aether_device_info_refresh)
                    )
                }
            }

            if (profile != null) {
                InfoRow(
                    label = stringResource(R.string.aether_device_info_gpu),
                    value = gpuInfo?.maliModel
                        ?: gpuInfo?.renderer
                        ?: gpuFamilyLabel(profile.gpuFamily)
                )
                InfoRow(
                    label = stringResource(R.string.aether_device_info_driver),
                    value = gpuInfo?.let { driverLabel(it) }
                        ?: stringResource(R.string.aether_device_info_unknown)
                )
                InfoRow(
                    label = stringResource(R.string.aether_device_info_gles),
                    value = if (profile.glEsVersionCode == 0) {
                        stringResource(R.string.aether_device_info_unknown)
                    } else {
                        "${profile.glEsMajor}.${profile.glEsMinor}"
                    }
                )
                InfoRow(
                    label = stringResource(R.string.aether_device_info_soc),
                    value = profile.socModel.takeIf { it.isNotBlank() }
                        ?: stringResource(R.string.aether_device_info_unknown)
                )
                InfoRow(
                    label = stringResource(R.string.aether_device_info_ram),
                    value = stringResource(
                        R.string.aether_device_info_ram_value,
                        profile.freeRamMb,
                        profile.totalRamMb
                    )
                )
                val maliModel = gpuInfo?.maliModel
                if (maliModel != null) {
                    InfoRow(
                        label = stringResource(R.string.aether_device_info_mali_class),
                        value = maliTierLabel(GpuInfoReader.classifyMali(maliModel))
                    )
                }            } else {
                InfoRow(
                    label = stringResource(R.string.aether_device_info_gpu),
                    value = stringResource(R.string.aether_device_info_unknown)
                )
            }

            InfoRow(
                label = stringResource(R.string.aether_device_info_temperature),
                value = thermalLabel(thermalInfo)
            )
            InfoRow(
                label = stringResource(R.string.aether_device_info_thermal_status),
                value = thermalStatusLabel(thermalInfo?.thermalStatus)
            )
            InfoRow(
                label = stringResource(R.string.aether_device_info_renderer_state),
                value = stringResource(
                    if (rendererSelected) R.string.aether_device_info_renderer_on
                    else R.string.aether_device_info_renderer_off
                )
            )

            Text(
                modifier = Modifier.alpha(0.65f),
                text = stringResource(R.string.aether_device_info_aether_note),
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}

@Composable
private fun InfoRow(
    label: String,
    value: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            modifier = Modifier.weight(0.38f).alpha(0.7f),
            text = label,
            style = MaterialTheme.typography.labelSmall
        )
        Text(
            modifier = Modifier.weight(0.62f),
            text = value,
            style = MaterialTheme.typography.labelSmall,
            textAlign = TextAlign.End
        )
    }
}

@Composable
private fun gpuFamilyLabel(family: GpuFamily): String {
    return stringResource(
        when (family) {
            GpuFamily.MALI -> R.string.aether_gpu_family_mali
            GpuFamily.ADRENO -> R.string.aether_gpu_family_adreno
            GpuFamily.POWERVR -> R.string.aether_gpu_family_powervr
            GpuFamily.XCLIPSE -> R.string.aether_gpu_family_xclipse
            GpuFamily.UNKNOWN -> R.string.aether_device_info_unknown
        }
    )
}

private fun driverLabel(info: GpuInfo): String {
    val vendor = info.vendor
    val version = info.version
    return when {
        vendor != null && version != null -> "$vendor • $version"
        vendor != null -> vendor
        version != null -> version
        else -> "—"
    }
}

@Composable
private fun maliTierLabel(tier: MaliTier): String {
    return stringResource(
        when (tier) {
            MaliTier.ENTRY -> R.string.aether_mali_class_entry
            MaliTier.MID -> R.string.aether_mali_class_mid
            MaliTier.HIGH -> R.string.aether_mali_class_high
            MaliTier.UNKNOWN -> R.string.aether_device_info_unknown
        }
    )
}

@Composable
private fun thermalLabel(info: ThermalInfo?): String {
    if (info == null) return stringResource(R.string.aether_device_info_unknown)
    val soc = info.socCelsius?.let { stringResource(R.string.aether_device_info_temp_soc, it) }
    val battery = info.batteryCelsius?.let {
        stringResource(R.string.aether_device_info_temp_battery, it)
    }
    return listOfNotNull(soc, battery).joinToString(" • ").ifEmpty {
        stringResource(R.string.aether_device_info_unknown)
    }
}

@Composable
private fun thermalStatusLabel(status: Int?): String {
    if (status == null) return stringResource(R.string.aether_thermal_status_unavailable)
    return stringResource(
        when (status) {
            0 -> R.string.aether_thermal_status_none
            1 -> R.string.aether_thermal_status_light
            2 -> R.string.aether_thermal_status_moderate
            3 -> R.string.aether_thermal_status_severe
            4 -> R.string.aether_thermal_status_critical
            5 -> R.string.aether_thermal_status_emergency
            else -> R.string.aether_thermal_status_shutdown
        }
    )
}
