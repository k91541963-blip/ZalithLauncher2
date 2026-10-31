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

package com.movtery.zalithlauncher.ui.screens.main

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.movtery.zalithlauncher.R
import com.movtery.zalithlauncher.game.diagnostics.LogAnalysis
import com.movtery.zalithlauncher.game.diagnostics.LogAnalyzer
import com.movtery.zalithlauncher.ui.components.SimpleAlertDialog
import com.movtery.zalithlauncher.ui.screens.content.LogAnalysisDetails
import com.movtery.zalithlauncher.ui.screens.content.adviceRes
import com.movtery.zalithlauncher.ui.screens.content.findingTitleRes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.RandomAccessFile

/** Crash logs are read from the end; the failure is almost always in the last part. */
private const val MAX_CRASH_LOG_BYTES = 1L * 1024 * 1024

/**
 * Shows the analyzer's conclusion on the crash screen.
 *
 * The launcher never claims to know why the game crashed: it lists the first findings with
 * the advice that matches them and keeps the raw log one tap away. When nothing is
 * recognized the section stays hidden instead of guessing.
 */
@Composable
fun CrashAnalysisSection(
    logFile: File,
    modifier: Modifier = Modifier
) {
    var analysis by remember(logFile) { mutableStateOf<LogAnalysis?>(null) }
    var showDetails by remember { mutableStateOf(false) }

    LaunchedEffect(logFile) {
        analysis = withContext(Dispatchers.IO) {
            runCatching {
                LogAnalyzer.analyze(readLogTail(logFile))
            }.getOrNull()
        }
    }

    val result = analysis ?: return
    val findings = result.findings.take(3)
    if (findings.isEmpty()) return

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = stringResource(R.string.nova_crash_analysis_title),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary
        )
        for (finding in findings) {
            Text(
                text = "• " + stringResource(findingTitleRes(finding.ruleId)),
                style = MaterialTheme.typography.bodyMedium
            )
            finding.advice.firstOrNull()?.let { advice ->
                Text(
                    text = stringResource(adviceRes(advice)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        TextButton(onClick = { showDetails = true }) {
            Text(text = stringResource(R.string.nova_log_analysis_details))
        }
    }

    if (showDetails) {
        SimpleAlertDialog(
            title = stringResource(R.string.nova_log_analysis_title),
            text = { LogAnalysisDetails(result) },
            confirmText = stringResource(R.string.generic_close),
            onDismiss = { showDetails = false }
        )
    }
}

/**
 * Reads at most [MAX_CRASH_LOG_BYTES] from the end of [file].
 *
 * Truncated multi byte characters at the cut are dropped, and the first partial line is
 * skipped so the analyzer never sees a broken line.
 */
private fun readLogTail(file: File): String {
    if (!file.isFile) return ""
    val size = file.length()
    if (size <= 0) return ""
    if (size <= MAX_CRASH_LOG_BYTES) return runCatching { file.readText() }.getOrDefault("")

    return runCatching {
        RandomAccessFile(file, "r").use { raf ->
            raf.seek(size - MAX_CRASH_LOG_BYTES)
            val bytes = ByteArray(MAX_CRASH_LOG_BYTES.toInt())
            raf.readFully(bytes)
            val text = String(bytes, Charsets.UTF_8).trimStart('\uFFFD')
            val firstNewline = text.indexOf('\n')
            if (firstNewline >= 0 && firstNewline < text.length - 1) {
                text.substring(firstNewline + 1)
            } else {
                text
            }
        }
    }.getOrDefault("")
}
