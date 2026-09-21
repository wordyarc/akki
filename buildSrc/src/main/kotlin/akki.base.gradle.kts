group = rootProject.group
version = rootProject.version

tasks.withType<Jar>().named { it == "jar" || it == "jvmJar" }.configureEach {
    manifest.attributes("Automatic-Module-Name" to "io.akki.${project.name.removePrefix("akki-")}")
}
