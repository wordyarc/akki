package sample

import io.akki.log

class Holder {
    private val stored = log

    fun name(): String = stored.name
}
