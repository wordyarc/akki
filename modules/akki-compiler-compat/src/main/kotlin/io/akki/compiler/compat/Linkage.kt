package io.akki.compiler.compat

public val Throwable.breaksLinkage: Boolean
    get() = when (this) {
        is ExceptionInInitializerError -> false
        is NoClassDefFoundError -> cause !is ExceptionInInitializerError
        else -> this is LinkageError
    }

public val Throwable.linkageBreak: Throwable?
    get() = generateSequence(this, Throwable::cause).firstOrNull(Throwable::breaksLinkage)
