package dev.ashenarx.akki.test

import dev.ashenarx.akki.Level

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

public val List<LogRecord>.messages: List<String>
    get() = map(LogRecord::message)

public val List<LogRecord>.levels: List<Level>
    get() = map(LogRecord::level)
