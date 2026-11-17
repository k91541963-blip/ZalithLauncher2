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

import android.content.Context
import com.google.gson.Gson
import java.io.File

/**
 * Result of one measured Minecraft session.
 *
 * Every value comes from real measurements taken while the game was running:
 * frame times from `AetherMetrics` (native swap timestamps), CPU from `/proc/stat`,
 * RAM from `ActivityManager`/PSS and temperature from the thermal zone. Fields that
 * could not be read stay `null` — the UI shows them as unknown instead of guessing.
 */
data class GameSessionSummary(
    val versionName: String,
    /** `AETHER_RENDER_MODE` that was active during the session. */
    val rendererMode: String,
    val startedAtEpochMs: Long,
    val endedAtEpochMs: Long,
    val durationSeconds: Int,
    val framesCaptured: Int,
    val averageFps: Double,
    val onePercentLowFps: Double,
    val p99FrameMs: Double,
    val jankPercent: Double,
    val averageCpuPercent: Double?,
    val peakRamUsedMb: Int,
    val totalRamMb: Int,
    val peakTemperatureCelsius: Float?,
    /** Measured FPS per roughly 10 second window, oldest first. */
    val fpsBuckets: List<Double>,
    /** True when the frame buffer hit its limit and later frames were not stored. */
    val samplesTruncated: Boolean
)

/**
 * Collects frame times and device samples during a session and turns them into a
 * [GameSessionSummary]. Pure Kotlin (no Android APIs) so the aggregation is unit tested.
 */
class GameSessionAccumulator(
    private val versionName: String,
    private val rendererMode: String,
    private val startedAtEpochMs: Long,
    private val maxStoredFrames: Int = MAX_STORED_FRAMES
) {

    companion object {
        /** ~66 minutes of 60 FPS frames; keeps memory bounded on low-RAM devices. */
        const val MAX_STORED_FRAMES = 240_000

        /** Bucket width for the FPS graph. */
        const val BUCKET_MICROS = 10_000_000L

        /** At most 30 minutes of buckets are kept. */
        const val MAX_BUCKETS = 180
    }

    private var frames = IntArray(4096)
    private var frameCount = 0
    private var truncated = false
    private var bucketMicros = 0L
    private var bucketFrames = 0
    private val buckets = ArrayList<Double>()
    private var cpuSum = 0.0
    private var cpuSamples = 0
    private var peakRamUsedMb = 0
    private var totalRamMb = 0
    private var peakTemperatureCelsius: Float? = null

    /** Appends frame time deltas (microseconds) read from the native ring buffer. */
    @Synchronized
    fun addFrameTimes(source: IntArray, count: Int) {
        val usable = count.coerceIn(0, source.size)
        for (i in 0 until usable) {
            val micros = source[i]
            if (micros <= 0) continue

            if (frameCount < maxStoredFrames) {
                if (frameCount == frames.size) {
                    frames = frames.copyOf((frames.size * 2).coerceAtMost(maxStoredFrames))
                }
                frames[frameCount++] = micros
            } else {
                truncated = true
            }

            // Pauses and loading screens must not drag the bucket average down.
            if (micros <= FrameTimeMetrics.MAX_PLAUSIBLE_FRAME_US) {
                bucketMicros += micros
                bucketFrames++
            }
        }
        closeFinishedBuckets()
    }

    /** Adds one device sample. Missing readings (`null`) are ignored, never invented. */
    @Synchronized
    fun addDeviceSample(
        cpuPercent: Float?,
        memory: MemorySnapshot,
        temperatureCelsius: Float?
    ) {
        if (cpuPercent != null && cpuPercent >= 0f) {
            cpuSum += cpuPercent
            cpuSamples++
        }
        if (memory.totalMb > 0) {
            totalRamMb = memory.totalMb
            if (memory.usedMb > peakRamUsedMb) {
                peakRamUsedMb = memory.usedMb
            }
        }
        if (temperatureCelsius != null) {
            val current = peakTemperatureCelsius
            if (current == null || temperatureCelsius > current) {
                peakTemperatureCelsius = temperatureCelsius
            }
        }
    }

    @Synchronized
    fun buildSummary(endedAtEpochMs: Long): GameSessionSummary? {
        if (frameCount == 0) return null
        flushBucket()
        val stats = FrameTimeMetrics.compute(frames.copyOf(frameCount)) ?: return null

        val durationSeconds = ((endedAtEpochMs - startedAtEpochMs) / 1000L)
            .coerceAtLeast(0L)
            .toInt()

        return GameSessionSummary(
            versionName = versionName,
            rendererMode = rendererMode,
            startedAtEpochMs = startedAtEpochMs,
            endedAtEpochMs = endedAtEpochMs,
            durationSeconds = durationSeconds,
            framesCaptured = frameCount,
            averageFps = stats.averageFps,
            onePercentLowFps = stats.onePercentLowFps,
            p99FrameMs = stats.p99FrameMs,
            jankPercent = stats.jankPercent,
            averageCpuPercent = if (cpuSamples > 0) cpuSum / cpuSamples else null,
            peakRamUsedMb = peakRamUsedMb,
            totalRamMb = totalRamMb,
            peakTemperatureCelsius = peakTemperatureCelsius,
            fpsBuckets = buckets.toList(),
            samplesTruncated = truncated
        )
    }

    private fun closeFinishedBuckets() {
        while (bucketMicros >= BUCKET_MICROS && bucketFrames > 0) {
            buckets.add(bucketFrames * 1_000_000.0 / bucketMicros)
            bucketMicros = 0L
            bucketFrames = 0
        }
        while (buckets.size > MAX_BUCKETS) {
            buckets.removeAt(0)
        }
    }

    private fun flushBucket() {
        if (bucketFrames > 0 && bucketMicros > 0) {
            buckets.add(bucketFrames * 1_000_000.0 / bucketMicros)
            bucketMicros = 0L
            bucketFrames = 0
        }
        while (buckets.size > MAX_BUCKETS) {
            buckets.removeAt(0)
        }
    }
}

/**
 * Persists the last measured session under the app's private files directory.
 * Only one summary is kept; a new session replaces the previous one.
 */
object GameSessionStore {

    private const val DIRECTORY = "aethermetrics"
    private const val FILE_NAME = "last_session.json"

    private val gson = Gson()

    fun file(context: Context): File =
        File(File(context.applicationContext.filesDir, DIRECTORY), FILE_NAME)

    fun write(context: Context, summary: GameSessionSummary) {
        runCatching {
            val target = file(context)
            target.parentFile?.mkdirs()
            target.writeText(gson.toJson(summary))
        }
    }

    fun readLast(context: Context): GameSessionSummary? = runCatching {
        val target = file(context)
        if (!target.isFile) return@runCatching null
        gson.fromJson(target.readText(), GameSessionSummary::class.java)
    }.getOrNull()

    fun clear(context: Context) {
        runCatching { file(context).delete() }
    }
}
