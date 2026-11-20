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
import org.junit.Assert.assertTrue
import org.junit.Test

class MemoryReliefTest {

    private fun options(text: String): File {
        val dir = Files.createTempDirectory("options").toFile()
        return File(dir, "options.txt").apply { writeText(text) }
    }

    private fun read(file: File) = file.readText().lines().associate {
        val idx = it.indexOf(':')
        it.substring(0, idx) to it.substring(idx + 1)
    }

    @Test
    fun `lowers every memory hungry setting`() {
        val file = options(
            "renderDistance:16\nsimulationDistance:12\ngraphicsMode:2\nentityShadows:true"
        )

        assertTrue(MemoryRelief.apply(file))
        val values = read(file)
        assertEquals("8", values["renderDistance"])
        assertEquals("5", values["simulationDistance"])
        assertEquals("1", values["graphicsMode"])
        assertEquals("false", values["entityShadows"])
    }

    @Test
    fun `never raises a value that is already lower`() {
        val file = options(
            "renderDistance:4\nsimulationDistance:4\ngraphicsMode:0\nentityShadows:false"
        )

        // Nothing to lower, so the file must not be rewritten at all.
        assertFalse(MemoryRelief.apply(file))
        val values = read(file)
        assertEquals("4", values["renderDistance"])
        assertEquals("0", values["graphicsMode"])
    }

    @Test
    fun `keeps unrelated settings and adds missing distance keys`() {
        val file = options("fov:0.5\nrenderDistance:12\nguiScale:2")

        assertTrue(MemoryRelief.apply(file))
        val values = read(file)
        assertEquals("0.5", values["fov"])
        assertEquals("2", values["guiScale"])
        assertEquals("8", values["renderDistance"])
        // Absent keys fall back to Minecraft defaults that are too high, so they are added.
        assertEquals("5", values["simulationDistance"])
    }

    @Test
    fun `a missing file is left alone without throwing`() {
        val dir = Files.createTempDirectory("options").toFile()
        val missing = File(dir, "options.txt")

        assertFalse(MemoryRelief.apply(missing))
        assertFalse(missing.exists())
    }

    @Test
    fun `a file that is not options txt is not rewritten`() {
        val file = options("this is not a key value file")

        assertFalse(MemoryRelief.apply(file))
        assertEquals("this is not a key value file", file.readText())
    }

    @Test
    fun `a non numeric distance is not guessed`() {
        val file = options("renderDistance:auto\nsimulationDistance:5")

        // An unparseable value is left as the game wrote it; only the missing key is added.
        MemoryRelief.apply(file)
        assertEquals("auto", read(file)["renderDistance"])
    }
}
