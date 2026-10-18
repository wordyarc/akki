package io.akki

import io.akki.internal.AKKI_PROPERTIES_RESOURCE
import java.io.File
import java.net.URL
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText

internal fun probe(
    main: Class<*>,
    properties: Map<String, String> = emptyMap(),
    resources: List<Path> = emptyList(),
): Pair<Int, String> {
    val java = ProcessHandle.current().info().command().orElseThrow()
    val classpath = (resources.map { it.toString() } + System.getProperty("java.class.path"))
        .joinToString(File.pathSeparator)
    val command = listOf(java) + properties.map { (name, value) -> "-D$name=$value" } + listOf("-cp", classpath, main.name)
    val process = ProcessBuilder(command).redirectErrorStream(true).start()
    val output = process.inputStream.bufferedReader().readText()
    return process.waitFor() to output
}

internal fun Path.akkiProperties(vararg properties: Pair<String, String>): Path {
    resolve(AKKI_PROPERTIES_RESOURCE).also { it.parent.createDirectories() }
        .writeText(properties.joinToString("\n", postfix = "\n") { (name, value) -> "$name=$value" })
    return this
}

internal fun Path.akkiPropertiesUrl(): URL = resolve(AKKI_PROPERTIES_RESOURCE).toRealPath().toFile().toURI().toURL()
