plugins {
    kotlin("jvm")
    `java-library`
    id("@pluginId@")
}

dependencies {
    api("@mavenGroup@:akki-core:@akkiVersion@")
}

akki {
    compilerOptions {
        minLevel.set(providers.gradleProperty("libraryMinLevel").map(io.akki.gradle.MinLevel::valueOf))
    }
}
