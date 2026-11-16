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

class ThermalInfoTest {

    @Test
    fun `parses millidegrees`() {
        assertEquals(45.0f, ThermalInfoReader.parseCelsius("45000")!!, 0.001f)
    }

    @Test
    fun `parses degrees with a decimal point`() {
        assertEquals(42.5f, ThermalInfoReader.parseCelsius("42.5")!!, 0.001f)
    }

    @Test
    fun `rejects unreadable and implausible values`() {
        assertNull(ThermalInfoReader.parseCelsius(null))
        assertNull(ThermalInfoReader.parseCelsius(""))
        assertNull(ThermalInfoReader.parseCelsius("not-a-number"))
        assertNull(ThermalInfoReader.parseCelsius("999999"))
        assertNull(ThermalInfoReader.parseCelsius("-90000"))
    }

    @Test
    fun `accepts plausible cool values`() {
        assertEquals(25.0f, ThermalInfoReader.parseCelsius("25000")!!, 0.001f)
    }
}
