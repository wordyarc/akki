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
    fun lowersLoggerCallsInConsumerProject(@TempDir projectDirectory: Path) {
        val output = build(projectDirectory, enabled = null)
        assertTrue(output.contains("AKKI evaluated=0"), output)
    }

    @Test
    fun leavesConsumerUntouchedWhenDisabled(@TempDir projectDirectory: Path) {
        val output = build(projectDirectory, enabled = false)
        assertTrue(output.contains("AKKI evaluated=1"), output)
    }

    private fun build(projectDirectory: Path, enabled: Boolean?): String {
        val repository = publishRepository(projectDirectory.resolve("repository"))
        projectDirectory.resolve("settings.gradle.kts").writeText(settings(repository))
        projectDirectory.resolve("build.gradle.kts").writeText(buildScript(repository, enabled))
        projectDirectory.resolve("src/main/kotlin/consumer").createDirectories()
        projectDirectory.resolve("src/main/kotlin/consumer/Main.kt").writeText(SOURCE)

        return GradleRunner.create()
            .withProjectDir(projectDirectory.toFile())
            .withArguments("run", "--stacktrace", "--configuration-cache")
            .build()
            .output
    }

    private fun publishRepository(repository: Path): Path {
        publish(repository, "dev.ashenarx", "akki-compiler", path("akki.compiler.plugin.jar"))
        publish(repository, "dev.ashenarx", "akki-core", path("akki.core.jar"))
        publish(
            repository,
            "dev.ashenarx",
            "akki-gradle",
            path("akki.gradle.plugin.jar"),
            dependencies = listOf(
                Triple("org.jetbrains.kotlin", "kotlin-gradle-plugin-api", property("akki.kotlin.version")),
            ),
        )
        publish(
            repository,
            "dev.ashenarx.akki",
            "dev.ashenarx.akki.gradle.plugin",
            jar = null,
            dependencies = listOf(Triple("dev.ashenarx", "akki-gradle", VERSION)),
        )
        return repository
    }

    private fun publish(
        repository: Path,
        group: String,
        artifact: String,
        jar: Path?,
        dependencies: List<Triple<String, String, String>> = emptyList(),
    ) {
        val module = repository.resolve("${group.replace('.', '/')}/$artifact/$VERSION").createDirectories()
        jar?.let { Files.copy(it, module.resolve("$artifact-$VERSION.jar")) }
        val declarations = dependencies.joinToString("\n") { (dependencyGroup, dependencyName, version) ->
            """
            |    <dependency>
            |      <groupId>$dependencyGroup</groupId>
            |      <artifactId>$dependencyName</artifactId>
            |      <version>$version</version>
            |    </dependency>
            """.trimMargin()
        }
        module.resolve("$artifact-$VERSION.pom").writeText(
            """
            <project xmlns="http://maven.apache.org/POM/4.0.0">
              <modelVersion>4.0.0</modelVersion>
              <groupId>$group</groupId>
              <artifactId>$artifact</artifactId>
              <version>$VERSION</version>
              ${if (jar == null) "<packaging>pom</packaging>" else ""}
              <dependencies>
            $declarations
              </dependencies>
            </project>
            """.trimIndent()
        )
    }

    private fun settings(repository: Path): String =
        """
        pluginManagement {
            repositories {
                maven { url = uri("${repository.toUri()}") }
                mavenCentral()
            }
        }

        rootProject.name = "consumer"
        """.trimIndent()

    private fun buildScript(repository: Path, enabled: Boolean?): String =
        """
        plugins {
            application
            kotlin("jvm") version "${property("akki.kotlin.version")}"
            id("dev.ashenarx.akki") version "$VERSION"
        }

        repositories {
            mavenCentral()
            maven { url = uri("${repository.toUri()}") }
        }

        dependencies {
            implementation("dev.ashenarx:akki-core:$VERSION")
        }

        ${enabled?.let { "akki { enabled = $it }" } ?: ""}

        application {
            mainClass.set("consumer.MainKt")
        }
        """.trimIndent()

    private fun path(name: String): Path = Path.of(property(name))

    private fun property(name: String): String = requireNotNull(System.getProperty(name)) { "missing -D$name" }

    private companion object {
        val VERSION: String = requireNotNull(System.getProperty("akki.version"))

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
                println("AKKI evaluated=${'$'}evaluated")
            }
            """.trimIndent()
    }
}
