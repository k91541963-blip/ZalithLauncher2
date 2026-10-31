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

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LogAnalyzerTest {

    @Test
    fun `detects heap out of memory and points at the line`() {
        val log = """
            [12:00:00] [main/INFO]: Loading Minecraft 1.21.4 with Fabric Loader 0.16.9
            [12:00:05] [main/INFO]: OpenGL ES 3.2 detected
            [12:01:00] [Render thread/ERROR]: java.lang.OutOfMemoryError: Java heap space
        """.trimIndent()

        val analysis = LogAnalyzer.analyze(log)

        assertEquals("1.21.4", analysis.mcVersion)
        val finding = analysis.primary
        assertNotNull(finding)
        assertEquals("heap_oom", finding!!.ruleId)
        assertEquals(LogSeverity.FATAL, finding.severity)
        assertEquals(LogCategory.MEMORY, finding.category)
        assertTrue(finding.advice.contains(LogAdvice.INCREASE_MEMORY))
        assertEquals(1, finding.evidence.size)
        assertEquals(3, finding.evidence.first().lineNumber)
        assertTrue(finding.evidence.first().text.contains("OutOfMemoryError"))
    }

    @Test
    fun `too large heap asks for a smaller allocation`() {
        val log = "Error occurred during initialization of VM\n" +
            "Could not reserve enough space for 8388608KB object heap"

        val finding = LogAnalyzer.analyze(log).primary

        assertNotNull(finding)
        assertEquals("heap_too_large", finding!!.ruleId)
        assertEquals(listOf(LogAdvice.LOWER_MEMORY), finding.advice)
    }

    @Test
    fun `fatal findings are listed before errors`() {
        val log = """
            [12:00:00] [main/ERROR]: Connection reset while downloading asset
            [12:00:01] [main/FATAL]: Missing or unsupported mandatory dependencies:
        """.trimIndent()

        val findings = LogAnalyzer.analyze(log).findings

        assertEquals(2, findings.size)
        assertEquals(LogSeverity.FATAL, findings[0].severity)
        assertEquals("mod_missing_dependency", findings[0].ruleId)
        assertEquals("network_failure", findings[1].ruleId)
    }

    @Test
    fun `collects caused by chain in order`() {
        val log = """
            java.lang.RuntimeException: Unable to launch
            	at com.example.Launcher.start(Launcher.java:10)
            Caused by: java.lang.UnsupportedClassVersionError: net/minecraft/client/Main has been compiled by a more recent version
            	at java.base/java.lang.ClassLoader.defineClass(ClassLoader.java:1)
            Caused by: java.lang.IllegalStateException: driver lost
        """.trimIndent()

        val analysis = LogAnalyzer.analyze(log)

        assertEquals(2, analysis.rootCauses.size)
        assertTrue(analysis.rootCauses[0].startsWith("java.lang.UnsupportedClassVersionError"))
        assertTrue(analysis.rootCauses[1].contains("driver lost"))
        assertEquals("java_version", analysis.primary?.ruleId)
    }

    @Test
    fun `renderer failures map to switch renderer advice`() {
        val log = """
            [12:00:00] [Render thread/ERROR]: Couldn't create GL context
            [12:00:00] [Render thread/ERROR]: EGL_BAD_CONFIG: eglCreateContext failed
        """.trimIndent()

        val finding = LogAnalyzer.analyze(log).primary

        assertNotNull(finding)
        assertEquals("renderer_context", finding!!.ruleId)
        assertEquals(LogCategory.RENDERER, finding.category)
        assertTrue(finding.advice.contains(LogAdvice.SWITCH_RENDERER))
    }

    @Test
    fun `mod conflicts are recognised`() {
        val log = """
            [12:00:00] [main/ERROR]: Mixin apply failed mixins.json:example.mixins.json:MixinClass -> net.minecraft.class_310
            [12:00:01] [main/ERROR]: InvalidMixinException: mixin transformation failed
        """.trimIndent()

        val finding = LogAnalyzer.analyze(log).primary

        assertNotNull(finding)
        assertEquals("mod_mixin_failure", finding!!.ruleId)
        assertTrue(finding.advice.contains(LogAdvice.UPDATE_MODS))
    }

    @Test
    fun `evidence is capped so a spammed line cannot flood the result`() {
        val log = (1..20).joinToString("\n") { "UnknownHostException: auth.example.com" }

        val analysis = LogAnalyzer.analyze(log)

        assertEquals(1, analysis.findings.size)
        assertEquals(3, analysis.findings.first().evidence.size)
        assertEquals(20, analysis.scannedLines)
        assertFalse(analysis.truncated)
    }

    @Test
    fun `scan limit marks the analysis as truncated`() {
        val log = (1..50).joinToString("\n") { "line $it" }

        val analysis = LogAnalyzer.analyze(log, maxLines = 10)

        assertEquals(10, analysis.scannedLines)
        assertTrue(analysis.truncated)
        assertTrue(analysis.findings.isEmpty())
    }

    @Test
    fun `clean log produces no findings`() {
        val log = """
            [12:00:00] [main/INFO]: Loading Minecraft 1.20.1
            [12:00:01] [main/INFO]: Java version: 17.0.9
            [12:00:02] [main/INFO]: Sound engine started
        """.trimIndent()

        val analysis = LogAnalyzer.analyze(log)

        assertTrue(analysis.findings.isEmpty())
        assertNull(analysis.primary)
        assertFalse(analysis.hasBlockingIssue)
        assertEquals("1.20.1", analysis.mcVersion)
        assertEquals("17.0.9", analysis.javaVersion)
    }

    @Test
    fun `native crash and storage failure are reported`() {
        val log = """
            [12:00:00] [main/ERROR]: java.io.IOException: No space left on device
            [12:00:01] [Render thread/FATAL]: A/libc: Fatal signal 11 (SIGSEGV), code 1
        """.trimIndent()

        val findings = LogAnalyzer.analyze(log).findings

        assertEquals(2, findings.size)
        assertTrue(findings.any { it.ruleId == "storage_failure" })
        assertTrue(findings.any { it.ruleId == "native_crash" })
        assertTrue(findings.first().severity == LogSeverity.FATAL)
        assertTrue(LogAnalyzer.analyze(log).hasBlockingIssue)
    }

    @Test
    fun `empty input is safe`() {
        val analysis = LogAnalyzer.analyze("")

        assertTrue(analysis.findings.isEmpty())
        assertTrue(analysis.scannedLines <= 1)
        assertFalse(analysis.truncated)
    }
}
