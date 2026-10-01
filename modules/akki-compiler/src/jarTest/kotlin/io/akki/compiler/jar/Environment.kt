package io.akki.compiler.jar

import java.io.File
import java.nio.file.Path
import java.util.zip.ZipInputStream
import kotlin.io.path.inputStream
import kotlin.io.path.readLines
import org.jetbrains.kotlin.tooling.core.KotlinToolingVersion

internal const val FACTORY_SERVICE: String = "META-INF/services/io.akki.compiler.compat.CompilerCompat\$Factory"

internal val akkiVersion: String = property("akki.version")

internal val testedKotlin: List<String> = property("akki.kotlin.tested").split(',')

internal val rejectedKotlin: List<String> = property("akki.kotlin.rejected").split(',')

internal val compilerJar: Path = classpath("akki.compiler.jar").single()

internal val adapters: List<Adapter> = classpath("akki.compiler.adapters").single().readLines()
    .map(String::trim)
    .filterNot { it.isEmpty() || it.startsWith('#') }
    .map { Adapter(it.substringBefore('=').trim(), it.substringAfter('=').trim()) }
    .sortedBy { KotlinToolingVersion(it.minVersion) }

internal val checkedKotlin: List<String> =
    (testedKotlin + adapters.map(Adapter::minVersion)).distinct().sortedBy { KotlinToolingVersion(it) }

internal class Adapter(val minVersion: String, val implementation: String) {
    val packageName: String
        get() = implementation.substringBeforeLast('.')

    override fun toString(): String = "$minVersion=$implementation"
}

internal fun adapterOf(kotlin: String): Adapter =
    adapters.last { KotlinToolingVersion(it.minVersion) <= KotlinToolingVersion(kotlin) }

internal fun property(name: String): String = requireNotNull(System.getProperty(name)) { "missing -D$name" }

internal fun classpath(name: String): List<Path> =
    property(name).split(File.pathSeparator).filter(String::isNotEmpty).map(Path::of)

internal fun Path.entries(): List<Pair<String, ByteArray>> = ZipInputStream(inputStream()).use { zip ->
    generateSequence(zip::getNextEntry).filterNot { it.isDirectory }.map { it.name to zip.readBytes() }.toList()
}
