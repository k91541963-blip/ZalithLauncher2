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
 * launch time, so an existing setup keeps working after an update.
 *
 * @param ramMb JVM heap size, or `null` to keep the current value
 * @param jvmArgs extra JVM arguments, empty string means "no extra arguments"
 * @param rendererId preferred renderer id, or `null` to keep the current renderer
 * @param lowEndUi true when the lighter UI mode should be enabled
 */
data class PerformancePreset(
    val id: String,
    val displayName: String,
    val description: String,
    val ramMb: Int?,
    val jvmArgs: String,
    val rendererId: String?,
    val lowEndUi: Boolean
)

/**
 * Built-in presets.
 *
 * These are starting points built from the device profile, not performance promises.
 * Launcher-side optimisations (UI, background work, memory pressure) do not raise the
 * game's frame rate by themselves; they only leave more resources for the game.
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
                displayName = "Düşük donanım",
                description = "Az RAM, seri GC, hafif arayüz. Eski ve giriş seviyesi cihazlar için.",
                ramMb = lowEndRam,
                jvmArgs = "-XX:+UseSerialGC -XX:MaxMetaspaceSize=128m -XX:+DisableExplicitGC",
                rendererId = null,
                lowEndUi = true
            ),
            PerformancePreset(
                id = ID_BALANCED,
                displayName = "Dengeli",
                description = "Önerilen bellek, G1 GC. Çoğu cihaz için güvenli varsayılan.",
                ramMb = advice.recommendedMb,
                jvmArgs = "-XX:+UseG1GC -XX:MaxGCPauseMillis=50 -XX:MaxMetaspaceSize=192m",
                rendererId = null,
                lowEndUi = false
            ),
            PerformancePreset(
                id = ID_QUALITY,
                displayName = "Yüksek kalite",
                description = "Bu cihaz için güvenli üst sınır. Yalnızca 6 GB ve üzeri RAM'de anlamlı.",
                ramMb = qualityRam,
                jvmArgs = "-XX:+UseG1GC -XX:G1NewSizePercent=20 -XX:MaxMetaspaceSize=256m",
                rendererId = null,
                lowEndUi = false
            )
        )
    }

    fun find(profile: HardwareProfile, id: String): PerformancePreset? =
        forProfile(profile).firstOrNull { it.id == id }
}
