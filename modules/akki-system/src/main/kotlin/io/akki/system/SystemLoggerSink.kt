@file:OptIn(InternalAkkiApi::class)

package io.akki.system

import io.akki.InternalAkkiApi
import io.akki.LogContext
import io.akki.backend.Sink
import io.akki.internal.forEachField
import java.util.ResourceBundle
import java.lang.System.Logger.Level as SystemLevel

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
    val context = LogContext.current()
    if (context.fields.isEmpty() && fields.isEmpty()) return message
    return buildString {
        append(message)
        var separator = " {"
        context.forEachField(fields) { key, value ->
            append(separator).append(key).append('=').append(value)
            separator = ", "
        }
        append('}')
    }
}
