module io.akki.slf4j {
    requires transitive io.akki.core;
    requires org.slf4j;

    exports io.akki.slf4j;

    provides io.akki.backend.LogBackend with io.akki.slf4j.Slf4jBackend;
}
