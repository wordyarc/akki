plugins {
    id("lokki.kotlin-jvm")
    `java-gradle-plugin`
}

kotlin {
    explicitApi()
}

gradlePlugin {
    plugins {
        create("lokki") {
            id = providers.gradleProperty("lokki.plugin.id").get()
            displayName = "Lokki"
            description = "Adds the Lokki compiler plugin to Kotlin compilations"
            implementationClass = "dev.ashenarx.lokki.gradle.LokkiGradlePlugin"
        }
    }
}
