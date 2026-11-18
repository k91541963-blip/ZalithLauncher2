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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PerformanceAdvisorTest {

    private fun session(
        fps: Double = 60.0,
        low: Double = fps * 0.8,
        jank: Double = 2.0,
        frames: Int = 36_000,
        duration: Int = 600,
        peakRam: Int = 800,
        totalRam: Int = 4_000,
        cpu: Double? = null,
        temperature: Float? = null
    ) = SessionHistoryEntry(
        versionName = "1.21.4",
        rendererMode = "aether",
        startedAtEpochMs = 1_000L,
        endedAtEpochMs = 1_000L + duration * 1_000L,
        durationSeconds = duration,
        framesCaptured = frames,
        averageFps = fps,
        onePercentLowFps = low,
        p99FrameMs = 1000.0 / fps,
        jankPercent = jank,
        averageCpuPercent = cpu,
        peakRamUsedMb = peakRam,
        totalRamMb = totalRam,
        peakTemperatureCelsius = temperature
    )

    private fun analyze(
        entry: SessionHistoryEntry,
        tuning: AetherTuning = AetherTuning.CORE_DEFAULT,
        vsync: Boolean = false
    ) = PerformanceAdvisor.analyze(entry, tuning, vsync)

    @Test
    fun `session without frames yields only a no-frames finding`() {
        val findings = analyze(session(frames = 0, duration = 0))

        assertEquals(1, findings.size)
        val evidence = findings.single().evidence
        assertTrue(evidence is AdvisorEvidence.NoFrames)
        assertEquals(AdvisorSeverity.INFO, findings.single().severity)
        assertEquals(AdvisorAction.NONE, findings.single().action)
    }

    @Test
    fun `smooth session reports no action`() {
        val findings = analyze(session(fps = 60.0, jank = 2.0))

        val smooth = findings.filterIsInstance<AdvisorFinding>()
            .single { it.evidence is AdvisorEvidence.Smooth }
        assertEquals(AdvisorSeverity.INFO, smooth.severity)
        assertEquals(AdvisorAction.NONE, smooth.action)
        assertTrue(findings.none { it.action != AdvisorAction.NONE })
    }

    @Test
    fun `smooth threshold is inclusive`() {
        val findings = analyze(
            session(fps = PerformanceAdvisor.SMOOTH_FPS, jank = PerformanceAdvisor.SMOOTH_MAX_JANK)
        )
        assertTrue(findings.any { it.evidence is AdvisorEvidence.Smooth })
    }

    @Test
    fun `low one percent flags stutter and proposes throughput`() {
        val findings = analyze(session(fps = 40.0, low = 10.0, jank = 4.0))

        val stutter = findings.single { it.evidence is AdvisorEvidence.Stutter }
        assertEquals(AdvisorSeverity.WARNING, stutter.severity)
        assertEquals(AdvisorAction.USE_THROUGHPUT, stutter.action)
        val evidence = stutter.evidence as AdvisorEvidence.Stutter
        assertTrue(evidence.lowOnePercent)
        assertTrue(!evidence.highJank)
    }

    @Test
    fun `high jank alone flags stutter`() {
        val findings = analyze(session(fps = 50.0, low = 45.0, jank = 12.0))

        val evidence = findings.single { it.evidence is AdvisorEvidence.Stutter }
            .evidence as AdvisorEvidence.Stutter
        assertTrue(evidence.highJank)
        assertTrue(!evidence.lowOnePercent)
    }

    @Test
    fun `already smooth tuning does not repeat itself`() {
        val findings = analyze(
            session(fps = 40.0, low = 10.0),
            tuning = AetherTuning.THROUGHPUT
        )
        val stutter = findings.single { it.evidence is AdvisorEvidence.Stutter }
        assertEquals(AdvisorAction.NONE, stutter.action)
    }

    @Test
    fun `memory pressure proposes memory saver`() {
        val findings = analyze(session(fps = 60.0, jank = 2.0, peakRam = 3_600, totalRam = 4_000))

        val memory = findings.single { it.evidence is AdvisorEvidence.MemoryPressure }
        assertEquals(AdvisorSeverity.WARNING, memory.severity)
        assertEquals(AdvisorAction.USE_MEMORY_SAVER, memory.action)
        assertEquals(
            3_600,
            (memory.evidence as AdvisorEvidence.MemoryPressure).peakUsedMb
        )
    }

    @Test
    fun `memory saver already active is not proposed again`() {
        val findings = analyze(
            session(peakRam = 3_600, totalRam = 4_000),
            tuning = AetherTuning.MEMORY_SAVER
        )
        val memory = findings.single { it.evidence is AdvisorEvidence.MemoryPressure }
        assertEquals(AdvisorAction.NONE, memory.action)
    }

    @Test
    fun `unknown total ram never reports pressure`() {
        val findings = analyze(session(peakRam = 3_600, totalRam = 0))
        assertTrue(findings.none { it.evidence is AdvisorEvidence.MemoryPressure })
    }

    @Test
    fun `refresh-locked average suggests disabling vsync`() {
        val findings = analyze(session(fps = 60.0, jank = 2.0), vsync = true)

        val locked = findings.single { it.evidence is AdvisorEvidence.RefreshLocked }
        assertEquals(AdvisorSeverity.SUGGESTION, locked.severity)
        assertEquals(AdvisorAction.DISABLE_VSYNC, locked.action)
    }

    @Test
    fun `refresh lock is not reported when vsync is off`() {
        val findings = analyze(session(fps = 60.0, jank = 2.0), vsync = false)
        assertTrue(findings.none { it.evidence is AdvisorEvidence.RefreshLocked })
    }

    @Test
    fun `high temperature is informational only`() {
        val findings = analyze(
            session(fps = 40.0, low = 35.0, jank = 2.0, temperature = 52f)
        )

        val heat = findings.single { it.evidence is AdvisorEvidence.HighTemperature }
        assertEquals(AdvisorSeverity.INFO, heat.severity)
        assertEquals(AdvisorAction.NONE, heat.action)
    }

    @Test
    fun `high cpu with low fps is informational only`() {
        val findings = analyze(
            session(fps = 30.0, low = 28.0, jank = 2.0, cpu = 92.0)
        )

        val cpu = findings.single { it.evidence is AdvisorEvidence.HighCpu }
        assertEquals(AdvisorSeverity.INFO, cpu.severity)
        assertEquals(AdvisorAction.NONE, cpu.action)
    }

    @Test
    fun `high cpu while smooth is not reported`() {
        val findings = analyze(session(fps = 60.0, jank = 2.0, cpu = 95.0))
        assertTrue(findings.none { it.evidence is AdvisorEvidence.HighCpu })
    }

    @Test
    fun `missing optional samples are never invented`() {
        val findings = analyze(session(cpu = null, temperature = null))
        assertTrue(findings.none { it.evidence is AdvisorEvidence.HighCpu })
        assertTrue(findings.none { it.evidence is AdvisorEvidence.HighTemperature })
    }
}
