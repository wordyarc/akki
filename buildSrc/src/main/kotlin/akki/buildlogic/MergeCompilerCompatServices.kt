package akki.buildlogic

import java.io.File
import java.util.zip.ZipFile
import javax.inject.Inject
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileSystemOperations
import org.gradle.api.provider.Property
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction

@CacheableTask
abstract class MergeCompilerCompatServices : DefaultTask() {
    @get:Classpath
    abstract val adapterJars: ConfigurableFileCollection

    @get:Input
    abstract val service: Property<String>

    @get:Input
    abstract val expectedProviders: SetProperty<String>

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @get:Inject
    protected abstract val files: FileSystemOperations

    @TaskAction
    fun merge() {
        val expected = expectedProviders.get()
        val declared = adapterJars.files.flatMap(::providersOf)
        val unknown = declared.filterNot(expected::contains)
        if (unknown.isNotEmpty()) fail("providers $unknown are not declared in $COMPILER_ADAPTERS_MANIFEST")
        val missing = expected - declared.toSet()
        if (missing.isNotEmpty()) fail("no adapter jar provides $missing")
        files.delete { delete(outputDirectory) }
        outputDirectory.file(service).get().asFile
            .writeGenerated(declared.distinct().sorted().joinToString(separator = "") { "$it\n" })
    }

    private fun providersOf(jar: File): List<String> {
        val descriptor = ZipFile(jar).use { zip ->
            val entry = zip.getEntry(service.get()) ?: fail("${jar.name} has no ${service.get()}")
            zip.getInputStream(entry).use { it.readBytes().toString(Charsets.UTF_8) }
        }
        return descriptor.lineSequence()
            .map { it.substringBefore('#').trim() }
            .filter(String::isNotEmpty)
            .onEach { if (!isQualifiedClassName(it)) fail("${jar.name} declares '$it', which is not a class name") }
            .toList()
            .ifEmpty { fail("${jar.name} declares no providers in ${service.get()}") }
    }

    private fun fail(problem: String): Nothing = throw GradleException("Cannot merge compiler adapters: $problem")
}
