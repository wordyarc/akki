// BACKEND_SERVICES: fixture.InstallingBackend
@file:OptIn(io.akki.DelicateAkkiApi::class)

package fixture

import io.akki.Log
import io.akki.backend.LogBackend
import io.akki.backend.LoggerBinding
import io.akki.test.RecordingBackend
import io.akki.test.withBackend
import kotlin.test.assertEquals

private val transient = RecordingBackend()
private val installed = RecordingBackend()
private val discovered = RecordingBackend()
private lateinit var installation: Log.Installation

class InstallingBackend : LogBackend {
    init {
        withBackend(transient) { Log.named("bootstrap").info("transient") }
        installation = Log.install(installed)
        Log.named("bootstrap").info("installed")
    }

    override fun bind(name: String): LoggerBinding = discovered.bind(name)
}

fun box(): String {
    val logger = Log.named("bootstrap")
    logger.info("application")
    try {
        assertEquals(listOf("transient"), transient.records.map { it.message })
        assertEquals(listOf("installed", "application"), installed.records.map { it.message })
        assertEquals(emptyList(), discovered.records)
    } finally {
        installation.close()
    }
    logger.info("restored")
    assertEquals(listOf("restored"), discovered.records.map { it.message })
    return "OK"
}
