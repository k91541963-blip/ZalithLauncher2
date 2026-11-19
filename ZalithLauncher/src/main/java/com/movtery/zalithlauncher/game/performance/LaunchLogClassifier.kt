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
import java.io.RandomAccessFile

/**
 * AetherLite 3.0 — reads the end of `latest.log` to tell "out of memory" apart from a
 * renderer/GL failure.
 *
 * Only the tail is read: a modded log can be tens of megabytes, and the crash reason is
 * written at the end. The check is deliberately literal; a missing or unreadable log
 * never throws and never produces a claim.
 */
object LaunchLogClassifier {

    /** Enough for the stack trace of an OOM plus the surrounding mod list. */
    const val TAIL_BYTES = 256 * 1024

    /**
     * Markers written by Minecraft, the JVM or the bundled GL core. Matching is
     * case-insensitive because the JVM prints both `OutOfMemoryError` and
     * `java.lang.OutOfMemoryError: Java heap space`.
     */
    private val OUT_OF_MEMORY_MARKERS = listOf(
        "outofmemoryerror",
        "java heap space",
        "ran out of memory",
        "gl_out_of_memory"
    )

    fun isOutOfMemory(text: String): Boolean {
        if (text.isBlank()) return false
        val lower = text.lowercase()
        return OUT_OF_MEMORY_MARKERS.any { lower.contains(it) }
    }

    /** Reads the last [TAIL_BYTES] of [file]; returns an empty string when unreadable. */
    fun readTail(file: File, maxBytes: Int = TAIL_BYTES): String {
        if (!file.isFile || maxBytes <= 0) return ""
        return runCatching {
            RandomAccessFile(file, "r").use { raf ->
                val length = raf.length()
                val start = (length - maxBytes).coerceAtLeast(0L)
                val size = (length - start).toInt()
                if (size <= 0) return@use ""
                val buffer = ByteArray(size)
                raf.seek(start)
                raf.readFully(buffer)
                String(buffer, Charsets.UTF_8)
            }
        }.getOrDefault("")
    }

    /** True when the log file's tail contains an out-of-memory marker. */
    fun logShowsOutOfMemory(file: File): Boolean = isOutOfMemory(readTail(file))
}
