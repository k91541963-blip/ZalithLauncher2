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
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionHistoryStoreTest {

    private fun store(maxEntries: Int = SessionHistoryStore.MAX_ENTRIES): SessionHistoryStore {
        val dir = Files.createTempDirectory("aether-history").toFile()
        return SessionHistoryStore(File(dir, "sessions.json"), maxEntries)
    }

    private fun entry(endedAt: Long, fps: Double = 60.0) = SessionHistoryEntry(
        versionName = "1.21.4",
        rendererMode = "aether",
        startedAtEpochMs = endedAt - 600_000L,
        endedAtEpochMs = endedAt,
        durationSeconds = 600,
        framesCaptured = 36_000,
        averageFps = fps,
        onePercentLowFps = fps * 0.8,
        p99FrameMs = 1000.0 / fps,
        jankPercent = 2.0
    )

    @Test
    fun `missing file reads as empty history`() {
        val store = store()
        assertTrue(store.all().isEmpty())
        assertTrue(store.latest(5).isEmpty())
    }

    @Test
    fun `append keeps newest first`() {
        val store = store()
        store.append(entry(1_000L, fps = 30.0))
        store.append(entry(3_000L, fps = 60.0))
        store.append(entry(2_000L, fps = 45.0))

        val all = store.all()
        assertEquals(listOf(3_000L, 2_000L, 1_000L), all.map { it.endedAtEpochMs })
        assertEquals(60.0, all[0].averageFps, 0.0001)
    }

    @Test
    fun `append is bounded to the entry limit`() {
        val store = store(maxEntries = 3)
        (1..5).forEach { index -> store.append(entry(index * 1_000L)) }

        val all = store.all()
        assertEquals(3, all.size)
        assertEquals(listOf(5_000L, 4_000L, 3_000L), all.map { it.endedAtEpochMs })
    }

    @Test
    fun `default limit is twenty sessions`() {
        assertEquals(20, SessionHistoryStore.MAX_ENTRIES)
        val store = store()
        (1..25).forEach { index -> store.append(entry(index * 1_000L)) }
        assertEquals(20, store.all().size)
        assertEquals(25_000L, store.all().first().endedAtEpochMs)
    }

    @Test
    fun `latest returns requested count`() {
        val store = store()
        (1..4).forEach { index -> store.append(entry(index * 1_000L)) }

        assertEquals(2, store.latest(2).size)
        assertEquals(listOf(4_000L, 3_000L), store.latest(2).map { it.endedAtEpochMs })
        assertTrue(store.latest(0).isEmpty())
        assertTrue(store.latest(-1).isEmpty())
    }

    @Test
    fun `corrupt file reads as empty and is replaced on append`() {
        val dir = Files.createTempDirectory("aether-history-corrupt").toFile()
        val file = File(dir, "sessions.json")
        file.writeText("{ this is not json")

        val store = SessionHistoryStore(file)
        assertTrue(store.all().isEmpty())

        store.append(entry(1_000L))
        assertEquals(1, store.all().size)
        assertTrue(file.readText().trimStart().startsWith("["))
    }

    @Test
    fun `clear removes history`() {
        val store = store()
        store.append(entry(1_000L))
        store.clear()
        assertTrue(store.all().isEmpty())
    }

    @Test
    fun `summary conversion drops graph buckets and keeps measurements`() {
        val summary = GameSessionSummary(
            versionName = "1.21.4",
            rendererMode = "aether",
            startedAtEpochMs = 1_000L,
            endedAtEpochMs = 601_000L,
            durationSeconds = 600,
            framesCaptured = 36_000,
            averageFps = 60.0,
            onePercentLowFps = 48.0,
            p99FrameMs = 20.0,
            jankPercent = 2.5,
            averageCpuPercent = 42.0,
            peakRamUsedMb = 900,
            totalRamMb = 2_000,
            peakTemperatureCelsius = 41f,
            fpsBuckets = listOf(60.0, 59.0),
            samplesTruncated = false
        )

        val entry = SessionHistoryEntry.from(summary)
        assertEquals(summary.versionName, entry.versionName)
        assertEquals(summary.rendererMode, entry.rendererMode)
        assertEquals(summary.endedAtEpochMs, entry.endedAtEpochMs)
        assertEquals(summary.framesCaptured, entry.framesCaptured)
        assertEquals(summary.averageFps, entry.averageFps, 0.0001)
        assertEquals(summary.onePercentLowFps, entry.onePercentLowFps, 0.0001)
        assertEquals(summary.jankPercent, entry.jankPercent, 0.0001)
        assertEquals(summary.averageCpuPercent, entry.averageCpuPercent)
        assertEquals(summary.peakRamUsedMb, entry.peakRamUsedMb)
        assertEquals(summary.peakTemperatureCelsius, entry.peakTemperatureCelsius)
        assertTrue(entry.hasFrames)
    }

    @Test
    fun `session without frames has no measured data`() {
        assertFalse(entry(1_000L).copy(framesCaptured = 0).hasFrames)
        assertFalse(entry(1_000L).copy(durationSeconds = 0).hasFrames)
        assertTrue(entry(1_000L).hasFrames)
    }
}
