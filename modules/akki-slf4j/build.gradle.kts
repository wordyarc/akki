import akki.buildlogic.ClasspathSystemProperty
import com.vanniktech.maven.publish.JavadocJar
import com.vanniktech.maven.publish.KotlinJvm

plugins {
    id("akki.kotlin-jvm")
    id("akki.publishing")
    id("org.jetbrains.kotlinx.kover")
}

description = "SLF4J 2 backend for akki"

kotlin {
    explicitApi()
}

mavenPublishing {
    configure(KotlinJvm(javadocJar = JavadocJar.Empty()))
}

val nopProvider: Configuration = configurations.create("nopProvider") {
    isCanBeConsumed = false
    isCanBeResolved = true
    isTransitive = false
}

dependencies {
    api(project(":akki-core"))
    compileOnly(libs.slf4j.api)
    testImplementation(project(":test-utils"))
    testImplementation(libs.logback.classic)
    nopProvider(libs.slf4j.nop)
}

tasks.test {
    jvmArgumentProviders.add(ClasspathSystemProperty("akki.slf4j.nop.classpath", nopProvider))
}
