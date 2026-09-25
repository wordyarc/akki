module io.akki.slf4j {
    requires transitive io.akki.core;
    requires static org.slf4j;

    exports io.akki.slf4j;

    uses org.slf4j.spi.SLF4JServiceProvider;

    provides io.akki.internal.LogBackendFactory with io.akki.slf4j.Slf4jBackendFactory;
}
