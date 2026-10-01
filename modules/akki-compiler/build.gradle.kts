import akki.buildlogic.COMPILER_ADAPTERS_MANIFEST
import akki.buildlogic.COMPILER_COMPAT_PROJECT
import akki.buildlogic.COMPILER_COMPAT_SERVICE
import akki.buildlogic.ClasspathSystemProperty
import akki.buildlogic.MergeCompilerCompatServices
import akki.buildlogic.compileAgainstCompilerApi
import akki.buildlogic.compilerAdapters
import akki.buildlogic.compilerApiBaseline
import akki.buildlogic.generateVersionConstant
import akki.buildlogic.jvmClassTest
import akki.buildlogic.onTargetJdk

plugins {
    id("akki.kotlin-jvm")
    id("akki.publishing")
    id("org.jetbrains.kotlinx.kover")
    `java-test-fixtures`
}

description = "Kotlin compiler plugin for akki: logger fields for the log intrinsic, direct backend calls and the " +
    "compile-time level threshold"

generateVersionConstant(packageName = "io.akki.compiler", sourceSet = "main")
compileAgainstCompilerApi(compilerApiBaseline)

val adapters = compilerAdapters
val testedKotlin: List<String> = providers.gradleProperty("akki.kotlin.tested").get().split(',')
val rejectedKotlin: List<String> = providers.gradleProperty("akki.kotlin.rejected").get().split(',')

val embeddedCompat: Configuration = configurations.create("embeddedCompat") {
    isCanBeConsumed = false
    isCanBeResolved = true
    isTransitive = false
}

val embeddedCompatSources: Configuration = configurations.create("embeddedCompatSources") {
    isCanBeConsumed = false
    isCanBeResolved = true
    isTransitive = false
    extendsFrom(embeddedCompat)
    attributes {
        attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category.DOCUMENTATION))
        attribute(DocsType.DOCS_TYPE_ATTRIBUTE, objects.named(DocsType.SOURCES))
        attribute(Bundling.BUNDLING_ATTRIBUTE, objects.named(Bundling.EXTERNAL))
        attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage.JAVA_RUNTIME))
    }
}

val fixtureRuntime: Configuration = configurations.create("fixtureRuntime") {
    isCanBeConsumed = false
    isCanBeResolved = true
}

val compilerTestLibraries: Configuration = configurations.create("compilerTestLibraries") {
    isCanBeConsumed = false
    isCanBeResolved = true
    isTransitive = false
}

val coreRuntime: Configuration = configurations.create("coreRuntime") {
    isCanBeConsumed = false
    isCanBeResolved = true
}

val compilerHosts: Map<String, Configuration> =
    (testedKotlin + rejectedKotlin + adapters.map { it.minVersion }).distinct().associateWith { version ->
        configurations.create("compilerHost-$version") {
            isCanBeConsumed = false
            isCanBeResolved = true
        }
    }

val jarTestSources: SourceSet = sourceSets.create("jarTest") {
    resources.srcDir("src/jarTest/data")
}

val jarTestTasks: Set<String> = setOf(jarTestSources.name, onTargetJdk(jarTestSources.name))

dependencies {
    compileOnly(project(COMPILER_COMPAT_PROJECT))
    embeddedCompat(project(COMPILER_COMPAT_PROJECT))
    adapters.forEach { adapter ->
        embeddedCompat(project(adapter.projectPath))
        testFixturesRuntimeOnly(project(adapter.projectPath))
    }
    testFixturesApi(libs.kotlin.compiler)
    testFixturesApi(libs.kotlin.compiler.internal.test.framework)
    testFixturesApi(libs.junit.jupiter)
    testFixturesRuntimeOnly(libs.compiler.test.framework.legacy.junit)
    testImplementation(project(COMPILER_COMPAT_PROJECT))
    testImplementation(libs.kotlin.reflect)
    fixtureRuntime(project(":akki-core"))
    fixtureRuntime(project(":akki-slf4j"))
    fixtureRuntime(project(":akki-test"))
    fixtureRuntime(project(":test-utils"))
    fixtureRuntime(libs.logback.classic)
    compilerTestLibraries(libs.kotlin.stdlib)
    compilerTestLibraries(libs.kotlin.stdlib.jdk8)
    compilerTestLibraries(libs.kotlin.reflect)
    compilerTestLibraries(libs.kotlin.test)
    compilerTestLibraries(libs.kotlin.script.runtime)
    compilerTestLibraries(libs.kotlin.annotations.jvm)
    jarTestSources.implementationConfigurationName(libs.kotlin.test.junit5)
    jarTestSources.implementationConfigurationName(libs.junit.jupiter)
    jarTestSources.implementationConfigurationName(libs.asm)
    jarTestSources.implementationConfigurationName(libs.kotlin.tooling.core)
    jarTestSources.runtimeOnlyConfigurationName(libs.junit.platform.launcher)
    coreRuntime(project(":akki-core"))
    compilerHosts.forEach { (version, host) -> host("org.jetbrains.kotlin:kotlin-compiler:$version") }
}

val embeddedAdapters: FileCollection = embeddedCompat.incoming.artifactView {
    componentFilter { it is ProjectComponentIdentifier && it.projectPath != COMPILER_COMPAT_PROJECT }
}.files

val mergeCompilerCompatServices = tasks.register<MergeCompilerCompatServices>("mergeCompilerCompatServices") {
    adapterJars.from(embeddedAdapters)
    service = COMPILER_COMPAT_SERVICE
    expectedProviders = adapters.map { it.factoryClassName }
    outputDirectory = layout.buildDirectory.dir("generated/compat/services")
}

tasks.jar {
    duplicatesStrategy = DuplicatesStrategy.FAIL
    dependsOn(embeddedCompat)
    from({ embeddedCompat.map(::zipTree) }) {
        exclude(
            COMPILER_COMPAT_SERVICE,
            "META-INF/MANIFEST.MF",
            "module-info.class",
            "META-INF/versions/*/module-info.class",
        )
    }
    from(mergeCompilerCompatServices)
}

tasks.withType<Jar>().matching { it.name == "sourcesJar" }.configureEach {
    duplicatesStrategy = DuplicatesStrategy.FAIL
    dependsOn(embeddedCompatSources)
    from({ embeddedCompatSources.map(::zipTree) }) {
        exclude("META-INF/**")
    }
}

val testData: Directory = layout.projectDirectory.dir("testData")

val generateTests = tasks.register<JavaExec>("generateTests") {
    val output = layout.buildDirectory.dir("generated/tests")
    inputs.dir(testData).withPropertyName("testData").withPathSensitivity(PathSensitivity.RELATIVE)
    outputs.dir(output).withPropertyName("tests")
    classpath = sourceSets.testFixtures.get().runtimeClasspath
    mainClass = "io.akki.compiler.test.GenerateTestsKt"
    args(output.get().asFile.path, testData.asFile.name)
}

sourceSets.test {
    java.srcDir(generateTests)
}

tasks.withType<Test>().matching { it.name !in jarTestTasks }.configureEach {
    inputs.dir(testData).withPropertyName("testData").withPathSensitivity(PathSensitivity.RELATIVE)
}

tasks.test {
    jvmArgumentProviders.add(ClasspathSystemProperty("akki.fixture.classpath", fixtureRuntime))
    val javacProperties = mapOf(
        "kotlin-stdlib" to "kotlin.full.stdlib.path",
        "kotlin-reflect" to "kotlin.reflect.jar.path",
    )
    listOf(
        "kotlin-stdlib",
        "kotlin-stdlib-jdk8",
        "kotlin-reflect",
        "kotlin-test",
        "kotlin-script-runtime",
        "kotlin-annotations-jvm",
    ).forEach { library ->
        val jar = compilerTestLibraries.filter { it.name.matches(Regex("$library-\\d.*\\.jar")) }
        jvmArgumentProviders.add(ClasspathSystemProperty("org.jetbrains.kotlin.test.$library", jar))
        javacProperties[library]?.let { jvmArgumentProviders.add(ClasspathSystemProperty(it, jar)) }
    }
    systemProperty("akki.jvm.target", libs.versions.jvm.target.get())
    systemProperty("idea.ignore.disabled.plugins", "true")
    systemProperty("idea.home.path", projectDir)
    val updateTestData = providers.gradleProperty("kotlin.test.update.test.data").orElse("false")
    systemProperty("kotlin.test.update.test.data", updateTestData.get())
}

jvmClassTest(tasks.test) {
    description = "Runs the logger name matrix with JVM_CLASS categories"
    filter { includeTestsMatching("*AkkiBoxTestGenerated*Names*") }
}

val jarTest = tasks.register<Test>(jarTestSources.name) {
    group = LifecycleBasePlugin.VERIFICATION_GROUP
    description = "Checks the published jar: its contents, its references into every tested Kotlin compiler and " +
        "its behaviour in a process of each of them"
    testClassesDirs = jarTestSources.output.classesDirs
    classpath = jarTestSources.runtimeClasspath
    val manifest = isolated.rootProject.projectDirectory.file(COMPILER_ADAPTERS_MANIFEST)
    jvmArgumentProviders.add(ClasspathSystemProperty("akki.compiler.adapters", files(manifest)))
    jvmArgumentProviders.add(ClasspathSystemProperty("akki.compiler.jar", files(tasks.jar)))
    jvmArgumentProviders.add(ClasspathSystemProperty("akki.compiler.embedded", embeddedCompat))
    val driver = files(jarTestSources.java.classesDirectory)
    jvmArgumentProviders.add(ClasspathSystemProperty("akki.compiler.driver", driver))
    jvmArgumentProviders.add(ClasspathSystemProperty("akki.core.classpath", coreRuntime))
    compilerHosts.forEach { (version, host) ->
        jvmArgumentProviders.add(ClasspathSystemProperty("akki.compiler.host.$version", host))
    }
    systemProperty("akki.kotlin.tested", testedKotlin.joinToString(","))
    systemProperty("akki.kotlin.rejected", rejectedKotlin.joinToString(","))
    systemProperty("akki.version", project.version.toString())
    systemProperty("akki.jvm.target", libs.versions.jvm.target.get())
    shouldRunAfter(tasks.test)
}

tasks.check {
    dependsOn(jarTest)
}

kover {
    currentProject {
        instrumentation {
            disabledForTestTasks.addAll(jarTestTasks)
        }
    }
}
