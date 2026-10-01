package akki.buildlogic

import javax.inject.Inject
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileSystemOperations
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction

@CacheableTask
abstract class GenerateCompilerCompatProvider : DefaultTask() {
    @get:Input
    abstract val minVersion: Property<String>

    @get:Input
    abstract val implementationClassName: Property<String>

    @get:OutputDirectory
    abstract val sourceDirectory: DirectoryProperty

    @get:OutputDirectory
    abstract val resourceDirectory: DirectoryProperty

    @get:Inject
    protected abstract val files: FileSystemOperations

    @TaskAction
    fun generate() {
        files.delete { delete(sourceDirectory, resourceDirectory) }
        val implementation = implementationClassName.get()
        val factory = factoryClassNameOf(implementation)
        val packageName = factory.substringBeforeLast('.')
        val name = factory.substringAfterLast('.')
        sourceDirectory.file("${factory.replace('.', '/')}.kt").get().asFile.writeGenerated(
            """
            |package $packageName
            |
            |import io.akki.compiler.compat.CompilerCompat
            |
            |public class $name : CompilerCompat.Factory {
            |    override val minVersion: String = "${minVersion.get()}"
            |
            |    override fun create(): CompilerCompat = ${implementation.substringAfterLast('.')}()
            |}
            |
            """.trimMargin(),
        )
        resourceDirectory.file(COMPILER_COMPAT_SERVICE).get().asFile.writeGenerated("$factory\n")
    }
}
