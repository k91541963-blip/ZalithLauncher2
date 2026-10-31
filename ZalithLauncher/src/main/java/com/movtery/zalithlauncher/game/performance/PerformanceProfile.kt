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
 * A named bundle of launcher settings that can be applied and rolled back as a unit.
 *
 * The launcher only changes what the user explicitly applies. Nothing here is enforced at
 * launch time, so an existing setup keeps working after an update. Titles and descriptions
 * live in the UI layer so every language can be translated.
 *
 * @param ramMb JVM heap size, or `null` to keep the current value
 * @param jvmArgs extra JVM arguments, empty string means "no extra arguments"
 * @param rendererId preferred renderer id, or `null` to keep the current renderer
 * @param resolutionRatio render scale percentage, or `null` to keep the current value
 */
data class PerformancePreset(
    val id: String,
    val ramMb: Int?,
    val jvmArgs: String,
    val rendererId: String?,
    val resolutionRatio: Int?
)

/**
 * Built-in presets.
 *
 * These are starting points built from the device profile, not performance promises.
 * Launcher-side presets (memory, GC, resolution) do not raise the game's frame rate by
 * themselves; they only leave more resources for the game and avoid settings the device
 * cannot handle.
 */
object PerformancePresets {

    const val ID_LOW_END = "low_end"
    const val ID_BALANCED = "balanced"
    const val ID_QUALITY = "quality"

    fun forProfile(profile: HardwareProfile): List<PerformancePreset> {
        val advice = MemoryAdvisor.advise(profile)
        val lowEndRam = (advice.recommendedMb * 3 / 4).coerceAtLeast(MemoryAdvisor.MIN_MB)
        val qualityRam = advice.maxMb

        return listOf(
            PerformancePreset(
                id = ID_LOW_END,
                ramMb = lowEndRam,
                jvmArgs = "-XX:+UseSerialGC -XX:MaxMetaspaceSize=128m -XX:+DisableExplicitGC",
                rendererId = null,
                resolutionRatio = 75
            ),
            PerformancePreset(
                id = ID_BALANCED,
                ramMb = advice.recommendedMb,
                jvmArgs = "-XX:+UseG1GC -XX:MaxGCPauseMillis=50 -XX:MaxMetaspaceSize=192m",
                rendererId = null,
                resolutionRatio = 100
            ),
            PerformancePreset(
                id = ID_QUALITY,
                ramMb = qualityRam,
                jvmArgs = "-XX:+UseG1GC -XX:G1NewSizePercent=20 -XX:MaxMetaspaceSize=256m",
                rendererId = null,
                resolutionRatio = 100
            )
        )
    }

    fun find(profile: HardwareProfile, id: String): PerformancePreset? =
        forProfile(profile).firstOrNull { it.id == id }
}
