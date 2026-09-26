# akki

[![Kotlin](https://img.shields.io/badge/kotlin-2.4.20-blue.svg?logo=kotlin)](https://kotlinlang.org)
[![GitHub license](https://img.shields.io/badge/license-Apache%20License%202.0-blue.svg?style=flat)](https://www.apache.org/licenses/LICENSE-2.0)

Log in Kotlin without declaring a logger. The compiler plugin derives its name from the surrounding code.
Requires Kotlin `2.4.x`.

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
caller line. An optional compile-time threshold removes calls below the selected level.

## Modules

* `akki-core`: the logging API.
  * `log` and `logger()` use the enclosing declaration's logger and require the plugin.
  * `Log.of<T>()` and `Log.named("...")` work without the plugin. Java can use `Log.of(Class)` and `Log.named`.
  * `Logger` provides eager and lazy overloads of `trace`, `debug`, `info`, `warn`, and `error`.
  * `LogBackend` connects a backend; `LogScope` selects one for the current thread.
* `akki-slf4j`: backend for SLF4J 2, enabled when an SLF4J 2 provider is available at runtime.
* `akki-compiler-kotlin-<line>`: Kotlin compiler plugin, with a separate artifact for each supported Kotlin line.
* `akki-gradle`: sets up the compiler plugin with `akki-core` and `akki-slf4j` of the same version.
* `akki-test`: captures test logs with `recordLogs { }` or `RecordingBackend`.
* `akki-coroutines`: carries a `LogScope` in a coroutine context via `LogScope.asContextElement()`.
* `akki-test-coroutines`: provides suspending `recordLogs { }` for coroutine tests.

## Setup

### Gradle

Add the plugin to your build:

```kotlin
plugins {
    kotlin("jvm") version "2.4.20"
    id("io.github.octofleet.akki") version "0.2.0"
}
```

Both `akki-core` and `akki-slf4j` are added by the plugin. Logging uses the application's SLF4J 2 provider;
Spring Boot, for example, includes logback-classic. If no provider is available, akki prints a one-time notice and
sends records at `INFO` or above to stderr. You can add a provider explicitly:

```kotlin
dependencies {
    runtimeOnly("ch.qos.logback:logback-classic:1.5.20")
}
```

To capture logs in tests:

```kotlin
dependencies {
    testImplementation("io.github.octofleet:akki-test:0.2.0")
}
```

### Versions and Kotlin compatibility

All akki artifacts use the same release version, following [semantic versioning](https://semver.org). Until `1.0`,
minor releases may introduce breaking changes. Patch releases provide fixes and backward-compatible additions;
these can include support for an additional Kotlin line.

Because the plugin depends on the Kotlin compiler, each akki release provides a separate
`akki-compiler-kotlin-<line>` artifact for every Kotlin line it supports. The Gradle plugin selects one based on the
project's Kotlin version. If that line is unsupported by the akki release, the build fails.

| akki    | Kotlin                      | Compiler plugin            |
|---------|-----------------------------|----------------------------|
| `0.2.x` | `2.4.0`, `2.4.10`, `2.4.20` | `akki-compiler-kotlin-2.4` |
| `0.1.0` | `2.4.x`                     | `akki-compiler`            |

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

The `Log` factory also works without the plugin:

```kotlin
private val log = Log.of<JobProcessor>()        // by type
private val log = Log.of(JobProcessor::class)   // by class
private val log = Log.of(javaClass)             // by runtime class, for abstract base classes
private val history = Log.named("jobs.history") // by an arbitrary name
```

The plugin can fold constant factory calls into static field reads. A folded `Log.of` for the enclosing type shares
the field used by `log`. `Log.of(javaClass)` always looks up the logger at runtime.

### Backends

`akki-slf4j` passes fields to SLF4J as key-value pairs and uses the exception as the cause. If a `LogBackend` is
registered with `ServiceLoader`, akki chooses it over `akki-slf4j`. Calling `Log.install(backend)` replaces the
backend for the whole process.

### Tests

```kotlin
val records = recordLogs { processor.run(Job("nightly-report")) }
assertEquals("started nightly-report", records.single().message)
```

Each capture is local to its thread, which keeps parallel tests independent. In coroutine tests, import `recordLogs`
from `akki-test-coroutines` to capture logs across dispatcher changes.

### Configuration

Set `minLevel` to remove lower-level records from the compiled code:

```kotlin
akki {
    minLevel = io.akki.gradle.MinLevel.INFO
}
```

By default, logger names use source notation, such as `com.example.Outer.Inner`. Launch the JVM with
`-Dio.akki.loggerNameStyle=jvm-class` to use JVM names instead: `com.example.Outer$Inner` and `JobsKt` for files.
The default value of this option is `source`. The Gradle plugin passes the option to the JVMs that Gradle starts,
such as `test`, `run` and `bootRun`:

```kotlin
akki {
    loggerNameStyle = io.akki.gradle.LoggerNameStyle.JVM_CLASS
}
```

A JVM started outside Gradle, for example with `java -jar` or in a container, still needs the `-D` option. A value
that a task sets itself with `systemProperty` or `jvmArgs` takes precedence over `loggerNameStyle`.

## Limitations

* Only JVM compilations are supported. Android needs minSdk 34 and has not been tested.
* Supported Kotlin lines are listed by release in the compatibility table. Currently, only `2.4` is supported.
* `log` and `logger()` compile without the plugin but throw when called. Use `Log.of` or `Log.named` in that case.
* Plugin setup and akki dependencies are automatic in Gradle. With Maven, set `-Xplugin` to the
  `akki-compiler-kotlin-<line>` artifact that matches the project's Kotlin line. Add `akki-core` and `akki-slf4j`
  with the same akki version as the plugin; the compiler plugin reports version mismatches.
* Accurate caller lines require the plugin. Inline wrappers around `log` produce synthetic line numbers. Java
  calls report `io.akki.Logger` unless that class is registered in logback's `frameworkPackages`.
* Only a message passed as a lambda is lazy. Ordinary arguments, including fields, are evaluated even when the
  level is disabled.
* `recordLogs` and `LogScope` capture the current thread. Executors and other threads use the global backend.
* Choose the name style when launching the JVM. `loggerNameStyle` reaches only the JVMs that Gradle starts, and
  setting the style with `System.setProperty` in `main` is too late.
* Records removed by `minLevel` cannot be restored through runtime logging configuration.
* MDC and markers are not supported. `Log.named` retains its loggers, so applications need a bounded set of names.
* The API is experimental. Releases before `1.0` may break compatibility.
