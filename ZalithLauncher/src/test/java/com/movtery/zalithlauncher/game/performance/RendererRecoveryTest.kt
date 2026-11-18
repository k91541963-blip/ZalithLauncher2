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

import com.movtery.zalithlauncher.game.renderer.recovery.RendererRecovery
import com.movtery.zalithlauncher.game.renderer.recovery.RendererRecoveryStore
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RendererRecoveryTest {

    private fun newDir(): java.io.File = Files.createTempDirectory("recovery").toFile()

    private fun store(dir: java.io.File = newDir()): RendererRecoveryStore =
        RendererRecoveryStore(dir)

    private fun recordAether(store: RendererRecoveryStore, version: String = "1.21.4") {
        store.recordAttempt(
            rendererId = RendererRecovery.AETHER_RENDER_ID,
            rendererName = "AetherRender",
            rendererUniqueId = RendererRecovery.uniqueIdentifierOf(
                RendererRecovery.AETHER_RENDER_ID
            )!!,
            versionName = version
        )
    }

    @Test
    fun `aether falls back to the bundled gles 3 core`() {
        assertEquals(
            RendererRecovery.NG_GL4ES_ID,
            RendererRecovery.fallbackRendererId(RendererRecovery.AETHER_RENDER_ID, 3)
        )
    }

    @Test
    fun `legacy gles 2 devices fall back to gl4es`() {
        assertEquals(
            RendererRecovery.GL4ES_ID,
            RendererRecovery.fallbackRendererId(RendererRecovery.AETHER_RENDER_ID, 2)
        )
    }

    @Test
    fun `the fallback chain ends instead of looping`() {
        assertEquals(
            RendererRecovery.GL4ES_ID,
            RendererRecovery.fallbackRendererId(RendererRecovery.NG_GL4ES_ID, 3)
        )
        assertNull(RendererRecovery.fallbackRendererId(RendererRecovery.GL4ES_ID, 3))
    }

    @Test
    fun `renderer ids map to bundled unique identifiers`() {
        assertNotNull(RendererRecovery.uniqueIdentifierOf(RendererRecovery.AETHER_RENDER_ID))
        assertNotNull(RendererRecovery.uniqueIdentifierOf(RendererRecovery.NG_GL4ES_ID))
        assertNotNull(RendererRecovery.uniqueIdentifierOf(RendererRecovery.GL4ES_ID))
        assertNull(RendererRecovery.uniqueIdentifierOf("unknown_renderer"))
    }

    @Test
    fun `attempt is readable until it is resolved`() {
        val store = store()
        recordAether(store)

        val pending = store.pending()
        assertNotNull(pending)
        assertEquals(RendererRecovery.AETHER_RENDER_ID, pending!!.rendererId)
        assertEquals("1.21.4", pending.versionName)
    }

    @Test
    fun `successful exit clears the attempt and records last good`() {
        val store = store()
        recordAether(store)
        val uniqueId = RendererRecovery.uniqueIdentifierOf(RendererRecovery.AETHER_RENDER_ID)!!

        store.markSuccess(
            rendererId = RendererRecovery.AETHER_RENDER_ID,
            rendererName = "AetherRender",
            rendererUniqueId = uniqueId,
            versionName = "1.21.4"
        )

        assertNull(store.pending())
        val good = store.latestLastGood()
        assertNotNull(good)
        assertEquals("AetherRender", good!!.rendererName)
        assertEquals("1.21.4", good.versionName)
        assertEquals(0, store.failureCount(uniqueId))
    }

    @Test
    fun `failed launch produces an event and a safe fallback`() {
        val store = store()
        recordAether(store)
        val attempt = store.pending()!!
        val event = store.handleFailure(attempt, glEsMajor = 3, now = 1234L)

        assertNotNull(event)
        assertTrue(event!!.switched)
        assertEquals(RendererRecovery.NG_GL4ES_ID, event.toId?.let {
            RendererRecovery.uniqueIdentifierOf(RendererRecovery.NG_GL4ES_ID)
        })
        assertEquals("1.21.4", event.versionName)
        assertNull(store.pending())

        // The event survives a new store instance, i.e. it is on disk for the next start.
        val reopened = RendererRecoveryStore(store.let { _ -> storeFileRoot(store) })
        assertEquals(event.fromName, reopened.event()?.fromName)
    }

    @Test
    fun `failure count grows and success resets it`() {
        val store = store()
        val uniqueId = RendererRecovery.uniqueIdentifierOf(RendererRecovery.AETHER_RENDER_ID)!!

        recordAether(store)
        store.handleFailure(store.pending()!!, 3)
        recordAether(store)
        store.handleFailure(store.pending()!!, 3)
        assertEquals(2, store.failureCount(uniqueId))

        store.markSuccess(
            rendererId = RendererRecovery.AETHER_RENDER_ID,
            rendererName = "AetherRender",
            rendererUniqueId = uniqueId,
            versionName = "1.21.4"
        )
        assertEquals(0, store.failureCount(uniqueId))
    }

    @Test
    fun `event can be dismissed without touching last good`() {
        val store = store()
        recordAether(store)
        store.markSuccess(
            rendererId = RendererRecovery.AETHER_RENDER_ID,
            rendererName = "AetherRender",
            rendererUniqueId = RendererRecovery.uniqueIdentifierOf(
                RendererRecovery.AETHER_RENDER_ID
            )!!,
            versionName = "1.21.4"
        )
        recordAether(store)
        store.handleFailure(store.pending()!!, 3)

        assertNotNull(store.event())
        store.dismissEvent()
        assertNull(store.event())
        assertNotNull(store.latestLastGood())
    }

    @Test
    fun `failing version loses its last good entry`() {
        val store = store()
        val uniqueId = RendererRecovery.uniqueIdentifierOf(RendererRecovery.AETHER_RENDER_ID)!!
        store.markSuccess(
            rendererId = RendererRecovery.AETHER_RENDER_ID,
            rendererName = "AetherRender",
            rendererUniqueId = uniqueId,
            versionName = "1.20.1"
        )
        recordAether(store, version = "1.20.1")
        store.handleFailure(store.pending()!!, 3)

        assertFalse(store.lastGoodAll().containsKey("1.20.1"))
    }

    @Test
    fun `no attempt means nothing to recover`() {
        val store = store()
        assertNull(store.pending())
        assertNull(store.event())
        assertNull(store.latestLastGood())
    }
}
