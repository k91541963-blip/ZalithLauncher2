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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.movtery.zalithlauncher.R
import com.movtery.zalithlauncher.game.performance.AdvisorAction
import com.movtery.zalithlauncher.game.performance.AdvisorEvidence
import com.movtery.zalithlauncher.game.performance.AdvisorFinding
import com.movtery.zalithlauncher.game.performance.AdvisorSeverity
import com.movtery.zalithlauncher.game.performance.ComparisonVerdict
import com.movtery.zalithlauncher.game.performance.PerformanceAdvisor
import com.movtery.zalithlauncher.game.performance.SessionComparison
import com.movtery.zalithlauncher.game.performance.SessionHistoryEntry
import com.movtery.zalithlauncher.game.performance.SessionHistoryStore
import com.movtery.zalithlauncher.game.renderer.aether.AetherTuning
import com.movtery.zalithlauncher.setting.AllSettings
import com.movtery.zalithlauncher.ui.components.TitleAndSummary
import com.movtery.zalithlauncher.ui.screens.content.settings.layouts.CardPosition
import com.movtery.zalithlauncher.ui.screens.content.settings.layouts.SettingsCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date
import java.util.Locale

private fun oneDecimal(value: Double): String =
    String.format(Locale.getDefault(), "%.1f", value)

private fun signedOneDecimal(value: Double): String =
    String.format(Locale.getDefault(), "%+.1f", value)

private fun signedInt(value: Int): String = String.format(Locale.getDefault(), "%+d", value)

/**
 * AetherLite 3.0 — measured-session advisor card.
 *
 * Each finding names the measurement that produced it and offers at most one existing
 * setting. Applying is a user action; the advisor itself never writes a setting.
 */
@Composable
fun PerformanceAdvisorCard(
    session: SessionHistoryEntry?,
    modifier: Modifier = Modifier,
    position: CardPosition = CardPosition.Middle
) {
    val tuning = AllSettings.aetherRenderTuning.state
    val vsync = AllSettings.aetherRenderVsync.state
    var applied by remember { mutableStateOf<String?>(null) }

    val findings = remember(session, tuning, vsync) {
        session?.let { PerformanceAdvisor.analyze(it, tuning, vsync) } ?: emptyList()
    }
    // Labels are resolved in composition; the click handler only applies a setting.
    val actionLabels = mapOf(
        AdvisorAction.USE_THROUGHPUT to stringResource(R.string.aether_advisor_action_throughput),
        AdvisorAction.USE_MEMORY_SAVER to stringResource(R.string.aether_advisor_action_memory),
        AdvisorAction.DISABLE_VSYNC to stringResource(R.string.aether_advisor_action_vsync)
    )

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
            TitleAndSummary(
                modifier = Modifier.fillMaxWidth(),
                title = stringResource(R.string.aether_advisor_title),
                summary = stringResource(R.string.aether_advisor_summary)
            )

            if (session == null) {
                Text(
                    modifier = Modifier.alpha(0.7f),
                    text = stringResource(R.string.aether_advisor_no_session),
                    style = MaterialTheme.typography.labelSmall
                )
            } else {
                if (findings.isEmpty()) {
                    Text(
                        modifier = Modifier.alpha(0.7f),
                        text = stringResource(R.string.aether_advisor_no_findings),
                        style = MaterialTheme.typography.labelSmall
                    )
                }
                findings.forEach { finding ->
                    val label = actionLabels[finding.action]
                    FindingRow(
                        finding = finding,
                        actionLabel = label,
                        onApply = {
                            if (label != null) {
                                applyAction(finding.action)
                                applied = label
                            }
                        }
                    )
                }
                applied?.let { message ->
                    Text(
                        text = stringResource(R.string.aether_advisor_applied, message),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Text(
                    modifier = Modifier.alpha(0.55f),
                    text = stringResource(R.string.aether_advisor_settings_note),
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
    }
}

@Composable
private fun FindingRow(
    finding: AdvisorFinding,
    actionLabel: String?,
    onApply: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(
                when (finding.severity) {
                    AdvisorSeverity.WARNING ->
                        MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f)
                    AdvisorSeverity.SUGGESTION ->
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                    AdvisorSeverity.INFO ->
                        MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f)
                }
            )
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Text(
            text = evidenceText(finding.evidence),
            style = MaterialTheme.typography.labelMedium
        )
        evidenceReason(finding.evidence)?.let { reason ->
            Text(
                modifier = Modifier.alpha(0.75f),
                text = reason,
                style = MaterialTheme.typography.labelSmall
            )
        }
        if (actionLabel != null) {
            TextButton(onClick = onApply) {
                Text(text = actionLabel)
            }
        }
    }
}

@Composable
private fun evidenceText(evidence: AdvisorEvidence): String = when (evidence) {
    is AdvisorEvidence.NoFrames -> stringResource(
        R.string.aether_advisor_no_frames,
        evidence.framesCaptured,
        evidence.durationSeconds
    )

    is AdvisorEvidence.Smooth -> stringResource(
        R.string.aether_advisor_smooth,
        oneDecimal(evidence.averageFps),
        oneDecimal(evidence.jankPercent)
    )

    is AdvisorEvidence.Stutter -> stringResource(
        R.string.aether_advisor_stutter,
        oneDecimal(evidence.averageFps),
        oneDecimal(evidence.onePercentLowFps),
        oneDecimal(evidence.jankPercent)
    )

    is AdvisorEvidence.MemoryPressure -> stringResource(
        R.string.aether_advisor_memory,
        evidence.peakUsedMb,
        evidence.totalMb
    )

    is AdvisorEvidence.RefreshLocked -> stringResource(
        R.string.aether_advisor_vsync,
        oneDecimal(evidence.averageFps)
    )

    is AdvisorEvidence.HighTemperature -> stringResource(
        R.string.aether_advisor_temperature,
        oneDecimal(evidence.celsius.toDouble())
    )

    is AdvisorEvidence.HighCpu -> stringResource(
        R.string.aether_advisor_cpu,
        oneDecimal(evidence.cpuPercent),
        oneDecimal(evidence.averageFps)
    )
}

@Composable
private fun evidenceReason(evidence: AdvisorEvidence): String? {
    if (evidence !is AdvisorEvidence.Stutter) return null
    val low = if (evidence.lowOnePercent) {
        stringResource(R.string.aether_advisor_reason_low)
    } else {
        null
    }
    val jank = if (evidence.highJank) {
        stringResource(R.string.aether_advisor_reason_jank)
    } else {
        null
    }
    return listOfNotNull(low, jank).joinToString(" ").takeIf { it.isNotBlank() }
}

/** Applies the finding's single setting. Only called from an explicit user tap. */
private fun applyAction(action: AdvisorAction) {
    when (action) {
        AdvisorAction.USE_THROUGHPUT ->
            AllSettings.aetherRenderTuning.save(AetherTuning.THROUGHPUT)

        AdvisorAction.USE_MEMORY_SAVER ->
            AllSettings.aetherRenderTuning.save(AetherTuning.MEMORY_SAVER)

        AdvisorAction.DISABLE_VSYNC ->
            AllSettings.aetherRenderVsync.save(false)

        AdvisorAction.NONE -> Unit
    }
}

/**
 * AetherLite 3.0 — session history and comparison.
 *
 * Reads only sessions that were actually recorded. Selecting two of them shows the
 * measured deltas and a verdict with an explicit tolerance.
 */
@Composable
fun SessionHistoryCard(
    modifier: Modifier = Modifier,
    refreshKey: Int = 0,
    position: CardPosition = CardPosition.Single
) {
    val context = LocalContext.current
    var entries by remember { mutableStateOf<List<SessionHistoryEntry>>(emptyList()) }
    var selected by remember { mutableStateOf<List<SessionHistoryEntry>>(emptyList()) }
    var confirmClear by remember { mutableStateOf(false) }
    var revision by remember { mutableStateOf(0) }

    LaunchedEffect(refreshKey, revision) {
        entries = withContext(Dispatchers.IO) {
            SessionHistoryStore.forContext(context).all()
        }
        // Drop selections that no longer exist after a clear.
        selected = selected.filter { choice -> entries.any { it == choice } }
    }

    val comparison = if (selected.size == 2) {
        SessionComparison.of(selected[0], selected[1])
    } else {
        null
    }

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
                title = stringResource(R.string.aether_history_title),
                summary = stringResource(
                    R.string.aether_history_summary,
                    SessionHistoryStore.MAX_ENTRIES
                )
            )

            if (entries.isEmpty()) {
                Text(
                    modifier = Modifier.alpha(0.7f),
                    text = stringResource(R.string.aether_history_empty),
                    style = MaterialTheme.typography.labelSmall
                )
            } else {
                entries.forEach { entry ->
                    HistoryRow(
                        entry = entry,
                        selected = selected.contains(entry),
                        onClick = {
                            selected = when {
                                selected.contains(entry) -> selected - entry
                                selected.size < 2 -> selected + entry
                                else -> listOf(selected[1], entry)
                            }
                        }
                    )
                }

                Text(
                    modifier = Modifier.alpha(0.6f),
                    text = stringResource(R.string.aether_history_selection_hint),
                    style = MaterialTheme.typography.labelSmall
                )

                comparison?.let { result ->
                    ComparisonBlock(result)
                    TextButton(
                        onClick = { selected = emptyList() }
                    ) {
                        Text(text = stringResource(R.string.aether_history_clear_selection))
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    when {
                        confirmClear -> {
                            Text(
                                modifier = Modifier
                                    .weight(1f)
                                    .alpha(0.8f),
                                text = stringResource(R.string.aether_history_clear_confirm, entries.size),
                                style = MaterialTheme.typography.labelSmall
                            )
                            TextButton(
                                onClick = {
                                    SessionHistoryStore.forContext(context).clear()
                                    confirmClear = false
                                    revision++
                                }
                            ) {
                                Text(
                                    text = stringResource(R.string.aether_history_delete),
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                            TextButton(onClick = { confirmClear = false }) {
                                Text(text = stringResource(R.string.generic_cancel))
                            }
                        }

                        else -> {
                            TextButton(onClick = { confirmClear = true }) {
                                Text(text = stringResource(R.string.aether_history_clear))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryRow(
    entry: SessionHistoryEntry,
    selected: Boolean,
    onClick: () -> Unit
) {
    val date = remember(entry.endedAtEpochMs) {
        DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
            .format(Date(entry.endedAtEpochMs))
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(
                if (selected) {
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                } else {
                    MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.4f)
                }
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                modifier = Modifier.weight(1f),
                text = "${entry.versionName.ifBlank { "?" }} · $date",
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                modifier = Modifier.alpha(0.7f),
                text = stringResource(
                    R.string.aether_history_duration,
                    (entry.durationSeconds / 60).coerceAtLeast(1)
                ),
                style = MaterialTheme.typography.labelSmall
            )
        }
        Text(
            modifier = Modifier.alpha(0.8f),
            text = stringResource(
                R.string.aether_history_row,
                oneDecimal(entry.averageFps),
                oneDecimal(entry.onePercentLowFps),
                oneDecimal(entry.jankPercent)
            ),
            style = MaterialTheme.typography.labelSmall
        )
    }
}

@Composable
private fun ComparisonBlock(comparison: SessionComparison) {
    val verdict = when (comparison.verdict) {
        ComparisonVerdict.BETTER -> R.string.aether_history_verdict_better
        ComparisonVerdict.WORSE -> R.string.aether_history_verdict_worse
        ComparisonVerdict.MIXED -> R.string.aether_history_verdict_mixed
        ComparisonVerdict.SAME -> R.string.aether_history_verdict_same
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = stringResource(R.string.aether_history_compare_title),
            style = MaterialTheme.typography.labelMedium
        )
        Text(
            modifier = Modifier.alpha(0.85f),
            text = stringResource(
                R.string.aether_history_compare_fps,
                oneDecimal(comparison.older.averageFps),
                oneDecimal(comparison.newer.averageFps),
                signedOneDecimal(comparison.fpsDelta)
            ),
            style = MaterialTheme.typography.labelSmall
        )
        Text(
            modifier = Modifier.alpha(0.85f),
            text = stringResource(
                R.string.aether_history_compare_low,
                oneDecimal(comparison.older.onePercentLowFps),
                oneDecimal(comparison.newer.onePercentLowFps),
                signedOneDecimal(comparison.onePercentLowDelta)
            ),
            style = MaterialTheme.typography.labelSmall
        )
        Text(
            modifier = Modifier.alpha(0.85f),
            text = stringResource(
                R.string.aether_history_compare_jank,
                oneDecimal(comparison.older.jankPercent),
                oneDecimal(comparison.newer.jankPercent),
                signedOneDecimal(comparison.jankDelta)
            ),
            style = MaterialTheme.typography.labelSmall
        )
        Text(
            modifier = Modifier.alpha(0.85f),
            text = stringResource(
                R.string.aether_history_compare_duration,
                signedInt(comparison.durationDeltaSeconds / 60)
            ),
            style = MaterialTheme.typography.labelSmall
        )
        Text(
            text = stringResource(verdict),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary
        )
    }
}
