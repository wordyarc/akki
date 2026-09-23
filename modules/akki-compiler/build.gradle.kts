import akki.buildlogic.ClasspathSystemProperty
import akki.buildlogic.WriteVersionConstant

plugins {
    id("akki.kotlin-jvm")
    id("org.jetbrains.kotlinx.kover")
    `java-test-fixtures`
}

val writeVersionConstant = tasks.register<WriteVersionConstant>("writeVersionConstant") {
    packageName = "io.akki.compiler"
    version = project.version.toString()
    outputDirectory = layout.buildDirectory.dir("generated/source/version")
}

kotlin {
    sourceSets.main {
        kotlin.srcDir(writeVersionConstant)
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
    systemProperty("io.akki.loggerNameStyle", "source")
    systemProperty("idea.ignore.disabled.plugins", "true")
    systemProperty("idea.home.path", projectDir)
    val updateTestData = providers.gradleProperty("kotlin.test.update.test.data").orElse("false")
    systemProperty("kotlin.test.update.test.data", updateTestData.get())
}

val jvmClassTest = tasks.register<Test>("jvmClassTest") {
    val source = tasks.test.get()
    group = "verification"
    description = "Runs the logger name matrix with JVM_CLASS categories"
    testClassesDirs = source.testClassesDirs
    classpath = source.classpath
    jvmArgumentProviders.addAll(source.jvmArgumentProviders.filterIsInstance<ClasspathSystemProperty>())
    systemProperties(source.systemProperties)
    systemProperty("io.akki.loggerNameStyle", "jvm-class")
    filter { includeTestsMatching("*AkkiBoxTestGenerated*Names*") }
    shouldRunAfter(source)
}

tasks.named("check") {
    dependsOn(jvmClassTest)
}
