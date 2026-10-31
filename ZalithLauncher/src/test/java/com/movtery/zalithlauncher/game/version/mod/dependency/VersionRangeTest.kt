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

package com.movtery.zalithlauncher.game.version.mod.dependency

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VersionRangeTest {

    @Test
    fun `space separated predicates must all hold`() {
        assertTrue(VersionRange.satisfies("1.21.4", ">=1.20 <1.22"))
        assertFalse(VersionRange.satisfies("1.22", ">=1.20 <1.22"))
        assertFalse(VersionRange.satisfies("1.19.2", ">=1.20"))
    }

    @Test
    fun `empty or wildcard range accepts any version`() {
        assertTrue(VersionRange.satisfies("1.0.0", null))
        assertTrue(VersionRange.satisfies("1.0.0", ""))
        assertTrue(VersionRange.satisfies("1.0.0", "*"))
        assertTrue(VersionRange.satisfies("1.0.0", ">=1.0.0"))
    }

    @Test
    fun `unknown version never satisfies a concrete range`() {
        assertFalse(VersionRange.satisfies(null, ">=1.20"))
        assertFalse(VersionRange.satisfies("", "1.20.x"))
    }

    @Test
    fun `caret range stays inside the major version`() {
        assertTrue(VersionRange.satisfies("1.20.1", "^1.20.1"))
        assertTrue(VersionRange.satisfies("1.20.9", "^1.20.1"))
        // semver: ^1.20.1 == >=1.20.1 <2.0.0
        assertTrue(VersionRange.satisfies("1.21.0", "^1.20.1"))
        assertFalse(VersionRange.satisfies("2.0.0", "^1.20.1"))
        assertFalse(VersionRange.satisfies("1.20.0", "^1.20.1"))
    }

    @Test
    fun `tilde range stays inside the minor version`() {
        assertTrue(VersionRange.satisfies("1.20.5", "~1.20.1"))
        assertFalse(VersionRange.satisfies("1.21.0", "~1.20.1"))
    }

    @Test
    fun `wildcard matches the patch level`() {
        assertTrue(VersionRange.satisfies("1.20.4", "1.20.x"))
        assertTrue(VersionRange.satisfies("1.20.4", "1.20.*"))
        assertFalse(VersionRange.satisfies("1.21", "1.20.x"))
    }

    @Test
    fun `bracket ranges use inclusive and exclusive bounds`() {
        assertTrue(VersionRange.satisfies("1.20", "[1.20,1.21)"))
        assertTrue(VersionRange.satisfies("1.20.9", "[1.20,1.21)"))
        assertFalse(VersionRange.satisfies("1.21", "[1.20,1.21)"))
        assertFalse(VersionRange.satisfies("1.19.9", "[1.20,1.21)"))
        assertTrue(VersionRange.satisfies("1.21", "[1.20,1.21]"))
    }

    @Test
    fun `versions compare segment by segment`() {
        assertTrue(VersionRange.compare("1.21", "1.21.4") < 0)
        assertTrue(VersionRange.compare("1.21.4", "1.21.4") == 0)
        assertTrue(VersionRange.compare("1.22", "1.21.9") > 0)
        assertTrue(VersionRange.compare("1.9", "1.10") < 0)
    }

    @Test
    fun `pre release is older than its release`() {
        assertTrue(VersionRange.compare("1.21.4-rc1", "1.21.4") < 0)
        assertTrue(VersionRange.satisfies("1.21.4-rc1", "<1.21.4"))
    }

    @Test
    fun `any of the declared ranges is enough`() {
        val ranges = listOf(">=1.20 <1.21", ">=1.21.2")
        assertTrue(VersionRange.satisfiesAny("1.20.4", ranges))
        assertTrue(VersionRange.satisfiesAny("1.21.3", ranges))
        assertFalse(VersionRange.satisfiesAny("1.21.1", ranges))
        assertTrue(VersionRange.satisfiesAny("0.0.1", emptyList()))
    }
}
