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

package com.movtery.zalithlauncher.game.performance

import com.movtery.zalithlauncher.game.renderer.RendererInterface
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RendererCompatibilityTest {

    private class FakeRenderer(
        private val id: String,
        private val name: String,
        private val minVersion: String? = null,
        private val maxVersion: String? = null
    ) : RendererInterface {
        override fun getRendererId(): String = id
        override fun getUniqueIdentifier(): String = "test-$id"
        override fun getRendererName(): String = name
        override fun getMinMCVersion(): String? = minVersion
        override fun getMaxMCVersion(): String? = maxVersion
        override fun getRendererEnv(): Lazy<Map<String, String>> = lazy { emptyMap() }
        override fun getDlopenLibrary(): Lazy<List<String>> = lazy { emptyList() }
        override fun getRendererLibrary(): String = "libtest.so"
    }

    private fun profile(
        is64Bit: Boolean = true,
        glEsMajor: Int = 3,
        glEsMinor: Int = 2,
        gpuFamily: GpuFamily = GpuFamily.ADRENO,
        androidSdk: Int = 33
    ) = HardwareProfile(
        supportedAbis = if (is64Bit) listOf("arm64-v8a") else listOf("armeabi-v7a"),
        is64Bit = is64Bit,
        cpuCores = 8,
        totalRamMb = 8192,
        freeRamMb = 4096,
        glEsMajor = glEsMajor,
        glEsMinor = glEsMinor,
        gpuFamily = gpuFamily,
        socModel = "SM8250",
        androidSdk = androidSdk,
        deviceName = "Test"
    )

    @Test
    fun `version outside renderer range is unsupported`() {
        val renderer = FakeRenderer("opengles2", "GL4ES", maxVersion = "1.21.4")
        val result = RendererCompatibility.assess(profile(), renderer, "1.21.5")

        assertEquals(RendererSupport.UNSUPPORTED, result.support)
        assertTrue(result.reasons.isNotEmpty())
    }

    @Test
    fun `version inside range is supported`() {
        val renderer = FakeRenderer("opengles2", "GL4ES", maxVersion = "1.21.4")
        val result = RendererCompatibility.assess(profile(), renderer, "1.21.1")

        assertEquals(RendererSupport.SUPPORTED, result.support)
    }

    @Test
    fun `zink is rejected on 32 bit devices`() {
        val renderer = FakeRenderer("opengles3_desktopgl_zink_kopper", "Kopper Zink")
        val result = RendererCompatibility.assess(profile(is64Bit = false), renderer, "1.21.1")

        assertEquals(RendererSupport.UNSUPPORTED, result.support)
        assertTrue(result.reasons.any { it.contains("64-bit") })
    }

    @Test
    fun `freedreno needs an adreno gpu`() {
        val renderer = FakeRenderer("gallium_freedreno", "Freedreno (Adreno)")
        val mali = RendererCompatibility.assess(profile(gpuFamily = GpuFamily.MALI), renderer)
        val adreno = RendererCompatibility.assess(profile(gpuFamily = GpuFamily.ADRENO), renderer)

        assertEquals(RendererSupport.UNSUPPORTED, mali.support)
        assertEquals(RendererSupport.SUPPORTED, adreno.support)
    }

    @Test
    fun `unknown gles version is reported as unknown`() {
        val renderer = FakeRenderer("opengles3", "Krypton Wrapper")
        val result = RendererCompatibility.assess(profile(glEsMajor = 0, glEsMinor = 0), renderer)

        assertEquals(RendererSupport.UNKNOWN, result.support)
    }

    @Test
    fun `gles 2 device cannot use gles 3 renderer`() {
        val renderer = FakeRenderer("opengles3", "Krypton Wrapper")
        val result = RendererCompatibility.assess(profile(glEsMajor = 2, glEsMinor = 0), renderer)

        assertEquals(RendererSupport.UNSUPPORTED, result.support)
    }

    @Test
    fun `ranking puts supported renderers first`() {
        val renderers = listOf(
            FakeRenderer("opengles3_desktopgl_zink_kopper", "Kopper Zink"),
            FakeRenderer("opengles2", "GL4ES"),
            FakeRenderer("gallium_freedreno", "Freedreno (Adreno)")
        )

        val ranked = RendererCompatibility.rank(profile(is64Bit = false), renderers)
        assertEquals(RendererSupport.UNSUPPORTED, ranked.last().support)
        assertTrue(ranked.first().support != RendererSupport.UNSUPPORTED)
    }
}
