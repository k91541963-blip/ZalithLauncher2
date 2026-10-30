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

/**
 * How confident we are that a renderer will work on this device and game version.
 *
 * [UNKNOWN] is a real answer: it means the launcher cannot tell from the data it has.
 * Callers must present it as "not verified" rather than as "supported".
 */
enum class RendererSupport {
    SUPPORTED,
    UNSUPPORTED,
    UNKNOWN
}

/**
 * @param reasons blocking facts, e.g. the game version is out of the renderer's range
 * @param warnings non blocking hints the user should know about
 */
data class RendererAssessment(
    val rendererId: String,
    val rendererName: String,
    val support: RendererSupport,
    val reasons: List<String>,
    val warnings: List<String>
) {
    val isUsable: Boolean
        get() = support != RendererSupport.UNSUPPORTED
}

/**
 * Checks renderer compatibility against a [HardwareProfile] and a Minecraft version.
 *
 * The rules below only encode facts the launcher can verify locally:
 *  - the version range declared by the renderer itself,
 *  - the minimum OpenGL ES level the renderer needs,
 *  - the GPU family a Gallium driver is written for,
 *  - 32-bit address space limits.
 *
 * Anything that would need a real driver probe is reported as [RendererSupport.UNKNOWN].
 */
object RendererCompatibility {

    private const val GLES_2 = 20
    private const val GLES_3 = 30

    fun assess(
        profile: HardwareProfile,
        renderer: RendererInterface,
        mcVersion: String? = null
    ): RendererAssessment {
        val reasons = mutableListOf<String>()
        val warnings = mutableListOf<String>()

        val min = renderer.getMinMCVersion()
        val max = renderer.getMaxMCVersion()
        if (!McVersion.isInRange(mcVersion, min, max)) {
            reasons += buildString {
                append("Bu renderer Minecraft ")
                append(mcVersion)
                append(" sürümünü desteklemiyor (desteklenen aralık: ")
                append(min ?: "-")
                append(" – ")
                append(max ?: "-")
                append(").")
            }
        }

        when (renderer.getRendererId()) {
            "opengles2" -> requireGles(profile, GLES_2, renderer, reasons, warnings)
            "opengles3" -> requireGles(profile, GLES_3, renderer, reasons, warnings)

            "opengles3_desktopgl_zink_kopper" -> {
                requireGles(profile, GLES_3, renderer, reasons, warnings)
                if (!profile.is64Bit) {
                    reasons += "Zink (Kopper) 64-bit süreç gerektirir; bu cihaz 32-bit."
                }
                if (profile.androidSdk < 29) {
                    warnings += "Vulkan sürücüsü Android 10 ve üzerinde daha kararlı çalışır."
                }
            }

            "gallium_freedreno" -> {
                if (profile.gpuFamily != GpuFamily.ADRENO) {
                    reasons += "Freedreno yalnızca Adreno GPU'larda çalışır."
                }
                if (profile.gpuFamily == GpuFamily.UNKNOWN) {
                    warnings += "GPU ailesi tespit edilemedi; Adreno olduğundan emin değilseniz seçmeyin."
                }
            }

            "gallium_panfrost" -> {
                if (profile.gpuFamily != GpuFamily.MALI) {
                    reasons += "Panfrost yalnızca Mali GPU'larda çalışır."
                }
                if (profile.gpuFamily == GpuFamily.UNKNOWN) {
                    warnings += "GPU ailesi tespit edilemedi; Mali olduğundan emin değilseniz seçmeyin."
                }
            }

            "gallium_virgl" -> {
                warnings += "VirGL ek bir virglrenderer ortamı gerektirir; performansı cihaza göre değişir."
            }
        }

        if (profile.glEsVersionCode == 0) {
            warnings += "OpenGL ES sürümü okunamadı; uyumluluk doğrulanamadı."
        }

        val support = when {
            reasons.isNotEmpty() -> RendererSupport.UNSUPPORTED
            profile.glEsVersionCode == 0 -> RendererSupport.UNKNOWN
            warnings.isNotEmpty() && renderer.getRendererId() == "gallium_virgl" -> RendererSupport.UNKNOWN
            else -> RendererSupport.SUPPORTED
        }

        return RendererAssessment(
            rendererId = renderer.getRendererId(),
            rendererName = renderer.getRendererName(),
            support = support,
            reasons = reasons,
            warnings = warnings
        )
    }

    /**
     * Assesses every renderer and returns the usable ones first, preserving the input order
     * inside each group so the launcher's own default order stays meaningful.
     */
    fun rank(
        profile: HardwareProfile,
        renderers: List<RendererInterface>,
        mcVersion: String? = null
    ): List<RendererAssessment> {
        return renderers
            .map { assess(profile, it, mcVersion) }
            .sortedBy { assessment ->
                when (assessment.support) {
                    RendererSupport.SUPPORTED -> 0
                    RendererSupport.UNKNOWN -> 1
                    RendererSupport.UNSUPPORTED -> 2
                }
            }
    }

    private fun requireGles(
        profile: HardwareProfile,
        required: Int,
        renderer: RendererInterface,
        reasons: MutableList<String>,
        warnings: MutableList<String>
    ) {
        val detected = profile.glEsVersionCode
        if (detected == 0) return // unknown, reported once by the caller
        if (detected < required) {
            reasons += "${renderer.getRendererName()} en az OpenGL ES " +
                "${required / 10}.${required % 10} gerektirir; cihazda ${profile.glEsMajor}.${profile.glEsMinor} var."
        } else if (detected == required) {
            warnings += "Cihaz tam sınırda: OpenGL ES ${profile.glEsMajor}.${profile.glEsMinor}."
        }
    }
}
