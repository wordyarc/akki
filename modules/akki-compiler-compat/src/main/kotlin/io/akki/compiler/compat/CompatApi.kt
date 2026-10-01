package io.akki.compiler.compat

@Target(AnnotationTarget.FUNCTION)
internal annotation class CompatApi(val since: String, val change: String)
