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

#ifndef AETHER_RENDER_H
#define AETHER_RENDER_H

#include <EGL/egl.h>
#include <stdbool.h>

#ifdef __cplusplus
extern "C" {
#endif

/**
 * True when AETHER_RENDER=1 (or on/true) is present in the game environment.
 * Read once per process; the value is set by the launcher before JNI starts.
 */
bool aether_render_enabled(void);

/**
 * Picks an EGL config according to `AETHER_RENDER_MODE`:
 *  - `native`: stock first-matching config, no scoring;
 *  - `balanced` (default): RGB888 + alpha 8 + depth 24 + stencil 8, no MSAA;
 *  - `compatibility`: RGB888 with 16-bit depth and optional alpha/stencil.
 *
 * Every AetherRender mode rejects 16-bit colour: an RGB565 window buffer rendered the
 * world white on several drivers. With AetherRender disabled this reproduces the
 * previous "first matching config" behaviour. If the mode-specific request finds no
 * config, it falls back to the stock attribute set instead of failing the launch.
 *
 * @param out_format receives EGL_NATIVE_VISUAL_ID for the chosen config
 * @return the chosen config or NULL when no config matches
 */
EGLConfig aether_render_choose_config(EGLDisplay display, EGLint *out_format);

/**
 * Applies per-surface policies after eglCreateWindowSurface/eglCreatePbufferSurface.
 * Balanced asks the driver to use EGL_BUFFER_DESTROYED so the back buffer does not need
 * to be preserved across swaps. Native and compatibility leave the driver default
 * untouched.
 *
 * AetherRender 3 also applies `AETHER_SWAP_INTERVAL` (0 = VSync off, 1 = on) here with
 * `eglSwapInterval`; the call is repeated when the context becomes current because some
 * drivers only accept it then. Failures are logged and ignored.
 */
void aether_render_on_surface_created(EGLDisplay display, EGLSurface surface);

/**
 * Logs the real GL strings once the game context is current. This is telemetry only:
 * it never changes rendering state.
 */
void aether_render_on_context_created(EGLDisplay display, EGLContext context);

#ifdef __cplusplus
}
#endif

#endif // AETHER_RENDER_H
