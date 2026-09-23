package akki.buildlogic

import org.gradle.api.Project
import org.gradle.api.artifacts.MinimalExternalModuleDependency
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.provider.Provider
import org.gradle.kotlin.dsl.getByType

internal val Project.libs: VersionCatalog
    get() = extensions.getByType<VersionCatalogsExtension>().named("libs")

internal fun Project.library(alias: String): Provider<MinimalExternalModuleDependency> =
    libs.findLibrary(alias).orElseThrow { IllegalStateException("no library '$alias' in the version catalog") }

internal fun Project.version(alias: String): String =
    libs.findVersion(alias).orElseThrow { IllegalStateException("no version '$alias' in the version catalog") }
        .requiredVersion

internal val Project.jvmToolchainVersion: Int
    get() = version("jvm-toolchain").toInt()

internal val Project.jvmTargetVersion: Int
    get() = version("jvm-target").toInt()
