/*
 * NovaLauncher
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
import androidx.annotation.WorkerThread
import com.movtery.zalithlauncher.utils.device.Architecture
import com.movtery.zalithlauncher.utils.platform.bytesToMB
import com.movtery.zalithlauncher.utils.platform.getFreeMemory
import com.movtery.zalithlauncher.utils.platform.getTotalMemory

/**
 * Best effort description of the GPU family.
 *
 * This is a heuristic based on device strings, not a driver query: Android does not expose
 * the GPU vendor without an active OpenGL context. Never treat it as authoritative.
 */
enum class GpuFamily {
    ADRENO,
    MALI,
    POWERVR,
    XCLIPSE,
    UNKNOWN
}

/**
 * Snapshot of the device capabilities used to recommend memory and renderer settings.
 *
 * All fields are read from stable Android APIs. Values that cannot be read are kept as
 * conservative defaults instead of throwing.
 */
data class HardwareProfile(
    val supportedAbis: List<String>,
    val is64Bit: Boolean,
    val cpuCores: Int,
    val totalRamMb: Int,
    val freeRamMb: Int,
    val glEsMajor: Int,
    val glEsMinor: Int,
    val gpuFamily: GpuFamily,
    val socModel: String,
    val androidSdk: Int,
    val deviceName: String
) {
    /** True when the device is expected to struggle with modern Minecraft versions. */
    val isLowEnd: Boolean
        get() = !is64Bit || totalRamMb < 4096 || cpuCores <= 4

    /** OpenGL ES version as a comparable number, e.g. `3.2` -> 32. */
    val glEsVersionCode: Int
        get() = glEsMajor * 10 + glEsMinor

    companion object {
        @WorkerThread
        fun detect(context: Context): HardwareProfile {
            val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val glEs = parseGlEsVersion(activityManager.deviceConfigurationInfo.glEsVersion)

            return HardwareProfile(
                supportedAbis = Build.SUPPORTED_ABIS?.toList().orEmpty(),
                is64Bit = Architecture.is64BitsDevice,
                cpuCores = Runtime.getRuntime().availableProcessors().coerceAtLeast(1),
                totalRamMb = getTotalMemory(context).bytesToMB(decimals = 0, roundDown = true).toInt(),
                freeRamMb = getFreeMemory(context).bytesToMB(decimals = 0, roundDown = true).toInt(),
                glEsMajor = glEs.first,
                glEsMinor = glEs.second,
                gpuFamily = detectGpuFamily(),
                socModel = readSocModel(),
                androidSdk = Build.VERSION.SDK_INT,
                deviceName = listOf(Build.MANUFACTURER, Build.MODEL)
                    .filter { it.isNotBlank() }
                    .joinToString(" ")
                    .trim()
            )
        }

        /** Parses `glEsVersion` such as `3.2` or `2.0`; returns `0.0` when unreadable. */
        fun parseGlEsVersion(raw: String?): Pair<Int, Int> {
            val parts = raw?.trim().orEmpty().split('.')
            if (parts.isEmpty()) return 0 to 0
            val major = parts.getOrNull(0)?.toIntOrNull() ?: 0
            val minor = parts.getOrNull(1)?.toIntOrNull() ?: 0
            return major to minor
        }

        private fun readSocModel(): String {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                Build.SOC_MODEL.takeIf { it.isNotBlank() && it != Build.UNKNOWN }
                    ?: Build.HARDWARE.orEmpty()
            } else {
                Build.HARDWARE.orEmpty()
            }
        }

        private fun detectGpuFamily(): GpuFamily {
            val haystack = buildString {
                append(readSocModel()).append(' ')
                append(Build.HARDWARE.orEmpty()).append(' ')
                append(Build.BOARD.orEmpty()).append(' ')
                append(Build.MODEL.orEmpty())
            }.lowercase()

            return when {
                haystack.contains("adreno") || haystack.contains("qcom") ||
                    haystack.contains("qualcomm") || haystack.contains("msm") ||
                    haystack.contains("sm8") || haystack.contains("sm7") -> GpuFamily.ADRENO

                haystack.contains("mali") || haystack.contains("exynos") ||
                    haystack.contains("mediatek") || haystack.contains("mt6") ||
                    haystack.contains("mt8") -> GpuFamily.MALI

                haystack.contains("powervr") -> GpuFamily.POWERVR
                haystack.contains("xclipse") || haystack.contains("amd") -> GpuFamily.XCLIPSE
                else -> GpuFamily.UNKNOWN
            }
        }
    }
}
