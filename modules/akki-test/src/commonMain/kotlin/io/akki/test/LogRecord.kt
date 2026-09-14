package io.akki.test

import io.akki.Level

public data class LogRecord(
    public val name: String,
    public val level: Level,
    public val message: String,
    public val cause: Throwable? = null,
    public val fields: Map<String, Any?> = emptyMap(),
)

public data class Resolution(
    public val name: String,
    public val level: Level,
)
