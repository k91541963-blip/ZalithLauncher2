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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.lwjgl.glfw.CallbackBridge

/**
 * Records one Minecraft session while the game screen is alive.
 *
 * Frame times are drained from the native AetherMetrics ring buffer once per second
 * (that is real swap pacing, not a guess), and CPU/RAM/temperature are sampled with the
 * same [PerformanceMonitor] the Performance Center uses. When the session ends the
 * summary is written to disk and the Performance Center shows it.
 *
 * The recorder never runs while no game is open, and it never fabricates values: if the
 * native library is not loaded yet or `/proc/stat` is unreadable, those fields stay
 * empty.
 */
object GameSessionRecorder {

    private const val POLL_INTERVAL_MS = 1000L

    /** Native ring holds 2048 samples; ask for the full ring each time. */
    private const val FRAME_BUFFER_SIZE = 2048

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null
    private var accumulator: GameSessionAccumulator? = null

    fun isRunning(): Boolean = job?.isActive == true

    /**
     * Starts recording for [versionName] launched with [rendererMode].
     * Calling this twice without [stop] keeps the first session (no duplicate sampling).
     */
    fun start(context: Context, versionName: String, rendererMode: String) {
        if (job?.isActive == true) return

        val appContext = context.applicationContext
        val session = GameSessionAccumulator(
            versionName = versionName,
            rendererMode = rendererMode,
            startedAtEpochMs = System.currentTimeMillis()
        )
        accumulator = session

        job = scope.launch {
            // Never capture frame times from a previous game/launcher window.
            runCatching { CallbackBridge.resetFrameTimeSamples() }
            var previousCpu = runCatching { PerformanceMonitor.readCpuTimes() }.getOrNull()
            val buffer = IntArray(FRAME_BUFFER_SIZE)

            while (isActive) {
                val count = runCatching {
                    CallbackBridge.getFrameTimeSamples(buffer)
                }.getOrDefault(0)
                if (count > 0) {
                    session.addFrameTimes(buffer, count)
                }

                val sample = runCatching {
                    withContext(Dispatchers.IO) {
                        PerformanceMonitor.read(appContext, previousCpu)
                    }
                }.getOrNull()
                if (sample != null) {
                    previousCpu = sample.cpuTimes
                    session.addDeviceSample(
                        cpuPercent = sample.snapshot.cpuUsagePercent,
                        memory = sample.snapshot.memory,
                        temperatureCelsius = sample.snapshot.thermal?.socCelsius
                    )
                }

                delay(POLL_INTERVAL_MS)
            }
        }
    }

    /**
     * Stops recording and stores the measured summary. Safe to call when nothing runs.
     * Percentile sorting happens off the main thread so leaving the game stays smooth.
     */
    fun stop(context: Context) {
        val session = accumulator ?: return
        job?.cancel()
        job = null
        accumulator = null

        val appContext = context.applicationContext
        scope.launch {
            val summary = session.buildSummary(System.currentTimeMillis()) ?: return@launch
            GameSessionStore.write(appContext, summary)
        }
    }
}
