import akki.buildlogic.compileModuleDescriptor
import akki.buildlogic.jvmModuleName
import akki.buildlogic.library
import akki.buildlogic.targetJvm
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("akki.versioning")
    id("org.jetbrains.kotlin.jvm")
    id("akki.testing")
}

tasks.named<Javadoc>("javadoc") {
    exclude("module-info.java")
}

compileModuleDescriptor(tasks.named<JavaCompile>("compileJava"), tasks.named<KotlinCompile>("compileKotlin"))

tasks.named<KotlinCompile>("compileKotlin") {
    compilerOptions.moduleName = jvmModuleName
}

kotlin {
    compilerOptions {
        allWarningsAsErrors = true
        freeCompilerArgs.add("-progressive")
    }
    targetJvm(compilerOptions)
}

dependencies {
    testImplementation(library("kotlin-test-junit5"))
    testImplementation(library("junit-jupiter"))
    testRuntimeOnly(library("junit-platform-launcher"))
}
