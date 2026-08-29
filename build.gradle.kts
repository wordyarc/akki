plugins {
    base
}

group = providers.gradleProperty("lokki.group").get()
version = providers.gradleProperty("lokki.version").get()
