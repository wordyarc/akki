plugins {
    id("lokki.kotlin-jvm")
    alias(libs.plugins.buildconfig)
    `java-test-fixtures`
    idea
}

val testDataDir = layout.projectDirectory.dir("testData")
val testGenDirectory = layout.buildDirectory.dir("test-gen")

sourceSets {
    main {
        java.setSrcDirs(listOf("src"))
        resources.setSrcDirs(listOf("resources"))
    }
    testFixtures {
        java.setSrcDirs(listOf("test-fixtures"))
    }
    test {
        java.setSrcDirs(listOf("test", testGenDirectory))
        resources.setSrcDirs(listOf(testDataDir))
    }
}

idea {
    // This is needed until IDEA fixes IDEA-339729.
    module.generatedSourceDirs.add(testGenDirectory.get().asFile)
}

val testArtifacts: Configuration = configurations.create("testArtifact")

val annotationsRuntimeClasspath = configurations.dependencyScope("annotationsRuntimeClasspath") {
    isTransitive = false
}
val annotationsJvmRuntimeClasspath = configurations.resolvable("annotationsJvmRuntimeClasspath") {
    extendsFrom(annotationsRuntimeClasspath)
}

dependencies {
    compileOnly(libs.kotlin.compiler)

    testFixturesApi(libs.kotlin.test.junit5)
    testFixturesApi(libs.kotlin.test.framework)
    testFixturesApi(libs.kotlin.compiler)
    testFixturesRuntimeOnly(libs.junit)

    annotationsRuntimeClasspath(project(":lokki-annotations"))

    // Dependencies required to run the internal test framework.
    testArtifacts(libs.kotlin.stdlib)
    testArtifacts(libs.kotlin.stdlib.jdk8)
    testArtifacts(libs.kotlin.reflect)
    testArtifacts(libs.kotlin.test)
    testArtifacts(libs.kotlin.script.runtime)
    testArtifacts(libs.kotlin.annotations.jvm)

}

buildConfig {
    useKotlinOutput {
        internalVisibility = true
    }

    packageName("org.jetbrains.kotlin.compiler.plugin.template")
    buildConfigField("String", "KOTLIN_PLUGIN_ID", "\"${providers.gradleProperty("lokki.plugin.id").get()}\"")
}

tasks.test {
    dependsOn(testArtifacts)
    dependsOn(annotationsJvmRuntimeClasspath)

    workingDir = rootDir

    systemProperty("annotationsRuntime.jvm.classpath", annotationsJvmRuntimeClasspath.get().asPath)

    // Properties required to run the internal test framework.
    setLibraryProperty("org.jetbrains.kotlin.test.kotlin-stdlib", "kotlin-stdlib")
    setLibraryProperty("org.jetbrains.kotlin.test.kotlin-stdlib-jdk8", "kotlin-stdlib-jdk8")
    setLibraryProperty("org.jetbrains.kotlin.test.kotlin-reflect", "kotlin-reflect")
    setLibraryProperty("org.jetbrains.kotlin.test.kotlin-test", "kotlin-test")
    setLibraryProperty("org.jetbrains.kotlin.test.kotlin-script-runtime", "kotlin-script-runtime")
    setLibraryProperty("org.jetbrains.kotlin.test.kotlin-annotations-jvm", "kotlin-annotations-jvm")

    systemProperty("idea.ignore.disabled.plugins", "true")
    systemProperty("idea.home.path", rootDir)

}

kotlin {
    compilerOptions {
        optIn.add("org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi")
        optIn.add("org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI")
    }
}

val generateTests = tasks.register<JavaExec>("generateTests") {
    inputs.dir(testDataDir)
        .withPropertyName("testData")
        .withPathSensitivity(PathSensitivity.RELATIVE)
    outputs.dir(testGenDirectory)
        .withPropertyName("generatedTests")

    classpath = sourceSets.testFixtures.get().runtimeClasspath
    mainClass.set("org.jetbrains.kotlin.compiler.plugin.template.GenerateTestsKt")
    workingDir = rootDir
    args(
        listOf(
            testGenDirectory.get().asFile.absolutePath,
            testDataDir.asFile.absolutePath,
        )
    )
}

tasks.compileTestKotlin {
    dependsOn(generateTests)
}

fun Test.setLibraryProperty(propName: String, jarName: String) {
    val path = testArtifacts.files
        .find { """$jarName-\d.*""".toRegex().matches(it.name) }
        ?.absolutePath
        ?: return
    systemProperty(propName, path)
}
