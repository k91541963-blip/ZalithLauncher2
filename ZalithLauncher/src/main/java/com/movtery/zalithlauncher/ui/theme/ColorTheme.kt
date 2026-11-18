/*
 * Zalith Launcher 2
 * Copyright (C) 2025-2026 MovTery <movtery228@qq.com> and contributors
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

package com.movtery.zalithlauncher.ui.theme

import androidx.compose.ui.graphics.Color

data class ColorTheme(
    val embermire: Color,
    val velvetRose: Color,
    val mistwave: Color,
    val glacier: Color,
    val verdantField: Color,
    val urbanAsh: Color,
    val verdantDawn: Color
)

enum class ColorThemeType {
    DYNAMIC,
    EMBERMIRE,
    VELVET_ROSE,
    MISTWAVE,
    GLACIER,
    VERDANTFIELD,
    URBAN_ASH,
    VERDANT_DAWN,
    AETHER_BLUE,
    PURPLE_NEBULA,
    EMERALD,
    CRIMSON,
    MIDNIGHT,
    AMOLED_BLACK,
    // AetherUI 3.0 "Titan" modes
    TITAN_DARK,
    CYBER_PURPLE,
    ARCTIC_BLUE,
    MINIMAL,
    CUSTOM
}

/**
 * Seed colours for the Aether 2.0 palettes.
 *
 * They are seeds for `dynamicColorScheme`, not the final surface colours. AMOLED uses
 * a teal accent and then overrides every surface to true black in [Theme.kt].
 */
object AetherThemeColors {
    val AETHER_BLUE = 0xFF2F81F7.toInt()
    val PURPLE_NEBULA = 0xFF8B5CF6.toInt()
    val EMERALD = 0xFF10B981.toInt()
    val CRIMSON = 0xFFE11D48.toInt()
    val MIDNIGHT = 0xFF3B5BDB.toInt()
    val AMOLED_BLACK = 0xFF00C2A8.toInt()

    // AetherUI 3.0 (Titan update). Titan Dark pairs gunmetal surfaces with amber;
    // Cyber Purple is a neon violet; Arctic Blue is ice cyan; Minimal is neutral grey.
    val TITAN_DARK = 0xFFC9A227.toInt()
    val CYBER_PURPLE = 0xFFB026FF.toInt()
    val ARCTIC_BLUE = 0xFF22D3EE.toInt()
    val MINIMAL = 0xFF8A8F98.toInt()

    /**
     * Seed colour for an Aether palette, or `null` when the type is not an Aether palette.
     * Kept as a pure Int function so it can be unit tested without a Compose runtime.
     */
    fun seedOf(type: ColorThemeType): Int? = when (type) {
        ColorThemeType.AETHER_BLUE -> AETHER_BLUE
        ColorThemeType.PURPLE_NEBULA -> PURPLE_NEBULA
        ColorThemeType.EMERALD -> EMERALD
        ColorThemeType.CRIMSON -> CRIMSON
        ColorThemeType.MIDNIGHT -> MIDNIGHT
        ColorThemeType.AMOLED_BLACK -> AMOLED_BLACK
        ColorThemeType.TITAN_DARK -> TITAN_DARK
        ColorThemeType.CYBER_PURPLE -> CYBER_PURPLE
        ColorThemeType.ARCTIC_BLUE -> ARCTIC_BLUE
        ColorThemeType.MINIMAL -> MINIMAL
        else -> null
    }
}