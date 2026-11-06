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

import com.moandjiezana.toml.Toml
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ForgeDependencyParserTest {

    @Test
    fun `forge dependencies are parsed with mandatory and version range`() {
        val toml = Toml().read(
            """
            modLoader = "javafml"
            loaderVersion = "[47,)"
            license = "MIT"

            [[mods]]
            modId = "examplemod"
            version = "1.0.0"
            displayName = "Example"

            [[dependencies.examplemod]]
            modId = "forge"
            mandatory = true
            versionRange = "[47,)"
            ordering = "NONE"
            side = "BOTH"

            [[dependencies.examplemod]]
            modId = "othermod"
            mandatory = false
            versionRange = "[1.0,2.0)"
            side = "BOTH"
            """.trimIndent()
        )

        val deps = ForgeDependencyParser.parse(toml.toMap(), "examplemod")

        assertEquals(2, deps.size)
        assertEquals("forge", deps[0].id)
        assertTrue(deps[0].required)
        assertEquals(listOf("[47,)"), deps[0].versionRanges)

        assertEquals("othermod", deps[1].id)
        assertFalse(deps[1].required)
        assertEquals(listOf("[1.0,2.0)"), deps[1].versionRanges)
    }

    @Test
    fun `neoforge type required maps to a required dependency`() {
        val toml = Toml().read(
            """
            [[mods]]
            modId = "examplemod"
            version = "1.0.0"
            displayName = "Example"

            [[dependencies.examplemod]]
            modId = "neoforge"
            type = "required"
            versionRange = "[21,)"
            """.trimIndent()
        )

        val deps = ForgeDependencyParser.parse(toml.toMap(), "examplemod")

        assertEquals(1, deps.size)
        assertEquals("neoforge", deps[0].id)
        assertTrue(deps[0].required)
    }

    @Test
    fun `server only and incompatible dependencies are ignored`() {
        val toml = Toml().read(
            """
            [[mods]]
            modId = "examplemod"
            version = "1.0.0"
            displayName = "Example"

            [[dependencies.examplemod]]
            modId = "serveronly"
            mandatory = true
            versionRange = "*"
            side = "SERVER"

            [[dependencies.examplemod]]
            modId = "conflict"
            type = "incompatible"
            versionRange = "[1.0]"
            """.trimIndent()
        )

        assertTrue(ForgeDependencyParser.parse(toml.toMap(), "examplemod").isEmpty())
    }

    @Test
    fun `dependencies of another mod id are not mixed in`() {
        val toml = Toml().read(
            """
            [[mods]]
            modId = "examplemod"
            version = "1.0.0"
            displayName = "Example"

            [[dependencies.examplemod]]
            modId = "forge"
            mandatory = true

            [[dependencies.othermod]]
            modId = "unrelated"
            mandatory = true
            """.trimIndent()
        )

        val deps = ForgeDependencyParser.parse(toml.toMap(), "examplemod")

        assertEquals(listOf("forge"), deps.map { it.id })
    }
}
