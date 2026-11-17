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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Frame time analysis must be exact: these numbers are shown to the user as measurements. */
class FrameTimeStatsTest {

    @Test
    fun `empty input has no statistics`() {
        assertNull(FrameTimeMetrics.compute(IntArray(0)))
    }

    @Test
    fun `implausible samples are rejected`() {
        // Zero, negative and multi-second gaps (pause/loading) must not become "1 FPS".
        assertNull(FrameTimeMetrics.compute(intArrayOf(0, -5, 2_000_000)))
    }

    @Test
    fun `steady sixty fps reports sixty`() {
        val frames = IntArray(600) { 16_667 }
        val stats = FrameTimeMetrics.compute(frames) ?: error("expected statistics")

        assertEquals(600, stats.sampleCount)
        assertEquals(60.0, stats.averageFps, 0.01)
        assertEquals(60.0, stats.onePercentLowFps, 0.01)
        assertEquals(16.667, stats.averageFrameMs, 0.001)
        assertEquals(16.667, stats.p99FrameMs, 0.001)
        assertEquals(0.0, stats.jankPercent, 0.001)
    }

    @Test
    fun `one percent low uses the slowest frames`() {
        // 99 frames at 10 ms and one 50 ms frame.
        val frames = IntArray(100) { index -> if (index == 99) 50_000 else 10_000 }
        val stats = FrameTimeMetrics.compute(frames) ?: error("expected statistics")

        assertEquals(50.0, stats.worstFrameMs, 0.001)
        // The slowest 1% (one frame) averages 50 ms, so the 1% low is 20 FPS.
        assertEquals(20.0, stats.onePercentLowFps, 0.001)
        // Nearest-rank P99 of 100 samples is the second slowest frame.
        assertEquals(10.0, stats.p99FrameMs, 0.001)
        assertEquals(1.0, stats.jankPercent, 0.001)
    }

    @Test
    fun `jank counts frames slower than thirty fps`() {
        val frames = IntArray(90) { 16_667 } + IntArray(10) { 50_000 }
        val stats = FrameTimeMetrics.compute(frames) ?: error("expected statistics")

        assertEquals(10.0, stats.jankPercent, 0.001)
        assertTrue(stats.averageFps in 40.0..50.0)
    }

    @Test
    fun `average fps is the harmonic mean of frame times`() {
        // 10 ms + 20 ms -> mean frame time 15 ms -> 66.7 FPS (not the 75 FPS arithmetic
        // mean of 100 FPS and 50 FPS).
        val stats = FrameTimeMetrics.compute(intArrayOf(10_000, 20_000))
            ?: error("expected statistics")
        assertEquals(66.67, stats.averageFps, 0.01)

        val mixed = FrameTimeMetrics.compute(intArrayOf(10_000, 10_000, 20_000))
            ?: error("expected statistics")
        assertEquals(1000.0 / 13.3333, mixed.averageFps, 0.01)
    }

    @Test
    fun `percentile endpoints clamp`() {
        val ascending = listOf(1.0, 2.0, 3.0, 4.0)
        assertEquals(1.0, FrameTimeMetrics.percentile(ascending, 0.0), 0.0)
        assertEquals(4.0, FrameTimeMetrics.percentile(ascending, 100.0), 0.0)
        assertEquals(0.0, FrameTimeMetrics.percentile(emptyList(), 99.0), 0.0)
    }
}
