import akki.buildlogic.compileModuleDescriptor
import akki.buildlogic.jvmModuleName
import akki.buildlogic.library
import akki.buildlogic.targetJvm
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("akki.versioning")
    id("org.jetbrains.kotlin.multiplatform")
    id("akki.testing")
}

kotlin {
    compilerOptions {
        allWarningsAsErrors = true
        freeCompilerArgs.addAll("-progressive", "-Xexpect-actual-classes")
    }

    jvm {
        targetJvm(compilerOptions)
        compilations.named("main") {
            compileTaskProvider.configure {
                compilerOptions.moduleName = jvmModuleName
            }
        }
    }

    sourceSets {
        commonTest.dependencies {
            implementation(library("kotlin-test"))
        }

        jvmTest.dependencies {
            implementation(library("kotlin-test-junit5"))
            implementation(library("junit-jupiter"))
            runtimeOnly(library("junit-platform-launcher"))
        }
    }
}

afterEvaluate {
    if ("compileJvmMainJava" in tasks.names) {
        compileModuleDescriptor(tasks.named<JavaCompile>("compileJvmMainJava"), tasks.named<KotlinCompile>("compileKotlinJvm"))
    }
}
