package akki.buildlogic

import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.getByType

internal val Project.jvmToolchainVersion: Int
    get() = extensions
        .getByType<VersionCatalogsExtension>()
        .named("libs")
        .findVersion("jvm-toolchain")
        .get()
        .requiredVersion
        .toInt()

internal val Project.jvmTargetVersion: Int
    get() = extensions
        .getByType<VersionCatalogsExtension>()
        .named("libs")
        .findVersion("jvm-target")
        .get()
        .requiredVersion
        .toInt()
