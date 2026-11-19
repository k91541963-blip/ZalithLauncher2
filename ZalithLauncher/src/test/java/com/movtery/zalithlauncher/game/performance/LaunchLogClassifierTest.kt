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

class LaunchLogClassifierTest {

    private fun tempFile(name: String = "latest.log"): File =
        File(Files.createTempDirectory("launchlog").toFile(), name)

    @Test
    fun `jvm heap exhaustion is recognised`() {
        assertTrue(
            LaunchLogClassifier.isOutOfMemory(
                "java.lang.OutOfMemoryError: Java heap space\n\tat net.minecraft.client.Minecraft.run"
            )
        )
    }

    @Test
    fun `minecraft out of memory screen text is recognised`() {
        assertTrue(LaunchLogClassifier.isOutOfMemory("Minecraft has run out of memory"))
        assertTrue(LaunchLogClassifier.isOutOfMemory("OutOfMemoryError"))
    }

    @Test
    fun `native gl out of memory is recognised`() {
        assertTrue(LaunchLogClassifier.isOutOfMemory("GL error 0x0505: GL_OUT_OF_MEMORY"))
    }

    @Test
    fun `ordinary errors are not reported as out of memory`() {
        assertFalse(LaunchLogClassifier.isOutOfMemory("java.lang.NullPointerException"))
        assertFalse(LaunchLogClassifier.isOutOfMemory("Stopping! Reason: crash"))
        assertFalse(LaunchLogClassifier.isOutOfMemory(""))
        assertFalse(LaunchLogClassifier.isOutOfMemory("   \n  "))
    }

    @Test
    fun `only the tail of a long log is read`() {
        val log = tempFile()
        // Marker far before the tail window must be ignored: it belongs to an older run.
        val header = "java.lang.OutOfMemoryError: Java heap space\n"
        val padding = "x".repeat(LaunchLogClassifier.TAIL_BYTES + 1024)
        log.writeText(header + padding + "\n[Render thread/INFO]: Stopping!\n")

        assertFalse(LaunchLogClassifier.logShowsOutOfMemory(log))
    }

    @Test
    fun `marker near the end is found through the tail read`() {
        val log = tempFile()
        val padding = "x".repeat(LaunchLogClassifier.TAIL_BYTES + 1024)
        log.writeText(padding + "\njava.lang.OutOfMemoryError: Java heap space\n")

        assertTrue(LaunchLogClassifier.logShowsOutOfMemory(log))
    }

    @Test
    fun `a short log is read completely`() {
        val log = tempFile()
        log.writeText("hello\njava.lang.OutOfMemoryError\n")

        assertEquals("hello\njava.lang.OutOfMemoryError\n", LaunchLogClassifier.readTail(log))
        assertTrue(LaunchLogClassifier.logShowsOutOfMemory(log))
    }

    @Test
    fun `missing and unreadable files never throw`() {
        val missing = tempFile("does-not-exist.log")
        assertEquals("", LaunchLogClassifier.readTail(missing))
        assertFalse(LaunchLogClassifier.logShowsOutOfMemory(missing))

        val directory = tempFile("a-directory")
        directory.mkdirs()
        assertEquals("", LaunchLogClassifier.readTail(directory))
        assertFalse(LaunchLogClassifier.logShowsOutOfMemory(directory))
    }

    @Test
    fun `zero byte window is rejected without reading`() {
        val log = tempFile()
        log.writeText("java.lang.OutOfMemoryError")

        assertEquals("", LaunchLogClassifier.readTail(log, maxBytes = 0))
    }
}
