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

import com.movtery.zalithlauncher.game.renderer.aether.AetherTuning
import kotlin.math.abs

enum class AdvisorSeverity {
    /** Measured fact; no change is needed or proposed. */
    INFO,
    /** A change might help; the user decides. */
    SUGGESTION,
    /** A measured problem worth acting on. */
    WARNING
}

/** The concrete setting change a finding may propose. Never applied automatically. */
enum class AdvisorAction {
    USE_THROUGHPUT,
    USE_MEMORY_SAVER,
    DISABLE_VSYNC,
    NONE
}

/** What was measured. Every number here comes from the session file. */
sealed interface AdvisorEvidence {
    data class NoFrames(val framesCaptured: Int, val durationSeconds: Int) : AdvisorEvidence

    data class Smooth(val averageFps: Double, val jankPercent: Double) : AdvisorEvidence

    data class Stutter(
        val averageFps: Double,
        val onePercentLowFps: Double,
        val jankPercent: Double,
        val lowOnePercent: Boolean,
        val highJank: Boolean
    ) : AdvisorEvidence

    data class MemoryPressure(val peakUsedMb: Int, val totalMb: Int) : AdvisorEvidence

    /** The average sits exactly on 30/60/120 FPS while VSync is on. */
    data class RefreshLocked(val averageFps: Double) : AdvisorEvidence

    data class HighTemperature(val celsius: Float) : AdvisorEvidence

    data class HighCpu(val cpuPercent: Double, val averageFps: Double) : AdvisorEvidence
}

data class AdvisorFinding(
    val evidence: AdvisorEvidence,
    val severity: AdvisorSeverity,
    val action: AdvisorAction
)

/**
 * AetherLite 3.0 — measurement-based advisor.
 *
 * Deliberately not a "smart" black box:
 *  - it only reads one measured session, nothing is estimated;
 *  - thresholds are constants in this file, visible and testable;
 *  - it never applies a setting, and it never proposes a variable the renderer cannot
 *    really use: the only actions are existing, user-visible settings;
 *  - a suggestion for a setting that is already active is downgraded to [AdvisorAction.NONE].
 */
object PerformanceAdvisor {

    /** A session at or above this average, with low jank, is reported as already smooth. */
    const val SMOOTH_FPS = 55.0
    const val SMOOTH_MAX_JANK = 5.0

    /** The slowest 1% below this fraction of the average signals uneven frame pacing. */
    const val LOW_ONE_PERCENT_RATIO = 0.5

    const val HIGH_JANK = 10.0
    const val MEMORY_PRESSURE_RATIO = 0.85
    const val HIGH_TEMPERATURE_CELSIUS = 45f
    const val HIGH_CPU_PERCENT = 85.0

    /** Tolerance for "this looks refresh-locked" (30/60/120 Hz displays). */
    const val REFRESH_LOCK_TOLERANCE_FPS = 2.0
    private val REFRESH_RATES = listOf(30.0, 60.0, 120.0)

    fun analyze(
        session: SessionHistoryEntry,
        currentTuning: AetherTuning,
        vsyncEnabled: Boolean
    ): List<AdvisorFinding> {
        if (!session.hasFrames) {
            return listOf(
                AdvisorFinding(
                    evidence = AdvisorEvidence.NoFrames(
                        framesCaptured = session.framesCaptured,
                        durationSeconds = session.durationSeconds
                    ),
                    severity = AdvisorSeverity.INFO,
                    action = AdvisorAction.NONE
                )
            )
        }

        val findings = mutableListOf<AdvisorFinding>()
        val smooth = session.averageFps >= SMOOTH_FPS &&
            session.jankPercent <= SMOOTH_MAX_JANK

        if (smooth) {
            findings += AdvisorFinding(
                evidence = AdvisorEvidence.Smooth(session.averageFps, session.jankPercent),
                severity = AdvisorSeverity.INFO,
                action = AdvisorAction.NONE
            )
        }

        val lowOnePercent = session.onePercentLowFps > 0.0 &&
            session.averageFps > 0.0 &&
            session.onePercentLowFps < session.averageFps * LOW_ONE_PERCENT_RATIO
        val highJank = session.jankPercent >= HIGH_JANK

        if (!smooth && (lowOnePercent || highJank)) {
            findings += AdvisorFinding(
                evidence = AdvisorEvidence.Stutter(
                    averageFps = session.averageFps,
                    onePercentLowFps = session.onePercentLowFps,
                    jankPercent = session.jankPercent,
                    lowOnePercent = lowOnePercent,
                    highJank = highJank
                ),
                severity = AdvisorSeverity.WARNING,
                action = if (currentTuning == AetherTuning.THROUGHPUT) {
                    AdvisorAction.NONE
                } else {
                    AdvisorAction.USE_THROUGHPUT
                }
            )
        }

        val total = session.totalRamMb
        if (total > 0 && session.peakRamUsedMb.toDouble() / total >= MEMORY_PRESSURE_RATIO) {
            findings += AdvisorFinding(
                evidence = AdvisorEvidence.MemoryPressure(
                    peakUsedMb = session.peakRamUsedMb,
                    totalMb = total
                ),
                severity = AdvisorSeverity.WARNING,
                action = if (currentTuning == AetherTuning.MEMORY_SAVER) {
                    AdvisorAction.NONE
                } else {
                    AdvisorAction.USE_MEMORY_SAVER
                }
            )
        }

        if (vsyncEnabled && REFRESH_RATES.any {
                abs(session.averageFps - it) <= REFRESH_LOCK_TOLERANCE_FPS
            }
        ) {
            findings += AdvisorFinding(
                evidence = AdvisorEvidence.RefreshLocked(session.averageFps),
                severity = AdvisorSeverity.SUGGESTION,
                action = AdvisorAction.DISABLE_VSYNC
            )
        }

        session.peakTemperatureCelsius
            ?.takeIf { it >= HIGH_TEMPERATURE_CELSIUS }
            ?.let { celsius ->
                findings += AdvisorFinding(
                    evidence = AdvisorEvidence.HighTemperature(celsius),
                    severity = AdvisorSeverity.INFO,
                    action = AdvisorAction.NONE
                )
            }

        session.averageCpuPercent
            ?.takeIf { it >= HIGH_CPU_PERCENT && session.averageFps < SMOOTH_FPS }
            ?.let { cpu ->
                findings += AdvisorFinding(
                    evidence = AdvisorEvidence.HighCpu(cpu, session.averageFps),
                    severity = AdvisorSeverity.INFO,
                    action = AdvisorAction.NONE
                )
            }

        return findings
    }
}
