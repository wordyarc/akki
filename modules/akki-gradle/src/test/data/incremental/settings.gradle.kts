pluginManagement {
    repositories {
        maven { url = uri("repository") }
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositories {
        maven { url = uri("repository") }
        mavenCentral()
    }
}

rootProject.name = "incremental-consumer"
include(":library", ":plugged", ":plain")
