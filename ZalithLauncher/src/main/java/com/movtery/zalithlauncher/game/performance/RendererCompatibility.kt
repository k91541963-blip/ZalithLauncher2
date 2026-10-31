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
 * Facts the local check can prove about a renderer on this device.
 *
 * Codes instead of text: every language renders them in the UI layer.
 */
enum class RendererIssue {
    MC_VERSION_OUT_OF_RANGE,
    GLES_TOO_LOW,
    GLES_AT_LIMIT,
    REQUIRES_64_BIT,
    VULKAN_NEEDS_NEWER_ANDROID,
    FREEDRENO_NEEDS_ADRENO,
    PANFROST_NEEDS_MALI,
    GPU_FAMILY_UNKNOWN,
    VIRGL_NEEDS_ENVIRONMENT,
    GLES_VERSION_UNKNOWN
}

/**
 * @param issues blocking facts, e.g. the game version is out of the renderer's range
 * @param warnings non blocking hints the user should know about
 */
data class RendererAssessment(
    val rendererId: String,
    val rendererName: String,
    val support: RendererSupport,
    val issues: List<RendererIssue>,
    val warnings: List<RendererIssue>
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
        val issues = mutableListOf<RendererIssue>()
        val warnings = mutableListOf<RendererIssue>()

        val min = renderer.getMinMCVersion()
        val max = renderer.getMaxMCVersion()
        if (!McVersion.isInRange(mcVersion, min, max)) {
            issues += RendererIssue.MC_VERSION_OUT_OF_RANGE
        }

        when (renderer.getRendererId()) {
            "opengles2" -> requireGles(profile, GLES_2, issues, warnings)
            "opengles3" -> requireGles(profile, GLES_3, issues, warnings)

            "opengles3_desktopgl_zink_kopper" -> {
                requireGles(profile, GLES_3, issues, warnings)
                if (!profile.is64Bit) {
                    issues += RendererIssue.REQUIRES_64_BIT
                }
                if (profile.androidSdk < 29) {
                    warnings += RendererIssue.VULKAN_NEEDS_NEWER_ANDROID
                }
            }

            "gallium_freedreno" -> {
                if (profile.gpuFamily != GpuFamily.ADRENO) {
                    issues += RendererIssue.FREEDRENO_NEEDS_ADRENO
                }
                if (profile.gpuFamily == GpuFamily.UNKNOWN) {
                    warnings += RendererIssue.GPU_FAMILY_UNKNOWN
                }
            }

            "gallium_panfrost" -> {
                if (profile.gpuFamily != GpuFamily.MALI) {
                    issues += RendererIssue.PANFROST_NEEDS_MALI
                }
                if (profile.gpuFamily == GpuFamily.UNKNOWN) {
                    warnings += RendererIssue.GPU_FAMILY_UNKNOWN
                }
            }

            "gallium_virgl" -> {
                warnings += RendererIssue.VIRGL_NEEDS_ENVIRONMENT
            }
        }

        if (profile.glEsVersionCode == 0) {
            warnings += RendererIssue.GLES_VERSION_UNKNOWN
        }

        val support = when {
            issues.isNotEmpty() -> RendererSupport.UNSUPPORTED
            profile.glEsVersionCode == 0 -> RendererSupport.UNKNOWN
            warnings.isNotEmpty() && renderer.getRendererId() == "gallium_virgl" -> RendererSupport.UNKNOWN
            else -> RendererSupport.SUPPORTED
        }

        return RendererAssessment(
            rendererId = renderer.getRendererId(),
            rendererName = renderer.getRendererName(),
            support = support,
            issues = issues,
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
        issues: MutableList<RendererIssue>,
        warnings: MutableList<RendererIssue>
    ) {
        val detected = profile.glEsVersionCode
        if (detected == 0) return // unknown, reported once by the caller
        if (detected < required) {
            issues += RendererIssue.GLES_TOO_LOW
        } else if (detected == required) {
            warnings += RendererIssue.GLES_AT_LIMIT
        }
    }
}
