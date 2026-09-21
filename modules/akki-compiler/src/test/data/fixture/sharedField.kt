package fixture

import io.akki.*

class Both {
    val viaOf: Logger = Log.of<Both>()

    fun same(): Boolean = viaOf === log
}

fun box(): String =
    Both::class.java.declaredFields.map { it.name }.sorted().joinToString(",") + "|" + Both().same()
