package io.akki.gradle

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.io.TempDir

class AkkiGradlePluginTest {
    @Test
    fun `lowers logger calls in a consumer project`(@TempDir projectDirectory: Path) {
        val output = build(projectDirectory)
        assertContains(output, "AKKI name=consumer.OrderService eager=1 lazy=0")
    }

    @Test
    fun `logs through the slf4j backend it finds on the classpath`(@TempDir projectDirectory: Path) {
        val output = build(projectDirectory, consumer = Consumer.Slf4j)
        val line = lineOf(Consumer.Slf4j, """log.info("received""")

        assertTrue(
            output.contains("AKKI INFO consumer.OrderService [consumer.OrderService.handle:$line] - received A-1"),
            output,
        )
        assertTrue(output.contains("AKKI evaluated=0"), output)
    }

    @Test
    fun `resolves the published slf4j dependencies from Maven POMs`(@TempDir projectDirectory: Path) {
        prepareConsumer(projectDirectory, Consumer.Slf4j, pomOnly = true)

        val output = runConsumer(projectDirectory).output
        val line = lineOf(Consumer.Slf4j, """log.info("received""")

        assertContains(output, "AKKI INFO consumer.OrderService [consumer.OrderService.handle:$line] - received A-1")
        assertContains(output, "AKKI evaluated=0")
    }

    @Test
    fun `logs through slf4j-simple in an isolated consumer`(@TempDir projectDirectory: Path) {
        prepareConsumer(
            projectDirectory,
            Consumer.SimpleSlf4j,
            extra = """
                application {
                    applicationDefaultJvmArgs = listOf(
                        "-Dorg.slf4j.simpleLogger.defaultLogLevel=" + providers.gradleProperty("simpleLevel").get(),
                    )
                }
            """.trimIndent(),
        )
        val verbose: String = runConsumer(projectDirectory, "-PsimpleLevel=trace").output
        assertContains(verbose, "TRACE consumer.OrderService - trace message")
        assertContains(verbose, "DEBUG consumer.OrderService - debug message")
        assertContains(verbose, "INFO consumer.OrderService - order=A-1 tenant=null received A-1")
        assertContains(verbose, "WARN consumer.OrderService - warn message")
        assertContains(verbose, "ERROR consumer.OrderService - failed")
        assertContains(verbose, "java.lang.IllegalStateException: boom")
        assertContains(verbose, "AKKI evaluated=2")
        assertFalse(verbose.contains("multiple SLF4J providers"), verbose)

        val quiet: String = runConsumer(projectDirectory, "-PsimpleLevel=info").output
        assertContains(quiet, "INFO consumer.OrderService - order=A-1 tenant=null received A-1")
        assertContains(quiet, "AKKI evaluated=0")
        assertFalse(quiet.contains("trace message"), quiet)
        assertFalse(quiet.contains("debug message"), quiet)
    }

    @Test
    fun `reads the logger name style at JVM startup before main`(@TempDir projectDirectory: Path) {
        prepareBootstrapConsumer(projectDirectory)

        val source = runConsumer(projectDirectory, "-PloggerNameStyle=source").output
        assertContains(source, "AKKI entered main")
        assertContains(source, "AKKI name=consumer.Owner.Nested")

        val jvm = runConsumer(projectDirectory, "-PloggerNameStyle=jvm-class").output
        assertContains(jvm, "AKKI entered main")
        assertContains(jvm, "AKKI name=consumer.Owner\$Nested")
    }

    @Test
    fun `reports an invalid startup style through the initializer cause`(@TempDir projectDirectory: Path) {
        prepareBootstrapConsumer(projectDirectory)

        val output: String = consumerRunner(projectDirectory, "-PloggerNameStyle=invalid").buildAndFail().output
        assertContains(output, "java.lang.ExceptionInInitializerError")
        assertContains(output, "Caused by: java.lang.IllegalStateException: akki:")
        assertContains(output, "io.akki.loggerNameStyle")
        assertContains(output, "invalid")
        assertContains(output, "source")
        assertContains(output, "jvm-class")
        assertFalse(output.contains("AKKI entered main"), output)
    }

    @Test
    fun `survives an slf4j-api downgraded below the version it was built for`(@TempDir projectDirectory: Path) {
        val output = build(
            projectDirectory,
            consumer = Consumer.Slf4j,
            extra = """configurations.all { resolutionStrategy.force("org.slf4j:slf4j-api:1.7.36") }""",
        )

        assertContains(output, "AKKI evaluated=0")
        assertContains(output, "akki: the backend failed to resolve logger 'consumer.OrderService'")
        assertContains(output, "java.lang.NoSuchMethodError")
        assertFalse(output.contains("AKKI INFO consumer.OrderService"), output)
    }

    @Test
    fun `runs on the module path and binds the backend as a service`(@TempDir projectDirectory: Path) {
        val output = build(projectDirectory, consumer = Consumer.Modular)
        val line = lineOf(Consumer.Modular, """log.info("received""")

        assertTrue(
            output.contains("AKKI INFO consumer.OrderService [consumer.OrderService.handle:$line] - received A-1"),
            output,
        )
        assertContains(output, "AKKI modules=consumer,io.akki.core slf4j=true")
    }

    @Test
    fun `keeps every level until a threshold is configured`(@TempDir projectDirectory: Path) {
        val output = build(projectDirectory, consumer = Consumer.Clipped)

        assertContains(output, "AKKI records=debug A-1,info A-1")
        assertFalse(output.contains("akki: minLevel"), output)
    }

    @Test
    fun `removes records below the configured threshold`(@TempDir projectDirectory: Path) {
        val output = build(
            projectDirectory,
            consumer = Consumer.Clipped,
            extra = """
                akki {
                    minLevel = io.akki.gradle.MinLevel.INFO
                }
            """.trimIndent(),
        )

        assertContains(output, "AKKI records=info A-1")
        assertContains(output, "akki: minLevel=info, records below it are removed from the bytecode")
    }

    @Test
    fun `updates generated logger fields incrementally with the configuration cache`(@TempDir projectDirectory: Path) {
        prepareConsumer(projectDirectory, Consumer.Incremental)
        val service = projectDirectory.resolve("src/main/kotlin/consumer/ChangingService.kt")
        val original = fixture("consumer/ChangingService.kt")
        service.writeText(original)

        val first = runConsumer(projectDirectory)
        assertEquals(TaskOutcome.SUCCESS, first.task(":compileKotlin")?.outcome, first.output)
        assertContains(first.output, "AKKI owner=before audit=audit.before retained=consumer.Unchanged")
        val unchanged = projectDirectory.resolve("build/classes/kotlin/main/consumer/Unchanged.class")
        val written = Files.getLastModifiedTime(unchanged)

        val repeated = runConsumer(projectDirectory)
        assertEquals(TaskOutcome.UP_TO_DATE, repeated.task(":compileKotlin")?.outcome, repeated.output)
        assertContains(repeated.output, "Reusing configuration cache.")
        assertContains(repeated.output, "AKKI owner=before audit=audit.before retained=consumer.Unchanged")

        service.writeText(original.replace("\"before\"", "log.name").replace("audit.before", "audit.after"))
        val added = runConsumer(projectDirectory)
        assertEquals(TaskOutcome.SUCCESS, added.task(":compileKotlin")?.outcome, added.output)
        assertContains(added.output, "Reusing configuration cache.")
        assertContains(added.output, "AKKI owner=consumer.ChangingService audit=audit.after retained=consumer.Unchanged")
        assertEquals(written, Files.getLastModifiedTime(unchanged), "an unchanged source was recompiled")

        service.writeText(original.replace("\"before\"", "\"after\"").replace("audit.before", "audit.final"))
        val removed = runConsumer(projectDirectory)
        assertEquals(TaskOutcome.SUCCESS, removed.task(":compileKotlin")?.outcome, removed.output)
        assertContains(removed.output, "AKKI owner=after audit=audit.final retained=consumer.Unchanged")
        assertEquals(written, Files.getLastModifiedTime(unchanged), "an unchanged source was recompiled")
    }

    @Test
    fun `recompiles when minLevel changes and reuses unchanged compilations`(@TempDir projectDirectory: Path) {
        prepareConsumer(
            projectDirectory,
            Consumer.Clipped,
            extra = """
                akki {
                    minLevel.set(providers.gradleProperty("testMinLevel").map { io.akki.gradle.MinLevel.valueOf(it) })
                }
            """.trimIndent(),
        )

        val full = runConsumer(projectDirectory, "-PtestMinLevel=TRACE")
        assertEquals(TaskOutcome.SUCCESS, full.task(":compileKotlin")?.outcome, full.output)
        assertContains(full.output, "AKKI records=debug A-1,info A-1")

        val clipped = runConsumer(projectDirectory, "-PtestMinLevel=INFO")
        assertEquals(TaskOutcome.SUCCESS, clipped.task(":compileKotlin")?.outcome, clipped.output)
        assertContains(clipped.output, "akki: minLevel=info")
        assertContains(clipped.output, "AKKI records=info A-1")

        val repeated = runConsumer(projectDirectory, "-PtestMinLevel=INFO")
        assertEquals(TaskOutcome.UP_TO_DATE, repeated.task(":compileKotlin")?.outcome, repeated.output)
        assertContains(repeated.output, "Reusing configuration cache.")
        assertContains(repeated.output, "AKKI records=info A-1")

        val restored = runConsumer(projectDirectory, "-PtestMinLevel=TRACE")
        assertEquals(TaskOutcome.SUCCESS, restored.task(":compileKotlin")?.outcome, restored.output)
        assertContains(restored.output, "AKKI records=debug A-1,info A-1")
        assertFalse(restored.output.contains("akki: minLevel"), restored.output)
    }

    @Test
    fun `rejects a Kotlin release it was not built for`(@TempDir projectDirectory: Path) {
        val expected = property("akki.kotlin.version").minor()
        require(OTHER_KOTLIN.minor() != expected)
        val repository = publishRepository(projectDirectory.resolve("repository"))
        projectDirectory.resolve("settings.gradle.kts").writeText(settings(repository))
        projectDirectory.resolve("build.gradle.kts")
            .writeText(buildScript(repository, Consumer.Bare, kotlinVersion = OTHER_KOTLIN))

        val output = GradleRunner.create()
            .withProjectDir(projectDirectory.toFile())
            .withArguments("help", "--console=plain")
            .buildAndFail()
            .output

        assertContains(output, "akki $VERSION is built for Kotlin $expected and cannot run with Kotlin $OTHER_KOTLIN")
    }

    @Test
    fun `applies only to JVM compilations`(@TempDir projectDirectory: Path) {
        val repository = publishRepository(projectDirectory.resolve("repository"))
        projectDirectory.resolve("settings.gradle.kts").writeText(settings(repository))
        projectDirectory.resolve("build.gradle.kts").writeText(
            """
            plugins {
                kotlin("multiplatform") version "${property("akki.kotlin.version")}"
                id("io.akki") version "$VERSION"
            }

            repositories {
                mavenCentral()
                maven { url = uri("${repository.toUri()}") }
            }

            kotlin {
                jvm()
                js { nodejs() }
            }

            tasks.register("akkiDependencies") {
                doLast {
                    for (name in listOf("jvmCompileClasspath", "jsCompileClasspath")) {
                        val akki = configurations.getByName(name).allDependencies.filter { it.group == "io.akki" }
                        println("AKKI " + name + " " + akki.map { it.name })
                    }
                }
            }
            """.trimIndent()
        )
        projectDirectory.resolve("src/commonMain/kotlin/consumer").createDirectories()
            .resolve("Shared.kt").writeText("package consumer\n\nfun shared(): String = \"shared\"\n")
        projectDirectory.resolve("src/jvmMain/kotlin/consumer").createDirectories()
            .resolve("BareMain.kt").writeText(fixture("consumer/BareMain.kt"))

        val output = GradleRunner.create()
            .withProjectDir(projectDirectory.toFile())
            .withArguments("akkiDependencies", "compileKotlinJvm", "compileKotlinJs", "--console=plain")
            .build()
            .output

        assertContains(output, "AKKI jvmCompileClasspath [akki-core]")
        assertContains(output, "AKKI jsCompileClasspath []")
    }

    @Test
    fun `brings its own runtime to a consumer that declares none`(@TempDir projectDirectory: Path) {
        val output = build(projectDirectory, consumer = Consumer.Bare)

        assertTrue(output.contains("INFO  consumer.OrderService - received A-1"), output)
    }

    @Test
    fun `consumes published core and test APIs from common source sets`(@TempDir projectDirectory: Path) {
        val repository = publishRepository(projectDirectory.resolve("repository"))
        projectDirectory.resolve("settings.gradle.kts").writeText(settings(repository))
        projectDirectory.resolve("build.gradle.kts").writeText(
            """
            plugins {
                kotlin("multiplatform") version "${property("akki.kotlin.version")}"
                id("io.akki") version "$VERSION"
            }

            repositories {
                mavenCentral()
                maven { url = uri("${repository.toUri()}") }
            }

            kotlin {
                jvm()
                sourceSets {
                    commonMain.dependencies {
                        implementation("io.akki:akki-core:$VERSION")
                    }
                    commonTest.dependencies {
                        implementation(kotlin("test"))
                        implementation("io.akki:akki-test:$VERSION")
                    }
                }
            }
            """.trimIndent()
        )
        projectDirectory.resolve("src/commonMain/kotlin/consumer").createDirectories()
            .resolve("SharedService.kt").writeText(fixture("consumer/SharedService.kt"))
        projectDirectory.resolve("src/commonTest/kotlin/consumer").createDirectories()
            .resolve("SharedServiceTest.kt").writeText(fixture("consumer/SharedServiceTest.kt"))

        val result = GradleRunner.create()
            .withProjectDir(projectDirectory.toFile())
            .withArguments("jvmTest", "--stacktrace", "--configuration-cache", "--console=plain")
            .build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":jvmTest")?.outcome, result.output)
    }

    @Test
    fun `locks core version against a newer direct dependency`(@TempDir projectDirectory: Path) {
        checkCoreVersionLock(projectDirectory, transitive = false)
    }

    @Test
    fun `locks core version against a newer transitive dependency`(@TempDir projectDirectory: Path) {
        checkCoreVersionLock(projectDirectory, transitive = true)
    }

    private fun checkCoreVersionLock(projectDirectory: Path, transitive: Boolean) {
        val repository = publishRepository(projectDirectory.resolve("repository"))
        val newerVersion = "999.0.0"
        publish(repository, "io.akki", "akki-core", path("akki.core.jar"), version = newerVersion)
        publish(
            repository,
            "consumer",
            "library",
            path("akki.slf4j.jar"),
            dependencies = listOf(Triple("io.akki", "akki-core", newerVersion)),
        )
        val dependency = if (transitive) "consumer:library:$VERSION" else "io.akki:akki-core:$newerVersion"
        projectDirectory.resolve("settings.gradle.kts").writeText(settings(repository))
        projectDirectory.resolve("build.gradle.kts").writeText(
            buildScript(repository, consumer = Consumer.Bare) + "\n" +
                """
                dependencies {
                    implementation("$dependency")
                }

                tasks.register("resolveCore") {
                    doLast {
                        for (name in listOf("compileClasspath", "runtimeClasspath")) {
                            val core = configurations.getByName(name).resolvedConfiguration.resolvedArtifacts
                                .single { it.moduleVersion.id.group == "io.akki" && it.name == "akki-core-jvm" }
                            check(core.file.isFile)
                            println("AKKI " + name + " core=" + core.moduleVersion.id.version)
                        }
                    }
                }
                """.trimIndent()
        )
        val runner = GradleRunner.create()
            .withProjectDir(projectDirectory.toFile())
            .withArguments("resolveCore", "--console=plain")
        val result = if (transitive) runner.build() else runner.buildAndFail()
        val output = result.output
        if (transitive) {
            assertContains(output, "AKKI compileClasspath core=$VERSION\n")
            assertContains(output, "AKKI runtimeClasspath core=$VERSION\n")
        } else {
            assertContains(output, "Cannot find a version of 'io.akki:akki-core'")
            assertContains(output, "strictly $VERSION")
            assertContains(output, "io.akki:akki-core:$newerVersion")
            assertContains(output, "Akki core and compiler plugin versions must match")
        }
    }

    private fun build(projectDirectory: Path, consumer: Consumer = Consumer.Core, extra: String = ""): String {
        prepareConsumer(projectDirectory, consumer, extra)
        return runConsumer(projectDirectory).output
    }

    private fun prepareBootstrapConsumer(projectDirectory: Path) {
        prepareConsumer(
            projectDirectory,
            Consumer.Bootstrap,
            extra = """
                application {
                    applicationDefaultJvmArgs = listOf(
                        "-Dio.akki.loggerNameStyle=" + providers.gradleProperty("loggerNameStyle").get(),
                    )
                }
            """.trimIndent(),
        )
    }

    private fun prepareConsumer(
        projectDirectory: Path,
        consumer: Consumer,
        extra: String = "",
        pomOnly: Boolean = false,
    ) {
        val repository = publishRepository(projectDirectory.resolve("repository"))
        projectDirectory.resolve("settings.gradle.kts").writeText(settings(repository))
        projectDirectory.resolve("build.gradle.kts")
            .writeText(buildScript(repository, consumer, pomOnly = pomOnly) + "\n" + extra)
        projectDirectory.resolve("src/main/kotlin/consumer").createDirectories()
        projectDirectory.resolve("src/main/kotlin/consumer/${consumer.source}")
            .writeText(fixture("consumer/${consumer.source}"))
        consumer.resource?.let {
            projectDirectory.resolve("src/main/resources").createDirectories().resolve(it)
                .writeText(fixture("consumer/$it"))
        }
        if (consumer.modular) {
            projectDirectory.resolve("src/main/java").createDirectories().resolve("module-info.java")
                .writeText("module consumer {\n    requires io.akki.core;\n}\n")
        }

    }

    private fun runConsumer(projectDirectory: Path, vararg arguments: String): BuildResult =
        consumerRunner(projectDirectory, *arguments).build()

    private fun consumerRunner(projectDirectory: Path, vararg arguments: String): GradleRunner =
        GradleRunner.create()
            .withProjectDir(projectDirectory.toFile())
            .withArguments("run", "--stacktrace", "--configuration-cache", "--no-build-cache", "--console=plain", *arguments)

    private fun publishRepository(repository: Path): Path {
        path("akki.test.repository").toFile().copyRecursively(repository.toFile())
        return repository
    }

    private fun publish(
        repository: Path,
        group: String,
        artifact: String,
        jar: Path?,
        dependencies: List<Triple<String, String, String>> = emptyList(),
        version: String = VERSION,
    ) {
        val module = repository.resolve("${group.replace('.', '/')}/$artifact/$version").createDirectories()
        jar?.let { Files.copy(it, module.resolve("$artifact-$version.jar")) }
        val declarations = dependencies.joinToString("\n") { (dependencyGroup, dependencyName, dependencyVersion) ->
            """
            |    <dependency>
            |      <groupId>$dependencyGroup</groupId>
            |      <artifactId>$dependencyName</artifactId>
            |      <version>$dependencyVersion</version>
            |    </dependency>
            """.trimMargin()
        }
        module.resolve("$artifact-$version.pom").writeText(
            """
            <project xmlns="http://maven.apache.org/POM/4.0.0">
              <modelVersion>4.0.0</modelVersion>
              <groupId>$group</groupId>
              <artifactId>$artifact</artifactId>
              <version>$version</version>
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

    private fun buildScript(
        repository: Path,
        consumer: Consumer,
        kotlinVersion: String = property("akki.kotlin.version"),
        pomOnly: Boolean = false,
    ): String =
        """
        plugins {
            application
            kotlin("jvm") version "$kotlinVersion"
            id("io.akki") version "$VERSION"
        }

        repositories {
            mavenCentral()
            maven {
                url = uri("${repository.toUri()}")
                ${if (pomOnly) "metadataSources { mavenPom(); artifact(); ignoreGradleMetadataRedirection() }" else ""}
            }
        }

        dependencies {
        ${dependencies(consumer)}
        }


        application {
            mainClass.set("${consumer.mainClass}")
            ${if (consumer.modular) "mainModule.set(\"consumer\")" else ""}
        }
        """.trimIndent()

    private fun dependencies(consumer: Consumer): String = when (consumer) {
        Consumer.Bare -> emptyList()
        Consumer.Core, Consumer.Clipped, Consumer.Incremental, Consumer.Bootstrap ->
            listOf("""implementation("io.akki:akki-core:$VERSION")""")
        Consumer.SimpleSlf4j -> listOf(
            """implementation("io.akki:akki-slf4j:$VERSION")""",
            """runtimeOnly("org.slf4j:slf4j-simple:${property("akki.slf4j.version")}")""",
        )
        Consumer.Slf4j, Consumer.Modular -> listOf(
            """implementation("io.akki:akki-slf4j:$VERSION")""",
            """runtimeOnly("ch.qos.logback:logback-classic:${property("akki.logback.version")}")""",
        )
    }.joinToString("\n") { "    $it" }

    private fun lineOf(consumer: Consumer, marker: String): Int =
        fixture("consumer/${consumer.source}").lines().indexOfFirst { it.contains(marker) } + 1

    private fun path(name: String): Path = Path.of(property(name))

    private fun property(name: String): String = requireNotNull(System.getProperty(name)) { "missing -D$name" }

    private fun fixture(name: String): String =
        requireNotNull(javaClass.getResource("/$name")) { "no fixture /$name" }.readText()

    private enum class Consumer(
        val source: String,
        val mainClass: String,
        val resource: String?,
        val modular: Boolean = false,
    ) {
        Bare("BareMain.kt", "consumer.BareMainKt", null),
        Core("Main.kt", "consumer.MainKt", null),
        Slf4j("Slf4jMain.kt", "consumer.Slf4jMainKt", "logback.xml"),
        SimpleSlf4j("SimpleSlf4jMain.kt", "consumer.SimpleSlf4jMainKt", null),
        Bootstrap("BootstrapMain.kt", "consumer.BootstrapMainKt", null),
        Clipped("ClippedMain.kt", "consumer.ClippedMainKt", null),
        Incremental("IncrementalMain.kt", "consumer.IncrementalMainKt", null),
        Modular("ModularMain.kt", "consumer.ModularMainKt", "logback.xml", modular = true),
    }

    private companion object {
        val VERSION: String = requireNotNull(System.getProperty("akki.version"))

        const val OTHER_KOTLIN: String = "2.3.21"

        fun String.minor(): String = split('.').take(2).joinToString(".")
    }
}
