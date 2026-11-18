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

package com.movtery.zalithlauncher.game.home

import java.io.File

/**
 * A world directory that Minecraft itself would list in the singleplayer menu.
 *
 * @param name folder name, which is what the launcher can read without parsing NBT
 * @param dir the world folder (`saves/<name>`)
 * @param lastPlayedAt newest modification time of `level.dat` and the folder, epoch ms
 */
data class RecentWorld(
    val name: String,
    val dir: File,
    val lastPlayedAt: Long
)

/**
 * Reads the selected version's `saves` folder.
 *
 * Only folders that actually contain `level.dat` count as worlds — a stray folder or a
 * half-copied backup must not appear as "last played". No NBT is parsed, so the display
 * name is the folder name, which is exactly what the user sees on disk.
 */
object HomeWorlds {

    const val LEVEL_FILE = "level.dat"

    /** Newest world, or `null` when the folder is missing/empty. */
    fun findLatest(savesDir: File): RecentWorld? = scan(savesDir, limit = 1).firstOrNull()

    /** Newest worlds first, at most [limit] entries. */
    fun scan(savesDir: File, limit: Int = 5): List<RecentWorld> {
        if (limit <= 0) return emptyList()
        val dirs = savesDir.listFiles() ?: return emptyList()
        return dirs.asSequence()
            .filter { it.isDirectory }
            .mapNotNull { dir ->
                val level = File(dir, LEVEL_FILE)
                if (!level.isFile) return@mapNotNull null
                RecentWorld(
                    name = dir.name,
                    dir = dir,
                    lastPlayedAt = maxOf(level.lastModified(), dir.lastModified())
                )
            }
            .sortedWith(
                compareByDescending<RecentWorld> { it.lastPlayedAt }.thenBy { it.name }
            )
            .take(limit)
            .toList()
    }
}
