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
import com.movtery.zalithlauncher.game.renderer.aether.AetherTuning
import com.movtery.zalithlauncher.setting.AllSettings
import com.movtery.zalithlauncher.setting.enums.AetherRenderMode

/**
 * AetherRender 2.1 — modern (26.x) Minecraft support.
 *
 * GL translation is the bundled NG-GL4ES core (`libng_gl4es.so`, the same engine the
 * launcher ships as "Krypton Wrapper"): desktop GL 3.1 is translated to GLES 3, which is
 * what Minecraft 1.17+ and the 26.x releases require. The older 1.1.4 core only offered
 * GL 2.1 and therefore stopped at 1.21.4; that core is still available through the
 * separate "GL4ES" renderer for legacy versions and GLES 2 devices.
 *
 * AetherRender adds a native EGL layer (`jni/aetherrender/aether_render.c`) that:
 *  - picks a tile-GPU friendly EGL config (no MSAA, 24-bit depth),
 *  - asks the driver to discard the back buffer after swap on tile-based GPUs,
 *  - applies one of five honest, selectable EGL policies (see [AetherRenderMode]),
 *  - records real frame times for the session report (`aether_metrics.c`),
 *  - logs the real GL strings for support reports.
 *
 * It makes no claim about FPS and does not replace Minecraft's chunk renderer. If the
 * driver rejects the surface policy the launch continues on the stock path.
 */
object AetherRenderer : RendererInterface {
    /**
     * The `opengles` prefix is what `egl_bridge.c` maps to the GL4ES/NG-GL4ES code path.
     * The `3` reflects the GLES 3 context this renderer now requests.
     */
    override fun getRendererId(): String = "opengles3_aether"

    override fun getUniqueIdentifier(): String = "1f0a7e2c-9b4d-4c8e-a1e6-7ae71e4b9c01"

    override fun getRendererName(): String = "AetherRender"

    /** Newest Minecraft generation this core is built for (snapshot builds included). */
    override fun getMaxMCVersion(): String = "26.3-snapshot-3"

    /** The last fully released version, shown in the UI instead of a snapshot tag. */
    override fun getDisplayMaxMCVersion(): String = "26.2"

    /**
     * The environment is built lazily on the launch path, so a mode change applies to
     * the next launch without restarting the launcher. Reading the setting is guarded
     * because unit tests do not initialize the settings storage.
     */
    override fun getRendererEnv(): Lazy<Map<String, String>> = lazy { buildEnv() }

    override fun getDlopenLibrary(): Lazy<List<String>> = lazy { emptyList() }

    override fun getRendererLibrary(): String = "libng_gl4es.so"

    private fun buildEnv(): Map<String, String> {
        val env = linkedMapOf(
            "AETHER_RENDER" to "1",
            "AETHER_RENDER_MODE" to currentMode().envValue,
            "AETHER_SWAP_INTERVAL" to if (currentVsync()) "1" else "0",
            // NG-GL4ES core: expose desktop GL 3.1 / GLES 3, keep Minecraft's colour path.
            "LIBGL_GL" to "31",
            "LIBGL_ES" to "3",
            "LIBGL_USE_MC_COLOR" to "1",
            "LIBGL_NORMALIZE" to "1",
            "LIBGL_NOERROR" to "1"
        )
        // AetherRender 3: only variables the bundled core actually reads (see AetherTuning).
        env.putAll(currentTuning().env)
        return env
    }

    private fun currentMode(): AetherRenderMode =
        runCatching { AllSettings.aetherRenderMode.state }
            .getOrDefault(AetherRenderMode.BALANCED)

    private fun currentTuning(): AetherTuning =
        runCatching { AllSettings.aetherRenderTuning.state }
            .getOrDefault(AetherTuning.CORE_DEFAULT)

    private fun currentVsync(): Boolean =
        runCatching { AllSettings.aetherRenderVsync.state }
            .getOrDefault(true)
}
