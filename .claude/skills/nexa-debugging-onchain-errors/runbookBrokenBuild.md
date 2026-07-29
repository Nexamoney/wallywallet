# Runbook: the project stopped compiling after a dependency bump

A step-by-step procedure for when a Nexa project no longer builds after changing a library version.
The Nexa stack iterates quickly and its libraries must be **mutually compatible**, so a single bump
can cascade. Read `nexa-project-setup` for the underlying compatibility model; this is the triage
sequence. (This is a procedure doc — no single library "owns" it.)

## 0. Capture the exact error first

Note whether it's a **resolution** error (Gradle can't find/resolve an artifact), a **compile**
error (Kotlin type/symbol mismatch), or a **link/runtime-at-test** error
(`UnsatisfiedLinkError`, `Cannot find state transition`). The three have different causes.

## 1. Resolution failures ("could not resolve org.nexa:…")

1. Confirm the **GitLab Maven repos** are all registered in `settings.gradle.kts` (each Nexa lib is
   in its own project registry — see `settings.gradle.kts.template`). A missing repo line is the
   usual cause for one library failing to resolve while others succeed.
2. Confirm the **coordinate**, not just the version: e.g. NPL is `org.nexa:npl` (group `org.nexa`,
   artifact `npl`) — *not* `org.nexa.npl:npl`. The Kotlin package (`org.nexa.npl.*`) is not the
   Maven coordinate.
3. The version you pinned may not be published. Look up the **current** version in that library's
   GitLab Maven registry rather than trusting a number copied into a doc — pinned numbers drift.

## 2. Compile failures (Kotlin symbol / type mismatch)

This is almost always a **mutual-compatibility** problem: one Nexa lib expects a different version
of another (or of Kotlin/serialization) than you pinned.

1. **Cross-check the POM.** The published `.pom` of each artifact declares its transitive pins; it
   is authoritative. Read it from the local Gradle cache (`~/.gradle/caches/...`) — `nexa-project-setup`
   shows the recipe. In particular, check what `libnexaapp`'s POM pins for `libnexakotlin`, and what
   `org.nexa:npl` drags in for `kotlin-stdlib` / `kotlinx-serialization` (npl can pull a *newer*
   Kotlin/serialization than your baseline; keep npl off any Wasm/Kotlin-sensitive classpath).
2. **Kotlin compiler vs stdlib drift.** If the error mentions metadata/ABI versions, your Kotlin
   compiler and the transitive `kotlin-stdlib` disagree — align the `kotlin` plugin version with
   what `libnexaapp`'s POM resolves to.
3. **A genuinely renamed API.** Some symbol changes are real API-surface changes, not version noise
   — e.g. `millinow()` → `epochMilliSeconds()` in libnexakotlin, or the `Nexa.*` → `org.nexa.*`
   package migration for npl/nexarpc/scriptmachine. If a symbol "disappeared," check whether it was
   renamed (search the library source) before downgrading.
4. **Don't reflexively downgrade.** Trust the published POM's relationships over a number you typed.
   Bump the *related* libraries to a mutually compatible set rather than pinning one backward.

## 3. mavenLocal / SNAPSHOT interference

If a clean checkout builds but yours doesn't (or a Wasm build breaks), a stale `mavenLocal()` may be
shadowing a published artifact with a conflicting local snapshot. Keep `mavenLocal()` **below**
`mavenCentral()` in the repository order, and clear stale local snapshots. (The Wasm/KT-stdlib
conflict only bites with a conflicting local stdlib snapshot to shadow.)

## 4. kotlinx-serialization + CBOR

A specific kotlinx-serialization build has historically interacted badly with CBOR round-trips. If
you see a `CborDecodingException` after a serialization bump, match the serialization version your
Nexa artifacts declare in their POMs (step 2.1), and only downgrade if you can actually reproduce
the failure — the ecosystem has moved forward, so don't downgrade on spec.

## 5. Test-time link/build errors (not a "dependency bump" per se)

These surface only when tests run, and look like build failures:

- **`UnsatisfiedLinkError` / `libnexa.so`** — the `scriptmachine` (or NPL compile) native library
  isn't loadable. The library ships bundled in the `libnexakotlin-jvm` jar and `Initialize()`
  auto-extracts it to `<working dir>/lib/` (the JVM library path is not consulted); check that
  `Initialize()` runs once before use, the platform is covered by the bundled builds, and the
  working directory is writable — only a *self-supplied* build (loaded via
  `initializeLibNexa(variant)`) needs `--enable-javacashlib` attention
  (`nexa-script-machine-testing`).
- **`Cannot find state transition`** — an NPL *compile* error, not a version mismatch: the compiler
  lacks a stack-transition for your contract. Register it (or delete the stale `stackScripts.bin`
  cache) per `nexa-npl-smart-contracts` → "How NPL compiles a rule." Don't chase it as a dependency
  problem.

## 6. Last resort

`./gradlew --refresh-dependencies clean build` to discard cached resolution after you've corrected
the pins. If a clean reference project (or the library's own test build) compiles against the same
artifacts and yours doesn't, diff your `libs.versions.toml` against it.

## Related

- `nexa-project-setup` — the compatibility model, the POM-cross-check recipe, and the version
  anti-patterns this runbook applies.
- `nexa-script-machine-testing` / `nexa-npl-smart-contracts` — the two test-time build errors in step 5.
- `errorCodeReference.md` — once it builds, the on-chain/runtime rejection codes.