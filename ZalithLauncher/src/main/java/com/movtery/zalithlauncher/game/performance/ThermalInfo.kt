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
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import androidx.annotation.WorkerThread
import java.io.File

/**
 * Best-effort thermal snapshot.
 *
 * Sources, in order of trust:
 *  - [thermalStatus]: the platform thermal API (Android 10+), no permission needed.
 *  - [batteryCelsius]: sticky battery broadcast, no permission needed.
 *  - [socCelsius]: vendor thermal zones under `/sys/class/thermal`; many devices hide
 *    them from apps, so `null` is a normal result, not an error.
 */
data class ThermalInfo(
    val socCelsius: Float? = null,
    val batteryCelsius: Float? = null,
    val thermalStatus: Int? = null
) {
    val hasData: Boolean
        get() = socCelsius != null || batteryCelsius != null || thermalStatus != null
}

/**
 * Reads thermal data without privileged permissions. Every read is individually guarded:
 * one unreadable file must not hide the rest of the information.
 */
object ThermalInfoReader {

    private const val MIN_VALID_C = -20f
    private const val MAX_VALID_C = 150f

    /** Parses a sysfs value (`45000` millidegrees or `45.0`) into Celsius. */
    fun parseCelsius(raw: String?): Float? {
        val text = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val value = text.toFloatOrNull() ?: return null
        val celsius = if (text.contains('.')) value else value / 1000f
        return celsius.takeIf { it in MIN_VALID_C..MAX_VALID_C }
    }

    @WorkerThread
    fun read(context: Context): ThermalInfo {
        return ThermalInfo(
            socCelsius = readSocCelsius(),
            batteryCelsius = readBatteryCelsius(context),
            thermalStatus = readThermalStatus(context)
        )
    }

    /** Picks the warmest relevant zone (SoC/GPU/CPU) when the kernel exposes one. */
    fun readSocCelsius(): Float? {
        val zones = File("/sys/class/thermal").listFiles() ?: return null
        var best: Float? = null
        for (zone in zones) {
            if (!zone.name.startsWith("thermal_zone")) continue
            val type = runCatching { File(zone, "type").readText().trim().lowercase() }.getOrNull()
                ?: continue
            if (!isRelevantZone(type)) continue
            val value = parseCelsius(
                runCatching { File(zone, "temp").readText() }.getOrNull()
            ) ?: continue
            if (best == null || value > best) best = value
        }
        return best
    }

    private fun isRelevantZone(type: String): Boolean {
        return type.contains("soc") ||
            type.contains("gpu") ||
            type.contains("cpu") ||
            type.contains("battery") ||
            type.contains("ap")
    }

    private fun readBatteryCelsius(context: Context): Float? {
        return runCatching {
            val intent = context.registerReceiver(
                null,
                IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            ) ?: return null
            val tenths = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE)
            if (tenths == Int.MIN_VALUE) return null
            (tenths / 10f).takeIf { it in MIN_VALID_C..MAX_VALID_C }
        }.getOrNull()
    }

    private fun readThermalStatus(context: Context): Int? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
        return runCatching {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            powerManager.currentThermalStatus
        }.getOrNull()
    }
}
