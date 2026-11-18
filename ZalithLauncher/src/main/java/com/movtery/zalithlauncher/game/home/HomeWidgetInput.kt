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

package com.movtery.zalithlauncher.game.home

/**
 * Validation for the widget gallery inputs.
 *
 * The launcher only checks what it can know for sure: the address is a single token
 * (`host` or `host:port`). It does not pretend to verify DNS or reachability here —
 * server cards ping the address themselves and show the real result.
 */
object HomeWidgetInput {

    const val MAX_ADDRESS_LENGTH = 255

    /** Trimmed address, or `null` when the input cannot be a server address. */
    fun normalizeAddress(raw: String): String? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty() || trimmed.length > MAX_ADDRESS_LENGTH) return null
        if (trimmed.any { it.isWhitespace() }) return null
        if (trimmed.contains("://")) return null
        if (trimmed.startsWith(":") || trimmed.endsWith(":")) return null
        if (trimmed.count { it == ':' } > 1 && !trimmed.startsWith("[")) return null
        return trimmed
    }

    /** Display name: trimmed input, falling back to the address when left empty. */
    fun normalizeName(raw: String, address: String): String =
        raw.trim().ifBlank { address }
}
