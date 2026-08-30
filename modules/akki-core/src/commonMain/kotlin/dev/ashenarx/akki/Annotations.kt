package dev.ashenarx.akki

@Target(AnnotationTarget.CLASS, AnnotationTarget.FILE)
@Retention(AnnotationRetention.RUNTIME)
public annotation class LogName(public val value: String)
