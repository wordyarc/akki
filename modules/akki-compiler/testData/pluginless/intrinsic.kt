// CHECK_BYTECODE_TEXT
// 2 io/akki/IntrinsicKt\.getLog
// 0 \$\$log
package fixture

import io.akki.*

class Service {
    fun probe(): String = log.name
}

interface Contract {
    fun probe(): String = log.name
}

fun box(): String = "OK"
