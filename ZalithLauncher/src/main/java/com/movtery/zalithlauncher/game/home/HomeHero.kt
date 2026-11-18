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

package com.movtery.zalithlauncher.game.home

import com.movtery.zalithlauncher.game.performance.GameSessionSummary

/** Why the hero card cannot start the game yet. */
enum class HomePlayBlock {
    NO_ACCOUNT,
    NO_VERSION,
    NO_ACCOUNT_AND_VERSION
}

/** The selected version as the hero card needs it (no Android types). */
data class HeroVersion(
    val name: String,
    val summary: String?
)

/**
 * Last measured session in the form the hero card shows.
 *
 * [averageFps] is `null` when the session produced no frame samples; the UI then omits
 * the FPS instead of printing a fabricated `0`.
 */
data class HeroSession(
    val versionName: String,
    val averageFps: Double?,
    val durationSeconds: Int,
    val endedAt: Long
)

/**
 * Everything the 3.0 hero card renders, derived from data the launcher actually has.
 *
 * Pure Kotlin: the play-button decision is unit tested without Compose or Android.
 */
data class HomeHeroState(
    val accountName: String? = null,
    val version: HeroVersion? = null,
    val lastWorld: RecentWorld? = null,
    val lastSession: HeroSession? = null
) {
    val canPlay: Boolean get() = accountName != null && version != null

    /** `null` means the Play button is usable. */
    val playBlock: HomePlayBlock?
        get() = when {
            accountName == null && version == null -> HomePlayBlock.NO_ACCOUNT_AND_VERSION
            accountName == null -> HomePlayBlock.NO_ACCOUNT
            version == null -> HomePlayBlock.NO_VERSION
            else -> null
        }
}

object HomeHeroBuilder {

    /** Converts a stored session; a session without frames never shows an FPS value. */
    fun fromSummary(summary: GameSessionSummary): HeroSession = HeroSession(
        versionName = summary.versionName,
        averageFps = summary.averageFps.takeIf {
            summary.framesCaptured > 0 && it.isFinite() && it > 0.0
        },
        durationSeconds = summary.durationSeconds.coerceAtLeast(0),
        endedAt = summary.endedAtEpochMs
    )
}
