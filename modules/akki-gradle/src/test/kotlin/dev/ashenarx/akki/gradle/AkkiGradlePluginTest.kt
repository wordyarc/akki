package dev.ashenarx.akki.gradle

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertTrue
import org.gradle.testkit.runner.GradleRunner
import org.junit.jupiter.api.io.TempDir

class AkkiGradlePluginTest {
    @Test
    fun attachesCompilerPlugin(@TempDir projectDirectory: Path): Unit {
        val version = requireNotNull(AkkiGradlePlugin().getPluginArtifact().version)
        val repository = projectDirectory.resolve("repository")
        publish(repository, "akki-compiler", version, requiredPath("akki.compiler.plugin.jar"))
        publish(repository, "akki-core", version, requiredPath("akki.core.jar"))
        publish(
            repository,
            "akki-gradle",
            version,
            requiredPath("akki.gradle.plugin.jar"),
            """
              <dependencies>
                <dependency>
                  <groupId>org.jetbrains.kotlin</groupId>
                  <artifactId>kotlin-gradle-plugin-api</artifactId>
                  <version>2.4.10</version>
                </dependency>
              </dependencies>
            """.trimIndent(),
        )
        publishPluginMarker(repository, version)

        projectDirectory.resolve("settings.gradle.kts").writeText(
            """
            pluginManagement {
                repositories {
                    maven { url = uri("${repository.toUri()}") }
                    gradlePluginPortal()
                    mavenCentral()
                }
            }

            rootProject.name = "consumer"
            """.trimIndent()
        )
        projectDirectory.resolve("build.gradle.kts").writeText(
            """
            plugins {
                application
                kotlin("jvm") version "2.4.10"
                id("dev.ashenarx.akki") version "$version"
            }

            repositories {
                mavenCentral()
                maven { url = uri("${repository.toUri()}") }
            }

            dependencies {
                implementation("dev.ashenarx:akki-core:$version")
            }

            application {
                mainClass.set("consumer.MainKt")
            }
            """.trimIndent()
        )
        projectDirectory.resolve("src/main/kotlin/consumer").createDirectories()
        projectDirectory.resolve("src/main/kotlin/consumer/Main.kt").writeText(SOURCE)

        val result = GradleRunner.create()
            .withProjectDir(projectDirectory.toFile())
            .withArguments("run", "--stacktrace", "--configuration-cache")
            .build()

        assertTrue(result.output.contains("AKKI_OK"), result.output)
    }

    private fun publish(
        repository: Path,
        artifact: String,
        version: String,
        jar: Path,
        pomContent: String = "",
    ): Unit {
        val module = repository.resolve("dev/ashenarx/$artifact/$version").createDirectories()
        Files.copy(jar, module.resolve("$artifact-$version.jar"))
        module.resolve("$artifact-$version.pom").writeText(
            """
            <project xmlns="http://maven.apache.org/POM/4.0.0">
              <modelVersion>4.0.0</modelVersion>
              <groupId>dev.ashenarx</groupId>
              <artifactId>$artifact</artifactId>
              <version>$version</version>
              $pomContent
            </project>
            """.trimIndent()
        )
    }

    private fun publishPluginMarker(repository: Path, version: String): Unit {
        val artifact = "dev.ashenarx.akki.gradle.plugin"
        val module = repository.resolve("dev/ashenarx/akki/$artifact/$version").createDirectories()
        module.resolve("$artifact-$version.pom").writeText(
            """
            <project xmlns="http://maven.apache.org/POM/4.0.0">
              <modelVersion>4.0.0</modelVersion>
              <groupId>dev.ashenarx.akki</groupId>
              <artifactId>$artifact</artifactId>
              <version>$version</version>
              <packaging>pom</packaging>
              <dependencies>
                <dependency>
                  <groupId>dev.ashenarx</groupId>
                  <artifactId>akki-gradle</artifactId>
                  <version>$version</version>
                </dependency>
              </dependencies>
            </project>
            """.trimIndent()
        )
    }

    private fun requiredPath(name: String): Path = Path.of(requireNotNull(System.getProperty(name)))

    private companion object {
        val SOURCE: String =
            """
            package consumer

            import dev.ashenarx.akki.DelicateAkkiApi
            import dev.ashenarx.akki.Level
            import dev.ashenarx.akki.Log
            import dev.ashenarx.akki.LogBackend
            import dev.ashenarx.akki.Sink

            @OptIn(DelicateAkkiApi::class)
            fun main() {
                var evaluated = 0
                val backend = LogBackend { _, level ->
                    if (level == Level.DEBUG) null else Sink { _, _, _ -> }
                }
                Log.install(backend).use {
                    Log.named("consumer").debug("value=${'$'}{++evaluated}")
                }
                check(evaluated == 0)
                println("AKKI_OK")
            }
            """.trimIndent()
    }
}
