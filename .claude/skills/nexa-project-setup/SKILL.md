---
name: nexa-project-setup
description: "Sets up Nexa (libnexakotlin/libnexaapp/NPL) dependencies in Kotlin / Kotlin Multiplatform projects and fixes build-time issues. Use when starting a new Nexa app, adding org.nexa.* artifacts, editing libs.versions.toml / settings.gradle.kts / build.gradle.kts in a Nexa context, registering the Gitlab Maven repo or mavenLocal(), pinning Nexa/Kotlin/AGP/Compose-Multiplatform versions, or troubleshooting build errors like NoSuchMethodError org.nexa.libnexakotlin.PlatformKt, Kotlin/Wasm stdlib-vs-compiler mismatch, or AGP/Kotlin-multiplatform plugin deprecation warnings. Not for runtime wallet, transaction, or chain-selection issues."
---

# Nexa project setup and dependencies

## When to use this skill

Trigger when a developer is starting a new Nexa application, adding Nexa support to an
existing Kotlin / Kotlin Multiplatform project, editing `gradle/libs.versions.toml`,
`settings.gradle.kts`, or `build.gradle.kts` files in a Nexa context, or troubleshooting
**build-time** issues with `org.nexa.*` packages.

Concretely trigger on:
- New project setup that mentions Nexa, NEXA, Wally, libnexakotlin, libnexaapp, NPL.
- `NoSuchMethodError: 'long org.nexa.libnexakotlin.PlatformKt.<something>()'` at startup.
- `The version of the Kotlin/Wasm standard library (X) differs from the version of the compiler (Y)`.
- `The 'org.jetbrains.kotlin.multiplatform' plugin deprecated compatibility with Android Gradle plugin: 'com.android.application'`.
- Questions about where to find Nexa packages, what versions to pin, how to add `mavenLocal()`.
- AGP / Kotlin / Compose-Multiplatform version mismatch errors in a project that imports `org.nexa:*` artifacts.

**Negative triggers** — do NOT use this skill for:
- Runtime wallet-protocol issues (use `nexa-wallet-connection`).
- Runtime transaction or script failures (use `nexa-debugging-onchain-errors`).
- Choosing which chain to run on (default to **testnet** for development; regtest only when you need to force-mine blocks; mainnet for production). This skill covers `DEFAULT_CHAIN` placement; `nexa-wallet-lifecycle-and-chain` ("Which chain do I develop on?") covers the choice and what else must move with it, and `nexa-transaction-construction` covers routing chain selection through code paths.

## Mental model

Nexa's library ecosystem is **young, pre-1.0, and pre-semver-discipline**. Function names
and package paths still move between minor versions. The library jars are also published
to a **Gitlab Maven** repo, not Maven Central — your `settings.gradle.kts` must explicitly
register each project's `/packages/maven` endpoint.

There are three stacked libraries you'll touch:

1. **`libnexakotlin`** — pure chain types and primitives (`SatoshiScript`, `PayAddress`,
   `Bip44Wallet`, `Hash256`, hashing, serialization). Multiplatform — works on JVM, WASM,
   Android, iOS, native.
2. **`libnexaapp`** — server-side Ktor wallet sessions, the TDPP/nexid wallet-talk
   protocol, the `flowConnector` reactive-state-over-WebSocket layer, and a Compose
   Multiplatform UI helper module. Wraps libnexakotlin.
3. **`npl`** — Nexa Programming Language. A Kotlin DSL that compiles to script-template
   bytecode. Requires the `scriptmachine` artifact at compile time (used by tests).

 `scriptmachine` (`org.nexa:scriptmachine`) is both the runtime NPL compiles against and the
library you use to *execute/debug* scripts in tests (see `nexa-script-machine-testing`). It is a
**JNI binding to the node's native `libnexa.so`**, so two things follow: it is **JVM-only** (keep
it off any Kotlin/Wasm classpath, same as `npl`), and its `Initialize()` throws
`UnsatisfiedLinkError` if the native library can't load at test time.
The native library is **bundled inside the `libnexakotlin-jvm` jar** (`nativeLibs/` resources,
per-platform builds) and is auto-extracted to `<working dir>/lib/` and loaded on first
`Initialize()` — you do not install `libnexa.so` yourself, and the JVM library path is not
consulted (it only comes into play when a non-default variant name is passed to
`initializeLibNexa`). A load failure therefore means the bundled builds don't cover your
platform, the working directory isn't writable, or a deliberately self-supplied build lacks
`--enable-javacashlib`. See `nexa-script-machine-testing` Mental model for the full story.

The critical fragility: `libnexaapp` is built against a *specific* version of
`libnexakotlin`. If your `libs.versions.toml` pins a different `libnexakotlin` than the
one `libnexaapp` was compiled against, you get `NoSuchMethodError` deep in a
`NexaAppSession` constructor or websocket handler — usually at wallet-connection time, not
at startup. **Always cross-check the POM of your pinned `libnexaapp` version against your
`libnexakotlin` pin.**

The other ongoing concern is **Kotlin compiler version vs stdlib version**. `libnexaapp`
transitively pulls a specific Kotlin stdlib. Your `kotlin = "X"` in `libs.versions.toml`
must equal that exact stdlib version — patch-level precision matters; the compiler and
stdlib have to agree exactly or the Kotlin/Wasm compile errors out with the "stdlib differs
from compiler" message.

## Setup and versions

You need the libraries below. **Pin exact versions** (not floating ranges), but look up the
*current* published version of each in its GitLab Maven registry (URLs in the settings block
below) rather than copying a number from this doc — the Nexa libraries iterate faster than this
guidance, so any number here will drift out of date. What matters and stays true is the
**relationships** between versions, captured in the comments and anti-patterns.

```toml
# gradle/libs.versions.toml

[versions]
agp                  = "<latest your Android Studio accepts>"  # older AS rejects AGP above its ceiling — see anti-pattern
kotlin               = "<match libnexaapp's transitive kotlin-stdlib>"  # see compiler/stdlib drift anti-pattern
composeMultiplatform = "<latest>"
composeMaterial3     = "<latest>"
ktor                 = "<latest>"
kotlinx-coroutines   = "<latest>"
serializationVersion = "<match your Nexa artifacts' transitive pin>"  # see CBOR caveat in anti-patterns
bigNumVersion        = "<latest>"

# Nexa stack -- versions MUST be mutually compatible.  Cross-check libnexaapp's POM (pattern below).
nexa_libnexakotlin = "<latest>"
nexa_mpthreads     = "<latest>"
nexa_nexarpc       = "<latest>"
nexa_scriptmachine = "<latest>"
libnexaapp         = "<latest>"   # must pair with a compatible libnexakotlin — cross-check its POM
bu_npl             = "<latest>"

[libraries]
nexa-libnexakotlin = { module = "org.nexa:libnexakotlin", version.ref = "nexa_libnexakotlin" }
nexa-mpthreads     = { module = "org.nexa:mpthreads", version.ref = "nexa_mpthreads" }
nexa-rpc           = { module = "org.nexa:nexarpc", version.ref = "nexa_nexarpc" }
nexa-scriptmachine = { module = "org.nexa:scriptmachine", version.ref = "nexa_scriptmachine" }
nexa-npl           = { module = "org.nexa:npl", version.ref = "bu_npl" }
nexaapp            = { module = "org.nexa.libnexaapp:app", version.ref = "libnexaapp" }
nexaapp-compose    = { module = "org.nexa.libnexaapp:compose", version.ref = "libnexaapp" }
nexaapp-server     = { module = "org.nexa.libnexaapp:server", version.ref = "libnexaapp" }
```

The NPL Maven coordinate is `org.nexa:npl` (group `org.nexa`, artifact `npl`) — **not**
`org.nexa.npl:npl`. Note the Kotlin *package* is `org.nexa.npl.*` (e.g.
`import org.nexa.npl.NBytes`); don't conflate the package with the Maven group/artifact.

Required `settings.gradle.kts` repositories (order matters):

```kotlin
// settings.gradle.kts
dependencyResolutionManagement {
    repositories {
        mavenCentral()
        google()
        maven { url = uri("https://gitlab.com/api/v4/projects/48544966/packages/maven") }  // mpthreads
        maven { url = uri("https://gitlab.com/api/v4/projects/38119368/packages/maven") }  // libnexarpc
        maven { url = uri("https://gitlab.com/api/v4/projects/46299034/packages/maven") }  // scriptmachine
        maven { url = uri("https://gitlab.com/api/v4/projects/48545045/packages/maven") }  // libnexakotlin
        maven { url = uri("https://gitlab.com/api/v4/projects/73565187/packages/maven") }  // libnexaapp
        maven { url = uri("https://gitlab.com/api/v4/projects/82390523/packages/maven") }  // Nexa NPL
        mavenLocal()                            // MUST be below mavenCentral -- see anti-patterns
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
    }
}
```

## Core patterns

### A standard server-module `build.gradle.kts`

```kotlin
plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.ktor)
    alias(libs.plugins.serialization)
    application
}

kotlin { jvmToolchain(21) }

dependencies {
    implementation(projects.shared)
    implementation(libs.logback)
    implementation(libs.kotlinx.serialization.cbor)
    implementation(libs.kotlinx.serialization.json)

    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.netty)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.server.cors)
    implementation(libs.ktor.server.websockets)

    implementation(libs.nexaapp.server)
    implementation(libs.nexa.mpthreads)
    implementation(libs.nexa.libnexakotlin)
    implementation(libs.nexa.scriptmachine)
    implementation(libs.nexa.npl)

    testImplementation(libs.kotlin.testJunit)
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.11.4")
    testRuntimeOnly("org.junit.vintage:junit-vintage-engine:5.11.4")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.11.4")
}

tasks.withType<Test> { useJUnitPlatform() }
```

### A standard compose-app-module dependency block

```kotlin
// composeApp/build.gradle.kts, commonMain dependencies
sourceSets {
    commonMain {
        dependencies {
            implementation(projects.shared)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.serialization.cbor)
            implementation(libs.nexaapp.compose)    // brings libnexaapp + libnexakotlin transitively
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.components.resources)
            implementation(libs.androidx.lifecycle.viewmodel)
            implementation(libs.androidx.lifecycle.runtimeCompose)
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.kotlinx.json)
            implementation(libs.ktor.serialization.kotlinx.cbor)
        }
    }
}
```

### Verifying version compatibility before depending on it

Before bumping any `nexa_*` version, inspect the published POM:

```bash
# After running `./gradlew :server:compileKotlin` once so the artifact is cached:
find ~/.gradle/caches/modules-2/files-2.1/org.nexa.libnexaapp -name "*-jvm-*.pom" -exec grep -A1 libnexakotlin {} \;
# Output is the libnexakotlin version libnexaapp was actually built against, e.g.:
#     <artifactId>libnexakotlin-jvm</artifactId>
#     <version>X.Y.Z</version>
```

If your pinned `nexa_libnexakotlin` doesn't match what `libnexaapp`'s POM declares, bump
yours to match before doing anything else.

The same trick works for `org.nexa:npl`: its POM declares the transitive `libnexakotlin`,
`scriptmachine`, `kotlin-stdlib`, and `kotlinx-serialization` versions it expects. npl is
JVM-only and lives on the server/test classpath; if its declared `kotlin-stdlib` is newer
than your project's `kotlin` pin, keep npl off any Kotlin/Wasm classpath or you'll trip the
"stdlib differs from compiler" error described in the anti-patterns.

### Verifying API signatures before relying on them

These skills quote function names and argument orders (`txCompleter(...)`,
`ruleWithPublicArgs(...)`, `SatoshiScript.p2t(...)`, `createTdppUrl(...)`, …). The Nexa libraries
are pre-1.0 and iterate faster than this documentation, so **a quoted signature can drift** — an
argument added, reordered, or renamed between versions. Before relying on a non-trivial signature,
confirm it against the artifact you actually resolved rather than from memory:

```bash
# Find a resolved Nexa jar, then dump a class's public API (methods + signatures):
JAR=$(find ~/.gradle/caches/modules-2 -name 'libnexakotlin-jvm-*.jar' | head -1)
javap -classpath "$JAR" org.nexa.libnexakotlin.CommonWallet | grep -i txCompleter
# Kotlin top-level functions live in a <File>Kt class, e.g. the npl DSL builders:
#   javap -classpath "$NPL_JAR" org.nexa.npl.NplKt
```

Even better, when a `*-sources.jar` is published, attach it (or use the IDE's "Go to declaration"
on the resolved dependency) to see Kotlin default args, infix/extension forms, and nullability that
`javap`'s bytecode view flattens. **When a signature in any skill disagrees with the resolved jar,
the jar wins** — read these skills for intent, gotchas, and the *shape* of a call; confirm the
exact parameters against the artifact before you depend on them.

## Common mistakes and anti-patterns

### Mismatched libnexakotlin/libnexaapp versions

**Wrong** — pinning a `libnexakotlin` older than the one your `libnexaapp` was built against.
For example, a `libnexakotlin` from before the `millinow()` → `epochMilliSeconds()` rename,
combined with a `libnexaapp` compiled after it:
```toml
nexa_libnexakotlin = "<pre-rename version>"   # still exposes millinow()
libnexaapp         = "<post-rename version>"  # calls epochMilliSeconds()
```
*Fails with* `NoSuchMethodError: 'long org.nexa.libnexakotlin.PlatformKt.epochMilliSeconds()'`
*at first wallet WebSocket open — the function was renamed in libnexakotlin and the older
version doesn't have it under the new name.*

**Right**: pin `nexa_libnexakotlin` to whatever your `libnexaapp`'s POM actually declares
(see the "Verifying version compatibility" pattern above).

### Kotlin compiler version drift from transitive stdlib

**Wrong** — `kotlin` pin different from the `kotlin-stdlib` your Nexa libraries pull in
transitively (illustratively: `kotlin = "X.Y.0"` while `libnexaapp` pulls
`kotlin-stdlib-wasm-js X.Y.10`):
```toml
kotlin = "<older>"
libnexaapp = "<latest>"   # transitively pulls a newer kotlin-stdlib-wasm-js
```
*Fails with* `The version of the Kotlin/Wasm standard library (X) differs from the version
of the compiler (Y).`

**Right**:
```toml
kotlin = "<matches what libnexaapp's stdlib resolves to>"
```

Watch for the same trap with `org.nexa:npl`: its POM may declare a newer `kotlin-stdlib` /
`kotlin-reflect` than your project pins. npl is JVM-only, so the simplest fix is to keep it
off any Kotlin/Wasm classpath (typically server/test only). Verify with the POM-cross-check
pattern above before bumping.

### `mavenLocal()` listed before `mavenCentral()`

**Wrong**:
```kotlin
repositories {
    mavenLocal()                     // BAD: shadow-resolves Kotlin stdlibs for WASM
    mavenCentral()
}
```
*Causes* `IllegalStateException: Symbol for Any not found` on WASM builds (KT-73141). Local
Kotlin stdlib snapshots get picked over the published stdlib.

**Right**:
```kotlin
repositories {
    mavenCentral()
    google()
    // ... gitlab nexa repos ...
    mavenLocal()                     // below mavenCentral is safe
}
```

This trap is **conditional**: KT-73141 only fires when `mavenLocal` actually contains a
conflicting Kotlin stdlib snapshot for it to shadow. A clean/empty `~/.m2` makes the ordering
harmless. The recommendation still stands (keep `mavenLocal()` below `mavenCentral()` so you
don't have to think about local-repo state), but a project that lists it first can still build
fine until someone publishes the wrong thing locally.

### Hand-editing AGP past your Android Studio's ceiling

**Wrong**: bumping `agp` to whatever the IDE suggests, without checking your AS version's
supported AGP range.
*Older Android Studio versions reject this with* `The project is using an incompatible
version (AGP X.Y.Z) of the Android Gradle plugin. Latest supported version is AGP A.B.C`.

**Right**: pin `agp` to the highest version your current Android Studio supports; bump only
when you also upgrade AS.

### Mixing kotlinx-serialization versions across the CBOR boundary

**Symptom**: `CborDecodingException: Input contains N unprocessed bytes left after decoding
a value` when `flowConnector` round-trips CBOR objects.

This was observed historically on a specific kotlinx-serialization 1.10.0 build, with the
workaround being to pin an older serialization (1.9.x). The Nexa ecosystem has since moved
to serialization 1.10.0 transitively (e.g. `org.nexa:npl` declares it in its POM), so
forcing an older version can create a transitive version conflict you then have to resolve
explicitly. If you hit this exception, first match whatever serialization your Nexa
artifacts declare in their POMs; only downgrade if you can actually reproduce the failing
CBOR round-trip on the current version.

### Running NPL `.compile()` at server startup

**Wrong**: calling `myContract.compile()` in `main()`. Adds 500ms-2s to every boot and
pulls the `scriptmachine` runtime into your hot-path classloader. *See
`nexa-npl-smart-contracts` for the right pattern (hardcoded bytecode constant).*

## Security considerations

This skill is build-time setup; runtime security concerns live in the wallet-connection,
transaction, and smart-contract skills. The only security-relevant items here:

- **Pin exact versions** (`= "1.2.3"`), not floating ranges (`= "1.2.+"`). Floating ranges
  let a Gitlab maven update silently change your binary, which is both a reproducibility
  problem and a supply-chain risk.
- **Do not commit your wallet file** (e.g. `starterWallet.db`, `starterTestnetWallet.db`)
  to source control. Add to `.gitignore` early.
- **Do not commit `local.properties`** which contains Android SDK paths and sometimes
  signing material.

## Related skills and references

- `nexa-wallet-connection` — once the project builds, how to actually let the Wally
  wallet talk to it.
- `nexa-ktor-server-integration` — Ktor + CORS + libnexaapp route setup.
- `nexa-npl-smart-contracts` — how to add the NPL contract toolchain to your test
  classpath.
- `nexa-script-machine-testing` — the `org.nexa:scriptmachine` library and its `libnexa.so`
  native prerequisite, for running scripts/contract spends in tests.
- `nexa-debugging-onchain-errors` — symptom→cause table once you start running tx code.

### Supporting files in this folder

- `settings.gradle.kts.template` — full settings file with all GitLab Maven endpoints (one per Nexa
  library) plus `pluginManagement` and the `mavenLocal()` ordering, pre-registered.