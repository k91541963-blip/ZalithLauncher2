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
import com.movtery.zalithlauncher.game.performance.GpuInfoReader
import com.movtery.zalithlauncher.game.performance.MaliTier
import com.movtery.zalithlauncher.setting.enums.AetherRenderMode

/**
 * A recommendation for one detected Mali part.
 *
 * This is a naming heuristic plus a conservative preset, **not** a benchmark. The user
 * always confirms it; nothing is applied silently.
 */
data class AetherGpuPreset(
    /** Canonical model name such as `Mali-G76`, or `null` when it could not be read. */
    val model: String?,
    val tier: MaliTier,
    val mode: AetherRenderMode,
    val tuning: AetherTuning,
    val noteRes: Int
)

/**
 * AetherRender 3 — per-GPU starting points for the parts named in the 3.0 spec.
 *
 * Rules that keep this honest:
 *  - the EGL mode is always [AetherRenderMode.BALANCED]; a 16-bit or stencil-less window
 *    surface is never requested (it rendered the world white on affected drivers);
 *  - unknown GPUs get [AetherTuning.CORE_DEFAULT] so the launcher does not invent
 *    optimisation its author cannot test;
 *  - entry-level parts (Mali-G57/G68, common on 3–4 GB phones) get the memory preset
 *    instead of the throughput preset, since they usually run out of GPU memory first.
 */
object AetherGpuProfiles {

    /** Exact per-model tuning, keyed by the model token after `Mali-`. */
    private val perModel: Map<String, AetherTuning> = mapOf(
        "G57" to AetherTuning.MEMORY_SAVER,
        "G68" to AetherTuning.MEMORY_SAVER,
        "G76" to AetherTuning.THROUGHPUT,
        "G78" to AetherTuning.THROUGHPUT,
        "G710" to AetherTuning.THROUGHPUT,
        "G715" to AetherTuning.THROUGHPUT
    )

    /**
     * Builds a recommendation for [model] (`Mali-G76 MC4`, `Mali-G57`, or a full driver
     * string — anything [GpuInfoReader.parseMaliModel] understands).
     */
    fun recommend(model: String?): AetherGpuPreset {
        val parsed = GpuInfoReader.parseMaliModel(model)
        val tier = GpuInfoReader.classifyMali(parsed)
        val token = parsed
            ?.substringAfter("Mali-", "")
            ?.substringBefore(' ')
            ?.uppercase()
            .orEmpty()

        val tuning = perModel[token] ?: when (tier) {
            MaliTier.ENTRY -> AetherTuning.MEMORY_SAVER
            MaliTier.MID, MaliTier.HIGH -> AetherTuning.THROUGHPUT
            MaliTier.UNKNOWN -> AetherTuning.CORE_DEFAULT
        }

        return AetherGpuPreset(
            model = parsed,
            tier = tier,
            mode = AetherRenderMode.BALANCED,
            tuning = tuning,
            noteRes = when (tier) {
                MaliTier.ENTRY -> R.string.aether_gpu_note_entry
                MaliTier.MID -> R.string.aether_gpu_note_mid
                MaliTier.HIGH -> R.string.aether_gpu_note_high
                MaliTier.UNKNOWN -> R.string.aether_gpu_note_unknown
            }
        )
    }
}
