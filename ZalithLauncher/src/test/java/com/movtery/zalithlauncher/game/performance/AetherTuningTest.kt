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

import com.movtery.zalithlauncher.game.renderer.aether.AetherGpuProfiles
import com.movtery.zalithlauncher.game.renderer.aether.AetherTuning
import com.movtery.zalithlauncher.setting.enums.AetherRenderMode
import com.movtery.zalithlauncher.ui.theme.AetherThemeColors
import com.movtery.zalithlauncher.ui.theme.ColorThemeType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AetherTuningTest {

    @Test
    fun `core default sets no variables`() {
        assertTrue(AetherTuning.CORE_DEFAULT.env.isEmpty())
        assertTrue(AetherTuning.CORE_DEFAULT.envKeys.isEmpty())
    }

    @Test
    fun `throughput only uses core variables`() {
        assertEquals(
            setOf("LIBGL_BATCH", "LIBGL_USEVBO", "LIBGL_RECYCLEFBO"),
            AetherTuning.THROUGHPUT.env.keys
        )
        assertTrue(AetherTuning.THROUGHPUT.env.values.all { it == "1" })
    }

    @Test
    fun `memory saver only uses core variables`() {
        assertEquals(
            setOf("LIBGL_FORCE16BITS", "LIBGL_NOVAOCACHE"),
            AetherTuning.MEMORY_SAVER.env.keys
        )
    }

    @Test
    fun `every variable is a prefixed LIBGL key with a binary value`() {
        AetherTuning.entries.forEach { tuning ->
            tuning.env.forEach { (key, value) ->
                assertTrue("$key must start with LIBGL_", key.startsWith("LIBGL_"))
                assertTrue("$key must be a flag", value == "0" || value == "1")
            }
            assertEquals(tuning.env.keys.toList(), tuning.envKeys)
        }
    }

    @Test
    fun `presets never collide on the same variable`() {
        val seen = mutableSetOf<String>()
        AetherTuning.entries.flatMap { it.env.keys }.forEach { key ->
            // Each variable belongs to one preset; overlapping presets would make the
            // selected preset ambiguous when the launcher writes the environment.
            assertTrue("duplicate variable $key", seen.add(key))
        }
    }
}

class AetherGpuProfilesTest {

    private fun tuningFor(model: String?) = AetherGpuProfiles.recommend(model).tuning

    @Test
    fun `entry level mali parts get the memory preset`() {
        assertEquals(AetherTuning.MEMORY_SAVER, tuningFor("Mali-G57"))
        assertEquals(AetherTuning.MEMORY_SAVER, tuningFor("Mali-G57 MC2"))
        assertEquals(AetherTuning.MEMORY_SAVER, tuningFor("Mali-G68"))
    }

    @Test
    fun `modern mali parts get the throughput preset`() {
        listOf("Mali-G76", "Mali-G78", "Mali-G710", "Mali-G715").forEach { model ->
            assertEquals(model, AetherTuning.THROUGHPUT, tuningFor(model))
        }
    }

    @Test
    fun `unknown gpu keeps core defaults`() {
        assertNull(AetherGpuProfiles.recommend(null).model)
        assertEquals(AetherTuning.CORE_DEFAULT, tuningFor(null))
        assertEquals(AetherTuning.CORE_DEFAULT, tuningFor("Adreno (TM) 640"))
    }

    @Test
    fun `recommendation never forces a lossy egl mode`() {
        listOf("Mali-G57", "Mali-G76", "Mali-G715", "Adreno 640", null).forEach { model ->
            assertEquals(AetherRenderMode.BALANCED, AetherGpuProfiles.recommend(model).mode)
        }
    }

    @Test
    fun `model and tier are reported from the driver string`() {
        val preset = AetherGpuProfiles.recommend("Mali-G76 MC4")
        assertEquals("Mali-G76 MC4", preset.model)
        assertEquals(MaliTier.MID, preset.tier)
    }
}

class AetherThemeSeedTest {

    @Test
    fun `every aether palette has a seed`() {
        val aetherTypes = listOf(
            ColorThemeType.AETHER_BLUE,
            ColorThemeType.PURPLE_NEBULA,
            ColorThemeType.EMERALD,
            ColorThemeType.CRIMSON,
            ColorThemeType.MIDNIGHT,
            ColorThemeType.AMOLED_BLACK,
            ColorThemeType.TITAN_DARK,
            ColorThemeType.CYBER_PURPLE,
            ColorThemeType.ARCTIC_BLUE,
            ColorThemeType.MINIMAL
        )
        val seeds = aetherTypes.map { type ->
            val seed = AetherThemeColors.seedOf(type)
            assertNotNull("$type must have a seed", seed)
            seed!!
        }
        // Distinct seeds: two palettes that look identical are a bug, not a design.
        assertEquals(aetherTypes.size, seeds.toSet().size)
    }

    @Test
    fun `non aether types report no seed`() {
        assertNull(AetherThemeColors.seedOf(ColorThemeType.DYNAMIC))
        assertNull(AetherThemeColors.seedOf(ColorThemeType.EMBERMIRE))
        assertNull(AetherThemeColors.seedOf(ColorThemeType.CUSTOM))
    }

    @Test
    fun `titan palettes stay opaque`() {
        // Seeds are ARGB ints; 0xFF alpha keeps every androidscheme fully opaque.
        listOf(
            AetherThemeColors.TITAN_DARK,
            AetherThemeColors.CYBER_PURPLE,
            AetherThemeColors.ARCTIC_BLUE,
            AetherThemeColors.MINIMAL
        ).forEach { seed ->
            assertEquals(0xFF, (seed ushr 24) and 0xFF)
        }
    }
}
