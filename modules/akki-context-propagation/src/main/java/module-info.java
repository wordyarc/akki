module io.akki.context.propagation {
    requires transitive io.akki.core;
    requires static context.propagation;

    exports io.akki.context.propagation;

    provides io.micrometer.context.ThreadLocalAccessor with io.akki.context.propagation.LogContextAccessor;
}
