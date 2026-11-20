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

import com.movtery.zalithlauncher.game.world.RestoreResult
import com.movtery.zalithlauncher.game.world.WorldArchive
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WorldArchiveTest {

    private fun tempDir(): File = Files.createTempDirectory("worlds").toFile()

    private fun zipOf(file: File, vararg entries: Pair<String, String>) {
        ZipOutputStream(file.outputStream()).use { zip ->
            entries.forEach { (name, content) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(content.toByteArray())
                zip.closeEntry()
            }
        }
    }

    private fun world(dir: File, name: String): File =
        File(dir, name).apply {
            mkdirs()
            File(this, WorldArchive.LEVEL_FILE).writeText("level-$name")
            File(this, "region").mkdirs()
            File(this, "region/r.0.0.mca").writeText("region-$name")
        }

    @Test
    fun `backup and restore round trips every world`() {
        val root = tempDir()
        val saves = File(root, "saves").apply { mkdirs() }
        world(saves, "alpha")
        world(saves, "beta")

        val archive = WorldArchive.backupAll(saves)
        assertTrue(archive.isFile)
        assertEquals(listOf("alpha", "beta"), WorldArchive.worldNames(saves))

        // Delete the originals, then restore the bundle.
        File(saves, "alpha").deleteRecursively()
        File(saves, "beta").deleteRecursively()

        val result = WorldArchive.restore(archive, saves)
        assertTrue(result is RestoreResult.Success)
        assertEquals(listOf("alpha", "beta"), (result as RestoreResult.Success).worlds.sorted())
        assertEquals("level-alpha", File(saves, "alpha/level.dat").readText())
        assertEquals("region-beta", File(saves, "beta/region/r.0.0.mca").readText())
    }

    @Test
    fun `an existing world is preserved instead of overwritten`() {
        val root = tempDir()
        val saves = File(root, "saves").apply { mkdirs() }
        world(saves, "alpha")
        val archive = WorldArchive.backupAll(saves, File(root, "bak.zip"))

        // The player kept playing after the backup; the new level must not be lost.
        File(saves, "alpha/level.dat").writeText("newer-level")

        val result = WorldArchive.restore(archive, saves, now = 1234L)
        assertTrue(result is RestoreResult.Success)
        val success = result as RestoreResult.Success
        assertEquals(listOf("alpha.before-restore-1234"), success.preserved)
        assertEquals("level-alpha", File(saves, "alpha/level.dat").readText())
        assertEquals("newer-level", File(saves, "alpha.before-restore-1234/level.dat").readText())
    }

    @Test
    fun `path traversal entries are rejected`() {
        val root = tempDir()
        val saves = File(root, "saves").apply { mkdirs() }
        val archive = File(root, "evil.zip")
        zipOf(
            archive,
            "alpha/level.dat" to "level",
            "alpha/../../../evil.txt" to "pwned"
        )

        val result = WorldArchive.restore(archive, saves)
        assertTrue(result is RestoreResult.Success)
        assertFalse(File(root, "evil.txt").exists())
        assertFalse(File(root.parentFile, "evil.txt").exists())
        assertTrue(File(saves, "alpha/level.dat").isFile)
    }

    @Test
    fun `an archive with no level file is refused`() {
        val root = tempDir()
        val saves = File(root, "saves").apply { mkdirs() }
        val archive = File(root, "empty.zip")
        zipOf(archive, "alpha/region/r.0.0.mca" to "no level")

        val result = WorldArchive.restore(archive, saves)
        assertTrue(result is RestoreResult.Failure)
        assertEquals(
            RestoreResult.Failure.Reason.NO_WORLD,
            (result as RestoreResult.Failure).reason
        )
    }

    @Test
    fun `a root level world and a saves prefixed archive are both accepted`() {
        val root = tempDir()
        val saves = File(root, "saves").apply { mkdirs() }

        val single = File(root, "worlds-1700000000000.zip")
        zipOf(single, "level.dat" to "root-level", "region/r.0.0.mca" to "root-region")
        val singleResult = WorldArchive.restore(single, saves)
        assertTrue(singleResult is RestoreResult.Success)
        assertTrue(File(saves, "restored-1700000000000/level.dat").isFile)

        val prefixed = File(root, "prefixed.zip")
        zipOf(prefixed, "saves/gamma/level.dat" to "prefixed-level")
        val prefixedResult = WorldArchive.restore(prefixed, saves)
        assertTrue(prefixedResult is RestoreResult.Success)
        assertEquals("prefixed-level", File(saves, "gamma/level.dat").readText())
    }

    @Test
    fun `backup list is newest first and ignores other files`() {
        val root = tempDir()
        val saves = File(root, "saves").apply { mkdirs() }
        world(saves, "alpha")
        val backups = WorldArchive.backupDir(saves).apply { mkdirs() }

        val older = File(backups, "worlds-1.zip").apply { writeText("x") }
        val newer = File(backups, "worlds-2.zip").apply { writeText("x") }
        older.setLastModified(1_000L)
        newer.setLastModified(2_000L)
        File(backups, "notes.txt").writeText("ignore me")

        // The two fake zips are unreadable; listBackups must skip them, not crash.
        assertEquals(emptyList<Any>(), WorldArchive.listBackups(saves))
        assertEquals(listOf("alpha"), WorldArchive.worldNames(saves))
    }

    @Test
    fun `a missing archive reports missing`() {
        val root = tempDir()
        val saves = File(root, "saves").apply { mkdirs() }
        val result = WorldArchive.restore(File(root, "nope.zip"), saves)
        assertEquals(
            RestoreResult.Failure.Reason.MISSING,
            (result as RestoreResult.Failure).reason
        )
    }
}
