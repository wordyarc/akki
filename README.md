# akki

[![Kotlin](https://img.shields.io/badge/kotlin-2.4.21-blue.svg?logo=kotlin)](https://kotlinlang.org)
[![GitHub license](https://img.shields.io/badge/license-Apache%20License%202.0-blue.svg?style=flat)](https://www.apache.org/licenses/LICENSE-2.0)
[![Build](https://github.com/wordyarc/akki/actions/workflows/build.yml/badge.svg?branch=master)](https://github.com/wordyarc/akki/actions/workflows/build.yml)
[![Maven Central](https://img.shields.io/maven-central/v/io.github.wordyarc/akki-core)](https://central.sonatype.com/artifact/io.github.wordyarc/akki-core)
[![Gradle Plugin Portal](https://img.shields.io/gradle-plugin-portal/v/io.github.wordyarc.akki)](https://plugins.gradle.org/plugin/io.github.wordyarc.akki)

Log in Kotlin without declaring a logger. The compiler plugin names the logger after the enclosing declaration.
Requires Kotlin `2.4.0` or newer.

```kotlin
import io.akki.log

class JobProcessor {
    fun run(job: Job) {
        log.info { "started ${job.id}" }
    }
}
```

For a `JobProcessor` in `com.example`, the plugin turns `log` into a read of a static field holding the
`com.example.JobProcessor` logger. It also rewrites logging calls to call the backend directly, preserving the
caller line. An optional compile-time threshold removes records below the selected level from the bytecode.

## Modules

* `akki-core`: the logging API.
  * `log` and `logger()` use the enclosing declaration's logger and require the compiler plugin.
  * `Log.of<T>()` and `Log.named("...")` work without the compiler plugin. From Java, call `Log.of(Class)` or
    `Log.named`.
  * `Logger` provides eager and lazy overloads of `trace`, `debug`, `info`, `warn`, and `error`.
  * `LogBackend` connects a backend; `LogScope` selects one for the current thread.
  * `LogContext` carries fields that every record of the current thread gets.
* `akki-slf4j`: backend for SLF4J 2, enabled when an SLF4J 2 provider is available at runtime.
* `akki-compiler`: Kotlin compiler plugin, one artifact for every supported Kotlin release.
* `akki-gradle`: applies the compiler plugin, adds `akki-core` and `akki-slf4j`, and keeps every akki module at the
  plugin version.
* `akki-test`: captures test logs with `recordLogs { }` or `RecordingBackend`.
* `akki-coroutines`: carries a `LogScope` or a `LogContext` in a coroutine context via `asContextElement()` and
  provides a suspending `withLogFields { }`.
* `akki-test-coroutines`: provides suspending `recordLogs { }` for coroutine tests.
* `akki-context-propagation`: registers a Micrometer `ThreadLocalAccessor` for `LogContext`, so Reactor and Spring
  carry the context themselves.
* `akki-bom`: supplies the version of every akki artifact as a Gradle platform.

## Setup

### Gradle

The Gradle plugin applies the compiler plugin and adds `akki-core` and `akki-slf4j` to every `jvm` compilation:

```kotlin
plugins {
    kotlin("jvm") version "2.4.21"
    id("io.github.wordyarc.akki") version "0.3.0"
}
```

#### Backend

Logging uses the application's SLF4J 2 provider; Spring Boot, for example, includes logback-classic. You can add a
provider explicitly:

```kotlin
dependencies {
    runtimeOnly("ch.qos.logback:logback-classic:1.5.20")
}
```

If no provider is available, akki prints a one-time notice and writes records at `INFO` and higher to stderr.

A library, or an application with its own `LogBackend`, leaves `akki-slf4j` out:

```kotlin
akki {
    jvm {
        includeSlf4jBackend = false
    }
}
```

If a dependency registers its own `LogBackend`, akki prefers it to `akki-slf4j`. The `backend` setting in
[Configuration](#configuration) overrides that choice.

#### Tests

To capture logs in tests:

```kotlin
dependencies {
    testImplementation("io.github.wordyarc:akki-test:0.3.0")
}
```

#### Versions of akki modules

With `akki-bom` as a platform, akki modules are declared without a version; in a Kotlin Multiplatform source set,
write `project.dependencies.platform(...)` instead:

```kotlin
dependencies {
    testImplementation(platform("io.github.wordyarc:akki-bom:0.3.0"))
    testImplementation("io.github.wordyarc:akki-test")
    testImplementation("io.github.wordyarc:akki-test-coroutines")
}
```

In the compilations it applies to, the Gradle plugin resolves every akki module at its own version: a module declared at
an older version is raised, a newer one that a dependency brings is lowered, and a newer one declared directly
fails the build. The pin stays local to the build and never reaches published metadata. Gradle builds without the
plugin still get a single akki version: each akki library depends on `akki-bom`, so Gradle aligns all of them to
the newest akki version in the graph.

### Command line and other build tools

Outside Gradle, pass the `akki-compiler` jar to the compiler with `-Xplugin`, its options with `-P`, and put
`akki-core` and a backend on the classpath yourself. The compiler plugin reports an `akki-core` of a different
version:

```shell
kotlinc -Xplugin=akki-compiler-0.3.0.jar -P plugin:io.akki:minLevel=info -classpath akki-core-jvm-0.3.0.jar -d out src
```

The logger name style and the backend are set with `-D` options or in `META-INF/akki.properties` on the classpath;
see [Configuration](#configuration).

### Versions and Kotlin compatibility

All akki artifacts use the same release version, following [semantic versioning](https://semver.org). Until `1.0`,
minor releases may introduce breaking changes. Patch releases provide fixes and backward-compatible additions;
these can include support for an additional Kotlin release.

The compiler plugin is tested on the Kotlin releases listed below, and Gradle and the command line use the same
`akki-compiler` artifact. Beta and RC builds of Kotlin are accepted too, and a dev build `X.Y.Z-dev-N` counts as
the `X.Y.Z` release. IDE and SNAPSHOT builds are rejected. On an older compiler the compilation fails with an
error that names the oldest supported version.

| akki    | Kotlin                                | Compiler plugin            |
|---------|---------------------------------------|----------------------------|
| `0.3.0` | `2.4.0`, `2.4.10`, `2.4.20`, `2.4.21` | `akki-compiler`            |
| `0.2.0` | `2.4.0`, `2.4.10`, `2.4.20`           | `akki-compiler-kotlin-2.4` |
| `0.1.0` | `2.4.x`                               | `akki-compiler`            |

## Usage

```kotlin
log.debug { "queue: ${queue.dump()}" }      // the lambda runs only when the level is enabled
log.error(cause) { "job ${job.id} failed" }
log.warn(cause, mapOf("jobId" to job.id, "attempt" to attempt)) { "retrying" }

if (log.isEnabled(Level.TRACE)) { /* ... */ }
```

The nearest enclosing declaration with a qualified name determines the logger name. Companion objects and enum
entry bodies share their class's logger. Lambdas, local classes, and anonymous classes use the enclosing class's
logger, or the file's logger at the top level. For example, top-level functions in `Jobs.kt` use `com.example.Jobs`.

Use `logger()` to store the declaration's logger in a property:

```kotlin
private val log = logger()

class JobProcessor {
    private val log = logger()
}
```

The `Log` factory also works without the compiler plugin:

```kotlin
private val log = Log.of<JobProcessor>()        // from the type argument
private val log = Log.of(JobProcessor::class)   // from a KClass
private val log = Log.of(javaClass)             // from the runtime Class, for abstract base classes
private val history = Log.named("jobs.history") // from an arbitrary name
```

The compiler plugin can fold constant factory calls into static field reads. A folded `Log.of` for the enclosing type
shares the field used by `log`. `Log.of(javaClass)` always looks up the logger at runtime.

### Context fields

`LogContext` holds fields that every record written on the current thread carries, such as a request id. A context is
an immutable value, safe to keep and to pass to other threads: `+` merges two contexts or adds one field and prefers
the right operand, `-` removes a key, a `null` value keeps its key, and `LogContext.EMPTY` has no fields. Fields of a
record shadow context fields with the same key.
`withLogFields` adds fields to the current context for the duration of a block. `withLogContext` enters exactly the
given context; use it to clear the context or to re-enter one captured earlier:

```kotlin
withLogFields("requestId" to request.id, "tenant" to tenant) {
    log.info { "accepted" }                                 // requestId, tenant
    withLogFields("step" to "validate") { log.debug("ok") } // requestId, tenant, step
    withLogContext(LogContext.EMPTY) { cleanup() }          // no context
}

val entry = (LogContext.current() + ("requestId" to id)).enter() // an interceptor that cannot wrap the call
try { chain.proceed() } finally { entry.close() }
```

The entry returned by `enter()` must be closed on the thread that entered it, innermost first. From Java, create
`new LogContext(Map)`, combine contexts with `plus`, and close the entry in try-with-resources.

To run a task on another thread with the context of the submitting thread, wrap it: `LogContext.current().wrap(task)`
accepts a `Runnable`, a `Callable` or a lambda and enters the context around the task. From Java, call
`LogContexts.wrap(context, task)`.

The block of `withLogFields` and `withLogContext` cannot suspend. In coroutines, use `withLogFields` from
`akki-coroutines`: the fields follow the coroutine across dispatchers. Import one of the two `withLogFields`
explicitly: with star imports of both packages the call is ambiguous. `LogContext.asContextElement()` does the same
for a whole context:

```kotlin
withLogFields("jobId" to job.id) { run(job) }                         // suspending, from io.akki.coroutines
runBlocking(LogContext.current().asContextElement()) { ... }          // carry the thread's context into coroutines
launch(Dispatchers.IO + LogContext.EMPTY.asContextElement()) { ... }  // background work without the context
```

An entry opened with `enter()` inside a coroutine lasts until the next suspension; afterwards the coroutine's own
context is restored.

Reactor and Spring propagate thread-local state with Micrometer `context-propagation`. `akki-context-propagation`
registers a `ThreadLocalAccessor` for the context under `LogContextAccessor.KEY`, so `ContextSnapshot` and Spring's
`ContextPropagatingTaskDecorator` carry the context to other threads. With `Hooks.enableAutomaticContextPropagation()`,
which Spring Boot turns on with `spring.reactor.context-propagation=auto`, a context written to the Reactor `Context`
is in effect in every operator. A `WebFilter` puts it there:

```kotlin
chain.filter(exchange).contextWrite { it.put(LogContextAccessor.KEY, LogContext("requestId" to id)) }
```

The module needs Micrometer `context-propagation` on the classpath; on the module path the application declares
`requires context.propagation` itself.

### Backends

`akki-slf4j` passes the context and the fields to SLF4J as key-value pairs, context first, and uses the exception as
the cause. Logback pattern layouts print key-value pairs with `%kvp`. For patterns that read `%X{...}`, the
`io.akki.slf4j.context` setting in [Configuration](#configuration) switches `akki-slf4j` to MDC: the context and the
fields of a record go there instead of key-value pairs, and MDC is restored after the record. A custom `LogBackend`
reads the context with `LogContext.current()` inside `Sink.emit`: akki does not merge it into `fields`.

If a `LogBackend` is registered with `ServiceLoader`, akki chooses it over `akki-slf4j`; when several are registered,
it takes the first by class name and reports the others to stderr. The `io.akki.backend` setting names the class of
the backend to use instead; for `akki-slf4j`, that is `io.akki.slf4j.Slf4jBackend`. A selection that matches no
backend, or a selected backend that cannot start, is an error: akki throws instead of falling back. Calling
`Log.install(backend)` replaces the backend for the whole process.

`LogBackend.filter` wraps a backend with a predicate over the logger name and the level. It runs in
`sink(level)`, so a rejected record is as cheap as a disabled level: its message lambda is not run. The predicate can
read `LogContext.current()`: the logger stays at `DEBUG`, and `DEBUG` records of every tenant but one are dropped. An
application installs the filtered backend by declaring it in `META-INF/services/io.akki.backend.LogBackend`:

```kotlin
class TenantBackend : LogBackend by Slf4jBackend().filter { name, level ->
    level >= Level.INFO || LogContext.current()["tenant"] == "acme"
}
```

### Tests

```kotlin
val records = recordLogs { processor.run(Job("nightly-report")) }
assertEquals("started nightly-report", records.single().message)
```

A capture belongs to one thread, so tests running in parallel never see each other's records. For coroutine tests,
import `recordLogs` from `akki-test-coroutines`: it keeps capturing when the coroutine moves between dispatchers.
`LogRecord.context` holds the context in effect when the record was written, separately from its `fields`:

```kotlin
assertEquals(LogContext("requestId" to 7), records.single().context)
```

### Configuration

The `akki {}` block has two parts. `compilerOptions {}` holds the compiler plugin settings, which change the
generated code. `jvm {}` holds the runtime settings of `jvm` compilations: the backend, its dependency, the logger
name style and where `akki-slf4j` puts the context. They ship with the application and leave the generated code
unchanged.

Set `minLevel` to remove lower-level records from the compiled code:

```kotlin
akki {
    compilerOptions {
        minLevel = io.akki.gradle.MinLevel.INFO
    }
}
```

By default, logger names use source notation, such as `com.example.Outer.Inner`. Set `loggerNameStyle` to use JVM
names instead: `com.example.Outer$Inner` and `JobsKt` for files:

```kotlin
akki {
    jvm {
        loggerNameStyle = io.akki.gradle.LoggerNameStyle.JVM_CLASS
    }
}
```

Set `backend` when a dependency registers a `LogBackend` and the application should use a different one, `akki-slf4j`
for example:

```kotlin
akki {
    jvm {
        backend = io.akki.gradle.AkkiJvmOptions.SLF4J_BACKEND
    }
}
```

`SLF4J_BACKEND` holds the class name of the `akki-slf4j` backend; the build fails on a value that is not a class name.

Set `slf4jContext` to `MDC` to have `akki-slf4j` write the context to MDC instead of key-value pairs, as described in
[Backends](#backends):

```kotlin
akki {
    jvm {
        slf4jContext = io.akki.gradle.Slf4jContext.MDC
    }
}
```

The Gradle plugin writes these settings to `META-INF/akki.properties` in the main resources. They ship with the
application, so `test`, `run`, `bootRun` and the jar use the same values. A system property with the same name, set
with `-Dio.akki.backend=io.akki.slf4j.Slf4jBackend` for example, takes precedence over the resource. An empty system
property, `-Dio.akki.backend=`, unsets the value shipped in the resource.

| Property                  | Values                                                                   | When unset                                        |
|---------------------------|--------------------------------------------------------------------------|---------------------------------------------------|
| `io.akki.loggerNameStyle` | `source`, `jvm-class`                                                    | `source`                                          |
| `io.akki.backend`         | class name of a registered `LogBackend`, or `io.akki.slf4j.Slf4jBackend` | a registered `LogBackend`, otherwise `akki-slf4j` |
| `io.akki.slf4j.context`   | `key-value-pairs`, `mdc`                                                 | `key-value-pairs`                                 |

These settings belong to the application. A library leaves them unset. An unknown property or value, or two
resources with different values for one property, is an error: akki throws instead of falling back. The error names
the system property or the file that set the value, and both files for a conflict. A property of a backend,
`io.akki.<backend>.<name>`, is checked by that backend: a misspelled `io.akki.slf4j.*` key in a resource or an unknown
value fails the first record, and the key goes unchecked while `akki-slf4j` is not on the classpath.

`includeSlf4jBackend`, `true` by default, adds `akki-slf4j` to the runtime classpath of every `jvm` compilation. Set
it to `false` in a library, or in an application that registers its own `LogBackend`. Selecting `akki-slf4j` with
`backend` or setting `slf4jContext` while it is `false` fails the build.

## Limitations

* Only JVM compilations are supported. Android needs minSdk 34 and has not been tested.
* Supported Kotlin releases are listed in the compatibility table. Their Beta and RC builds are accepted, and so are
  dev builds of a release; IDE and SNAPSHOT builds are not.
* `log` and `logger()` compile without the compiler plugin but throw when called. Use `Log.of` or `Log.named` in that
  case.
* Only the Gradle plugin sets up the compiler plugin and the akki dependencies automatically; see
  [Command line and other build tools](#command-line-and-other-build-tools).
* Accurate caller lines require the compiler plugin. Inline wrappers around `log` produce synthetic line numbers. Java
  calls report `io.akki.Logger` unless that class is registered in logback's `frameworkPackages`.
* Only a message passed as a lambda is lazy. Ordinary arguments, including fields, are evaluated even when the
  level is disabled.
* `recordLogs`, `LogScope` and `LogContext` apply to the current thread. Executors and other threads use the global
  backend. They start with an empty context unless the task is wrapped with `LogContext.wrap` or carried by
  `akki-context-propagation`.
* `io.akki.loggerNameStyle`, `io.akki.backend` and `io.akki.slf4j.context` are read once, when the first logger is
  created or the first record is written. Set them in `jvm {}` or with the `-D` option; `System.setProperty` in `main`
  may come too late.
* Records removed by `minLevel` cannot be restored through runtime logging configuration.
* Markers are not supported. With `io.akki.slf4j.context=mdc`, MDC holds the context only while akki writes a record:
  logs that other code writes through SLF4J do not get the fields unless the application puts them into MDC itself.
* `Log.named` keeps every logger it creates, so applications need a bounded set of names.
* The API is experimental. Releases before `1.0` may break compatibility.
