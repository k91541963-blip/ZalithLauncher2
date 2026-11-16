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
 * One `/proc/stat` sample of the aggregate `cpu` line, in kernel jiffies.
 *
 * Only pure parsing lives here so it can be unit tested on the JVM. Reading the file is
 * done by [PerformanceMonitor] on a background dispatcher.
 */
data class CpuTimes(
    val total: Long,
    val idle: Long
)

/**
 * Parses and compares aggregate CPU time.
 *
 * Linux reports `cpu user nice system idle iowait irq softirq steal guest guest_nice`.
 * `iowait` counts as idle for a load percentage, because the CPU was not executing.
 * `guest`/`guest_nice` are already included in `user`/`nice`, so they are ignored.
 *
 * Android 8+ may hide `/proc/stat` from apps on some kernels; callers must treat a
 * `null` result as "not measurable" and never show a guessed number.
 */
object CpuUsage {

    /** Parses the first `cpu ` line. Returns `null` for malformed or empty input. */
    fun parse(stat: String?): CpuTimes? {
        if (stat.isNullOrBlank()) return null
        val line = stat.lineSequence()
            .firstOrNull { it.startsWith("cpu ") || it == "cpu" }
            ?: return null
        val parts = line.trim().split(' ', '\t').filter { it.isNotEmpty() }
        if (parts.size < 5) return null

        val values = parts.drop(1).map { it.toLongOrNull() ?: return null }
        if (values.size < 4) return null
        val total = values.sum()
        if (total <= 0L) return null
        val idle = values[3] + (values.getOrNull(4) ?: 0L)
        return CpuTimes(total = total, idle = idle.coerceAtMost(total))
    }

    /**
     * Busy percentage between two samples.
     *
     * Returns `null` when there is no usable delta (first call, counter reset, or an
     * unreadable previous sample). The result is clamped to `0..100`.
     */
    fun usagePercent(previous: CpuTimes?, current: CpuTimes?): Float? {
        if (previous == null || current == null) return null
        val totalDelta = current.total - previous.total
        val idleDelta = current.idle - previous.idle
        if (totalDelta <= 0L) return null
        if (idleDelta < 0L || idleDelta > totalDelta) return null
        return ((totalDelta - idleDelta) * 100f / totalDelta)
            .coerceIn(0f, 100f)
    }
}
