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

import org.junit.Assert.assertEquals
import org.junit.Test

class SessionComparisonTest {

    private fun entry(
        endedAt: Long,
        fps: Double,
        low: Double = fps * 0.8,
        jank: Double = 3.0,
        duration: Int = 600
    ) = SessionHistoryEntry(
        versionName = "1.21.4",
        rendererMode = "aether",
        startedAtEpochMs = endedAt - duration * 1_000L,
        endedAtEpochMs = endedAt,
        durationSeconds = duration,
        framesCaptured = (fps * duration).toInt(),
        averageFps = fps,
        onePercentLowFps = low,
        p99FrameMs = 1000.0 / fps,
        jankPercent = jank
    )

    @Test
    fun `pair is ordered by end time`() {
        val first = entry(2_000L, fps = 50.0)
        val second = entry(1_000L, fps = 60.0)

        val comparison = SessionComparison.of(first, second)
        assertEquals(1_000L, comparison.older.endedAtEpochMs)
        assertEquals(2_000L, comparison.newer.endedAtEpochMs)

        val reversed = SessionComparison.of(second, first)
        assertEquals(comparison.older, reversed.older)
        assertEquals(comparison.newer, reversed.newer)
    }

    @Test
    fun `higher fps and lower jank is better`() {
        val comparison = SessionComparison(
            older = entry(1_000L, fps = 50.0, jank = 8.0),
            newer = entry(2_000L, fps = 60.0, jank = 3.0)
        )
        assertEquals(ComparisonVerdict.BETTER, comparison.verdict)
        assertEquals(10.0, comparison.fpsDelta, 0.0001)
        assertEquals(-5.0, comparison.jankDelta, 0.0001)
    }

    @Test
    fun `lower fps and higher jank is worse`() {
        val comparison = SessionComparison(
            older = entry(1_000L, fps = 60.0, jank = 3.0),
            newer = entry(2_000L, fps = 48.0, jank = 9.0)
        )
        assertEquals(ComparisonVerdict.WORSE, comparison.verdict)
    }

    @Test
    fun `one metric up and the other down is mixed`() {
        val betterFps = SessionComparison(
            older = entry(1_000L, fps = 50.0, jank = 3.0),
            newer = entry(2_000L, fps = 60.0, jank = 9.0)
        )
        assertEquals(ComparisonVerdict.MIXED, betterFps.verdict)

        val betterJank = SessionComparison(
            older = entry(1_000L, fps = 50.0, jank = 9.0),
            newer = entry(2_000L, fps = 49.0, jank = 3.0)
        )
        assertEquals(ComparisonVerdict.MIXED, betterJank.verdict)
    }

    @Test
    fun `differences inside tolerance count as same`() {
        val comparison = SessionComparison(
            older = entry(1_000L, fps = 60.0, jank = 4.0),
            newer = entry(2_000L, fps = 60.8, jank = 4.3)
        )
        assertEquals(ComparisonVerdict.SAME, comparison.verdict)
    }

    @Test
    fun `delta beyond tolerance is not same`() {
        val comparison = SessionComparison(
            older = entry(1_000L, fps = 60.0, jank = 4.0),
            newer = entry(2_000L, fps = 61.5, jank = 4.0)
        )
        // FPS alone improved; jank is unchanged, so the verdict is mixed by design.
        assertEquals(ComparisonVerdict.MIXED, comparison.verdict)
    }
}
