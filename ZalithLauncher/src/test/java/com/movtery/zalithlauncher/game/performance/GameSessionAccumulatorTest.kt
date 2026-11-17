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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Session aggregation: every reported value must trace back to a measured sample. */
class GameSessionAccumulatorTest {

    private fun memory(usedMb: Int, totalMb: Int = 4096): MemorySnapshot = MemorySnapshot(
        totalMb = totalMb,
        availableMb = (totalMb - usedMb).coerceAtLeast(0),
        lowMemory = false,
        appPssMb = null,
        javaHeapUsedMb = 0,
        javaHeapMaxMb = 0
    )

    @Test
    fun `no frames means no summary`() {
        val accumulator = GameSessionAccumulator("1.21.4", "balanced", 1_000L)
        accumulator.addDeviceSample(50f, memory(2_048), 44f)
        assertNull(accumulator.buildSummary(61_000L))
    }

    @Test
    fun `frame times and device samples are aggregated`() {
        val accumulator = GameSessionAccumulator("1.21.4", "balanced", 1_000L)
        val frames = IntArray(600) { 16_667 }
        accumulator.addFrameTimes(frames, frames.size)

        accumulator.addDeviceSample(40f, memory(2_048), 45f)
        accumulator.addDeviceSample(60f, memory(3_072), 50f)
        // Missing readings must not be invented or skewed by a zero.
        accumulator.addDeviceSample(null, memory(2_560), null)

        val summary = accumulator.buildSummary(61_000L) ?: error("expected a summary")

        assertEquals("1.21.4", summary.versionName)
        assertEquals("balanced", summary.rendererMode)
        assertEquals(60, summary.durationSeconds)
        assertEquals(600, summary.framesCaptured)
        assertEquals(60.0, summary.averageFps, 0.01)
        assertEquals(60.0, summary.onePercentLowFps, 0.01)
        assertEquals(16.667, summary.p99FrameMs, 0.001)
        assertEquals(0.0, summary.jankPercent, 0.001)
        assertEquals(50.0, summary.averageCpuPercent!!, 0.001)
        assertEquals(3_072, summary.peakRamUsedMb)
        assertEquals(4_096, summary.totalRamMb)
        assertEquals(50f, summary.peakTemperatureCelsius!!, 0.001f)
        assertFalse(summary.samplesTruncated)
        assertEquals(1, summary.fpsBuckets.size)
        assertEquals(60.0, summary.fpsBuckets.first(), 0.1)
    }

    @Test
    fun `missing device readings stay null`() {
        val accumulator = GameSessionAccumulator("1.21.4", "native", 0L)
        val frames = IntArray(120) { 8_333 }
        accumulator.addFrameTimes(frames, frames.size)
        accumulator.addDeviceSample(null, memory(0, totalMb = 0), null)

        val summary = accumulator.buildSummary(10_000L) ?: error("expected a summary")
        assertNull(summary.averageCpuPercent)
        assertNull(summary.peakTemperatureCelsius)
        assertEquals(0, summary.totalRamMb)
        assertEquals(120.0, summary.averageFps, 0.5)
    }

    @Test
    fun `frame storage is bounded and flagged`() {
        val accumulator = GameSessionAccumulator("1.21.4", "lite", 0L, maxStoredFrames = 10)
        val frames = IntArray(50) { 16_667 }
        accumulator.addFrameTimes(frames, frames.size)

        val summary = accumulator.buildSummary(2_000L) ?: error("expected a summary")
        assertEquals(10, summary.framesCaptured)
        assertTrue(summary.samplesTruncated)
    }

    @Test
    fun `fps buckets split at ten second windows and flush on exit`() {
        val accumulator = GameSessionAccumulator("1.21.4", "balanced", 0L)
        // 1000 frames x 10 ms = exactly one 10 s window at 100 FPS.
        val window = IntArray(1_000) { 10_000 }
        accumulator.addFrameTimes(window, window.size)

        // Another 3 s that never reaches a full window: flushed by buildSummary().
        val tail = IntArray(300) { 10_000 }
        accumulator.addFrameTimes(tail, tail.size)

        val summary = accumulator.buildSummary(13_000L) ?: error("expected a summary")
        assertEquals(2, summary.fpsBuckets.size)
        summary.fpsBuckets.forEach { assertEquals(100.0, it, 0.5) }
        assertNotNull(summary.p99FrameMs)
    }

    @Test
    fun `count is clamped to the source array`() {
        val accumulator = GameSessionAccumulator("1.21.4", "balanced", 0L)
        accumulator.addFrameTimes(intArrayOf(10_000, 10_000), 10)
        accumulator.addFrameTimes(intArrayOf(10_000, 10_000), -3)

        val summary = accumulator.buildSummary(1_000L) ?: error("expected a summary")
        assertEquals(2, summary.framesCaptured)
    }
}
