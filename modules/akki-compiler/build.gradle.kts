import org.gradle.api.tasks.bundling.Jar

plugins {
    id("akki.kotlin-jvm")
}

evaluationDependsOn(":akki-core")

kotlin {
    explicitApi()

    compilerOptions {
        optIn.add("org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi")
        optIn.add("org.jetbrains.kotlin.ir.InternalSymbolFinderAPI")
        optIn.add("org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI")
    }
}

dependencies {
    compileOnly(libs.kotlin.compiler)
    testImplementation(project(":akki-core"))
    testImplementation(libs.kotlin.compiler)
    testImplementation(libs.kotlin.test.junit5)
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

val compilerPluginJar = tasks.named<Jar>("jar")
val coreJar = project(":akki-core").tasks.named<Jar>("jvmJar")
val compilerPluginJarPath = compilerPluginJar.flatMap { it.archiveFile }.get().asFile.absolutePath
val coreJarPath = coreJar.flatMap { it.archiveFile }.get().asFile.absolutePath

tasks.test {
    dependsOn(compilerPluginJar, coreJar)
    systemProperty("akki.compiler.plugin.jar", compilerPluginJarPath)
    systemProperty("akki.core.jar", coreJarPath)
}
