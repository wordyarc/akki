package io.akki.compiler.jar

import java.net.URLClassLoader
import java.nio.file.Path

internal fun linkageFailures(classes: Set<String>, classpath: List<Path>): List<String> {
    val urls = classpath.map { it.toUri().toURL() }.toTypedArray()
    return URLClassLoader(urls, ClassLoader.getPlatformClassLoader()).use { loader ->
        classes.sorted().mapNotNull { name ->
            try {
                Class.forName(name.replace('/', '.'), false, loader).declaredMethods
                null
            } catch (failure: LinkageError) {
                "$name: $failure"
            }
        }
    }
}
