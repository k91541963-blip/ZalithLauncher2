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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PerformancePresetTest {

    private fun profile(totalRamMb: Int, is64Bit: Boolean = true) = HardwareProfile(
        supportedAbis = listOf("arm64-v8a"),
        is64Bit = is64Bit,
        cpuCores = 8,
        totalRamMb = totalRamMb,
        freeRamMb = totalRamMb / 2,
        glEsMajor = 3,
        glEsMinor = 2,
        gpuFamily = GpuFamily.ADRENO,
        socModel = "Test SoC",
        androidSdk = 33,
        deviceName = "Test device"
    )

    @Test
    fun `profiles are ordered from least to most memory`() {
        val presets = PerformancePresets.forProfile(profile(4096))

        assertEquals(3, presets.size)
        val lowEnd = presets.first { it.id == PerformancePresets.ID_LOW_END }
        val balanced = presets.first { it.id == PerformancePresets.ID_BALANCED }
        val quality = presets.first { it.id == PerformancePresets.ID_QUALITY }

        assertNotNull(lowEnd.ramMb)
        assertNotNull(balanced.ramMb)
        assertNotNull(quality.ramMb)
        assertTrue(lowEnd.ramMb!! < balanced.ramMb!!)
        assertTrue(balanced.ramMb!! <= quality.ramMb!!)
        assertTrue(lowEnd.ramMb!! >= MemoryAdvisor.MIN_MB)
    }

    @Test
    fun `balanced preset uses the recommended memory`() {
        val hardware = profile(6144)
        val advice = MemoryAdvisor.advise(hardware)

        val balanced = PerformancePresets.find(hardware, PerformancePresets.ID_BALANCED)

        assertNotNull(balanced)
        assertEquals(advice.recommendedMb, balanced!!.ramMb)
        assertEquals(100, balanced.resolutionRatio)
        assertTrue(balanced.jvmArgs.contains("UseG1GC"))
    }

    @Test
    fun `quality preset uses the safe maximum`() {
        val hardware = profile(8192)
        val advice = MemoryAdvisor.advise(hardware)

        val quality = PerformancePresets.find(hardware, PerformancePresets.ID_QUALITY)

        assertNotNull(quality)
        assertEquals(advice.maxMb, quality!!.ramMb)
    }

    @Test
    fun `low end preset lowers resolution and uses serial gc`() {
        val lowEnd = PerformancePresets.find(profile(3072), PerformancePresets.ID_LOW_END)

        assertNotNull(lowEnd)
        assertEquals(75, lowEnd!!.resolutionRatio)
        assertTrue(lowEnd.jvmArgs.contains("UseSerialGC"))
    }

    @Test
    fun `presets never change the renderer on their own`() {
        val presets = PerformancePresets.forProfile(profile(4096))

        assertTrue(presets.all { it.rendererId == null })
    }

    @Test
    fun `unknown preset id returns null`() {
        assertNull(PerformancePresets.find(profile(4096), "does_not_exist"))
    }
}
