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

/*
 * Lives in the game.performance package because that is the unit test package the CI
 * mirror runs; the code under test is `com.movtery.zalithlauncher.game.home`.
 */
package com.movtery.zalithlauncher.game.performance

import com.movtery.zalithlauncher.game.home.HeroVersion
import com.movtery.zalithlauncher.game.home.HomeHeroBuilder
import com.movtery.zalithlauncher.game.home.HomeHeroState
import com.movtery.zalithlauncher.game.home.HomePlayBlock
import com.movtery.zalithlauncher.game.home.HomeWidgetInput
import com.movtery.zalithlauncher.game.home.HomeWorlds
import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeHeroTest {

    // --- hero play state ----------------------------------------------------

    @Test
    fun `hero cannot play without account and version`() {
        val state = HomeHeroState()
        assertFalse(state.canPlay)
        assertEquals(HomePlayBlock.NO_ACCOUNT_AND_VERSION, state.playBlock)
    }

    @Test
    fun `hero blocks only the missing piece`() {
        val version = HeroVersion(name = "1.21.4", summary = "1.21.4, Fabric - 0.16.9")

        val noAccount = HomeHeroState(version = version)
        assertFalse(noAccount.canPlay)
        assertEquals(HomePlayBlock.NO_ACCOUNT, noAccount.playBlock)

        val noVersion = HomeHeroState(accountName = "Steve")
        assertFalse(noVersion.canPlay)
        assertEquals(HomePlayBlock.NO_VERSION, noVersion.playBlock)
    }

    @Test
    fun `hero can play with account and version`() {
        val state = HomeHeroState(
            accountName = "Steve",
            version = HeroVersion(name = "1.21.4", summary = null)
        )
        assertTrue(state.canPlay)
        assertNull(state.playBlock)
    }

    // --- session mapping ----------------------------------------------------

    @Test
    fun `session without frames shows no fps`() {
        val hero = HomeHeroBuilder.fromSummary(summary(frames = 0, averageFps = 0.0))
        assertNull(hero.averageFps)
        assertEquals("1.21.4", hero.versionName)
    }

    @Test
    fun `session with frames keeps the measured fps`() {
        val hero = HomeHeroBuilder.fromSummary(summary(frames = 1000, averageFps = 58.34))
        assertNotNull(hero.averageFps)
        assertEquals(58.34, hero.averageFps!!, 0.0001)
    }

    @Test
    fun `negative duration is clamped`() {
        val hero = HomeHeroBuilder.fromSummary(
            summary(frames = 10, averageFps = 60.0, durationSeconds = -5)
        )
        assertEquals(0, hero.durationSeconds)
    }

    // --- world scanning -----------------------------------------------------

    @Test
    fun `world scan picks the newest world with a level dat`() {
        val saves = Files.createTempDirectory("saves").toFile()
        world(saves, "old", at = 1_000L)
        world(saves, "newer", at = 3_000L)
        world(saves, "between", at = 2_000L)

        val latest = HomeWorlds.findLatest(saves)
        assertNotNull(latest)
        assertEquals("newer", latest!!.name)
    }

    @Test
    fun `world scan ignores folders without a level dat`() {
        val saves = Files.createTempDirectory("saves").toFile()
        File(saves, "backup").mkdirs()
        world(saves, "real", at = 5_000L)

        val all = HomeWorlds.scan(saves, limit = 10)
        assertEquals(listOf("real"), all.map { it.name })
    }

    @Test
    fun `world scan respects the limit`() {
        val saves = Files.createTempDirectory("saves").toFile()
        world(saves, "a", at = 1_000L)
        world(saves, "b", at = 2_000L)
        world(saves, "c", at = 3_000L)

        assertEquals(2, HomeWorlds.scan(saves, limit = 2).size)
        assertEquals(listOf("c", "b"), HomeWorlds.scan(saves, limit = 2).map { it.name })
        assertTrue(HomeWorlds.scan(saves, limit = 0).isEmpty())
    }

    @Test
    fun `world scan survives a missing saves folder`() {
        val missing = File(Files.createTempDirectory("saves").toFile(), "nope")
        assertNull(HomeWorlds.findLatest(missing))
        assertTrue(HomeWorlds.scan(missing).isEmpty())
    }

    // --- widget input -------------------------------------------------------

    @Test
    fun `server address accepts host and host with port`() {
        assertEquals("play.example.com", HomeWidgetInput.normalizeAddress(" play.example.com "))
        assertEquals("play.example.com:25565", HomeWidgetInput.normalizeAddress("play.example.com:25565"))
        assertEquals("[::1]:25565", HomeWidgetInput.normalizeAddress("[::1]:25565"))
    }

    @Test
    fun `server address rejects unusable input`() {
        assertNull(HomeWidgetInput.normalizeAddress(""))
        assertNull(HomeWidgetInput.normalizeAddress("   "))
        assertNull(HomeWidgetInput.normalizeAddress("mine craft.com"))
        assertNull(HomeWidgetInput.normalizeAddress("minecraft://play.example.com"))
        assertNull(HomeWidgetInput.normalizeAddress(":25565"))
        assertNull(HomeWidgetInput.normalizeAddress("play.example.com:"))
        assertNull(HomeWidgetInput.normalizeAddress("a".repeat(HomeWidgetInput.MAX_ADDRESS_LENGTH + 1)))
    }

    @Test
    fun `server name falls back to the address`() {
        assertEquals("play.example.com", HomeWidgetInput.normalizeName("   ", "play.example.com"))
        assertEquals("Survival", HomeWidgetInput.normalizeName(" Survival ", "play.example.com"))
    }

    // --- helpers ------------------------------------------------------------

    private fun world(saves: File, name: String, at: Long) {
        val dir = File(saves, name)
        dir.mkdirs()
        val level = File(dir, HomeWorlds.LEVEL_FILE)
        level.writeText("level")
        level.setLastModified(at)
        // Writing the file touches the folder; pin both timestamps so the test is stable.
        dir.setLastModified(at)
    }

    private fun summary(
        frames: Int,
        averageFps: Double,
        durationSeconds: Int = 600
    ) = GameSessionSummary(
        versionName = "1.21.4",
        rendererMode = "balanced",
        startedAtEpochMs = 1_000L,
        endedAtEpochMs = 2_000L,
        durationSeconds = durationSeconds,
        framesCaptured = frames,
        averageFps = averageFps,
        onePercentLowFps = 0.0,
        p99FrameMs = 0.0,
        jankPercent = 0.0,
        averageCpuPercent = null,
        peakRamUsedMb = 0,
        totalRamMb = 0,
        peakTemperatureCelsius = null,
        fpsBuckets = emptyList(),
        samplesTruncated = false
    )
}
