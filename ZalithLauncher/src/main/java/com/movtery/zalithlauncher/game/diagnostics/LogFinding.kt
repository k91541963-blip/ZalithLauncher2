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

package com.movtery.zalithlauncher.game.diagnostics

/**
 * How serious a [LogFinding] is for the user.
 *
 * [FATAL] means the game could not start or was killed by the runtime, [ERROR] means it
 * started but something broke, [WARNING] is a hint that may explain bad performance.
 */
enum class LogSeverity {
    FATAL,
    ERROR,
    WARNING
}

/** Coarse area of the launcher/game a [LogFinding] belongs to. */
enum class LogCategory {
    MEMORY,
    RENDERER,
    MODS,
    JAVA,
    NETWORK,
    STORAGE,
    ACCOUNT,
    OTHER
}

/**
 * Actionable advice attached to a [LogFinding].
 *
 * The core analyzer never builds user facing text: the UI maps every value to a localized
 * string, so adding a language does not require touching this file.
 */
enum class LogAdvice {
    INCREASE_MEMORY,
    LOWER_MEMORY,
    FREE_MEMORY,
    SWITCH_RENDERER,
    LOWER_GRAPHICS,
    INSTALL_MISSING_MODS,
    REMOVE_CONFLICTING_MODS,
    UPDATE_MODS,
    UPDATE_JAVA,
    VERIFY_GAME_FILES,
    CHECK_NETWORK,
    CHECK_STORAGE,
    SIGN_IN_AGAIN,
    READ_CRASH_REPORT
}

/**
 * One matched line of the log, kept as evidence so the user can verify the conclusion.
 *
 * @param lineNumber 1 based line number inside the analysed text
 * @param text the matching line, trimmed and shortened to a readable length
 */
data class LogEvidence(
    val lineNumber: Int,
    val text: String
)

/**
 * A single conclusion drawn from the log.
 *
 * @param ruleId stable identifier, also used by the UI to pick the title string
 * @param confidence 0..1, how much the matched lines support this conclusion
 */
data class LogFinding(
    val ruleId: String,
    val severity: LogSeverity,
    val category: LogCategory,
    val confidence: Float,
    val advice: List<LogAdvice>,
    val evidence: List<LogEvidence>
)

/**
 * Result of [LogAnalyzer.analyze].
 *
 * @param findings most relevant first
 * @param rootCauses distinct `Caused by:` messages, in log order
 * @param truncated true when the log was longer than the scan limit
 */
data class LogAnalysis(
    val findings: List<LogFinding>,
    val rootCauses: List<String>,
    val mcVersion: String?,
    val javaVersion: String?,
    val scannedLines: Int,
    val truncated: Boolean
) {
    val primary: LogFinding?
        get() = findings.firstOrNull()

    /** True when the log contains something that prevented the game from running properly. */
    val hasBlockingIssue: Boolean
        get() = findings.any { it.severity != LogSeverity.WARNING }
}
