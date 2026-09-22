package fixture

import io.akki.*

annotation class Marker {
    companion object {
        val captured: String = log.name

        fun probe(): String = Log.of<Marker>().name
    }
}

@Marker
class Annotated

fun box(): String = listOf(
    Marker.captured,
    Marker.probe(),
    Annotated::class.java.isAnnotationPresent(Marker::class.java),
    Class.forName("fixture.Marker\$\$Log").declaredFields.single().isSynthetic,
).joinToString(",")
