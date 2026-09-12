import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("akki.base")
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.kotlin.plugin.allopen")
    id("org.jetbrains.kotlinx.benchmark")
}

val plugged: SourceSet by sourceSets.creating {
    kotlin.setSrcDirs(listOf("src/benchmarks/kotlin"))
}

val plain: SourceSet by sourceSets.creating {
    kotlin.setSrcDirs(listOf("src/benchmarks/kotlin"))
}

java {
    toolchain.languageVersion = JavaLanguageVersion.of(libs.versions.jvm.toolchain.get().toInt())
    sourceCompatibility = JavaVersion.toVersion(libs.versions.jvm.target.get())
    targetCompatibility = JavaVersion.toVersion(libs.versions.jvm.target.get())
}

allOpen {
    annotation("org.openjdk.jmh.annotations.State")
}

val compilerPlugin: Configuration = configurations.create("compilerPlugin") {
    isCanBeConsumed = false
    isCanBeResolved = true
}

dependencies {
    compilerPlugin(project(":akki-compiler"))
    listOf(plugged, plain).forEach { sourceSet ->
        sourceSet.implementationConfigurationName(project(":akki-core"))
        sourceSet.implementationConfigurationName(libs.kotlinx.benchmark.runtime)
    }
}

tasks.withType<KotlinCompile>().configureEach {
    compilerOptions.jvmTarget = JvmTarget.fromTarget(libs.versions.jvm.target.get())
}

tasks.named<KotlinCompile>(plugged.getCompileTaskName("kotlin")) {
    inputs.files(compilerPlugin).withNormalizer(ClasspathNormalizer::class)
    compilerOptions.freeCompilerArgs.addAll(
        provider { compilerPlugin.files.map { "-Xplugin=${it.absolutePath}" } },
    )
}

benchmark {
    targets {
        register(plugged.name)
        register(plain.name)
    }
    configurations {
        named("main") {
            warmups = 3
            iterations = 5
            iterationTime = 500
            iterationTimeUnit = "ms"
            mode = "avgt"
            outputTimeUnit = "ns"
        }
    }
}
