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

package com.movtery.zalithlauncher.game.renderer.aether

import com.movtery.zalithlauncher.R

/**
 * AetherRender 3 — tuning presets for the bundled NG-GL4ES core.
 *
 * Every key in [env] is read by the shipped `libng_gl4es.so`; the variable names were
 * verified by scanning the binary, so a preset can never set a variable the core would
 * silently ignore. [CORE_DEFAULT] sets nothing at all, which reproduces the 2.x
 * behaviour exactly — picking it is always a safe way back.
 *
 * Presets change how the translator feeds the driver (batching, VBO usage, buffer
 * recycling, texture depth). They never change Minecraft's chunk renderer and come with
 * no FPS promise; the effect is measured, not assumed.
 *
 * @param nameRes label shown in the renderer settings
 * @param summaryRes one-line explanation of what the preset really changes
 * @param env environment variables passed to the game process
 */
enum class AetherTuning(
    val nameRes: Int,
    val summaryRes: Int,
    val env: Map<String, String>
) {
    /** No variables set: the core's own defaults. */
    CORE_DEFAULT(
        R.string.aether_tuning_core,
        R.string.aether_tuning_core_summary,
        emptyMap()
    ),

    /**
     * Batched immediate mode with VBO storage and framebuffer object recycling.
     * Aimed at GPUs that can afford the extra buffers (Mali-G76 and newer).
     */
    THROUGHPUT(
        R.string.aether_tuning_throughput,
        R.string.aether_tuning_throughput_summary,
        linkedMapOf(
            "LIBGL_BATCH" to "1",
            "LIBGL_USEVBO" to "1",
            "LIBGL_RECYCLEFBO" to "1"
        )
    ),

    /**
     * Halves texture memory (16-bit) and drops the VAO cache. For 3–4 GB devices where
     * the GPU allocation limit, not the GPU speed, is the bottleneck. Colours may band.
     */
    MEMORY_SAVER(
        R.string.aether_tuning_memory,
        R.string.aether_tuning_memory_summary,
        linkedMapOf(
            "LIBGL_FORCE16BITS" to "1",
            "LIBGL_NOVAOCACHE" to "1"
        )
    );

    /** Only the variables this preset actually sets, for the settings card. */
    val envKeys: List<String>
        get() = env.keys.toList()
}
