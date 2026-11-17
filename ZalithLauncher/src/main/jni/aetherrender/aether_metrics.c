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
 * AetherMetrics — measured frame pacing for the launcher and in-game overlay.
 *
 * Minecraft already tells us how many frames it presented in the last second
 * (`CallbackBridge.getCurrentFps()`), but a per-second integer cannot produce a
 * 1% low or a P99 frame time. This file records the swap timestamps themselves,
 * which is the only honest way to report frame pacing.
 *
 * Cost per frame: one `clock_gettime(CLOCK_MONOTONIC)`, one integer division and a
 * short mutex section. No allocation, no logging, no GL calls.
 */

#include <pthread.h>
#include <string.h>
#include <time.h>

#include "aether_metrics.h"

static pthread_mutex_t g_lock = PTHREAD_MUTEX_INITIALIZER;
static uint32_t g_ring[AETHER_METRICS_RING];
static int g_write_index = 0;
static int g_stored = 0;
static int64_t g_last_frame_ns = 0;

static int64_t monotonic_ns(void) {
    struct timespec ts;
    if (clock_gettime(CLOCK_MONOTONIC, &ts) != 0) {
        return -1;
    }
    return (int64_t) ts.tv_sec * 1000000000LL + (int64_t) ts.tv_nsec;
}

void aether_metrics_on_frame(void) {
    int64_t now = monotonic_ns();
    if (now < 0) {
        return;
    }

    pthread_mutex_lock(&g_lock);
    int64_t previous = g_last_frame_ns;
    g_last_frame_ns = now;

    if (previous > 0 && now > previous) {
        int64_t delta_us = (now - previous) / 1000;
        // Pauses, loading screens and backgrounding produce multi-second gaps.
        // Recording them would fake a "1 FPS" frame and skew every percentile.
        if (delta_us > 0 && delta_us <= AETHER_METRICS_MAX_GAP_US) {
            g_ring[g_write_index] = (uint32_t) delta_us;
            g_write_index = (g_write_index + 1) % AETHER_METRICS_RING;
            if (g_stored < AETHER_METRICS_RING) {
                g_stored++;
            }
        }
    }
    pthread_mutex_unlock(&g_lock);
}

int aether_metrics_copy(uint32_t *out, int max) {
    if (out == NULL || max <= 0) {
        return 0;
    }

    pthread_mutex_lock(&g_lock);
    int count = g_stored < max ? g_stored : max;
    int start = (g_write_index - count + AETHER_METRICS_RING) % AETHER_METRICS_RING;
    for (int i = 0; i < count; i++) {
        out[i] = g_ring[(start + i) % AETHER_METRICS_RING];
    }
    pthread_mutex_unlock(&g_lock);
    return count;
}

void aether_metrics_reset(void) {
    pthread_mutex_lock(&g_lock);
    memset(g_ring, 0, sizeof(g_ring));
    g_write_index = 0;
    g_stored = 0;
    g_last_frame_ns = 0;
    pthread_mutex_unlock(&g_lock);
}
