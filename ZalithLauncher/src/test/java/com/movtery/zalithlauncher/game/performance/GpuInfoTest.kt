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

class GpuInfoTest {

    @Test
    fun `parses Mali model with core count`() {
        assertEquals("Mali-G57 MC2", GpuInfoReader.parseMaliModel("ARM Mali-G57 MC2"))
    }

    @Test
    fun `parses Mali model without core count`() {
        assertEquals("Mali-G78", GpuInfoReader.parseMaliModel("Mali-G78 r0p1"))
    }

    @Test
    fun `normalises lowercase driver strings`() {
        assertEquals("Mali-G68 MC4", GpuInfoReader.parseMaliModel("mali-g68 mc4"))
    }

    @Test
    fun `parses T series and keeps legacy MP core count`() {
        assertEquals("Mali-T860 MP4", GpuInfoReader.parseMaliModel("Mali-T860 MP4"))
    }

    @Test
    fun `handles missing space before core count`() {
        assertEquals("Mali-G76 MP12", GpuInfoReader.parseMaliModel("Mali-G76MP12"))
    }

    @Test
    fun `returns null for non Mali GPUs`() {
        assertNull(GpuInfoReader.parseMaliModel("Adreno (TM) 640"))
        assertNull(GpuInfoReader.parseMaliModel("PowerVR Rogue GE8320"))
        assertNull(GpuInfoReader.parseMaliModel(null))
        assertNull(GpuInfoReader.parseMaliModel("   "))
    }

    @Test
    fun `classifies the test devices by name`() {
        assertEquals(MaliTier.ENTRY, GpuInfoReader.classifyMali("Mali-G57 MC2"))
        assertEquals(MaliTier.MID, GpuInfoReader.classifyMali("Mali-G68 MC4"))
        assertEquals(MaliTier.MID, GpuInfoReader.classifyMali("Mali-G76 MP12"))
        assertEquals(MaliTier.HIGH, GpuInfoReader.classifyMali("Mali-G78 MP14"))
        assertEquals(MaliTier.UNKNOWN, GpuInfoReader.classifyMali("Adreno 640"))
    }
}
