package io.akki.gradle

import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault

@DisableCachingByDefault(because = "Not worth caching")
internal abstract class WriteAkkiProperties : DefaultTask() {
    @get:Input
    @get:Optional
    abstract val loggerNameStyle: Property<LoggerNameStyle>

    @get:Input
    @get:Optional
    abstract val backend: Property<String>

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @TaskAction
    fun write() {
        val directory = outputDirectory.get().asFile
        directory.deleteRecursively()
        directory.mkdirs()
        val properties = listOfNotNull(
            loggerNameStyle.orNull?.let { "$LOGGER_NAME_STYLE_PROPERTY=${it.name.lowercase().replace('_', '-')}" },
            backend.orNull?.let { "$BACKEND_PROPERTY=$it" },
        )
        if (properties.isEmpty()) return
        val file = directory.resolve(AKKI_PROPERTIES_RESOURCE)
        file.parentFile.mkdirs()
        file.writeText(properties.joinToString("\n", postfix = "\n"))
    }

    companion object {
        const val AKKI_PROPERTIES_RESOURCE: String = "META-INF/akki.properties"
        const val LOGGER_NAME_STYLE_PROPERTY: String = "io.akki.loggerNameStyle"
        const val BACKEND_PROPERTY: String = "io.akki.backend"

        fun name(sourceSet: String): String =
            "write${sourceSet.replaceFirstChar(Char::uppercaseChar)}AkkiProperties"
    }
}
