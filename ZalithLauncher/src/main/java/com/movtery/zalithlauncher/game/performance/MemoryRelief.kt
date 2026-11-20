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
 * AetherLite 3.0.2 — lowers the world/graphics load in `options.txt`.
 *
 * When the Java heap is already at the device's safe maximum, a bigger `-Xmx` is either
 * impossible or makes Android kill the app. The remaining real lever is how much world
 * the game has to keep in memory:
 *
 *  - `renderDistance` decides how many chunks are loaded around the player,
 *  - `simulationDistance` how many of them tick,
 *  - `graphicsMode` 2 ("fabulous") allocates extra framebuffers for transparency,
 *  - `entityShadows` adds a shadow pass.
 *
 * The edit is deliberately one-way: values are only ever lowered, never raised, and no
 * unknown key is touched. Nothing is written when the file is missing or already at or
 * below the targets, and the caller must have asked the user first.
 */
object MemoryRelief {

    const val RENDER_DISTANCE = 8
    const val SIMULATION_DISTANCE = 5

    /** `1` = fancy. Fabulous (`2`) is the only value downgraded. */
    const val GRAPHICS_MODE_FANCY = 1

    /**
     * Rewrites [file] in place when it contains a setting above the relief targets.
     *
     * @return true when the file was changed
     */
    fun apply(file: File): Boolean {
        if (!file.isFile) return false

        val lines = runCatching { file.readLines() }.getOrNull() ?: return false
        // A file without a single "key:value" line is not options.txt; leave it alone.
        if (lines.none { it.indexOf(':') > 0 }) return false

        var changed = false
        val seen = mutableSetOf<String>()

        fun clamped(line: String, key: String, cap: Int): String {
            seen += key
            val current = line.substringAfter(':', "").trim().toIntOrNull() ?: return line
            if (current <= cap) return line
            changed = true
            return "$key:$cap"
        }

        val rewritten = lines.map { line ->
            val idx = line.indexOf(':')
            if (idx <= 0) return@map line
            when (val key = line.substring(0, idx)) {
                "renderDistance" -> clamped(line, key, RENDER_DISTANCE)
                "simulationDistance" -> clamped(line, key, SIMULATION_DISTANCE)
                "graphicsMode" -> clamped(line, key, GRAPHICS_MODE_FANCY)
                "entityShadows" -> {
                    seen += key
                    if (line.substring(idx + 1).trim().equals("true", ignoreCase = true)) {
                        changed = true
                        "$key:false"
                    } else {
                        line
                    }
                }
                else -> line
            }
        }.toMutableList()

        // Keys that are absent fall back to Minecraft's defaults, which are higher than
        // these targets on most installs; add them explicitly so the relief is real.
        fun appendIfMissing(key: String, value: String) {
            if (key in seen) return
            rewritten += "$key:$value"
            changed = true
        }
        appendIfMissing("renderDistance", RENDER_DISTANCE.toString())
        appendIfMissing("simulationDistance", SIMULATION_DISTANCE.toString())

        if (!changed) return false

        return runCatching {
            val temp = File(file.parentFile, "${file.name}.tmp")
            temp.writeText(rewritten.joinToString("\n"))
            if (!temp.renameTo(file)) {
                temp.delete()
                return false
            }
            true
        }.getOrDefault(false)
    }
}
