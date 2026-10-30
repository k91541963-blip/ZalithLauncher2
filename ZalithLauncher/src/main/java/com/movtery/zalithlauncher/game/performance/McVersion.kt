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

package com.movtery.zalithlauncher.game.performance

/**
 * Lightweight Minecraft version comparison.
 *
 * Handles versions such as `1.21.4`, `1.21`, `26.3-snapshot-3` and `1.20.1-forge-47.2.0`
 * by comparing only the leading numeric part. Non-numeric suffixes are ignored on purpose:
 * a modded version string shares the compatibility range of its base game version.
 */
object McVersion {

    /**
     * Extracts the numeric prefix of a version string, e.g. `26.3-snapshot-3` -> `[26, 3]`.
     * Returns an empty list when the string does not start with a digit.
     */
    fun numericParts(version: String?): List<Int> {
        val raw = version?.trim().orEmpty()
        if (raw.isEmpty()) return emptyList()

        val prefix = buildString {
            for (char in raw) {
                if (char.isDigit() || char == '.') append(char) else break
            }
        }
        if (prefix.isEmpty()) return emptyList()

        return prefix.split('.')
            .map { it.toIntOrNull() ?: 0 }
            .dropLastWhile { it == 0 && prefix.endsWith('.') }
    }

    /**
     * Compares two version strings by their numeric parts.
     * @return negative when [a] is older, zero when equal, positive when [a] is newer.
     */
    fun compare(a: String?, b: String?): Int {
        val left = numericParts(a)
        val right = numericParts(b)

        // An unparsable version cannot be ordered; treat it as equal so callers do not
        // reject a renderer or profile based on a value we could not understand.
        if (left.isEmpty() || right.isEmpty()) return 0

        val size = maxOf(left.size, right.size)
        for (index in 0 until size) {
            val l = left.getOrElse(index) { 0 }
            val r = right.getOrElse(index) { 0 }
            if (l != r) return l.compareTo(r)
        }
        return 0
    }

    /** True when [version] is inside the inclusive range defined by [min] and [max]. */
    fun isInRange(version: String?, min: String?, max: String?): Boolean {
        if (version.isNullOrBlank()) return true
        if (min != null && compare(version, min) < 0) return false
        if (max != null && compare(version, max) > 0) return false
        return true
    }
}
