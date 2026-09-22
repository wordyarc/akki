@file:JvmMultifileClass
@file:JvmName("Orders")

package fixture

import io.akki.*
import java.lang.invoke.MethodHandles

fun probe(): Logger = log

fun box(): String {
    val runtime = Log.of(MethodHandles.lookup().lookupClass())
    return listOf(probe().name, runtime.name, probe() === runtime).joinToString(",")
}
