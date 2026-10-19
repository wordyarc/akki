package io.akki.test

import io.akki.Level
import io.akki.LogContext

public class LogRecord(
    public val loggerName: String,
    public val level: Level,
    public val message: String,
    public val cause: Throwable? = null,
    fields: Map<String, Any?> = emptyMap(),
    public val context: LogContext = LogContext.EMPTY,
) {
    public val fields: Map<String, Any?> = fields.toMap()

    override fun equals(other: Any?): Boolean =
        this === other || (
            other is LogRecord &&
                loggerName == other.loggerName &&
                level == other.level &&
                message == other.message &&
                cause == other.cause &&
                fields == other.fields &&
                context == other.context
            )

    override fun hashCode(): Int {
        var result = loggerName.hashCode()
        result = 31 * result + level.hashCode()
        result = 31 * result + message.hashCode()
        result = 31 * result + (cause?.hashCode() ?: 0)
        result = 31 * result + fields.hashCode()
        result = 31 * result + context.hashCode()
        return result
    }

    override fun toString(): String =
        "LogRecord(loggerName=$loggerName, level=$level, message=$message, cause=$cause, fields=$fields, context=$context)"
}
