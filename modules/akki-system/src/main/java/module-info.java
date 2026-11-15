module io.akki.system {
    requires transitive io.akki.core;

    exports io.akki.system;

    provides io.akki.internal.LogBackendFactory with io.akki.system.SystemLoggerBackendFactory;
}
