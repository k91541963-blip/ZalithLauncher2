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

package com.movtery.zalithlauncher.ui.screens.content.versions.elements

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
import androidx.compose.ui.unit.dp
import com.movtery.zalithlauncher.R
import com.movtery.zalithlauncher.game.version.mod.dependency.DependencyIssue
import com.movtery.zalithlauncher.game.version.mod.dependency.DependencyProblem
import com.movtery.zalithlauncher.ui.components.SimpleAlertDialog

/**
 * Warning strip above the mod list when declared dependencies cannot be satisfied.
 *
 * The strip only counts problems; the dialog lists each one with the mod that needs it, so
 * the user can decide whether to install, enable or update something. Nothing is changed
 * automatically.
 */
@Composable
fun ModDependencyBanner(
    issues: List<DependencyIssue>,
    modifier: Modifier = Modifier
) {
    if (issues.isEmpty()) return
    var showDetails by remember { mutableStateOf(false) }

    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 14.dp, top = 6.dp, bottom = 6.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.nova_mod_dep_title),
                    style = MaterialTheme.typography.labelSmall
                )
                Text(
                    text = stringResource(R.string.nova_mod_dep_summary, issues.size),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            TextButton(onClick = { showDetails = true }) {
                Text(text = stringResource(R.string.nova_mod_dep_details))
            }
        }
    }

    if (showDetails) {
        SimpleAlertDialog(
            title = stringResource(R.string.nova_mod_dep_title),
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = stringResource(R.string.nova_mod_dep_note),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    for (issue in issues) {
                        Text(
                            text = issueText(issue),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            },
            confirmText = stringResource(R.string.generic_close),
            onDismiss = { showDetails = false }
        )
    }
}

@Composable
private fun issueText(issue: DependencyIssue): String {
    val ranges = issue.requiredRanges.joinToString(" / ")
    return when (issue.problem) {
        DependencyProblem.MISSING -> stringResource(
            R.string.nova_mod_dep_missing,
            issue.modName,
            issue.dependencyId
        )

        DependencyProblem.DISABLED -> stringResource(
            R.string.nova_mod_dep_disabled,
            issue.modName,
            issue.dependencyId
        )

        DependencyProblem.VERSION_MISMATCH -> stringResource(
            R.string.nova_mod_dep_version,
            issue.modName,
            issue.dependencyId,
            ranges.ifEmpty { "*" },
            issue.installedVersion ?: stringResource(R.string.nova_mod_dep_unknown_version)
        )
    }
}
