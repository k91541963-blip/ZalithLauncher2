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

import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLSurface
import android.opengl.GLES20
import androidx.annotation.WorkerThread
import java.io.File

/**
 * GPU strings read from a real EGL/GLES context, plus a best-effort Mali model.
 *
 * Every field can be `null`: the launcher never fills unknown values with guesses.
 */
data class GpuInfo(
    val renderer: String? = null,
    val vendor: String? = null,
    val version: String? = null,
    val maliModel: String? = null
) {
    val hasData: Boolean
        get() = !renderer.isNullOrBlank() || !vendor.isNullOrBlank() || !maliModel.isNullOrBlank()
}

/**
 * Rough Mali generation grouping, derived from the public model name only.
 *
 * This is a naming heuristic used to pick a conservative starting preset. It is not a
 * benchmark and must not be presented as measured performance.
 */
enum class MaliTier {
    ENTRY,
    MID,
    HIGH,
    UNKNOWN
}

/**
 * Reads GPU information without leaving a window or a context behind.
 *
 * The EGL probe creates a 1x1 pbuffer on the default display and destroys only its own
 * surface/context. It deliberately never calls `eglTerminate`: terminating the process-wide
 * default display can break the launcher's own Compose renderer.
 */
object GpuInfoReader {

    private val maliModelRegex = Regex(
        """(Mali-[GT]\d{2,3})(?:\s*(MC\d+|MP\d+))?""",
        RegexOption.IGNORE_CASE
    )

    private val lock = Any()
    private val sysfsCandidates = listOf(
        "/sys/class/misc/mali0/device/gpuinfo",
        "/sys/devices/platform/mali.0/gpuinfo",
        "/sys/devices/platform/mali/gpuinfo"
    )

    /** Extracts `Mali-G57 MC2` style names from a driver string, or returns `null`. */
    fun parseMaliModel(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        val match = maliModelRegex.find(raw) ?: return null
        val model = "Mali-" + match.groupValues[1].substringAfter('-').uppercase()
        val cores = match.groupValues[2].uppercase()
        return if (cores.isBlank()) model else "$model $cores"
    }

    /** Groups a Mali model by name. Unknown or non-Mali models return [MaliTier.UNKNOWN]. */
    fun classifyMali(model: String?): MaliTier {
        val name = parseMaliModel(model) ?: return MaliTier.UNKNOWN
        return when (name.substringAfter("Mali-").substringBefore(' ').uppercase()) {
            "G31", "G51", "G52", "G57" -> MaliTier.ENTRY
            "G68", "G71", "G72", "G76", "G77" -> MaliTier.MID
            "G78", "G510", "G610", "G710", "G715" -> MaliTier.HIGH
            else -> MaliTier.UNKNOWN
        }
    }

    /**
     * Reads the model from the Mali kernel driver when the vendor exposes it.
     * Returns `null` when the files are absent or unreadable (common on newer kernels).
     */
    fun readSysfsModel(): String? {
        for (path in sysfsCandidates) {
            val text = readSmallText(File(path)) ?: continue
            parseMaliModel(text)?.let { return it }
        }
        // Some vendors only expose the generic misc node.
        val misc = File("/sys/class/misc")
        misc.listFiles()
            ?.asSequence()
            ?.filter { it.name.startsWith("mali") }
            ?.take(4)
            ?.forEach { node ->
                val text = readSmallText(File(node, "device/gpuinfo")) ?: return@forEach
                parseMaliModel(text)?.let { return it }
            }
        return null
    }

    /** Real EGL probe. Blocking; call from [kotlinx.coroutines.Dispatchers.IO] only. */
    @WorkerThread
    fun probe(): GpuInfo? = synchronized(lock) {
        runCatching { probeInternal() }.getOrNull()
    }

    /** Probe first, then fall back to the kernel driver string. */
    @WorkerThread
    fun read(): GpuInfo? {
        val probed = probe()
        if (probed != null) return probed
        val sysfs = readSysfsModel() ?: return null
        return GpuInfo(maliModel = sysfs)
    }

    private fun probeInternal(): GpuInfo? {
        val display: EGLDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        if (display == EGL14.EGL_NO_DISPLAY) return null

        val versions = IntArray(2)
        if (!EGL14.eglInitialize(display, versions, 0, versions, 1)) return null

        var config: EGLConfig? = null
        var context: EGLContext? = null
        var surface: EGLSurface? = null
        try {
            val configs = arrayOfNulls<EGLConfig>(1)
            val configCount = IntArray(1)
            val configAttrs = intArrayOf(
                EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
                EGL14.EGL_SURFACE_TYPE, EGL14.EGL_PBUFFER_BIT,
                EGL14.EGL_RED_SIZE, 8,
                EGL14.EGL_GREEN_SIZE, 8,
                EGL14.EGL_BLUE_SIZE, 8,
                EGL14.EGL_NONE
            )
            if (!EGL14.eglChooseConfig(display, configAttrs, 0, configs, 0, 1, configCount, 0) ||
                configCount[0] <= 0
            ) {
                return null
            }
            config = configs[0] ?: return null

            context = EGL14.eglCreateContext(
                display,
                config,
                EGL14.EGL_NO_CONTEXT,
                intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE),
                0
            )
            if (context == EGL14.EGL_NO_CONTEXT) return null

            surface = EGL14.eglCreatePbufferSurface(
                display,
                config,
                intArrayOf(EGL14.EGL_WIDTH, 1, EGL14.EGL_HEIGHT, 1, EGL14.EGL_NONE),
                0
            )
            if (surface == EGL14.EGL_NO_SURFACE) return null

            if (!EGL14.eglMakeCurrent(display, surface, surface, context)) return null

            val renderer = GLES20.glGetString(GLES20.GL_RENDERER)
            val vendor = GLES20.glGetString(GLES20.GL_VENDOR)
            val version = GLES20.glGetString(GLES20.GL_VERSION)
            return GpuInfo(
                renderer = renderer?.trim()?.takeIf { it.isNotEmpty() },
                vendor = vendor?.trim()?.takeIf { it.isNotEmpty() },
                version = version?.trim()?.takeIf { it.isNotEmpty() },
                maliModel = parseMaliModel(renderer)
            )
        } finally {
            EGL14.eglMakeCurrent(
                display,
                EGL14.EGL_NO_SURFACE,
                EGL14.EGL_NO_SURFACE,
                EGL14.EGL_NO_CONTEXT
            )
            surface?.takeIf { it != EGL14.EGL_NO_SURFACE }
                ?.let { EGL14.eglDestroySurface(display, it) }
            context?.takeIf { it != EGL14.EGL_NO_CONTEXT }
                ?.let { EGL14.eglDestroyContext(display, it) }
            // Never eglTerminate() the default display: it is shared with the launcher UI.
        }
    }

    private fun readSmallText(file: File): String? {
        return runCatching {
            if (!file.isFile || !file.canRead()) return null
            if (file.length() > 8192) return null
            file.readText().trim().takeIf { it.isNotEmpty() }
        }.getOrNull()
    }
}

/**
 * Process-wide cache for the GPU probe.
 *
 * The probe creates a temporary EGL context, so it must not run on every recomposition.
 * The settings screen calls [refresh] only when the user explicitly asks for it.
 */
object GpuInfoCache {

    private val lock = Any()

    @Volatile
    private var cached: GpuInfo? = null

    @Volatile
    private var attempted: Boolean = false

    fun peek(): GpuInfo? = cached

    @WorkerThread
    fun get(): GpuInfo? = synchronized(lock) {
        if (attempted) return cached
        attempted = true
        cached = GpuInfoReader.read()
        cached
    }

    @WorkerThread
    fun refresh(): GpuInfo? = synchronized(lock) {
        attempted = true
        cached = GpuInfoReader.read()
        cached
    }

    fun clear() = synchronized(lock) {
        attempted = false
        cached = null
    }
}
