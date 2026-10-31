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
 * Matches mod versions against the range syntax used by Fabric/Quilt and Forge metadata.
 *
 * Supported forms:
 *  - empty, `*`            -> any version
 *  - `1.20.1`              -> exactly that version
 *  - `1.20.x`, `1.20.*`    -> any patch of 1.20
 *  - `>=1.20 <1.21`        -> space separated predicates, all must hold
 *  - `^1.20.1`, `~1.20.1`  -> caret/tilde ranges
 *  - `[1.20,1.21)`         -> Maven/Forge bracket range
 *
 * Versions are compared segment by segment, numbers numerically and text lexically, so
 * `1.21` < `1.21.4` and `1.21.4-rc1` < `1.21.4`.
 */
object VersionRange {

    fun satisfies(version: String?, range: String?): Boolean {
        if (range.isNullOrBlank() || range.trim() == "*") return true
        if (version.isNullOrBlank()) return false

        val trimmed = range.trim()
        if (trimmed.startsWith("[") || trimmed.startsWith("(")) {
            return satisfiesBracket(version.trim(), trimmed)
        }

        return trimmed.split(WHITESPACE).all { predicate ->
            satisfiesPredicate(version.trim(), predicate)
        }
    }

    /** Returns true when any of [ranges] accepts [version]; an empty list accepts everything. */
    fun satisfiesAny(version: String?, ranges: List<String>): Boolean {
        if (ranges.isEmpty()) return true
        return ranges.any { satisfies(version, it) }
    }

    fun compare(a: String, b: String): Int {
        val partsA = tokenize(a)
        val partsB = tokenize(b)
        val size = maxOf(partsA.size, partsB.size)

        for (index in 0 until size) {
            val partA = partsA.getOrNull(index)
            val partB = partsB.getOrNull(index)
            if (partA == null && partB == null) return 0
            if (partA == null) {
                // "1.21.4" vs "1.21.4-rc1": a release is newer than its pre-release
                return if (partB!!.isNumeric()) -1 else 1
            }
            if (partB == null) {
                return if (partA.isNumeric()) 1 else -1
            }
            val result = comparePart(partA, partB)
            if (result != 0) return result
        }
        return 0
    }

    private fun satisfiesPredicate(version: String, predicate: String): Boolean {
        val token = predicate.trim()
        if (token.isEmpty() || token == "*" || token.equals("x", ignoreCase = true)) return true

        val operator = OPERATORS.firstOrNull { token.startsWith(it) }
        val raw = if (operator != null) token.substring(operator.length).trim() else token
        if (raw.isEmpty() || raw == "*" || raw.equals("x", ignoreCase = true)) return true

        return when (operator) {
            ">=" -> compare(version, raw) >= 0
            "<=" -> compare(version, raw) <= 0
            ">" -> compare(version, raw) > 0
            "<" -> compare(version, raw) < 0
            "=" -> compare(version, raw) == 0
            "^" -> inRange(version, raw, caretUpperBound(raw))
            "~" -> inRange(version, raw, tildeUpperBound(raw))
            else -> matchesWildcard(version, raw)
        }
    }

    private fun matchesWildcard(version: String, raw: String): Boolean {
        val suffix = when {
            raw.endsWith(".x", ignoreCase = true) -> raw.dropLast(2)
            raw.endsWith(".*") -> raw.dropLast(2)
            else -> return compare(version, raw) == 0
        }
        return version == suffix || version.startsWith("$suffix.")
    }

    private fun inRange(version: String, lower: String, upper: String): Boolean {
        return compare(version, lower) >= 0 && compare(version, upper) < 0
    }

    private fun caretUpperBound(raw: String): String {
        val parts = tokenize(raw)
        val major = parts.getOrNull(0)?.toLongOrNull() ?: 0L
        val minor = parts.getOrNull(1)?.toLongOrNull() ?: 0L
        return if (major > 0L) {
            "${major + 1}.0.0"
        } else {
            "0.${minor + 1}.0"
        }
    }

    private fun tildeUpperBound(raw: String): String {
        val parts = tokenize(raw)
        val major = parts.getOrNull(0)?.toLongOrNull() ?: 0L
        val minor = parts.getOrNull(1)?.toLongOrNull() ?: 0L
        return "$major.${minor + 1}.0"
    }

    private fun satisfiesBracket(version: String, range: String): Boolean {
        val lowerInclusive = range.startsWith("[")
        val upperInclusive = range.endsWith("]")
        val body = range.substring(1, range.length - 1)
        val parts = body.split(",")
        val lower = parts.getOrNull(0)?.trim().orEmpty()
        val upper = parts.getOrNull(1)?.trim().orEmpty()

        if (lower.isNotEmpty()) {
            val result = compare(version, lower)
            if (result < 0 || (result == 0 && !lowerInclusive)) return false
        }
        if (upper.isNotEmpty()) {
            val result = compare(version, upper)
            if (result > 0 || (result == 0 && !upperInclusive)) return false
        }
        return true
    }

    private fun comparePart(a: String, b: String): Int {
        val numberA = a.toLongOrNull()
        val numberB = b.toLongOrNull()
        return when {
            numberA != null && numberB != null -> numberA.compareTo(numberB)
            numberA != null -> 1
            numberB != null -> -1
            else -> a.compareTo(b, ignoreCase = true)
        }
    }

    private fun tokenize(version: String): List<String> {
        return version
            .substringBefore('+')
            .split(SEPARATORS)
            .filter { it.isNotEmpty() }
    }

    private fun String.isNumeric(): Boolean = toLongOrNull() != null

    private val WHITESPACE = Regex("""\s+""")
    private val SEPARATORS = Regex("""[.\-]""")
    private val OPERATORS = listOf(">=", "<=", ">", "<", "=", "^", "~")
}
