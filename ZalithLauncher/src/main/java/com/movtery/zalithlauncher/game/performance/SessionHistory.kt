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

import android.content.Context
import com.google.gson.Gson
import java.io.File

/**
 * One measured session in the history. The 10-second FPS buckets are not stored: the
 * history is for comparing sessions, and dropping the graph keeps the file small.
 */
data class SessionHistoryEntry(
    val versionName: String = "",
    val rendererMode: String = "",
    val startedAtEpochMs: Long = 0L,
    val endedAtEpochMs: Long = 0L,
    val durationSeconds: Int = 0,
    val framesCaptured: Int = 0,
    val averageFps: Double = 0.0,
    val onePercentLowFps: Double = 0.0,
    val p99FrameMs: Double = 0.0,
    val jankPercent: Double = 0.0,
    val averageCpuPercent: Double? = null,
    val peakRamUsedMb: Int = 0,
    val totalRamMb: Int = 0,
    val peakTemperatureCelsius: Float? = null,
    val samplesTruncated: Boolean = false
) {
    /** A session without frames cannot support any performance statement. */
    val hasFrames: Boolean get() = framesCaptured > 0 && durationSeconds > 0

    companion object {
        fun from(summary: GameSessionSummary): SessionHistoryEntry = SessionHistoryEntry(
            versionName = summary.versionName,
            rendererMode = summary.rendererMode,
            startedAtEpochMs = summary.startedAtEpochMs,
            endedAtEpochMs = summary.endedAtEpochMs,
            durationSeconds = summary.durationSeconds,
            framesCaptured = summary.framesCaptured,
            averageFps = summary.averageFps,
            onePercentLowFps = summary.onePercentLowFps,
            p99FrameMs = summary.p99FrameMs,
            jankPercent = summary.jankPercent,
            averageCpuPercent = summary.averageCpuPercent,
            peakRamUsedMb = summary.peakRamUsedMb,
            totalRamMb = summary.totalRamMb,
            peakTemperatureCelsius = summary.peakTemperatureCelsius,
            samplesTruncated = summary.samplesTruncated
        )
    }
}

/**
 * Append-only session history under `filesDir/aethermetrics/sessions.json`.
 *
 * Newest first, bounded to [MAX_ENTRIES]. A corrupt or partially written file is treated
 * as "no history" and is replaced on the next append; the launcher must never crash
 * because of its own telemetry file.
 */
class SessionHistoryStore(
    private val file: File,
    private val maxEntries: Int = MAX_ENTRIES
) {
    companion object {
        const val MAX_ENTRIES = 20
        const val DIRECTORY = "aethermetrics"
        const val FILE_NAME = "sessions.json"

        fun forContext(context: Context): SessionHistoryStore = SessionHistoryStore(
            File(File(context.applicationContext.filesDir, DIRECTORY), FILE_NAME)
        )
    }

    /** All stored sessions, newest first. */
    fun all(): List<SessionHistoryEntry> = read().sortedByDescending { it.endedAtEpochMs }

    /** Newest [limit] sessions (0 → empty). */
    fun latest(limit: Int): List<SessionHistoryEntry> =
        if (limit <= 0) emptyList() else all().take(limit)

    /** Adds one session and returns the new newest-first list. */
    fun append(entry: SessionHistoryEntry): List<SessionHistoryEntry> {
        val merged = (all() + entry)
            .sortedByDescending { it.endedAtEpochMs }
            .take(maxEntries)
        write(merged)
        return merged
    }

    fun clear() {
        runCatching { file.delete() }
    }

    // --- io ----------------------------------------------------------------

    private fun read(): List<SessionHistoryEntry> {
        if (!file.isFile) return emptyList()
        val text = runCatching { file.readText() }.getOrNull() ?: return emptyList()
        val dtos = runCatching {
            Gson().fromJson(text, Array<EntryDto>::class.java)?.toList()
        }.getOrNull() ?: return emptyList()
        return dtos.map { it.toEntry() }
    }

    private fun write(entries: List<SessionHistoryEntry>) {
        runCatching {
            file.parentFile?.mkdirs()
            file.writeText(Gson().toJson(entries.map(EntryDto::from)))
        }
    }

    /**
     * Nullable mirror used for JSON so a truncated file cannot inject `null` into the
     * non-null model fields.
     */
    private data class EntryDto(
        val versionName: String? = null,
        val rendererMode: String? = null,
        val startedAtEpochMs: Long? = null,
        val endedAtEpochMs: Long? = null,
        val durationSeconds: Int? = null,
        val framesCaptured: Int? = null,
        val averageFps: Double? = null,
        val onePercentLowFps: Double? = null,
        val p99FrameMs: Double? = null,
        val jankPercent: Double? = null,
        val averageCpuPercent: Double? = null,
        val peakRamUsedMb: Int? = null,
        val totalRamMb: Int? = null,
        val peakTemperatureCelsius: Float? = null,
        val samplesTruncated: Boolean? = null
    ) {
        fun toEntry() = SessionHistoryEntry(
            versionName = versionName.orEmpty(),
            rendererMode = rendererMode.orEmpty(),
            startedAtEpochMs = startedAtEpochMs ?: 0L,
            endedAtEpochMs = endedAtEpochMs ?: 0L,
            durationSeconds = durationSeconds ?: 0,
            framesCaptured = framesCaptured ?: 0,
            averageFps = averageFps ?: 0.0,
            onePercentLowFps = onePercentLowFps ?: 0.0,
            p99FrameMs = p99FrameMs ?: 0.0,
            jankPercent = jankPercent ?: 0.0,
            averageCpuPercent = averageCpuPercent,
            peakRamUsedMb = peakRamUsedMb ?: 0,
            totalRamMb = totalRamMb ?: 0,
            peakTemperatureCelsius = peakTemperatureCelsius,
            samplesTruncated = samplesTruncated ?: false
        )

        companion object {
            fun from(entry: SessionHistoryEntry) = EntryDto(
                versionName = entry.versionName,
                rendererMode = entry.rendererMode,
                startedAtEpochMs = entry.startedAtEpochMs,
                endedAtEpochMs = entry.endedAtEpochMs,
                durationSeconds = entry.durationSeconds,
                framesCaptured = entry.framesCaptured,
                averageFps = entry.averageFps,
                onePercentLowFps = entry.onePercentLowFps,
                p99FrameMs = entry.p99FrameMs,
                jankPercent = entry.jankPercent,
                averageCpuPercent = entry.averageCpuPercent,
                peakRamUsedMb = entry.peakRamUsedMb,
                totalRamMb = entry.totalRamMb,
                peakTemperatureCelsius = entry.peakTemperatureCelsius,
                samplesTruncated = entry.samplesTruncated
            )
        }
    }
}
