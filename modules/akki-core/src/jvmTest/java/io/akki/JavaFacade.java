package io.akki;

import io.akki.backend.LogBackend;
import java.util.Map;
import java.util.concurrent.Callable;
import kotlin.Pair;

final class JavaFacade {
    private JavaFacade() {
    }

    static Logger ofClass() {
        return Log.of(JavaFacade.class);
    }

    static Logger named(String name) {
        return Log.named(name);
    }

    static boolean isEnabled(Logger log, Level level) {
        return log.isEnabled(level);
    }

    static void eager(Logger log, Throwable cause, Map<String, Object> fields) {
        log.info("eager");
        log.warn("eager with cause", cause);
        log.error("eager with fields", cause, fields);
    }

    static void lazy(Logger log, Throwable cause, Map<String, Object> fields) {
        log.info(() -> "lazy");
        log.warn(cause, () -> "lazy with cause");
        log.error(cause, fields, () -> "lazy with fields");
    }

    static LogContext context(Map<String, Object> fields) {
        return new LogContext(fields);
    }

    static LogContext combined(LogContext left, LogContext right) {
        return left.plus(right);
    }

    static LogContext withField(LogContext context, String key, Object value) {
        return context.plus(new Pair<>(key, value));
    }

    static LogContext without(LogContext context, String key) {
        return context.minus(key);
    }

    static Object field(LogContext context, String key) {
        return context.get(key);
    }

    static LogContext empty() {
        return LogContext.EMPTY;
    }

    static LogContext inside(LogContext context) {
        try (LogContext.Entry entry = context.enter()) {
            return LogContext.current();
        }
    }

    static Runnable wrapped(LogContext context, Runnable task) {
        return LogContexts.wrap(context, task);
    }

    static <T> Callable<T> wrapped(LogContext context, Callable<T> task) {
        return LogContexts.wrap(context, task);
    }

    static BackendInstallation install(LogBackend backend) {
        return Log.install(backend);
    }
}
