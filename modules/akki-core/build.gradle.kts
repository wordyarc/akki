import org.gradle.api.tasks.testing.Test

plugins {
    id("akki.kotlin-multiplatform")
}

kotlin {
    explicitApi()

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
            implementation(libs.slf4j.api)
            runtimeOnly(libs.junit.platform.launcher)
            runtimeOnly(libs.slf4j.simple)
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
