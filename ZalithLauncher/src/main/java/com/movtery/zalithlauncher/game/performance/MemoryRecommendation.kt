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

    /** What the user should actually do after an out-of-memory launch. */
    enum class Kind {
        /** Raise the heap to [MemoryAdvice.recommendedMb] and retry. */
        RAISE_HEAP,

        /**
         * The heap is already at the device's safe maximum (or the failure was in the
         * GPU/native allocator). A bigger heap is either impossible or useless, so the
         * honest fix is to lower the world/graphics load.
         */
        LOWER_GRAPHICS
    }

    data class Advice(
        val kind: Kind,
        val recommendedMb: Int? = null,
        /** True when a heap increase was impossible because the device cap was reached. */
        val atLimit: Boolean = false
    )

    /**
     * Chooses between "more heap" and "less world load" from the evidence.
     *
     * @param pressure classification of the log tail; [MemoryPressure.GRAPHICS] never
     *   produces a heap increase because the Java heap is not what ran out.
     */
    fun advise(
        allocatedMb: Int,
        modCount: Int,
        maxMb: Int,
        pressure: MemoryPressure
    ): Advice {
        if (pressure == MemoryPressure.GRAPHICS) {
            return Advice(kind = Kind.LOWER_GRAPHICS)
        }
        // A real heap OOM proves the current value is too small even on a vanilla
        // instance, so fall back to one conservative bump when the mod floor is silent.
        val recommended = recommendedMb(allocatedMb, modCount, maxMb)
            ?: postOomIncrease(allocatedMb, maxMb)
        if (recommended != null) {
            return Advice(kind = Kind.RAISE_HEAP, recommendedMb = recommended)
        }
        // Heap pressure with no safe increase available: the device cap is the limit.
        val atLimit = maxMb > 0 && allocatedMb >= maxMb - ROUNDING_MB
        return Advice(kind = Kind.LOWER_GRAPHICS, atLimit = atLimit)
    }

    /** One 512 MB step (rounded down) after an OOM, never above the device safe maximum. */
    private fun postOomIncrease(allocatedMb: Int, maxMb: Int): Int? {
        if (maxMb <= 0) return null
        val target = minOf(allocatedMb + 512, maxMb)
        val rounded = (target / ROUNDING_MB) * ROUNDING_MB
        return rounded.takeIf { it >= allocatedMb + MIN_GAIN_MB }
    }
}
