import akki.buildlogic.generateVersionConstant
import akki.buildlogic.jvmClassTest
import akki.buildlogic.onTargetJdk
import akki.buildlogic.outsideCheck
import org.gradle.api.tasks.testing.Test

plugins {
    id("akki.kotlin-multiplatform")
    id("akki.publishing")
    id("org.jetbrains.kotlinx.kover")
}

description = "Kotlin logging where the compiler names the logger: the log intrinsic, the Log factory and the backend SPI"

generateVersionConstant(packageName = "io.akki.internal", sourceSet = "commonMain")

kotlin {
    explicitApi()

    sourceSets {
        commonTest.dependencies {
            implementation(project(":akki-test"))
        }

        jvmTest.dependencies {
            implementation(libs.kotlin.metadata.jvm)
            implementation(libs.lincheck)
        }
    }
}

val lincheckPattern = "*LincheckTest"
val jvmTest = tasks.named<Test>("jvmTest") {
    filter { excludeTestsMatching(lincheckPattern) }
}

jvmClassTest(jvmTest)

val lincheck = tasks.register("lincheck") {
    group = "verification"
    description = "Runs the Lincheck tests outside check: a stall over 30 s on a loaded machine fails them"
}

val lincheckTest = tasks.register<Test>("lincheckTest") {
    group = "verification"
    description = "Checks concurrent backend replacement and logger binding with Lincheck"
    testClassesDirs = jvmTest.get().testClassesDirs
    classpath = jvmTest.get().classpath
    filter { includeTestsMatching(lincheckPattern) }
    outsideCheck(lincheck)
}

lincheck {
    dependsOn(lincheckTest)
}

kover {
    currentProject {
        instrumentation {
            disabledForTestTasks.addAll(lincheckTest.name, onTargetJdk(lincheckTest.name))
        }
    }
}
