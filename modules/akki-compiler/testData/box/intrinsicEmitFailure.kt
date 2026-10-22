@file:OptIn(io.akki.InternalAkkiApi::class)

package fixture

import io.akki.backend.LogBackend
import io.akki.backend.LoggerBinding
import io.akki.backend.Sink
import io.akki.internal.LogScope
import io.akki.internal.withLogScope
import io.akki.log
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

fun box(): String {
    val failure = UnsupportedOperationException("emit failed")
    val backend = LogBackend { LoggerBinding { Sink { _, _, _ -> throw failure } } }
    withLogScope(LogScope(backend)) {
        assertSame(failure, assertFailsWith<UnsupportedOperationException> { log.info("eager") })
        assertSame(failure, assertFailsWith<UnsupportedOperationException> { log.info { "lazy" } })
    }
    return "OK"
}
