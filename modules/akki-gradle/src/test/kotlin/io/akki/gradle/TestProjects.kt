package io.akki.gradle

import java.nio.file.Path
import org.gradle.testkit.runner.GradleRunner

internal const val REPOSITORY: String = "repository"

internal val GROUP: String = property("akki.maven.group")

internal val PLUGIN_ID: String = property("akki.plugin.id")

internal val VERSION: String = property("akki.version")

internal fun property(name: String): String = requireNotNull(System.getProperty(name)) { "missing -D$name" }

internal fun path(name: String): Path = Path.of(property(name))

internal fun publishRepository(projectDirectory: Path): Path = projectDirectory.resolve(REPOSITORY).also {
    path("akki.test.repository").toFile().copyRecursively(it.toFile())
}

internal fun gradleRunner(projectDirectory: Path, vararg arguments: String): GradleRunner =
    GradleRunner.create()
        .withProjectDir(projectDirectory.toFile())
        .withArguments(*arguments, "--stacktrace", "--configuration-cache", "--no-build-cache", "--console=plain")

internal object KotlinVersions {
    @JvmStatic
    fun tested(): List<String> = property("akki.kotlin.tested").split(',')

    @JvmStatic
    fun rejected(): List<String> = property("akki.kotlin.rejected").split(',')
}
