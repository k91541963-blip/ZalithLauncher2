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

import com.movtery.zalithlauncher.game.renderer.renderers.AetherRenderer
import com.movtery.zalithlauncher.game.renderer.renderers.GL4ESRenderer
import com.movtery.zalithlauncher.game.renderer.renderers.KopperZinkRenderer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AetherRendererTest {

    @Test
    fun `renderer id keeps the native GL4ES code path`() {
        // pojavSetWindowHint() in egl_bridge.c only maps ids starting with "opengles"
        // to RENDERER_GL4ES. Changing the prefix silently breaks swap/make-current.
        assertTrue(AetherRenderer.getRendererId().startsWith("opengles"))
    }

    @Test
    fun `enables the native tuning layer`() {
        val env = AetherRenderer.getRendererEnv().value
        assertEquals("1", env["AETHER_RENDER"])
        // NG-GL4ES needs a GLES 3 context for Minecraft 1.17+ and the 26.x releases.
        assertEquals("3", env["LIBGL_ES"])
        assertEquals("31", env["LIBGL_GL"])
        assertEquals("1", env["LIBGL_USE_MC_COLOR"])
        // AetherRender 3: VSync is a real native setting, always passed explicitly.
        assertEquals("1", env["AETHER_SWAP_INTERVAL"])
        // The mode is always passed explicitly so the native layer never has to guess.
        assertTrue(
            env["AETHER_RENDER_MODE"] in
                listOf("native", "balanced", "lite", "performance", "compatibility")
        )
    }

    @Test
    fun `uses the modern NG-GL4ES core`() {
        // The 1.1.4 core only exposes GL 2.1 and stops at 1.21.4; 26.x needs NG-GL4ES.
        assertEquals("libng_gl4es.so", AetherRenderer.getRendererLibrary())
    }

    @Test
    fun `supports the 26_3 generation`() {
        assertEquals("26.3-snapshot-3", AetherRenderer.getMaxMCVersion())
        // A released version, not a snapshot tag, is what the UI should promise.
        assertEquals("26.2", AetherRenderer.getDisplayMaxMCVersion())
        assertTrue(AetherRenderer.getRendererId().startsWith("opengles3"))
    }

    @Test
    fun `unique identifier does not clash with built-ins`() {
        val ids = listOf(
            GL4ESRenderer.getUniqueIdentifier(),
            KopperZinkRenderer.getUniqueIdentifier(),
            AetherRenderer.getUniqueIdentifier()
        )
        assertEquals(ids.size, ids.distinct().size)
        assertNotEquals("", AetherRenderer.getUniqueIdentifier())
    }
}
