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

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.movtery.zalithlauncher.R
import com.movtery.zalithlauncher.game.home.HomeWidgetInput
import com.movtery.zalithlauncher.game.version.installed.VersionsManager
import com.movtery.zalithlauncher.ui.screens.content.elements.VersionIconImage
import com.movtery.zalithlauncher.ui.screens.content.home.server.ServerCardManager
import com.movtery.zalithlauncher.ui.screens.content.home.version.VersionCardManager

private enum class WidgetKind { VERSION, SERVER }

/**
 * AetherUI 3.0 — widget gallery.
 *
 * Adds a real widget to the home grid: a version card (already installed versions) or a
 * server card (name + address). Duplicates are rejected by the managers themselves, and
 * the message shown is the manager's real result — not a cosmetic "success".
 */
@Composable
fun HomeWidgetGallery(onDismiss: () -> Unit) {
    var kind by remember { mutableStateOf(WidgetKind.VERSION) }
    var versionName by remember { mutableStateOf("") }
    var serverName by remember { mutableStateOf("") }
    var serverAddress by remember { mutableStateOf("") }
    var feedback by remember { mutableStateOf<String?>(null) }

    val duplicateMessage = stringResource(R.string.home_widget_already_added)
    val invalidAddressMessage = stringResource(R.string.home_widget_invalid_ip)

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.onSurface
        ) {
            Column(
                modifier = Modifier.padding(all = 18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = stringResource(R.string.home_widget_add_title),
                    style = MaterialTheme.typography.titleMedium
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    WidgetKindTab(
                        modifier = Modifier.weight(1f),
                        selected = kind == WidgetKind.VERSION,
                        text = stringResource(R.string.home_widget_version_card),
                        onClick = {
                            kind = WidgetKind.VERSION
                            feedback = null
                        }
                    )
                    WidgetKindTab(
                        modifier = Modifier.weight(1f),
                        selected = kind == WidgetKind.SERVER,
                        text = stringResource(R.string.home_widget_server_card),
                        onClick = {
                            kind = WidgetKind.SERVER
                            feedback = null
                        }
                    )
                }

                when (kind) {
                    WidgetKind.VERSION -> VersionPicker(
                        selected = versionName,
                        onSelect = { versionName = it },
                        onAdd = { name ->
                            val version = VersionsManager.versions.value
                                .firstOrNull { it.getVersionName() == name }
                            // addCard writes the record; the grid follows through its collector.
                            feedback = if (version != null && VersionCardManager.addCard(version)) {
                                onDismiss()
                                null
                            } else {
                                duplicateMessage
                            }
                        }
                    )

                    WidgetKind.SERVER -> ServerInput(
                        name = serverName,
                        address = serverAddress,
                        onNameChange = { serverName = it },
                        onAddressChange = { serverAddress = it },
                        onAdd = {
                            val address = HomeWidgetInput.normalizeAddress(serverAddress)
                            if (address == null) {
                                feedback = invalidAddressMessage
                            } else if (
                                ServerCardManager.addCard(
                                    name = HomeWidgetInput.normalizeName(serverName, address),
                                    serverIp = address
                                )
                            ) {
                                onDismiss()
                            } else {
                                feedback = duplicateMessage
                            }
                        }
                    )
                }

                feedback?.let { message ->
                    Text(
                        modifier = Modifier.alpha(0.8f),
                        text = message,
                        style = MaterialTheme.typography.labelSmall
                    )
                }

                Row(modifier = Modifier.fillMaxWidth()) {
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = onDismiss) {
                        Text(text = stringResource(R.string.generic_cancel))
                    }
                }
            }
        }
    }
}

@Composable
private fun WidgetKindTab(
    selected: Boolean,
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        shape = MaterialTheme.shapes.large,
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerHighest
        },
        contentColor = if (selected) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.onSurface
        }
    ) {
        Text(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            text = text,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun VersionPicker(
    selected: String,
    onSelect: (String) -> Unit,
    onAdd: (String) -> Unit
) {
    val versions by VersionsManager.versions.collectAsStateWithLifecycle()
    val usable = remember(versions) { versions.filter { it.isValid() } }

    if (usable.isEmpty()) {
        Text(
            modifier = Modifier.alpha(0.7f),
            text = stringResource(R.string.home_widget_no_versions),
            style = MaterialTheme.typography.labelSmall
        )
        return
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 260.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        items(usable, key = { it.getVersionName() }) { version ->
            val name = version.getVersionName()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelect(name) }
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                VersionIconImage(
                    modifier = Modifier.size(26.dp),
                    version = version
                )
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = name,
                        color = if (selected == name) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            Color.Unspecified
                        },
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        modifier = Modifier.alpha(0.65f),
                        text = version.getVersionSummary(),
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }

    Button(
        modifier = Modifier.fillMaxWidth(),
        enabled = selected.isNotBlank(),
        onClick = { onAdd(selected) }
    ) {
        Text(text = stringResource(R.string.home_widget_add_action))
    }
}

@Composable
private fun ServerInput(
    name: String,
    address: String,
    onNameChange: (String) -> Unit,
    onAddressChange: (String) -> Unit,
    onAdd: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = name,
            onValueChange = onNameChange,
            singleLine = true,
            label = { Text(text = stringResource(R.string.home_widget_server_name)) }
        )
        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = address,
            onValueChange = onAddressChange,
            singleLine = true,
            label = { Text(text = stringResource(R.string.home_widget_server_ip)) }
        )
        Button(
            modifier = Modifier.fillMaxWidth(),
            enabled = address.isNotBlank(),
            onClick = onAdd
        ) {
            Text(text = stringResource(R.string.home_widget_add_action))
        }
    }
}
