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
        assertEquals("2", env["LIBGL_ES"])
        // The mode is always passed explicitly so the native layer never has to guess.
        assertTrue(
            env["AETHER_RENDER_MODE"] in
                listOf("native", "balanced", "lite", "performance", "compatibility")
        )
    }

    @Test
    fun `uses the bundled GL4ES core`() {
        assertEquals("libgl4es_114.so", AetherRenderer.getRendererLibrary())
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
