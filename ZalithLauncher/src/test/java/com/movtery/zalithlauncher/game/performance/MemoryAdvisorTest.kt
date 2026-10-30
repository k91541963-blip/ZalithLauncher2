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

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MemoryAdvisorTest {

    private fun profile(
        totalRamMb: Int,
        is64Bit: Boolean = true,
        cpuCores: Int = 8
    ) = HardwareProfile(
        supportedAbis = if (is64Bit) listOf("arm64-v8a") else listOf("armeabi-v7a"),
        is64Bit = is64Bit,
        cpuCores = cpuCores,
        totalRamMb = totalRamMb,
        freeRamMb = totalRamMb / 2,
        glEsMajor = 3,
        glEsMinor = 2,
        gpuFamily = GpuFamily.ADRENO,
        socModel = "SM8250",
        androidSdk = 33,
        deviceName = "Test"
    )

    @Test
    fun `32 bit devices are capped at 1 GB`() {
        val advice = MemoryAdvisor.advise(profile(totalRamMb = 3072, is64Bit = false))
        assertTrue(advice.maxMb <= 1024)
        assertTrue(advice.recommendedMb <= advice.maxMb)
        assertTrue(advice.notes.any { it.contains("32-bit") })
    }

    @Test
    fun `recommendation grows with device memory`() {
        val small = MemoryAdvisor.advise(profile(2048)).recommendedMb
        val medium = MemoryAdvisor.advise(profile(4096)).recommendedMb
        val large = MemoryAdvisor.advise(profile(8192)).recommendedMb

        assertTrue(small < medium)
        assertTrue(medium < large)
    }

    @Test
    fun `recommendation never exceeds safe maximum`() {
        for (total in listOf(1024, 2048, 3072, 4096, 6144, 8192, 12288)) {
            val advice = MemoryAdvisor.advise(profile(total))
            assertTrue("total=$total", advice.recommendedMb <= advice.maxMb)
            assertTrue("total=$total", advice.recommendedMb >= MemoryAdvisor.MIN_MB)
        }
    }

    @Test
    fun `low end devices get a low end note`() {
        val advice = MemoryAdvisor.advise(profile(totalRamMb = 3072, cpuCores = 4))
        assertTrue(advice.notes.isNotEmpty())
    }

    @Test
    fun `clamping keeps values inside the safe range`() {
        val advice = MemoryAdvisor.advise(profile(4096))
        assertEquals(advice.maxMb, advice.clamp(99999))
        assertEquals(MemoryAdvisor.MIN_MB, advice.clamp(1))
        assertTrue(advice.isAboveSafeLimit(advice.maxMb + 1))
        assertFalse(advice.isAboveSafeLimit(advice.maxMb))
    }
}
