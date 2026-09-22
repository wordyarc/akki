import akki.buildlogic.WriteVersionConstant
import org.gradle.api.tasks.testing.Test

plugins {
    id("akki.kotlin-multiplatform")
    id("org.jetbrains.kotlinx.kover")
}

val writeVersionConstant = tasks.register<WriteVersionConstant>("writeVersionConstant") {
    packageName = "io.akki.internal"
    version = project.version.toString()
    outputDirectory = layout.buildDirectory.dir("generated/source/version")
}

kotlin {
    explicitApi()

    sourceSets.commonMain {
        kotlin.srcDir(writeVersionConstant)
    }

    jvm {
        testRuns.configureEach {
            executionTask.configure {
                systemProperty("io.akki.loggerNameStyle", "source")
            }
        }
    }

    sourceSets {
        jvmMain.dependencies {
            implementation(libs.kotlin.metadata.jvm)
        }

        commonTest.dependencies {
            implementation(project(":akki-test"))
        }

        jvmTest.dependencies {
            implementation(libs.kotlin.test.junit5)
            implementation(libs.junit.jupiter)
            runtimeOnly(libs.junit.platform.launcher)
        }
    }
}

val jvmTest = tasks.named<Test>("jvmTest")
val jvmClassTest = tasks.register<Test>("jvmClassTest") {
    group = "verification"
    description = "Runs JVM tests with JVM_CLASS logger names"
    testClassesDirs = jvmTest.get().testClassesDirs
    classpath = jvmTest.get().classpath
    systemProperty("io.akki.loggerNameStyle", "jvm-class")
    shouldRunAfter(jvmTest)
}

tasks.named("check") {
    dependsOn(jvmClassTest)
}
