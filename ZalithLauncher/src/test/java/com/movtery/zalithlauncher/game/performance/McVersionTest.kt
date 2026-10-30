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

class McVersionTest {

    @Test
    fun `numeric parts ignore suffixes`() {
        assertEquals(listOf(1, 21, 4), McVersion.numericParts("1.21.4"))
        assertEquals(listOf(26, 3), McVersion.numericParts("26.3-snapshot-3"))
        assertEquals(listOf(1, 21), McVersion.numericParts("1.21"))
        assertEquals(emptyList<Int>(), McVersion.numericParts("snapshot"))
        assertEquals(emptyList<Int>(), McVersion.numericParts(null))
    }

    @Test
    fun `compare orders patch and minor versions`() {
        assertTrue(McVersion.compare("1.21.1", "1.21.4") < 0)
        assertTrue(McVersion.compare("1.21.4", "1.21.1") > 0)
        assertEquals(0, McVersion.compare("1.21.4", "1.21.4"))
        assertTrue(McVersion.compare("1.20.1-forge-47.2.0", "1.21") < 0)
        assertEquals(0, McVersion.compare("1.21", "1.21.0"))
    }

    @Test
    fun `unknown versions are never rejected`() {
        assertTrue(McVersion.isInRange(null, "1.20", "1.21"))
        assertTrue(McVersion.isInRange("snapshot", "1.20", "1.21"))
    }

    @Test
    fun `range check is inclusive`() {
        assertTrue(McVersion.isInRange("1.21.4", null, "1.21.4"))
        assertFalse(McVersion.isInRange("1.21.5", null, "1.21.4"))
        assertFalse(McVersion.isInRange("1.19", "1.20", null))
    }
}
