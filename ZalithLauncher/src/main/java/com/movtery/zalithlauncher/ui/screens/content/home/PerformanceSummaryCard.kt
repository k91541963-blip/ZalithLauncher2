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

package com.movtery.zalithlauncher.ui.screens.content.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.movtery.zalithlauncher.R
import com.movtery.zalithlauncher.game.performance.CpuTimes
import com.movtery.zalithlauncher.game.performance.PerformanceMonitor
import com.movtery.zalithlauncher.game.performance.PerformanceSnapshot
import com.movtery.zalithlauncher.ui.components.BackgroundCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

/** Home card refresh interval. Slower than the Performance Center on purpose. */
private const val HOME_POLL_INTERVAL_MS = 5000L

/**
 * Compact, live performance summary for the home dashboard.
 *
 * It reads the same [PerformanceMonitor] sources as the full Performance Center and only
 * polls while the home screen is visible. Tapping the card opens the full screen; no
 * metric here is invented and unknown values are rendered as a dash.
 */
@Composable
fun PerformanceSummaryCard(
    modifier: Modifier = Modifier,
    isVisible: Boolean,
    onOpen: () -> Unit
) {
    val context = LocalContext.current
    var snapshot by remember { mutableStateOf<PerformanceSnapshot?>(null) }
    var previousCpu by remember { mutableStateOf<CpuTimes?>(null) }

    LaunchedEffect(isVisible) {
        if (!isVisible) return@LaunchedEffect
        while (isActive) {
            val sample = withContext(Dispatchers.IO) {
                PerformanceMonitor.read(context.applicationContext, previousCpu)
            }
            previousCpu = sample.cpuTimes
            snapshot = sample.snapshot
            delay(HOME_POLL_INTERVAL_MS)
        }
    }

    BackgroundCard(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        onClick = onOpen
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.aether_perf_center_title),
                        style = MaterialTheme.typography.titleSmall
                    )
                    Text(
                        modifier = Modifier.alpha(0.7f),
                        text = stringResource(R.string.aether_perf_home_summary),
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val memory = snapshot?.memory
                SummaryCell(
                    modifier = Modifier.weight(1f),
                    label = stringResource(R.string.aether_perf_home_cpu),
                    value = snapshot?.cpuUsagePercent?.let {
                        stringResource(R.string.aether_perf_home_cpu_value, it.toInt())
                    } ?: UNKNOWN
                )
                SummaryCell(
                    modifier = Modifier.weight(1f),
                    label = stringResource(R.string.aether_perf_home_ram),
                    value = memory?.takeIf { it.totalMb > 0 }?.let {
                        stringResource(
                            R.string.aether_perf_home_ram_value,
                            it.usedMb,
                            it.totalMb
                        )
                    } ?: UNKNOWN
                )
                SummaryCell(
                    modifier = Modifier.weight(1f),
                    label = stringResource(R.string.aether_perf_home_gpu),
                    value = snapshot?.gpu?.maliModel
                        ?: snapshot?.gpu?.renderer
                        ?: UNKNOWN
                )
                SummaryCell(
                    modifier = Modifier.weight(1f),
                    label = stringResource(R.string.aether_perf_home_temp),
                    value = (snapshot?.thermal?.socCelsius ?: snapshot?.thermal?.batteryCelsius)
                        ?.let { stringResource(R.string.aether_perf_home_temp_value, it) }
                        ?: UNKNOWN
                )
            }

            Text(
                modifier = Modifier.alpha(0.6f),
                text = stringResource(R.string.aether_perf_home_hint),
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}

private const val UNKNOWN = "—"

@Composable
private fun SummaryCell(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(
            modifier = Modifier.alpha(0.7f),
            text = label,
            style = MaterialTheme.typography.labelSmall
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
