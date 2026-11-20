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

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MemoryRecommendationTest {

    private fun tempDir(): File = Files.createTempDirectory("memory").toFile()

    @Test
    fun `vanilla has no mod specific floor`() {
        assertEquals(0, MemoryRecommendation.floorFor(0))
        assertNull(MemoryRecommendation.recommendedMb(2048, 0, 4096))
    }

    @Test
    fun `floors grow with the mod count`() {
        assertEquals(MemoryRecommendation.LIGHT_MOD_FLOOR_MB, MemoryRecommendation.floorFor(1))
        assertEquals(MemoryRecommendation.LIGHT_MOD_FLOOR_MB, MemoryRecommendation.floorFor(9))
        assertEquals(MemoryRecommendation.MODERATE_MOD_FLOOR_MB, MemoryRecommendation.floorFor(10))
        assertEquals(MemoryRecommendation.MODERATE_MOD_FLOOR_MB, MemoryRecommendation.floorFor(29))
        assertEquals(MemoryRecommendation.HEAVY_MOD_FLOOR_MB, MemoryRecommendation.floorFor(30))
        assertEquals(MemoryRecommendation.HEAVY_MOD_FLOOR_MB, MemoryRecommendation.floorFor(50))
    }

    @Test
    fun `fifty mods on a two gigabyte heap propose three gigabytes`() {
        assertEquals(3072, MemoryRecommendation.recommendedMb(2048, 50, 4096))
    }

    @Test
    fun `an already adequate heap is left alone`() {
        assertNull(MemoryRecommendation.recommendedMb(3072, 50, 4096))
        assertNull(MemoryRecommendation.recommendedMb(4096, 50, 4096))
    }

    @Test
    fun `a small bump is not worth interrupting the launch`() {
        // Floor is already below the allocation, so there is nothing to gain at all.
        assertNull(MemoryRecommendation.recommendedMb(2304, 5, 4096))
        // Gain of 128 MB is below the minimum and stays silent.
        assertNull(MemoryRecommendation.recommendedMb(1920, 1, 4096))
    }

    @Test
    fun `the proposal never exceeds the device safe maximum`() {
        assertEquals(2560, MemoryRecommendation.recommendedMb(2048, 50, 2600))
        assertEquals(2560, MemoryRecommendation.recommendedMb(2048, 50, 2560))
    }

    @Test
    fun `an unusable maximum produces no proposal`() {
        assertNull(MemoryRecommendation.recommendedMb(2048, 50, 0))
        assertNull(MemoryRecommendation.recommendedMb(2048, 50, -1))
    }

    @Test
    fun `proposal is rounded down to the slider step`() {
        assertEquals(2560, MemoryRecommendation.recommendedMb(2048, 50, 2600))
        // 3072 requested, capped to 3000, then rounded down to a 64 MB multiple.
        assertEquals(2944, MemoryRecommendation.recommendedMb(2048, 50, 3000))
    }

    @Test
    fun `mods directory is counted case insensitively and only for jars`() {
        val dir = tempDir()
        val mods = File(dir, "mods").apply { mkdirs() }
        File(mods, "sodium.jar").writeText("jar")
        File(mods, "iris.JAR").writeText("jar")
        File(mods, "notes.txt").writeText("not a mod")
        File(mods, "nested").mkdirs()

        assertEquals(2, MemoryRecommendation.countMods(dir))
    }

    @Test
    fun `missing mods directory means vanilla`() {
        assertEquals(0, MemoryRecommendation.countMods(tempDir()))
    }

    @Test
    fun `heap pressure with room proposes a raise`() {
        val advice = MemoryRecommendation.advise(
            allocatedMb = 2048,
            modCount = 50,
            maxMb = 4096,
            pressure = MemoryPressure.HEAP
        )

        assertEquals(MemoryRecommendation.Kind.RAISE_HEAP, advice.kind)
        assertEquals(3072, advice.recommendedMb)
        assertFalse(advice.atLimit)
    }

    @Test
    fun `heap pressure at the device cap asks for lower graphics instead`() {
        val advice = MemoryRecommendation.advise(
            allocatedMb = 3008,
            modCount = 50,
            maxMb = 3072,
            pressure = MemoryPressure.HEAP
        )

        assertEquals(MemoryRecommendation.Kind.LOWER_GRAPHICS, advice.kind)
        assertNull(advice.recommendedMb)
        assertTrue(advice.atLimit)
    }

    @Test
    fun `a vanilla heap out of memory still gets one safe bump`() {
        // floorFor(0) is 0, so the pre-launch path stays silent; after a real OOM the
        // launcher must still propose something instead of showing no advice at all.
        val advice = MemoryRecommendation.advise(
            allocatedMb = 2048,
            modCount = 0,
            maxMb = 4096,
            pressure = MemoryPressure.HEAP
        )

        assertEquals(MemoryRecommendation.Kind.RAISE_HEAP, advice.kind)
        assertEquals(2560, advice.recommendedMb)
    }

    @Test
    fun `graphics pressure never proposes more heap`() {
        val advice = MemoryRecommendation.advise(
            allocatedMb = 2048,
            modCount = 50,
            maxMb = 4096,
            pressure = MemoryPressure.GRAPHICS
        )

        // More heap would be useless here, even though the number is below the floor.
        assertEquals(MemoryRecommendation.Kind.LOWER_GRAPHICS, advice.kind)
        assertNull(advice.recommendedMb)
        assertFalse(advice.atLimit)
    }
}
