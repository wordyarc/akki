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

dependencies {
    api(project(":akki-core"))
    compileOnly(libs.slf4j.api)
    testImplementation(libs.logback.classic)
}
