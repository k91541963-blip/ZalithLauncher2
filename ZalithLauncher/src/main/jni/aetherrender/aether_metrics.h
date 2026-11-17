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

#ifndef AETHER_METRICS_H
#define AETHER_METRICS_H

#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

/**
 * Real frame pacing capture.
 *
 * The game calls this once per buffer swap. Consecutive swap timestamps are stored
 * as microsecond deltas in a fixed ring buffer, so the launcher can read genuine
 * frame times (and therefore genuine 1% low / P99 values) instead of guessing them
 * from the once-per-second integer FPS counter.
 *
 * The hook is a `clock_gettime(CLOCK_MONOTONIC)` call plus a short critical section;
 * it changes no rendering state and cannot fail the launch. Gaps longer than
 * [AETHER_METRICS_MAX_GAP_US] (pause menu, dimension load, app backgrounded) are
 * dropped instead of being recorded as a huge frame time.
 */
#define AETHER_METRICS_RING 2048
#define AETHER_METRICS_MAX_GAP_US 1000000

/** Records one presented frame. Safe to call from the render thread only. */
void aether_metrics_on_frame(void);

/**
 * Copies the newest `max` frame time deltas (microseconds) in chronological order.
 * @return the number of samples written to `out` (0 when nothing was captured).
 */
int aether_metrics_copy(uint32_t *out, int max);

/** Drops every captured sample and restarts delta tracking. */
void aether_metrics_reset(void);

#ifdef __cplusplus
}
#endif

#endif // AETHER_METRICS_H
