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
import org.junit.Test

class CpuUsageTest {

    @Test
    fun `parses the aggregate cpu line and counts iowait as idle`() {
        val stat = """
            cpu  100 0 50 850 20 0 0 0 0 0
            cpu0 50 0 25 425 10 0 0 0 0 0
        """.trimIndent()

        val times = CpuUsage.parse(stat)

        assertEquals(1020L, times?.total)
        assertEquals(870L, times?.idle)
    }

    @Test
    fun `accepts a four-field line`() {
        val times = CpuUsage.parse("cpu 1 2 3 4")

        assertEquals(10L, times?.total)
        assertEquals(4L, times?.idle)
    }

    @Test
    fun `returns null without a cpu line`() {
        assertNull(CpuUsage.parse(null))
        assertNull(CpuUsage.parse(""))
        assertNull(CpuUsage.parse("intr 1 2 3\nctxt 4"))
    }

    @Test
    fun `returns null for malformed or zero counters`() {
        assertNull(CpuUsage.parse("cpu a b c d"))
        assertNull(CpuUsage.parse("cpu 0 0 0 0"))
        assertNull(CpuUsage.parse("cpu 1 2"))
    }

    @Test
    fun `computes busy percentage between two samples`() {
        val previous = CpuTimes(total = 1000, idle = 800)
        val current = CpuTimes(total = 2000, idle = 1600)

        val usage = CpuUsage.usagePercent(previous, current)

        assertEquals(20f, usage ?: -1f, 0.001f)
    }

    @Test
    fun `first sample and counter resets cannot produce a percentage`() {
        assertNull(CpuUsage.usagePercent(null, CpuTimes(100, 50)))
        assertNull(CpuUsage.usagePercent(CpuTimes(100, 50), null))
        assertNull(CpuUsage.usagePercent(CpuTimes(100, 50), CpuTimes(100, 50)))
        assertNull(CpuUsage.usagePercent(CpuTimes(100, 50), CpuTimes(90, 40)))
    }

    @Test
    fun `rejects impossible idle deltas instead of clamping to a fake value`() {
        // idle grew faster than total time; the counters are inconsistent.
        val previous = CpuTimes(total = 1000, idle = 100)
        val current = CpuTimes(total = 1100, idle = 400)

        assertNull(CpuUsage.usagePercent(previous, current))
    }

    @Test
    fun `clamps a fully busy delta to one hundred percent`() {
        val previous = CpuTimes(total = 1000, idle = 900)
        val current = CpuTimes(total = 1100, idle = 900)

        assertEquals(100f, CpuUsage.usagePercent(previous, current) ?: -1f, 0.001f)
    }
}
