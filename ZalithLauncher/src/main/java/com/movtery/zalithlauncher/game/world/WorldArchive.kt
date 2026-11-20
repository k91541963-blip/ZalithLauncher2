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

package com.movtery.zalithlauncher.game.world

import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/** A `worlds-*.zip` archive in `<gameDir>/backups`. */
data class WorldBackup(
    val file: File,
    val createdAt: Long,
    val sizeBytes: Long,
    /** World folder names found in the archive, in archive order. */
    val worldNames: List<String>
)

/** Outcome of restoring an archive. Never throws on malformed input. */
sealed interface RestoreResult {
    /**
     * @param worlds world folders written to `saves`
     * @param preserved existing folders renamed to `<name>.before-restore-<ts>` first
     */
    data class Success(val worlds: List<String>, val preserved: List<String>) : RestoreResult

    data class Failure(val reason: Reason) : RestoreResult {
        enum class Reason {
            MISSING,
            NOT_A_ZIP,
            NO_WORLD
        }
    }
}

/**
 * AetherLite 3.0.2 (Aşama 3E) — world backup and restore.
 *
 * The editor already offered "back up all worlds", but the resulting `worlds-*.zip`
 * could only be imported through the generic single-world importer, which expects one
 * `level.dat` and looks for it by name. A bundle of many worlds therefore could not be
 * restored. This helper owns the whole round trip:
 *
 *  - `backupAll` writes every file under `saves/` with the world folder as top segment,
 *  - `restore` accepts that layout (several worlds), a single world at the archive root,
 *    or an archive with a leading `saves/` segment,
 *  - an existing world folder is **renamed, never deleted**, so a restore cannot lose data.
 *
 * Malformed archives, absolute paths and `..` entries are rejected before anything is
 * written (zip-slip). All methods are plain JVM code and unit-tested outside Android.
 */
object WorldArchive {

    const val BACKUP_DIR = "backups"
    const val LEVEL_FILE = "level.dat"

    /** Directory that stores archives next to `saves`. */
    fun backupDir(savesDir: File): File = File(savesDir.parentFile, BACKUP_DIR)

    /** Same naming scheme the editor already uses. */
    fun defaultBackupFile(savesDir: File, now: Long = System.currentTimeMillis()): File =
        File(backupDir(savesDir), "worlds-$now.zip")

    /** Top-level folders in `saves` that contain a `level.dat`. */
    fun worldNames(savesDir: File): List<String> =
        savesDir.listFiles()
            ?.filter { it.isDirectory && File(it, LEVEL_FILE).isFile }
            ?.map { it.name }
            ?.sorted()
            ?: emptyList()

    /**
     * Zips every file under [savesDir]. World folders become the top-level entries, so
     * [restore] can put each one back independently.
     *
     * @throws IllegalArgumentException when [savesDir] is not a directory
     */
    fun backupAll(savesDir: File, output: File = defaultBackupFile(savesDir)): File {
        require(savesDir.isDirectory) { "saves directory does not exist: $savesDir" }
        output.parentFile?.mkdirs()

        ZipOutputStream(output.outputStream().buffered()).use { zip ->
            savesDir.walkTopDown()
                .filter { it.isFile }
                .forEach { file ->
                    val entryName = file.relativeTo(savesDir).invariantSeparatorsPath
                    zip.putNextEntry(ZipEntry(entryName).apply { time = file.lastModified() })
                    file.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                }
        }
        return output
    }

    /** Existing archives, newest first. Unreadable archives are skipped, not guessed. */
    fun listBackups(savesDir: File): List<WorldBackup> {
        val dir = backupDir(savesDir)
        return dir.listFiles()
            ?.filter { it.isFile && it.name.endsWith(".zip", ignoreCase = true) }
            ?.mapNotNull { file ->
                val names = runCatching {
                    ZipFile(file).use { zip ->
                        zip.entries().asSequence()
                            .map { it.name.replace('\\', '/') }
                            .filter { it.endsWith("/$LEVEL_FILE") }
                            .map { it.substringBefore('/') }
                            .distinct()
                            .toList()
                    }
                }.getOrNull() ?: return@mapNotNull null
                WorldBackup(
                    file = file,
                    createdAt = file.lastModified(),
                    sizeBytes = file.length(),
                    worldNames = names
                )
            }
            ?.sortedByDescending { it.createdAt }
            ?: emptyList()
    }

    /**
     * Restores every world in [archive] into [savesDir].
     *
     * A world folder that already exists is renamed to `<name>.before-restore-<now>`
     * before the archive is extracted, so the old copy is never overwritten in place.
     */
    fun restore(
        archive: File,
        savesDir: File,
        now: Long = System.currentTimeMillis()
    ): RestoreResult {
        if (!archive.isFile) return RestoreResult.Failure(RestoreResult.Failure.Reason.MISSING)

        return runCatching {
            ZipFile(archive).use { zip ->
                val entries = zip.entries().asSequence()
                    .mapNotNull { entry ->
                        safeEntryName(entry.name)?.let { name -> name to entry }
                    }
                    .toList()
                if (entries.isEmpty()) {
                    RestoreResult.Failure(RestoreResult.Failure.Reason.NO_WORLD)
                } else {
                    copyWorlds(entries, zip, savesDir, archive, now)
                }
            }
        }.getOrElse {
            // A directory, a truncated download or a non-zip file lands here.
            RestoreResult.Failure(RestoreResult.Failure.Reason.NOT_A_ZIP)
        }
    }

    /** Shapes an archive into `worldName -> relative entries`, then extracts each world. */
    private fun copyWorlds(
        entries: List<Pair<String, ZipEntry>>,
        zip: ZipFile,
        savesDir: File,
        archive: File,
        now: Long
    ): RestoreResult {
        val strip = entries.all { it.first.startsWith("saves/") }
        val normalized = entries.map { (name, entry) ->
            (if (strip) name.removePrefix("saves/") else name) to entry
        }

        val atRoot = normalized.any { (name, _) -> name == LEVEL_FILE }
        val groups: Map<String, List<Pair<String, ZipEntry>>> = if (atRoot) {
            mapOf(singleWorldName(archive) to normalized)
        } else {
            normalized.groupBy { it.first.substringBefore('/') }
                .filter { (world, list) ->
                    world.isNotBlank() && list.any { pair -> pair.first == "$world/$LEVEL_FILE" }
                }
        }

        if (groups.isEmpty()) {
            return RestoreResult.Failure(RestoreResult.Failure.Reason.NO_WORLD)
        }

        val restored = mutableListOf<String>()
        val preserved = mutableListOf<String>()

        for ((world, worldEntries) in groups) {
            if (!isSafeSegment(world)) continue

            val target = File(savesDir, world)
            if (target.exists()) {
                val kept = uniqueSibling(savesDir, "$world.before-restore-$now")
                if (target.renameTo(kept)) preserved += kept.name
                // If the rename fails the existing world stays as it is; do not overwrite it.
                else continue
            }
            if (!target.mkdirs() && !target.isDirectory) continue

            for ((name, entry) in worldEntries) {
                if (entry.isDirectory) continue
                val relative = name.removePrefix("$world/")
                if (relative.isBlank()) continue
                val destination = File(target, relative)
                if (!isInside(target, destination)) continue
                destination.parentFile?.mkdirs()
                zip.getInputStream(entry).use { input ->
                    destination.outputStream().buffered().use { output -> input.copyTo(output) }
                }
            }
            restored += world
        }

        return if (restored.isEmpty()) {
            RestoreResult.Failure(RestoreResult.Failure.Reason.NO_WORLD)
        } else {
            RestoreResult.Success(worlds = restored, preserved = preserved)
        }
    }

    /**
     * Normalizes an archive entry and rejects absolute paths and `..` segments.
     * Directory entries keep their trailing slash trimmed.
     */
    private fun safeEntryName(raw: String): String? {
        val name = raw.replace('\\', '/').removePrefix("./")
        if (name.isBlank()) return null
        if (name.startsWith("/")) return null
        if (name.length >= 2 && name[1] == ':') return null // C:\ style
        val parts = name.trimEnd('/').split('/')
        if (parts.isEmpty() || parts.any { it.isEmpty() || it == "." || it == ".." }) return null
        return parts.joinToString("/")
    }

    private fun isSafeSegment(name: String): Boolean =
        name.isNotBlank() && !name.contains('/') && !name.contains('\\') && name != "." && name != ".."

    /** Resolves an archive-relative path and proves it stays inside [root]. */
    private fun isInside(root: File, candidate: File): Boolean {
        val rootPath = root.canonicalFile.path + File.separator
        return candidate.canonicalFile.path.startsWith(rootPath)
    }

    private fun uniqueSibling(dir: File, base: String): File {
        var candidate = File(dir, base)
        var index = 1
        while (candidate.exists()) {
            candidate = File(dir, "$base-$index")
            index++
        }
        return candidate
    }

    /** `worlds-1712345678900.zip` -> `restored-1712345678900`, since it has no folder name. */
    private fun singleWorldName(archive: File): String =
        archive.nameWithoutExtension
            .removePrefix("worlds-")
            .let { if (it.isBlank()) "restored-world" else "restored-$it" }
}
