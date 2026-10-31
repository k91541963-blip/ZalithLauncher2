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

package com.movtery.zalithlauncher.ui.screens.content

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.movtery.zalithlauncher.R
import com.movtery.zalithlauncher.game.diagnostics.LogAdvice
import com.movtery.zalithlauncher.game.diagnostics.LogAnalysis
import com.movtery.zalithlauncher.game.diagnostics.LogFinding
import com.movtery.zalithlauncher.game.diagnostics.LogSeverity
import com.movtery.zalithlauncher.ui.components.SimpleAlertDialog

/**
 * Compact banner shown on top of the log viewer when the analyzer found something.
 *
 * The banner only states the primary conclusion; tapping "Details" opens the full report with
 * evidence lines, advice and the `Caused by:` chain. Nothing is shown when the log looks clean.
 */
@Composable
fun LogAnalysisBanner(
    analysis: LogAnalysis,
    modifier: Modifier = Modifier
) {
    val primary = analysis.primary ?: return
    var showDetails by remember { mutableStateOf(false) }

    Surface(
        modifier = modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceVariant,
        shadowElevation = 4.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 14.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.nova_log_analysis_title),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = stringResource(findingTitleRes(primary.ruleId)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            TextButton(onClick = { showDetails = true }) {
                Text(text = stringResource(R.string.nova_log_analysis_details))
            }
        }
    }

    if (showDetails) {
        SimpleAlertDialog(
            title = stringResource(R.string.nova_log_analysis_title),
            text = { LogAnalysisDetails(analysis) },
            confirmText = stringResource(R.string.generic_close),
            onDismiss = { showDetails = false }
        )
    }
}

@Composable
private fun LogAnalysisDetails(analysis: LogAnalysis) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        val versions = listOfNotNull(
            analysis.mcVersion?.let { "Minecraft $it" },
            analysis.javaVersion?.let { "Java $it" }
        )
        if (versions.isNotEmpty()) {
            Text(
                text = versions.joinToString(" • "),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (analysis.truncated) {
            Text(
                text = stringResource(R.string.nova_log_analysis_truncated),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.tertiary
            )
        }

        for (finding in analysis.findings) {
            FindingBlock(finding)
        }

        if (analysis.rootCauses.isNotEmpty()) {
            Text(
                text = stringResource(R.string.nova_log_analysis_root_causes),
                style = MaterialTheme.typography.titleSmall
            )
            for (cause in analysis.rootCauses) {
                Text(
                    text = "• $cause",
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}

@Composable
private fun FindingBlock(finding: LogFinding) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = stringResource(findingTitleRes(finding.ruleId)),
            style = MaterialTheme.typography.titleSmall
        )
        Text(
            text = stringResource(severityRes(finding.severity)),
            style = MaterialTheme.typography.labelSmall,
            color = severityColor(finding.severity)
        )

        if (finding.evidence.isNotEmpty()) {
            Text(
                text = stringResource(R.string.nova_log_analysis_evidence),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            for (evidence in finding.evidence) {
                Text(
                    text = stringResource(
                        R.string.nova_log_analysis_evidence_line,
                        evidence.lineNumber,
                        evidence.text
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace
                )
            }
        }

        if (finding.advice.isNotEmpty()) {
            Text(
                text = stringResource(R.string.nova_log_analysis_advice),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            for (advice in finding.advice) {
                Text(
                    text = "• " + stringResource(adviceRes(advice)),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
private fun severityColor(severity: LogSeverity) = when (severity) {
    LogSeverity.FATAL -> MaterialTheme.colorScheme.error
    LogSeverity.ERROR -> MaterialTheme.colorScheme.tertiary
    LogSeverity.WARNING -> MaterialTheme.colorScheme.onSurfaceVariant
}

private fun severityRes(severity: LogSeverity) = when (severity) {
    LogSeverity.FATAL -> R.string.nova_log_severity_fatal
    LogSeverity.ERROR -> R.string.nova_log_severity_error
    LogSeverity.WARNING -> R.string.nova_log_severity_warning
}

private fun findingTitleRes(ruleId: String) = when (ruleId) {
    "heap_oom" -> R.string.nova_log_rule_heap_oom
    "native_oom" -> R.string.nova_log_rule_native_oom
    "heap_too_large" -> R.string.nova_log_rule_heap_too_large
    "renderer_context" -> R.string.nova_log_rule_renderer_context
    "renderer_native" -> R.string.nova_log_rule_renderer_native
    "mod_missing_dependency" -> R.string.nova_log_rule_mod_missing_dependency
    "mod_mixin_failure" -> R.string.nova_log_rule_mod_mixin_failure
    "mod_duplicate" -> R.string.nova_log_rule_mod_duplicate
    "java_version" -> R.string.nova_log_rule_java_version
    "java_main_class" -> R.string.nova_log_rule_java_main_class
    "network_failure" -> R.string.nova_log_rule_network_failure
    "storage_failure" -> R.string.nova_log_rule_storage_failure
    "account_failure" -> R.string.nova_log_rule_account_failure
    "native_crash" -> R.string.nova_log_rule_native_crash
    "crash_report" -> R.string.nova_log_rule_crash_report
    else -> R.string.nova_log_rule_unknown
}

private fun adviceRes(advice: LogAdvice) = when (advice) {
    LogAdvice.INCREASE_MEMORY -> R.string.nova_log_advice_increase_memory
    LogAdvice.LOWER_MEMORY -> R.string.nova_log_advice_lower_memory
    LogAdvice.FREE_MEMORY -> R.string.nova_log_advice_free_memory
    LogAdvice.SWITCH_RENDERER -> R.string.nova_log_advice_switch_renderer
    LogAdvice.LOWER_GRAPHICS -> R.string.nova_log_advice_lower_graphics
    LogAdvice.INSTALL_MISSING_MODS -> R.string.nova_log_advice_install_missing_mods
    LogAdvice.REMOVE_CONFLICTING_MODS -> R.string.nova_log_advice_remove_conflicting_mods
    LogAdvice.UPDATE_MODS -> R.string.nova_log_advice_update_mods
    LogAdvice.UPDATE_JAVA -> R.string.nova_log_advice_update_java
    LogAdvice.VERIFY_GAME_FILES -> R.string.nova_log_advice_verify_game_files
    LogAdvice.CHECK_NETWORK -> R.string.nova_log_advice_check_network
    LogAdvice.CHECK_STORAGE -> R.string.nova_log_advice_check_storage
    LogAdvice.SIGN_IN_AGAIN -> R.string.nova_log_advice_sign_in_again
    LogAdvice.READ_CRASH_REPORT -> R.string.nova_log_advice_read_crash_report
}
