package io.akki.compiler.compat

public abstract class ReflectiveCompilerCompatFactory(
    final override val minVersion: String,
    private val implementationClassName: String,
) : CompilerCompat.Factory {
    final override fun create(): CompilerCompat {
        val implementationClass = Class.forName(
            implementationClassName,
            true,
            javaClass.classLoader,
        ).asSubclass(CompilerCompat::class.java)

        return implementationClass.getDeclaredConstructor().newInstance()
    }
}
