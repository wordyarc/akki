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
    fun `lowers logger calls in a consumer project`(@TempDir projectDirectory: Path) {
        val output = build(projectDirectory, enabled = null)
        assertTrue(output.contains("AKKI name=consumer.OrderService evaluated=0"), output)
    }

    @Test
    fun `leaves the consumer untouched when disabled`(@TempDir projectDirectory: Path) {
        val output = build(projectDirectory, enabled = false)
        assertTrue(output.contains("AKKI name=consumer.OrderService evaluated=1"), output)
    }

    @Test
    fun `logs through the slf4j backend it finds on the classpath`(@TempDir projectDirectory: Path) {
        val output = build(projectDirectory, enabled = null, consumer = Consumer.Slf4j)
        val line = lineOf(Consumer.Slf4j, """log.info("received""")

        assertTrue(
            output.contains("AKKI INFO consumer.OrderService [consumer.OrderService.handle:$line] - received A-1"),
            output,
        )
        assertTrue(output.contains("AKKI evaluated=0"), output)
    }

    private fun build(projectDirectory: Path, enabled: Boolean?, consumer: Consumer = Consumer.Core): String {
        val repository = publishRepository(projectDirectory.resolve("repository"))
        projectDirectory.resolve("settings.gradle.kts").writeText(settings(repository))
        projectDirectory.resolve("build.gradle.kts").writeText(buildScript(repository, enabled, consumer))
        projectDirectory.resolve("src/main/kotlin/consumer").createDirectories()
        projectDirectory.resolve("src/main/kotlin/consumer/${consumer.source}")
            .writeText(fixture("consumer/${consumer.source}"))
        consumer.resource?.let {
            projectDirectory.resolve("src/main/resources").createDirectories().resolve(it)
                .writeText(fixture("consumer/$it"))
        }

        return GradleRunner.create()
            .withProjectDir(projectDirectory.toFile())
            .withArguments("run", "--stacktrace", "--configuration-cache")
            .build()
            .output
    }

    private fun publishRepository(repository: Path): Path {
        publish(repository, "dev.ashenarx", "akki-compiler", path("akki.compiler.plugin.jar"))
        publish(
            repository,
            "dev.ashenarx",
            "akki-core",
            path("akki.core.jar"),
            dependencies = listOf(
                Triple("org.jetbrains.kotlin", "kotlin-metadata-jvm", property("akki.kotlin.version")),
            ),
        )
        publish(
            repository,
            "dev.ashenarx",
            "akki-slf4j",
            path("akki.slf4j.jar"),
            dependencies = listOf(
                Triple("dev.ashenarx", "akki-core", VERSION),
                Triple("org.slf4j", "slf4j-api", property("akki.slf4j.version")),
            ),
        )
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

    private fun buildScript(repository: Path, enabled: Boolean?, consumer: Consumer): String =
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
        ${dependencies(consumer)}
        }

        ${enabled?.let { "akki { enabled = $it }" } ?: ""}

        application {
            mainClass.set("${consumer.mainClass}")
        }
        """.trimIndent()

    private fun dependencies(consumer: Consumer): String = when (consumer) {
        Consumer.Core -> listOf("""implementation("dev.ashenarx:akki-core:$VERSION")""")
        Consumer.Slf4j -> listOf(
            """implementation("dev.ashenarx:akki-slf4j:$VERSION")""",
            """runtimeOnly("ch.qos.logback:logback-classic:${property("akki.logback.version")}")""",
        )
    }.joinToString("\n") { "    $it" }

    private fun lineOf(consumer: Consumer, marker: String): Int =
        fixture("consumer/${consumer.source}").lines().indexOfFirst { it.contains(marker) } + 1

    private fun path(name: String): Path = Path.of(property(name))

    private fun property(name: String): String = requireNotNull(System.getProperty(name)) { "missing -D$name" }

    private fun fixture(name: String): String =
        requireNotNull(javaClass.getResource("/$name")) { "no fixture /$name" }.readText()

    private enum class Consumer(val source: String, val mainClass: String, val resource: String?) {
        Core("Main.kt", "consumer.MainKt", null),
        Slf4j("Slf4jMain.kt", "consumer.Slf4jMainKt", "logback.xml"),
    }

    private companion object {
        val VERSION: String = requireNotNull(System.getProperty("akki.version"))
    }
}
