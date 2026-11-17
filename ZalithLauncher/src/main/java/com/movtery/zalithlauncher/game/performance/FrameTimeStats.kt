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

/**
 * Frame pacing statistics computed from real swap timestamps captured by
 * `aether_metrics_on_frame()` (see `jni/aetherrender/aether_metrics.c`).
 *
 * Nothing here is estimated from the once-per-second FPS counter: the input is the
 * per-frame delta list, so [onePercentLowFps], [p99FrameMs] and [jankPercent] are
 * genuine percentiles of this device's frame times.
 */
data class FrameTimeStats(
    val sampleCount: Int,
    /** Harmonic mean of the instantaneous frame rates (1000 / mean frame time). */
    val averageFps: Double,
    /** FPS of the slowest 1% of frames (average of their frame times). */
    val onePercentLowFps: Double,
    val averageFrameMs: Double,
    val p95FrameMs: Double,
    val p99FrameMs: Double,
    val worstFrameMs: Double,
    /** Share of frames slower than 33.37 ms (below 30 FPS). */
    val jankPercent: Double
)

/**
 * Pure frame time analysis, kept free of Android APIs so it can be unit tested.
 */
object FrameTimeMetrics {

    /**
     * Frame deltas above one second come from pauses, loading screens or the app being
     * backgrounded. Recording them would fake a 1 FPS sample, so they are dropped.
     */
    const val MAX_PLAUSIBLE_FRAME_US = 1_000_000

    /** A frame slower than 30 FPS is counted as jank. */
    const val JANK_FRAME_MS = 1000.0 / 30.0

    /**
     * @param samplesUs frame time deltas in microseconds, in capture order
     * @return measured statistics, or `null` when no usable sample exists
     */
    fun compute(samplesUs: IntArray): FrameTimeStats? {
        if (samplesUs.isEmpty()) return null
        val framesMs = DoubleArray(samplesUs.size) { samplesUs[it].toDouble() / 1000.0 }
            .filter { it > 0.0 && it <= MAX_PLAUSIBLE_FRAME_US / 1000.0 }
            .sorted()
        if (framesMs.isEmpty()) return null

        val count = framesMs.size
        val meanMs = framesMs.average()
        val onePercentCount = (count / 100).coerceAtLeast(1)
        val slowest = framesMs.subList(count - onePercentCount, count)
        val slowestMeanMs = slowest.average()
        val jankFrames = framesMs.count { it > JANK_FRAME_MS }

        return FrameTimeStats(
            sampleCount = count,
            averageFps = if (meanMs > 0.0) 1000.0 / meanMs else 0.0,
            onePercentLowFps = if (slowestMeanMs > 0.0) 1000.0 / slowestMeanMs else 0.0,
            averageFrameMs = meanMs,
            p95FrameMs = percentile(framesMs, 95.0),
            p99FrameMs = percentile(framesMs, 99.0),
            worstFrameMs = framesMs.last(),
            jankPercent = jankFrames.toDouble() * 100.0 / count
        )
    }

    /**
     * Nearest-rank percentile over an ascending array: `p100` returns the worst frame
     * and `p0` the best, matching the usual "P99 frame time" definition.
     */
    fun percentile(ascendingMs: List<Double>, p: Double): Double {
        if (ascendingMs.isEmpty()) return 0.0
        val clamped = p.coerceIn(0.0, 100.0)
        val rank = kotlin.math.ceil(clamped / 100.0 * ascendingMs.size).toInt() - 1
        return ascendingMs[rank.coerceIn(0, ascendingMs.size - 1)]
    }
}
