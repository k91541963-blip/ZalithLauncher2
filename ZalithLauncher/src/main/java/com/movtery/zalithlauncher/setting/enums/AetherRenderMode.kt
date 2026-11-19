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
 * AetherLite 3.0.1 removed the former `lite` and `performance` modes: their only real
 * effect was a stencil-less or 16-bit (RGB565) window surface, which made the world render
 * white on several Android compositors. Saved settings that still name them fall back to
 * [BALANCED], and the native layer maps the old `AETHER_RENDER_MODE` values the same way.
 *
 * @param nameRes label shown in the renderer settings
 * @param envValue value passed to the native layer as `AETHER_RENDER_MODE`
 */
enum class AetherRenderMode(val nameRes: Int, val envValue: String) {
    /** Stock EGL selection and no surface hints; useful to isolate driver issues. */
    NATIVE(R.string.aether_render_mode_native, "native"),

    /**
     * Default: 8-bit RGBA, 24-bit depth, 8-bit stencil (needed by "Fabulous"), no
     * multisampling and `EGL_BUFFER_DESTROYED`.
     */
    BALANCED(R.string.aether_render_mode_balanced, "balanced"),

    /**
     * Relaxed selection for drivers with no 24-bit depth or stencil config: still
     * 8-bit RGB, but 16-bit depth and optional alpha/stencil. No surface hints.
     */
    COMPATIBILITY(R.string.aether_render_mode_compatibility, "compatibility")
}
