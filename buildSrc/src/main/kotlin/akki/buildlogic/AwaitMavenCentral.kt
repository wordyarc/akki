package akki.buildlogic

import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import org.gradle.api.DefaultTask
import org.gradle.api.provider.ListProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction

abstract class AwaitMavenCentral : DefaultTask() {
    @get:Input
    abstract val poms: ListProperty<String>

    @TaskAction
    fun await() {
        poms.get().map { URI("$MAVEN_CENTRAL/$it").toURL() }.forEach { pom ->
            while (!serves(pom)) Thread.sleep(POLL_INTERVAL_MILLIS)
        }
    }

    private fun serves(pom: URL): Boolean {
        val connection = pom.openConnection() as HttpURLConnection
        connection.requestMethod = "HEAD"
        connection.connectTimeout = REQUEST_TIMEOUT_MILLIS
        connection.readTimeout = REQUEST_TIMEOUT_MILLIS
        val status = runCatching { connection.responseCode }
        connection.disconnect()
        if (status.getOrNull() == HttpURLConnection.HTTP_OK) return true
        logger.lifecycle("Waiting for {}: {}", pom, status.fold({ "HTTP $it" }, { it.toString() }))
        return false
    }

    private companion object {
        const val MAVEN_CENTRAL: String = "https://repo1.maven.org/maven2"
        const val POLL_INTERVAL_MILLIS: Long = 30_000
        const val REQUEST_TIMEOUT_MILLIS: Int = 30_000
    }
}
