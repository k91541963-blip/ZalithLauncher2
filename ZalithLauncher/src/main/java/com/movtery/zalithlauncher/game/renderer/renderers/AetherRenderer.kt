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

package com.movtery.zalithlauncher.game.renderer.renderers

import com.movtery.zalithlauncher.game.renderer.RendererInterface

/**
 * AetherRender v1.
 *
 * The GL translation itself is the proven GL4ES 1.1.4 core. AetherRender adds a native
 * EGL layer (`jni/aetherrender/aether_render.c`) that:
 *  - picks a tile-GPU friendly EGL config (no MSAA, 24-bit depth),
 *  - requests `EGL_BUFFER_DESTROYED` for window surfaces,
 *  - logs the real GL strings for support reports.
 *
 * It makes no claim about FPS and does not replace Minecraft's chunk renderer. If the
 * driver rejects the surface policy the launch continues on the stock path.
 */
object AetherRenderer : RendererInterface {
    override fun getRendererId(): String = "opengles2_aether"

    override fun getUniqueIdentifier(): String = "1f0a7e2c-9b4d-4c8e-a1e6-7ae71e4b9c01"

    override fun getRendererName(): String = "AetherRender"

    override fun getMaxMCVersion(): String = "1.21.4"

    override fun getRendererEnv(): Lazy<Map<String, String>> = lazy {
        mapOf(
            "AETHER_RENDER" to "1",
            "LIBGL_ES" to "2",
            "LIBGL_MIPMAP" to "3",
            "LIBGL_NOERROR" to "1",
            "LIBGL_NOINTOVLHACK" to "1",
            "LIBGL_NORMALIZE" to "1"
        )
    }

    override fun getDlopenLibrary(): Lazy<List<String>> = lazy { emptyList() }

    override fun getRendererLibrary(): String = "libgl4es_114.so"
}
