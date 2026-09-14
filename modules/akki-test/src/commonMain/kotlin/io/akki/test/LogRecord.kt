package io.akki.test

import io.akki.Level

public class LogRecord(
    public val name: String,
    public val level: Level,
    public val message: String,
    public val cause: Throwable? = null,
    fields: Map<String, Any?> = emptyMap(),
) {
    public val fields: Map<String, Any?> = fields.toMap()

    override fun equals(other: Any?): Boolean =
        this === other || (
            other is LogRecord &&
                name == other.name &&
                level == other.level &&
                message == other.message &&
                cause == other.cause &&
                fields == other.fields
            )

    override fun hashCode(): Int {
        var result = name.hashCode()
        result = 31 * result + level.hashCode()
        result = 31 * result + message.hashCode()
        result = 31 * result + (cause?.hashCode() ?: 0)
        result = 31 * result + fields.hashCode()
        return result
    }

    override fun toString(): String =
        "LogRecord(name=$name, level=$level, message=$message, cause=$cause, fields=$fields)"
}

public class Resolution(
    public val name: String,
    public val level: Level,
) {
    override fun equals(other: Any?): Boolean =
        this === other || (other is Resolution && name == other.name && level == other.level)

    override fun hashCode(): Int = 31 * name.hashCode() + level.hashCode()

    override fun toString(): String = "Resolution(name=$name, level=$level)"
}
