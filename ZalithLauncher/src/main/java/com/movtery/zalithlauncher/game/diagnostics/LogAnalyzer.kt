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

import com.movtery.zalithlauncher.game.diagnostics.LogAdvice.CHECK_NETWORK
import com.movtery.zalithlauncher.game.diagnostics.LogAdvice.CHECK_STORAGE
import com.movtery.zalithlauncher.game.diagnostics.LogAdvice.FREE_MEMORY
import com.movtery.zalithlauncher.game.diagnostics.LogAdvice.INCREASE_MEMORY
import com.movtery.zalithlauncher.game.diagnostics.LogAdvice.INSTALL_MISSING_MODS
import com.movtery.zalithlauncher.game.diagnostics.LogAdvice.LOWER_GRAPHICS
import com.movtery.zalithlauncher.game.diagnostics.LogAdvice.LOWER_MEMORY
import com.movtery.zalithlauncher.game.diagnostics.LogAdvice.READ_CRASH_REPORT
import com.movtery.zalithlauncher.game.diagnostics.LogAdvice.REMOVE_CONFLICTING_MODS
import com.movtery.zalithlauncher.game.diagnostics.LogAdvice.SIGN_IN_AGAIN
import com.movtery.zalithlauncher.game.diagnostics.LogAdvice.SWITCH_RENDERER
import com.movtery.zalithlauncher.game.diagnostics.LogAdvice.UPDATE_JAVA
import com.movtery.zalithlauncher.game.diagnostics.LogAdvice.UPDATE_MODS
import com.movtery.zalithlauncher.game.diagnostics.LogAdvice.VERIFY_GAME_FILES

/**
 * Reads a Minecraft log or crash report and turns it into [LogFinding]s.
 *
 * The analyzer is deliberately conservative: it only reports what is visible in the text.
 * Every finding carries the lines it was derived from, so the UI can show evidence instead
 * of asking the user to trust a black box. Unknown failures produce no finding at all
 * rather than a guess.
 */
object LogAnalyzer {

    /** Default scan limit; large logs are read from the beginning, which is where startup fails. */
    const val DEFAULT_MAX_LINES: Int = 20_000

    private const val MAX_EVIDENCE = 3
    private const val MAX_EVIDENCE_LENGTH = 240
    private const val MAX_ROOT_CAUSES = 8

    private val MC_VERSION = listOf(
        Regex("""Loading Minecraft\s+([0-9][\w.\-+]*)""", RegexOption.IGNORE_CASE),
        Regex("""Minecraft Version:\s*([0-9][\w.\-+]*)""", RegexOption.IGNORE_CASE),
        Regex("""--version\s+([0-9][\w.\-+]*)""", RegexOption.IGNORE_CASE),
        Regex("""Setting user:.*?,\s*version\s+([0-9][\w.\-+]*)""", RegexOption.IGNORE_CASE)
    )

    private val JAVA_VERSION = listOf(
        Regex("""Java version:\s*([0-9][\w.\-+]*)""", RegexOption.IGNORE_CASE),
        Regex("""java\.version\s*=\s*([0-9][\w.\-+]*)""", RegexOption.IGNORE_CASE),
        Regex("""OpenJDK\s+(\d+[\w.\-+]*)""", RegexOption.IGNORE_CASE),
        Regex("""Java HotSpot\(TM\).*\(build\s+([0-9][^)]*)\)""", RegexOption.IGNORE_CASE)
    )

    private val ROOT_CAUSE = Regex("""^Caused by:\s*(.+)$""", RegexOption.IGNORE_CASE)

    private val RULES: List<LogRule> = listOf(
        rule(
            id = "heap_oom",
            severity = LogSeverity.FATAL,
            category = LogCategory.MEMORY,
            confidence = 0.95f,
            advice = listOf(INCREASE_MEMORY, FREE_MEMORY),
            patterns = listOf(
                """OutOfMemoryError.*Java heap space""",
                """GC overhead limit exceeded""",
                """java\.lang\.OutOfMemoryError"""
            )
        ),
        rule(
            id = "native_oom",
            severity = LogSeverity.FATAL,
            category = LogCategory.MEMORY,
            confidence = 0.8f,
            advice = listOf(FREE_MEMORY, LOWER_GRAPHICS),
            patterns = listOf(
                """OutOfMemoryError.*(native memory|unable to create)""",
                """Cannot allocate memory""",
                """mmap failed""",
                """failed to map \d+ bytes"""
            )
        ),
        rule(
            id = "heap_too_large",
            severity = LogSeverity.FATAL,
            category = LogCategory.MEMORY,
            confidence = 0.95f,
            advice = listOf(LOWER_MEMORY),
            patterns = listOf(
                """Could not reserve enough space for .*heap""",
                """Invalid (maximum|initial) heap size"""
            )
        ),
        rule(
            id = "renderer_context",
            severity = LogSeverity.ERROR,
            category = LogCategory.RENDERER,
            confidence = 0.85f,
            advice = listOf(SWITCH_RENDERER, LOWER_GRAPHICS),
            patterns = listOf(
                """Couldn't create (GL|OpenGL|EGL)""",
                """Failed to (create|initialize) (GL|OpenGL|EGL|Vulkan|GLFW|window|context)""",
                """EGL_BAD_[A-Z_]+""",
                """GL_INVALID_(ENUM|VALUE|OPERATION)""",
                """MESA-LOADER""",
                """libGL error""",
                """zink:.*(error|fail)""",
                """vulkan.*(error|failed)"""
            )
        ),
        rule(
            id = "renderer_native",
            severity = LogSeverity.FATAL,
            category = LogCategory.RENDERER,
            confidence = 0.85f,
            advice = listOf(SWITCH_RENDERER),
            patterns = listOf(
                """UnsatisfiedLinkError.*(libGL|libEGL|libgl|renderer|virgl|angle)""",
                """dlopen failed.*\.so"""
            )
        ),
        rule(
            id = "mod_missing_dependency",
            severity = LogSeverity.FATAL,
            category = LogCategory.MODS,
            confidence = 0.9f,
            advice = listOf(INSTALL_MISSING_MODS),
            patterns = listOf(
                """Missing or unsupported mandatory dependencies""",
                """Missing dependenc(y|ies)""",
                """requires .* which is missing""",
                """Incompatible mod set""",
                """ModResolutionException""",
                """Mod ID: .* requires"""
            )
        ),
        rule(
            id = "mod_mixin_failure",
            severity = LogSeverity.FATAL,
            category = LogCategory.MODS,
            confidence = 0.85f,
            advice = listOf(UPDATE_MODS, REMOVE_CONFLICTING_MODS),
            patterns = listOf(
                """Mixin apply failed""",
                """InvalidMixinException""",
                """MixinTransformerError""",
                """Mixin.*(apply|transform).*(failed|error)"""
            )
        ),
        rule(
            id = "mod_duplicate",
            severity = LogSeverity.ERROR,
            category = LogCategory.MODS,
            confidence = 0.8f,
            advice = listOf(REMOVE_CONFLICTING_MODS),
            patterns = listOf(
                """Duplicate mods""",
                """duplicate mod""",
                """two mods with the same id"""
            )
        ),
        rule(
            id = "java_version",
            severity = LogSeverity.FATAL,
            category = LogCategory.JAVA,
            confidence = 0.95f,
            advice = listOf(UPDATE_JAVA),
            patterns = listOf(
                """UnsupportedClassVersionError""",
                """compiled by a more recent version""",
                """class file version \d+"""
            )
        ),
        rule(
            id = "java_main_class",
            severity = LogSeverity.FATAL,
            category = LogCategory.JAVA,
            confidence = 0.75f,
            advice = listOf(VERIFY_GAME_FILES, UPDATE_JAVA),
            patterns = listOf(
                """Could not find or load main class""",
                """NoClassDefFoundError: (net/minecraft|net/minecraftforge|cpw/mods)""",
                """ClassNotFoundException: (net\.minecraft|net\.minecraftforge)"""
            )
        ),
        rule(
            id = "network_failure",
            severity = LogSeverity.ERROR,
            category = LogCategory.NETWORK,
            confidence = 0.7f,
            advice = listOf(CHECK_NETWORK),
            patterns = listOf(
                """UnknownHostException""",
                """SSLHandshakeException""",
                """ConnectException""",
                """SocketTimeoutException""",
                """Connection reset""",
                """Failed to download""",
                """Unable to access 'https?://"""
            )
        ),
        rule(
            id = "storage_failure",
            severity = LogSeverity.ERROR,
            category = LogCategory.STORAGE,
            confidence = 0.8f,
            advice = listOf(CHECK_STORAGE),
            patterns = listOf(
                """No space left on device""",
                """ENOSPC""",
                """AccessDeniedException""",
                """Permission denied""",
                """Read-only file system"""
            )
        ),
        rule(
            id = "account_failure",
            severity = LogSeverity.ERROR,
            category = LogCategory.ACCOUNT,
            confidence = 0.7f,
            advice = listOf(SIGN_IN_AGAIN),
            patterns = listOf(
                """AuthenticationException""",
                """Invalid credentials""",
                """Failed to (log ?in|authenticate)""",
                """401 Unauthorized""",
                """Invalid session"""
            )
        ),
        rule(
            id = "native_crash",
            severity = LogSeverity.FATAL,
            category = LogCategory.RENDERER,
            confidence = 0.7f,
            advice = listOf(SWITCH_RENDERER, LOWER_GRAPHICS),
            patterns = listOf(
                """SIGSEGV""",
                """SIGABRT""",
                """Fatal signal \d+""",
                """A/libc.*Fatal"""
            )
        ),
        rule(
            id = "crash_report",
            severity = LogSeverity.FATAL,
            category = LogCategory.OTHER,
            confidence = 0.6f,
            advice = listOf(READ_CRASH_REPORT),
            patterns = listOf(
                """The game crashed whilst""",
                """Description: .+"""
            )
        )
    )

    private val RULES_BY_ID: Map<String, LogRule> = RULES.associateBy { it.id }

    /**
     * Analyses [log], scanning at most [maxLines] lines.
     *
     * @return never `null`; an empty list of findings means "nothing recognizable".
     */
    fun analyze(log: String, maxLines: Int = DEFAULT_MAX_LINES): LogAnalysis {
        val limit = maxLines.coerceAtLeast(1)
        val evidenceByRule = LinkedHashMap<String, MutableList<LogEvidence>>()
        val rootCauses = LinkedHashSet<String>()
        var scannedLines = 0
        var truncated = false
        var mcVersion: String? = null
        var javaVersion: String? = null

        for ((index, rawLine) in log.lineSequence().withIndex()) {
            if (index >= limit) {
                truncated = true
                break
            }
            scannedLines++

            val line = rawLine.trim()
            if (line.isEmpty()) continue

            if (mcVersion == null) mcVersion = firstMatch(MC_VERSION, line)
            if (javaVersion == null) javaVersion = firstMatch(JAVA_VERSION, line)

            if (rootCauses.size < MAX_ROOT_CAUSES) {
                ROOT_CAUSE.find(line)?.groupValues?.getOrNull(1)
                    ?.trim()
                    ?.takeIf { it.isNotEmpty() }
                    ?.let { rootCauses += it.take(MAX_EVIDENCE_LENGTH) }
            }

            for (rule in RULES) {
                if (rule.patterns.none { it.containsMatchIn(line) }) continue
                val evidence = evidenceByRule.getOrPut(rule.id) { mutableListOf() }
                if (evidence.size < MAX_EVIDENCE) {
                    evidence += LogEvidence(
                        lineNumber = index + 1,
                        text = line.take(MAX_EVIDENCE_LENGTH)
                    )
                }
            }
        }

        val findings = evidenceByRule.mapNotNull { (ruleId, evidence) ->
            val rule = RULES_BY_ID[ruleId] ?: return@mapNotNull null
            LogFinding(
                ruleId = rule.id,
                severity = rule.severity,
                category = rule.category,
                confidence = rule.confidence,
                advice = rule.advice,
                evidence = evidence.toList()
            )
        }.sortedWith(
            compareByDescending<LogFinding> { it.severity.weight }
                .thenByDescending { it.confidence }
        )

        return LogAnalysis(
            findings = findings,
            rootCauses = rootCauses.toList(),
            mcVersion = mcVersion,
            javaVersion = javaVersion,
            scannedLines = scannedLines,
            truncated = truncated
        )
    }

    private fun firstMatch(patterns: List<Regex>, line: String): String? {
        for (pattern in patterns) {
            val match = pattern.find(line) ?: continue
            val value = match.groupValues.getOrNull(1)?.trim().orEmpty()
            if (value.isNotEmpty()) return value
        }
        return null
    }

    private fun rule(
        id: String,
        severity: LogSeverity,
        category: LogCategory,
        confidence: Float,
        advice: List<LogAdvice>,
        patterns: List<String>
    ) = LogRule(
        id = id,
        severity = severity,
        category = category,
        confidence = confidence,
        advice = advice,
        patterns = patterns.map { Regex(it, RegexOption.IGNORE_CASE) }
    )

    private val LogSeverity.weight: Int
        get() = when (this) {
            LogSeverity.FATAL -> 2
            LogSeverity.ERROR -> 1
            LogSeverity.WARNING -> 0
        }
}

/** A single pattern group of [LogAnalyzer]. */
private data class LogRule(
    val id: String,
    val severity: LogSeverity,
    val category: LogCategory,
    val confidence: Float,
    val advice: List<LogAdvice>,
    val patterns: List<Regex>
)
