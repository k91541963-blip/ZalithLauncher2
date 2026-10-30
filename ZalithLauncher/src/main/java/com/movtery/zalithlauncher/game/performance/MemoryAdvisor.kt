/*
 * NovaLauncher
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

/**
 * Memory recommendation for the JVM.
 *
 * @param recommendedMb value that can be applied directly to the `ramAllocation` setting
 * @param minMb lowest value the launcher allows
 * @param maxMb highest value considered safe on this device
 * @param notes human readable explanation of the recommendation
 */
data class MemoryAdvice(
    val recommendedMb: Int,
    val minMb: Int,
    val maxMb: Int,
    val notes: List<String>
) {
    /** True when the user requested more memory than this device should give to the JVM. */
    fun isAboveSafeLimit(requestedMb: Int): Boolean = requestedMb > maxMb

    /** Clamps a user value into the safe range. */
    fun clamp(requestedMb: Int): Int = requestedMb.coerceIn(minMb, maxMb)
}

/**
 * Suggests a safe JVM heap size based on the detected [HardwareProfile].
 *
 * Two independent limits apply:
 *  - Android itself needs memory. Handing most of the RAM to the JVM makes the whole
 *    device swap and the game stutter, so a fraction of total RAM is reserved for the OS.
 *  - A 32-bit process can only address about 1 GB, so 32-bit devices are capped hard.
 *
 * The numbers are deliberately conservative. They are a starting point, not a guarantee:
 * actual usage depends on the Minecraft version, mod count and renderer.
 */
object MemoryAdvisor {

    const val MIN_MB = 256
    private const val OS_RESERVE_MB = 1024
    private const val ROUNDING_MB = 64
    private const val HARD_CAP_32_BIT_MB = 1024
    private const val HARD_CAP_MB = 4096

    fun advise(profile: HardwareProfile): MemoryAdvice {
        val notes = mutableListOf<String>()

        if (!profile.is64Bit) {
            val recommended = 696.coerceAtMost(profile.totalRamMb - OS_RESERVE_MB)
                .coerceAtLeast(MIN_MB)
            notes += "32-bit cihaz: JVM adres alanı ~1 GB ile sınırlı, daha fazlası verilemez."
            return MemoryAdvice(
                recommendedMb = roundDown(recommended),
                minMb = MIN_MB,
                maxMb = HARD_CAP_32_BIT_MB,
                notes = notes
            )
        }

        val base = when {
            profile.totalRamMb < 1536 -> 448
            profile.totalRamMb < 2048 -> 640
            profile.totalRamMb < 3072 -> 896
            profile.totalRamMb < 4096 -> 1152
            profile.totalRamMb < 6144 -> 1536
            profile.totalRamMb < 8192 -> 2048
            else -> 3072
        }

        val osReserve = if (profile.totalRamMb >= 6144) 2048 else OS_RESERVE_MB
        val safeByOs = profile.totalRamMb - osReserve
        val safeByFraction = (profile.totalRamMb * 0.45).toInt()
        val maxMb = minOf(safeByOs, safeByFraction, HARD_CAP_MB)
            .coerceAtLeast(MIN_MB)

        val recommended = roundDown(base.coerceIn(MIN_MB, maxMb))

        if (profile.totalRamMb < 4096) {
            notes += "Cihaz RAM'i düşük: 1.21+ sürümlerde 8 chunk render mesafesi önerilir."
        }
        if (profile.cpuCores <= 4) {
            notes += "Çekirdek sayısı düşük: arka plan uygulamalarını kapatmak bellek baskısını azaltır."
        }
        if (recommended < base) {
            notes += "Sistem için ayrılan pay nedeniyle öneri ${recommended} MB'a düşürüldü."
        }

        return MemoryAdvice(
            recommendedMb = recommended,
            minMb = MIN_MB,
            maxMb = maxMb,
            notes = notes
        )
    }

    private fun roundDown(value: Int): Int {
        val rounded = (value / ROUNDING_MB) * ROUNDING_MB
        return rounded.coerceAtLeast(MIN_MB)
    }
}
