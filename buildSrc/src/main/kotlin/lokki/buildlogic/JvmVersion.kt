package lokki.buildlogic

import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.getByType

internal val Project.jvmVersion: Int
    get() = extensions
        .getByType<VersionCatalogsExtension>()
        .named("libs")
        .findVersion("jvm")
        .get()
        .requiredVersion
        .toInt()
