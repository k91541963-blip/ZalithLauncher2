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

package com.movtery.zalithlauncher.game.version.mod.dependency

/**
 * Reads Forge/NeoForge dependency declarations from the parsed TOML map of
 * `META-INF/mods.toml` or `META-INF/neoforge.mods.toml`.
 *
 * Supported shapes:
 * - `[[dependencies.<ownerModId>]]` (modern Forge and NeoForge)
 * - `[[dependencies]]` (legacy global table)
 *
 * Forge uses `mandatory = true|false`; NeoForge uses `type = "required"|"optional"|...`.
 * Server-only dependencies are skipped because the launcher runs the client.
 */
object ForgeDependencyParser {

    @Suppress("UNCHECKED_CAST")
    fun parse(tomlMap: Map<String, Any?>, ownerModId: String): List<ModDependency> {
        val root = tomlMap["dependencies"] ?: return emptyList()

        val tables: List<Map<*, *>> = when (root) {
            is Map<*, *> -> when (val own = root[ownerModId]) {
                is List<*> -> own.filterIsInstance<Map<*, *>>()
                is Map<*, *> -> listOf(own)
                else -> emptyList()
            }
            is List<*> -> root.filterIsInstance<Map<*, *>>()
            else -> emptyList()
        }

        return tables.mapNotNull { parseDependency(it) }
    }

    private fun parseDependency(table: Map<*, *>): ModDependency? {
        val id = (table["modId"] as? String)?.trim().orEmpty()
        if (id.isEmpty()) return null

        // The launcher only runs the client; server-only requirements are irrelevant.
        val side = table["side"] as? String
        if (side.equals("SERVER", ignoreCase = true)) return null

        val type = table["type"] as? String
        // These are conflicts/warnings, not missing requirements.
        if (type.equals("incompatible", ignoreCase = true) ||
            type.equals("discouraged", ignoreCase = true)
        ) {
            return null
        }

        val mandatory = table["mandatory"]
        val required = when {
            mandatory is Boolean -> mandatory
            type != null -> type.equals("required", ignoreCase = true)
            else -> true
        }

        val range = (table["versionRange"] as? String)
            ?.trim()
            ?.takeIf { it.isNotEmpty() && it != "*" }

        return ModDependency(
            id = id,
            versionRanges = listOfNotNull(range),
            required = required
        )
    }
}
