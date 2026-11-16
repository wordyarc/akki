# akki

[![Kotlin](https://img.shields.io/badge/kotlin-2.4.21-blue.svg?logo=kotlin)](https://kotlinlang.org)
[![GitHub license](https://img.shields.io/badge/license-Apache%20License%202.0-blue.svg?style=flat)](https://www.apache.org/licenses/LICENSE-2.0)
[![Build](https://github.com/wordyarc/akki/actions/workflows/build.yml/badge.svg?branch=master)](https://github.com/wordyarc/akki/actions/workflows/build.yml)
[![Maven Central](https://img.shields.io/maven-central/v/io.github.wordyarc/akki-core)](https://central.sonatype.com/artifact/io.github.wordyarc/akki-core)
[![Gradle Plugin Portal](https://img.shields.io/gradle-plugin-portal/v/io.github.wordyarc.akki)](https://plugins.gradle.org/plugin/io.github.wordyarc.akki)

Logging for Kotlin without logger declarations. `log` is the logger of the enclosing class or file, and a compiler
plugin resolves it at compile time.

```kotlin
import io.akki.log

class JobProcessor {
    fun run(job: Job) {
        log.info { "started ${job.id}" }
    }
}
```

For a `JobProcessor` in `com.example`, the compiler plugin turns `log` into a read of a static field that holds the
`com.example.JobProcessor` logger.

* Logging calls go to the backend directly, so it reports the line of the calling code.
* A message lambda runs only when the level is enabled. Records carry structured fields and a per-thread
  [context](#context).
* A compile-time threshold removes records below a level from the bytecode.
* Records go to SLF4J 2, to `System.Logger` or to your own `LogBackend`.
* `akki-test` captures records in tests.

## Requirements

* Kotlin `2.4.0` or a newer release listed in [Kotlin compatibility](#kotlin-compatibility).
* JDK 17 or newer. Only JVM compilations are supported.

## Setup

### Gradle

```kotlin
plugins {
    kotlin("jvm") version "2.4.21"
    id("io.github.wordyarc.akki") version "0.4.0"
}
```

The Gradle plugin applies the compiler plugin to every JVM compilation and adds `akki-core` and `akki-slf4j`. Records
go to the SLF4J 2 provider of the application, such as logback-classic. Without a provider, akki prints a one-time
notice and writes records at `INFO` and higher to stderr. [Backends](#backends) lists the alternatives.

Other akki modules take the version of the Gradle plugin. With `akki-bom` as a platform they are declared without one:

```kotlin
dependencies {
    testImplementation(platform("io.github.wordyarc:akki-bom:0.4.0"))
    testImplementation("io.github.wordyarc:akki-test")
    testImplementation("io.github.wordyarc:akki-test-coroutines")
}
```

The Gradle plugin compiles against every akki module at its own version, and a newer one declared directly fails the
build. The runtime classpath is aligned by `akki-bom`, which every akki module depends on.

### Command line

Pass the `akki-compiler` jar to the compiler with `-Xplugin` and its options with `-P`. The compilation needs
`akki-core` on the classpath, and a backend module goes on the runtime classpath. The compiler plugin reports an
`akki-core` of a different version.

```shell
kotlinc -Xplugin=akki-compiler.jar -P plugin:io.akki:minLevel=info -classpath akki-core-jvm.jar -d out src
```

## Usage

### Logging

`Logger` has `trace`, `debug`, `info`, `warn` and `error`. Each takes the message as a string or as a lambda, with an
optional cause and fields.

```kotlin
log.info("started")
log.debug { "queue: ${queue.dump()}" }      // the lambda runs only when the level is enabled
log.error(cause) { "job ${job.id} failed" }
log.warn(cause, mapOf("jobId" to job.id, "attempt" to attempt)) { "retrying" }

if (log.isEnabled(Level.TRACE)) { /* ... */ }
```

Only the lambda is lazy. The other arguments, including `fields`, are evaluated even when the level is disabled.

The lambda has the type `context(RecordScope) () -> String`, so a function of type `() -> String` is not a message:
call it in the lambda, `log.info { message() }`. A module with a language version below `2.4` calls the lambda
overloads only with the `-Xcontext-parameters` compiler option. From Java, pass `record -> "message"` and pass fields
as the `fields` argument.

Fields written in the lambda are lazy too. This API is experimental and needs `@OptIn(ExperimentalAkkiApi::class)`
and imports of `io.akki.field` and `io.akki.fields`. A value written in the lambda replaces the value of the same key
in the `fields` argument.

```kotlin
log.debug { fields("jobId" to job.id, "queue" to queue.dump()); "polled" }
```

To reuse a set of fields, declare a function with `context(_: RecordScope)` and call it in the lambda. Inside a class
that declares its own `field` or `fields` function with the same parameters, the call goes to that function. The
compiler plugin reports it with the `RECORD_FIELD_SHADOWED` warning; call `io.akki.field(...)` by its qualified name
there.

### Logger names

The nearest enclosing declaration with a qualified name gives the logger its name. Companion objects and enum entry
bodies use the logger of their class. Lambdas, local classes and anonymous classes use the logger of the enclosing
class, or of the file at the top level: top-level functions in `Jobs.kt` log to `com.example.Jobs`.

`logger()` returns the same logger as a value. The `Log` factory names a logger explicitly and works without the
compiler plugin, from Java too:

```kotlin
private val log = logger()                      // the logger of the enclosing declaration
private val log = Log.of<JobProcessor>()
private val log = Log.of(javaClass)             // the runtime class, for abstract base classes
private val history = Log.named("jobs.history")
```

### Context

`LogContext` holds fields that every record written on the current thread carries, such as a request id. Fields of a
record shadow context fields with the same key.

```kotlin
withLogFields("requestId" to request.id, "tenant" to tenant) {
    log.info { "accepted" }                                 // requestId, tenant
    withLogFields("step" to "validate") { log.debug("ok") } // requestId, tenant, step
    withLogContext(LogContext.EMPTY) { cleanup() }          // no context
}
```

A context is an immutable value: `+` merges contexts or adds a field, `-` removes a key. `withLogContext` replaces
the current context with the given one. Where a block cannot wrap the code, `context.enter()` returns an entry that is
closed on the same thread, innermost first. From Java, create `new LogContext(Map)`, combine contexts with `plus` and
close the entry in try-with-resources.

Other threads start with an empty context. `LogContext.current().wrap(task)` takes a `Runnable`, a `Callable` or a
lambda and runs it in the context of the submitting thread; from Java, call `LogContexts.wrap(context, task)`.

### Coroutines and reactive code

The blocks of `withLogFields` and `withLogContext` cannot suspend. `akki-coroutines` provides a suspending
`withLogFields` in `io.akki.coroutines` and `asContextElement()`; both carry the context across dispatchers. Import
one `withLogFields` explicitly: with star imports of both packages the call is ambiguous.

```kotlin
withLogFields("jobId" to job.id) { run(job) }                         // suspending, from io.akki.coroutines
runBlocking(LogContext.current().asContextElement()) { ... }          // carry the thread's context into coroutines
launch(Dispatchers.IO + LogContext.EMPTY.asContextElement()) { ... }  // background work without the context
```

`enter()` binds a context to the thread, not to the coroutine: in a coroutine that carries a context element, an entry
opened by hand lasts until the next suspension.

`akki-context-propagation` registers a Micrometer `ThreadLocalAccessor` under `LogContextAccessor.KEY`, so Reactor
with automatic context propagation and Spring's `ContextPropagatingTaskDecorator` carry the context. The application
adds Micrometer `context-propagation` itself and, on the module path, declares `requires context.propagation`.

```kotlin
chain.filter(exchange).contextWrite { it.put(LogContextAccessor.KEY, LogContext("requestId" to id)) }
```

### Testing

`recordLogs` from `akki-test` returns the records that the block writes on the current thread, so tests running in
parallel do not see each other's records. For suspending code, import `recordLogs` from `akki-test-coroutines`.

```kotlin
val records = recordLogs { processor.run(Job("nightly-report")) }
assertEquals("started nightly-report", records.single().message)
```

A `LogRecord` has the logger name, the level, the message, the cause, the `fields` of the record and, separately, the
`context` in effect when it was written.

Code that takes a `Logger` as a parameter can get a `RecordingLogger`, which records what every thread writes to it:

```kotlin
val logger = RecordingLogger(minLevel = Level.INFO)
Processor(logger).run(Job("nightly-report"))
assertEquals("started nightly-report", logger.records.single().message)
```

## Backends

| Backend         | Module        | Context and fields                                                                |
|-----------------|---------------|-----------------------------------------------------------------------------------|
| SLF4J 2         | `akki-slf4j`  | key-value pairs, `%kvp` in logback patterns; MDC with `io.akki.slf4j.context=mdc` |
| `System.Logger` | `akki-system` | appended to the message as `{key=value, ...}`                                     |
| Custom          | your own      | fields arrive in `Sink.emit`, the context is read with `LogContext.current()`     |

`akki-system` needs no other library. The JDK routes its records to `java.util.logging`, or to its console logger in
a runtime image without the `java.logging` module, and levels and output are configured there.

A custom backend implements `LogBackend` and is registered with `ServiceLoader` in
`META-INF/services/io.akki.backend.LogBackend`. `LogBackend.filter` wraps a backend with a predicate over the logger
name and the level. A rejected record costs as little as a disabled level: its lambda is not run.

```kotlin
class TenantBackend : LogBackend by Slf4jBackend().filter { name, level ->
    level >= Level.INFO || LogContext.current()["tenant"] == "acme"
}
```

A registered `LogBackend` wins over `akki-slf4j` and `akki-system`. Among several candidates akki takes the first by
class name and names the others on stderr. The `backend` setting overrides this choice. `Log.install(backend)`
replaces the backend for the whole process until the returned installation is closed, and is marked
`@DelicateAkkiApi` for that reason.

## Configuration

`compilerOptions {}` changes the generated code. `jvm {}` holds runtime settings: the Gradle plugin writes them to
`META-INF/akki.properties` in the main resources, so they ship with the application. Every setting is optional.

```kotlin
import io.akki.gradle.AkkiBackend
import io.akki.gradle.LoggerNameStyle
import io.akki.gradle.MinLevel
import io.akki.gradle.Slf4jContext

akki {
    compilerOptions {
        minLevel = MinLevel.INFO                    // remove records below INFO from the bytecode
    }
    jvm {
        loggerNameStyle = LoggerNameStyle.JVM_CLASS // com.example.Outer$Inner and JobsKt
        backend = AkkiBackend.SLF4J
        slf4j {
            context = Slf4jContext.MDC              // write the context and fields to MDC for %X{...} patterns
        }
    }
}
```

With `minLevel`, `isEnabled` with a constant level below the threshold compiles to `false`, and `MinLevel.OFF` removes
every record. The build fails when `slf4j {}` has settings and `backend` selects `akki-system`.

`backend` decides which module the Gradle plugin adds to the runtime classpath and which backend akki uses:

| `backend`                          | Module added  | Backend used                                                          |
|------------------------------------|---------------|-----------------------------------------------------------------------|
| unset                              | `akki-slf4j`  | chosen at run time: a registered `LogBackend`, otherwise `akki-slf4j` |
| `AkkiBackend.SLF4J`                | `akki-slf4j`  | `akki-slf4j`                                                          |
| `AkkiBackend.SYSTEM_LOGGER`        | `akki-system` | `akki-system`                                                         |
| `AkkiBackend.fromClassName("...")` | none          | the `LogBackend` of that class                                        |
| `AkkiBackend.NONE`                 | none          | chosen at run time among the backends on the classpath                |

Outside Gradle, set the runtime settings in `META-INF/akki.properties` on the classpath. A system property with the
same name takes precedence, and an empty one, `-Dio.akki.backend=`, unsets the value of the resource.

| Property                  | Values                                                                 | When unset                                          |
|---------------------------|------------------------------------------------------------------------|-----------------------------------------------------|
| `io.akki.loggerNameStyle` | `source`, `jvm-class`                                                  | `source`                                            |
| `io.akki.backend`         | class name of a registered `LogBackend` or of an akki module's backend | a registered `LogBackend`, otherwise an akki module |
| `io.akki.slf4j.context`   | `key-value-pairs`, `mdc`                                               | `key-value-pairs`                                   |

The class names of the akki backends are `io.akki.slf4j.Slf4jBackend` and `io.akki.system.SystemLoggerBackend`.

A setting never makes logging throw. akki reports an unknown property or value, or a backend that cannot start, to
stderr once, names the source and uses the default. Two resources with different values for one property leave it
unset until a system property sets it. The settings are read once, when the first logger is created or the first
record is written: `System.setProperty` in `main` may come too late.

Runtime settings belong to the application. A library leaves `jvm {}` empty, or sets `AkkiBackend.NONE` to bring no
backend: any other value ships in its jar and applies to every application that uses it.

## Modules

All artifacts are published under the group `io.github.wordyarc` with the same version.

| Module                     | Purpose                                                                        |
|----------------------------|--------------------------------------------------------------------------------|
| `akki-core`                | the logging API: `log`, `Log`, `Logger`, `LogContext`, `LogBackend`            |
| `akki-slf4j`               | backend for SLF4J 2                                                            |
| `akki-system`              | backend for `System.Logger`                                                    |
| `akki-compiler`            | Kotlin compiler plugin, one artifact for every supported Kotlin release        |
| `akki-gradle`              | Gradle plugin `io.github.wordyarc.akki`                                        |
| `akki-test`                | `recordLogs { }`, `RecordingBackend`, `RecordingLogger`                        |
| `akki-coroutines`          | `LogContext` in coroutines                                                     |
| `akki-test-coroutines`     | suspending `recordLogs { }`                                                    |
| `akki-context-propagation` | `LogContext` in Reactor and Spring through Micrometer `context-propagation`    |
| `akki-bom`                 | the version of every akki artifact as a Gradle platform                        |

## Kotlin compatibility

| akki    | Kotlin                                            |
|---------|---------------------------------------------------|
| `0.4.0` | `2.4.0`, `2.4.10`, `2.4.20`, `2.4.21`, `2.5.0-RC` |
| `0.3.0` | `2.4.0`, `2.4.10`, `2.4.20`, `2.4.21`             |
| `0.2.0` | `2.4.0`, `2.4.10`, `2.4.20`                       |
| `0.1.0` | `2.4.x`                                           |

The compiler plugin is tested on the listed releases. Their Beta, RC and dev builds are accepted; IDE and SNAPSHOT
builds are rejected. A compiler older than `2.4.0` fails the compilation with an error that names the oldest
supported version; a newer one that the compiler plugin cannot link against, with an error that names the newest
tested release.

Releases follow [semantic versioning](https://semver.org). The API is experimental: until `1.0`, minor releases may
break compatibility.

## Limitations

* Android needs minSdk 34 and has not been tested. The Gradle plugin adds no backend module and writes no runtime
  settings for Android compilations.
* `log` and `logger()` compile without the compiler plugin but throw when called. Use `Log.of` or `Log.named` there.
* Inline wrappers around `log` produce synthetic line numbers. Calls from Java report `io.akki.Logger` as the caller
  unless that class is registered in logback's `frameworkPackages`.
* Records removed by `minLevel` cannot be restored through runtime logging configuration.
* `akki-system` writes fields as text, and `java.util.logging` reports the class and the method of the caller without
  the line.
* Markers are not supported. With `io.akki.slf4j.context=mdc`, MDC holds the context only while akki writes a record:
  logs that other code writes through SLF4J do not get the fields.
* `Log.named` keeps every logger it creates, so applications need a bounded set of names.
