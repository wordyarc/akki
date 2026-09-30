package io.akki.compiler.compat

import java.lang.reflect.InvocationTargetException
import java.util.ServiceConfigurationError
import java.util.ServiceLoader
import org.jetbrains.kotlin.config.KotlinCompilerVersion

public object CompilerCompatLoader {
    public fun load(trace: (String) -> Unit = {}): CompilerCompat =
        load(KotlinCompilerVersion.getVersion(), CompilerCompat.Factory::class.java.classLoader, trace)

    internal fun load(compiler: String?, classLoader: ClassLoader?, trace: (String) -> Unit = {}): CompilerCompat {
        if (compiler == null) throw CompatLoadException("The Kotlin compiler does not report its version.")
        val current = CompilerVersion.parseCanonical(compiler)
            ?: throw CompatLoadException(
                "Kotlin '$compiler' is not supported. Only stable MAJOR.MINOR.PATCH releases are recognized.",
            )
        val factory = selectFactory(current, discover(classLoader))
        val compat = factory.createAdapter(compiler)
        trace(
            "Kotlin $compiler uses ${compat.javaClass.name}, the compiler adapter for Kotlin ${factory.minVersion} " +
                "created by ${factory.origin}.",
        )
        return compat
    }
}

internal fun selectFactory(
    current: CompilerVersion,
    factories: List<CompilerCompat.Factory>,
): CompilerCompat.Factory {
    if (factories.isEmpty()) {
        throw CompatLoadException("No compiler adapter factories were found on the compiler plugin classpath.")
    }

    val candidates = factories.map { factory ->
        val minimum = CompilerVersion.parseCanonical(factory.minVersion)
            ?: throw CompatLoadException(
                "${factory.origin} declares an invalid minimum Kotlin version '${factory.minVersion}'.",
            )
        minimum to factory
    }

    val duplicate = candidates.groupBy { it.first }
        .entries.firstOrNull { it.value.size > 1 }
    if (duplicate != null) {
        val origins = duplicate.value.joinToString { it.second.origin }
        throw CompatLoadException("Several factories declare the minimum Kotlin version ${duplicate.key}: $origins.")
    }

    return candidates.asSequence()
        .filter { (minimum, _) -> minimum <= current }
        .maxByOrNull { (minimum, _) -> minimum }
        ?.second
        ?: throw CompatLoadException(
            "Kotlin $current is not supported. The oldest supported version is ${candidates.minOf { it.first }}.",
        )
}

private fun discover(classLoader: ClassLoader?): List<CompilerCompat.Factory> = try {
    ServiceLoader.load(CompilerCompat.Factory::class.java, classLoader).toList()
} catch (failure: ServiceConfigurationError) {
    throw CompatLoadException("Compiler adapter factories cannot be discovered.", failure)
}

private fun CompilerCompat.Factory.createAdapter(compiler: String): CompilerCompat = try {
    create()
} catch (failure: InvocationTargetException) {
    throw loadFailure(failure.targetException ?: failure, compiler)
} catch (failure: ReflectiveOperationException) {
    throw loadFailure(failure, compiler)
} catch (failure: ClassCastException) {
    throw loadFailure(failure, compiler)
} catch (failure: LinkageError) {
    throw loadFailure(failure, compiler)
}

private fun CompilerCompat.Factory.loadFailure(failure: Throwable, compiler: String): Throwable = when (failure) {
    is LinkageError -> CompatLoadException(
        "The compiler adapter for Kotlin $minVersion created by $origin does not link against Kotlin $compiler.",
        failure,
    )
    is Error -> failure
    else -> CompatLoadException("The compiler adapter for Kotlin $minVersion cannot be created by $origin.", failure)
}

private val CompilerCompat.Factory.origin: String
    get() = "${javaClass.name} from ${javaClass.protectionDomain?.codeSource?.location ?: "an unknown location"}"
