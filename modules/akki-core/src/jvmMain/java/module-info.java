module io.akki.core {
    requires transitive kotlin.stdlib;
    requires kotlin.metadata.jvm;

    exports io.akki;
    exports io.akki.backend;
    exports io.akki.internal;

    uses io.akki.backend.LogBackend;
    uses io.akki.internal.LogBackendFactory;
}
