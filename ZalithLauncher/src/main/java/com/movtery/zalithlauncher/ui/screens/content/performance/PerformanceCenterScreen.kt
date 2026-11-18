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

package com.movtery.zalithlauncher.ui.screens.content.performance

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.movtery.zalithlauncher.R
import com.movtery.zalithlauncher.game.performance.CpuTimes
import com.movtery.zalithlauncher.game.performance.GameSessionAccumulator
import com.movtery.zalithlauncher.game.performance.GameSessionStore
import com.movtery.zalithlauncher.game.performance.GameSessionSummary
import com.movtery.zalithlauncher.game.performance.SessionHistoryEntry
import com.movtery.zalithlauncher.game.performance.GpuInfoReader
import com.movtery.zalithlauncher.game.performance.MaliTier
import com.movtery.zalithlauncher.game.performance.PerformanceMonitor
import com.movtery.zalithlauncher.game.performance.PerformanceSnapshot
import com.movtery.zalithlauncher.game.performance.ThermalInfo
import com.movtery.zalithlauncher.setting.AllSettings
import com.movtery.zalithlauncher.ui.base.BaseScreen
import com.movtery.zalithlauncher.ui.components.AnimatedColumn
import com.movtery.zalithlauncher.ui.components.TitleAndSummary
import com.movtery.zalithlauncher.ui.components.verticalScrollWithBar
import com.movtery.zalithlauncher.ui.screens.NormalNavKey
import com.movtery.zalithlauncher.ui.screens.content.settings.PerformancePresetCard
import com.movtery.zalithlauncher.ui.screens.content.settings.layouts.CardPosition
import com.movtery.zalithlauncher.ui.screens.content.settings.layouts.SettingsCard
import com.movtery.zalithlauncher.ui.screens.content.settings.layouts.SettingsCardColumn
import com.movtery.zalithlauncher.viewmodel.ScreenBackStackViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** How often the Performance Center refreshes while it is visible. */
private const val POLL_INTERVAL_MS = 2000L

/**
 * Live Performance Center.
 *
 * Everything shown here is measured on this device: CPU/RAM/temperature through Android
 * APIs and `/proc`, and game frame times from AetherMetrics (native swap timestamps) for
 * the last session. Readings that are not available are shown as unknown instead of
 * being replaced with a placeholder; no number is estimated.
 *
 * Polling only runs while the screen is actually visible, so leaving the page stops the
 * work immediately.
 */
@Composable
fun PerformanceCenterScreen(
    key: NormalNavKey.PerformanceCenter,
    backStackViewModel: ScreenBackStackViewModel
) {
    val context = LocalContext.current
    var snapshot by remember { mutableStateOf<PerformanceSnapshot?>(null) }
    var previousCpu by remember { mutableStateOf<CpuTimes?>(null) }
    var refreshing by remember { mutableStateOf(false) }
    var refreshKey by remember { mutableIntStateOf(0) }
    var lastSession by remember { mutableStateOf<GameSessionSummary?>(null) }

    BaseScreen(
        screenKey = key,
        currentKey = backStackViewModel.mainScreen.currentKey
    ) { isVisible ->
        LaunchedEffect(isVisible, refreshKey) {
            if (!isVisible) {
                refreshing = false
                return@LaunchedEffect
            }
            refreshing = true
            lastSession = withContext(Dispatchers.IO) {
                GameSessionStore.readLast(context.applicationContext)
            }
            // A single sample cannot produce a CPU percentage: it needs a delta between
            // two /proc/stat reads. The first refresh therefore only seeds the counters.
            while (isActive) {
                val sample = withContext(Dispatchers.IO) {
                    PerformanceMonitor.read(context.applicationContext, previousCpu)
                }
                previousCpu = sample.cpuTimes
                snapshot = sample.snapshot
                refreshing = false
                delay(POLL_INTERVAL_MS)
            }
        }

        AnimatedColumn(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScrollWithBar(state = rememberScrollState())
                .padding(all = 12.dp),
            isVisible = isVisible
        ) { scope ->
            AnimatedItem(scope) { yOffset ->
                HeaderCard(
                    modifier = Modifier.offset { IntOffset(x = 0, y = yOffset.roundToPx()) },
                    refreshing = refreshing,
                    onRefresh = { refreshKey++ }
                )
            }

            AnimatedItem(scope) { yOffset ->
                SettingsCardColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .offset { IntOffset(x = 0, y = yOffset.roundToPx()) }
                ) {
                    val cpu = snapshot?.cpuUsagePercent
                    MetricCard(
                        title = stringResource(R.string.aether_perf_cpu),
                        value = when {
                            cpu != null -> stringResource(
                                R.string.aether_perf_cpu_value,
                                cpu.toInt(),
                                snapshot?.cpuCores ?: 0
                            )
                            snapshot != null -> stringResource(R.string.aether_perf_cpu_measuring)
                            else -> stringResource(R.string.aether_perf_home_reading)
                        },
                        position = CardPosition.Top,
                        progress = cpu?.div(100f)
                    )

                    val memory = snapshot?.memory
                    MetricCard(
                        title = stringResource(R.string.aether_perf_ram),
                        value = if (memory != null && memory.totalMb > 0) {
                            stringResource(
                                R.string.aether_perf_ram_value,
                                memory.usedMb,
                                memory.totalMb
                            )
                        } else {
                            stringResource(R.string.aether_perf_memory_unavailable)
                        },
                        position = CardPosition.Middle,
                        progress = if (memory != null && memory.totalMb > 0) {
                            memory.usedMb.toFloat() / memory.totalMb
                        } else null,
                        details = listOfNotNull(
                            stringResource(R.string.aether_perf_app_memory) to
                                (memory?.appPssMb?.let { "$it MB" }
                                    ?: stringResource(R.string.aether_perf_memory_unavailable)),
                            stringResource(R.string.aether_perf_jvm_heap) to
                                (memory?.let {
                                    stringResource(
                                        R.string.aether_perf_jvm_heap_value,
                                        it.javaHeapUsedMb,
                                        it.javaHeapMaxMb
                                    )
                                } ?: stringResource(R.string.aether_perf_memory_unavailable))
                        )
                    )

                    val gpu = snapshot?.gpu
                    MetricCard(
                        title = stringResource(R.string.aether_device_info_gpu),
                        value = gpu?.maliModel
                            ?: gpu?.renderer
                            ?: stringResource(R.string.aether_device_info_unknown),
                        position = CardPosition.Middle,
                        details = listOfNotNull(
                            stringResource(R.string.aether_device_info_driver) to
                                (gpu?.let { driverLabel(it.vendor, it.version) }
                                    ?: stringResource(R.string.aether_device_info_unknown)),
                            gpu?.maliModel?.let {
                                stringResource(R.string.aether_device_info_mali_class) to
                                    maliTierLabel(GpuInfoReader.classifyMali(it))
                            },
                            stringResource(R.string.aether_render_mode_title) to
                                stringResource(AllSettings.aetherRenderMode.state.nameRes)
                        )
                    )

                    MetricCard(
                        title = stringResource(R.string.aether_device_info_temperature),
                        value = thermalValue(snapshot?.thermal),
                        position = CardPosition.Bottom,
                        details = listOfNotNull(
                            stringResource(R.string.aether_device_info_thermal_status) to
                                thermalStatusLabel(snapshot?.thermal?.thermalStatus)
                        )
                    )
                }
            }

            if (snapshot?.memory?.lowMemory == true) {
                AnimatedItem(scope) { yOffset ->
                    WarningCard(
                        modifier = Modifier.offset { IntOffset(x = 0, y = yOffset.roundToPx()) },
                        text = stringResource(R.string.aether_perf_low_memory)
                    )
                }
            }

            AnimatedItem(scope) { yOffset ->
                PerformancePresetCard(
                    modifier = Modifier.offset { IntOffset(x = 0, y = yOffset.roundToPx()) }
                )
            }

            AnimatedItem(scope) { yOffset ->
                SessionCard(
                    modifier = Modifier.offset { IntOffset(x = 0, y = yOffset.roundToPx()) },
                    session = lastSession,
                    onClear = {
                        GameSessionStore.clear(context.applicationContext)
                        lastSession = null
                    }
                )
            }

            AnimatedItem(scope) { yOffset ->
                PerformanceAdvisorCard(
                    modifier = Modifier.offset { IntOffset(x = 0, y = yOffset.roundToPx()) },
                    session = lastSession?.let { SessionHistoryEntry.from(it) }
                )
            }

            AnimatedItem(scope) { yOffset ->
                SessionHistoryCard(
                    modifier = Modifier.offset { IntOffset(x = 0, y = yOffset.roundToPx()) },
                    refreshKey = refreshKey,
                    visible = isVisible
                )
            }

            AnimatedItem(scope) { yOffset ->
                SettingsCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .offset { IntOffset(x = 0, y = yOffset.roundToPx()) },
                    position = CardPosition.Single
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(all = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.aether_perf_ingame_title),
                            style = MaterialTheme.typography.titleSmall
                        )
                        Text(
                            modifier = Modifier.alpha(0.7f),
                            text = stringResource(R.string.aether_perf_ingame_note),
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HeaderCard(
    modifier: Modifier = Modifier,
    refreshing: Boolean,
    onRefresh: () -> Unit
) {
    SettingsCard(
        modifier = modifier.fillMaxWidth(),
        position = CardPosition.Single
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
                    title = stringResource(R.string.aether_perf_center_title),
                    summary = stringResource(R.string.aether_perf_center_summary)
                )
                IconButton(
                    enabled = !refreshing,
                    onClick = onRefresh
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_refresh),
                        contentDescription = stringResource(R.string.aether_perf_refresh)
                    )
                }
            }
            Text(
                modifier = Modifier.alpha(0.65f),
                text = stringResource(R.string.aether_perf_live_note),
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}

/**
 * A single measured metric with an optional 0..1 progress bar and extra rows.
 * `progress` is `null` when the value is unknown, which hides the bar instead of
 * drawing a misleading empty one.
 */
@Composable
private fun MetricCard(
    title: String,
    value: String,
    position: CardPosition,
    modifier: Modifier = Modifier,
    progress: Float? = null,
    details: List<Pair<String, String>> = emptyList()
) {
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
            Text(
                modifier = Modifier.alpha(0.7f),
                text = title,
                style = MaterialTheme.typography.labelMedium
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium
            )
            progress?.let { fraction ->
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    progress = { fraction.coerceIn(0f, 1f) }
                )
            }
            details.forEach { (label, data) ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        modifier = Modifier
                            .weight(0.45f)
                            .alpha(0.7f),
                        text = label,
                        style = MaterialTheme.typography.labelSmall
                    )
                    Text(
                        modifier = Modifier.weight(0.55f),
                        text = data,
                        style = MaterialTheme.typography.labelSmall,
                        textAlign = TextAlign.End
                    )
                }
            }
        }
    }
}

@Composable
private fun WarningCard(
    modifier: Modifier = Modifier,
    text: String
) {
    SettingsCard(
        modifier = modifier.fillMaxWidth(),
        position = CardPosition.Single
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(all = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_warning_filled),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error
            )
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium
            )
        }
    }
}

/**
 * Last measured game session.
 *
 * FPS, 1% low, P99 frame time and the per-window graph come from real swap timestamps
 * captured by AetherMetrics while Minecraft was running; CPU/RAM/temperature come from
 * the same monitors used above. When the game has never been launched this card says so
 * instead of showing zeros.
 */
@Composable
private fun SessionCard(
    modifier: Modifier = Modifier,
    session: GameSessionSummary?,
    onClear: () -> Unit
) {
    SettingsCard(
        modifier = modifier.fillMaxWidth(),
        position = CardPosition.Single
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(all = 16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TitleAndSummary(
                    modifier = Modifier.weight(1f),
                    title = stringResource(R.string.aether_perf_session_title),
                    summary = stringResource(R.string.aether_perf_session_summary)
                )
                if (session != null) {
                    TextButton(onClick = onClear) {
                        Text(text = stringResource(R.string.aether_perf_session_clear))
                    }
                }
            }

            if (session == null) {
                Text(
                    modifier = Modifier.alpha(0.7f),
                    text = stringResource(R.string.aether_perf_session_empty),
                    style = MaterialTheme.typography.labelSmall
                )
            } else {
                SessionRow(
                    label = stringResource(R.string.aether_perf_session_version),
                    value = session.versionName
                )
                SessionRow(
                    label = stringResource(R.string.aether_perf_session_renderer),
                    value = session.rendererMode
                )
                SessionRow(
                    label = stringResource(R.string.aether_perf_session_avg_fps),
                    value = stringResource(
                        R.string.aether_perf_session_fps_value,
                        session.averageFps
                    )
                )
                SessionRow(
                    label = stringResource(R.string.aether_perf_session_one_low),
                    value = stringResource(
                        R.string.aether_perf_session_fps_value,
                        session.onePercentLowFps
                    )
                )
                SessionRow(
                    label = stringResource(R.string.aether_perf_session_p99),
                    value = stringResource(
                        R.string.aether_perf_session_ms_value,
                        session.p99FrameMs
                    )
                )
                SessionRow(
                    label = stringResource(R.string.aether_perf_session_jank),
                    value = stringResource(
                        R.string.aether_perf_session_percent_value,
                        session.jankPercent
                    )
                )
                SessionRow(
                    label = stringResource(R.string.aether_perf_session_duration),
                    value = stringResource(
                        R.string.aether_perf_session_duration_value,
                        session.durationSeconds / 60,
                        session.durationSeconds % 60
                    )
                )
                SessionRow(
                    label = stringResource(R.string.aether_perf_session_frames),
                    value = session.framesCaptured.toString()
                )
                SessionRow(
                    label = stringResource(R.string.aether_perf_session_cpu),
                    value = session.averageCpuPercent?.let {
                        stringResource(R.string.aether_perf_session_percent_value, it)
                    } ?: stringResource(R.string.aether_perf_session_unknown)
                )
                SessionRow(
                    label = stringResource(R.string.aether_perf_session_ram),
                    value = if (session.totalRamMb > 0) {
                        stringResource(
                            R.string.aether_perf_session_ram_value,
                            session.peakRamUsedMb,
                            session.totalRamMb
                        )
                    } else {
                        stringResource(R.string.aether_perf_session_unknown)
                    }
                )
                SessionRow(
                    label = stringResource(R.string.aether_perf_session_temp),
                    value = session.peakTemperatureCelsius?.let {
                        stringResource(R.string.aether_device_info_temp_soc, it)
                    } ?: stringResource(R.string.aether_perf_session_unknown)
                )
                SessionRow(
                    label = stringResource(R.string.aether_perf_session_when),
                    value = formatTimestamp(session.startedAtEpochMs)
                )

                if (session.fpsBuckets.isNotEmpty()) {
                    Text(
                        modifier = Modifier.alpha(0.7f),
                        text = stringResource(R.string.aether_perf_session_graph),
                        style = MaterialTheme.typography.labelSmall
                    )
                    FpsBucketBars(buckets = session.fpsBuckets)
                }

                if (session.samplesTruncated) {
                    Text(
                        modifier = Modifier.alpha(0.7f),
                        text = stringResource(
                            R.string.aether_perf_session_truncated,
                            GameSessionAccumulator.MAX_STORED_FRAMES
                        ),
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }
    }
}

@Composable
private fun SessionRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            modifier = Modifier
                .weight(0.45f)
                .alpha(0.7f),
            text = label,
            style = MaterialTheme.typography.labelSmall
        )
        Text(
            modifier = Modifier.weight(0.55f),
            text = value,
            style = MaterialTheme.typography.labelSmall,
            textAlign = TextAlign.End
        )
    }
}

/**
 * One bar per measured 10 second window. Bar height is relative to the fastest window,
 * so the shape shows pacing changes instead of a normalised guess.
 */
@Composable
private fun FpsBucketBars(buckets: List<Double>) {
    val maximum = buckets.maxOrNull()?.takeIf { it > 0.0 } ?: 1.0
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        buckets.forEach { fps ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight((fps / maximum).toFloat().coerceIn(0.04f, 1f))
                    .background(
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.55f),
                        shape = RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp)
                    )
            )
        }
    }
}

private fun formatTimestamp(epochMs: Long): String =
    SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(epochMs))

private fun driverLabel(vendor: String?, version: String?): String = when {
    vendor != null && version != null -> "$vendor • $version"
    vendor != null -> vendor
    version != null -> version
    else -> "—"
}

@Composable
private fun thermalValue(info: ThermalInfo?): String {
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
