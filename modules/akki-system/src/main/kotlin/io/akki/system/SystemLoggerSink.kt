package io.akki.system

import io.akki.LogContext
import io.akki.backend.Sink
import java.lang.System.Logger.Level as SystemLevel
import java.util.ResourceBundle

internal class SystemLoggerSink(
    private val logger: System.Logger,
    private val level: SystemLevel,
) : Sink, System.Logger {
    override fun emit(message: String, cause: Throwable?, fields: Map<String, Any?>) {
        logger.log(level, null as ResourceBundle?, withFields(message, fields), cause)
    }

    override fun getName(): String = logger.name

    override fun isLoggable(level: SystemLevel): Boolean = logger.isLoggable(level)

    override fun log(level: SystemLevel, bundle: ResourceBundle?, msg: String?, thrown: Throwable?) {
        logger.log(level, bundle, msg, thrown)
    }

    override fun log(level: SystemLevel, bundle: ResourceBundle?, format: String?, vararg params: Any?) {
        logger.log(level, bundle, format, *params)
    }
}

private fun withFields(message: String, fields: Map<String, Any?>): String {
    val context = LogContext.current().fields
    if (context.isEmpty() && fields.isEmpty()) return message
    val entries = context.entries.filter { it.key !in fields } + fields.entries
    return entries.joinToString(prefix = "$message {", postfix = "}") { (key, value) -> "$key=$value" }
}
