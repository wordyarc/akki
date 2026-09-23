package consumer

import io.akki.test.recordLogs
import kotlin.test.Test
import kotlin.test.assertEquals

class SharedServiceTest {
    @Test
    fun records() {
        val records = recordLogs { SharedService().handle() }

        assertEquals(1, records.size)
        assertEquals("consumer.SharedService", records.single().name)
        assertEquals("received from commonMain", records.single().message)
    }
}
