package dev.ashenarx.lokki

@Target(AnnotationTarget.CLASS, AnnotationTarget.FILE)
@Retention(AnnotationRetention.RUNTIME)
public annotation class LogName(public val value: String)
