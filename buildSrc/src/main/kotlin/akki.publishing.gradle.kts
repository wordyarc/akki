plugins {
    id("akki.base")
    `maven-publish`
}

val testRepository = layout.buildDirectory.dir("publications/test-repository")

publishing {
    repositories {
        maven {
            name = "test"
            url = uri(testRepository)
        }
    }
}

val cleanTestRepository = tasks.register<Delete>("cleanTestRepository") {
    delete(testRepository)
}

tasks.withType<PublishToMavenRepository>().configureEach {
    if (name.endsWith("ToTestRepository")) dependsOn(cleanTestRepository)
}
