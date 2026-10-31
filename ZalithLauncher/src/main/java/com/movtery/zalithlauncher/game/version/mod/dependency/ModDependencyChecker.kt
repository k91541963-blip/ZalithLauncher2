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

package com.movtery.zalithlauncher.game.version.mod.dependency

/**
 * One dependency declared by a mod.
 *
 * @param id mod id, e.g. `fabric-api`
 * @param versionRanges accepted ranges, any of them is enough; empty means "any version"
 * @param required false for optional suggestions, which never produce an issue
 */
data class ModDependency(
    val id: String,
    val versionRanges: List<String> = emptyList(),
    val required: Boolean = true
)

/** Minimal view of an installed mod needed to resolve dependencies. */
data class ModManifest(
    val id: String,
    val name: String,
    val version: String?,
    val dependencies: List<ModDependency>
)

enum class DependencyProblem {
    /** The dependency is not installed at all. */
    MISSING,

    /** The dependency is installed but disabled. */
    DISABLED,

    /** The dependency is installed, but its version is outside the accepted range. */
    VERSION_MISMATCH
}

/**
 * A dependency that cannot be satisfied by the currently enabled mods.
 *
 * @param requiredRanges ranges declared by the mod, empty means any version
 * @param installedVersion version of the installed dependency, `null` when missing/disabled
 */
data class DependencyIssue(
    val modId: String,
    val modName: String,
    val dependencyId: String,
    val problem: DependencyProblem,
    val requiredRanges: List<String>,
    val installedVersion: String?
)

/**
 * Cross-checks declared mod dependencies against the installed mods.
 *
 * Only facts are reported: a dependency is missing, disabled, or its installed version is
 * outside the declared range. Loader provided ids (`minecraft`, `java`, `fabricloader`, …)
 * are ignored because the launcher does not install them as mods.
 */
object ModDependencyChecker {

    private val BUILT_IN_IDS = setOf(
        "minecraft",
        "java",
        "fabricloader",
        "fabric-loader",
        "quilt_loader",
        "quilt-loader",
        "forge",
        "neoforge"
    )

    /**
     * @param enabledMods manifests of enabled mods
     * @param disabledModIds ids of installed but disabled mods
     */
    fun check(
        enabledMods: List<ModManifest>,
        disabledModIds: Set<String> = emptySet()
    ): List<DependencyIssue> {
        val available = enabledMods.associateBy { it.id }
        val issues = mutableListOf<DependencyIssue>()

        for (mod in enabledMods) {
            for (dependency in mod.dependencies) {
                if (!dependency.required) continue
                if (dependency.id == mod.id) continue
                if (dependency.id in BUILT_IN_IDS) continue

                val installed = available[dependency.id]
                when {
                    installed != null -> {
                        if (!VersionRange.satisfiesAny(installed.version, dependency.versionRanges)) {
                            issues += DependencyIssue(
                                modId = mod.id,
                                modName = mod.name,
                                dependencyId = dependency.id,
                                problem = DependencyProblem.VERSION_MISMATCH,
                                requiredRanges = dependency.versionRanges,
                                installedVersion = installed.version
                            )
                        }
                    }

                    dependency.id in disabledModIds -> issues += DependencyIssue(
                        modId = mod.id,
                        modName = mod.name,
                        dependencyId = dependency.id,
                        problem = DependencyProblem.DISABLED,
                        requiredRanges = dependency.versionRanges,
                        installedVersion = null
                    )

                    else -> issues += DependencyIssue(
                        modId = mod.id,
                        modName = mod.name,
                        dependencyId = dependency.id,
                        problem = DependencyProblem.MISSING,
                        requiredRanges = dependency.versionRanges,
                        installedVersion = null
                    )
                }
            }
        }

        return issues
    }
}
