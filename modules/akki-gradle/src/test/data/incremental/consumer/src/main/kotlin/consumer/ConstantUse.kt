package consumer

import io.akki.Log
import library.CATEGORY

class ConstantUse {
    fun name(): String = Log.named(CATEGORY).name
}
