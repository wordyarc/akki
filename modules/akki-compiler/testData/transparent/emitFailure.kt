package fixture

import io.akki.Log
import io.akki.LogScope
import io.akki.backend.LogBackend
import io.akki.backend.LoggerBinding
import io.akki.backend.Sink
import io.akki.withLogScope
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

fun box(): String {
    val failure = UnsupportedOperationException("emit failed")
    val backend = LogBackend { LoggerBinding { Sink { _, _, _ -> throw failure } } }
    withLogScope(LogScope(backend)) {
        assertSame(failure, assertFailsWith<UnsupportedOperationException> { Log.named("failing").info("eager") })
        assertSame(failure, assertFailsWith<UnsupportedOperationException> { Log.named("failing").info { "lazy" } })
    }
    return "OK"
}
