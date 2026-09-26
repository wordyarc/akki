package consumer

import io.akki.Log
import kotlin.test.Test

class BootstrapTest {
    @Test
    fun printsLoggerName() {
        println("AKKI test name=${Log.of<Owner.Nested>().name}")
    }
}
