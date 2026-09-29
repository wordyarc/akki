import akki.buildlogic.ClasspathSystemProperty
import akki.buildlogic.compilerArtifactId
import akki.buildlogic.generateVersionConstant
import akki.buildlogic.jvmClassTest
import akki.buildlogic.kotlinLine
import akki.buildlogic.publishAs

plugins {
    id("akki.kotlin-jvm")
    id("akki.publishing")
    id("org.jetbrains.kotlinx.kover")
    `java-test-fixtures`
}

description = "Kotlin compiler plugin for akki, built for Kotlin $kotlinLine: logger fields for the log intrinsic, " +
    "direct backend calls and the compile-time level threshold"

publishAs(compilerArtifactId)
generateVersionConstant(packageName = "io.akki.compiler", sourceSet = "main")

val fixtureRuntime: Configuration = configurations.create("fixtureRuntime") {
    isCanBeConsumed = false
    isCanBeResolved = true
}

val compilerTestLibraries: Configuration = configurations.create("compilerTestLibraries") {
    isCanBeConsumed = false
    isCanBeResolved = true
    isTransitive = false
}

dependencies {
    compileOnly(libs.kotlin.compiler)
    testFixturesApi(libs.kotlin.compiler)
    testFixturesApi(libs.kotlin.compiler.internal.test.framework)
    testFixturesApi(libs.junit.jupiter)
    testFixturesRuntimeOnly(libs.compiler.test.framework.legacy.junit)
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

tasks.withType<Test>().configureEach {
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
