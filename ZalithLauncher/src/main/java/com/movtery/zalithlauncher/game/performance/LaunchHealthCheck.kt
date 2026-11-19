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
import android.os.Build
import android.os.PowerManager
import android.os.StatFs
import androidx.annotation.WorkerThread
import java.io.File

/** A condition that can make a launch fail or perform poorly. */
enum class LaunchWarning {
    LOW_MEMORY,
    LOW_STORAGE,
    THERMAL,
    /** The JVM heap is small for the number of installed mods. */
    LOW_ALLOCATION
}

/**
 * Result of the pre-launch device check.
 *
 * [freeRamMb] and [freeStorageMb] are snapshots taken at check time; [thermalStatus] is the
 * platform thermal status (0 when the API is unavailable). [modCount] and [allocatedMb]
 * are read from the instance, and [recommendedMb] is only set for a heavy instance whose
 * heap the device can actually raise.
 */
data class LaunchHealth(
    val warnings: List<LaunchWarning>,
    val freeRamMb: Int,
    val freeStorageMb: Int,
    val thermalStatus: Int,
    val modCount: Int = 0,
    val allocatedMb: Int = 0,
    val recommendedMb: Int? = null
)

/**
 * Cheap, read-only pre-launch checks.
 *
 * The thresholds are intentionally conservative: the dialog is only shown when the device is
 * very likely to struggle, not for every small fluctuation. The launcher never blocks a launch
 * on its own; the user can always choose to continue.
 */
object LaunchHealthCheck {
    private const val MIN_FREE_RAM_MB = 512
    private const val MIN_FREE_STORAGE_MB = 1024
    private const val THERMAL_WARNING_STATUS = 3 // PowerManager.THERMAL_STATUS_SEVERE

    @WorkerThread
    fun check(
        context: Context,
        gameDir: File,
        allocatedMb: Int = 0
    ): LaunchHealth {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memoryInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memoryInfo)

        val freeRamMb = (memoryInfo.availMem / (1024L * 1024L)).toInt()

        val storageRoot = gameDir.takeIf { it.exists() } ?: context.filesDir
        val freeStorageMb = runCatching {
            val statFs = StatFs(storageRoot.absolutePath)
            (statFs.availableBytes / (1024L * 1024L)).toInt()
        }.getOrDefault(Int.MAX_VALUE)

        val thermalStatus = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            powerManager.currentThermalStatus
        } else {
            0
        }

        val modCount = MemoryRecommendation.countMods(gameDir)
        // Only heavy instances get a launch-time nudge. A 10-mod pack on 2 GB can be a
        // deliberate choice, and repeating the dialog on every launch would be nagging;
        // a real OOM is reported separately by the recovery card.
        val recommendedMb = if (allocatedMb > 0 && modCount >= MemoryRecommendation.HEAVY_MODS) {
            val advice = MemoryAdvisor.advise(HardwareProfile.detect(context))
            MemoryRecommendation.recommendedMb(allocatedMb, modCount, advice.maxMb)
        } else {
            null
        }

        val warnings = buildList {
            if (freeRamMb < MIN_FREE_RAM_MB) add(LaunchWarning.LOW_MEMORY)
            if (freeStorageMb < MIN_FREE_STORAGE_MB) add(LaunchWarning.LOW_STORAGE)
            if (thermalStatus >= THERMAL_WARNING_STATUS) add(LaunchWarning.THERMAL)
            if (recommendedMb != null) add(LaunchWarning.LOW_ALLOCATION)
        }

        return LaunchHealth(
            warnings = warnings,
            freeRamMb = freeRamMb,
            freeStorageMb = freeStorageMb,
            thermalStatus = thermalStatus,
            modCount = modCount,
            allocatedMb = allocatedMb,
            recommendedMb = recommendedMb
        )
    }
}
