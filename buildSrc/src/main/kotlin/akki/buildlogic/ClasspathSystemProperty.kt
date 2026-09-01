package akki.buildlogic

import java.io.File
import org.gradle.api.file.FileCollection
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Input
import org.gradle.process.CommandLineArgumentProvider

class ClasspathSystemProperty(
    @get:Input val propertyName: String,
    @get:Classpath val files: FileCollection,
) : CommandLineArgumentProvider {
    override fun asArguments(): Iterable<String> =
        listOf("-D$propertyName=${files.joinToString(File.pathSeparator) { it.absolutePath }}")
}
