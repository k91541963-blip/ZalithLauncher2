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

import android.content.Context
import androidx.annotation.WorkerThread

/**
 * Process wide cache for [HardwareProfile].
 *
 * Detection performs binder calls to `ActivityManager`, so it is done once per process and
 * shared by every screen that needs it. The device profile does not change while the app is
 * running, so there is no expiry.
 */
object HardwareProfileCache {

    @Volatile
    private var cached: HardwareProfile? = null

    /** Returns the already detected profile without touching the system, or `null`. */
    fun peek(): HardwareProfile? = cached

    @Synchronized
    @WorkerThread
    fun get(context: Context): HardwareProfile {
        cached?.let { return it }
        val detected = HardwareProfile.detect(context.applicationContext)
        cached = detected
        return detected
    }

    /** Drops the cached profile, e.g. after a configuration change that may alter GLES info. */
    fun clear() {
        cached = null
    }
}
