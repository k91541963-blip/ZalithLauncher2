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

package com.movtery.zalithlauncher.setting.enums

import com.movtery.zalithlauncher.R

/**
 * EGL policy used by AetherRender.
 *
 * The mode only changes decisions the launcher can actually make (EGL config selection
 * and surface hints). It never claims an FPS increase and does not touch Minecraft's
 * chunk renderer.
 *
 * @param nameRes label shown in the renderer settings
 * @param envValue value passed to the native layer as `AETHER_RENDER_MODE`
 */
enum class AetherRenderMode(val nameRes: Int, val envValue: String) {
    /** Stock EGL selection and no surface hints; useful to isolate driver issues. */
    NATIVE(R.string.aether_render_mode_native, "native"),

    /** Default: no MSAA, 24-bit depth, `EGL_BUFFER_DESTROYED`. */
    BALANCED(R.string.aether_render_mode_balanced, "balanced"),

    /** Balanced policy, preferring configs without stencil and without alpha. */
    LITE(R.string.aether_render_mode_lite, "lite"),

    /** Prefers 16-bit RGB565 window configs to save memory bandwidth (opt-in). */
    PERFORMANCE(R.string.aether_render_mode_performance, "performance"),

    /** Stock EGL selection and no surface hints; same as native, kept for support reports. */
    COMPATIBILITY(R.string.aether_render_mode_compatibility, "compatibility")
}
