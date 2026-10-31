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
import org.junit.Assert.assertTrue
import org.junit.Test

class ModDependencyCheckerTest {

    private fun manifest(
        id: String,
        version: String? = "1.0.0",
        dependencies: List<ModDependency> = emptyList()
    ) = ModManifest(
        id = id,
        name = id,
        version = version,
        dependencies = dependencies
    )

    @Test
    fun `missing dependency is reported`() {
        val mods = listOf(
            manifest(
                "example_mod",
                dependencies = listOf(ModDependency("fabric-api", listOf(">=0.90.0")))
            )
        )

        val issues = ModDependencyChecker.check(mods)

        assertEquals(1, issues.size)
        assertEquals(DependencyProblem.MISSING, issues.first().problem)
        assertEquals("fabric-api", issues.first().dependencyId)
        assertEquals("example_mod", issues.first().modId)
    }

    @Test
    fun `disabled dependency is reported as disabled`() {
        val mods = listOf(
            manifest("example_mod", dependencies = listOf(ModDependency("fabric-api")))
        )

        val issues = ModDependencyChecker.check(mods, disabledModIds = setOf("fabric-api"))

        assertEquals(DependencyProblem.DISABLED, issues.first().problem)
    }

    @Test
    fun `version outside the declared range is reported`() {
        val mods = listOf(
            manifest("fabric-api", version = "0.88.0"),
            manifest(
                "example_mod",
                dependencies = listOf(ModDependency("fabric-api", listOf(">=0.90.0")))
            )
        )

        val issues = ModDependencyChecker.check(mods)

        assertEquals(1, issues.size)
        assertEquals(DependencyProblem.VERSION_MISMATCH, issues.first().problem)
        assertEquals("0.88.0", issues.first().installedVersion)
    }

    @Test
    fun `satisfied dependency produces no issue`() {
        val mods = listOf(
            manifest("fabric-api", version = "0.92.0"),
            manifest(
                "example_mod",
                dependencies = listOf(ModDependency("fabric-api", listOf(">=0.90.0")))
            )
        )

        assertTrue(ModDependencyChecker.check(mods).isEmpty())
    }

    @Test
    fun `loader provided ids are ignored`() {
        val mods = listOf(
            manifest(
                "example_mod",
                dependencies = listOf(
                    ModDependency("minecraft", listOf(">=1.20")),
                    ModDependency("java", listOf(">=17")),
                    ModDependency("fabricloader", listOf(">=0.15"))
                )
            )
        )

        assertTrue(ModDependencyChecker.check(mods).isEmpty())
    }

    @Test
    fun `optional dependencies never produce issues`() {
        val mods = listOf(
            manifest(
                "example_mod",
                dependencies = listOf(ModDependency("optional_mod", required = false))
            )
        )

        assertTrue(ModDependencyChecker.check(mods).isEmpty())
    }

    @Test
    fun `self dependency is ignored`() {
        val mods = listOf(
            manifest("example_mod", dependencies = listOf(ModDependency("example_mod")))
        )

        assertTrue(ModDependencyChecker.check(mods).isEmpty())
    }

    @Test
    fun `dependency with any acceptable range is satisfied`() {
        val mods = listOf(
            manifest("fabric-api", version = "0.91.0"),
            manifest(
                "example_mod",
                dependencies = listOf(
                    ModDependency("fabric-api", listOf(">=0.80.0 <0.90.0", ">=0.91.0"))
                )
            )
        )

        assertTrue(ModDependencyChecker.check(mods).isEmpty())
    }
}
