import akki.buildlogic.WriteVersionConstant
import org.gradle.api.tasks.testing.Test

plugins {
    id("akki.kotlin-multiplatform")
    id("akki.publishing")
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
            implementation(libs.lincheck)
            runtimeOnly(libs.junit.platform.launcher)
        }
    }
}

val lincheckPattern = "*JvmBackendLincheckTest"
val jvmTest = tasks.named<Test>("jvmTest") {
    filter { excludeTestsMatching(lincheckPattern) }
}
val jvmClassTest = tasks.register<Test>("jvmClassTest") {
    group = "verification"
    description = "Runs JVM tests with JVM_CLASS logger names"
    testClassesDirs = jvmTest.get().testClassesDirs
    classpath = jvmTest.get().classpath
    systemProperty("io.akki.loggerNameStyle", "jvm-class")
    filter { excludeTestsMatching(lincheckPattern) }
    shouldRunAfter(jvmTest)
}

val lincheckTest = tasks.register<Test>("lincheckTest") {
    group = "verification"
    description = "Checks concurrent backend replacement and logger binding with Lincheck"
    testClassesDirs = jvmTest.get().testClassesDirs
    classpath = jvmTest.get().classpath
    filter { includeTestsMatching(lincheckPattern) }
    shouldRunAfter(jvmClassTest)
}

kover {
    currentProject {
        instrumentation {
            disabledForTestTasks.addAll("lincheckTest", "lincheckTestOnJdk17")
        }
    }
}

tasks.named("check") {
    dependsOn(jvmClassTest, lincheckTest)
}
