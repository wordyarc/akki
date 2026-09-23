// FILE: A__Shared.kt
package fixture

import io.akki.log

fun first() = log

// FILE: B__Shared.kt
package fixture

import io.akki.log

fun second() = log

// FILE: A__B.kt
@file:JvmMultifileClass
@file:JvmName("Utils__Facade")

package fixture

import io.akki.log

fun multifile() = log

// FILE: Main.kt
package fixture

import io.akki.*
import kotlin.test.assertEquals
import kotlin.test.assertSame

fun box(): String {
    val loggers = listOf(first(), second(), multifile())
    val classes = listOf("fixture.A__SharedKt", "fixture.B__SharedKt", "fixture.Utils__Facade__A__BKt")
    val expected = if (System.getProperty(LOGGER_NAME_STYLE_PROPERTY_NAME) == LOGGER_NAME_STYLE_VALUE_JVM_CLASS) {
        classes
    } else {
        listOf("fixture.A__Shared", "fixture.B__Shared", "fixture.A__B")
    }
    assertEquals(expected, loggers.map { it.name })
    for ((logger, name) in loggers.zip(classes)) {
        val type = Class.forName(name)
        assertSame(logger, Log.of(type))
        assertSame(logger, Log.of(type.kotlin))
    }
    return "OK"
}
