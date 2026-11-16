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

/*
 * AetherRender v1 — EGL/tile-GPU tuning layer.
 *
 * This file is intentionally small and only touches things the launcher can prove:
 *  - it chooses an EGL config without multisampling and with a 24-bit depth buffer,
 *  - it asks the driver to discard the back buffer after swap on tile-based GPUs,
 *  - it logs the real GL strings for support reports.
 *
 * It does not translate desktop GL (GL4ES does that), does not rewrite Minecraft's
 * chunk renderer and does not claim an FPS increase. See docs/AETHERRENDER.md.
 */

#include <android/log.h>
#include <EGL/egl.h>
#include <limits.h>
#include <stdbool.h>
#include <stdlib.h>
#include <string.h>

#include "../ctxbridges/egl_loader.h"
#include "aether_render.h"

#define AETHER_LOG_TAG "AetherRender"

#ifndef EGL_SWAP_BEHAVIOR
#define EGL_SWAP_BEHAVIOR 0x3093
#endif
#ifndef EGL_BUFFER_DESTROYED
#define EGL_BUFFER_DESTROYED 0x3095
#endif

#define GL_VENDOR 0x1F00
#define GL_RENDERER 0x1F01
#define GL_VERSION 0x1F02
#define GL_SHADING_LANGUAGE_VERSION 0x8B8C

static int g_enabled = -1;
static int g_gpu_logged = 0;
static int g_surface_logged = 0;

static int env_is_enabled(const char *value) {
    if (value == NULL) return 0;
    return strcmp(value, "1") == 0 ||
           strcmp(value, "on") == 0 ||
           strcmp(value, "true") == 0 ||
           strcmp(value, "TRUE") == 0;
}

bool aether_render_enabled(void) {
    if (g_enabled < 0) {
        g_enabled = env_is_enabled(getenv("AETHER_RENDER"));
        __android_log_print(ANDROID_LOG_INFO, AETHER_LOG_TAG,
                            "AetherRender %s", g_enabled ? "enabled" : "disabled");
    }
    return g_enabled == 1;
}

EGLConfig aether_render_choose_config(EGLDisplay display, EGLint *out_format) {
    const EGLint attributes[] = {
            EGL_BLUE_SIZE, 8,
            EGL_GREEN_SIZE, 8,
            EGL_RED_SIZE, 8,
            EGL_ALPHA_SIZE, 8,
            EGL_DEPTH_SIZE, 24,
            EGL_SURFACE_TYPE, EGL_WINDOW_BIT | EGL_PBUFFER_BIT,
            EGL_RENDERABLE_TYPE, EGL_OPENGL_ES2_BIT,
            EGL_NONE
    };

    EGLConfig configs[64];
    EGLint num_configs = 0;
    if (eglChooseConfig_p == NULL ||
        eglChooseConfig_p(display, attributes, configs, 64, &num_configs) != EGL_TRUE) {
        __android_log_print(ANDROID_LOG_ERROR, AETHER_LOG_TAG,
                            "eglChooseConfig failed: %04x", eglGetError_p ? eglGetError_p() : 0);
        return NULL;
    }
    if (num_configs <= 0) {
        __android_log_print(ANDROID_LOG_ERROR, AETHER_LOG_TAG,
                            "eglChooseConfig found no matching config");
        return NULL;
    }

    EGLConfig chosen = configs[0];
    if (aether_render_enabled() && eglGetConfigAttrib_p != NULL) {
        long best_score = LONG_MIN;
        int best_index = 0;

        for (EGLint i = 0; i < num_configs; i++) {
            EGLint surface_type = 0, renderable_type = 0;
            EGLint red = 0, green = 0, blue = 0, alpha = 0, depth = 0, stencil = 0;
            EGLint samples = 0, sample_buffers = 0, caveat = EGL_NONE;

            eglGetConfigAttrib_p(display, configs[i], EGL_SURFACE_TYPE, &surface_type);
            eglGetConfigAttrib_p(display, configs[i], EGL_RENDERABLE_TYPE, &renderable_type);
            eglGetConfigAttrib_p(display, configs[i], EGL_RED_SIZE, &red);
            eglGetConfigAttrib_p(display, configs[i], EGL_GREEN_SIZE, &green);
            eglGetConfigAttrib_p(display, configs[i], EGL_BLUE_SIZE, &blue);
            eglGetConfigAttrib_p(display, configs[i], EGL_ALPHA_SIZE, &alpha);
            eglGetConfigAttrib_p(display, configs[i], EGL_DEPTH_SIZE, &depth);
            eglGetConfigAttrib_p(display, configs[i], EGL_STENCIL_SIZE, &stencil);
            eglGetConfigAttrib_p(display, configs[i], EGL_SAMPLES, &samples);
            eglGetConfigAttrib_p(display, configs[i], EGL_SAMPLE_BUFFERS, &sample_buffers);
            eglGetConfigAttrib_p(display, configs[i], EGL_CONFIG_CAVEAT, &caveat);

            if ((surface_type & EGL_WINDOW_BIT) == 0) continue;
            if ((renderable_type & EGL_OPENGL_ES2_BIT) == 0) continue;
            if (red < 8 || green < 8 || blue < 8) continue;

            long score = 0;
            // Multisampling is the single most expensive mistake on bandwidth-limited GPUs.
            score -= (long) samples * 20;
            score -= (long) sample_buffers * 40;
            // Minecraft needs a depth buffer; 24 bits avoids z-fighting at distance.
            score += depth >= 24 ? 60 : (depth >= 16 ? 10 : -100);
            score += alpha >= 8 ? 10 : 0;
            score += stencil >= 8 ? 2 : 0;
            score += caveat == EGL_NONE ? 20 : -20;

            if (score > best_score) {
                best_score = score;
                best_index = i;
            }
        }
        chosen = configs[best_index];
    }

    EGLint visual = 0;
    if (eglGetConfigAttrib_p != NULL) {
        eglGetConfigAttrib_p(display, chosen, EGL_NATIVE_VISUAL_ID, &visual);
    }
    if (out_format != NULL) *out_format = visual;

    if (aether_render_enabled() && eglGetConfigAttrib_p != NULL) {
        EGLint depth = 0, samples = 0, sample_buffers = 0;
        eglGetConfigAttrib_p(display, chosen, EGL_DEPTH_SIZE, &depth);
        eglGetConfigAttrib_p(display, chosen, EGL_SAMPLES, &samples);
        eglGetConfigAttrib_p(display, chosen, EGL_SAMPLE_BUFFERS, &sample_buffers);
        __android_log_print(ANDROID_LOG_INFO, AETHER_LOG_TAG,
                            "EGL config: depth=%d samples=%d sample_buffers=%d visual=0x%x",
                            depth, samples, sample_buffers, visual);
    }
    return chosen;
}

void aether_render_on_surface_created(EGLDisplay display, EGLSurface surface) {
    if (!aether_render_enabled() || display == EGL_NO_DISPLAY || surface == EGL_NO_SURFACE) {
        return;
    }
    if (eglSurfaceAttrib_p == NULL) return;

    if (eglSurfaceAttrib_p(display, surface, EGL_SWAP_BEHAVIOR, EGL_BUFFER_DESTROYED)) {
        if (!g_surface_logged) {
            __android_log_print(ANDROID_LOG_INFO, AETHER_LOG_TAG,
                                "Surface swap behaviour: EGL_BUFFER_DESTROYED");
            g_surface_logged = 1;
        }
    } else if (!g_surface_logged) {
        // Not fatal: many drivers only expose the default behaviour.
        __android_log_print(ANDROID_LOG_WARN, AETHER_LOG_TAG,
                            "eglSurfaceAttrib(EGL_SWAP_BEHAVIOR) rejected: %04x",
                            eglGetError_p ? eglGetError_p() : 0);
        g_surface_logged = 1;
    }
}

void aether_render_on_context_created(EGLDisplay display, EGLContext context) {
    (void) display;
    (void) context;
    if (!aether_render_enabled() || g_gpu_logged) return;
    if (eglGetProcAddress_p == NULL) return;

    typedef const unsigned char *(*PFN_GL_GET_STRING)(unsigned int name);
    PFN_GL_GET_STRING glGetString =
            (PFN_GL_GET_STRING) eglGetProcAddress_p("glGetString");
    if (glGetString == NULL) return;

    const char *vendor = (const char *) glGetString(GL_VENDOR);
    const char *renderer = (const char *) glGetString(GL_RENDERER);
    const char *version = (const char *) glGetString(GL_VERSION);
    const char *glsl = (const char *) glGetString(GL_SHADING_LANGUAGE_VERSION);

    __android_log_print(ANDROID_LOG_INFO, AETHER_LOG_TAG,
                        "GPU vendor=%s renderer=%s version=%s glsl=%s",
                        vendor ? vendor : "?",
                        renderer ? renderer : "?",
                        version ? version : "?",
                        glsl ? glsl : "?");
    g_gpu_logged = 1;
}
