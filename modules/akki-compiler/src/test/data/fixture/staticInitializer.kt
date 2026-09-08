package fixture

import io.akki.*

private val topLevel: String = log.name

object Holder {
    val member: String = log.name
}

fun box(): String = topLevel + "," + Holder.member
