package io.akki.compiler.compat

import io.akki.compiler.compat.fixture.FIXTURE_PACKAGE
import java.net.URL
import java.nio.file.Path
import java.util.Collections
import java.util.Enumeration
import kotlin.io.path.writeLines
import kotlin.reflect.KClass

internal class AdapterClassLoader(
    directory: Path,
    providers: List<String>,
    private val forbidden: Set<String> = emptySet(),
) : ClassLoader(AdapterClassLoader::class.java.classLoader) {
    private val descriptor: URL = directory.resolve("providers").writeLines(providers).toUri().toURL()

    private val requests = mutableListOf<String>()

    val requested: List<String>
        get() = synchronized(requests) { requests.toList() }

    override fun loadClass(name: String, resolve: Boolean): Class<*> {
        if (!name.startsWith("$FIXTURE_PACKAGE.")) return super.loadClass(name, resolve)
        synchronized(getClassLoadingLock(name)) {
            findLoadedClass(name)?.let { return it }
            synchronized(requests) { requests += name }
            if (name in forbidden) throw ClassFormatError("$name must not be loaded")
            val bytes = parent.getResourceAsStream("${name.replace('.', '/')}.class")?.use { it.readBytes() }
                ?: throw ClassNotFoundException(name)
            return defineClass(name, bytes, 0, bytes.size)
        }
    }

    override fun getResources(name: String): Enumeration<URL> =
        if (name == SERVICE) Collections.enumeration(listOf(descriptor)) else super.getResources(name)

    companion object {
        private val SERVICE: String = "META-INF/services/${CompilerCompat.Factory::class.java.name}"

        fun of(
            directory: Path,
            vararg providers: KClass<*>,
            forbidden: Set<KClass<*>> = emptySet(),
        ): AdapterClassLoader = AdapterClassLoader(
            directory = directory,
            providers = providers.map { it.java.name },
            forbidden = forbidden.mapTo(mutableSetOf()) { it.java.name },
        )
    }
}
