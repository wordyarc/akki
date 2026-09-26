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
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource

class AkkiGradlePluginTest {
    @Test
    fun `lowers logger calls in a consumer project`(@TempDir projectDirectory: Path) {
        val output = build(projectDirectory)
        assertContains(output, "AKKI name=consumer.OrderService eager=1 lazy=0")
    }

    @Test
    fun `discovers the slf4j backend on the classpath`(@TempDir projectDirectory: Path) {
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
    fun `passes the configured logger name style to the JVMs that Gradle launches`(@TempDir projectDirectory: Path) {
        prepareConsumer(
            projectDirectory,
            Consumer.Bootstrap,
            extra = """
                akki {
                    loggerNameStyle.set(providers.gradleProperty("style").map(io.akki.gradle.LoggerNameStyle::valueOf))
                }
            """.trimIndent(),
        )

        val jvm = runConsumer(projectDirectory, "test", "-Pstyle=JVM_CLASS")
        assertEquals(TaskOutcome.SUCCESS, jvm.task(":test")?.outcome, jvm.output)
        assertContains(jvm.output, "AKKI startup style=jvm-class")
        assertContains(jvm.output, "AKKI name=consumer.Owner\$Nested")
        assertContains(jvm.output, "AKKI test name=consumer.Owner\$Nested")

        val repeated = runConsumer(projectDirectory, "test", "-Pstyle=JVM_CLASS")
        assertContains(repeated.output, "Reusing configuration cache.")
        assertEquals(TaskOutcome.UP_TO_DATE, repeated.task(":test")?.outcome, repeated.output)
        assertContains(repeated.output, "AKKI name=consumer.Owner\$Nested")

        val source = runConsumer(projectDirectory, "test", "-Pstyle=SOURCE")
        assertEquals(TaskOutcome.SUCCESS, source.task(":test")?.outcome, source.output)
        assertContains(source.output, "AKKI startup style=source")
        assertContains(source.output, "AKKI name=consumer.Owner.Nested")
        assertContains(source.output, "AKKI test name=consumer.Owner.Nested")
    }

    @Test
    fun `keeps the logger name style that a task sets itself`(@TempDir projectDirectory: Path) {
        prepareConsumer(
            projectDirectory,
            Consumer.Bootstrap,
            extra = """
                akki {
                    loggerNameStyle = io.akki.gradle.LoggerNameStyle.JVM_CLASS
                }

                application {
                    applicationDefaultJvmArgs = listOf("-Dio.akki.loggerNameStyle=source")
                }

                tasks.test {
                    systemProperty("io.akki.loggerNameStyle", "source")
                }
            """.trimIndent(),
        )

        val output = runConsumer(projectDirectory, "test").output

        assertContains(output, "AKKI name=consumer.Owner.Nested")
        assertContains(output, "AKKI test name=consumer.Owner.Nested")
    }

    @Test
    fun `passes no logger name style until one is configured`(@TempDir projectDirectory: Path) {
        val output = build(projectDirectory, consumer = Consumer.Bootstrap)

        assertContains(output, "AKKI startup style=null")
        assertContains(output, "AKKI name=consumer.Owner.Nested")
    }

    @Test
    fun `writes to stderr after an slf4j-api downgrade`(@TempDir projectDirectory: Path) {
        val output = build(
            projectDirectory,
            consumer = Consumer.Slf4j,
            extra = """configurations.all { resolutionStrategy.force("org.slf4j:slf4j-api:1.7.36") }""",
        )

        assertContains(output, "writing to stderr at INFO. $SLF4J_HINT")
        assertContains(output, "INFO  consumer.OrderService - received A-1")
        assertContains(output, "AKKI evaluated=0")
    }

    @Test
    fun `preserves the slf4j version of an slf4j 1 application`(@TempDir projectDirectory: Path) {
        val output = build(projectDirectory, consumer = Consumer.LegacySlf4j)

        assertContains(output, "writing to stderr at INFO. $SLF4J_HINT")
        assertContains(output, "INFO  consumer.OrderService - received A-1")
        assertContains(output, "INFO consumer.Application - application record")
        assertContains(output, "AKKI slf4j-api=1.7.36")
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
        assertContains(output, "akki: minLevel=info, lower-level records are removed from the bytecode")
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
    fun `adds the compiler plugin built for the Kotlin line of the project`(@TempDir projectDirectory: Path) {
        prepareConsumer(projectDirectory, Consumer.Bare)

        val output = GradleRunner.create()
            .withProjectDir(projectDirectory.toFile())
            .withArguments("dependencies", "--configuration", "kotlinCompilerPluginClasspathMain", "--console=plain")
            .build()
            .output

        assertContains(output, "$GROUP:akki-compiler-kotlin-${property("akki.kotlin.version").line()}:$VERSION")
    }

    @ParameterizedTest(name = "Kotlin {0}")
    @MethodSource("testedKotlinVersions")
    fun `compiles and runs on every tested Kotlin release`(kotlinVersion: String, @TempDir projectDirectory: Path) {
        prepareConsumer(
            projectDirectory,
            Consumer.Compatibility,
            extra = "akki { minLevel = io.akki.gradle.MinLevel.INFO }",
            kotlinVersion = kotlinVersion,
        )

        val output = runConsumer(projectDirectory).output

        assertContains(output, "akki: minLevel=info")
        assertContains(
            output,
            "AKKI records=INFO consumer.Service created, INFO consumer.Service handled A-1, " +
                "INFO audit audited A-1, INFO consumer.Service explicit A-1, WARN consumer.Service folded A-1, " +
                "INFO consumer.CompatibilityMain traced A-2, INFO consumer.CompatibilityMain top-level",
        )
    }

    @Test
    fun `rejects an unsupported Kotlin line`(@TempDir projectDirectory: Path) {
        val supported = property("akki.kotlin.version").line()
        require(OTHER_KOTLIN.line() != supported)
        val repository = publishRepository(projectDirectory.resolve("repository"))
        projectDirectory.resolve("settings.gradle.kts").writeText(settings(repository))
        projectDirectory.resolve("build.gradle.kts")
            .writeText(buildScript(repository, Consumer.Bare, kotlinVersion = OTHER_KOTLIN))

        val output = GradleRunner.create()
            .withProjectDir(projectDirectory.toFile())
            .withArguments("help", "--console=plain")
            .buildAndFail()
            .output

        assertContains(
            output,
            "akki $VERSION supports Kotlin $supported.x, but this project uses Kotlin $OTHER_KOTLIN. " +
                "The compiler plugin API differs between Kotlin releases. " +
                "Use a supported Kotlin version or an akki version that supports Kotlin ${OTHER_KOTLIN.line()}.x.",
        )
    }

    @Test
    fun `applies only to JVM compilations`(@TempDir projectDirectory: Path) {
        val repository = publishRepository(projectDirectory.resolve("repository"))
        projectDirectory.resolve("settings.gradle.kts").writeText(settings(repository))
        projectDirectory.resolve("build.gradle.kts").writeText(
            """
            plugins {
                kotlin("multiplatform") version "${property("akki.kotlin.version")}"
                id("$PLUGIN_ID") version "$VERSION"
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
                    for (compilation in listOf("jvm", "js")) {
                        for (name in listOf(compilation + "CompileClasspath", compilation + "RuntimeClasspath")) {
                            val akki = configurations.getByName(name).allDependencies.filter { it.group == "$GROUP" }
                            println("AKKI " + name + " " + akki.map { it.name }.sorted())
                        }
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
        assertContains(output, "AKKI jvmRuntimeClasspath [akki-core, akki-slf4j]")
        assertContains(output, "AKKI jsCompileClasspath []")
        assertContains(output, "AKKI jsRuntimeClasspath []")
    }

    @Test
    fun `supplies core and the slf4j backend to consumers without runtime dependencies`(@TempDir projectDirectory: Path) {
        val output = build(projectDirectory, consumer = Consumer.Bare)

        assertContains(output, "writing to stderr at INFO. $SLF4J_HINT")
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
                id("$PLUGIN_ID") version "$VERSION"
            }

            repositories {
                mavenCentral()
                maven { url = uri("${repository.toUri()}") }
            }

            kotlin {
                jvm()
                sourceSets {
                    commonMain.dependencies {
                        implementation("$GROUP:akki-core:$VERSION")
                    }
                    commonTest.dependencies {
                        implementation(kotlin("test"))
                        implementation("$GROUP:akki-test:$VERSION")
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

    @Test
    fun `leaves the core version of a published library open to its consumers`(@TempDir projectDirectory: Path) {
        val repository = publishRepository(projectDirectory.resolve("repository"))
        val newerVersion = "999.0.0"
        publish(repository, GROUP, "akki-core", path("akki.core.jar"), version = newerVersion)
        val library = projectDirectory.resolve("library").createDirectories()
        library.resolve("settings.gradle.kts").writeText(settings(repository, name = "library"))
        library.resolve("build.gradle.kts").writeText(
            """
            plugins {
                kotlin("jvm") version "${property("akki.kotlin.version")}"
                id("$PLUGIN_ID") version "$VERSION"
                `maven-publish`
            }

            group = "consumer"
            version = "$VERSION"

            repositories {
                mavenCentral()
                maven { url = uri("${repository.toUri()}") }
            }

            publishing {
                publications { create<MavenPublication>("library") { from(components["java"]) } }
                repositories { maven { url = uri("${repository.toUri()}") } }
            }
            """.trimIndent()
        )
        library.resolve("src/main/kotlin/consumer").createDirectories()
            .resolve("SharedService.kt").writeText(fixture("consumer/SharedService.kt"))
        GradleRunner.create()
            .withProjectDir(library.toFile())
            .withArguments("publish", "--console=plain")
            .build()
        val application = projectDirectory.resolve("application").createDirectories()
        application.resolve("settings.gradle.kts").writeText(settings(repository, name = "application"))
        application.resolve("build.gradle.kts").writeText(
            """
            plugins {
                kotlin("jvm") version "${property("akki.kotlin.version")}"
            }

            repositories {
                mavenCentral()
                maven { url = uri("${repository.toUri()}") }
            }

            dependencies {
                implementation("consumer:library:$VERSION")
                implementation("$GROUP:akki-core:$newerVersion")
            }

            tasks.register("resolveCore") {
                val runtimeClasspath = configurations.runtimeClasspath
                doLast {
                    val akki = runtimeClasspath.get().incoming.resolutionResult.allComponents
                        .mapNotNull { it.moduleVersion }
                        .filter { it.group == "$GROUP" }
                    println("AKKI core=" + akki.single { it.name == "akki-core" }.version)
                    println("AKKI slf4j=" + akki.single { it.name == "akki-slf4j" }.version)
                }
            }
            """.trimIndent()
        )

        val output = GradleRunner.create()
            .withProjectDir(application.toFile())
            .withArguments("resolveCore", "--console=plain")
            .build()
            .output

        assertContains(output, "AKKI core=$newerVersion\n")
        assertContains(output, "AKKI slf4j=$VERSION\n")
    }

    @Test
    fun `pins the slf4j backend despite a newer transitive dependency`(@TempDir projectDirectory: Path) {
        val repository = publishRepository(projectDirectory.resolve("repository"))
        val newerVersion = "999.0.0"
        publish(repository, GROUP, "akki-slf4j", path("akki.slf4j.jar"), version = newerVersion)
        publish(repository, "consumer", "library", null, dependencies = listOf(Triple(GROUP, "akki-slf4j", newerVersion)))
        projectDirectory.resolve("settings.gradle.kts").writeText(settings(repository))
        projectDirectory.resolve("build.gradle.kts").writeText(
            buildScript(repository, consumer = Consumer.Bare) + "\n" +
                """
                dependencies {
                    implementation("consumer:library:$VERSION")
                }

                tasks.register("resolveSlf4j") {
                    doLast {
                        val slf4j = configurations.runtimeClasspath.get().resolvedConfiguration.resolvedArtifacts
                            .single { it.moduleVersion.id.group == "$GROUP" && it.name == "akki-slf4j" }
                        check(slf4j.file.isFile)
                        println("AKKI runtimeClasspath slf4j=" + slf4j.moduleVersion.id.version)
                    }
                }
                """.trimIndent()
        )

        val output = GradleRunner.create()
            .withProjectDir(projectDirectory.toFile())
            .withArguments("resolveSlf4j", "--console=plain")
            .build()
            .output

        assertContains(output, "AKKI runtimeClasspath slf4j=$VERSION\n")
    }

    private fun checkCoreVersionLock(projectDirectory: Path, transitive: Boolean) {
        val repository = publishRepository(projectDirectory.resolve("repository"))
        val newerVersion = "999.0.0"
        publish(repository, GROUP, "akki-core", path("akki.core.jar"), version = newerVersion)
        publish(
            repository,
            "consumer",
            "library",
            path("akki.slf4j.jar"),
            dependencies = listOf(Triple(GROUP, "akki-core", newerVersion)),
        )
        val dependency = if (transitive) "consumer:library:$VERSION" else "$GROUP:akki-core:$newerVersion"
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
                                .single { it.moduleVersion.id.group == "$GROUP" && it.name == "akki-core-jvm" }
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
            assertContains(output, "Cannot find a version of '$GROUP:akki-core'")
            assertContains(output, "strictly $VERSION")
            assertContains(output, "$GROUP:akki-core:$newerVersion")
            assertContains(output, "Akki modules require the same version as the compiler plugin")
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
        kotlinVersion: String = property("akki.kotlin.version"),
    ) {
        val repository = publishRepository(projectDirectory.resolve("repository"))
        projectDirectory.resolve("settings.gradle.kts").writeText(settings(repository))
        projectDirectory.resolve("build.gradle.kts")
            .writeText(buildScript(repository, consumer, kotlinVersion, pomOnly) + "\n" + extra)
        projectDirectory.resolve("src/main/kotlin/consumer").createDirectories()
        projectDirectory.resolve("src/main/kotlin/consumer/${consumer.source}")
            .writeText(fixture("consumer/${consumer.source}"))
        consumer.resource?.let {
            projectDirectory.resolve("src/main/resources").createDirectories().resolve(it)
                .writeText(fixture("consumer/$it"))
        }
        consumer.test?.let {
            projectDirectory.resolve("src/test/kotlin/consumer").createDirectories().resolve(it)
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

    private fun settings(repository: Path, name: String = "consumer"): String =
        """
        pluginManagement {
            repositories {
                maven { url = uri("${repository.toUri()}") }
                mavenCentral()
            }
        }

        rootProject.name = "$name"
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
            id("$PLUGIN_ID") version "$VERSION"
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

        ${if (consumer.test != null) "tasks.test { testLogging.showStandardStreams = true }" else ""}
        """.trimIndent()

    private fun dependencies(consumer: Consumer): String = when (consumer) {
        Consumer.Bare, Consumer.Compatibility -> emptyList()
        Consumer.Core, Consumer.Clipped, Consumer.Incremental ->
            listOf("""implementation("$GROUP:akki-core:$VERSION")""")
        Consumer.Bootstrap -> listOf(
            """implementation("$GROUP:akki-core:$VERSION")""",
            """testImplementation(kotlin("test"))""",
        )
        Consumer.SimpleSlf4j -> listOf(
            """runtimeOnly("org.slf4j:slf4j-simple:${property("akki.slf4j.version")}")""",
        )
        Consumer.Slf4j -> listOf(
            """implementation("$GROUP:akki-slf4j:$VERSION")""",
            """runtimeOnly("ch.qos.logback:logback-classic:${property("akki.logback.version")}")""",
        )
        Consumer.Modular -> listOf(
            """runtimeOnly("ch.qos.logback:logback-classic:${property("akki.logback.version")}")""",
        )
        Consumer.LegacySlf4j -> listOf(
            """implementation("org.slf4j:slf4j-api:1.7.36")""",
            """runtimeOnly("ch.qos.logback:logback-classic:1.2.13")""",
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
        val test: String? = null,
    ) {
        Bare("BareMain.kt", "consumer.BareMainKt", null),
        Core("Main.kt", "consumer.MainKt", null),
        Slf4j("Slf4jMain.kt", "consumer.Slf4jMainKt", "logback.xml"),
        SimpleSlf4j("SimpleSlf4jMain.kt", "consumer.SimpleSlf4jMainKt", null),
        Bootstrap("BootstrapMain.kt", "consumer.BootstrapMainKt", null, test = "BootstrapTest.kt"),
        Clipped("ClippedMain.kt", "consumer.ClippedMainKt", null),
        Incremental("IncrementalMain.kt", "consumer.IncrementalMainKt", null),
        Modular("ModularMain.kt", "consumer.ModularMainKt", "logback.xml", modular = true),
        Compatibility("CompatibilityMain.kt", "consumer.CompatibilityMainKt", null),
        LegacySlf4j("LegacySlf4jMain.kt", "consumer.LegacySlf4jMainKt", null),
    }

    private companion object {
        val GROUP: String = requireNotNull(System.getProperty("akki.maven.group"))

        val PLUGIN_ID: String = requireNotNull(System.getProperty("akki.plugin.id"))

        val VERSION: String = requireNotNull(System.getProperty("akki.version"))

        const val OTHER_KOTLIN: String = "2.3.21"

        const val SLF4J_HINT: String = "Add an SLF4J 2 provider to the runtime classpath, for example logback-classic."

        @JvmStatic
        fun testedKotlinVersions(): List<String> = requireNotNull(System.getProperty("akki.kotlin.tested")).split(',')

        fun String.line(): String = split('.').take(2).joinToString(".")
    }
}
