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

/**
 * AetherLite 3.0 — heap recommendation for modded instances.
 *
 * Vanilla Minecraft already works with the device-based default. A large modpack does
 * not: 50 mods on a 2 GB heap is the classic "Minecraft ran out of memory" screen. This
 * helper only proposes a higher value; nothing is applied without the user's tap.
 *
 * The proposed value never exceeds the device's safe maximum ([MemoryAdvisor.maxMb]) and
 * is rounded down to 64 MB, matching the memory slider.
 */
object MemoryRecommendation {

    const val MODERATE_MODS = 10
    const val HEAVY_MODS = 30

    const val LIGHT_MOD_FLOOR_MB = 2048
    const val MODERATE_MOD_FLOOR_MB = 2560
    const val HEAVY_MOD_FLOOR_MB = 3072

    /** A small bump is not worth interrupting the player for. */
    const val MIN_GAIN_MB = 256
    private const val ROUNDING_MB = 64

    /** Heap floor for a given mod count; `0` means "no mod-specific floor". */
    fun floorFor(modCount: Int): Int = when {
        modCount >= HEAVY_MODS -> HEAVY_MOD_FLOOR_MB
        modCount >= MODERATE_MODS -> MODERATE_MOD_FLOOR_MB
        modCount >= 1 -> LIGHT_MOD_FLOOR_MB
        else -> 0
    }

    /**
     * @return the heap to propose, or `null` when the current value is already adequate,
     *   the device cannot safely go higher, or the gain would be below [MIN_GAIN_MB].
     */
    fun recommendedMb(allocatedMb: Int, modCount: Int, maxMb: Int): Int? {
        val floor = floorFor(modCount)
        if (floor == 0 || maxMb <= 0) return null

        val target = maxOf(allocatedMb, floor).coerceAtMost(maxMb)
        val rounded = (target / ROUNDING_MB) * ROUNDING_MB
        return rounded.takeIf { it >= allocatedMb + MIN_GAIN_MB }
    }

    /** Counts enabled mod jars in `<gameDir>/mods`; missing directory means vanilla. */
    fun countMods(gameDir: File): Int {
        val modsDir = File(gameDir, "mods")
        if (!modsDir.isDirectory) return 0
        return modsDir.listFiles()?.count { file ->
            file.isFile && file.name.endsWith(".jar", ignoreCase = true)
        } ?: 0
    }
}
