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
 * AetherRender 3 — EGL/tile-GPU tuning layer.
 *
 * This file is intentionally small and only touches things the launcher can prove:
 *  - it chooses an EGL config without multisampling and with a 24-bit depth buffer,
 *  - it asks the driver to discard the back buffer after swap on tile-based GPUs,
 *  - it applies one of five selectable policies (native, balanced, lite, performance,
 *    compatibility) and logs every decision,
 *  - it applies the user's real VSync choice with eglSwapInterval,
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
#ifndef EGL_OPENGL_ES3_BIT_KHR
#define EGL_OPENGL_ES3_BIT_KHR 0x0040
#endif

#define GL_VENDOR 0x1F00
#define GL_RENDERER 0x1F01
#define GL_VERSION 0x1F02
#define GL_SHADING_LANGUAGE_VERSION 0x8B8C

typedef enum {
    MODE_NATIVE = 0,
    MODE_BALANCED,
    MODE_LITE,
    MODE_PERFORMANCE,
    MODE_COMPATIBILITY
} AetherMode;

static int g_enabled = -1;
static int g_mode_loaded = 0;
static AetherMode g_mode = MODE_BALANCED;
static int g_gpu_logged = 0;
static int g_surface_logged = 0;
static int g_config_logged = 0;
static int g_swap_logged = 0;

static int env_is_enabled(const char *value) {
    if (value == NULL) return 0;
    return strcmp(value, "1") == 0 ||
           strcmp(value, "on") == 0 ||
           strcmp(value, "true") == 0 ||
           strcmp(value, "TRUE") == 0;
}

static const char *mode_name(AetherMode mode) {
    switch (mode) {
        case MODE_NATIVE: return "native";
        case MODE_LITE: return "lite";
        case MODE_PERFORMANCE: return "performance";
        case MODE_COMPATIBILITY: return "compatibility";
        case MODE_BALANCED:
        default: return "balanced";
    }
}

static AetherMode parse_mode(const char *value) {
    if (value == NULL || value[0] == '\0') return MODE_BALANCED;
    if (strcmp(value, "native") == 0) return MODE_NATIVE;
    if (strcmp(value, "lite") == 0) return MODE_LITE;
    if (strcmp(value, "performance") == 0) return MODE_PERFORMANCE;
    if (strcmp(value, "compatibility") == 0) return MODE_COMPATIBILITY;
    return MODE_BALANCED;
}

static AetherMode aether_mode(void) {
    if (!g_mode_loaded) {
        g_mode = parse_mode(getenv("AETHER_RENDER_MODE"));
        g_mode_loaded = 1;
    }
    return g_mode;
}

bool aether_render_enabled(void) {
    if (g_enabled < 0) {
        g_enabled = env_is_enabled(getenv("AETHER_RENDER"));
        __android_log_print(ANDROID_LOG_INFO, AETHER_LOG_TAG,
                            "AetherRender %s (mode=%s)",
                            g_enabled ? "enabled" : "disabled",
                            mode_name(aether_mode()));
    }
    return g_enabled == 1;
}

/**
 * Requested swap interval: 0 = VSync off, 1 = VSync on, -1 = launcher said nothing.
 * AetherRender 3 exposes this as a real user setting (`AETHER_SWAP_INTERVAL`); when the
 * variable is absent the driver default is left untouched, exactly like 2.x.
 */
static int requested_swap_interval(void) {
    const char *value = getenv("AETHER_SWAP_INTERVAL");
    if (value == NULL || value[0] == '\0') return -1;
    if (strcmp(value, "0") == 0) return 0;
    if (strcmp(value, "1") == 0) return 1;
    return -1;
}

/**
 * Applies the requested swap interval with `eglSwapInterval`. On strict drivers the
 * call only sticks once the surface is current, so it is attempted on surface creation
 * and again on context creation. Failures are logged and never abort the launch.
 */
static void apply_swap_interval(EGLDisplay display, EGLSurface surface) {
    if (!aether_render_enabled() || eglSwapInterval_p == NULL) return;
    const int interval = requested_swap_interval();
    if (interval < 0) return;

    if (surface == EGL_NO_SURFACE && eglGetCurrentSurface_p != NULL) {
        surface = eglGetCurrentSurface_p(EGL_DRAW);
    }
    if (surface == EGL_NO_SURFACE) return;

    const EGLBoolean ok = eglSwapInterval_p(display, interval);
    if (!g_swap_logged) {
        if (ok) {
            __android_log_print(ANDROID_LOG_INFO, AETHER_LOG_TAG,
                                "Swap interval: %d (VSync %s)", interval,
                                interval == 0 ? "off" : "on");
        } else {
            __android_log_print(ANDROID_LOG_WARN, AETHER_LOG_TAG,
                                "eglSwapInterval(%d) rejected: %04x", interval,
                                eglGetError_p != NULL ? eglGetError_p() : 0);
        }
        g_swap_logged = 1;
    }
}

/** True when the mode only logs and leaves EGL selection to the stock path. */
static bool mode_is_stock_egl(AetherMode mode) {
    return mode == MODE_NATIVE || mode == MODE_COMPATIBILITY;
}

/**
 * Renderable bit must match the context version that gl_init_context() will request
 * from LIBGL_ES. NG-GL4ES asks for GLES 3 (Minecraft 1.17+ / 26.x); asking for an ES2
 * config there makes eglCreateContext() fail with EGL_BAD_MATCH on strict drivers.
 */
static EGLint required_renderable_bit(void) {
    const char *value = getenv("LIBGL_ES");
    if (value != NULL && atoi(value) >= 3) {
        return EGL_OPENGL_ES3_BIT_KHR;
    }
    return EGL_OPENGL_ES2_BIT;
}

static EGLConfig choose_stock(EGLDisplay display, const EGLint *attributes,
                              EGLint *out_format) {
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
    EGLint visual = 0;
    if (eglGetConfigAttrib_p != NULL) {
        eglGetConfigAttrib_p(display, chosen, EGL_NATIVE_VISUAL_ID, &visual);
    }
    if (out_format != NULL) *out_format = visual;
    return chosen;
}

/** Scores one config for the active mode. lower-than-LLONG_MIN means "reject". */
static long score_config(EGLDisplay display, EGLConfig config, AetherMode mode) {
    EGLint surface_type = 0, renderable_type = 0;
    EGLint red = 0, green = 0, blue = 0, alpha = 0, depth = 0, stencil = 0;
    EGLint samples = 0, sample_buffers = 0, caveat = EGL_NONE;

    eglGetConfigAttrib_p(display, config, EGL_SURFACE_TYPE, &surface_type);
    eglGetConfigAttrib_p(display, config, EGL_RENDERABLE_TYPE, &renderable_type);
    eglGetConfigAttrib_p(display, config, EGL_RED_SIZE, &red);
    eglGetConfigAttrib_p(display, config, EGL_GREEN_SIZE, &green);
    eglGetConfigAttrib_p(display, config, EGL_BLUE_SIZE, &blue);
    eglGetConfigAttrib_p(display, config, EGL_ALPHA_SIZE, &alpha);
    eglGetConfigAttrib_p(display, config, EGL_DEPTH_SIZE, &depth);
    eglGetConfigAttrib_p(display, config, EGL_STENCIL_SIZE, &stencil);
    eglGetConfigAttrib_p(display, config, EGL_SAMPLES, &samples);
    eglGetConfigAttrib_p(display, config, EGL_SAMPLE_BUFFERS, &sample_buffers);
    eglGetConfigAttrib_p(display, config, EGL_CONFIG_CAVEAT, &caveat);

    if ((surface_type & EGL_WINDOW_BIT) == 0) return LONG_MIN;
    if ((renderable_type & required_renderable_bit()) == 0) return LONG_MIN;
    if (red < 5 || green < 5 || blue < 5) return LONG_MIN;

    long score = 0;
    // Multisampling is the single most expensive mistake on bandwidth-limited GPUs.
    score -= (long) samples * 20;
    score -= (long) sample_buffers * 40;
    // Minecraft needs a depth buffer; 24 bits avoids z-fighting at distance.
    score += depth >= 24 ? 60 : (depth >= 16 ? 10 : -100);
    score += caveat == EGL_NONE ? 20 : -20;

    const bool is_565 = red == 5 && green == 6 && blue == 5;
    const bool is_888 = red >= 8 && green >= 8 && blue >= 8;

    switch (mode) {
        case MODE_LITE:
            // Favour opaque, stencil-free configs: fewer bytes per pixel and less state.
            score += alpha == 0 ? 30 : 0;
            score += stencil == 0 ? 10 : 0;
            score += is_888 ? 10 : 0;
            break;

        case MODE_PERFORMANCE:
            // Opt-in 16-bit colour: halves the window bandwidth at the cost of banding.
            score += is_565 ? 150 : 0;
            score += alpha == 0 ? 20 : 0;
            score += stencil == 0 ? 10 : 0;
            score += is_888 ? 5 : 0;
            break;

        case MODE_BALANCED:
        case MODE_NATIVE:
        case MODE_COMPATIBILITY:
        default:
            score += is_888 ? 30 : 0;
            score += alpha >= 8 ? 10 : 0;
            score += stencil >= 8 ? 2 : 0;
            break;
    }

    return score;
}

EGLConfig aether_render_choose_config(EGLDisplay display, EGLint *out_format) {
    const AetherMode mode = aether_mode();

    if (!aether_render_enabled() || mode_is_stock_egl(mode)) {
        const EGLint stock_attributes[] = {
                EGL_BLUE_SIZE, 8,
                EGL_GREEN_SIZE, 8,
                EGL_RED_SIZE, 8,
                EGL_ALPHA_SIZE, 8,
                EGL_DEPTH_SIZE, 24,
                EGL_SURFACE_TYPE, EGL_WINDOW_BIT | EGL_PBUFFER_BIT,
                EGL_RENDERABLE_TYPE, required_renderable_bit(),
                EGL_NONE
        };
        EGLConfig config = choose_stock(display, stock_attributes, out_format);
        if (!g_config_logged) {
            __android_log_print(ANDROID_LOG_INFO, AETHER_LOG_TAG,
                                "EGL config: stock path (mode=%s)", mode_name(mode));
            g_config_logged = 1;
        }
        return config;
    }

    // Lite skips alpha; performance asks for at least RGB565 so 16-bit configs qualify.
    EGLint attributes[32];
    int index = 0;
    attributes[index++] = EGL_BLUE_SIZE;
    attributes[index++] = (mode == MODE_PERFORMANCE) ? 5 : 8;
    attributes[index++] = EGL_GREEN_SIZE;
    attributes[index++] = (mode == MODE_PERFORMANCE) ? 6 : 8;
    attributes[index++] = EGL_RED_SIZE;
    attributes[index++] = (mode == MODE_PERFORMANCE) ? 5 : 8;
    if (mode != MODE_LITE && mode != MODE_PERFORMANCE) {
        attributes[index++] = EGL_ALPHA_SIZE;
        attributes[index++] = 8;
    }
    attributes[index++] = EGL_DEPTH_SIZE;
    attributes[index++] = 24;
    attributes[index++] = EGL_SURFACE_TYPE;
    attributes[index++] = EGL_WINDOW_BIT | EGL_PBUFFER_BIT;
    attributes[index++] = EGL_RENDERABLE_TYPE;
    attributes[index++] = required_renderable_bit();
    attributes[index] = EGL_NONE;

    EGLConfig configs[64];
    EGLint num_configs = 0;
    if (eglChooseConfig_p == NULL ||
        eglChooseConfig_p(display, attributes, configs, 64, &num_configs) != EGL_TRUE ||
        num_configs <= 0) {
        __android_log_print(ANDROID_LOG_WARN, AETHER_LOG_TAG,
                            "No config for mode=%s, falling back to balanced attributes",
                            mode_name(mode));
        const EGLint fallback[] = {
                EGL_BLUE_SIZE, 8,
                EGL_GREEN_SIZE, 8,
                EGL_RED_SIZE, 8,
                EGL_ALPHA_SIZE, 8,
                EGL_DEPTH_SIZE, 24,
                EGL_SURFACE_TYPE, EGL_WINDOW_BIT | EGL_PBUFFER_BIT,
                EGL_RENDERABLE_TYPE, required_renderable_bit(),
                EGL_NONE
        };
        return choose_stock(display, fallback, out_format);
    }

    EGLConfig chosen = configs[0];
    long best_score = LONG_MIN;
    for (EGLint i = 0; i < num_configs; i++) {
        long score = score_config(display, configs[i], mode);
        if (score != LONG_MIN && score > best_score) {
            best_score = score;
            chosen = configs[i];
        }
    }

    EGLint visual = 0, depth = 0, alpha = 0, red = 0, green = 0, blue = 0;
    EGLint samples = 0, sample_buffers = 0;
    if (eglGetConfigAttrib_p != NULL) {
        eglGetConfigAttrib_p(display, chosen, EGL_NATIVE_VISUAL_ID, &visual);
        eglGetConfigAttrib_p(display, chosen, EGL_DEPTH_SIZE, &depth);
        eglGetConfigAttrib_p(display, chosen, EGL_ALPHA_SIZE, &alpha);
        eglGetConfigAttrib_p(display, chosen, EGL_RED_SIZE, &red);
        eglGetConfigAttrib_p(display, chosen, EGL_GREEN_SIZE, &green);
        eglGetConfigAttrib_p(display, chosen, EGL_BLUE_SIZE, &blue);
        eglGetConfigAttrib_p(display, chosen, EGL_SAMPLES, &samples);
        eglGetConfigAttrib_p(display, chosen, EGL_SAMPLE_BUFFERS, &sample_buffers);
    }
    if (out_format != NULL) *out_format = visual;

    if (!g_config_logged) {
        __android_log_print(ANDROID_LOG_INFO, AETHER_LOG_TAG,
                            "EGL config: mode=%s rgb=%d/%d/%d alpha=%d depth=%d samples=%d sample_buffers=%d visual=0x%x",
                            mode_name(mode), red, green, blue, alpha, depth,
                            samples, sample_buffers, visual);
        g_config_logged = 1;
    }
    return chosen;
}

void aether_render_on_surface_created(EGLDisplay display, EGLSurface surface) {
    if (!aether_render_enabled() || display == EGL_NO_DISPLAY || surface == EGL_NO_SURFACE) {
        return;
    }

    // AetherRender 3: VSync is an independent axis, applied in every EGL mode.
    apply_swap_interval(display, surface);

    const AetherMode mode = aether_mode();
    if (mode_is_stock_egl(mode)) {
        if (!g_surface_logged) {
            __android_log_print(ANDROID_LOG_INFO, AETHER_LOG_TAG,
                                "Surface hints disabled (mode=%s)", mode_name(mode));
            g_surface_logged = 1;
        }
        return;
    }
    if (eglSurfaceAttrib_p == NULL) return;

    if (eglSurfaceAttrib_p(display, surface, EGL_SWAP_BEHAVIOR, EGL_BUFFER_DESTROYED)) {
        if (!g_surface_logged) {
            __android_log_print(ANDROID_LOG_INFO, AETHER_LOG_TAG,
                                "Surface swap behaviour: EGL_BUFFER_DESTROYED (mode=%s)",
                                mode_name(mode));
            g_surface_logged = 1;
        }
    } else if (!g_surface_logged) {
        // Not fatal: many drivers only expose the default behaviour.
        __android_log_print(ANDROID_LOG_WARN, AETHER_LOG_TAG,
                            "eglSurfaceAttrib(EGL_SWAP_BEHAVIOR) rejected: %04x (mode=%s)",
                            eglGetError_p ? eglGetError_p() : 0,
                            mode_name(mode));
        g_surface_logged = 1;
    }
}

void aether_render_on_context_created(EGLDisplay display, EGLContext context) {
    (void) context;
    if (!aether_render_enabled()) return;
    // The surface is bound now, so a strict driver can accept eglSwapInterval here.
    apply_swap_interval(display, EGL_NO_SURFACE);
    if (g_gpu_logged) return;
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
                        "GPU vendor=%s renderer=%s version=%s glsl=%s mode=%s",
                        vendor ? vendor : "?",
                        renderer ? renderer : "?",
                        version ? version : "?",
                        glsl ? glsl : "?",
                        mode_name(aether_mode()));
    g_gpu_logged = 1;
}
