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

package com.movtery.zalithlauncher.game.performance

enum class ComparisonVerdict {
    BETTER,
    WORSE,
    MIXED,
    SAME
}

/**
 * Two measured sessions, ordered by end time, with the deltas the comparison card shows.
 *
 * Only values that were really measured are compared. A "better" verdict needs the
 * average FPS to rise and jank not to rise; a difference inside the tolerances counts as
 * "same" so ordinary run-to-run noise is not reported as an improvement.
 */
data class SessionComparison(
    val older: SessionHistoryEntry,
    val newer: SessionHistoryEntry
) {
    companion object {
        const val FPS_TOLERANCE = 1.0
        const val JANK_TOLERANCE = 0.5

        /** Orders the pair by end time so "older" and "newer" are always truthful. */
        fun of(first: SessionHistoryEntry, second: SessionHistoryEntry): SessionComparison =
            if (first.endedAtEpochMs <= second.endedAtEpochMs) {
                SessionComparison(older = first, newer = second)
            } else {
                SessionComparison(older = second, newer = first)
            }
    }

    val fpsDelta: Double get() = newer.averageFps - older.averageFps
    val onePercentLowDelta: Double get() = newer.onePercentLowFps - older.onePercentLowFps
    val jankDelta: Double get() = newer.jankPercent - older.jankPercent
    val durationDeltaSeconds: Int get() = newer.durationSeconds - older.durationSeconds

    val verdict: ComparisonVerdict
        get() {
            val fpsScore = score(fpsDelta, FPS_TOLERANCE)
            val jankScore = -score(jankDelta, JANK_TOLERANCE)
            val scores = listOf(fpsScore, jankScore)
            return when {
                scores.all { it > 0 } -> ComparisonVerdict.BETTER
                scores.all { it < 0 } -> ComparisonVerdict.WORSE
                scores.all { it == 0 } -> ComparisonVerdict.SAME
                else -> ComparisonVerdict.MIXED
            }
        }

    private fun score(delta: Double, tolerance: Double): Int = when {
        delta > tolerance -> 1
        delta < -tolerance -> -1
        else -> 0
    }
}
