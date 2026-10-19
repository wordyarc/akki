package io.akki.gradle

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
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

    @get:Input
    abstract val includeSlf4jBackend: Property<Boolean>

    @get:Input
    @get:Optional
    abstract val slf4jContext: Property<Slf4jContext>

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @TaskAction
    fun write() {
        val directory = outputDirectory.get().asFile
        directory.deleteRecursively()
        directory.mkdirs()
        val properties = listOfNotNull(
            loggerNameStyle.orNull?.let { "$LOGGER_NAME_STYLE_PROPERTY=${it.propertyValue()}" },
            backend.orNull?.let { "$BACKEND_PROPERTY=${validated(it)}" },
            slf4jContext.orNull?.let { "$SLF4J_CONTEXT_PROPERTY=${validated(it).propertyValue()}" },
        )
        if (properties.isEmpty()) return
        val file = directory.resolve(AKKI_PROPERTIES_RESOURCE)
        file.parentFile.mkdirs()
        file.writeText(properties.joinToString("\n", postfix = "\n"))
    }

    private fun validated(backend: String): String {
        if (!backend.isClassName()) {
            throw GradleException(
                "akki: '$backend' in akki.jvm.backend is not a class name: expected the class name of a LogBackend, " +
                    "for example ${AkkiJvmOptions.SLF4J_BACKEND}",
            )
        }
        if (backend == AkkiJvmOptions.SLF4J_BACKEND && !includeSlf4jBackend.get()) {
            throw GradleException(
                "akki: akki.jvm.backend selects akki-slf4j, but akki.jvm.includeSlf4jBackend is false. " +
                    "Include akki-slf4j or select another backend",
            )
        }
        return backend
    }

    private fun validated(context: Slf4jContext): Slf4jContext {
        if (!includeSlf4jBackend.get()) {
            throw GradleException(
                "akki: akki.jvm.slf4jContext configures akki-slf4j, but akki.jvm.includeSlf4jBackend is false. " +
                    "Include akki-slf4j or remove the setting",
            )
        }
        return context
    }

    private fun Enum<*>.propertyValue(): String = name.lowercase().replace('_', '-')

    private fun String.isClassName(): Boolean = split('.').all { segment ->
        segment.isNotEmpty() && segment.first().isJavaIdentifierStart() && segment.all { it.isJavaIdentifierPart() }
    }

    companion object {
        const val AKKI_PROPERTIES_RESOURCE: String = "META-INF/akki.properties"
        const val LOGGER_NAME_STYLE_PROPERTY: String = "io.akki.loggerNameStyle"
        const val BACKEND_PROPERTY: String = "io.akki.backend"
        const val SLF4J_CONTEXT_PROPERTY: String = "io.akki.slf4j.context"

        fun name(sourceSet: String): String =
            "write${sourceSet.replaceFirstChar(Char::uppercaseChar)}AkkiProperties"
    }
}
