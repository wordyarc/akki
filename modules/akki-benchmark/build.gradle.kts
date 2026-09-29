import akki.buildlogic.targetJvm
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("akki.versioning")
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.kotlin.plugin.allopen")
    id("org.jetbrains.kotlinx.benchmark")
}

val plugged: SourceSet = sourceSets.create("plugged") {
    kotlin.setSrcDirs(listOf("src/benchmarks/kotlin"))
}

val plain: SourceSet = sourceSets.create("plain") {
    kotlin.setSrcDirs(listOf("src/benchmarks/kotlin"))
}

val clipped: SourceSet = sourceSets.create("clipped") {
    kotlin.setSrcDirs(listOf("src/benchmarks/kotlin"))
}

kotlin {
    targetJvm(compilerOptions)
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
    listOf(plugged, plain, clipped).forEach { sourceSet ->
        sourceSet.implementationConfigurationName(project(":akki-core"))
        sourceSet.implementationConfigurationName(project(":akki-slf4j"))
        sourceSet.implementationConfigurationName(libs.logback.classic)
        sourceSet.implementationConfigurationName(libs.kotlinx.benchmark.runtime)
    }
}

mapOf(plugged to emptyList(), clipped to listOf("-P", "plugin:io.akki:minLevel=info")).forEach { (set, options) ->
    tasks.named<KotlinCompile>(set.getCompileTaskName("kotlin")) {
        inputs.files(compilerPlugin).withNormalizer(ClasspathNormalizer::class)
        compilerOptions.freeCompilerArgs.addAll(
            provider { compilerPlugin.files.map { "-Xplugin=${it.absolutePath}" } + options },
        )
    }
}

benchmark {
    targets {
        register(plugged.name)
        register(plain.name)
        register(clipped.name)
    }
    configurations {
        named("main") {
            warmups = 3
            iterations = 5
            iterationTime = 500
            iterationTimeUnit = "ms"
            mode = "avgt"
            outputTimeUnit = "ns"
            advanced("jvmForks", 3)
        }
    }
}
