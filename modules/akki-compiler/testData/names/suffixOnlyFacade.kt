// FILE: root.kt
@file:JvmName("Kt")

import io.akki.Log
import io.akki.Logger
import io.akki.log
import java.lang.invoke.MethodHandles

fun rootLogger(): Logger = log
fun rootRuntimeLogger(): Logger = Log.of(MethodHandles.lookup().lookupClass())

// FILE: packaged.kt
@file:JvmName("Kt")

package fixture

import io.akki.Log
import io.akki.Logger
import io.akki.log
import java.lang.invoke.MethodHandles

fun packagedLogger(): Logger = log
fun packagedRuntimeLogger(): Logger = Log.of(MethodHandles.lookup().lookupClass())

// FILE: main.kt
import fixture.packagedLogger
import fixture.packagedRuntimeLogger
import kotlin.test.assertEquals
import kotlin.test.assertSame

fun box(): String {
    assertEquals("Kt", rootRuntimeLogger().name)
    assertSame(rootRuntimeLogger(), rootLogger())
    assertEquals("fixture.Kt", packagedRuntimeLogger().name)
    assertSame(packagedRuntimeLogger(), packagedLogger())
    return "OK"
}
