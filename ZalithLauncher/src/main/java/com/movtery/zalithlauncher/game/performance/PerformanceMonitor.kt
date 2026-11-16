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

import android.app.ActivityManager
import android.content.Context
import android.os.Debug
import androidx.annotation.WorkerThread
import java.io.File

/**
 * System memory and launcher process memory.
 *
 * All values are read from Android APIs; nothing is estimated. [appPssMb] is the real
 * proportional set size reported by the kernel for this process, not the JVM heap.
 */
data class MemorySnapshot(
    val totalMb: Int,
    val availableMb: Int,
    val lowMemory: Boolean,
    val appPssMb: Int?,
    val javaHeapUsedMb: Int,
    val javaHeapMaxMb: Int
) {
    val usedMb: Int
        get() = (totalMb - availableMb).coerceAtLeast(0)
}

/**
 * One measured snapshot. Missing readings stay `null` instead of being filled with
 * placeholders, so the UI can honestly say "unknown".
 */
data class PerformanceSnapshot(
    val cpuUsagePercent: Float?,
    val cpuCores: Int,
    val memory: MemorySnapshot,
    val thermal: ThermalInfo?,
    val gpu: GpuInfo?,
    val maliTier: MaliTier?
)

/**
 * Result of a sample: the snapshot plus the CPU counters needed for the next delta.
 * The caller keeps [cpuTimes] and passes it back to [PerformanceMonitor.read].
 */
data class PerformanceSample(
    val snapshot: PerformanceSnapshot,
    val cpuTimes: CpuTimes?
)

/**
 * Reads live device/process statistics for the Performance Center.
 *
 * Every source is individually guarded. `/proc/stat` is routinely unreadable on newer
 * Android releases; in that case CPU load is `null` and the UI shows "unknown".
 * Call from [kotlinx.coroutines.Dispatchers.IO] only.
 */
object PerformanceMonitor {

    private const val BYTES_PER_MB = 1024L * 1024L

    @WorkerThread
    fun read(context: Context, previousCpuTimes: CpuTimes?): PerformanceSample {
        val appContext = context.applicationContext
        val cpuTimes = readCpuTimes()
        val memory = readMemory(appContext)
        val gpu = GpuInfoCache.get() ?: GpuInfoCache.peek()
        val thermal = runCatching { ThermalInfoReader.read(appContext) }.getOrNull()

        val snapshot = PerformanceSnapshot(
            cpuUsagePercent = CpuUsage.usagePercent(previousCpuTimes, cpuTimes),
            cpuCores = Runtime.getRuntime().availableProcessors().coerceAtLeast(1),
            memory = memory,
            thermal = thermal,
            gpu = gpu,
            maliTier = gpu?.maliModel?.let { GpuInfoReader.classifyMali(it) }
        )
        return PerformanceSample(snapshot = snapshot, cpuTimes = cpuTimes)
    }

    /** Reads `cpu0..cpuN` aggregate counters, or `null` when the file is not readable. */
    @WorkerThread
    fun readCpuTimes(): CpuTimes? = runCatching {
        val file = File("/proc/stat")
        if (!file.isFile || !file.canRead() || file.length() > 64 * 1024) return@runCatching null
        CpuUsage.parse(file.readText())
    }.getOrNull()

    @WorkerThread
    fun readMemory(context: Context): MemorySnapshot {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val info = ActivityManager.MemoryInfo()
        val readOk = runCatching { activityManager?.getMemoryInfo(info) }.isSuccess && info.totalMem > 0

        val totalMb = if (readOk) (info.totalMem / BYTES_PER_MB).toInt() else 0
        val availableMb = if (readOk) (info.availMem / BYTES_PER_MB).toInt() else 0

        val pssKb = runCatching {
            val debugInfo = Debug.MemoryInfo()
            Debug.getMemoryInfo(debugInfo)
            debugInfo.totalPss
        }.getOrNull()

        val runtime = Runtime.getRuntime()
        val heapUsedMb = ((runtime.totalMemory() - runtime.freeMemory()) / BYTES_PER_MB).toInt()
        val heapMaxMb = (runtime.maxMemory() / BYTES_PER_MB).toInt()

        return MemorySnapshot(
            totalMb = totalMb,
            availableMb = availableMb,
            lowMemory = readOk && info.lowMemory,
            appPssMb = pssKb?.takeIf { it > 0 }?.let { it / 1024 },
            javaHeapUsedMb = heapUsedMb.coerceAtLeast(0),
            javaHeapMaxMb = heapMaxMb.coerceAtLeast(0)
        )
    }
}
