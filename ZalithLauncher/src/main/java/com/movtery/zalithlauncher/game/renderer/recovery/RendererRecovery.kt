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

package com.movtery.zalithlauncher.game.renderer.recovery

import android.app.ActivityManager
import android.content.Context
import com.movtery.zalithlauncher.game.renderer.renderers.AetherRenderer
import com.movtery.zalithlauncher.game.renderer.renderers.GL4ESRenderer
import com.movtery.zalithlauncher.game.renderer.renderers.NGGL4ESRenderer
import com.movtery.zalithlauncher.setting.AllSettings
import com.movtery.zalithlauncher.utils.logging.Logger
import java.io.File
import java.util.Properties

/** A launch that started but has not been confirmed as finished. */
data class PendingLaunch(
    val rendererId: String,
    val rendererName: String,
    val rendererUniqueId: String,
    val versionName: String,
    val startedAt: Long
)

/** What the user should see after a failed launch. */
data class RecoveryEvent(
    val fromName: String,
    val fromId: String,
    val toName: String?,
    val toId: String?,
    val versionName: String,
    val at: Long
) {
    /** True when the launcher actually switched renderers. */
    val switched: Boolean get() = toId != null
}

/** The renderer that last finished a launch successfully, per Minecraft version. */
data class LastGoodRenderer(
    val versionName: String,
    val rendererId: String,
    val rendererName: String,
    val at: Long
)

/**
 * Pure decision table for the safe renderer after a failure.
 *
 * The chain is deliberately short and ends: AetherRender -> bundled NG-GL4ES
 * ("Krypton Wrapper") -> GL4ES -> stop. A renderer that already is the last resort is
 * never replaced, so the launcher cannot bounce between two broken renderers.
 */
object RendererRecovery {

    const val AETHER_RENDER_ID = "opengles3_aether"
    const val NG_GL4ES_ID = "opengles3"
    const val GL4ES_ID = "opengles2"

    /** Returns the fallback renderer id, or `null` when no safer renderer exists. */
    fun fallbackRendererId(failedRendererId: String, glEsMajor: Int): String? = when {
        failedRendererId == AETHER_RENDER_ID ->
            if (glEsMajor >= 3) NG_GL4ES_ID else GL4ES_ID

        failedRendererId.startsWith(NG_GL4ES_ID) -> GL4ES_ID
        failedRendererId.startsWith(GL4ES_ID) -> null

        // Plugins and Zink paths fall back to the bundled core.
        else -> if (glEsMajor >= 3) NG_GL4ES_ID else GL4ES_ID
    }

    /** Maps a renderer id to the bundled renderer's unique identifier. */
    fun uniqueIdentifierOf(rendererId: String): String? = when (rendererId) {
        AETHER_RENDER_ID -> AetherRenderer.getUniqueIdentifier()
        NG_GL4ES_ID -> NGGL4ESRenderer.getUniqueIdentifier()
        GL4ES_ID -> GL4ESRenderer.getUniqueIdentifier()
        else -> null
    }
}

/**
 * Crash recovery and "last known good renderer" store.
 *
 * Files live in `filesDir/renderer_recovery` and use `java.util.Properties`, so the whole
 * flow is testable on the JVM without Android or JSON stubs.
 *
 * Lifecycle:
 *  1. [recordAttempt] before the JVM starts (survives native crashes),
 *  2. [markSuccess] when the game exits with code 0,
 *  3. [handleFailure] either right after a non-zero exit or on the next app start when
 *     the process died before it could report the code.
 */
class RendererRecoveryStore(private val root: File) {

    companion object {
        const val DIR = "renderer_recovery"
        private const val FILE_PENDING = "pending.properties"
        private const val FILE_FAILURES = "failures.properties"
        private const val FILE_LAST_GOOD = "last_good.properties"
        private const val FILE_EVENT = "event.properties"

        fun forContext(context: Context): RendererRecoveryStore =
            RendererRecoveryStore(File(context.filesDir, DIR))
    }

    // --- attempt -----------------------------------------------------------

    fun recordAttempt(
        rendererId: String,
        rendererName: String,
        rendererUniqueId: String,
        versionName: String,
        now: Long = System.currentTimeMillis()
    ) {
        val props = Properties().apply {
            setProperty("rendererId", rendererId)
            setProperty("rendererName", rendererName)
            setProperty("rendererUniqueId", rendererUniqueId)
            setProperty("versionName", versionName)
            setProperty("startedAt", now.toString())
        }
        save(FILE_PENDING, props)
    }

    fun pending(): PendingLaunch? {
        val props = load(FILE_PENDING) ?: return null
        val id = props.getProperty("rendererId").orEmpty()
        val uniqueId = props.getProperty("rendererUniqueId").orEmpty()
        if (id.isEmpty() || uniqueId.isEmpty()) return null
        return PendingLaunch(
            rendererId = id,
            rendererName = props.getProperty("rendererName").orEmpty(),
            rendererUniqueId = uniqueId,
            versionName = props.getProperty("versionName").orEmpty(),
            startedAt = props.getProperty("startedAt")?.toLongOrNull() ?: 0L
        )
    }

    // --- success -----------------------------------------------------------

    /** Clears the attempt, resets the failure counter and records the last good run. */
    fun markSuccess(
        rendererId: String,
        rendererName: String,
        rendererUniqueId: String,
        versionName: String,
        now: Long = System.currentTimeMillis()
    ) {
        delete(FILE_PENDING)

        val failures = load(FILE_FAILURES) ?: Properties()
        failures.remove(rendererUniqueId)
        if (failures.isEmpty) delete(FILE_FAILURES) else save(FILE_FAILURES, failures)

        if (versionName.isNotBlank()) {
            val good = load(FILE_LAST_GOOD) ?: Properties()
            good.setProperty("$versionName.uniqueId", rendererUniqueId)
            good.setProperty("$versionName.rendererId", rendererId)
            good.setProperty("$versionName.rendererName", rendererName)
            good.setProperty("$versionName.at", now.toString())
            save(FILE_LAST_GOOD, good)
        }
    }

    // --- failure -----------------------------------------------------------

    /**
     * Handles a failed launch: counts the failure, decides the safe renderer and clears
     * the attempt so the same crash cannot be processed twice.
     *
     * @return the event to show, or `null` when there was nothing to recover
     */
    fun handleFailure(
        attempt: PendingLaunch,
        glEsMajor: Int,
        now: Long = System.currentTimeMillis()
    ): RecoveryEvent? {
        delete(FILE_PENDING)

        val failures = load(FILE_FAILURES) ?: Properties()
        val key = attempt.rendererUniqueId
        val count = (failures.getProperty(key)?.toIntOrNull() ?: 0) + 1
        failures.setProperty(key, count.toString())
        save(FILE_FAILURES, failures)

        if (attempt.versionName.isNotBlank()) {
            // A failed run must not leave an older success pointing at the same version.
            val good = load(FILE_LAST_GOOD) ?: Properties()
            val prefix = "${attempt.versionName}."
            if (good.keys.any { it.toString().startsWith(prefix) }) {
                val filtered = Properties()
                for ((k, v) in good) {
                    if (!k.toString().startsWith(prefix)) filtered[k] = v
                }
                if (filtered.isEmpty) delete(FILE_LAST_GOOD) else save(FILE_LAST_GOOD, filtered)
            }
        }

        val fallbackId = RendererRecovery.fallbackRendererId(attempt.rendererId, glEsMajor)
        val fallbackUniqueId = fallbackId?.let { RendererRecovery.uniqueIdentifierOf(it) }
        val fallbackName = fallbackId?.let { nameOf(it) }

        val event = RecoveryEvent(
            fromName = attempt.rendererName.ifBlank { attempt.rendererId },
            fromId = attempt.rendererId,
            toName = fallbackName.takeIf { fallbackUniqueId != null },
            toId = fallbackUniqueId,
            versionName = attempt.versionName,
            at = now
        )
        save(
            FILE_EVENT,
            Properties().apply {
                setProperty("fromName", event.fromName)
                setProperty("fromId", event.fromId)
                event.toName?.let { setProperty("toName", it) }
                event.toId?.let { setProperty("toId", it) }
                setProperty("versionName", event.versionName)
                setProperty("at", event.at.toString())
            }
        )
        return event
    }

    // --- events & history --------------------------------------------------

    fun event(): RecoveryEvent? {
        val props = load(FILE_EVENT) ?: return null
        return RecoveryEvent(
            fromName = props.getProperty("fromName").orEmpty(),
            fromId = props.getProperty("fromId").orEmpty(),
            toName = props.getProperty("toName"),
            toId = props.getProperty("toId"),
            versionName = props.getProperty("versionName").orEmpty(),
            at = props.getProperty("at")?.toLongOrNull() ?: 0L
        )
    }

    fun dismissEvent() = delete(FILE_EVENT)

    /** Failed-launch count for one renderer, reset on the next successful launch. */
    fun failureCount(rendererUniqueId: String): Int {
        val props = load(FILE_FAILURES) ?: return 0
        return props.getProperty(rendererUniqueId)?.toIntOrNull() ?: 0
    }

    fun lastGoodAll(): Map<String, LastGoodRenderer> {
        val props = load(FILE_LAST_GOOD) ?: return emptyMap()
        val versions = props.stringPropertyNames()
            .filter { it.endsWith(".uniqueId") }
            .map { it.removeSuffix(".uniqueId") }
        return versions.associateWith { version ->
            LastGoodRenderer(
                versionName = version,
                rendererId = props.getProperty("$version.rendererId").orEmpty(),
                rendererName = props.getProperty("$version.rendererName").orEmpty(),
                at = props.getProperty("$version.at")?.toLongOrNull() ?: 0L
            )
        }
    }

    /** Most recent successful launch across all versions, or `null`. */
    fun latestLastGood(): LastGoodRenderer? =
        lastGoodAll().values.maxByOrNull { it.at }

    // --- io ----------------------------------------------------------------

    private fun file(name: String) = File(root, name)

    private fun load(name: String): Properties? {
        val file = file(name)
        if (!file.isFile) return null
        return runCatching {
            Properties().apply { file.inputStream().use { load(it) } }
        }.getOrNull()
    }

    private fun save(name: String, props: Properties) {
        runCatching {
            root.mkdirs()
            file(name).outputStream().use { props.store(it, null) }
        }
    }

    private fun delete(name: String) {
        runCatching { file(name).delete() }
    }

    private fun nameOf(rendererId: String): String = when (rendererId) {
        RendererRecovery.AETHER_RENDER_ID -> AetherRenderer.getRendererName()
        RendererRecovery.NG_GL4ES_ID -> NGGL4ESRenderer.getRendererName()
        RendererRecovery.GL4ES_ID -> GL4ESRenderer.getRendererName()
        else -> rendererId
    }
}

/**
 * Startup half of the recovery flow.
 *
 * Called once from `ZLApplication` after settings are loaded. When the previous launch
 * died before it could report an exit code (native crash, LMK kill), the pending attempt
 * is still on disk and this is the only place that can react to it.
 */
object RendererRecoveryStartup {

    private const val TAG = "RendererRecovery"

    fun apply(context: Context): RecoveryEvent? = runCatching {
        val store = RendererRecoveryStore.forContext(context)
        val attempt = store.pending() ?: return@runCatching null
        val event = store.handleFailure(attempt, detectGlEsMajor(context)) ?: return@runCatching null

        event.toId?.let { uniqueId ->
            AllSettings.renderer.save(uniqueId)
            Logger.warning(
                TAG,
                "Previous launch with ${attempt.rendererName} did not finish; " +
                    "switched to ${event.toName ?: "no fallback"}"
            )
        }
        event
    }.onFailure {
        Logger.warning(TAG, "Renderer recovery check failed", it)
    }.getOrNull()

    /**
     * GLES major version from `ActivityManager`, i.e. the value the platform already
     * parsed. No EGL context is created here: this runs during application startup.
     */
    fun detectGlEsMajor(context: Context): Int {
        val info = (context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager)
            ?.deviceConfigurationInfo
            ?.glEsVersion
            .orEmpty()
        return info.substringBefore('.').toIntOrNull() ?: 2
    }
}
