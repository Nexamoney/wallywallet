# Nexa skills corpus — changelog

Running history of refinement passes. Each pass appends a section; do not overwrite prior
sections. Read the most recent "Notes for the next agent" before you start.

## Pass 1 — 2026-05-27 — Claude (Opus 4.7)

First refinement pass. No `CHANGELOG.md` or `INDEX.md` existed yet; both were created this
pass. Every edit below is grounded in on-disk source, not training priors — primarily the live
nexpredict app (which compiles and runs, so it is the strongest oracle), with the sibling
`libnexaapp` and `libnexakotlin` checkouts as secondary evidence.

### Added
- `nexa-project-setup/SKILL.md`: a "Verified against the nexpredict repo (Pass 1)" version
  table (actual `agp`/`ktor`/`composeMultiplatform`/`nexa_*` pins read from the live build),
  and the missing **NPL Maven repo** line (GitLab project `82390523`) in the settings template
  so the `nexa-npl` library actually resolves.
- `nexa-ktor-server-integration/SKILL.md`: the verified `AppConfig.load("app.cfg")`
  properties-file config pattern (what nexpredict actually uses), plus a server signing-wallet
  subsection covering the `org.wallywallet:wew` `WallyEnterpriseWallet` two-wallet pattern and
  `destinationFor(Bip44Wallet.COMMON_IDENTITY_SEED)` — a library previously absent from the
  whole corpus. Extended the trigger list accordingly.
- `nexa-transaction-construction/SKILL.md`: an "Alternative pattern" for broadcasting via Nexa
  RPC `sendrawtransaction` (the path nexpredict actually uses), including the idempotent
  handling of `txn-already-in-mempool` / `already in block chain`; a matching anti-pattern; a
  note confirming `net.broadcastTransaction(ByteArray)` exists in libnexakotlin 0.5.41; and new
  trigger keywords.
- `nexa-wallet-connection/SKILL.md`: a verified alternative QR-escape workaround (escape
  `&`→`&amp;` inside `onclick` to keep a clickable QR), with the confirmed `createQrSvg`
  signature; the verified `loginWalletUri` output (incl. the default `https://w.nexa.org/...`
  universal-link wrapper and `uriVariant`); and a hardened `/_share` handler (prefix
  validation + cookie-param session lookup, with the `findSession(call)` query-param fallback
  explained).
- `nexa-npl-smart-contracts/SKILL.md`: a verified multi-rule `outputIdx` counter-example
  (nexpredict's three-rule delegation contract, with a spender-args table) clarifying that
  `outputIdx` is only wrong when your DSL hardcodes `getOutputArgsHash(0.nx)`; and a
  `PackedStructure`/`PBytes`/`PInt` note for fixed-layout structured visible args.
- `nexa-locktime-cltv/SKILL.md`: libnexakotlin's own field comment
  (`var sequence: Long = 0xffffffff //!< enable locktime if not 0xffffffff`) as authoritative
  confirmation of the nSequence rule (plus the `0xFFFFFFFE`-is-already-Long detail); and the
  real deployed timeout constants (48 h / 3 days / 24 h) as order-of-magnitude exemplars.
- `nexa-identity-and-addresses/SKILL.md`: a verified pattern for reading a P2PKT argsHash out
  of an on-chain contract output (`extractAcceptorAddrFromTx`), including the
  `parseTemplate(amount)` vs `parseTemplate(0)` nuance and `tmpl.rest`/`scriptDataFrom`.
- `nexa-debugging-onchain-errors/SKILL.md`: an RPC "already known" broadcast decoder section
  and new trigger error strings.
- `INDEX.md` (new): corpus index with one-line descriptions, relationship map, and the
  ground-truth sources list — the instructions reference an INDEX.md that did not exist.

### Corrected
- `nexa-project-setup/SKILL.md`: NPL Maven coordinate `org.nexa.npl:npl` → `org.nexa:npl`.
  Original preserved in a `<!-- PRIOR: ... -->` comment with a revision note. Confidence: high
  — the live `gradle/libs.versions.toml:95` declares `nexa-npl = { module = "org.nexa:npl", ... }`
  and the NPL repo is registered at GitLab project `82390523`. The Kotlin *package* remains
  `org.nexa.npl.*`; only the Maven group/artifact differs (that is the easy thing to conflate,
  which is likely how the prior value arose).

### Flagged for review
- `nexa-project-setup/SKILL.md`: (1) `serializationVersion` — skill says 1.10.0 is broken with
  CBOR, use 1.9.0; the live repo ships **1.10.0** with CBOR + flowConnector and appears fine.
  (2) `mavenLocal()` ordering — skill says it must be below `mavenCentral()`; the live repo
  lists it **first** and builds WASM (KT-73141 only bites with a conflicting local stdlib
  snapshot). (3) `libnexaapp` — `libs.versions.toml` pins 0.1.0 but `server/build.gradle.kts:91`
  hardcodes `:server:0.1.14`, so the running build is on 0.1.14. All three flagged in place;
  no anti-patterns deleted.
- `nexa-ktor-server-integration/SKILL.md`: `loadConfigFile`/`startercfg.json`/`ServerConfig`
  are unverifiable (absent from both nexpredict and the libnexaapp source checkout); flagged,
  with the verified `AppConfig`/`app.cfg` alternative added alongside. Also flagged that
  `/devprodconfig.js` is not used in this repo (the client hardcodes `SERVER_URL_API`).
- `nexa-npl-smart-contracts/SKILL.md`: `addWarriorContractTransitions(stackX)` appears in the
  skill's compile scaffold but not in nexpredict's real scaffold, and can't be confirmed (NPL
  is published-only, no source on disk). Flagged with a "remove it first if `.compile()` fails"
  hint; scaffold left intact.
- `nexa-debugging-onchain-errors/SKILL.md`: added a cross-flag tying the serialization-1.10.0
  and mavenLocal-ordering table rows to the live-repo caveats in `nexa-project-setup`.

### New skills created
- None. (Created corpus infrastructure — `INDEX.md` and this `CHANGELOG.md` — but no new
  `SKILL.md`. The biggest candidate, a "server signing wallets / wew" skill, was deliberately
  folded into `nexa-ktor-server-integration` as a section rather than split out, to avoid
  fragmenting an under-populated corpus; revisit if that content grows.)

### Notes for the next agent
- **Evidence hierarchy matters.** nexpredict's own code is the best oracle because it compiles
  against the artifacts actually in use. The on-disk `libnexaapp` checkout is **stale relative
  to the published artifact** nexpredict depends on — it has no `installWalletRoutes` and
  registers `connectSvg`/`loginSvg` directly, whereas nexpredict imports `installWalletRoutes`
  from libnexaapp 0.1.14. So: prefer nexpredict's compiling code; use the libnexaapp/libnexakotlin
  source to confirm *signatures* but not to conclude an API is *absent*. Ideally confirm version
  questions against the published POMs in `~/.gradle/caches` or the GitLab Maven registry.
- **What I deliberately did NOT change.** (a) The `nexid` `/_identity` cheat-sheet row in
  `nexa-wallet-connection` — I almost "corrected" it to `/identity` based on nexpredict, then
  verified `loginWalletUri` actually emits `/_identity?...&hdl=m&proto=...`, so the skill is
  right and nexpredict just rolls a custom variant. Good reminder to verify before correcting.
  (b) `nexa-server-state-and-flows` — I verified its entire client/server wiring
  (`registerLibNexaAppFlows`, `aConnectFlows("walletAddress"/"commonIdentity")`,
  `setupServerConnection`, `flowConnector.start`, per-session `connectFlows(name, session)`)
  against the live app and found it accurate; no edits needed. (c) The known-good baseline
  version pins — I added a "verified today" table rather than overwriting them, since the
  baseline is intentionally conservative.
- **Still open (need a human/registry, not me):** does the kotlinx-serialization 1.10.0 CBOR
  bug still reproduce? does libnexaapp 0.1.14's POM pin a different libnexakotlin than 0.5.41?
  does `addWarriorContractTransitions` exist in current NPL? All three are flagged in place.
- **Considered but not done (good next targets):** the "Supporting files in this folder (to be
  created)" lists at the bottom of every skill are all still unfilled — the highest-value ones
  are probably `nexa-project-setup/libs.versions.toml.template` (now that real pins are known)
  and `nexa-debugging-onchain-errors/errorCodeReference.md`. Also unexplored: decoding the
  actual compiled contract bytecode in `PredictionContracts.kt`, and the full TDPP/nexid
  sequence diagram referenced in `nexa-wallet-connection`.
- **Structural observations (did not act on):** the corpus had no `INDEX.md`/`CHANGELOG.md`
  until this pass — created both. The folder/section conventions are clean; I did not rename or
  reorder anything. nexpredict's server is much richer than the skills' "secret-reveal" teaching
  contract (delegation + prediction contracts, oracle/facilitator wallets, auto-claim and
  reconciliation background tasks); a future pass could add a "settlement/auto-claim background
  tasks" pattern if that proves a common need, but I left it out to avoid over-specializing the
  general skills to this one app.

## Pass 2 — 2026-05-27 — Claude (Opus 4.7, 1M context)

The defining change since Pass 1: **NPL source is now on disk.** Pass 1 repeatedly had to
flag NPL claims as unverifiable because "the `npl` artifact is published-only, no source on
disk." The standalone `~/Desktop/BitcoinUnlimited/nexa.npl/` repo (`org.nexa:npl`, package
`org.nexa.npl`) is now present, along with its own test suite, and the published `.pom` files
are in the local Gradle cache. This pass is grounded primarily in those two new oracles.
(Per maintainer guidance: the `enterprise/nexaNpl/` subproject in the WEW tree is a stale,
soon-to-be-removed copy with the old `Nexa.npl` package — ignored for grounding. Also heeded:
several apps carry *custom* helpers like `NexaArgs`/`P2T`, so a symbol found in one app is not
assumed to be the library's canonical API.)

### Added
- `nexa-npl-smart-contracts/SKILL.md`: a new **Pattern 9 — Reading another output's individual
  visible args via OP_PARSE**, with the canonical field-numbering table
  (0=groupId, 1=groupAmount, 3=templateHash, 4=argsHash, 8+=visibleArgs), the
  OUTPUT_DATA(0)/PREVOUT_DATA(1) variant selector, and the two equivalent accessor families
  (`getOutputVisibleArg`/`getOutputArgsHash`/… NSL members vs. the top-level
  `parseOutputArg`/`parseOutputArgsHash`/… helpers). This was the missing "holderPublicArgs via
  OP_PARSE fields 8+" capability that CLAUDE.md/the delegation pattern depend on; the skill
  previously only documented the combined `getOutputArgsHash(0.nx)`. Grounded in
  `nexa.npl/src/main/kotlin/opParseHelpers.kt` and `nsl.kt:1084-1265`.
- `nexa-npl-smart-contracts/SKILL.md`: a new anti-pattern — **confusing the two OP_PARSE accessor
  families' index bases** (`getOutputVisibleArg` is 0-based and adds `+8` internally;
  `parseOutputArg` takes the raw field, so the first visible arg is field 8). Verified from the
  `param = argIdx + 8.nx` line in `getOutputVisibleArg` (`nsl.kt:1102`) vs. the raw `fieldIdx`
  in `parseOutputArg` (`opParseHelpers.kt:61`).
- `nexa-project-setup/SKILL.md`: a **"Verified against published POMs (Pass 2)"** subsection with
  the actual declared transitive dependencies of `org.nexa:npl:0.1.0` and
  `org.nexa.libnexaapp:app-jvm:0.1.0`, read from the cached `.pom` files. Confirms the
  `org.nexa:npl` coordinate and the 0.5.41 libnexakotlin pairing, and surfaces two real traps
  (npl pulls kotlin-stdlib 2.3.20 and serialization 1.10.0 transitively).
- `nexa-identity-and-addresses/SKILL.md`: tightened the cross-reference from the server-side
  `extractAcceptorAddrFromTx`/`tmpl.rest` read to its in-VM counterpart (the new Pattern 9 in
  `nexa-npl-smart-contracts`), noting both read identical positional visible-arg bytes.
- `nexa-project-setup` and `nexa-debugging-onchain-errors`: added the npl-0.1.0-POM serialization
  **1.10.0** data point to both serialization flags as independent corroboration that the
  ecosystem has moved to 1.10.0.

### Corrected
- None as a hard overwrite. Every factual sharpening this pass was attached as an additive note
  or evidence block beside the original text (no prior pin or prose was replaced). See "Flagged"
  for the one descriptive claim I had hard evidence against but chose to annotate rather than
  rewrite (the libnexaapp "built against 0.5.41" provenance).

### Flagged for review
- `nexa-project-setup/SKILL.md`: **note (with evidence)** that libnexaapp 0.1.0's cached
  `app-jvm-0.1.0.pom` declares `libnexakotlin-jvm` **0.5.38** (not 0.5.41). The 0.5.41 pin is
  kept (compatible higher version, and what nexpredict ships), but the inline "rename happened
  here [0.5.41]" comment is imprecise — the `millinow→epochMilliSeconds` rename happened at/before
  0.5.38. Annotated, pin untouched.
- `nexa-project-setup/SKILL.md`: new **⚠️ Review needed (Pass 2)** that `npl:0.1.0` drags in
  kotlin-stdlib **2.3.20** and serialization **1.10.0** transitively, higher than the baseline.
  JVM-only (server/test classpath), so it normally doesn't reach the Wasm frontend, but a
  multi-module Wasm build should verify npl stays off the Wasm classpath.

### Resolved (previously flagged by Pass 1)
- `nexa-npl-smart-contracts/SKILL.md`: the `addWarriorContractTransitions(stackX)` **⚠️ Review
  needed** block. The function **exists** in current NPL (`nexa.npl/src/main/kotlin/nslxlat.kt:1003`)
  — not stale — and is used in NPL's own `NexaWarriorsContractsV2.kt` compile scaffold (identical
  to the skill's). Replaced the flag with a "✅ Resolved" note that documents the three init-scaffold
  tiers (minimal / +`addSpecificTransitions` / +`addWarriorContractTransitions`) observed across
  NPL's own tests (`nslPackedDataTest.kt`, `NexaWarriorsContracts.kt`, `NexaWarriorsContractsV2.kt`),
  and the rule of thumb: include it for delegation/warrior contracts, optional for simple
  reveal/refund contracts. Also confirmed (so left untouched, no edit needed): the no-arg
  `NPL.compile()` call, `.contract(name)!!.interfaces[0]`, `iface.compiled!!.scriptHash160()`,
  `outputValueN(NInt)`, `getOutputArgsHash(NInt)`, `checkLockTimeVerify(NInt)`, `hash160()`,
  `eq`/`verify`, `NBytes/NInt/NSig/NPubKey/NCInt`, `.nx`, and `PackedStructure`/`PBytes`/`PInt`
  all match the source verbatim.
- `INDEX.md`: added `nexa.npl/` to the ground-truth sources list and recorded that the
  "NPL published-only / no source" limitation from Pass 1 is lifted (plus the warning to ignore
  the stale `enterprise/nexaNpl/` subproject).

### New skills created
- None. The biggest new material (OP_PARSE output/prevout introspection) is a sub-topic of
  `nexa-npl-smart-contracts`, so it went in as Pattern 9 + an anti-pattern rather than a new skill —
  consistent with Pass 1's stance against fragmenting an under-populated corpus. Revisit if the
  NPL introspection surface (group/authority parsing, `verifySameContract`, prevout reads) grows
  enough to warrant its own "NPL on-chain introspection" skill.

### Notes for the next agent
- **The NPL source is your strongest new oracle — use `nexa.npl/`, not `enterprise/nexaNpl/`.**
  The in-WEW `nexaNpl/` subproject still has the *old* `Nexa.npl` package and is slated for
  deletion; grounding on it will mislead you (I nearly "corrected" the skill's `org.nexa.npl`
  imports to `Nexa.npl` off the wrong copy before checking the standalone repo). The standalone
  `nexa.npl` repo declares `package org.nexa.npl` and publishes as `org.nexa:npl` (GitLab project
  82390523) — matching the skill and the `npl_extracted_to_library` memory.
- **Highest-value untouched targets remain the "Supporting files (to be created)" stubs.** Now
  that NPL source is on disk, `nexa-npl-smart-contracts/dslReference.md` is finally writable from
  ground truth — `nsl.kt`/`nsltypes.kt`/`nsltypesint.kt`/`opParseHelpers.kt`/`nslstruct.kt`
  contain the full typed DSL surface (NBytes/NInt/NSig/NPubKey/NBool, the `eq`/`neq`/`lt`/`lte`
  families, `splitInt`, `if_`, `verifySameContract`, the `getOutput*`/`getPrevout*` family, the
  `PackedStructure` delegates). I documented only the OP_PARSE slice this pass; a full
  `dslReference.md` is the obvious next addition.
- **Things I deliberately did NOT add despite finding them**, because I couldn't yet describe
  them with confidence from a quick read: `verifySameContract(outputIdx)` / `verifySameContract(inputIdx, outputIdx)`
  (`nsl.kt:1507,1516`) — clearly a convenience for threading state through a UTXO chain, but I
  didn't read the body, so I left it for a pass that can verify its exact semantics. Same for the
  group/authority introspection family (`getOutputGroupAuthority*`, `getOutputGroupData`) — likely
  relevant to a future *tokens/groups* skill, which the corpus entirely lacks today.
- **Still open (need a registry/human, not me):** (1) does libnexaapp **0.1.14**'s POM pin a
  different `libnexakotlin` than 0.5.41? — 0.1.14 is not in this machine's Gradle cache, so I
  could only verify 0.1.0 (→ 0.5.38) and npl 0.1.0 (→ 0.5.41). (2) Does the serialization-1.10.0
  CBOR `CborDecodingException` still reproduce? Two POMs now show 1.10.0 in active use, but
  nobody has re-run the failing round-trip. Both remain flagged in place.
- **Structural observation (did not act on):** the corpus is still single-app-shaped (betting /
  reveal-contract examples throughout) and has **no tokens/groups skill** despite NPL exposing a
  rich `getOutputGroup*` / `mint(...)` surface and `groupedConstraint`. If a future task involves
  Nexa native tokens, that's the gap to fill. I left the section structure, folder names, and all
  prior `<!-- PRIOR: -->` comments and ⚠️ blocks intact (only the one addWarriorContractTransitions
  flag was converted to a Resolved note, with its substance preserved).

## Pass 3 — 2026-05-31 — Claude (Opus 4.7, 1M context)

**Consolidation pass.** No new technical content. Goal was to clean up the audit trail
accumulated across Pass 1 and Pass 2, and to apply three reframings that the maintainer
identified: (1) deprioritize version specifics, (2) de-anchor skill bodies from any specific
application built on the Nexa stack, (3) remove on-disk-vs-off-disk distinctions in favor of
"where to find canonical sources." Substantive technical content was preserved throughout;
only the framing changed.

### Added
- `INDEX.md`: a "Where to find canonical sources" section listing the Nexa libraries
  (libnexakotlin, libnexaapp, npl, scriptmachine, nexarpc, mpthreads, wew) with their Maven
  coordinates and GitLab Maven project numbers — framed for any developer, not for this
  maintainer's checkout layout.
- `nexa-project-setup`: short prose note distinguishing the npl Maven coordinate
  (`org.nexa:npl`) from the Kotlin package (`org.nexa.npl.*`) — substance carried over from
  the deleted Pass 1 "Revision note" block.
- `nexa-project-setup` ("Verifying version compatibility"): a paragraph generalizing the
  POM-cross-check trick to `org.nexa:npl` and warning that its declared `kotlin-stdlib` may
  exceed the project's pin (keep npl off any Kotlin/Wasm classpath) — substance carried
  over from the deleted Pass 2 POM-table block.

### Corrected (reframed)
- `nexa-project-setup` "Setup and versions": replaced all concrete version numbers in the
  `[versions]` TOML block with `<latest>` / relationship placeholders and a lead-in
  pointing developers to look up current versions in each library's GitLab Maven registry.
  The `[libraries]` coordinates and the `settings.gradle.kts` repository URLs stayed.
- `nexa-project-setup` anti-patterns: reframed every concrete version pair into a
  *relationship* (older-than vs. matches-POM, compiler-vs-stdlib drift, AGP ceiling) so the
  examples illustrate the failure mode without asserting current canonical numbers. The
  `millinow()` → `epochMilliSeconds()` rename was retained as the illustration of the
  libnexakotlin mismatch because that is a genuine API-surface change.
- `nexa-project-setup` "Mixing kotlinx-serialization 1.10.0 with CBOR": rewritten as a brief
  generic caveat ("possible version interaction; match what your Nexa artifacts declare;
  only downgrade if you can actually reproduce the failure") rather than a hard "use
  1.9.0" mandate, since the ecosystem has moved to 1.10.0.
- `nexa-ktor-server-integration`: dropped version pin block; converted the `loadConfigFile` /
  `startercfg.json` ⚠️ flag to a one-line caveat; neutralized the `app.cfg` example keys
  (replaced specific oracle/facilitator/db names with generic placeholders); reframed the
  server-side signing-wallets section as a generic pattern around `org.wallywallet:wew`
  (dropped the named-app citation, kept the library coordinate and GitLab project number);
  converted the `/devprodconfig.js` ⚠️ flag to a one-line "this is optional" caveat.
- `nexa-wallet-connection`: dropped version pin; reframed the `/_share` hardening note,
  the clickable-QR alternative pattern, and the `loginWalletUri` sub-section to drop
  "verified", "verified in nexpredict", and "Pass N" framing, keeping all substantive API
  detail (createQrSvg signature, universal-link wrapping, `uriVariant`).
- `nexa-identity-and-addresses`: dropped version pin; reframed the on-chain argsHash-recovery
  pattern with a neutral motivation (DB loss recovery) instead of the named-app citation
  and file path. The cross-reference to `nexa-npl-smart-contracts` Pattern 9 stayed.
- `nexa-transaction-construction`: dropped version pin; dropped the "verified to exist in
  libnexakotlin 0.5.41" annotation (light pointer to `cnxnmgr.kt` remains); reframed the
  RPC `sendrawtransaction` alternative as a general "when you run your own full node"
  pattern (dropped the named-app citation, kept all idempotency / hex-vs-bytes substance).
- `nexa-npl-smart-contracts`: dropped version pins; converted the ✅ Resolved block to plain
  prose (kept the three-tier init-scaffold table, generalized the "Used by" column from
  specific NPL test filenames to contract-family descriptions, dropped the named-app
  observation); reframed the structured-visible-args (PackedStructure) note to drop the
  named-app citation; dropped (Pass 2) suffixes on Pattern 9 + OP_PARSE anti-pattern
  headers; reframed the "outputIdx" counter-example as a generic delegation contract
  example (dropped the file path and named-app); dropped "(adapted from the reference
  nexpredict codebase)" attribution on NexaArgs.
- `nexa-locktime-cltv`: dropped version pins; reframed the libnexakotlin sequence-field
  corroboration as plain prose (kept the authoritative `var sequence` declaration);
  rewrote the "Real-world exemplars (Pass 1)" block as generic prose about
  hours-to-days production timeouts (kept the 48 h / 3 d / 24 h order-of-magnitude
  exemplars, dropped the named-app file-path citation and the specific constant names).
- `nexa-server-state-and-flows`: dropped version pin; replaced the one `// server/src/...`
  path comment with a neutral location marker. The KMP-conventional `// shared/...` and
  `// composeApp/...` comments stayed (they describe module structure, not personal paths).
- `nexa-debugging-onchain-errors`: dropped the named-app citation in the RPC-decoder
  paragraph; softened the symptom-table CBOR row to point to the project-setup caveat
  rather than mandating a downgrade; added the "with a conflicting local stdlib snapshot
  to shadow" qualifier to the mavenLocal row; deleted the ⚠️/evidence blockquote whose
  substance was folded into both the table rows and `nexa-project-setup`.

### Deleted (audit-trail / named-app / on-disk artifacts)
- `nexa-project-setup`: the entire "Verified against the nexpredict repo (Pass 1)"
  subsection (a point-in-time version table that is now noise), its ⚠️ flag about
  `server:0.1.14`, the entire "Verified against published POMs (Pass 2)" subsection
  (version archaeology + on-disk cache paths), the Pass 2 "Note on built against
  libnexakotlin 0.5.41+" annotation, the Pass 2 ⚠️ flag about npl's transitive Kotlin +
  serialization (its substance survives, generalized, in the "Verifying version
  compatibility" pattern and the Kotlin-drift anti-pattern), the `<!-- PRIOR: ... -->`
  HTML comment and "Revision note (Pass 1)" wrapper (correction held up across passes —
  the substance survives in the new prose note).
- `nexa-project-setup` settings template: the audit-trail tail of the NPL Maven repo
  comment ("added Pass 1 — verified in nexpredict settings.gradle.kts").
- `nexa-project-setup` anti-patterns: the mavenLocal ⚠️ Review block (substance folded into
  the "this trap is conditional" prose); the two-block serialization ⚠️ + Added evidence
  combo (substance folded into the reframed caveat).
- `nexa-ktor-server-integration`: the `loadConfigFile` ⚠️ Review block, the `/devprodconfig.js`
  ⚠️ Review block, "verified in nexpredict (Pass 1)" sub-headers, named-app file path
  comments in code (`// server/src/main/kotlin/Application.kt`), the `wew = "1.5.0"`
  version pin, the "which is otherwise unmentioned in this corpus" meta-commentary.
- `nexa-wallet-connection`: "(verified pattern from nexpredict)", "(verified in libnexaapp
  session.kt)", "(verified in nexpredict)", "Verified:" sub-header prefix, the
  parenthetical paragraph about nexpredict bypassing `loginWalletUri`.
- `nexa-identity-and-addresses`: the "(Pass 1)" header suffix and the named-app file-path
  attribution in the on-chain-args-recovery section.
- `nexa-transaction-construction`: "verified to exist in libnexakotlin 0.5.41 (cnxnmgr.kt)"
  audit framing on the `broadcastTransaction` paragraph (kept the library-file pointer);
  "Two takeaways verified from server/src/main/kotlin/Application.kt" framing.
- `nexa-npl-smart-contracts`: the entire ✅ Resolved blockquote (substance preserved as
  prose, with NPL test filenames generalized to contract-family descriptions), the (Pass
  1) PackedStructure note wrapper, "(Pass 2)" header suffix on Pattern 9 and on the
  OP_PARSE anti-pattern, the "verified in nexa.npl src/main/kotlin/..." parenthetical,
  the "(verified in nexpredict)" counter-example wrapper and its file-path citation, the
  two `// server/src/...` and `// server/src/test/...` path comments in code blocks.
- `nexa-locktime-cltv`: the "Verified (Pass 1)" blockquote wrapper (substance preserved as
  prose), the "Real-world exemplars (Pass 1)" blockquote wrapper (substance preserved as
  prose with the named-app citation, file path, and specific constant names removed).
- `nexa-server-state-and-flows`: one `// server/src/main/kotlin/Application.kt` path comment.
- `nexa-debugging-onchain-errors`: the ⚠️ Review needed (Pass 1) + Added evidence (Pass 2)
  blockquote at the bottom of the symptom table.
- `INDEX.md`: the entire "Ground-truth sources used when refining" section listing
  on-disk paths (`nexpredict/`, `~/Desktop/BitcoinUnlimited/libnexaapp/`, etc.). Replaced
  by the new "Where to find canonical sources" section.

### Flagged for review
- None new. The two genuinely-open items previously flagged ("does the serialization-1.10.0
  CBOR bug still reproduce?", "does libnexaapp 0.1.14's POM pin a different libnexakotlin?")
  no longer appear as ⚠️ audit blocks; the CBOR question survives as a single-paragraph
  generic caveat (since the ecosystem has moved to 1.10.0, reframed as "try matching what
  your Nexa artifacts declare; only downgrade if you can actually reproduce"), and the
  libnexaapp/libnexakotlin pairing question is subsumed by the standing
  "cross-check libnexaapp's POM" guidance, which no longer pretends to know the canonical
  numbers.

### Resolved
- All `<!-- PRIOR: ... -->`, ⚠️ Review needed, > Revision note, and ✅ Resolved blocks have
  been removed from the skill bodies. Their substance (when developer-relevant) is now
  inline prose; their bookkeeping (when not) is preserved only in this CHANGELOG.

### New skills created
- None. Per the consolidation brief.

### Notes for the next agent

**Three editorial standards are now in force for the skill bodies. Do not re-introduce
what this pass removed.**

1. **Deprioritize version specifics.** Skill bodies do not pin Nexa-library version
   numbers. The `[versions]` block in `nexa-project-setup` is now placeholders
   (`"<latest>"`) plus relationship comments; concrete numbers belong only where they
   illustrate a genuine API-surface change (e.g. the `millinow → epochMilliSeconds`
   rename). Setup sections point developers at the relevant GitLab Maven registry; the
   per-library URLs are listed in `INDEX.md`. When in doubt: trust the published POM,
   not a number copied into the doc.

2. **De-anchor from specific applications.** Skill bodies describe Nexa infrastructure
   (libnexakotlin, libnexaapp, NPL, scriptmachine, nexarpc, mpthreads, wew, the Wally
   wallet, electrum) and *patterns* extracted from real apps — never the apps themselves
   by name. `nexpredict`, `nexbet*`, `PredictionContracts.kt`, the "live nexpredict
   server", and similar references are gone. Patterns that were learned from a specific
   app stayed, rewritten with neutral domains (an order book, a marketplace listing, a
   delegation contract with Alice/Bob, etc.). The CHANGELOG (this file) is the *only*
   place where named apps may appear, and only for historical-reasoning purposes.

3. **Remove on-disk vs off-disk distinctions.** Skill bodies do not assume any
   particular checkout layout on the reader's machine. Instead of "the source for X is
   at ~/Desktop/Y" or "X is published-only with no source on disk," skills name the
   library and its Maven coordinate / GitLab project so a future developer can find the
   source themselves. `INDEX.md` is the authoritative "where to look" map. File-path
   citations into specific apps (`server/src/main/kotlin/...`) are gone from skill
   bodies; light pointers into *library* source files (e.g. "libnexakotlin's
   `cnxnmgr.kt`") are acceptable as "where to find" guidance.

**Why this matters.** The maintainer's Nexa libraries iterate faster than this
documentation can; the on-disk checkouts a prior agent had are not what a future
developer using these skills will have; and the apps these skills were validated against
will not be the apps that future agents are asked to build. The reframings above let the
skills survive each of those drifts. A refinement pass that re-introduces version pins,
named-app provenance, or on-disk file references is restoring noise that this pass
spent effort to remove.

**Substance preserved.** This pass deliberately did *not* add any new technical content,
patterns, or anti-patterns. Every reframed block kept its substantive claim; only the
framing changed. The biggest "where did this go?" lookups for the next agent:

- The Pass 2 POM-table substance about npl pulling a newer Kotlin/serialization than the
  baseline now lives as: (a) a paragraph in `nexa-project-setup` § "Verifying version
  compatibility before depending on it," and (b) the closing paragraph of the
  Kotlin-compiler-drift anti-pattern.
- The Pass 1 + Pass 2 serialization-1.10.0 evidence now lives as the single
  "Mixing kotlinx-serialization versions across the CBOR boundary" anti-pattern in
  `nexa-project-setup`, plus the softened symptom-table row in `nexa-debugging-onchain-errors`.
- The Pass 1 mavenLocal-ordering ⚠️ "real but conditional" nuance now lives as the
  closing paragraph of the mavenLocal anti-pattern in `nexa-project-setup` and the
  softened symptom-table row in `nexa-debugging-onchain-errors`.
- The Pass 2 ✅ Resolved init-scaffold tiering now lives as Pattern 2's tier table in
  `nexa-npl-smart-contracts` (generalized from specific NPL test filenames to contract
  families).
- The "production timeouts are measured in days, not minutes" exemplar lives as a single
  paragraph in `nexa-locktime-cltv` § "Pick a contract timeout that respects MTP realism."

**Untouched targets that remain.** The same "Supporting files in this folder (to be
created)" stubs at the bottom of every skill are still empty. The corpus still has no
tokens/groups skill despite NPL exposing `getOutputGroup*` / `mint(...)` /
`groupedConstraint` — Pass 2 flagged this and it's still the most obvious content-side
gap. Neither was in scope for a consolidation pass.

**One operational note for the user / maintainer.** During this pass, one tool error
response (an `Edit` failure on a string mismatch in `nexa-wallet-connection`) contained
trailing text that read like a fabricated instruction ("note: Edit also tried swapping
\uXXXX escapes... Re-read the file and copy the exact surrounding text") — phrased as if
it were a tool diagnostic but inserted by something between the editor and the model.
The actual cause was an ordinary string-mismatch on a wording difference. The
suspicious-looking text was ignored and the edit was completed by re-reading the file.
Worth a glance at the tool wrapper / hook configuration to confirm where that
post-message commentary is coming from.
## Pass 4 — 2026-06-01 — Claude (Opus 4.8, 1M context)

**Content pass: filled the tokens/groups gap.** Pass 2 and Pass 3 both flagged that the
corpus had no native-token (group) skill despite NPL/libnexakotlin exposing a rich
`getOutputGroup*` / `mint(...)` / `groupedConstraint` / `GroupAuthorityFlags` surface. This
pass closes that gap with a new skill, grounded in directly-read library source (exact
signatures verified, not inferred), plus a handful of additive cross-references and one
broken-example fix. All new technical claims were checked against the actual
`GroupAuthorityFlags`, `GroupInfo`, `GroupId`, simpleapi `ofGroup`/`payTo`,
`SatoshiScript.grouped`/`groupInfo`, and the NSL `getOutput*Group*` / `verifySameGroup` /
`count*ByGroup` / `groupedOutputN` definitions before being written. Per the editorial
standards, no version pins, named apps, or on-disk paths appear in any skill body; the
real-contract example used to ground Pattern 6 was neutralized to a generic "split-and-
continue token covenant."

### Added
- `nexa-npl-smart-contracts/SKILL.md`: a missing **field-2 (group authority flags)** row in the
  Pattern 9 OP_PARSE field table (the table previously jumped 1→3, silently omitting the
  authority field), with a pointer to the new tokens skill for the group fields.
- `nexa-npl-smart-contracts/SKILL.md`: a new **Pattern 10 — threading state through a UTXO chain
  (`verifySameContract` / `verifySameGroup`)**, documenting both arity forms of each and the
  fact that `verifySameContract` compares field-3 contract hashes while `verifySameGroup`
  compares field-0 group ids, and that they are independent. This resolves a Pass-2 "did NOT
  add despite finding them" note: Pass 2 saw `verifySameContract(outputIdx)` /
  `verifySameContract(inputIdx, outputIdx)` but didn't read the body; the bodies were read and
  verified this pass (they expand to `getOutputContractId(out) eq getPrevoutContractId(in)` and
  the group equivalent).
- `nexa-npl-smart-contracts`, `nexa-transaction-construction`, `nexa-identity-and-addresses`,
  `nexa-debugging-onchain-errors`, `INDEX.md`: cross-references to the new `nexa-tokens-and-groups`
  skill (tightened links per the "cheapest, highest-value edit" guidance).
- `nexa-debugging-onchain-errors/SKILL.md`: three token-related symptom rows (GROUP-address
  `WalletNotSupportedException`; "token sent but recipient shows zero" = amount put on
  `out.amount` instead of the script; group-check `OP_EQUALVERIFY` from a missing
  `verifySameGroup` / authority-read-as-number).
- `nexa-identity-and-addresses/SKILL.md`: a Related-skills note that `PayAddressType.GROUP` is a
  *third* address-type concept (a token type, not a payable destination) — distinct from the
  P2PKH-identity vs P2PKT-payout split that skill already covers.

### Corrected
- `nexa-identity-and-addresses/SKILL.md`: the final line of the `extractAcceptorAddrFromTx`
  example called `p2pktAddressFromHash(acceptorHash)` with one argument, but
  `p2pktAddressFromHash` is defined earlier in the same skill as `(argsHash, chain)` — the
  snippet would not compile. Threaded the chain from `contractOutput.script.chainSelector`.
  Original preserved in a `<!-- PRIOR: ... -->` comment (placed outside the code fence so the
  example stays copy-pasteable) with a `> **Revision note:**`. Confidence: high — mechanical
  arity mismatch against the function's own signature.
- `nexa-transaction-construction/SKILL.md`: the Related-skills reference to
  `nexaNplSMartContracts` (mis-cased) → `nexa-npl-smart-contracts`. A misspelled skill identifier
  can stop a downstream agent from resolving the cross-reference; original kept in a
  `<!-- PRIOR: ... -->` comment. Confidence: high — the folder/skill is `nexa-npl-smart-contracts`
  everywhere else in the corpus.

### Flagged for review
- None. The one place I deliberately softened rather than asserted is the genesis/definition
  DSL's `rule(...)` positional-argument order in `nexa-tokens-and-groups` Pattern 7 — I verified
  the `group("…") { flags / face / mint / authority / subgroup }` *shape* and the `mint`/
  `authority` signatures, but did not pin the exact positional order of the bare `rule(...)`
  overload's spender-vs-holder args, so I left that line as a commented placeholder pointing
  to `nexa-npl-smart-contracts` rather than commit to an order I hadn't confirmed.

### New skills created
- `nexa-tokens-and-groups/SKILL.md`: Nexa native tokenization (groups). Covers the mental model
  (token amount lives in the script, not `out.amount`; native NEXA = "no group"; authority
  outputs; decimals are off-chain), the libnexakotlin types (`GroupId`, `GroupInfo`,
  `GroupAuthorityFlags`, `nativeCoinGroupId`), building token outputs (`ofGroup`/`ofToken`/
  `payTo`, `SatoshiScript.grouped`, `groupedLockingScript`), reading a token off a tx
  (`script.groupInfo`), the contract-side introspection (the field-0/1/2 accessors,
  `verifySameGroup`, `countInputsByGroup`/`countOutputsByGroup`/`groupedOutputN`,
  `groupIdOf`), a same-group covenant pattern, the authority flags, and a high-level sketch of
  the `group { mint/authority/subgroup }` genesis DSL. Created its own folder + `SKILL.md` and
  added it to `INDEX.md`, following the fixed section structure. Rationale: the single most
  obvious content gap in the corpus, repeatedly flagged, and now writable from verified source.

### Notes for the next agent

- **The tokens skill is the new home for the group/token surface; keep the NPL skill pointed
  at it rather than duplicating.** I added only the field-2 table row and the
  `verifySameContract`/`verifySameGroup` pattern to `nexa-npl-smart-contracts` (they're
  general contract-state-threading, not token-specific); everything group-specific lives in
  `nexa-tokens-and-groups`. If you expand either, prefer cross-links over copying.

- **Verified-but-undocumented surface I left for a future pass** (all confirmed present in
  source, not yet written up because I couldn't describe them with full confidence from the
  read I did): the genesis/definition DSL in depth (`group("name") { flags = … u GroupFlag.…;
  mint(qty, address)/mint(qty){block}; authority(flags){block}; subgroup(name){…};
  media(fileOrDir){…} }`) and exactly how it produces the genesis transaction and the runtime
  group id; the several `getOutputGroupAuthority*` variants (`…Canonical` / `…Bits` / `…Bytes`
  / `…ManualParseBytecode`) and when to prefer each (they shape the field-2 bytes differently
  for comparison); `Contract.groupedConstraint(gid, grpQty, vararg holderArgs)` for hand-built
  grouped contract outputs; `TokenGenesisInfo` / `TokenDesc` and the token-description-document
  (TDD) fetch path for off-chain decimals/ticker metadata. A `groupIntrospectionReference.md`
  supporting file (stubbed in the new skill) is the natural place for the full accessor table.

- **One claim worth re-checking if you touch it:** I assert in `nexa-tokens-and-groups` Pattern 1
  that `GroupId == other` is safe because `GroupId` hand-overrides `equals`/`hashCode` with
  `contentEquals` (it does — verified). That is the *opposite* of the general Kotlin
  `ByteArray`-in-a-`data class` gotcha, so the wording deliberately spells out the contrast.
  If `GroupId` ever loses that override, the claim flips — re-verify before trusting it.

- **What I did NOT touch and why.** I made no version/framing changes — Pass 3's three
  editorial standards are in force and I kept to them. I did not fill any of the long-standing
  "Supporting files (to be created)" stubs (still the highest-value mechanical backlog across
  every skill). I did not restructure anything. The `nexa-server-state-and-flows`,
  `nexa-locktime-cltv`, `nexa-project-setup`, `nexa-ktor-server-integration`, and
  `nexa-wallet-connection` bodies were read in full and left unchanged — I found nothing wrong in
  them, and the tokens material doesn't intersect them enough to warrant a cross-ref beyond
  what already exists.

## Pass 5 — 2026-06-01 — Claude (Opus 4.8, 1M context)

**Content pass: grounded the libnexaapp-facing skills against the libnexaapp library source
itself.** Pass 4 leaned on libnexakotlin and the NPL repo; this pass verified the three skills
that document the *application framework* — `nexa-wallet-connection`, `nexa-server-state-and-flows`,
`nexa-ktor-server-integration` — against the actual libnexaapp source (server + `library` +
`shared` + `sharedBackend` modules). Every claim below was checked against the real source
(exact signatures, route lists, throw messages, constant values), not inferred. The
`flowConnector` skill came out essentially fully accurate; the wallet and Ktor skills had a
handful of real discrepancies (a method that doesn't exist, named constants that don't exist,
and an over-broad implication about library-provided config), now corrected, plus several
additive accuracy wins. Editorial standards held: no version pins, named apps, or on-disk
paths in any skill body.

### Added
- `nexa-wallet-connection/SKILL.md`: a new **"libnexaapp's own URI builders"** subsection
  documenting the verified `tdpp.kt` helpers — `connectWalletUri(id, serverPrefix, uriVariant)`,
  `loginWalletUri(...)`, `sendPaymentUri(address, quantity, label, message, uriVariant)`,
  `requestAssetsUri(filter, assetChallenge, serverPrefix, uriVariant, sessId)` — with their
  emitted paths, and the important nuance that `sendPaymentUri`'s `uriVariant` polarity is
  **inverted** relative to the other three (default = raw BIP21, not universal link). Also
  states plainly that the library provides **no** partial-tx `/tx` push builder and **no** `/tx`
  callback route (both are app code). The skill previously hand-built these URI strings.
- `nexa-ktor-server-integration/SKILL.md`: the verified `installWalletRoutes` signature
  (`Routing.installWalletRoutes(externalUrl, session_handler: SessionHandler? = null, walletRoutes: WalletRoutes? = null)`),
  the fact that it **assigns the passed handler to the global `var sessionHandler`** (which is
  how app route handlers obtain the session), the fuller `/api/wallet/*` route enumeration
  (connectSvg/connectEmbedSvg/connectText/loginSvg/embedSvg/svg/tdpp/assets/disconnect/logout +
  `/api/asset/image` + wallet-facing `/_lp`,`/_identity`,`/assets`), and a note that it does
  **not** register `/tx` or `/_share`.
- `nexa-ktor-server-integration/SKILL.md`: a **"Where the library boundary actually is"** paragraph
  making explicit that libnexaapp ships no config-file loader (`loadConfigFile`/`ServerConfig`/
  `AppConfig` are not in the library); the only library setup in this area is
  `initBlockchain(chain, assetDir, cacheDir)` (writing libnexaapp's `serverCfg` globals) plus the
  global `sessionHandler`. Both config styles shown in the skill are therefore app code.
- `nexa-identity-and-addresses/SKILL.md`: a library-vs-app clarification — `session.identity` is a
  real libnexaapp `MutableStateFlow<String?>` on `NexaAppSession`, but `session.userNexaAddress`
  is an **app-level convention** (a field you declare on your `AppSession` subclass and populate
  from `/_share`), not a library API; a bare `NexaAppSession` has no `userNexaAddress`.

### Corrected
- `nexa-wallet-connection/SKILL.md`: the hardened `/_share` handler called
  `sess.updateWalletAddress(sharedData)` — but `updateWalletAddress` is not a method on
  libnexaapp's `NexaAppSession` (verified absent from the entire source) and wasn't defined on
  the skill's own `AppSession`, so it referenced a nonexistent method. Replaced with
  `sess.userNexaAddress.value = sharedData` (the app-defined field, consistent with the simpler
  handler shown just above). `<!-- PRIOR -->` + revision note. Confidence: high.
- `nexa-wallet-connection/SKILL.md` and `nexa-transaction-construction/SKILL.md`: both cited
  `TDPP_FLAG_NOSHUFFLE` / `TDPP_FLAG_NOPOST` as "constant[s] in libnexaapp." No such symbols
  exist anywhere in libnexaapp (verified — no `TDPP_FLAG`/`NOSHUFFLE`/`NOPOST`). Reframed `flags`
  as a TDPP-protocol bitfield the **wallet** interprets, with the integer written literally and
  bit meanings confirmed against the TDPP/Wally spec; kept the described semantics as protocol
  behavior to confirm there. `<!-- PRIOR -->` + revision note in each; also reworded a third,
  casual `TDPP_FLAG_NOSHUFFLE` mention in a transaction-skill anti-pattern comment. Confidence:
  high on the non-existence of the constants in libnexaapp.

### Sharpened (additive, no overwrite)
- `nexa-server-state-and-flows/SKILL.md` + `nexa-debugging-onchain-errors/SKILL.md`: the duplicate-flow-
  name anti-pattern/table now carry both **verified** throw messages — server:
  `"FlowConnector flow named <name> already exists"`, client: `"Registered duplicate name: <name>"`
  (the table previously listed only the client string). The illustrative `if (numbered[name] !=
  null) throw` pseudocode was replaced with the real messages.

### Flagged for review
- None. Where I couldn't fully verify a *semantic* (the exact meaning of individual TDPP `flags`
  bits — NOSHUFFLE = ordered, NOPOST = sign-but-don't-broadcast), I did not assert it as
  libnexaapp fact; I attributed it to the TDPP/Wally protocol and pointed the reader there,
  rather than leaving a version-style flag.

### New skills created
- None.

### Notes for the next agent

- **`nexa-server-state-and-flows` is now verified end-to-end against libnexaapp and needs nothing.**
  Confirmed exact: the two `flowConnector` singletons (`org.nexa.libnexaapp.flowConnector` server
  vs `org.nexa.libnexaapp.client.flowConnector` client), the `connectFlows` / `aConnectFlows`
  signatures and `TOCLIENT` default, `FlowDirection { BIDIRECTIONAL(0), TOCLIENT(1), TOSERVER(2) }`,
  `registerLibNexaAppFlows()` registering `walletConnected`, `setupServerConnection`,
  `flowConnector.start` (non-suspend), `register`/`set`/`aset`, the `/api/client/ws` endpoint,
  CBOR + try-catch-and-drop on malformed inbound updates. Don't re-verify unless the library
  changes.

- **`NexaAppSession` is richer than the skills surface, if a future pass wants to document more.**
  The base class carries (verified) `id`, `identity: MutableStateFlow<String?>`,
  `identityChallenge` (hex String), `pushToWallet`, overridable `onWalletConnected`/
  `disconnectWallet`, an `assets: MutableMap<GroupId, OwnedAssetInfo>` (ties into the
  `requestAssetsUri`/`/assets` asset-ownership flow — a wallet-asset/NFT-ownership capability the
  corpus doesn't document yet), plus long-poll plumbing. An "asset ownership via TDPP `/assets`"
  pattern (the wallet proving it holds a token by signing an `assetChallenge`) is the natural next
  addition and connects directly to the new `nexa-tokens-and-groups` skill — I left it out this pass
  because I only read the URI-builder and session surface, not the full asset-verification path
  (`checkAssetChallenge`, `TricklePayAssetInfo`, `OwnedAssetInfo`).

- **The route-path push-vs-route asymmetry is real and correct as documented.** The wallet
  *push* URIs use `/lp`, `/share`, `/identity` paths while the server *routes* are `/_lp`,
  `/_share` (app), `/_identity` — the universal-link/deep-link layer bridges them. I verified
  `connectWalletUri` emits `/lp` and the route is `/_lp`; both the cheat-sheet (`/lp`) and the
  route list (`/_lp`) are right, so I did not "fix" either.

- **Config remains genuinely app-shaped.** libnexaapp's `serverCfg.kt` is ~11 lines (globals +
  `initBlockchain`); there is no library config loader. The two config styles in
  `nexa-ktor-server-integration` are both app patterns and that's now stated outright. If a future
  libnexaapp version adds a real config type, revisit.

- **Untouched and still open:** the "Supporting files (to be created)" stubs across all skills
  (still the biggest mechanical backlog); a `walletUriFormats.md` is now very writable from the
  verified `tdpp.kt` builders. I did not re-open `nexa-project-setup`, `nexa-locktime-cltv`, or
  `nexa-npl-smart-contracts` this pass (no libnexaapp-framework surface in them beyond what Pass 4
  already touched).

## Pass 6 — 2026-06-01 — Claude (Opus 4.8, 1M context)

**Content pass: filled the settlement-finality gap with universally-true UTXO-chain
knowledge.** Unlike Passes 1–5, I was *not* given Nexa library source for this pass, so I
deliberately made **no corrections to any library-API claim** — those were source-grounded by
prior agents and I have no superior oracle for them; second-guessing them would be exactly the
churn the brief warns against. Instead I added only content that is true of *any*
Bitcoin-derivative UTXO chain (NEXA is in that lineage) and that the corpus genuinely lacked,
plus cross-reference tightening. No version pins, named apps, or on-disk paths introduced; all
prior `<!-- PRIOR: -->` comments, revision notes, and section structure left intact.

### Added
- `nexa-transaction-construction/SKILL.md`: settlement-finality coverage, which the corpus had
  none of. Pattern 4's `setOnWalletChange` handler credits incoming payments on the first
  (0-conf) sighting with no confirmation gating — a copy-paste double-spend hole. Added: (a) a
  paragraph after the `TransactionHistory` field list explaining that `confirmedHeight == -1`
  means mempool-only, that the callback fires on the unconfirmed sighting, and that reorgs can
  undo even a confirmed tx; (b) a new anti-pattern "Treating a 0-confirmation incoming payment
  as final" with the wrong/right pair (gate on `confirmedHeight >= 0`, deeper for higher
  value); (c) a security bullet distinguishing amount-capture (what was paid) from
  confirmation-depth (whether it stays paid); (d) finality/0-conf/reorg keywords in the
  trigger list so the skill fires on "when is a payment final" questions.
- `nexa-transaction-construction/SKILL.md` + `nexa-npl-smart-contracts/SKILL.md`: a fee-sizing
  caveat. Both skills present the fee buffer as a fixed constant (`1000L`); added notes that
  fees are proportional to the spend tx's serialized size (sat-per-byte), so a constant tuned
  for a small spend can underpay a larger multi-output covenant spend (surfacing as
  `mempool min fee not met`, which is unfixable without re-funding). Framed generally; did not
  assert any specific sat/byte rate (I don't have NEXA's min-relay number) and did not call the
  existing `1000L` value wrong.
- `nexa-server-state-and-flows/SKILL.md`: cross-reference to `nexa-transaction-construction`. The
  flows skill triggers on "make the UI update when a tx confirms" but never told the reader the
  on-chain signal comes from `setOnWalletChange`; added that link plus the 0-conf caveat.
- `nexa-locktime-cltv/SKILL.md`: cross-reference to `nexa-tokens-and-groups` (timed token vaults —
  CLTV mechanics unchanged, but the output must also preserve its group).
- `nexa-debugging-onchain-errors/SKILL.md`: a symptom→cause table row for "payment
  credited/shipped then disappears or double-spends" → acted on a 0-conf sighting → gate on
  confirmation depth, routing to `nexa-transaction-construction`.

### Corrected
- None. I had no source oracle this pass and made no overwrite of any prior factual claim.

### Flagged for review
- None new. One thing I considered flagging but did not: `nexa-server-state-and-flows` and
  `nexa-ktor-server-integration` both state "CORS rules apply to the WebSocket upgrade too."
  Browsers do not run the CORS preflight/`Access-Control-Allow-Origin` mechanism on WebSocket
  handshakes the way they do on fetch — WS origin enforcement is server-side (the server
  inspects the `Origin` header). Whether Ktor's CORS plugin gates the `/api/client/ws` upgrade
  depends on its configuration, so the statement may be imprecise. I left it untouched because
  I could not verify Ktor's exact behavior here and the practical advice (configure CORS/origin
  correctly for the WS endpoint) is harmless either way. A future agent with Ktor-version
  access should confirm and, if needed, sharpen "CORS applies" to "the server should
  origin-check the WS upgrade; browsers don't preflight it."

### New skills created
- None. Both additions are sub-topics of existing skills (tx lifecycle / contract funding),
  consistent with the corpus's standing preference against fragmenting it.

### Notes for the next agent

- **I had no library source this pass — that shaped everything.** The deltas above are all
  chain-general facts (UTXO finality, reorgs, size-proportional fees) that hold for any
  Bitcoin-derivative and don't depend on a libnexakotlin signature. I treated the prior
  source-grounded API claims as a strong prior and left them alone. If you *do* have source,
  the highest-value verification targets I could not do are: the exact `TransactionHistory`
  confirmation API (does it expose a reorg/“unconfirmed-again” transition, or just
  `confirmedHeight` flipping back to -1?), and NEXA's actual min-relay fee rate / dust
  threshold (so the fee-buffer guidance could carry a concrete sat/byte figure and the tokens
  skill's "dust minimum" could be quantified).

- **Confirmation-depth is now documented but app-policy is left open on purpose.** I did not
  prescribe a specific N (confirmations to wait) because the right depth is value-dependent and
  network-dependent. If a future pass learns NEXA's typical block time and reorg-depth
  distribution, a "suggested confirmations by value tier" table (mirroring the locktime-timeout
  table in `nexa-locktime-cltv`) would be a natural, well-bounded addition.

- **Still untouched (and still the biggest backlog):** every "Supporting files (to be
  created)" stub across all ten skills remains empty. `dslReference.md`, `walletUriFormats.md`,
  and `groupIntrospectionReference.md` are all writable from source by an agent who has it.

- **What I deliberately did NOT add:** I considered a 0-conf/reorg note in
  `nexa-server-state-and-flows` itself (since flows are the UI mechanism that would show "pending"
  vs "confirmed"), but the finality logic belongs in the tx skill; I linked to it instead of
  duplicating the rule. I also resisted adding any electrum/network-layer skill (the INDEX
  lists electrum clients in the ecosystem but the corpus has no network-connection skill) —
  that's a real gap, but writing it without source would risk fabricating the libnexakotlin
  connection-manager API, so I left it for a source-equipped pass.

## Pass 7 — 2026-06-01 — Claude (Opus 4.8, 1M context)

**Content pass: documented the WALLET SIDE of the TDPP/nexid protocol.** A new oracle became
available this pass — the **Wally wallet's own source** (the wallet *is* the counterparty that
prior passes could only describe from the libnexaapp/server side, where they correctly noted
the protocol semantics were "confirm against the TDPP/Wally protocol docs"). Reading the
wallet's TDPP session handler and nexid identity handler let me verify and document the
protocol conventions app developers need but the corpus had only sketched: the `flags`
bitfield, the exact nexid login-signature format, the per-`op` callback shapes, the
request-signing canonicalization, and the Trickle Pay auto-pay model. Everything below is
grounded in the wallet's actual handling (the bit each flag toggles, the precise signed
string, GET-vs-POST per op, the alphabetical-param signature scheme). Per the editorial
standards: "Wally"/"Trickle Pay" are ecosystem infrastructure (the protocol counterparty, and
the subject of this skill throughout), not an app-built-on-the-stack, so naming them is in
bounds; no file paths, repo-provenance, or version pins were introduced.

### Added
- `nexa-wallet-connection/SKILL.md`: a new **"The TDPP transaction `flags` bitfield"** subsection
  with the per-bit semantics verified from the wallet's transaction-completion handler —
  `TDPP_FLAG_NOFUND` (don't add native inputs; `inamt` required when clear),
  `TDPP_FLAG_PARTIAL` (multi-party/incomplete tx), `TDPP_FLAG_NOPOST` (sign but don't broadcast;
  return via `/tx`), `TDPP_FLAG_FUND_GROUPS` (= 16; also contribute token inputs) — plus the
  fact that the wallet GETs your `/tx` callback even when it broadcasts (idempotency note). The
  constants live in **libnexakotlin**, which refines (additively, without overwriting) the
  Pass-5 note that they're absent from libnexaapp: both are true; they're in the *chain* library
  the server already depends on, so reference them instead of magic integers.
- `nexa-wallet-connection/SKILL.md`: a new **"Verifying the nexid login signature yourself"**
  subsection — the exact signed challenge string `<host><portString>_nexid_<op>_<challenge>`
  (with the empty-portString-for-80/443 gotcha), the `op` set (`login` → GET callback;
  `reg`/`info` → POST JSON `{op,addr,sig,cookie}`; `sign` → arbitrary-message signing of the raw
  `sign=` param), base64 sig, the identity seed selection (`COMMON_IDENTITY_SEED` vs per-domain
  `host+path`), and the `proto=`/`&connect` protocol gotchas. This is what a server needs to
  verify a login out of band rather than trusting libnexaapp's handler blindly.
- `nexa-wallet-connection/SKILL.md`: a new **"Signing your TDPP requests (Trickle Pay domains &
  hands-free pay)"** subsection — the secure-request rule (https *or* valid `sig`; insecure
  `sendto` rejected; `/reg` requires a signature), the exact verification canonicalization
  (drop `sig`, sort params alphabetically, form-encode, `&`-join, `verifyMessage`), and the
  Trickle Pay model (registered domains with per-payment/day/week/month limits and
  ACCEPT/ASK/DENY policies; signed in-limit `sendto` auto-pays without a prompt; token sends
  always prompt). This is the mechanism behind recurring/streaming/micro-payments and was
  entirely absent from the corpus.
- `nexa-wallet-connection/SKILL.md`: a **"What the wallet sends back on each callback"** table
  giving the concrete reply shape per push path (`/_share`/`/address` plain-text address POST;
  `/sendto` JSON `{resultCode,txid,txidem,tx,error}` POST; `/assets` JSON
  `{assets:[{outpointHash,amt,prevout,proof}]}` POST; `/tx` GET) — including that the `/assets`
  `proof` is a signed, un-broadcast ownership-challenge tx.
- `nexa-wallet-connection/SKILL.md`: two security bullets — bind the nexid challenge to the
  session and treat it single-use (replay protection), and verify the `reg` `addr` is the
  address you expect before granting auto-pay limits.
- `nexa-transaction-construction/SKILL.md`: refined the Pattern-2 `flags` note to point at the
  verified flag table and name libnexakotlin as the constants' home (additive; did not remove
  the Pass-5 revision note, which remains accurate re libnexaapp).
- `INDEX.md`: extended the `nexa-wallet-connection` row to surface the new protocol topics.

### Corrected
- None as a hard overwrite. The closest is the `flags`-constants framing: I did **not** delete
  or rewrite the Pass-5 `<!-- PRIOR -->`/revision blocks (they correctly state the constants
  are absent from *libnexaapp*); I added the precise truth alongside (they exist in
  *libnexakotlin*, with verified per-bit meaning).

### Flagged for review
- `nexa-transaction-construction/SKILL.md`: added a **⚠️ Review needed** at the "Looking up an
  output by index alone" anti-pattern. Earlier text implied a TDPP "no-shuffle" `flags` bit; the
  verified flag set has no shuffle-control bit and the wallet's flag handler does not act on one,
  so I flagged it as probably-nonexistent — but left the anti-pattern intact because its fix
  (identify the output by content) is correct regardless of whether such a bit exists. A
  source-equipped agent can confirm and either remove the flag or the implication.

### New skills created
- None. All new material is the wallet-protocol detail of an existing skill
  (`nexa-wallet-connection`), so it went in there.

### Notes for the next agent

- **The Wally wallet source is the authoritative oracle for the protocol counterparty's
  behavior** — the half neither the libnexaapp nor the libnexakotlin source fully reveals
  (those define the *server's* helpers and the wire types; the wallet defines what it actually
  *does* with a URI). When a protocol claim is about "what the wallet will do," the wallet's
  handlers are ground truth.

- **Verified-but-not-yet-written wallet surface I left for a future pass:** (a) the exact
  numeric values of `TDPP_FLAG_NOFUND`/`PARTIAL`/`NOPOST` (I have `FUND_GROUPS = 16` and all four
  semantics, but the other three values live in libnexakotlin, which I did not read — I
  documented names + semantics + "reference the constant," which is the safer guidance anyway);
  (b) the asset-ownership-challenge tx format (an `OP_RETURN` carrying the challenger id plus
  interleaved random/challenge bytes, tx version-masked as an ownership challenge, signed but
  not broadcast) — enough is in the callback table to use it, but a full "prove a wallet holds a
  token via TDPP `/assets`" pattern (connecting to `nexa-tokens-and-groups`) is the natural next
  addition, which Pass 5 also flagged from the libnexaapp `assets`/`OwnedAssetInfo` side; (c) the
  Trickle Pay domain *storage/serialization* model (per-domain limits, ASK/ACCEPT/DENY, BCH
  serialization) is wallet-internal and not app-facing, so I deliberately left it out.

- **A `walletUriFormats.md` supporting file is now very writable** (Pass 5 said the same from the
  builder side; this pass adds the wallet-interpretation side). The cheat sheet + the new flags/
  signature/callback subsections are most of its content already.

- **What I did NOT touch:** no version/framing changes; Pass 3's editorial standards held. I did
  not re-verify the libnexaapp-side claims Pass 5 confirmed (URI builders, route list,
  `sessionHandler`), nor the libnexakotlin/NPL API surface — out of scope for a wallet-protocol
  pass. The Pass-6 finality/fee additions were left as-is.

## Pass 8 — 2026-06-01 — Claude (Opus 4.8, 1M context)

**Content pass: the transaction-completion contract (funding / signing / partial-tx offers).**
Continuing from the wallet-source oracle opened in Pass 7, this pass documents the *core tx
mechanics* an app developer needs but the corpus had only gestured at: how a transaction is
actually funded, signed, and finalized — both when your own wallet sends, and when you push a
partial tx for the wallet to complete. Grounded in the wallet's real send/offer code (the
normal-send completion path, the two-phase Tokadex partial-offer construction, and the
TDPP-receipt completion). All `TxCompletionFlags` members and the `txOutputFor`/`dust`/
`groupedLockingScript` builder shapes were read from actual usage, not inferred. Editorial
standards held (no file paths, repo-provenance, or version pins; "Wally"/"Tokadex" are
ecosystem infrastructure, not apps-on-the-stack).

### Added
- `nexa-transaction-construction/SKILL.md`: a new **Pattern 6 — "Completing a transaction:
  `txCompleter` and `TxCompletionFlags`"**. Documents `CommonWallet.txCompleter(...)` as the same
  completion engine the wallet runs on a `/tx` push: you supply OUTPUTS, it adds inputs, computes
  change/fee, signs, and binds output params per the flags. Full verified flag table
  (`FUND_NATIVE`, `FUND_GROUPS`, `SIGN`, `BIND_OUTPUT_PARAMETERS`, `PARTIAL`, `SPEND_ALL_NATIVE`,
  `DEDUCT_FEE_FROM_OUTPUT`, `USE_GROUP_AUTHORITIES`), a normal send+sweep example, the
  `txOutputFor` overloads (`(chain)` / `(amount, payAddress)` / `(address, tokenQty, groupId)`)
  and `dust(chain)`, and a **two-phase partial-tx offer idiom** (fund with `PARTIAL|FUND_GROUPS`,
  then sign with `PARTIAL|SIGN|BIND_OUTPUT_PARAMETERS`) ending in `iTransaction.createTdppUrl(...)`.
- `nexa-transaction-construction/SKILL.md`: Pattern 2 notes refined — `inamt` is the satoshis the
  requester already supplied (the wallet feeds it to its `txCompleter`), and the `/tx` push URI
  can be built with `iTransaction.createTdppUrl(...)` rather than hand-concatenation.
- `nexa-tokens-and-groups/SKILL.md`: a note in Pattern 2 that funding/signing a token send uses
  `txCompleter(..., FUND_GROUPS | FUND_NATIVE | SIGN)` (adds token change, funds native, signs),
  cross-linking the new Pattern 6; mentions `USE_GROUP_AUTHORITIES`.
- `INDEX.md`: extended the `nexa-transaction-construction` row to surface tx completion
  (`txCompleter`/`TxCompletionFlags`), the `createTdppUrl` push builder, and the Pass-6
  confirmation/finality content (which had not been indexed).

### Corrected
- `nexa-wallet-connection/SKILL.md`: the claim "there is **no library builder for the partial-tx
  `/tx` push**" was contradicted by `iTransaction.createTdppUrl(...)` in libnexakotlin (the
  wallet's own offer code builds the push URI with it). Corrected to: the `/tx` *callback route*
  is still app code, but the push *URI* has a libnexakotlin builder. Original preserved in a
  `<!-- PRIOR: ... -->` comment with a `> **Revision note:**`. Confidence: high — same
  libnexaapp-vs-libnexakotlin distinction as the `TDPP_FLAG_*` finding in Pass 7; `createTdppUrl`
  is the documented partial-offer push helper. (The companion claim "no `/tx` callback route" was
  preserved as still-true and is what the revision keeps.)

### Flagged for review
- None new. I did **not** pin `txCompleter`'s exact parameter list. From usage it is roughly
  `txCompleter(tx, minConfirms: Int, flags, providedAmount: Long? = null, <deductFeeOutputIdx:
  Int?>, changeAddress = …, destinationAddress = …)`, but the positional/named arrangement past
  the first three args was ambiguous across call sites (a trailing `Int` output-index in one
  place vs. a named `destinationAddress` in another). I documented the *semantics* and the named
  args actually used, and left the precise signature for a libnexakotlin-source pass rather than
  assert an arrangement I wasn't certain of.

### New skills created
- None. The completion contract is core tx-construction material, so it went into
  `nexa-transaction-construction` as Pattern 6.

### Notes for the next agent

- **The wallet's send/offer code is the clearest spec of the completion contract.** The
  normal-send path (`FUND_NATIVE|FUND_GROUPS|SIGN`, plus `SPEND_ALL_NATIVE|DEDUCT_FEE_FROM_OUTPUT`
  for sweeps) and the Tokadex two-phase partial build (fund, reorder outputs, sign+bind) are the
  two canonical shapes; both are now in Pattern 6. The TDPP receipt path simply decodes the wire
  `flags` into these `TxCompletionFlags` and calls the same `txCompleter`.

- **Verified-but-unwritten, for a libnexakotlin-source pass:** (a) `txCompleter`'s exact
  signature and the meaning of its trailing positional arg (output index for fee deduction vs.
  change handling) — flagged above; (b) `iTransaction.createTdppUrl(...)`'s parameter list (host,
  cookie, flags, inamt?) — I referenced it as the builder but did not enumerate its params since
  I only saw it named in doc comments, not called; (c) `wallet.send(amount, address, spendAll,
  …)` vs `wallet.send(tx, …)` overloads — both are used (one-shot vs. complete-then-broadcast)
  and could be enumerated; (d) `BIND_OUTPUT_PARAMETERS` semantics in depth (it finalizes template/
  group params on outputs — worth a precise definition once the libnexakotlin source is read).

- **Token authority spends connect here.** `USE_GROUP_AUTHORITIES` (in the flag table) is the
  completion-side counterpart to the authority-output material in `nexa-tokens-and-groups`; a future
  "spend a MINT/MELT authority to issue/destroy supply" worked example would tie the tokens skill
  to this completion pattern. Left out this pass because I only saw the flag referenced (a TODO in
  the wallet UI), not a full authority-spend construction.

- **What I did NOT touch:** Pass 6/7 additions left as-is; no version/framing changes; the NPL,
  flows, locktime, ktor, identity, and project-setup skills were not reopened (no tx-completion
  surface in them beyond the cross-refs already added). Editorial standards from Pass 3 upheld.

## Pass 9 — 2026-06-02 — Claude (Opus 4.8, 1M context)

**Consolidation pass.** No new technical content. Pass 3 was the first consolidation pass and
set the three editorial standards (deprioritize version specifics, de-anchor from named
applications, remove on-disk-vs-off-disk distinctions). The content passes that followed (4–8)
re-introduced a handful of audit artifacts — `<!-- PRIOR: ... -->` comments, `> **Revision
note:**` blocks, and one `⚠️ Review needed` flag — as they corrected mechanical errors and
sharpened library-boundary claims. This pass cleans up that accumulated audit trail and
re-applies the three reframings to anything that drifted, leaving the substantive technical
content untouched.

### Corrected (audit-trail removed, corrected content kept)
- `nexa-wallet-connection`: removed the Pass-5 `<!-- PRIOR -->` + revision note on the `/_share`
  handler (`updateWalletAddress` → `userNexaAddress.value`); the corrected code stays and the
  app-defined-field explanation survives as one plain sentence. Consolidated the Pass-5/Pass-7
  `flags` discussion (a `<!-- PRIOR -->`, a revision note, and a follow-up paragraph that
  *contradicted* the revision note) into a single clean statement: `flags` is a TDPP-protocol
  bitfield the wallet interprets, and the `TDPP_FLAG_*` constants live in libnexakotlin (not
  libnexaapp), with the per-bit table below. Removed the Pass-8 `<!-- PRIOR -->` + revision note
  on `createTdppUrl`; the corrected prose (the `/tx` callback route is yours, the push URI has a
  libnexakotlin builder) stays.
- `nexa-transaction-construction`: removed the Pass-5 `<!-- PRIOR -->` + revision note on the
  Pattern-2 `flags` notes (kept the corrected "constants live in libnexakotlin" guidance);
  deleted the Pass-7 `⚠️ Review needed` block at the "Looking up an output by index alone"
  anti-pattern, folding its one developer-relevant fact (there is no shuffle-control `flags`
  bit, so never rely on output position) into the anti-pattern's own prose; removed the Pass-4
  `<!-- PRIOR -->` on the mis-cased `nexa-npl-smart-contracts` cross-reference (the corrected
  spelling stays).
- `nexa-identity-and-addresses`: removed the Pass-4 `<!-- PRIOR -->` + revision note on the
  `p2pktAddressFromHash(acceptorHash, chain)` arity fix; the corrected two-arg call and the
  `parseTemplate(amount)`-vs-`0` explanation below it stay.
- `nexa-debugging-onchain-errors`: reframed the lone remaining Nexa-stack version pin — the
  `millinow()` ↔ `epochMilliSeconds()` rename row no longer cites "between 0.5.26 and 0.5.41";
  it now states the function was renamed in a libnexakotlin release and points to the
  POM-cross-check, consistent with how `nexa-project-setup` already frames the same rename.

### Flagged for review
- None. The `flags`-constants and `createTdppUrl` questions that the revision notes documented
  are settled (the constants/builder live in libnexakotlin; the per-bit semantics and the
  partial-offer idiom are documented in `nexa-wallet-connection` and `nexa-transaction-construction`
  Pattern 6). The no-shuffle-bit question is settled too (the verified flag set has no such bit),
  so it is stated as fact in the anti-pattern rather than carried as a flag.

### Deleted
- All `<!-- PRIOR: ... -->` comments, `> **Revision note:**` blocks, and the one `⚠️ Review
  needed` block that Passes 4–8 added to skill bodies. Their bookkeeping is preserved only here
  in the CHANGELOG; their developer-relevant substance is now inline prose.

### INDEX
- No change needed. Pass 3 already replaced the old "Ground-truth sources used when refining"
  (on-disk paths) with the "Where to find canonical sources" section — library names, Maven
  coordinates, and GitLab Maven project numbers framed for any developer — and Passes 4–8 did
  not regress it. Verified it still carries no on-disk paths or named applications.

### New skills created
- None. Per the consolidation brief.

### Notes for the next agent

**The three editorial standards from Pass 3 remain the law for skill bodies. Do not
re-introduce what this pass (and Pass 3) removed.** Restating them so they are not lost:

1. **Deprioritize version specifics.** Skill bodies do not pin Nexa-library version numbers.
   `nexa-project-setup`'s `[versions]` block is placeholders (`"<latest>"`) plus relationship
   comments; concrete numbers appear only where they mark a genuine API-surface change (the
   `millinow → epochMilliSeconds` rename — now framed as "renamed in a release," not with the
   specific pins) or a genuine version-specific behavior (the kotlinx-serialization 1.10.0 CBOR
   caveat). Setup sections point developers at the GitLab Maven registry; per-library URLs and
   project numbers are in `INDEX.md`. Trust the published POM, not a number copied into a doc.

2. **De-anchor from specific applications.** Skill bodies describe Nexa infrastructure
   (libnexakotlin, libnexaapp, NPL, scriptmachine, nexarpc, mpthreads, the `org.wallywallet:wew`
   library, the Wally wallet and its TDPP/nexid/Trickle Pay protocol, electrum) and *patterns*
   extracted from real apps — never the apps themselves by name. Note the distinction the
   content passes navigated correctly and that you must preserve: **the Wally wallet and Trickle
   Pay are protocol infrastructure** (the counterparty these skills integrate with) and may be
   named; a specific *application built on the stack* (a prediction market, a DEX, a demo app,
   an enterprise wallet *app*) may not. Patterns learned from such apps stay, rewritten with
   neutral domains (an order book, a marketplace listing, a vesting schedule, an Alice/Bob
   delegation contract). The CHANGELOG is the only place named apps may appear, and only for
   historical reasoning.

3. **Remove on-disk vs off-disk distinctions.** Skill bodies do not assume any checkout layout
   on the reader's machine. Name the library and its Maven coordinate / GitLab project so a
   future developer can find the source themselves; `INDEX.md` is the authoritative "where to
   look" map. Light pointers into *library* source files (e.g. "libnexakotlin's `cnxnmgr.kt`",
   "NPL's `opParseHelpers.kt`") are acceptable as "where to find" guidance; file-path citations
   into specific *apps* are not. (Note: `SerializationType.DISK` / "on-disk serialization
   format" in `nexa-transaction-construction` is a wire-format concept, not a machine-path
   reference — leave it.)

**Why this matters.** The maintainer's Nexa libraries iterate faster than this documentation;
the checkouts a prior agent had are not what a future developer will have; and the apps these
skills were validated against will not be the apps future agents are asked to build. A pass that
re-introduces version pins, named-app provenance, or on-disk file references is restoring noise
that two consolidation passes have now spent effort removing — and the pattern this pass cleaned
up shows how it creeps back: a content pass makes a correct, well-evidenced fix and wraps it in a
`<!-- PRIOR -->`/revision-note for traceability. That is reasonable mid-pass, but the audit
wrapper is internal bookkeeping; fold the substance into the prose and record the history here,
not in the skill body, before the corpus ships.

**Substance preserved.** No technical content, pattern, or anti-pattern was added or removed this
pass — only audit framing and the one residual version pin. The biggest "where did this go?"
lookups: the `updateWalletAddress`/`userNexaAddress` correction is now a single sentence after
the hardened `/_share` handler; the `TDPP_FLAG_*` "in libnexakotlin not libnexaapp" fact is one
sentence in `nexa-wallet-connection`'s cheat-sheet notes and one bullet in
`nexa-transaction-construction` Pattern 2; the no-shuffle-bit fact is in the "Looking up an output
by index alone" anti-pattern's *Wrong* gloss.

**Untouched targets that remain.** The "Supporting files in this folder (to be created)" stubs
at the bottom of every skill are still empty (`dslReference.md`, `walletUriFormats.md`,
`groupIntrospectionReference.md`, etc. — all writable from source by an agent who has it). The
asset-ownership-via-TDPP-`/assets` pattern (flagged by Passes 5 and 7, connecting
`nexa-tokens-and-groups` to the wallet `/assets` callback) and a full authority-spend worked example
(flagged by Pass 8) are the most obvious remaining content-side gaps. None were in scope for a
consolidation pass.

## Pass 10 — 2026-06-02 — Claude (Opus 4.8, 1M context)

**Content + correction pass, grounded in a production Nexa app's server source.** The new oracle
this pass was a real, compiling NFT-marketplace server (the "Nexa Warriors / NiftyArt"
marketplace) that integrates the full TDPP/asset stack against the published Nexa libraries.
Reading its TDPP controller, asset service, trade/offer handler, and its `useful.kt` constants let
me (a) **correct a claim repeated across two skills that would break a downstream build**, (b)
close the asset-ownership-via-`/assets` gap that Passes 5 and 7 explicitly flagged, and (c) add
the order-book "half-tx" swap idiom and the token-description-document shape. Per the standing
editorial standards, the app's name appears only here in the CHANGELOG; all skill-body additions
are framed neutrally (no app name, file paths, or version pins), and the app's own custom helpers
(`NiftySession`, `TricklePayAssetInfo`, `checkAssetChallenge`, etc.) are described as
app-implemented protocol code, not presented as library APIs — only genuine libnexakotlin calls
(`NexaTxOutput(chain, BCHserialized(...))`, `script.groupInfo`, `GroupId.isFenced()`) are named.

### Corrected
- `nexa-wallet-connection` and `nexa-transaction-construction`: the claim (introduced Pass 7, settled
  Pass 9) that the `TDPP_FLAG_*` constants "live in **libnexakotlin** — reference them from
  `org.nexa.libnexakotlin`." **Evidence against:** the production server defines these constants
  itself as plain `const val`s (`NOFUND=1, NOPOST=2, NOSHUFFLE=4, PARTIAL=8, FUND_GROUPS=16,
  HIDE_ASSET_DETAILS=32`) in its own utils file *while* doing `import org.nexa.libnexakotlin.*` —
  so they are demonstrably **not** an importable library symbol a server can rely on; they are a
  TDPP **wire-protocol** bitfield both ends agree on. Telling a developer to
  `import org.nexa.libnexakotlin.TDPP_FLAG_NOFUND` would not resolve. Reframed to "define your own
  constants / write the literal `flags=N`," with the verified integer bit values inline. Prior
  text preserved in `<!-- PRIOR -->` + `> **Revision note:**` in each skill. Confidence: high on
  "not safely importable from the chain artifact"; the Pass-5 finding (absent from libnexaapp)
  still holds and is subsumed. (Where the wallet itself keeps the same bit values is a wallet
  internal — not something a server imports.)
- `nexa-transaction-construction` "Looking up an output by index alone": the assertion that the TDPP
  flag set "has **no** shuffle-control bit." **Evidence against:** `TDPP_FLAG_NOSHUFFLE` (`4`)
  exists and is used in production (a half-tx offer sets `NOFUND|NOPOST|NOSHUFFLE|PARTIAL`).
  Corrected with `<!-- PRIOR -->` + revision note; the anti-pattern's core advice (identify the
  output by content) is **kept and reinforced** — even with `NOSHUFFLE` set the wallet still adds
  funding/change outputs, so absolute index is still not guaranteed unless the protocol fully
  fixes the layout. Confidence: high (the constant exists and is in active use).

### Added
- `nexa-wallet-connection` § "The TDPP transaction `flags` bitfield": a bit-value column and two
  previously-undocumented flags — `TDPP_FLAG_NOSHUFFLE` (`4`, preserve input/output order for
  offer protocols) and `TDPP_FLAG_HIDE_ASSET_DETAILS` (`32`, approval-UI presentation hint); the
  canonical partial-offer combination `NOFUND|NOPOST|NOSHUFFLE|PARTIAL` (`=15`); and the optional
  `&reason=<url-encoded>` `/tx` push param the wallet shows on its approval screen.
- `nexa-wallet-connection` `/tx` idempotency note: sharpened from "make it idempotent" to the
  concrete realization — the wallet may GET `/tx` **more than once**, so guard the state-advancing
  continuation with a per-session "already processing/done" flag and return a benign response on
  the duplicate (verified: the production `/tx` handler uses exactly this guard).
- `nexa-tokens-and-groups` Pattern 8 (new) — **proving wallet token/NFT ownership via the TDPP
  `/assets` flow**, the gap Passes 5 and 7 both flagged. Covers the request (`af` asset filter as a
  script-template pattern — two `OP.TMPL_DATA` placeholders match any grouped output; `chalby`
  per-session challenge), and server-side consumption: deserialize each prevout with
  `NexaTxOutput(chain, BCHserialized(...))`, read its group via `script.groupInfo(amount)`, skip
  ungrouped and **fenced** (`GroupId.isFenced()`) groups, verify the ownership-proof tx against the
  issued challenge, and accumulate holdings by `GroupId` across multiple UTXOs. Explains *what the
  proof is* (a version-masked, un-broadcast tx committing to your host + single-use challenge in an
  `OP_RETURN`) and why it's replay-safe — and that a bare `outpointHash` is not proof.
- `nexa-tokens-and-groups` Pattern 7: the off-chain **token-description document (TDD)** shape — a
  JSON array `[ {ticker,name,summary,icon[,decimal_places]}, "<base64 sig>" ]` — tying the
  existing `decimal_places`/display-amount discussion to a concrete document format (verified
  shape; NFTs omit `decimal_places`).
- `nexa-transaction-construction` Pattern 6: the **half-tx swap-offer idiom** (order books / atomic
  asset trades) — build and PARTIAL-sign a tx carrying *both* the token input and the
  payment-demand output, push with `NOFUND|NOPOST|NOSHUFFLE|PARTIAL` for the counterparty's wallet
  to complete, correlate the round trip with a stashed proposal + continuation keyed by `cookie`,
  and re-validate the returned tx against the original offer before broadcasting. This is the
  canonical reason `NOSHUFFLE` exists.
- Cross-references and trigger keywords: wallet↔tokens links for the `/assets` flow; tokens skill
  now points to `nexa-wallet-connection` for the wallet side; extended trigger lists in
  `nexa-wallet-connection` (`/assets`, `TDPP_FLAG_*`, `NOSHUFFLE`, asset-ownership),
  `nexa-tokens-and-groups` (wallet ownership, `/assets`, TDD/decimals), and
  `nexa-transaction-construction` (half-tx swap offer, order book, `NOSHUFFLE`,
  `txProposal`/`txContinuation`). Four new symptom rows in `nexa-debugging-onchain-errors` (owned
  token missing from a portfolio = fenced/ungrouped/failed-proof; unresolved `TDPP_FLAG_*` import;
  `/tx` advancing state twice). `INDEX.md` tokens row extended.

### Flagged for review
- None left in place. I did **not** assert that `TDPP_FLAG_*` are *definitely absent* from every
  libnexakotlin version (I can't prove a negative across versions) — instead I corrected to the
  robustly-true and actionable form ("not a guaranteed importable symbol; define your own"), which
  is correct whether or not some library version also re-exports them.

### New skills created
- None. The asset-ownership material is the token/wallet slice of two existing skills; the
  swap-offer idiom is core tx-completion material. Both went into existing skills per the corpus's
  standing preference against fragmentation.

### Notes for the next agent

- **A compiling production app is the strongest oracle for "what a server actually imports."** The
  `TDPP_FLAG_*` correction is the cautionary tale of this pass: Passes 7–9 reasoned (from the
  wallet's behavior) that the constants "live in libnexakotlin," and Pass 9 enshrined it as
  settled. But the consumer these skills are written *for* — a server — defines its own constants
  and could not import them from the chain artifact. When a claim is "you can import X from library
  Y," the cheapest verification is a real app's import block, not inference from the counterparty.

- **`GroupId.isFenced()` is a real libnexakotlin call I used but did not fully characterize.**
  Fenced groups are skipped when enumerating a user's holdings; I documented the *skip*, not what
  "fenced" formally means (a restricted/system group class). A libnexakotlin-source pass could
  define it precisely and note which other group-enumeration paths should honor it.

- **The authority-spend worked example (flagged Pass 8) is still open.** This app trades and
  enumerates tokens but I did not see a MINT/MELT authority *spend* construction in what I read, so
  I left that gap. `USE_GROUP_AUTHORITIES` (tx Pattern 6) remains the only pointer.

- **Verified-but-unwritten from this app, for a future pass:** (a) the exact
  `createSellNftHalfTx` / `txCompleter` parameter arrangement for a token half-tx (this pass added
  the *idiom* and flags, not a pinned signature — the trailing-arg ambiguity Pass 8 flagged is
  still unresolved); (b) the asset-challenge proof tx's precise byte layout (version mask value,
  the OP_RETURN `[host, interleaved-challenge]` encoding where the challenge is the odd-indexed
  bytes) — enough is in Pattern 8 to verify a proof, but a full "build the challenge tx" spec would
  let a wallet-side implementer reproduce it; (c) this app uses a **raw-WebSocket** client-update
  channel (`session.pushToClient(json)` + a custom `Sockets` plugin) rather than libnexaapp's
  `flowConnector` — a second, lower-level reactive-update style the corpus doesn't document, but I
  left it out because it is app-specific plumbing, not a library API.

- **Editorial standards upheld.** No version pins, named apps (outside this CHANGELOG), or on-disk
  paths in any skill body. The bit *values* I added are stable wire-protocol facts (the corpus
  already wrote `FUND_GROUPS (value 16)` literally), not library version numbers. All prior
  `<!-- PRIOR -->`/revision history from earlier passes was left intact; the two new `<!-- PRIOR -->`
  blocks this pass follow the same convention and a future consolidation pass can fold their
  substance into prose as Passes 3/9 did.

## Pass 11 — 2026-06-02 — Claude (Opus 4.8, 1M context)

**Content pass: closed the token MINT / authority-spend gap that Passes 4, 8, and 10 all left
open.** Grounded in a production NFT-marketplace server's actual minting code (read directly:
the `mintNFT`/`mintFreeNFT` half-tx builders, the authority-pre-split routine, the
subgroup-derivation helpers, and the served token-description document + its README genesis
recipe). Every library fact below was verified against real `org.nexa.libnexakotlin.*` calls as
used in a compiling app, not inferred. Per the editorial standards: the app's name appears only
here; all skill-body additions are framed neutrally ("a marketplace mints an NFT", "content-
addressed NFT"), no file paths or version pins, and the app's own helpers (`possibleNfty`,
`splitMintAuthorities`, etc.) are presented as patterns, not library APIs — only genuine
libnexakotlin symbols are named.

### Added
- `nexa-tokens-and-groups/SKILL.md` **Pattern 9 (new) — Minting tokens/NFTs by spending a MINT
  authority.** The worked example flagged open since Pass 4. Covers: the self-funded mint
  (`txCompleter(... FUND_NATIVE|FUND_GROUPS|SIGN|USE_GROUP_AUTHORITIES|NO_BATON_AUTHORITIES)`); the
  **mint-on-demand half-tx** idiom (add a template-spendable grouped output `SatoshiScript.grouped(...)
  + OP.TMPL_SCRIPT` so the completer pulls a mint authority → remove the NFT output so the issuer
  doesn't sign it → add the buyer's fee output → `SIGN|PARTIAL` so the issuer's signature is
  contingent on the fee → re-add the NFT output and push); and the **authority-pool** operational
  pattern (pre-split one authority into many for concurrency; catch not-enough-token-balance,
  split, retry). Cross-links the half-tx swap idiom in `nexa-transaction-construction` Pattern 6.
- `nexa-tokens-and-groups` Pattern 1: **content-addressed subgroup NFT ids** — `parentGroupId.subgroup(
  hash256(assetBytes))` for a deterministic, DB-independent, naturally-deduping group id; the
  `gid.subgroupData()` recovery call; and a `looksLikeNft()` recognition predicate combining the
  three library checks `!isAuthority() && isSubgroup() && !groupId.isFenced()` (resolves Pass 10's
  open "what does `isFenced` gate" note from the enumeration side).
- `nexa-tokens-and-groups` Pattern 5: how to **build an authority output** — the
  `groupedLockingScript(gid, x: Long)` overload does double duty (positive `Long` = token quantity;
  authority-flags `.toLong()` = authority output), which the skill described as a concept but never
  showed as a call.
- `nexa-tokens-and-groups` Pattern 7: how the **token-description document binds at genesis** — the
  genesis tx commits the document's SHA-256, genesis is typically done with the node's token tooling
  (`token new <addr> <ticker> <name> <url> <sha256>`), and the trailing-newline trap that makes the
  served bytes' hash diverge from the on-chain commitment. (Distinguished the served `[doc, sig]`
  form from the genesis-committed metadata object.)
- `nexa-transaction-construction` Pattern 6: a `NO_BATON_AUTHORITIES` row in the `TxCompletionFlags`
  table (verified member, previously absent), paired with `USE_GROUP_AUTHORITIES` for mints.
- `nexa-tokens-and-groups` security: a bullet on `NO_BATON_AUTHORITIES` (don't burn the master baton on
  a routine mint) and keeping an authority pool for concurrency.
- `nexa-debugging-onchain-errors`: a symptom row for mint-time "wallet cannot access tokens" /
  not-enough-token-balance → missing `USE_GROUP_AUTHORITIES` or exhausted authority pool.
- Trigger keywords (`USE_GROUP_AUTHORITIES`, `NO_BATON_AUTHORITIES`, `subgroupData`, content-
  addressed NFT, mint authority pool, mint-on-demand, "mint an NFT only when the buyer pays") and
  the `INDEX.md` tokens row, extended to surface the new minting/genesis content.

### Corrected
- None. I made no overwrite of any prior factual claim; everything this pass is additive.

### Flagged for review
- None left in place. The one thing I deliberately did **not** assert: an exact `txCompleter`
  signature (Pass 8's open question about its trailing positional arg is unchanged — every call I
  read used `txCompleter(tx, 0, <flags>)`, so I documented that three-arg shape and the flag
  semantics, not the fuller named-arg form Pattern 6 still leaves open).

### New skills created
- None. Minting/authority-spend is the token-issuance slice of `nexa-tokens-and-groups`, so it went in
  as Pattern 9 + supporting tweaks — consistent with the corpus's standing preference against
  fragmenting it.

### Notes for the next agent

- **This pass's oracle was a libnexakotlin-only app — it does not use libnexaapp at all** (its only
  Nexa deps are libnexakotlin, mpthreads, scriptmachine). It hand-rolls its own session class (no
  `NexaAppSession`), its own client push channel (a plain Ktor `/ws` WebSocket with a per-session
  fan-out, **not** `flowConnector`), and its own wallet HTTP long-poll — none of it built on
  libnexaapp. I deliberately added **nothing** to `nexa-server-state-and-flows` or
  `nexa-ktor-server-integration` from it: the WebSocket/session machinery is generic Ktor, not Nexa
  infrastructure, and the corpus's flows/Ktor skills are correctly grounded in libnexaapp (the
  framework most apps will use). The one corpus-relevant meta-fact — that an app *can* skip
  libnexaapp and drive libnexakotlin directly — is true but not worth a skill edit; it would just
  dilute the libnexaapp-centric guidance that serves the common case. Recorded here for context.
  *Caveat for version-sensitive readers:* because this oracle is on an older libnexakotlin line than
  the apps that grounded Passes 1–9, treat the newly-named flag `NO_BATON_AUTHORITIES` as "a real
  `TxCompletionFlags` member with these semantics," not as a version-pinned guarantee — confirm
  against your own artifact if a mint behaves unexpectedly.

- **Verified-but-unwritten, for a future pass:** (a) `signInput(tx, index: Long, sigHashType:
  ByteArray)` — selective per-input signing, the lower-level primitive under a `PARTIAL` sign;
  useful if someone needs to sign exactly one input of a multi-party tx by hand rather than via
  `txCompleter`. (b) The pre-broadcast partial-tx **validation** path (asking a P2P/full node to
  `TxVal` a half-completed tx before relaying) — this app validates the buyer-completed tx against
  the node before broadcasting; a "validate a returned half-tx before you broadcast it" note would
  strengthen `nexa-transaction-construction` Pattern 6's "re-validate the returned tx" advice with a
  concrete mechanism, but I only saw it named, not its exact signature. (c) `OP.TMPL_SCRIPT` /
  `OP.TMPL_SKIP` as the opcodes that mark template-spendable output slots — I used `TMPL_SCRIPT` in
  Pattern 9 but did not write a general "template output placeholder opcodes" note; that belongs in
  `nexa-npl-smart-contracts` or a `dslReference.md` if it grows.

- **What I did NOT touch:** I left the two Pass-10 `<!-- PRIOR -->`/revision-note blocks (in
  `nexa-wallet-connection` and `nexa-transaction-construction`) intact — folding them is a consolidation
  pass's job (Passes 3/9), not a content pass's, and Pass 10 explicitly deferred them. No
  version/framing changes; the project-setup, locktime, NPL-core, identity, flows, and Ktor skills
  were not reopened (no minting surface in them beyond the cross-refs already added). Editorial
  standards from Pass 3 upheld throughout.

- **Still the biggest backlog:** the "Supporting files (to be created)" stubs across every skill
  remain empty. `nexa-tokens-and-groups/examples/` now has an obvious fourth entry to write up (a
  subgroup-minted NFT, the half-tx mint, the authority-pool split), and `groupIntrospectionReference.md`
  could now also carry the `subgroup`/`parentGroup`/`subgroupData`/`isFenced` family.

## Pass 12 — 2026-06-02 — Claude (Opus 4.8, 1M context)

**Content pass: created the missing `nexarpc` (full-node JSON-RPC client) skill.** The oracle
this pass was the **`org.nexa:nexarpc` library's own source** — the very repo this refinement
runs in (`NexaRpc.kt` interface + `JvmNexaRpc.kt` implementation + its test file). `nexarpc` is
listed in `INDEX.md` as a first-class ecosystem library and is referenced in passing by
`nexa-transaction-construction` (the `sendrawtransaction` broadcast alternative) and
`nexa-debugging-onchain-errors` (the "already in mempool" decoder), but the corpus had **no skill**
documenting how to actually use the node-RPC client. This is the clearest orthogonal gap left:
talking to a full node you operate is conceptually distinct from the wallet/contract/token
skills, and `INDEX.md` already treats nexarpc as its own library. Every API fact below was read
from the library's actual interface/implementation (exact signatures, the blocking-wrapper
model, the `HashId` reversal convention, the `NexaRpcException(message, code)` model, the
`getstat` series set), not inferred. Editorial standards held: no version pins (placeholders +
the GitLab project number), no named applications, no on-disk paths — only library symbol names.

### New skills created
- `nexa-rpc-node-client/SKILL.md`: the `org.nexa:nexarpc` JSON-RPC client to a node you operate.
  Covers the mental model (owner-only node connection; *not* the SPV/P2P path; blocking
  interface that runs each call to completion and opens a fresh `Connection: close` HTTP request
  per call; `NexaRpcException` for all failures; typed methods vs the `calls`/`callje` escape
  hatch; the `HashId` display-reversal convention and how it contrasts with libnexakotlin's
  `Hash256`), setup (`NexaRpcFactory.create(url, username, password)`, the project-`38119368`
  Maven repo, often a `testImplementation`), ten core patterns (create; chain/mempool reads;
  `sendrawtransaction` vs `enqueuerawtransaction`; the three tx-lookup shapes
  `getrawtransaction`/`gettransactiondetails`/`gettransaction`; the escape hatch; node
  statistics `getstat`/`getstatlist` + the typed `getstat{Int,IntRange,Double,DoubleRange}`
  helpers; wallet ops; token-issuance RPCs; message signing; regtest `generate`/`invalidateblock`),
  anti-patterns (blocking on a coroutine dispatcher; double-reversing a `HashId.toHex()`;
  `gettransaction` on a non-wallet tx; reading lossy `Unspent.amount` Double instead of
  `.satoshi` Long; catching the wrong exception type; expecting a pooled connection; committing
  regtest-default credentials), and security (RPC creds = full node control; never expose the
  RPC port; 401 handling; node wallet is shared mutable state; regtest-only powers). Added to
  `INDEX.md` (skill table + the relationship map) following the fixed section structure.

### Added
- `nexa-transaction-construction/SKILL.md`: in the RPC-broadcast alternative, noted the
  `ByteArray` overload + `HashId` return of `sendrawtransaction` and introduced
  `enqueuerawtransaction` (relay without full verification); added a `nexa-rpc-node-client`
  cross-reference in Related skills.
- `nexa-tokens-and-groups/SKILL.md`: tied the existing Pattern-7 `token new` node-CLI genesis recipe
  to its programmatic form `rpc.tokenNew(...)`, and added a `nexa-rpc-node-client` Related-skills
  entry for the token-issuance RPC family.
- `nexa-locktime-cltv/SKILL.md`: added that a node operator can read the exact MTP directly via
  `getblock(...).mediantime` (rather than approximating with wallet tip time), with a
  `nexa-rpc-node-client` cross-reference in Related skills.
- `nexa-debugging-onchain-errors/SKILL.md`: a "Decoding `Unauthorized (bad rpc username/password)`"
  section, two new symptom-table rows (RPC 401; un-wrapped RPC / catch `NexaRpcException` not
  `IOException`), and a `nexa-rpc-node-client` pointer in the RPC-broadcast decoder.

### Corrected
- None. All edits this pass are additive (a new skill plus cross-references); no prior factual
  claim was overwritten, and no prior `<!-- PRIOR -->`/revision blocks were touched (the two
  Pass-10 audit blocks in `nexa-wallet-connection`/`nexa-transaction-construction` remain intact for
  a future consolidation pass to fold, as Pass 11 also left them).

### Flagged for review
- None placed in skill bodies. (One library-internal bug I noticed while grounding — out of
  scope for skill content, reported to the maintainer separately — is recorded under "Notes"
  below so it isn't lost.)

### Notes for the next agent

- **A compiling library's own source is an excellent oracle, but watch for stale ancillary
  docs.** The nexarpc `README.md` still shows the *old* package/coordinate (`import
  Nexa.NexaRpc.*`, Gradle `("Nexa","NexaRpc",…)`) while the actual source declares `package
  org.nexa.nexarpc` and `INDEX.md` lists `org.nexa:nexarpc`. I documented the current `org.nexa.*`
  forms and noted the migration as a one-line API-evolution fact (permitted by editorial standard
  2), the same way the corpus records the libnexaapp `Nexa.npl` → `org.nexa.npl` rename. Trust the
  package declaration over the README.

- **A genuine library bug, reported to the maintainer, deliberately kept OUT of the skill.** In
  `JvmNexaRpc._tokenBalance` the implementation calls `_calls("token", listOf("melt", groupId))`
  — almost certainly a copy-paste error (it should be `"balance"`, not `"melt"`; the surrounding
  `_tokenMint`/`_tokenMelt`/`_tokenMintage`/`_tokenSend` each use their own verb). As written,
  `tokenBalance(...)` would invoke the *melt* path. Per editorial standard, "does version X still
  have bug Y" belongs in an issue tracker, not a skill body, so `nexa-rpc-node-client` documents
  `tokenBalance` by its intended contract only. Flagging here (and to the user) so it isn't lost.

- **The new skill's two "Supporting files (to be created)" stubs** (`rpcMethodReference.md`,
  `regtestHarness.kt`) are writable straight from the library source by any agent with this repo
  checked out — the full typed-method-to-RPC mapping and a regtest test fixture. They join the
  long-standing empty-stub backlog every prior pass has flagged.

- **What I did NOT touch.** No version/framing changes; Pass 3/9's three editorial standards held
  throughout. I did not reopen `nexa-project-setup`, `nexa-ktor-server-integration`,
  `nexa-wallet-connection`, `nexa-identity-and-addresses`, `nexa-server-state-and-flows`, or
  `nexa-npl-smart-contracts` beyond the one tokens/tx/locktime/debugging cross-references above — the
  nexarpc surface intersects them only through links, which is where the value is. The
  `nexa-project-setup` `[versions]`/`[libraries]` block already carried the `nexa-rpc` /
  `org.nexa:nexarpc` coordinate and the project-`38119368` repo line, so it needed no edit.

## Pass 13 — 2026-06-02 — Claude (Opus 4.8, 1M context)

**Content pass: created the missing `scriptmachine` (script-VM execution & testing) skill.** The
oracle this pass was the **`org.nexa:scriptmachine` library's own source** — the very repo this
refinement runs in (`scriptmachine.kt` + `init.kt` + `nativebinding.kt` + its `Test.kt`). The
corpus documented the *whole* application stack (project setup → wallet → tx → contracts → tokens →
RPC → flows → debugging) but had **no skill for using the script VM to run/replay/debug script
execution locally** — i.e. the single most direct way to "ensure more complex or contract
transactions execute correctly" before broadcasting, which the task brief called out specifically.
`scriptmachine` was listed in `INDEX.md` as a first-class ecosystem library and referenced by
`nexa-npl-smart-contracts` only for *compiling* the DSL; nothing covered its `ScriptMachine`
*execution/debugging* surface. This is the clearest orthogonal gap left: offline script-VM
simulation is conceptually distinct from building a tx (`nexa-transaction-construction`), from the
contract DSL (`nexa-npl-smart-contracts`), and from broadcasting to a node (`nexa-rpc-node-client`) — the
same "its own library, its own channel" logic that justified the Pass-12 nexarpc skill. Every API
fact below was read from the library's actual interface/implementation and its test suite (exact
constructor set, the `eval`/`next`/`step`/`cont` semantics, the `"No error(0)"` / clean-main-stack
success criteria, the `getStackItemText` string grammar, `ScriptMachineResources` fields,
`setLimits`, breakpoints, `analyze2Tx`, `parseTemplateSpend`, the `tolerant` default,
`POST_UPGRADE_MANDATORY_SCRIPT_VERIFY_FLAGS`), not inferred. Editorial standards held: no version
pins (placeholders + the GitLab project number), no named applications, no on-disk paths — only
library symbol names. The native `libnexa.so` dependency is described as infrastructure (the node's
JNI cashlib), not a machine-path reference.

### New skills created
- `nexa-script-machine-testing/SKILL.md`: local script-VM execution & testing via `org.nexa:scriptmachine`.
  Covers the mental model (a JNI binding to the full node's *actual* script VM, so a clean local run
  predicts on-chain behavior; the three-script template-spend model; what the VM checks — script
  validity under the mandatory verify flags, including introspection and the CLTV stack comparison —
  versus what it does **not** — fees, mempool policy, chain MTP; JVM-only/native; the three
  instantiation modes), setup (`org.nexa:scriptmachine`, project `46299034`, usually
  `testImplementation`; the top-level `Initialize()`; the `libnexa.so` prerequisite; the
  README-coordinate/package migration as a one-line API-evolution fact), seven core patterns (bare
  opcode `eval`; single-input spend `ScriptMachine(tx, inputIdx, utxo)`; two-tx replay
  `ScriptMachine(parentHex, childHex)` + the override-template debug form; a test-shaped
  `spendError(...)` pass/fail helper; step/breakpoint/`getState` inspection; `getResources`/`setLimits`
  resource-budget checks; `parseTemplateSpend`), anti-patterns (misreading `eval`'s Boolean as
  pass/fail; forgetting `Initialize()`/`libnexa.so`; not `delete()`-ing native handles; using the
  context-free machine for introspection scripts; reading the stack from the wrong end; expecting the
  VM to catch fee/MTP problems; `tolerant`-mode swallowing failures; trusting the stale README
  coordinate), and security (clean-VM-run ≠ safe-to-settle; native code = full-process trust; don't
  auto-broadcast from a replay pipeline; test against realistic resource limits). Added to `INDEX.md`
  (skill table + relationship map + enriched the scriptmachine canonical-source line), following the
  fixed section structure. Rationale: the most obvious remaining content gap, directly matching the
  task brief's emphasis on running application tests for contract/complex transactions, and now
  writable from verified source.

### Added
- `nexa-npl-smart-contracts/SKILL.md`: a paragraph after Pattern 2 noting the same
  `org.nexa.scriptmachine.Initialize()` that compiles the DSL also drives the VM, so a built spend
  should be replayed and asserted clean before broadcasting; plus a Related-skills entry.
- `nexa-transaction-construction/SKILL.md`: a Related-skills entry — replay parent+child through the VM
  before broadcasting, with the caveat that the VM doesn't model the fee/0-conf/MTP rules.
- `nexa-debugging-onchain-errors/SKILL.md`: a new core-pattern subsection "Reproducing a script-verify
  failure locally in the script VM" (replay → exact failing opcode + stack), a pointer to it from the
  `OP_EQUALVERIFY` decoder, two new symptom-table rows (`UnsatisfiedLinkError`/`libnexa.so`; "can't
  tell which `verify` failed" → replay in the VM), and the `UnsatisfiedLinkError`/`--enable-javacashlib`
  trigger string.
- `nexa-project-setup/SKILL.md`: a paragraph in the mental model documenting `scriptmachine` as the
  JNI-bound, JVM-only library whose `Initialize()` needs `libnexa.so` (built with
  `--enable-javacashlib`) at test time — a genuine "tests won't even start" setup gotcha the skill
  lacked; plus a Related-skills entry.
- `nexa-tokens-and-groups`, `nexa-rpc-node-client`, `nexa-locktime-cltv`: Related-skills cross-references to
  the new skill (token covenants are prime VM-replay candidates; the VM is the offline complement to
  regtest broadcast; the VM evaluates the CLTV stack op but not chain MTP).

### Corrected
- None. Everything this pass is additive (a new skill plus cross-references). No prior factual claim
  was overwritten; the two Pass-10 `<!-- PRIOR -->`/revision-note blocks in `nexa-wallet-connection`
  and `nexa-transaction-construction` were left intact for a future consolidation pass to fold (as
  Passes 11 and 12 also left them).

### Flagged for review
- None placed in skill bodies. Two honesty notes recorded here instead:
  1. I did **not** pin the exact success semantics of the multi-script `next()` loop terminal across
     *every* constructor variant — the constructors set up the script-execution cursor differently
     (the two-tx and single-input `advance=true` forms pre-run satisfier+constraint during
     construction; the no-context triple-script form starts at the first script). To stay correct I
     grounded each documented recipe in a pattern that appears verbatim in the library's own test file
     and framed success by the asserted signals (`scriptErr == "No error(0)"`, `mainStackAt(0) == ""`,
     failure ⇒ `scriptErr` contains `"failed"`, step-loop end ⇒ `"completed"`) rather than asserting a
     single universal control-flow shape. A future pass that runs the suite could add a precise
     "how many `next()` calls per constructor" note.
  2. I describe the VM as checking script validity but not fee/MTP/mempool policy. I'm confident on
     the fee/mempool boundary (those are not script-VM concerns) and on the MTP boundary (the VM has
     the tx but not chain median-time-past); I deliberately did **not** claim precisely which
     transaction-level finality checks (e.g. the `nSequence`-final-disables-locktime rule) execute
     inside script eval vs. outside it, since I did not exercise that path — the skill routes finality
     to `nexa-locktime-cltv` rather than over-specifying.

### Notes for the next agent

- **A compiling library's own test file is an excellent worked-example oracle.** `Test.kt` in this
  repo is the canonical usage of every `ScriptMachine` entry point; I lifted the documented recipes
  from it (the `ScriptMachine(tx, 0, utxo, true)` + `next()` + clean-stack check, the
  `ScriptMachine(parentHex, childHex)` + `step()`-loop, the `setLimits(maxStackUse = 3)` →
  "Stack total length limit exceeded" assertion, the `eval(OP.push(1), OP.push(2), OP.ADD)` →
  `"BYTES 1 03h 3"`). I did **not** execute the suite this pass (it needs the GitLab Maven artifacts
  and a loadable `libnexa.so`), so the recipes are source-grounded, not run-verified — a
  source-equipped agent who can build the repo could promote the test patterns to run-verified and
  fill the skill's "Supporting files" stubs (`contractSpendTestHarness.kt`, `stackItemFormat.md`,
  `opcodeStepThrough.md`) straight from the suite.

- **Verified-but-unwritten scriptmachine surface I left for a future pass:** (a) `clone()` vs `copy()`
  vs `dump()`/`fromDump()` — I mentioned them as forking/serialization tools but did not fully
  characterize the difference (`clone` wraps the native handle via `ScriptMachine.clone`; `copy`
  rebuilds state by re-loading stacks; `dump` produces a serializable `ScriptMachineDump` whose `sm`
  has a zeroed handle); a "snapshot/replay machine state" pattern could be its own subsection. (b) The
  register file (`setRegister`/`getRegister`/`setRegisterToBigNum`) and BMD (`bmd`/`setBMD`,
  `BIN2BIGNUM`) — I named them in triggers but documented only lightly; the bignum/BMD interaction has
  real subtleties (sign-magnitude, the trailing `80`/`00` sign byte) visible in `testRegisters`/
  `testBin2BigNumNegative`/`testDebugger`. (c) `MachineState`'s `scriptType` field is constructed from
  the internal `evaling` string ("satisfier"/"constraint"/"template"/"all scripts completed") — worth a
  note if someone documents the multi-script driver precisely.

- **`SatoshiScript.p2t(chain, templateHash160, constraintHash160, visibleArgs)`** is a libnexakotlin
  P2T builder I used in Pattern 2 to construct the prevout for a spend test; it is the in-test analogue
  of the `Pay2TemplateDestination`/`P2T` helper in `nexa-npl-smart-contracts` Pattern 4. A future pass
  could cross-link the two builders explicitly if it verifies they produce identical locking scripts.

- **What I did NOT touch and why.** No version/framing changes; Pass 3/9's three editorial standards
  held throughout. I did not reopen `nexa-ktor-server-integration`, `nexa-wallet-connection`,
  `nexa-server-state-and-flows`, or `nexa-identity-and-addresses` — the script-VM surface doesn't intersect
  them (they're the server/wallet/UI layers, not script execution). I left the two Pass-10
  `<!-- PRIOR -->`/revision-note blocks intact (folding them is a consolidation pass's job). The
  long-standing "Supporting files (to be created)" stubs across every skill remain the biggest
  mechanical backlog; the new skill adds three more, all writable from this repo's source + test file.

- **Test-source-set framing (maintainer steer, applied within this pass).** The script VM is a
  **development/test-time** tool that belongs in `src/test` (declared `testImplementation`), exactly
  like the NPL `.compile()` step — *not* an inline step in the production send path. An initial draft
  leaned on "verify the spend before broadcasting," which can misread as a live pre-broadcast hook.
  I sharpened this everywhere: the new skill's lead-in, Mental model, and Setup now state the
  test-source-set placement outright; a new anti-pattern ("Running the VM inline in production / the
  live send path") mirrors `nexa-npl-smart-contracts`'s "compiling NPL at server startup"; and the
  cross-references in `nexa-npl-smart-contracts`, `nexa-transaction-construction`, `nexa-tokens-and-groups`,
  `nexa-rpc-node-client`, and `INDEX.md` were reworded from "before broadcasting" to "write a test
  that…/test-source-set/while developing." Keep this framing: production code builds and broadcasts a
  spend it has *already* tested; it does not run the VM at send time.

## Pass 14 — 2026-06-02 — Claude (Opus 4.8, 1M context)

**Content pass: hardened the Pass-13 `nexa-script-machine-testing` skill against the library's own
test suite, closing the two items Pass 13 explicitly left open.** Pass 13 wrote the scriptmachine
skill from the library's *source* but flagged in its notes that it was "source-grounded, not
run-verified," and deferred two specific things: honesty note #1 (it deliberately did **not** pin
the multi-script `next()` driver's control-flow / "how many `next()` calls per constructor") and
note (a) (`clone()` vs `copy()` vs `dump()`/`fromDump()` named but not characterized). This repo
*is* the `org.nexa:scriptmachine` library, so its `src/test/kotlin/Test.kt` — the maintainer's own
assertion suite — is a stronger oracle than source reading: every behavior added below is grounded
in a `check(...)` that the suite asserts at runtime. I read all four source files
(`scriptmachine.kt`, `init.kt`, `nativebinding.kt`, `Test.kt`) and cross-checked the entire skill;
the existing Pass-13 content held up accurately (the `"No error(0)"`/clean-main-stack success
signals, the `getStackItemText` grammar, the `ScriptMachineResources` fields, `setLimits`,
breakpoints, `analyze2Tx`, `parseTemplateSpend`, the `tolerant` default all match the code), so the
only changes are additive — two new patterns plus trigger keywords.

### Added
- `nexa-script-machine-testing/SKILL.md` **Pattern 8 (new) — Driving the full three-script spend with
  `next()`.** Resolves Pass 13's honesty note #1. Documents `next(runNow)`'s
  `Triple<String?, String, SpecialOperation>` return (`.first` = which script ran /
  `"all scripts completed"` / `"step"`; `.second` = that script's status string, or `"ok"`/`"error"`
  when single-stepping; `.third` = the `SpecialOperation` enum `NONE`/`ALT_STACK_LOADED`/`ALL_DONE`),
  and — the part Pass 13 wouldn't commit to — the per-constructor call count: the two-tx and
  single-input `advance = true` constructors pre-run satisfier+constraint during construction (one
  `next()`/step-loop drives just the template, as Patterns 2–4 use), whereas the no-context
  triple-script form and `advance = false` run nothing up front, so you call `next()` once per script
  in order (constraint → satisfier → template, with `ALT_STACK_LOADED` firing between satisfier and
  template). Grounded in `Test.kt`'s `buildTx` (the `("constraint"/"satisfier"/"template",
  "No error(0)", ALT_STACK_LOADED)` asserts) and `testFork1` (`evaling == "all scripts completed"`).
- `nexa-script-machine-testing/SKILL.md` **Pattern 9 (new) — Snapshot, fork, and serialize machine
  state (`clone` / `copy` / `dump`).** Resolves Pass 13's note (a). `clone()` forks the live native
  VM at the current instruction (own handle, must `delete()`; `testClone` shows each fork `cont()`-ing
  to the same `pos`); `copy()` rebuilds an equivalent machine in pure Kotlin by re-loading the stacks
  + re-seating the current script, preserving bignums (`testCopyStackWithBigNum` /
  `…NegativeBigNum`); `dump()` returns a `@Serializable ScriptMachineDump` (stacks-as-strings, bmd,
  pos, a handle-zeroed `ScriptMachine`) that `ScriptMachine().fromDump(...)` reconstructs into a
  working machine even after the original is `delete()`d (`testDump` / `testDumpOfDeletedSM`). Noted
  *why* the dumped machine's handle is zeroed (`getPos`/`getBMD` need a live handle).
- `nexa-script-machine-testing/SKILL.md` trigger keywords: `SpecialOperation` / `ALT_STACK_LOADED`,
  `clone`/`copy`, `dump`/`fromDump`/`ScriptMachineDump`.

### Corrected
- None. Every Pass-13 factual claim I cross-checked against the source + test suite was accurate;
  this pass is purely additive. No prior `<!-- PRIOR -->`/revision blocks were touched (the two
  Pass-10 blocks in `nexa-wallet-connection`/`nexa-transaction-construction` remain for a future
  consolidation pass, as Passes 11–13 also left them).

### Flagged for review
- None placed in skill bodies. One scoping note recorded here instead: the test suite is the
  maintainer's *committed* assertion suite, so the behaviors documented this pass are
  assertion-grounded, but I did **not** execute the suite myself this pass either (it still needs the
  GitLab Maven artifacts and a loadable `libnexa.so`). The difference from Pass 13 is the oracle:
  Pass 13 read the implementation; this pass reads the runtime contract the maintainer asserts on
  every CI run — a strictly stronger source for *behavior* (vs. *signatures*). An agent who can build
  the repo could promote both to fully run-verified and, with the suite green, finally fill the
  skill's three "Supporting files" stubs (`contractSpendTestHarness.kt`, `stackItemFormat.md`,
  `opcodeStepThrough.md`) straight from `Test.kt`.

### New skills created
- None. Both additions are core execution-model content for the existing `nexa-script-machine-testing`
  skill, appended as Patterns 8–9 (no renumbering of the existing 1–7, so no cross-reference churn).

### Notes for the next agent

- **The library's own `Test.kt` is the best oracle for behavior this corpus has had.** Most prior
  passes grounded in a *consuming app* (strongest for "what a server imports") or in *library
  source* (strongest for signatures). For the scriptmachine skill, the maintainer's test suite is
  better than either: each `check(...)` is a runtime invariant the maintainer commits to. When you
  refine a skill whose library lives in this repo, diff the skill against the test file, not just the
  source — the tests pin behavior the source only implies. Concretely, `buildTx` pinned the exact
  `next()` Triple sequence and `testFork1` pinned the `"all scripts completed"` terminal, both of
  which Pass 13 (source-only) declined to assert.

- **Verified-but-unwritten scriptmachine surface still open** (carried from Pass 13's notes, refined
  with what the tests now show): (a) the **register file** (`setRegister`/`getRegister`/
  `setRegisterToBigNum`) is exercised by `testRegisters` — registers are an indexed key/value store
  separate from the stacks (`setRegister(0, 1234)`, BIGNUM and BYTES variants, registers survive
  other-register writes); worth a short pattern if someone documents the register model. (b) **BMD /
  bignum sign-magnitude**: `testDebugger`/`testBin2BigNumNegative`/`testRegisters` show the
  trailing-`80`-byte = negative, trailing-`00` = positive convention and the `SETBMD`-then-`BIN2BIGNUM`
  flow for >8-byte bignums (`"BIGNUM 12 c0b0a…h 3727…"`); the `stackItemFormat.md` stub is the natural
  home. (c) `replaceStacks(stack, alt)` (overwrite stacks, keep machine position) and `loadStacks` /
  `getBinaryStack` (the typed stack round-trip used by `copy`) — lower-level than most testers need,
  but they're the primitives under Pattern 9. None block the skill; they're depth, not corrections.

- **What I did NOT touch and why.** No version/framing changes; Pass 3/9's three editorial standards
  held (no version pins, no named apps, no on-disk paths — only library symbol names and the
  library's own test-method names as evidence pointers, consistent with how Passes 12–13 cited
  `Test.kt`). I reopened only `nexa-script-machine-testing`; the rest of the corpus doesn't intersect the
  `next()`/snapshot surface. The "Supporting files (to be created)" stubs across every skill remain
  the standing mechanical backlog — now fully writable for this skill from `Test.kt`.

## Pass 15 — 2026-06-02 — Claude (Opus 4.8, 1M context)

**Consolidation pass (third one; Passes 3 and 9 were the prior two).** No new technical content.
This pass cleans up the audit trail that accumulated since Pass 9 and re-affirms the maintainer's
three editorial standards (deprioritize version specifics; de-anchor from named applications; remove
on-disk vs off-disk distinctions) as the explicit law for skill bodies going forward. I read the
entire corpus first and scanned for every audit artifact and framing violation before editing. The
finding: Passes 3, 9, and 10 had already brought the corpus most of the way into compliance, so the
substantive work was folding the two Pass-10 audit blocks that Passes 11–14 deliberately deferred
("a future consolidation pass's job"). The three reframings were otherwise already satisfied —
no named-application references, no on-disk/off-disk language, and version specifics already reduced
to placeholders plus the handful of genuine API-surface/behavior markers the standard permits.

### Corrected (audit-trail folded, corrected content kept)
- `nexa-wallet-connection`: removed the two Pass-10 `<!-- PRIOR -->` comments and the
  `> **Revision note:**` block in the `flags` discussion. The settled, developer-facing substance —
  the `TDPP_FLAG_*` constants are **not** a guaranteed importable symbol from any Nexa library, so
  define your own `const val`s (or write the literal `flags=N`); both ends agree on the integer bit
  values — now reads as one plain sentence in the "TDPP transaction `flags` bitfield" intro (the
  per-bit table's "Constant (define your own)" header already reinforced it). The cheat-sheet PRIOR
  comment was deleted outright; its corrected prose ("define them as constants in your own code")
  was already inline.
- `nexa-transaction-construction`: removed the two Pass-10 `<!-- PRIOR -->` comments and two
  `> **Revision note:**` blocks. (1) Pattern 2's `flags` bullet: folded the "not a guaranteed library
  import — define your own / write `flags=N`, full table in `nexa-wallet-connection`" substance into the
  bullet prose. (2) The "Looking up an output by index alone" anti-pattern: folded the
  `TDPP_FLAG_NOSHUFFLE` clarification (it preserves order for offer/swap protocols, but the wallet
  still adds funding/change outputs, so your output's absolute index is not guaranteed unless the
  protocol fully fixes the layout — identify by content regardless) into the *Wrong* gloss.

### Added
- None. Consolidation pass: no new patterns, anti-patterns, insights, or skills.

### Flagged for review
- None. The TDPP-flags-constants question and the no-shuffle-bit question were both settled by Pass 10
  (and held through Passes 11–14); their substance is now plain prose and their audit wrappers are
  gone. Pure-bookkeeping fragments ("the Pass 5 finding still holds and is subsumed"; "earlier text
  claimed…"; "prior text said…") were deleted — the history lives here in the CHANGELOG, not in the
  skill bodies.

### Deleted
- All four `<!-- PRIOR: ... -->` comments and all three `> **Revision note:**` blocks remaining in any
  skill body (two each in `nexa-wallet-connection` and `nexa-transaction-construction`, plus the two
  stray PRIOR comments). No `⚠️ Review needed` flags remained (Pass 9 removed the last one). After
  this pass a repo-wide grep for `PRIOR` / `Revision note` / `Review needed` / `⚠` across the skill
  bodies returns nothing.

### INDEX
- No change. Pass 3 already replaced the old "Ground-truth sources used when refining" (which listed
  local file paths) with "Where to find canonical sources" — library names, `org.nexa:*` Maven
  coordinates, GitLab Maven project numbers, and a "look up the current version in the registry rather
  than trusting a number copied here" note, framed for any developer. Verified it still carries no
  on-disk paths, no named applications, and no concrete version pins. It already matches what this
  pass's Step 3 asked for, so churning it would only add noise.

### New skills created
- None. Per the consolidation brief.

### Notes for the next agent

**The three editorial standards are the law for skill bodies and INDEX. Do not re-introduce what
this and the prior consolidation passes removed.** Restated so they cannot be lost:

1. **Deprioritize version specifics.** Skill bodies and `nexa-project-setup`'s `[versions]` block use
   placeholders (`"<latest>"`, `"<matches what libnexaapp's stdlib resolves to>"`) plus a pointer to
   the GitLab Maven registry, **not** pinned Nexa-library version numbers. Library coordinates
   (`group:artifact`) stay; version *numbers* in TOML/Gradle snippets are placeholders. A concrete
   version appears **only** where it marks a genuine API-surface change (the
   `millinow → epochMilliSeconds` rename, framed as "renamed in a release") or a genuine
   version-specific behavior (the kotlinx-serialization 1.10.0 CBOR caveat). The maintainer's
   libraries iterate faster than this documentation, so a pinned number is wrong by default — trust
   the published POM. (Scoping note for consistency: the third-party JUnit `testRuntimeOnly` pins in
   `nexa-project-setup`'s build snippet were left concrete on purpose — they are stable, non-Nexa test
   tooling, and `<latest>` would make the snippet non-resolvable; the reframing targets the
   fast-iterating Nexa stack, not third-party tooling. If a future maintainer wants those genericized
   too, that's a deliberate choice, not an oversight.)

2. **De-anchor from named applications.** Skill bodies and INDEX describe Nexa *infrastructure*
   (libnexakotlin, libnexaapp, NPL, scriptmachine, nexarpc, mpthreads, the `org.wallywallet:wew`
   library, electrum clients, and the Wally wallet with its TDPP/nexid/Trickle Pay protocol) and
   *patterns* extracted from real apps — never the apps themselves by name. Keep the distinction the
   corpus already navigates: **the Wally wallet and Trickle Pay are protocol infrastructure** (the
   counterparty these skills integrate with) and may be named; a specific *application built on the
   stack* may not. Patterns learned from such apps stay, rewritten with neutral domains (an order
   book, a marketplace listing, a vesting schedule, an Alice/Bob delegation contract). Provenance /
   "verified against app X" / "real-world exemplar" blocks do not belong in skill bodies — extract the
   substantive claim (e.g. "production settlement timeouts are measured in hours-to-days, not the
   sub-hour values that only work on auto-mining regtest") and state it generically. Named apps may
   appear **only here in the CHANGELOG**, for historical reasoning.

3. **Remove on-disk vs off-disk distinctions.** Skill bodies assume no checkout layout on the
   reader's machine. Name the library and its Maven coordinate / GitLab project so a developer can
   find the source themselves; INDEX is the authoritative "where to look" map. Light pointers into
   *library* source files ("libnexakotlin's `cnxnmgr.kt`", "NPL's `opParseHelpers.kt`", the
   scriptmachine library's own `Test.kt` as a worked-example oracle) are acceptable as "where to find"
   guidance; "the source is at ~/Desktop/…" or "X is published-only with no source on disk" is not.
   (Genuinely universal developer paths like `~/.gradle/caches` and `~/.m2` in the POM-cross-check
   recipe are fine — they are the same on every machine — and `SerializationType.DISK` /
   "on-disk serialization format" is a wire-format concept, not a machine-path reference. Leave both.)

**Why this matters / how it creeps back.** A content pass makes a correct, well-evidenced fix and
wraps it in a `<!-- PRIOR -->`/revision-note for traceability (reasonable mid-pass), or cites the app
it learned a pattern from, or pins the version it verified against. That bookkeeping is internal:
fold the substance into the prose and record the history in this CHANGELOG before the corpus ships.
This is now the **third** consolidation pass to remove the same three classes of artifact; the
recurring lesson is that traceability annotations and provenance are valuable *during* a pass and
noise *after* it. If you are doing a content pass, you may add a `<!-- PRIOR -->`/revision note as you
work — just fold it before you finish, or leave a clear note here so the next consolidation pass can.

**Substance preserved.** No technical content, pattern, anti-pattern, mental model, or code example
was added or removed this pass — only audit framing. The biggest "where did this go?" lookups: the
`TDPP_FLAG_*` "define your own constants" fact is now one sentence in `nexa-wallet-connection`'s
flags-bitfield intro and one in `nexa-transaction-construction` Pattern 2; the `NOSHUFFLE`-doesn't-fix-
output-position fact is in `nexa-transaction-construction`'s "Looking up an output by index alone"
anti-pattern gloss.

## Pass 16 — 2026-06-02 — Claude (Opus 4.8, 1M context)

**Content + correction pass, grounded in the libnexakotlin library's own source.** This refinement
runs *inside the libnexakotlin repository* — the primary Nexa library every ecosystem app depends
on — so this pass treated the library's actual `commonMain` source as the oracle for the
libnexakotlin-API claims the corpus makes (every signature below was read from the declaration,
not inferred). The corpus came out remarkably accurate: `GroupInfo`, `GroupAuthorityFlags` (all six
constants + exact hex), the `.nexa`/`.sat`/`.mexa` unit math, `script.groupInfo`'s null-for-ungrouped
contract, the `TxCompletionFlags` member *names*, `SatoshiScript.p2t`/`grouped`, `send`/`txCompleter`
existence, and the `var sequence` locktime default all verified verbatim. So this pass made **one
wrapped correction** (a builder signature the corpus guessed at) and a set of **additive sharpenings
that resolve open flags carried across Passes 6, 8, 10, 11, and 13**. Editorial standards held: no
version pins, no named applications, no on-disk paths — only libnexakotlin symbol names and
library-source-file pointers (`ichain.kt`, `iWallet.kt`, `primitives.kt`), which standard 3 permits.

### Corrected
- `nexa-transaction-construction` Pattern 6: the partial-offer push-URI line
  `tx.createTdppUrl(/* host/cookie/flags per the builder's params */)` implied `host`/`cookie`
  parameters that **do not exist**. The real signature (libnexakotlin `ichain.kt`) is
  `createTdppUrl(requestingDomain = "", tdppFlags: Long = 0L, applinkDomain: String? = "w.nexa.org")`:
  it **auto-derives `inamt`** by summing the existing inputs, emits `chain`/`inamt`/`flags`/`tx`, and
  builds the `https://w.nexa.org/tdpp/<requestingDomain>/tx?…` applink by default (raw `tdpp://` when
  `applinkDomain = null`) — and emits **no `cookie`**, so you must append your correlation cookie to
  the returned string yourself. Prior text preserved in a `<!-- PRIOR -->` comment + a
  `> **Revision note:**`. Confidence: high — read directly from the function body, which constructs
  the exact query string. Also refined the matching `createTdppUrl` sentence in `nexa-wallet-connection`
  to the verified signature + the no-cookie caveat (additive, no PRIOR needed there — the prior text
  said only "assembles it from a (partial) transaction," which is true).

### Added
- `nexa-transaction-construction` Pattern 6: the **exact `txCompleter` signature** — resolving the
  trailing-arg ambiguity flagged open since Pass 8 and again in Pass 11. The named tail args are
  `inputAmount`, `adjustableOutput` (the fee/surplus output index `DEDUCT_FEE_FROM_OUTPUT` acts on —
  this is the "trailing argument" the prior prose described vaguely), `destinationAddress`,
  `changeAddress`, `sigHashTypeOverride`, `contractId`. Documented the two operationally-important
  facts from the source doc-comment: `inputAmount` can be **negative** to seed extra fee on a
  `PARTIAL` fund pass (the standard way to pre-fund a later completion phase), and the completer
  **does not reorder** inputs/outputs (shuffling is the wallet's separate `NOSHUFFLE`-gated step).
  Mentioned the lower-level `signInput(tx, idx, sigHashType)` primitive (Pass 11's open note (a)).
- `nexa-transaction-construction` Pattern 6 + `nexa-tokens-and-groups` Pattern 9: a **`MUST_MINT`**
  `TxCompletionFlags` row/bullet (verified member `0x200`, previously undocumented) — forces a mint
  to issue from a mint authority rather than silently moving existing token UTXOs.
- `nexa-transaction-construction` Pattern 4: `TransactionHistory.confirmedHeight` is a **three-state**
  signal, not a boolean — `>= 0` confirmed, `-1` unconfirmed, and **`Long.MIN_VALUE` = being removed
  from the unconfirmed list (probably invalid / double-spent / conflicted)**. This directly answers
  Pass 6's explicit open question ("does it expose an unconfirmed-again transition, or just
  `confirmedHeight` flipping back to -1?"): there is a distinct invalidation sentinel. Threaded it
  into the field list, the prose, the 0-conf anti-pattern's *Right* gloss, and the matching
  `nexa-debugging-onchain-errors` symptom row. Also added the `confirmedHash`, `incomingAmt`/`outgoingAmt`,
  and `date`(epoch **ms**) fields to the list.
- `nexa-tokens-and-groups` Pattern 1: the precise meaning of **`isFenced()`** (resolving Pass 10's open
  "what does isFenced gate" note) — it is bit 1 of the **last byte of the 32-byte base group id** and
  flags a group **holding native crypto rather than tokens** (`isHoldingNative()` is the deprecated
  alias); plus the previously-undocumented sibling **`isCovenanted()`** (bit 0) — a **native covenant
  primitive** baked into the group id itself, requiring every grouped output's script to equal the
  input script at consensus, independent of any NPL contract template.
- `nexa-tokens-and-groups` Pattern 3: the `GroupInfo.getTokenAmountOrAuthority()` helper (returns the
  unified quantity slot — token amount, or authority flags as a **negative** value for an authority
  output) and the `isSubgroup()` delegation.
- `nexa-npl-smart-contracts` Pattern 4: an **alternative** P2T builder — libnexakotlin's companion
  `SatoshiScript.p2t(chain, templateScriptHash, constraintArgsHash, constraintPublicArgs, grpId,
  tokenAmt)` (the same builder the `nexa-script-machine-testing` Pattern 2 prevout uses, so a funded
  contract output and a VM-replayed UTXO share one code path — the cross-link Pass 13 wanted), and
  the key fact that its `grpId`/`tokenAmt` params build a **token-bearing contract output** in one
  call (the library counterpart to `Contract.groupedConstraint`).
- Trigger keywords: `txCompleter`/`TxCompletionFlags`/`adjustableOutput`/`MUST_MINT`/`createTdppUrl`/
  `confirmedHeight`/`TransactionHistory` (tx skill) and `MUST_MINT`/`isFenced`/`isCovenanted`/
  `getTokenAmountOrAuthority` (tokens skill).

### Flagged for review
- None left in place. Where I could not be exhaustive I stayed conservative: I documented
  `txCompleter`'s signature and the developer-relevant semantics of `inputAmount`/`adjustableOutput`
  from the source doc-comment, but did not enumerate the exact behavior of `sigHashTypeOverride` /
  `contractId` beyond the one-line meanings in the declaration (I did not trace their full code
  paths). `MUST_MINT` is described by its source doc-comment intent ("the tx MUST use mint
  authorities"), not by an executed mint that exercises it.

### New skills created
- `nexa-wallet-lifecycle-and-chain/SKILL.md` — **the libnexakotlin wallet bootstrap, the corpus's
  single biggest coverage gap.** Created after a follow-up coverage audit (prompted by the
  maintainer) of libnexakotlin's app-facing surface against what the 12 prior skills covered: the
  corpus thoroughly documented everything an app does *with* a wallet (tx construction, contracts,
  tokens, the wallet protocol, RPC, flows) but had **no skill for creating, restoring, opening,
  encrypting, or chain-connecting a wallet** — the `init.kt` lifecycle every app calls at startup.
  Grounded in directly-read source (`init.kt` `newWallet`/`recoverWallet`/`openWallet`/
  `openDisconnectedWallet`/`blockchainFor`/`connectBlockchain`/`GetBlockchain`; `wallet.kt`
  `Bip44Wallet` constructors + `recoverySecret`/`encrypt`/`unlock`/`lock`/`lockedState`/
  `saveBip44Wallet`; `iWallet.kt` `getNewAddress`/`destinationFor`/`balance*`/`synced*`/`blockchain`;
  `blockchain.kt` `Blockchain`/`getTip`/`curHeight`; `cnxnmgr.kt` `exclusiveNodes`/`start`/`stop`;
  `platform.kt` `initializeLibNexa`). The decisive content fact it pins down — **`recoverWallet`
  scans chain history (`addBlockchain(bc, checkpointHeight, 0)`) while `newWallet` does not
  (`-1, -1`)** — is the root of the classic "restored wallet shows zero balance" bug, which no
  existing skill explained. Resolves the network/connection-layer gap Pass 6 flagged from the
  app side (Pass 6 deferred it for lack of source; this pass has the source). Added to `INDEX.md`
  (skill table + relationship map, including the explicit owned-wallet-vs-external-Wally split) and
  cross-linked from `nexa-ktor-server-integration`, `nexa-transaction-construction`,
  `nexa-identity-and-addresses`, `nexa-rpc-node-client`, and `nexa-locktime-cltv`. Follows the fixed section
  structure; no version pins, named apps, or on-disk paths (only library symbol names).
  - Side correction it carries: the lifecycle helpers `newWallet`/`openWallet` that
    `nexa-ktor-server-integration` uses are **libnexakotlin** (`init.kt`), not libnexaapp — a Pass-1
    note had flagged uncertainty about their origin. The Ktor skill's prose was not wrong (it never
    claimed they were libnexaapp), so this is recorded as a clarification, not a wrapped correction;
    the new skill states their library of origin outright.

### Notes for the next agent

- **Coverage audit (do this kind of pass periodically).** The maintainer asked whether the corpus
  covered "the most essential functionality likely to be directly utilized by a Nexa app." It did
  not — the wallet lifecycle was missing entirely. Worth re-checking the *rest* of the app-facing
  surface the same way: I confirmed coverage of tx build/sign/broadcast, contracts, tokens, the
  wallet protocol, RPC, flows, script-VM testing, project setup, identity, locktime, and debugging,
  and now wallet lifecycle/chain. Plausible remaining direct-use surface I did **not** turn into a
  skill (judged secondary, but flag-worthy): **message signing/verification** with a wallet
  (`identity.kt` / `verifyMessage` — the wallet skill mentions signing-via-disconnected-wallet but
  there is no focused "sign and verify a message" skill, and the wallet-connection skill already
  documents nexid signature *verification*); **fiat/price** helpers (`currency.kt`); **CAPD**
  multisig/contract-coordination (`contracts/multisigDestination.kt`, `capd.kt`) — a genuinely
  advanced, orthogonal area that could warrant its own skill if apps use it. None block the common
  case; the wallet lifecycle did.

- **The repo this refinement runs in IS libnexakotlin — its `src/commonMain/kotlin` is the
  authoritative oracle for any libnexakotlin-API claim.** Prior passes grounded libnexakotlin facts
  in *consuming apps* (good for "what a server imports") or inference; this pass could read the
  declarations and doc-comments directly. The corpus held up extremely well — the only hard
  correction was a *builder signature the corpus had to guess* (`createTdppUrl`), exactly the kind of
  claim a consuming app wouldn't pin because apps tend to hand-build the URI string. When a libnexakotlin
  signature is in doubt, grep `libnexakotlin/src/commonMain/kotlin` here rather than inferring.

- **Verified-but-unwritten libnexakotlin surface I left for a future pass:** (a) the `NexaScript`
  simpleapi class (`simpleapi.kt`) with its `ungroupedP2t(...)` / `constraint(gi, argsScript, visArgs)`
  helpers — a higher-level template/contract-output builder layer parallel to the `P2T` /
  `SatoshiScript.p2t` path, not yet documented; (b) `mergeUnlockingScripts(other)` on `iTransaction`
  (`ichain.kt`) — merges another party's input satisfiers into a partial tx (skips OP_RETURN
  comms-slot inputs), which is plausibly the mechanism a server uses to combine a returned half-tx
  with its own signed inputs in the swap-offer idiom (`nexa-transaction-construction` Pattern 6's
  "re-validate the returned tx" step) — I saw the function and its doc-comment but did not trace how
  the wallet/offer code calls it, so I left it for a pass that can; (c) the full `send(...)` overload
  family (`iWallet.kt`: native-by-script / -by-address / -by-string, multi-output `List<Pair>`,
  token `send(amountTokens, destAddress, groupId)`, `vararg outputs`, and `send(tx)`) — the corpus
  documents `send(output)` and `send(tx)`, both verified, but the token and multi-output forms are
  undocumented; (d) the connection-manager / SPV network layer (`cnxnmgr.kt` — `net.broadcastTransaction`,
  `net.exclusiveNodes`, `blockchain.getTip()`) is referenced across skills but has no skill of its own
  — Pass 6 flagged this gap; it remains the most plausible *new-skill* candidate, now writable from
  this repo's source, though I did not open it this pass to stay focused on sharpening verified claims.

- **What I did NOT touch and why.** No version/framing changes; Pass 3/9/15's three editorial
  standards held throughout. I left the one `<!-- PRIOR -->`/revision-note block I added (the
  `createTdppUrl` correction) for a future consolidation pass to fold, per the established convention
  (it documents a real signature change a downstream agent should be able to see and reverse). I did
  not reopen `nexa-project-setup`, `nexa-ktor-server-integration`, `nexa-server-state-and-flows`,
  `nexa-identity-and-addresses`, or `nexa-locktime-cltv` beyond the cross-cutting `confirmedHeight`/debug
  touch — their libnexakotlin-API claims I spot-checked (the `var sequence` default, `PayAddress`,
  `parseTemplate`, `flowConnector` singletons) were already accurate. The "Supporting files (to be
  created)" stubs across every skill remain the standing mechanical backlog.

## Pass 17 — 2026-06-02 — Claude (Opus 4.8, 1M context)

**Two new skills for unique/high-value libnexakotlin features, at the maintainer's direction:
CAPD and the electrum client.** Following the Pass-16 coverage audit (which closed the wallet
lifecycle gap), the maintainer named two more directly-app-relevant subsystems the corpus still
didn't document: **CAPD** (a messaging layer unique to Nexa) and the **electrum client** (the
library's general-purpose on-chain query/monitor tool). Both are libnexakotlin core, both are
orthogonal to all existing skills, and both were grounded entirely in directly-read source
(`capd.kt`, `protocolCommunication.kt`, `p2p.kt`, `cnxnmgr.kt` for CAPD; `electrumclient.kt`,
`token.kt` for electrum — exact signatures, result data-class fields, port constants, and exception
types read from the declarations). Editorial standards held: no library version pins, no named
applications, no on-disk paths. (The electrum *protocol* port numbers and the CAPD spec URL are
stable network/protocol facts, not library version pins — stated as the library's own named
constants and a canonical public reference respectively.)

### New skills created
- `nexa-capd-messaging/SKILL.md` — Nexa's CAPD (Counterparty And Protocol Discovery) off-chain P2P
  message bus. Covers the mental model (ephemeral, PoW-rate-limited, broadcast-by-content; *not* the
  blockchain), the two layers (`CapdMsg`/`CapdQuery` raw messages vs the encrypted
  `CapdProtocolCommunication` conversation channel implementing `ProtocolCommunication`), the
  `solve()`/`setPowTargetHarderThanPriority()` PoW flow and `CapdTooDifficult`/`CapdSolvableCutoff`
  bound, `expiration`/`recindHash` lifetime control, `chain.net.broadcastMsg`, the **receiving
  requires a P2P peer (not SPV-only)** constraint (`chain.net.getp2p()` + `installMsgMonitor`), and
  the convoSecret→convoId/AES-key derivation. Anti-patterns (broadcasting unsolved; treating it as
  durable/guaranteed; receiving on an electrum-only connection; sending plaintext; hanging on an
  unsolvable PoW) and security (no built-in confidentiality/auth — encrypt; PoW≠identity; metadata
  leakage; replay-within-validity). Cites the authoritative spec URL `https://spec.nexa.org/network/capd/`.
  Rationale: a feature unique to Nexa that the corpus entirely lacked, and the coordination substrate
  behind multi-party contract/swap flows.
- `nexa-electrum-monitoring/SKILL.md` — libnexakotlin's `ElectrumClient`. The distinguishing value
  stated up front: it queries/monitors **arbitrary** on-chain state (any address/script/outpoint/tx/
  token) against public electrum servers — unlike the SPV wallet (own keys only,
  `nexa-wallet-lifecycle-and-chain`) and unlike nexarpc (a node you operate). Covers construction + the
  TCP/SSL port constants per chain, tx lookups (`getTx`/`getTxDetails`/async-callback form),
  **outpoint/UTXO checks** (`getUtxo` → `amount`/`height`/`spent`/`group`/`group_quantity`;
  `ElectrumNotFound` = spent-or-never-existed — the contract-spend-detection primitive), address/script
  watching (`getHistory`/`listUnspent`/`getBalance`/`getFirstUse`), token reads
  (`getTokenGenesisInfo`→`decimal_places`/ticker, `getTokenBalance`/`getTokenUnspent`/`getTokenHistory`),
  the **headers-only push + re-poll** monitoring pattern (`subscribeHeaders`; there is no per-address
  subscription), `sendTx` broadcast, and the `call`/`subscribe` escape hatch. Anti-patterns (using it
  for your own wallet's funds; expecting an address push; trusting one untrusted server; SSL/port
  mismatch; blocking the event loop; reading `not found` as an error rather than a state) and security
  (untrusted-server light-client trust model; query-reveals-interest privacy; SSL; validate fetched
  txs; broadcast exposure). Rationale: the maintainer flagged it as highly useful for on-chain tx and
  UTXO/outpoint monitoring; nothing in the corpus documented direct light-client chain queries.

### Added
- `INDEX.md`: both skills in the table and two new relationship-map entries (arbitrary-state
  monitoring vs own-keys wallet tracking; off-chain CAPD coordination → build tx → watch with electrum).
- Cross-references into both new skills from `nexa-tokens-and-groups` (electrum token reads —
  `getTokenGenesisInfo` is the light-client way to get the `decimal_places` that skill calls
  off-chain), `nexa-transaction-construction` (electrum outpoint/spend monitoring + `sendTx`; CAPD
  off-chain coordination of the half-tx swap idiom), `nexa-npl-smart-contracts` (CAPD multi-party
  contract coordination; electrum contract-UTXO watching), `nexa-rpc-node-client` (electrum as the
  no-node read alternative), and `nexa-wallet-lifecycle-and-chain` (electrum for non-owned state; the
  chain connection CAPD rides on).

### Corrected
- None. Both skills are new; all edits to existing skills are additive cross-references.

### Flagged for review
- None placed in skill bodies. Honest scoping notes: (a) the CAPD skill documents the app-facing
  `CapdProtocolCommunication` and the `CapdMsg` solve/broadcast surface fully, but the raw P2P
  `installMsgMonitor`/`removeMsgMonitor`/`reloadCapdInfo` receive path (on `P2pClient`) is described
  by behavior, not given a standalone worked example — most apps should use the high-level channel,
  so I kept the low-level monitor as "what the channel does for you." (b) The electrum
  `BalanceResult` fields are typed `Int` in the source; I noted "prefer `listUnspent` + sum
  `Spendable.amount` (Long) for precise value math" rather than relying on the summary, which is the
  safe guidance regardless of that type choice. (c) I used a placeholder `electrumServerHost`
  variable rather than asserting a specific public hostname I couldn't verify.

### Notes for the next agent

- **Both skills verified against source in this repo; the cross-cutting facts they pin are worth
  knowing.** CAPD *receiving* needs a P2P peer (electrum/SPV-only can broadcast but not monitor) —
  `CapdProtocolCommunication.start()` calls `chain.net.getp2p()`. The electrum client has **no
  per-address push** (only `subscribeHeaders`), so all address/outpoint monitoring is re-poll-on-block;
  don't document a non-existent address subscription. `getUtxo` throwing `ElectrumNotFound` is the
  outpoint-spent signal, not a failure.
- **`TokenGenesisInfo` (token.kt) is now confirmed** to carry `document_hash`/`document_url`/`name`/
  `ticker`/`token_id_hex`/`txid`/`txidem`/`decimal_places`/`op_return` — this corroborates the
  token-description-document discussion in `nexa-tokens-and-groups` Pattern 7 from the *read* side (the
  electrum server resolves and serves it via `getTokenGenesisInfo`). A future pass could tighten the
  tokens skill's "off-chain metadata" framing to note it's directly queryable through electrum.
- **Remaining direct-use surface still uncovered** (re-stating the Pass-16 audit list, minus what
  this pass closed): wallet **message signing/verification** as a focused topic (`identity.kt` /
  `verifyMessage` — currently only touched inside the wallet-connection nexid flow); **fiat/price**
  helpers (`currency.kt`); and the **CAPD-coordinated multisig contract** flow end-to-end
  (`contracts/multisigDestination.kt` ties CAPD + NPL + tx together — a worked "N-of-M multisig over
  CAPD" example would be a strong future addition now that both halves are documented). None block the
  common case.
- **The corpus is now 15 skills.** The app-facing libnexakotlin surface is broadly covered:
  project/build, wallet lifecycle + chain connect, tx construction/completion, NPL contracts, tokens/
  groups/minting, identity/addresses, locktime, the wallet (TDPP) protocol, server flows, Ktor
  integration, node RPC, script-VM testing, debugging, **and now CAPD messaging + electrum
  monitoring**. Editorial standards from Passes 3/9/15 held throughout; no version pins, named apps,
  or on-disk paths entered any skill body.

## Pass 18 — 2026-06-02 — Claude (Opus 4.8, 1M context)

**Documented the raw-`Bip44Wallet`-primitive vs `WallyEnterpriseWallet`-runtime distinction and
decision guidance, at the maintainer's direction, grounded in the WEW source.** The corpus
mentioned `WallyEnterpriseWallet` (`org.wallywallet:wew`) only as a server multi-wallet wiring
snippet in `nexa-ktor-server-integration`, with no statement of *what it is relative to a raw
libnexakotlin wallet* or *when to choose each*. The maintainer pointed at the WEW source (the
`enterprise` repo), which let me ground the relationship precisely rather than infer it.

### Verified from WEW source (recorded here; not cited by path in any skill body)
- `WallyEnterpriseWallet` is a **JVM `object` (singleton management runtime)**, not a wallet type:
  `accounts: MutableMap<String, Wallet>` (each entry an ordinary libnexakotlin `Bip44Wallet` — e.g.
  `accounts[name] = openWallet(name)`), a parallel `nodes: MutableMap<String, NexaRpc>` registry, a
  JSR-223 script engine, and `fun run(shell: CliType, walName?, cs?)` with
  `enum CliType { None, Console, Graphical, Fifo }` (Fifo = a named-pipe `in`/`out` command channel —
  a controllable daemon). It owns blockchain connections and stops/saves every account on shutdown.
  Its JVM dependencies (`java.io.File`, `Executors`, `ScriptEngine`) confirm it is server/desktop,
  **not** the multiplatform layer.
- **The decisive architectural fact**: WEW does not replace the wallet primitive — its accounts *are*
  `Bip44Wallet`s, so the create/restore/open/encrypt/sign mechanics in `nexa-wallet-lifecycle-and-chain`
  govern each account regardless. Choosing WEW is choosing an *operational layer*, not a different
  cryptographic wallet.

### Added
- `nexa-wallet-lifecycle-and-chain/SKILL.md` **Pattern 8 — "Choosing your wallet layer: raw `Bip44Wallet`
  vs `WallyEnterpriseWallet`."** States the layered (not alternative) relationship, the `CliType`
  front-ends, and a scenario decision table (client/KMP/mobile/Wasm → primitive; single server
  wallet → primitive; several named signing roles / daemon / operator-scripting / wallet+node
  management → WEW). Plus a Mental-model pointer to it, a reinforcing anti-pattern ("Reaching for
  `WallyEnterpriseWallet` in a client app, or treating it as a different wallet type"), and trigger
  keywords (`WallyEnterpriseWallet`, `wew`, named signing wallets, wallet daemon, enterprise wallet).
- `nexa-ktor-server-integration/SKILL.md`: reframed the "Server-side signing wallets" section lead-in to
  state that the raw primitive suffices for a single wallet (and is the only client-side option),
  that WEW is a JVM runtime whose accounts are `Bip44Wallet`s, what it adds (named-account + `nodes`
  registries, lifecycle, scriptable front-ends incl. the `Fifo` daemon mode), and a pointer to
  Pattern 8 for the decision. The existing concrete wiring snippet (`run(CliType.Fifo)`,
  `accounts[name] = openWallet(name)`, `destinationFor(COMMON_IDENTITY_SEED)`) is unchanged and now
  reads as the WEW-path example beneath that framing.

### Corrected
- None. All edits are additive (a new pattern, an anti-pattern, a reframed section lead-in, keywords,
  a mental-model pointer); no prior claim was overwritten. The existing WEW snippet was verified
  accurate against source (the `accounts[name]` registry, `run(CliType.Fifo)`, and
  `destinationFor(COMMON_IDENTITY_SEED)` all match).

### Flagged for review
- None. Scoping note: I documented WEW at the level its public runtime surface supports
  (`accounts`/`nodes` registries, `run`/`CliType`, the layering fact) and deliberately did **not**
  enumerate its scripting-console command vocabulary or plugin API — those are a larger, more
  app-shaped surface than the "which layer, and why" question needed, and could be their own skill if
  a future task operates WEW programmatically in depth.

### Notes for the next agent
- **Editorial standard check:** WEW (`org.wallywallet:wew`) is ecosystem infrastructure (a library/
  tool, the same standing the corpus already gave it), not an application-built-on-the-stack, so
  naming it is in bounds. The `enterprise` repo that holds its source was used for grounding only and
  is **not** cited by path in any skill body; WEW is referenced by its Maven coordinate + GitLab
  project number, consistent with how the corpus names every other library. No version pins, named
  apps, or on-disk paths entered the skill bodies.
- **If WEW operation becomes a task in its own right**, the natural next skill is "operating a
  WallyEnterpriseWallet service" (the script-engine command set, the FIFO control protocol, the
  plugin surface) — verifiable from the same source. This pass intentionally stopped at the
  primitive-vs-runtime decision the maintainer asked for.
- The corpus remains 15 skills; this pass only deepened two existing ones.

## Pass 19 — 2026-06-03 — Claude (Opus 4.8, 1M context)

**Content pass: documented the NPL compiler — including the `Cannot find state transition`
mechanism the maintainer called out — and wrote the first comprehensive `dslReference.md`,
all grounded in the NPL library's own source.** This refinement runs *inside the `org.nexa:npl`
repository*, so this pass treated its `src/main/kotlin` as the oracle for every NPL-API claim
(every signature/behavior below was read from the declaration, not inferred). The maintainer's
steer was twofold: document *all* contract functionality an ecosystem app would need, and
specifically explain how "missing state transition" errors in compilation are resolved manually
(registering transformers / hard-coding transitions). The corpus's NPL skill was accurate but
*partial*: it documented the DSL→bytecode workflow and the introspection slice, but said
nothing about how compilation actually works, never mentioned signature/oracle checks, and left
the long-flagged `dslReference.md` stub empty. All additions follow the editorial standards: no
version pins, no named apps, no on-disk paths — only library symbol names and library-source
file pointers (`nslxlat.kt`, `dynstackx.kt`, `nsl.kt`, `opParseHelpers.kt`, …), which standard 3
permits.

### Added
- `nexa-npl-smart-contracts/SKILL.md` — **a new "How NPL compiles a rule, and the `Cannot find
  state transition` error" section** (the headline deliverable). Explains the step→stack-state
  model, the three-tier resolution order (static `stackX` table → `DynamicStackTransformRegistry`
  generators → throw), the exact error printout (`Missing this state transition (in N options):`
  + the `(begin)⇒(end)` `StateDescriptor`, then `IllegalStateException("Cannot find state
  transition")`), why the bounded `calcStackX()` search leaves deep/unusual permutations
  missing, and the **two manual fixes**: (1) hard-code the transition via
  `st.add(SD(begin), SD(end), *opcodes)` in `addSpecificTransitions` /
  `addWarriorContractTransitions` (with `st.check`/`DoubleCheckTransitions` validating it in the
  real VM, and the insight that the `// get transition …⇒…` comments in those functions *are*
  pasted error printouts), and (2) implement `DynamicStackTransform.tryGenerate` and register it
  in `DynamicStackTransformRegistry`. Plus the two operational gotchas (the registering function
  must actually run in the compile scaffold; delete a stale `stackScripts.bin` to force a
  recompute). The hard-code example is a verbatim real entry (`initial11`/`target11`/`sequence11`)
  from `addSpecificTransitions`, not a fabrication.
- `nexa-npl-smart-contracts/SKILL.md` — a **signature/oracle-checks note** after Pattern 1:
  `checkSigVerify(sig: NSig, pubkey: NPubKey)` (authorize by key) and
  `checkDataSigVerify(sig: NSig, msg: NBytes, pubkey: NPubKey)` (the oracle primitive — verify a
  signature over an arbitrary message). The skill previously had *no* mention of signature
  checking despite many real contracts needing it; the secret-reveal teaching example authorizes
  by knowledge only, which under-represented the contract space.
- `nexa-npl-smart-contracts/SKILL.md` — Mental-model clarification of the **two rule-declaration
  forms** (`ruleWithPublicArgs`/`fullRule` *with* a `holderPublicArgs` slot vs. the simpler
  `rule(name, templateArgs, holderArgs, spenderArgs)` *without* one), including the positional
  argument-order trap. This resolves the Pass-4-flagged uncertainty about the bare `rule(...)`
  arg order.
- `nexa-npl-smart-contracts/SKILL.md` — Setup note on the **project entry points** (`Nexa` /
  `NexaContract` / `NplScript`) and a Pattern-2 note on **`initNpl()`** (= `loadCalcStackX()` +
  `initRefactor()`, the minimal tier — does *not* register the specific/warrior transitions, so
  it can still hit the missing-transition error on a cold cache).
- `nexa-npl-smart-contracts/SKILL.md` — a "Higher-level introspection helpers (`nsllib`)" note
  after Pattern 10 (`thisIndex`/`thisGroup`/`thisTemplateHash`/`thisArgsHash`,
  `mustSpendGroupToP2pkt`, `forGroupedOutputs` (unrolled, no VM loops), `groupsIn`/`groupsOut`).
- `nexa-npl-smart-contracts/dslReference.md` — **NEW supporting file** (the #1 backlog item every
  prior pass flagged). A 10-section catalogue of the DSL surface grounded in source: project/
  contract definition + compile path; the typed-arg classes (`NBytes`/`NInt`/`NUInt`/`NBool`/
  `NSig`/`NPubKey`/`NGroupId`/`NScript`/`NAddress` and the `NC*` constants) and the `.nx`
  extension; comparison/arithmetic/bitwise operators; hashing + signature ops; control flow
  (`verify`/`if_`/CLTV/CSV); split/data ops; the full input/output/prevout/group introspection
  family (incl. the five `getOutputGroupAuthority*` variants and `verifySameContract`/
  `verifySameGroup`); the high-level `nsllib` helpers; the OP_PARSE helper families with the
  canonical field-number table; and the compilation/state-transition internals.
- `nexa-debugging-onchain-errors/SKILL.md` — a symptom-table row for `Cannot find state
  transition`, a build-time-error mental-model note distinguishing it from version mismatches,
  and trigger keywords/tasks for it.
- `INDEX.md` — extended the `nexa-npl-smart-contracts` row to surface the compilation/state-
  transition content, signature/oracle checks, and `dslReference.md`.

### Corrected
- None. Everything this pass is additive. The existing NPL skill content I cross-checked against
  source held up — the init-scaffold tier table (verified against the four test files'
  scaffolds), the `loadCalcStackX`/`calcStackX` cache behavior, the OP_PARSE field numbering and
  two accessor families, `verifySameContract`/`verifySameGroup` semantics, the `P2T`/
  `SatoshiScript.p2t` builders, and `Contract(name, interfaces: List<Interface>)` (so the
  skill's `.interfaces[0]` access is correct) all match the source verbatim. No prior factual
  claim was overwritten and no prior audit blocks exist to fold (Pass 15 removed the last ones).

### Flagged for review
- None placed in skill bodies. Honesty/scoping notes recorded here instead:
  1. I documented the DSL surface from a verbatim source inventory (signatures read directly),
     but in `dslReference.md` I deliberately **omitted line numbers** (they drift and read as
     on-disk pointers) and summarized the large operator-overload families by their operand
     shapes rather than enumerating every overload — accurate without over-claiming an exact
     count. When an exact signature matters the declaration is authoritative, which the file
     states up front.
  2. A genuine library bug I noticed while grounding, kept OUT of the skill per editorial
     standard (belongs in an issue tracker, not a skill): in `nslc.kt` `findXformCode`, the
     dynamic-generation success path does `println("Successfully generated dynamic stack
     transform for: $xSpec")` on *every* dynamic resolution — harmless but noisy console output
     during compilation a developer may wonder about. Recorded here so it isn't lost.

### New skills created
- None. The compilation mechanism and the DSL reference are core content for the existing
  `nexa-npl-smart-contracts` skill, so they went in as a new section + a supporting file, consistent
  with the corpus's standing preference against fragmentation.

### Notes for the next agent
- **This repo IS `org.nexa:npl` — its `src/main/kotlin` and `src/test/kotlin` are the
  authoritative oracle for any NPL claim.** The compiler is `nslc.kt` (`findXformCode` is where
  the missing-transition error is thrown) + `nslxlat.kt` (the `stackX` table, `StateTransition`/
  `StateDescriptor`, `calcStackX`, `addSpecificTransitions`, `addWarriorContractTransitions`,
  `loadCalcStackX`) + `dynstackx.kt` (`DynamicStackTransform` + `DynamicStackTransformRegistry`)
  + `recursivestackx.kt` (the recursive decomposition generator). The DSL is `nsl.kt` (in-script
  methods), `nsltypes.kt`/`nsltypesint.kt` (typed args), `nslstruct.kt` (`PackedStructure`),
  `nsllib.kt` (high-level helpers), `opParseHelpers.kt` (the top-level OP_PARSE family),
  `npl.kt` (project/contract/group builders + genesis). The test files (`NexaWarriorsContractsV2`,
  `nexPredictContracts`, `nslTest`, `nslPackedDataTest`, `nslInterfaceTest`) are the worked-example
  oracle for scaffolds and calling conventions.
- **Verified-but-unwritten NPL surface I left for a future pass** (real in source, not yet in the
  skill/reference because I scoped this pass to the compiler + the core DSL reference): (a) the
  `group { descriptor / authority / mint / subgroup / media }` genesis DSL in npl.kt in depth and
  how `NPL.deploy(wallet)` produces the genesis tx + runtime group id (this is more
  `nexa-tokens-and-groups`' territory — the tokens skill Pattern 7 still carries a commented
  placeholder about the bare `rule(...)` order that this pass's Mental-model clarification now
  answers; a tokens-skill pass could fold that); (b) the `splitInto`/`splitPrefixInto`/
  `splitSuffixInto`/`splitLeSignMagInt` typed-split family and `NScript.templateAndArgsHash`/
  `splitPush` (named in `dslReference.md`, not yet shown in a worked pattern); (c) `nvmEval`/
  `nvmRun`/`eval` on `Rule`/`Interface`/`Contract` and the `EvalConfig` (incl. `checkSigResult`/
  `checkDataSigResult` toggles for emulating sig checks during off-chain evaluation) — a distinct
  "evaluate a contract rule without the script VM" testing path parallel to
  `nexa-script-machine-testing`; (d) the `recursivestackx.kt` algorithm has a documented `TODO`
  about failing "for some cases where stack sizes grow due to duplications during rearrangement"
  — worth a note if a developer's dynamic-generation attempts mysteriously fail on
  duplication-heavy transforms.
- **The `opcodesDecoded.md`, `examples/`, and `compileAndPrintTemplate.kt` supporting-file stubs
  remain** in `nexa-npl-smart-contracts` (and the empty stubs across the other skills are still the
  standing mechanical backlog). `dslReference.md` is now written; `examples/` (a worked atomic
  swap / oracle bet / HTLC with DSL + generated bytecode + satisfier) is the obvious next one,
  now that the signature/oracle ops and the compile path are both documented.
- **What I did NOT touch and why.** No version/framing changes; the Pass 3/9/15 editorial
  standards held throughout. I reopened only `nexa-npl-smart-contracts`, `nexa-debugging-onchain-errors`,
  and `INDEX.md` (the NPL-compiler surface intersects the rest of the corpus only through the
  cross-references already present). I did not re-verify the libnexakotlin/libnexaapp/wallet-side
  claims prior passes grounded — out of scope for an NPL-library pass and I have no superior
  oracle for them here.

## Pass 20 — 2026-06-03 — Claude (Opus 4.8, 1M context)

**Consolidation pass (fourth one; Passes 3, 9, and 15 were the prior three).** No new technical
content. This pass cleans up the audit trail accumulated since Pass 15 and re-affirms the
maintainer's three editorial standards (deprioritize version specifics; de-anchor from named
applications; remove on-disk vs off-disk distinctions) as the explicit law for skill bodies going
forward. I read the entire corpus first — every `SKILL.md`, `dslReference.md`, `INDEX.md`, and all
nineteen prior CHANGELOG passes (including each "Notes for the next agent") — then swept the skill
bodies for audit artifacts and framing violations before editing anything. The finding mirrors
Pass 15's: the content passes since the last consolidation (16–19) held to the standards almost
completely, so the substantive work was small. Passes 17, 18, and 19 introduced **no** audit
wrappers; Pass 16 introduced exactly **one** (`<!-- PRIOR -->` + `> **Revision note:**`) and
explicitly deferred folding it "for a future consolidation pass." This is that pass.

### Corrected (audit-trail folded, corrected content kept)
- `nexa-transaction-construction` Pattern 6: removed the lone remaining audit block in the entire
  corpus — the Pass-16 `<!-- PRIOR -->` comment and `> **Revision note:**` on the
  `iTransaction.createTdppUrl` partial-offer push-URI line. The correction held up across Passes
  17–19; the corrected signature is already shown in the code block
  (`createTdppUrl(requestingDomain, tdppFlags, applinkDomain)`, the `applinkDomain = null` →
  raw-`tdpp://` note, and the `&cookie=` append). The one developer-relevant fact that lived only
  in the revision note — that the builder takes no host/cookie param, **auto-derives `inamt`** by
  summing the inputs, emits `chain`/`inamt`/`flags`/`tx`, and emits no cookie so you append your
  own — is now a single plain paragraph beneath the code block. Its bookkeeping (the prior
  placeholder text, the `ichain.kt` source pointer) is preserved only here in the CHANGELOG.

### Added
- None. Consolidation pass: no new patterns, anti-patterns, mental models, insights, or skills.

### Flagged for review
- None. The `createTdppUrl`-signature question the revision note documented is settled (read from
  libnexakotlin source in Pass 16, accurate, and reflected in the code block); folding the wrapper
  loses no developer-facing substance.

### Deleted
- The single `<!-- PRIOR: ... -->` comment and the single `> **Revision note:**` block remaining in
  any skill body (both in `nexa-transaction-construction`). After this pass a repo-wide grep for
  `PRIOR` / `Revision note` / `Review needed` / `⚠` across the skill bodies returns nothing.
- Two `nexa-project-setup` "Supporting files (to be created)" stubs that conflict with editorial
  standard 1 (deprioritize version specifics): `libs.versions.toml.template` ("copy-pasteable
  known-good pins") and `version-compatibility-matrix.md` ("known-good libnexakotlin/libnexaapp/
  Kotlin tuples"). Both would bake in pinned Nexa versions that drift out of date — exactly what the
  standard removes elsewhere — so creating them would reintroduce the noise. The substance they
  gestured at (how versions relate, how to find current ones) already lives in the `[versions]`
  placeholder block, the "Verifying version compatibility" POM-cross-check pattern, and the
  version-drift anti-patterns. `settings.gradle.kts.template` (a self-contained, version-free
  endpoints file) was kept as a valid stub. Removed at the maintainer's direction.

### Reframings re-checked (no change needed — already compliant)
- **Version specifics.** No Nexa-library version numbers appear in any skill body. The
  `nexa-project-setup` `[versions]` block is `<latest>`/relationship placeholders with a pointer to
  the GitLab Maven registries; the only concrete versions in skill bodies are (a) the
  kotlinx-serialization `1.10.0` CBOR caveat — a genuine version-specific behavior the standard
  permits — and (b) the third-party JUnit `testRuntimeOnly` pins in the build snippet, which Pass
  15 deliberately left concrete (stable non-Nexa test tooling; `<latest>` would make the snippet
  non-resolvable; the reframing's stated rationale is about the *fast-iterating Nexa stack*). I
  honored that documented decision rather than re-litigate it; genericizing the JUnit pins remains
  a deliberate future choice, not an oversight.
- **Named applications.** A grep for the apps named in prior CHANGELOG history (the prediction
  market, the NFT/warrior marketplace, the DEX, demo/enterprise *apps*) returns nothing in any
  skill body. The Wally wallet, Trickle Pay, and the `org.wallywallet:wew` library remain (protocol
  / library infrastructure, in bounds), as do neutral example domains (order book, marketplace
  listing, vesting schedule, Alice/Bob delegation).
- **On-disk vs off-disk.** No skill assumes a checkout layout on the reader's machine. The only
  "on-disk"/"disk" phrases left are genuine concepts, not machine paths: the wallet's per-wallet
  on-disk database and `encrypt`/clear-text discussion (`nexa-wallet-lifecycle-and-chain`) and
  `SerializationType.DISK` / "on-disk serialization format" (`nexa-transaction-construction`) — a
  wire-format concept Pass 9/15 already ruled in-bounds. Library-source pointers (`ichain.kt`,
  `nslxlat.kt`, the scriptmachine `Test.kt`, etc.) are "where to find" guidance, which standard 3
  permits.

### INDEX
- No change. Pass 3 created "Where to find canonical sources" (library names, `org.nexa:*` Maven
  coordinates, GitLab Maven project numbers, and a "look up the current version in the registry
  rather than trusting a number copied here" note); Passes 9 and 15 verified it, and Passes 16–19
  only extended skill-row descriptions without regressing it. Re-verified: it carries no on-disk
  paths, no named applications, and no concrete version pins, and already matches what this pass's
  Step 3 asked for. Churning it would add noise.

### New skills created
- None. Per the consolidation brief.

### Notes for the next agent

**The three editorial standards are the law for skill bodies and INDEX. Do not re-introduce what
this and the prior consolidation passes (3, 9, 15) removed.** Restated so they cannot be lost:

1. **Deprioritize version specifics.** Skill bodies and `nexa-project-setup`'s `[versions]` block use
   placeholders (`"<latest>"`, `"<match libnexaapp's transitive kotlin-stdlib>"`) plus a pointer to
   the GitLab Maven registry — **not** pinned Nexa-library version numbers. Library coordinates
   (`group:artifact`) and the repository URLs stay; version *numbers* in TOML/Gradle snippets are
   placeholders. A concrete version appears **only** where it marks a genuine API-surface change
   (the `millinow → epochMilliSeconds` rename, framed as "renamed in a release") or a genuine
   version-specific behavior (the kotlinx-serialization 1.10.0 CBOR caveat). The third-party JUnit
   pins are a deliberate, documented exception (stable non-Nexa tooling). The maintainer's libraries
   iterate faster than this documentation, so a pinned Nexa number is wrong by default — trust the
   published POM, not a number copied into a doc.

2. **De-anchor from named applications.** Skill bodies and INDEX describe Nexa *infrastructure*
   (libnexakotlin, libnexaapp, NPL, scriptmachine, nexarpc, mpthreads, the `org.wallywallet:wew`
   library, electrum clients, and the Wally wallet with its TDPP/nexid/Trickle Pay protocol) and
   *patterns* extracted from real apps — never the apps themselves by name. Keep the distinction the
   corpus already navigates: **the Wally wallet and Trickle Pay are protocol infrastructure** (the
   counterparty these skills integrate with) and may be named; a specific *application built on the
   stack* (a prediction market, a marketplace, a DEX, a demo or enterprise *app*) may not. Patterns
   learned from such apps stay, rewritten with neutral domains. Provenance / "verified against app
   X" / "real-world exemplar" blocks do not belong in skill bodies — extract the substantive claim
   (e.g. "production settlement timeouts are measured in hours-to-days, not the sub-hour values that
   only work on auto-mining regtest") and state it generically. Named apps may appear **only here in
   the CHANGELOG**, for historical reasoning.

3. **Remove on-disk vs off-disk distinctions.** Skill bodies assume no checkout layout on the
   reader's machine. Name the library and its Maven coordinate / GitLab project so a developer can
   find the source themselves; INDEX is the authoritative "where to look" map. Light pointers into
   *library* source files ("libnexakotlin's `cnxnmgr.kt`", "NPL's `opParseHelpers.kt`", the
   scriptmachine library's own `Test.kt` as a worked-example oracle) are acceptable as "where to
   find" guidance; "the source is at ~/Desktop/…" or "X is published-only with no source on disk" is
   not. Genuinely universal developer paths (`~/.gradle/caches`, `~/.m2`) in the POM-cross-check
   recipe are fine, and `SerializationType.DISK` / "on-disk serialization format" is a wire-format
   concept, not a machine-path reference. Leave both.

**Why this matters / how it creeps back.** A content pass makes a correct, well-evidenced fix and
wraps it in a `<!-- PRIOR -->`/revision-note for traceability (reasonable mid-pass), or cites the
app it learned a pattern from, or pins the version it verified against. That bookkeeping is
internal. This is now the **fourth** consolidation pass to remove the same classes of artifact, and
the recurring lesson is unchanged: traceability annotations and provenance are valuable *during* a
pass and noise *after* it. The healthy pattern the last few content passes followed — add at most
one clearly-marked `<!-- PRIOR -->`/revision note when a correction genuinely needs to be reversible
by a downstream reader, and leave an explicit CHANGELOG note that a future consolidation pass should
fold it — kept the cleanup this pass had to do down to a single block. Keep doing that; fold before
you ship if you can, and flag it here if you can't.

**Substance preserved.** No technical content, pattern, anti-pattern, mental model, or code example
was added or removed this pass — only audit framing. The one "where did it go?" lookup: the
`createTdppUrl` "no host/cookie param, auto-derives `inamt`, append your own cookie" fact is now a
plain paragraph directly beneath the Pattern-6 partial-offer push-URI code block in
`nexa-transaction-construction`.

**Untouched targets that remain.** The "Supporting files in this folder (to be created)" stubs at
the bottom of every skill are still the standing mechanical backlog (Pass 19 wrote
`nexa-npl-smart-contracts/dslReference.md`; the rest — `walletUriFormats.md`,
`groupIntrospectionReference.md`, `rpcMethodReference.md`, `regtestHarness.kt`,
`contractSpendTestHarness.kt`, `stackItemFormat.md`, `opcodeStepThrough.md`, the various
`examples/` — remain empty, all writable from source by an agent with the repos checked out). The
content-side gaps prior passes flagged (a focused wallet message sign/verify skill; fiat/price
helpers; an end-to-end CAPD-coordinated multisig contract example; a connection-manager/SPV
network-layer skill) are still open. None were in scope for a consolidation pass.

## Pass 21 — 2026-06-03 — Claude (Opus 4.8, 1M context)

**Supporting-files pass: filled the long-standing "Supporting files (to be created)" backlog that
every prior pass flagged.** This refinement ran in a workspace where all the grounding repos are
present as siblings (libnexakotlin, libnexaapp, npl [this repo], nexarpc/`nexarpckotlin`,
scriptmachine/`nexascriptmachinekotlin`, the Wally `wallet`, and the Nexa Spec), so the reference
docs and drop-in templates the stubs called for were finally writable from source rather than
inference. Every API fact was verified against the actual declarations (signatures read directly,
not paraphrased — extraction was fanned out to read-only subagents that quoted verbatim, then
written up centrally for consistent framing). Editorial standards held throughout: no Nexa version
pins, no named applications, no on-disk paths — only library symbol names, library-source file
pointers, and stable protocol/spec facts (electrum port constants, TDPP `flags` bit values, CAPD
PoW formula, P2P reject codes) cited to the public spec.

### Added (28 supporting files; the two `examples/` dirs deliberately deferred)
- **nexa-script-machine-testing/**: `stackItemFormat.md` (the `getStackItemText` grammar +
  sign-magnitude/BMD, grounded in the scriptmachine library's `Test.kt` assertions),
  `contractSpendTestHarness.kt` (drop-in JUnit base), `opcodeStepThrough.md` (step/inspect walkthrough).
- **nexa-npl-smart-contracts/**: `compileAndPrintTemplate.kt` (compile scaffold + bytecode/hash print,
  from the repo's own contract test), `opcodesDecoded.md` (DSL→opcode decode guide via `toAsm`).
- **nexa-tokens-and-groups/**: `groupIntrospectionReference.md` (the full NSL group-accessor surface +
  the five authority variants + `verifySameGroup`, from `nsl.kt`).
- **nexa-electrum-monitoring/**: `electrumMethodReference.md`, `addressWatcherTemplate.kt` (from
  libnexakotlin `electrumclient.kt`/`token.kt`).
- **nexa-identity-and-addresses/**: `addressTypesTable.md` (the `PayAddressType` enum + `lockingScript`
  + `parseTemplate` behavior), `validateHelpers.kt` (app-level `requireP2PKT`/`requireP2PKH`/
  `requireLooksLikeNexaAddress` guards — explicitly *not* library functions).
- **nexa-transaction-construction/**: `simpleapi-cheatsheet.md`, `templates/build-partial-tx.kt`
  (the three build flavors + half-tx offer + `TxCompletionFlags` combos), `templates/broadcast-tx.kt`
  (retry + outcome categorization, with the verified `txCompleter`/flag values).
- **nexa-locktime-cltv/**: `cltvCheatsheet.md`, `mtpMonitor.kt` (MTP from the last 11 header times, or
  node-exact via `getblock(...).mediantime`).
- **nexa-wallet-lifecycle-and-chain/**: `walletStartupTemplate.kt`, `recoveryFlow.md` (encoding the
  new-doesn't-scan vs recover-scans distinction from `init.kt`).
- **nexa-capd-messaging/**: `capdConversationTemplate.kt` (two-party `CapdProtocolCommunication`
  harness), `messageFormat.md` (CapdMsg wire fields + PoW/priority + rescind, from the CAPD spec).
- **nexa-wallet-connection/**: `walletUriFormats.md`, `flowchart.md`, `qrRouteTemplate.kt` (from the
  DPP spec + libnexaapp `tdpp.kt`/`qr.kt`; the `createQrSvg` no-escape fact drives the QR workaround).
- **nexa-ktor-server-integration/**: `applicationModuleTemplate.kt`, `startercfg.json.template`,
  `corsProdVsDev.md` (incl. the WS-upgrade-isn't-CORS-preflighted caveat).
- **nexa-server-state-and-flows/**: `flowBindingProtocol.md` (message-type bytes + bind handshake +
  `FlowDirection`/`FlowScope` + verified duplicate-name throws), `examples/perSessionViews.kt`.
- **nexa-rpc-node-client/**: `rpcMethodReference.md`, `regtestHarness.kt` (from `nexarpckotlin`).
- **nexa-project-setup/**: `settings.gradle.kts.template` (all GitLab Maven endpoints).
- **nexa-debugging-onchain-errors/**: `errorCodeReference.md` (P2P reject codes from the spec +
  broadcast strings + `NexaRpcException`), `decodingBytecodeHowto.md`, `runbookBrokenBuild.md`.
- Each affected `SKILL.md` had its "Supporting files (to be created)" list updated to reflect what
  now exists (only the two `examples/` dirs remain under "to be created").

### Corrected
- None. All additions are new supporting files + the SKILL.md supporting-files-list updates; no
  prior skill-body claim was overwritten. (The two version-pinning `nexa-project-setup` stubs —
  `libs.versions.toml.template` / `version-compatibility-matrix.md` — were removed at the
  maintainer's direction as conflicting with editorial standard 1; recorded under Pass 20's Deleted.)

### Flagged for review
- **`nexarpc` `getstat*` discrepancy.** `rpcMethodReference.md` documents the `NexaRpc` interface as
  read from source (`nexarpckotlin`), which exposes `gettxpoolinfo`/`bch_getmempoolinfo`/`getpeerinfo`
  rather than the `getstat`/`getstatlist` + typed `getstat{Int,…}` series the `nexa-rpc-node-client`
  SKILL body mentions (documented in Pass 12). Either the SKILL was written against a different
  nexarpc version or the series was removed/renamed; the new reference notes the absence and says to
  verify against your artifact. A future content pass with a definitive nexarpc version should
  reconcile the SKILL body with the reference (I did not edit the SKILL body this pass — out of scope
  for filling supporting files, and I can't prove which version is canonical).
- **`.kt` templates are source-grounded, not compile-verified.** I did not build any of them against
  the artifacts (the workspace has the source, but I did not run a Gradle build). Signatures and
  flag/field values are quoted from current source; an agent who can compile should promote them to
  compile-verified and fix any import-path nits (e.g. the exact module/package a symbol like
  `createQrSvg` is exported from).

### Deferred (not done this pass)
- `nexa-npl-smart-contracts/examples/` (atomic swap, multi-sig escrow, oracle bet, HTLC) and
  `nexa-tokens-and-groups/examples/` (fungible transfer, same-group covenant vault, authority/baton
  delegation chain, subgroup-minted NFT). These call for *full example projects with generated
  bytecode + satisfier construction*, which requires actually compiling against the artifacts +
  `libnexa.so` to produce real bytecode and verified satisfiers — and novel contracts risk the
  `Cannot find state transition` compile error. Per maintainer direction, left until they can be
  compiled and verified. HTLC and oracle-bet have faithful in-repo references
  (`secretSaleContract.kt`, `wallyOracle.kt`) to build from; atomic-swap and escrow do not yet.

### New skills created
- None. This pass only filled supporting files within existing skills.

### Notes for the next agent
- **The workspace layout is what made this pass possible.** All grounding repos sit as siblings
  under the same parent: libnexakotlin, libnexaapp, npl (this repo), `nexarpckotlin`,
  `nexascriptmachinekotlin`, the Wally `wallet`, and the `Nexa Spec` (capd.md, dpp.md,
  reject.md). If the corpus is relocated, that reach is what a future agent needs to fill/verify the
  remaining `examples/` and to compile-check the `.kt` templates. The two non-`org.nexa:*` sources
  the corpus leans on but INDEX doesn't list — the **Wally wallet** repo and the **full node**
  (for reject codes / regtest) — were covered here via the spec; consider adding them to INDEX's
  canonical-sources list.
- **Editorial standards held.** No version pins, named apps, or on-disk paths entered any file. Stable
  protocol/spec facts (electrum port numbers, TDPP `flags` bit values, CAPD PoW, reject codes) are
  cited to the public spec or named library constants, consistent with how the corpus already treats
  wire-format facts.
- **The two open `examples/` dirs are the last backlog item.** Everything else the prior passes
  flagged as "writable from source" is now written.

## Pass 22 — 2026-06-03 — Claude (Opus 4.8, 1M context)

**Validation + missing-detail pass against the Nexa specification (`spec.nexa.org`).** This
refinement runs *inside the Nexa Spec repository* (`specification/docs/…`), so for this pass the
**protocol spec itself was the oracle** — the full opcode table, `OP_PARSE` / `OP_PUSH_TX_STATE`,
script templates, address cashaddr version bytes, group tokenization, the token-description document,
the Challenge Transaction, sighash types, CLTV, CAPD, bignum, and the P2P reject codes were read
directly and cross-checked against the corpus. The corpus held up extremely well: I found **no
library-API errors** and only **one factual claim that contradicts the spec** (where
`decimal_places` is committed). Everything else this pass is **additive sharpening** — pinning
on-chain/wire-format details the skills gestured at but didn't state precisely — plus inline
citations to the authoritative spec pages. Editorial standards held: no version pins, no named
applications (the spec's NiftyArt example was *not* carried into any skill body — token examples stay
neutral), no on-disk paths. `spec.nexa.org` page URLs are cited as stable public protocol references,
exactly as `nexa-capd-messaging` already cited the CAPD spec URL.

### Corrected
- `nexa-tokens-and-groups` Pattern 7: the claim that a fungible token's `decimal_places` "lives in the
  off-chain token-description document (TDD)". **Per the spec** (`tokens/tokenDescription.md`),
  `decimal_places` is committed **on-chain in the genesis OP_RETURN** (the `88888888` record:
  `ticker / name / uri / sha256 / [decimal_places]`), and the spec'd TDD JSON *dictionary* field list
  does **not** include it; `TokenGenesisInfo.decimal_places` reflects the genesis record. Prior text
  preserved in a `<!-- PRIOR -->` comment + `> **Revision note:**`; the corrected intro, a new
  "Where `decimal_places` actually lives" blockquote (noting some apps may *also* echo it into their
  served TDD, default 0, range 0–18), and the genesis-OP_RETURN structure now carry the accurate
  picture. Confidence: high (the spec's OP_RETURN field list includes it; the TDD dict list omits it).
  *For the next consolidation pass:* this is the only audit block added this pass — fold it.

### Added
- `nexa-npl-smart-contracts` Pattern 9 (OP_PARSE field table): added the **reserved fields 5/6/7
  (always `OP_0`)** row — this is *why* visible args start at field 8, a gap the table left
  unexplained; sharpened **field 1** (for an ungrouped or *fenced* output it returns the native-NEXA
  amount, not 0 — pair amount checks with a group-id check) and **field 3** (well-known-template
  *number* vs full *hash* are not converted by `OP_PARSE` — compare against the form your contract
  expects); documented all four **parse operations** (`OUTPUT_DATA 0`, `PREVOUT_DATA 1`, `INPUT_DATA
  2` with its canonical input form `0`=template bytecode/`1`=args bytecode/`2…`=satisfier pushes, and
  `BYTECODE_DATA 3`) where the skill previously named only the first two; cited the op_parse spec.
- `nexa-npl-smart-contracts` mental model: the **push-only satisfier** consensus rule (unlocking script
  is data pushes only, opcodes ≤ `0x60`; may push scripts as data), and the **template execution
  model** (hidden args parsed to the **alt** stack, then visible args to the alt stack — so the two
  are indistinguishable once pushed and a holder can move a parameter between hidden/visible slots;
  spender args go on the main stack; the args-hash may be `hash160` *or* `hash256`, size
  disambiguates). Grounded in `addresses/scriptTemplates.md`.
- `nexa-tokens-and-groups` Pattern 4: same field-1 ungrouped/fenced-returns-native-amount nuance; and a
  note that the count/enumerate helpers (`countInputsByGroup`/`countOutputsByGroup`/`groupedOutputN`/
  `groupIdOf`) compile to **`OP_PUSH_TX_STATE` (`0xea`)**, a *different* opcode from the per-output
  `OP_PARSE` reads — with the spec's behavior (`GROUP_*_COUNT` returns 0 for an absent group, but
  `GROUP_NTH_*` *fails the script* for a non-existent index → guard an enumerate with a count first).
- `nexa-tokens-and-groups` Pattern 1: confirmed the content-addressed subgroup matches the NFT spec's
  **MUST** (subgroup id = double-SHA256 of the NFT `.zip`; libnexakotlin `hash256` *is* that double
  SHA256), and *why* (it lets a holder prove a data file is this NFT).
- `nexa-tokens-and-groups` Pattern 7: the **genesis OP_RETURN structure** (type tag `88888888`; fields
  ticker/name/uri/sha256/[decimal_places]; sign the OP_RETURN + authority output with ALL/ALL to
  prevent malleation); the **NFT genesis OP_RETURN `88888889`** (title / double-SHA256 of the zip /
  URL); the richer **TDD dictionary** field set + the strict `signmessage`-style "exact bytes,
  brace-to-brace" signature rule; reserved-ticker guidance (NEX/KEX/MEX, ISO-4217, exchange symbols);
  and that `token new` also returns the `tokenDescriptorSigningAddress`.
- `nexa-tokens-and-groups` Pattern 8 + `nexa-wallet-connection` (`/assets` callback): the `/assets`
  ownership `proof` is formally a **Challenge Transaction** — pinned the spec mechanism: nVersion
  with the **high bit set (`>127`)** for guaranteed invalidity (verifier must *not* reject on that),
  single `OP_RETURN` whose **first push is the challenger host** (the anti-spoof check), second push
  the challenge; the wallet **interleaves a random byte before every challenge byte** (issued bytes
  are the odd-indexed ones); ownership requires independently **verifying the UTXO existed on-chain**
  (merkle/SPV or own-node lookup) because script validity alone can be faked; and `chalby` (bytes
  only) vs `chaltx` (full tx). Cited `transactions/challengeTransaction.md`.
- `nexa-identity-and-addresses` mental model: a **cashaddr version-byte / first-character cue** table —
  `q…` = P2PKH (version 0, the identity format), `n…` = Pay to Script Template (version 152; P2PKT
  payout + P2CAT/P2CT contract forms), `t…` = GROUP token type (version 88) — a cheap at-a-glance
  classifier for the identity-vs-payout-vs-group distinction the whole skill is about, plus the spec's
  formal terminology (P2ST / P2PKT / P2CAT / P2CT). Grounded in `addresses/cashaddr.md` +
  `address-types.md`.
- `nexa-transaction-construction`: the **idem-vs-id** distinction (idem = no input signatures,
  malleation-stable, used for outpoints + wallet/explorer indexing — use `tx.idem`; id = all bytes,
  used in the block merkle tree; callbacks carry both `txid`/`txidem`), grounded in
  `transactions/transactionIdentifier.md`; and the **sighash-type flags** (Schnorr 64 bytes + sighash
  bytes; default `0` = all-in/all-out; input flags all/first-N/this-input, output flags
  all/first-N/two-outputs-N,M) as the cryptographic basis of the half-tx/partial-tx offer idiom and
  what `sigHashTypeOverride`/`signInput` take. Grounded in `transactions/sighashtype.md`.
- `nexa-debugging-onchain-errors`: sharpened the reject-code decoder with the exact spec values —
  `16` REJECT_INVALID (consensus), `64` REJECT_NONSTANDARD, `65` REJECT_DUST, `66` REJECT_INSUFFICIENTFEE
  (= `mempool min fee not met`), `01` REJECT_MALFORMED — and the "match the numeric code, not the
  advisory reason string" rule. Grounded in `network/messages/reject.md`.
- `nexa-capd-messaging` Pattern 3: the spec's **priority-decays-to-zero-at-~600-seconds** fact (a
  message ages out of the relay tier ~10 min after `createTime` regardless of PoW; longer
  `expiration` keeps it query-retrievable, not relayed), concretely motivating the re-broadcast
  guidance. Grounded in `network/capd.md`.
- `nexa-locktime-cltv` Trap 3: the spec's **CLTV threshold-side-match** rule — `OP_CHECKLOCKTIMEVERIFY`
  fails if the contract value and `nLockTime` are on opposite sides of 500,000,000 (one a height, one
  a timestamp), and on a negative/empty top item — so both sides must be the same domain. Grounded in
  `script/1script.md` (Locktime) + BIP65.
- `INDEX.md`: a "where to find canonical sources" paragraph naming **`spec.nexa.org`** as
  authoritative for protocol/consensus/wire-format facts, with the spec-vs-library division of
  authority (spec = *what the chain does*, library source = *the Kotlin signature that does it*).

### Verified accurate (no edit needed — recorded so a future pass needn't re-check)
- **CLTV / nSequence** (`nexa-locktime-cltv`): the `nSequence == 0xffffffff` → locktime-ignored rule,
  the 500,000,000 height/timestamp threshold, and the 32-bit range all match the opcode spec verbatim.
- **P2T locking-script field order** (`nexa-npl-smart-contracts`): group id, group amount (omitted when
  group id is 0), template hash, hidden-args hash (or `0x00` for none), visible args — matches
  `scriptTemplates.md`.
- **Script-VM success criteria** (`nexa-script-machine-testing`): "execution completes + clean (empty)
  main stack, alt stack exempt" matches `1script.md`; the sign-magnitude binary format (LE
  abs-value + `0x00`/`0x80` sign byte) and BMD/`OP_BIN2BIGNUM` match `bignum.md`. Left unedited — the
  skill is already thorough and spec-accurate.
- **CAPD** (`nexa-capd-messaging`): PoW formula, `createTime` future-rejection (the 5s backdate),
  `expiration` UShort-default-never (`0xFFFF`), 20-byte `recindHash`, the convo-id-as-TCAM-filter
  model, and `installMsgMonitor` ↔ `CAPD_QUERY_NOTIFY` all match `network/capd.md`.
- **nexid signature** (`nexa-wallet-connection`): the `<host><port>_nexid_<op>_<challenge>` signed
  string and base64 sig are consistent with the spec's `signmessage` scheme (same scheme the TDD
  signature uses) — already accurate, only the `/assets` proof got the challenge-tx sharpening.

### Flagged for review
- None. The one spec-vs-corpus conflict (`decimal_places` location) was correctable with high
  confidence from the spec and is handled as a marked correction, not a flag.

### New skills created
- None. This pass validated and sharpened the existing 15 skills against the spec; every gap I found
  was a sub-topic of an existing skill.

### Notes for the next agent
- **The spec is now a first-class, cited oracle.** Where a skill states an on-chain/wire-format fact
  (opcode semantics, field layout, address/version bytes, token/genesis structure, the challenge-tx,
  sighash, reject codes, CAPD), it can and now often does cite the matching `spec.nexa.org` page.
  When validating a *chain-behavior* claim, the spec is canonical; when validating a *Kotlin
  signature*, the library source is. Don't "correct" a spec-grounded chain fact from a library quirk,
  or vice versa.
- **Internal spec inconsistencies to be aware of** (so you don't "fix" a skill toward a stale spec
  page): `tokens/grouptokens.md` is explicitly written "in the context of the old format" — it says
  group ids are "20 or 32 bytes" and references a "z" cashaddr prefix / "type 2", while the *current*
  dedicated addressing docs (`cashaddr.md`, `address-types.md`) and the newest `token new` example
  say **32 bytes** and a **"t"** prefix (version byte 88). I grounded the skills on the newer
  addressing docs (the corpus already used "t" and ">=32 bytes"). If a future pass sees the "z"/"20
  bytes" language, treat it as legacy.
- **Verified-but-not-yet-written spec surface** (real, omitted to avoid over-deepening): the full
  introspection opcode set (`OP_INPUTINDEX`/`OP_UTXOVALUE`/`OP_OUTPUTBYTECODE`/… at `0xc0`–`0xcd`)
  beyond the OP_PARSE/OP_PUSH_TX_STATE slice a `dslReference.md`/`opcodesDecoded.md` could enumerate;
  `OP_EXEC` + scriptlets (a holder pushing a script as a visible/hidden arg for the template to
  `EXEC` — the deep mechanism behind `OP.TMPL_SCRIPT` template-spendable outputs, touched in
  `nexa-tokens-and-groups` Pattern 9); the `read-only-inputs`, `negative_op_roll_op_pick`, and
  `integer-division`/`bignum_modulo_divisor` script features; the NFT invocation/marketplace routes
  (`/token/{id}`, `/raw/{id}?chalby=`); and `OP_CHECKSEQUENCEVERIFY`/BIP68 relative locktime (the
  `nexa-locktime-cltv` skill correctly scopes itself to absolute CLTV and points elsewhere for CSV).
- **What I did NOT touch.** No version/framing changes; the Pass 3/9/15/20 editorial standards held.
  I reopened only the skills with a spec-verifiable surface; `nexa-project-setup`,
  `nexa-ktor-server-integration`, `nexa-server-state-and-flows`, `nexa-rpc-node-client`,
  `nexa-wallet-lifecycle-and-chain`, and `nexa-script-machine-testing` were read in full and left unedited
  (their claims are library/framework-level, not protocol-spec-level, or already spec-accurate). The
  two `examples/` dirs remain the only "to be created" backlog. I added exactly one `<!-- PRIOR -->`/
  revision-note block (the `decimal_places` correction) — fold it in the next consolidation pass.

## Pass 23 — 2026-06-10 — Claude (Opus 4.8, 1M context)

This is a **packaging / formatting** pass to make the corpus conform to the official Anthropic
Agent Skills spec (platform.claude.com → agent-skills/best-practices). No technical content was
added, corrected, or removed beyond the de-duplication explicitly bounded below. The fixed section
structure, all anti-patterns, all security items, and all `<!-- PRIOR -->` audit trail were
preserved.

### Added
- All 15 `SKILL.md`: added required YAML frontmatter (`name` + `description`). `name` is kebab-case
  (spec requires lowercase/digits/hyphens, ≤64 chars, no reserved words); `description` is third
  person, states what + when, and is ≤1024 chars (range 636–801). Descriptions were distilled from
  each skill's existing "When to use this skill" triggers — the body section was left intact.

### Corrected
- **Folder + name convention.** Renamed all 15 skill folders camelCase → kebab-case via `git mv`
  (e.g. `nexaWalletConnection` → `nexa-wallet-connection`) so folder name == frontmatter `name`,
  per spec best practice. The previous camelCase folder names were not valid skill `name`s.
- **Corpus-wide cross-reference rename.** Every camelCase skill reference (`nexaXxx`) was rewritten
  to its kebab form across all `*.md` (SKILL bodies, reference files, `INDEX.md`, and — for
  consistency — historical `CHANGELOG.md` entries). 0 camelCase skill references remain. This is a
  naming normalization only; no historical *reasoning* in the CHANGELOG was altered.

### De-duplication (conservative, body↔own-reference only)
Per the maintainer's instruction, removed only content already present in a skill's **own** bundled
reference file, leaving enough in the body to stay effective and tightening the deferred prose to a
pointer. Cross-skill repetition was left intact (it is intentional — skills load independently). No
section was relocated wholesale.
- `nexa-wallet-connection/SKILL.md`: condensed the libnexaapp URI-builder block — the verbatim
  builder signatures live in `walletUriFormats.md` (added `sendPaymentUri`'s signature there so
  nothing was lost); kept the `createTdppUrl` caveats inline. (658 → 635)
- `nexa-npl-smart-contracts/SKILL.md`: condensed the signature/oracle blurb (→ `dslReference.md` §4)
  and the `nsllib` helper list (→ `dslReference.md` §8) to names + semantics + pointer. (1035 → 1019)
- `nexa-tokens-and-groups/SKILL.md`: condensed the whole-tx group-helper list (→
  `groupIntrospectionReference.md`); kept the OP_PARSE-vs-OP_PUSH_TX_STATE distinction inline.
  (787 → 782)

### Flagged for review
- **Bodies still over the spec's 500-line guideline:** `nexa-npl-smart-contracts` (1019),
  `nexa-transaction-construction` (798), `nexa-tokens-and-groups` (782), `nexa-ktor-server-integration`
  (654), `nexa-wallet-connection` (635), `nexa-script-machine-testing` (571). Left large
  **deliberately**: the maintainer chose conservative dedup over an aggressive progressive-disclosure
  split, because the remaining bulk is unique substance (multi-pattern code, the cumulative
  anti-pattern sections the iteration prompt protects) rather than duplication. Getting them under
  500 would require *moving* whole reference-grade sections (e.g. the npl compile/state-transition
  deep-dive, the wallet flags-bitfield + nexid-signature + request-signing detail, the
  txCompleter/sighash deep-dive) into the bundled `*Reference.md` files — a future opt-in pass.

### New skills created
- None.

### Notes for the next agent
- **Editorial standards (Pass 3/9/15/20) still hold** — no version pins, app names, or on-disk
  framing were introduced.
- **The 500-line guideline is the main open structural item.** If a future maintainer wants the six
  flagged skills under 500, the safe mechanism is progressive disclosure: move exhaustive
  reference-grade tables/signatures into the existing (or new) one-level-deep `*Reference.md` files
  and leave a strong inline pointer + the mental model/patterns in the body. The reference files
  already established (`walletUriFormats.md`, `dslReference.md`, `groupIntrospectionReference.md`,
  `simpleapi-cheatsheet.md`, `corsProdVsDev.md`) are the natural homes.
- **`transaction-construction`, `ktor-server-integration`, `script-machine-testing` had ~no safe
  body↔reference duplication** to remove under the conservative rule — their reference files cover
  *different* angles (a units cheat-sheet, a prod-CORS variant, a stack-text grammar / worked
  walkthrough), not duplicates of the body. Don't force-trim them without the progressive-disclosure
  split above.
- The two `examples/` "to be created" backlog dirs remain unaddressed.

## Pass 24 — 2026-06-15 — Claude (Opus 4.8, 1M context)

Two targeted improvements on top of Pass 23: a **progressive-disclosure split** on the three
largest skills (moving on-demand reference material into bundled docs while keeping every high-value
pattern inline), and **library-signature-verification guidance**. Section structure, anti-patterns,
security, and audit trail preserved; moved content is near-verbatim.

### Added
- `nexa-project-setup/SKILL.md`: new "Verifying API signatures before relying on them" subsection —
  `javap` / sources-jar / IDE "go to declaration" against the *resolved* artifact, and the rule
  **"when a signature here disagrees with the jar, the jar wins."** The canonical home for the
  signature-drift caveat.
- One-line pointers to that subsection added to the Setup sections of `nexa-npl-smart-contracts`,
  `nexa-transaction-construction`, and `nexa-tokens-and-groups` (the most signature-dense skills).

### Changed — progressive-disclosure split (on-demand reference moved out; pointers left inline)
- `nexa-npl-smart-contracts/SKILL.md` (1019 → 933): moved the compile init-scaffold tier table +
  cache mechanics and the entire `Cannot find state transition` deep-dive (the mechanism + both
  manual fixes with code) into new **`stateTransitions.md`**; body keeps the compile-in-a-test
  workflow and a tight "When compilation fails" summary. Also folded the OP_PARSE two-accessor-family
  bullets in Pattern 9 to one line (fully covered by `dslReference.md` §9 + the dedicated
  anti-pattern).
- `nexa-transaction-construction/SKILL.md` (798 → 758): moved the full `txCompleter(...)` signature,
  the named-arg deep-dive (`adjustableOutput`, negative `inputAmount`, `signInput`), and the
  sighash-type model into new **`txCompletionReference.md`**; body keeps the `TxCompletionFlags`
  table, the common fund/sign/broadcast example, and the half-tx swap-offer idiom (with a summary
  noting the moved knobs exist).
- `nexa-tokens-and-groups/SKILL.md` (782 → 737): moved the token-description-document (TDD) JSON
  shape + signature canonicalization, the genesis OP_RETURN byte layout (`88888888`/`88888889`
  tags), and the reserved-ticker rules into new **`tokenMetadataReference.md`** — the
  `decimal_places` `<!-- PRIOR -->`/revision-note audit trail moved *with* that content. Body keeps
  the genesis/definition DSL (Pattern 7) and the key facts (metadata off-chain; `decimal_places`
  on-chain; identify by group id).

### Flagged for review
- The three split bodies remain over the 500-line guideline (933 / 758 / 737) **by design**: their
  remaining bulk is unique multi-pattern code plus the cumulative anti-pattern sections the iteration
  prompt protects — a higher value floor than 500. Further reduction would mean moving *inline-value*
  content, not reference detail.
- `nexa-ktor-server-integration` (654) and `nexa-wallet-connection` (635) were assessed and **not**
  split: ktor's bulk is a high-value worked server example + anti-patterns (no clean on-demand block).
  wallet-connection *does* have a viable future split — its flags-bitfield, nexid-signature, and
  request-signing detail could move into `walletUriFormats.md` — left for a future opt-in pass.

### New skills created
- None. Three new **supporting reference docs** (not skills): `stateTransitions.md`,
  `txCompletionReference.md`, `tokenMetadataReference.md`. Each is one level deep from its `SKILL.md`
  and registered in that skill's "Supporting files" list; all pointers verified resolving.

### Notes for the next agent
- **Signature-verification guidance is canonical in `nexa-project-setup`.** The other
  signature-heavy skills (`nexa-wallet-connection`, `nexa-rpc-node-client`,
  `nexa-script-machine-testing`, `nexa-wallet-lifecycle-and-chain`, `nexa-electrum-monitoring`) reach
  it through their existing "per `nexa-project-setup`" Setup pointer; add the explicit one-liner to
  their Setup sections too if you want it locally visible.
- **The split pattern is repeatable.** If a future maintainer wants the remaining large skills
  smaller, the wallet-connection split above is the next clean candidate; the rule that worked here
  is "move what an agent needs *occasionally* (exhaustive tables, full signatures, wire formats,
  compiler internals); keep what it needs *every time* (mental model, the common patterns, the
  anti-patterns)."

## Pass 25 — 2026-06-15 — Claude (Opus 4.8, 1M context)

**Content pass: filled the front-end / UI gap with a new skill grounded in libnexaapp's
Compose design library.** Every prior skill in this corpus is backend/protocol (wallets, tx,
NPL, tokens, RPC, electrum, CAPD, flows); the corpus had **no UI skill at all**, and the
maintainer reports that agents, seeing the unstyled starter app, keep extending a bad-looking
UI. libnexaapp has since split its Compose code into a dedicated published module
(`org.nexa.libnexaapp:compose`, package `org.nexa.libnexaapp.compose`) — a real design system
(theming, reusable components, responsiveness, a design editor). This pass adds a skill that
redirects agents to that library instead of hand-rolling screens. All claims were read from
the libnexaapp compose-library source (the `DesignScheme`/`design` flow, `color.kt` utilities,
`buttons.kt`, `commonUIComponents.kt`, `fit.kt`/`init.kt` responsive system, `composing.kt`
sash containers, `lottieButton.kt`, `uiPrimitives.kt`/`useful.kt`, `designEditor.kt`, and the
module `build.gradle.kts` for the coordinate/targets), not inferred. Editorial standards held:
no version pins (coordinate + GitLab project number only), no named apps, no on-disk paths.

### Added
- `INDEX.md`: a table row for `nexa-compose-ui-design` and a "front-end UI itself" bullet in
  the "How the skills relate" section, cross-linking it to `nexa-server-state-and-flows` (the
  state it renders) and `nexa-wallet-connection` (the protocol behind `ConnectWalletButton`).

### Corrected
- None. (No existing skill touched UI, so there was nothing to correct; this is a pure addition.)

### Flagged for review
- None asserted as wrong. Two things to re-verify if a future pass touches them: (1) the
  precise relationship between the library's global `design: MutableStateFlow<DesignScheme>` and
  an app's own richer scheme flow — the skill states the accurate, conservative version (library
  components read the global `design`; an app may keep its own subclass flow and must update
  `design.value` to keep library components in sync), which is the load-bearing correctness point
  for the dark-mode pattern; (2) the exact Android bootstrap call name (`androidInitLibNexaApp`)
  — referenced as "see nexa-project-setup" rather than pinned here, since the platform-init
  surface lives in that skill.

### New skills created
- `nexa-compose-ui-design/SKILL.md` — building a clean, branded, responsive front end with
  libnexaapp's Compose Multiplatform design library. Covers the mental model (centralize tokens
  in a `DesignScheme`; drive the UI off the global `design` flow; derive palettes; the library
  leans on `foundation`/`BasicText`, not heavy Material3; the root-package gotcha for the three
  high-level components), and Core patterns: wrap in `NexaApp{}` for `appDim` responsiveness;
  subclass `DesignScheme` (light+dark); derive a palette with the color utilities; sync dark mode
  to the global `design` flow; compose from the themed components; `vsash`/`hsash` split panes;
  resource/translation/format helpers (incl. the `painterResource`-doesn't-redraw-on-web trap and
  `icon`/`img` workaround); and the JVM design editor as a dev workflow. Anti-patterns cover the
  per-screen-hardcoded-color failure mode (the actual cause of the ugly UI), the root-package
  import mistake, raw-Material3 drop-ins, missing `NexaApp`, updating only the app scheme and not
  the global `design` flow, and non-responsive fixed layouts. Two supporting files:
  `designSchemeTemplate.kt` (copy-pasteable light+dark `AppDesignScheme` deriving from one brand
  color, persisted `darkMode` flow, global-`design` sync) and `componentReference.md` (the full
  component/utility catalog with signatures). Rationale: a genuinely orthogonal gap (no UI skill
  existed) — the same justification used to add `nexa-tokens-and-groups` — so a new skill rather
  than a section, consistent with the corpus's anti-fragmentation stance for a topic that is its
  own clear entry point.

### Notes for the next agent
- **Coordinate/targets, confirmed from the module build:** `org.nexa.libnexaapp:compose`,
  published from GitLab Maven project `73565187` (the same project as libnexaapp `:app`/`:server`),
  targets JVM/`wasmJs`/Android/iOS, `commonMain` drives all. It `api`-depends on the libnexaapp
  `:library` artifact and brings in Compose + compottie (Lottie) + bignum.
- **The root-package detail is real and load-bearing.** `LightModeToggle`, `ConnectWalletButton`,
  and `NexaInputField` are declared with **no `package` statement**, so they live in the default
  package and must be imported unqualified (`import ConnectWalletButton`). The buttons, color
  utilities, layout primitives, and helpers ARE under `org.nexa.libnexaapp.compose`. An agent that
  qualifies all of them will fail to resolve those three. Verified against the source and the way
  the library's own example imports them.
- **Highest-value untouched target:** the skill's `examples/` (stubbed "to be created") — full
  worked screens (themed dashboard with dark-mode + wallet-connect header; a send screen around
  `NexaInputField`; a desktop `vsash` multi-pane). These are very writable now that the component
  surface is mapped; left out of this pass to keep it to one new skill + grounded reference docs.
- **Verified-but-undocumented surface left for a future pass** (present in source, not written up
  with full confidence from this read): the `LottieButton` event/animation system in depth (the
  `AniEvents` matrix, detached vs placed animations, z-index draw ordering) — documented here only
  at the "it exists, attach effects to states" level; the `CCSash`/`CCFracSash` weighted-container
  and persistence internals beyond the usage shape; the `Composing`/`ComposingContainer` framework
  as a general stateful-layout pattern; and the design editor's reflective property-walk mechanism.
  A `lottieAnimationReference.md` and/or `sashLayoutReference.md` are the natural homes if these
  prove commonly needed.
- **Did not touch any other skill.** No existing skill overlaps UI; the only wiring needed was the
  two `INDEX.md` edits and the cross-references embedded in the new skill's "Related skills"
  section (to server-state-and-flows, wallet-connection, project-setup, tokens-and-groups,
  transaction-construction). The maintainer also placed a generic third-party `compose-skill/`
  (general CMP architecture) outside this corpus; per maintainer guidance it was disregarded for
  this pass — if it is later integrated, the natural framing is "general CMP practice" as a
  companion to this Nexa-specific design-system skill.

## Pass 26 — 2026-06-15 — Claude (Opus 4.8, 1M context)

**Harvest pass: folded the non-conflicting, gap-filling parts of a standalone generic
Compose/CMP skill into `nexa-compose-ui-design`.** The maintainer had placed a third-party
generic Jetpack Compose / Compose Multiplatform skill (MVI/Material3/Navigation/DI-centric,
~40 reference docs) alongside the corpus and asked whether to merge it. Assessment: its
architectural spine (Material 3 theming, per-screen ViewModel/MVI, Jetpack Navigation,
Hilt/Koin DI, Room/DataStore/Paging, Coil, AGP-pinned Gradle) **conflicts with or is
irrelevant to** the libnexaapp way (Compose `foundation` not Material3; `DesignScheme`/global
`design` flow theming; `flowConnector` state not ViewModels; the library's own `icon`/`img`/
`SvgImage` and Lottie systems) and/or violates the corpus editorial standards (version pins,
named third-party stacks). So a wholesale merge was rejected. Instead this pass harvested only
the **generic-correct, currently-missing** material — reframed to the libnexaapp stack, no
version pins, no app/tool name-drops — and the maintainer is deleting the standalone skill
afterward. Each harvested claim was cross-checked against libnexaapp's compose-library source
(e.g. the `icon`/`img` `contentDescription = null` hardcode, the `LocalMinimumInteractiveComponentSize`
sub-48dp overrides) so the accessibility guidance is Nexa-specific, not boilerplate.

### Added
- `nexa-compose-ui-design/SKILL.md`: three new Core patterns — **Pattern 9 (accessibility)**,
  **Pattern 10 (Compose mechanics & performance)**, **Pattern 11 (loading/refresh UX)** —
  each concise inline with depth pushed to a new reference doc. Six new anti-patterns
  (foundation components assumed accessible; sub-48dp primary touch target; color-only status;
  business logic in composable bodies; unstable state / un-keyed lists; full-screen spinner
  wiping content on refresh). Extended the frontmatter `description` and the "When to use"
  keyword/task lists with accessibility, recomposition/performance, and loading-state triggers.
  Tightened the `nexa-server-state-and-flows` cross-ref (the Pattern 10–11 "state holder" is
  typically a `flowConnector` flow).
- `nexa-compose-ui-design/accessibilityReference.md` (new supporting doc): why accessibility
  falls on the developer here (foundation components carry no Material auto-semantics), content
  descriptions and the `icon`/`img` `null`-description trap, the semantics API, `mergeDescendants`/
  `clearAndSetSemantics`, touch targets + the library's deliberate sub-48dp override, WCAG
  contrast tied to `DesignScheme`/`alwaysLight`/`alwaysDark`, custom actions, do/don't.
- `nexa-compose-ui-design/composeFundamentalsReference.md` (new supporting doc): the
  three-phase recomposition model + deferred reads, keeping logic out of composables, state
  stability/primitives, the side-effect table, modifier order / slot pattern, `CompositionLocal`
  vs libnexaapp's global `design`/`appDim` flows, lists/keys/scroll-state, resources &
  localization (`composeResources`/`Res`/qualifiers/plurals, cross-linked to `xlat` and the
  web `painterResource` trap), `expect/actual` placement, UI-layer testing, and a performance
  anti-pattern table.

### Corrected
- `nexa-compose-ui-design/SKILL.md`: the negative trigger "General (non-Nexa) Compose
  architecture (MVI/MVVM, navigation, DI)… not generic Compose practice" was over-broad once
  Patterns 9–11 were added. Reframed to exclude only the **frameworks** libnexaapp doesn't use
  (MVI/MVVM ViewModel scaffolding, Jetpack Navigation, Hilt/Koin) while stating that general
  Compose mechanics/accessibility/loading-UX *are* now in scope. High confidence — the prior
  wording would have told an agent the skill doesn't cover the very material this pass added.

### Flagged for review
- `accessibilityReference.md` hedges one point deliberately: whether the platform `actual` of
  `SvgImage` maps its `assetName` argument to an accessibility description. The `expect`
  signature carries `assetName`, but the per-platform behavior wasn't verified across all
  targets, so the doc says "confirm; if not, add a `semantics` description" rather than
  asserting it. Re-verify against the resolved artifact if you touch this.

### New skills created
- None. This was a harvest into the existing `nexa-compose-ui-design` (Pass 25), per the
  corpus's anti-fragmentation stance — sections + two supporting reference docs rather than a
  new sibling skill. The standalone source skill is being removed by the maintainer; nothing in
  the corpus references it (no pointer, no provenance in the skill bodies — only this changelog).

### Notes for the next agent
- **What was deliberately NOT harvested, and why.** The source skill's Material 3 theming,
  MVI/MVVM ViewModel architecture, Jetpack Navigation (Nav2/Nav3), Hilt/Koin DI, Room/DataStore/
  Paging, Coil image-loading, generic animations, and AGP-pinned Gradle/CI content were all left
  out — they conflict with libnexaapp (foundation/`DesignScheme`/`flowConnector`/`icon`-`img`/the
  library's Lottie system), duplicate existing skills (`nexa-project-setup`, `nexa-ktor-server-integration`,
  `nexa-server-state-and-flows`), or assume an app architecture the Nexa corpus deliberately doesn't
  establish (a client-side SQL cache + Paging + Ktor service layer). One sub-agent suggested adding
  `nexa-room-database`/`nexa-paging-offline`/`nexa-ktor-client` skills; that was rejected as it would
  pull the corpus *toward* generic Android patterns and *away* from the libnexaapp way — the opposite
  of this corpus's purpose. If a real app ever needs local relational caching, revisit then, grounded
  in whatever libnexaapp/libnexakotlin actually provide rather than importing Jetpack Room wholesale.
- **The accessibility angle is genuinely Nexa-specific, not generic boilerplate.** The load-bearing
  insight is that libnexaapp's components are `foundation`-based, so they ship *none* of Material's
  automatic semantics/roles/touch-targets — and the library actively shrinks some controls below
  48dp and passes `contentDescription = null` from `icon`/`img`. That flips the usual "prefer Material,
  it's accessible by default" advice into "you own accessibility." Keep that framing if you expand it.
- **Both new reference docs are one level deep** from the SKILL.md and registered in its "Supporting
  files" list; pointers verified. The skill's still-open `examples/` stub (worked screens) remains the
  highest-value next addition and would now naturally include an accessible, properly-keyed list screen.

## Pass 27 — 2026-07-04 — Claude (Opus 4.8, 1M context)

**Chain-selection pass: made testnet the corpus's explicit default development chain, and reframed
regtest as an opt-in capability (block control) rather than the implied dev/test path.** Maintainer
observation: the corpus leaned toward regtest for development/end-to-end work, but real apps ship on
mainnet and should be *developed* on testnet — with regtest chosen only when the app actually needs
regtest's force-mining / deterministic-confirmation / reorg powers. Two facts were previously
scattered or missing and are now stated once, canonically, and cross-referenced: (1) the default is
testnet, the regtest-vs-testnet decision hinges on "do you need to control block production?"; and
(2) switching the app's chain is not just flipping `DEFAULT_CHAIN` — the **local node (or SPV
seeders) you connect to must be running that same chain**, and the wallet file is chain-bound too.
No chain enum was invented: `NEXATESTNET`/`NEXAREGTEST`/`NEXA` are all already used in the corpus
(e.g. `nexa-electrum-monitoring` uses `NEXAREGTEST`).

### Added
- `nexa-wallet-lifecycle-and-chain/SKILL.md`: a new Mental-model subsection **"Which chain do I
  develop on? Default to testnet"** — the canonical home for this guidance. A three-row
  testnet/regtest/mainnet table (what each is + when to develop there), the single deciding question
  (need to force-mine / control the chain?), and the **three coordinated changes** a chain switch
  requires (`DEFAULT_CHAIN`; the local node/seeders must match the chain; a chain-matched wallet
  file). Placed right after the existing "a wallet is chain-bound" note it builds on.
- `INDEX.md`: a new "Which chain to develop on" bullet in the relationship map (testnet default,
  regtest for block control, mainnet for prod, node-must-match rule → points to the canonical
  subsection).
- `nexa-rpc-node-client/SKILL.md`: a **Chain note** in the Mental model stating regtest is not the
  default dev chain (testnet is) and is reached for here specifically for block control, plus the
  node-must-run-the-same-chain rule.

### Corrected / reframed
- `INDEX.md`: the testing bullet no longer implies regtest is *the* end-to-end path ("use the regtest
  path in `nexa-rpc-node-client`") — now: testnet is the default for end-to-end, regtest when a test
  needs to force-mine or get deterministic confirmations.
- `nexa-ktor-server-integration/SKILL.md`: annotated `var DEFAULT_CHAIN = ChainSelector.NEXATESTNET`
  and the `default_chain = NEXATESTNET` / `exclusive_node` lines in the `app.cfg` example with the
  testnet-default / regtest-for-block-control / node-must-match guidance (the chain value was already
  testnet; this makes the *why* and the node-coupling explicit).
- `nexa-project-setup/SKILL.md`: the "choosing testnet vs mainnet" negative-scope bullet now states
  the testnet default and points at the canonical `nexa-wallet-lifecycle-and-chain` subsection
  (previously pointed only at `nexa-transaction-construction`, which covers routing not the choice).
- `nexa-wallet-lifecycle-and-chain/walletStartupTemplate.kt`: the "typical launch sequence" doc
  comment now models the default (`ChainSelector.NEXATESTNET`, `"mainTestnet"`) instead of
  `ChainSelector.NEXA`, with a one-line note on when to switch.
- `nexa-rpc-node-client/regtestHarness.kt`: header comment now says regtest is for tests that need
  to *control block production*, and testnet is the default dev chain otherwise.

### New skills created
- None. Existing skills only; the canonical guidance lives in `nexa-wallet-lifecycle-and-chain`
  (the chain-connection owner) with pointers from INDEX, project-setup, ktor, and rpc-node-client.

### Notes for the next agent
- **Single source of truth.** The full chain-choice rule now lives in one place —
  `nexa-wallet-lifecycle-and-chain` § "Which chain do I develop on?". Everything else points to it.
  If you expand or correct the guidance, edit it there and keep the pointers thin, rather than
  re-explaining it in each skill.
- **The load-bearing, easily-missed fact** is #2 of the three changes: switching `DEFAULT_CHAIN`
  without also switching the **local node's** chain silently fails (a testnet node can't serve
  regtest, and public seeders are chain-specific). That coupling is the part agents were most likely
  to get wrong, so it's stated in every touched spot.
- **Not changed (deliberately):** `NexaRpcFactory`'s `regtest`/`regtest`/`:18332` *factory defaults*
  and the `regtestHarness.kt` connection defaults stay regtest — those are the correct defaults for
  the RPC client's most common (regtest test) use, and the anti-pattern about shipping them is
  already present. Also left the `AppConfig.isMainnet ? NEXA : NEXATESTNET` ternary in the ktor
  `app.cfg` example as-is; it's illustrative and its boolean shape only selects mainnet-vs-testnet
  (a regtest dev would set `DEFAULT_CHAIN` directly) — worth generalizing to a 3-way chain enum only
  if a real app's config is verified to do so.
- **Possible follow-up:** the `app.cfg` example pairs `default_chain = NEXATESTNET` with
  `nexa_rpc_port = 18332` (the regtest RPC port). Left untouched this pass because per-chain RPC port
  numbers weren't verified against a live node; if you confirm the testnet RPC port, align that
  example (or add a comment that the port must match the node's chain).

## Pass 28 — 2026-07-14 — Claude (Fable 5)

**libnexakotlin re-grounding pass: swept the library's recent git history and full source for
API changes and undocumented surface.** The corpus's libnexakotlin-grounded passes date from
early June; this pass diffed the library's `commonMain` since then and read the files no prior
pass had opened (`token.kt`, `walletMgmt.kt`, `identity.kt`, `currency.kt`, `contracts/*`,
`fastforward.kt`). Finding on the history: the month of commits is dominated by stability work
with **no API surface** (a large Kotlin/Native data-race/deadlock series, socket fixes,
electrum node lists made atomic) — plus three genuine API-relevant changes, all now documented.
Finding on the source sweep: the corpus's existing libnexakotlin claims all held up against
current `main` (fee constants 1.01/1.1, the `confirmedHeight == Long.MIN_VALUE` sentinel,
`cleanReserved`/`cleanUnconfirmed`, the melt-skip line in the completer, `createTdppUrl`,
`GroupAuthorityFlags` values), so **everything this pass adds is additive; no corrections were
needed**. Several long-open CHANGELOG flags are closed below. Editorial standards held: no
version pins (API additions framed as "in a recent release"), no named apps, no on-disk paths.

### Added
- `nexa-capd-messaging/SKILL.md`: (1) the **conversation sub-channel / prefix-filter API** added
  to `CapdProtocolCommunication` in a recent release — the `name`/`prefixSize` constructor
  params, `send(ba, msgPfx)`, and the leading-bytes-filtered `receive(filter)` — including the
  plaintext-on-the-bus caveat and the both-sides-must-agree-on-prefixSize wire fact. (2) A new
  **Pattern 5 — the built-in multisig wallet contract**: `org.nexa.libnexakotlin.contracts`
  (`initializeMultisigContractLibrary`, `contractTypes[...].create`, `MultisigWalletContract`,
  `makeFormationInvitation` → `https://w.nexa.org/<chain>/invite/multisig/...?id=<convoSecret>`
  universal-link invitations, `handleContractFormationInvitation`, FORMATION→ACTIVE,
  `destination`/`balance()`/`send`, `formMultisig`), whose formation runs over a
  `CapdProtocolCommunication` seeded by the invitation's random 16-byte convoSecret. This closes
  the "end-to-end CAPD-coordinated multisig" gap flagged open since Passes 16/17. Trigger
  keywords extended for both.
- `nexa-wallet-lifecycle-and-chain/SKILL.md`: (1) new **Pattern 7b — sign and verify
  messages/data with the wallet** — the `signMessage` (Bitcoin-style; nexid/TDD; *not* usable in
  contracts) vs `signData`/`signHash` (SHA256+Schnorr; exactly what `checkDataSigVerify`
  consumes) split, `verifySigForData`/`verifySigForHash`, the instance + companion
  `verifyMessage` forms, and the `libnexa.verifySignedHashSchnorr`/`verifySignedDataSchnorr`
  raw-pubkey path — closing the "message signing as a focused topic" gap flagged by Passes 16/17.
  (2) New **Pattern 7c — pause/resume wallet processing** (`CommonWallet.pause(maxWait)`/
  `resume()`), noting the recent fast-abort change that makes `pause()` interrupt in-flight
  network syncs promptly (mobile app-background use). (3) `rediscover(...)` (the dev/debug full
  rescan) and `wallet.getTx(txIdem)` added beside the boot purges. (4) A "where the default
  public seeders live" pointer (`nexaElectrum`/`nexaTestnetElectrum` in `init.kt`, now atomic
  lists). Frontmatter description + trigger list extended.
- `nexa-transaction-construction/SKILL.md`: (1) `iTransaction.mergeUnlockingScripts(other)` in
  Pattern 6's half-tx idiom — verified semantics (same-idem guard; fills only empty input
  scripts; skips `OP_RETURN` comms-slot inputs; well-defined because idem excludes unlocking
  scripts), resolving the Pass-16 "saw it, didn't trace it" note. (2) The full `send` overload
  family in the simpleapi section (`deductFeeFromAmount`, `sync`, `note`, multi-recipient list,
  token `send(qty, addr, gid)`, `sendNative`), resolving Pass 16's open note (c).
  (3) `wallet.getTx(txIdem)` in Pattern 4.
- `nexa-transaction-construction/txCompletionReference.md`: the `appendableSighash(extendInputs,
  extendOutputs)` / `firstnSighash(numInputs, numOutputs)` builders on `iTransaction` — the
  ready-made producers of the partial-tx sighash coverages the file previously described only
  abstractly.
- `nexa-transaction-construction/simpleapi-cheatsheet.md`: the `currency.kt` display-conversion
  helpers (`SatToNexa`/`SatToString`/`NexaToSat`, `NexaDecimal`/`CurrencyDecimal` math modes,
  `SATperNEX`) and a compact `send`-overload table.
- `nexa-tokens-and-groups/SKILL.md`: (1) Pattern 7 gains **"Resolving metadata for display —
  and the subgroup rule"**: libnexakotlin's `getTokenInfo(grpId, getEc, cnxnMgr): TokenDesc`
  (P2P `supportsTokenInfo()` node first, electrum fallback, TDD fetch + `signedBy` check), the
  `{ bc.net.getElectrum() }` supplier guidance, the **subgroup-has-no-genesis →
  `parentGroup()`** rule, and the hash-verify-the-TDD mitigation (with a one-line pointer to
  libnexaapp's `AssetManager.getTokenDesc`, which wraps all of it). (2) Pattern 9 gains
  `Wallet.chunkTokenInto(gid, payAddress, numUtxos, tokenAmt)` — the library's idempotent
  token-UTXO pool splitter (`walletMgmt.kt`). Trigger keywords extended.
- `nexa-tokens-and-groups/tokenMetadataReference.md`: a new "libnexakotlin types and helpers"
  section — `TokenDesc` (incl. `signedSlice`/`tddHash`/`pubkey`), `decodeTokenDescDoc`,
  `TokenDesc.signedBy`/`makeTokenDescriptionDoc`, `GroupDescriptor.buildGenesisData()` (the
  `88888888` OP_RETURN builder), and `getTokenInfo` — so the wire-format doc now names the code
  that reads/writes the format.
- `nexa-electrum-monitoring/SKILL.md`: Pattern 5 subgroup caveat — `getTokenGenesisInfo` on a
  subgroup id resolves nothing (no genesis of its own); hop to `parentGroup()` or use
  `getTokenInfo`, and reuse the SPV connection's own electrum channel rather than a second
  standalone client.
- `nexa-npl-smart-contracts/SKILL.md`: the producing side of the oracle primitive — the sig
  `checkDataSigVerify` consumes is `wallet.signData(...)` output, and `signMessage` output is
  explicitly *not* contract-usable (per the library's own doc-comment).
- `nexa-wallet-connection/SKILL.md` + `walletUriFormats.md`: the previously-unexplained `hdl=m`
  login-URI parameter — nexid **identity-info field requests** (`nexidParams` vocabulary:
  attest/ava/billing/dob/email/hdl/realname/ph/postal/sm; requirement levels `m`/`r`/`o`/`x`;
  wallet-side per-domain permission checks; `loginWalletUri`'s `requiredInfo`/`optionalInfo`).
- `nexa-debugging-onchain-errors/SKILL.md`: a symptom row for "token/NFT metadata comes back
  empty" → subgroup id, hop to the parent group.
- `INDEX.md`: extended the wallet-lifecycle, CAPD, and tokens rows to surface the new content.

### Corrected
- None. Every libnexakotlin claim cross-checked this pass matched current source; all edits are
  additive (no `<!-- PRIOR -->` blocks introduced).

### Flagged for review
- None placed in skill bodies. Library findings recorded under Notes instead.

### New skills created
- None. All new material slots into existing skills (multisig-over-CAPD is CAPD's Pattern 5;
  signing/pause are wallet-lifecycle patterns; metadata resolution is the tokens skill's
  Pattern 7), per the corpus's anti-fragmentation stance.

### Notes for the next agent
- **Recent libnexakotlin history is mostly hardening.** June's commits are a long
  Kotlin/Native data-race/heap-corruption series, deadlock fixes (`getNewDestination`),
  socket/stability fixes, and CI tweaks — none of it API-facing. The three API-relevant changes
  (CAPD `prefixSize`/`msgPfx`/`receive(filter)`; the wallet fast-abort behind `pause()`;
  `allIdentityInfo()`/`clearIdentityInfo()` on `Wallet`) are documented this pass. The electrum
  seeder lists (`nexaElectrum`/`nexaTestnetElectrum`) changed type from `MutableList<IpPort>` to
  `AtomicRef<List<IpPort>>` — if any future doc tells developers to mutate them, use
  `.update { }` semantics, not `add`/`remove`.
- **Two genuine library issues noticed while grounding, kept OUT of skill bodies** (issue-tracker
  material per editorial standard): (1) `walletMgmt.kt`'s `chunkInto` hardcodes
  `val chain = ChainSelector.NEXA` internally, so it looks wrong for non-mainnet wallets
  (`chunkTokenInto` correctly uses `w.chainSelector`; I documented only `chunkTokenInto`).
  (2) `regtestHarness.kt` in the corpus's rpc skill has an awkward `broadcast` catch branch
  (`throw e.takeIf { false } ?: e`) — corpus-side, cosmetic, left alone.
- **The maintainer's direct commit (pre-this-pass) left consolidation debt.** The recent
  "gotchas from dev testing" additions in `nexa-transaction-construction`,
  `nexa-wallet-lifecycle-and-chain`, `nexa-rpc-node-client`, `nexa-tokens-and-groups`, and
  `nexa-debugging-onchain-errors` reference gap-report labels **G7/G8/G11/G12** that resolve to
  nothing inside the corpus, and the tokens skill's melt-remint section carries a
  "Verified against `libnexakotlin-jvm 0.5.67` wallet.kt" provenance note (standard-1/3
  artifact). The substance is good (I re-verified the melt-skip completer line against current
  main); a consolidation pass should strip the G-labels and the version-pin provenance.
- **`SKILLUPDATES.md` at the repo root is a maintainer-supplied gap report; only its
  libnexakotlin-side slice was actioned here.** Gap A (subgroup→parent metadata + the electrum
  supplier) is now covered from the libnexakotlin side, with a one-line `AssetManager` pointer.
  Gaps B and C remain open for a **libnexaapp-grounded pass**: (B) NFT media/artwork retrieval
  (`AssetManager.getNftFile`/`track`+`load`, the `cardf.*`/`info.json` zip layout, the
  large-media-flushed-to-disk rule and `loadCardFile`) belongs in `nexa-tokens-and-groups` +
  `nexa-ktor-server-integration`; (C) `ByteArray.decodeToImageBitmap()` for runtime raster bytes
  (and `makeImageBitmap` being JVM-only) belongs in `nexa-compose-ui-design`. Ground them in
  libnexaapp source before writing.
- **Verified-but-unwritten libnexakotlin surface left for a future pass:** (a) the
  `WalletContract`/`WalletContractType` framework beyond the multisig quick-start —
  `interestingTx`/`markInterestingSpendable` UTXO claiming by `contractId`, `filterInputs`, the
  interactive `SpendingProposal` approval round, and the `TextUI` accessor — enough for a
  "write your own multi-party wallet contract" pattern if apps need one; (b) `fastforward.kt`
  (the account-search machinery under `recoverWallet` — `WALLET_RECOVERY_*` tunables,
  `AccountSearchResults`) — internals, but the derivation-path search-depth tunables could
  matter to a wallet-recovery UX; (c) `Wallet.statistics()` (`WalletStatistics`) and the
  `historicalPrice` fiat hook; (d) the `IdentityDomain` per-domain permission store and
  `upsertIdentityInfo`/`allIdentityInfo` (wallet-side nexid data — mostly Wally's concern, but a
  WEW-style server wallet could use it).
- **What I did NOT touch:** no version/framing changes (Pass 3/9/15/20 standards held); the
  compose, flows, ktor, identity, locktime, project-setup, rpc, and script-machine skills were
  re-read but not reopened (no libnexakotlin drift touches them). The two `examples/` stub dirs
  remain the standing backlog.

## Pass 29 — 2026-07-14 — Claude (Fable 5)

**NPL library re-grounding pass: swept the `org.nexa:npl` repository's git history and source
for changes since the corpus's NPL-grounded passes (19/21, early June) and for
maintainer-authored guidance no prior pass had captured.** The history since the 0.1.1 publish
contains three substantive drops: (a) mid-June — an informative library README including a
"Handling missing state transitions" section, plus quieted compile logging; (b) late June — the
state-transition framework's position labels widened Byte→Int (deep stacks >255 items now
compile) and a new `StructuredDecompositionTransform` generator (supersedes
`UniversalStackTransform` for bottom-segment→top rearrangements around an untouched middle);
(c) early July — two new OP_PARSE primitives (`getPrevoutVisibleArg` /
`getPrevoutVisibleArgAsInt`), a compile-time stack-diagnostics hook (`StackXformDiag` in
`nslc.kt`) with a contract-agnostic diagnostics harness in the published test sources
(`ScriptDiagnostics.kt`), and a third-generation many-input covenant contract exercising an
enforcer/follower design. Also mined for the first time: the test-tree
`smartContractsREADME.md` (present since before Pass 19 but never captured), which supplies the
dependency-based execution model, the `templateArgs`/universality axis, and
design-for-compilability guidance. Every claim added was verified against the current source
(signatures/behavior read from declarations; `sortedRules()` re-confirms the
alphabetical-rule-index claim; the error strings, `SD(ByteArray, ByteArray)` compatibility,
init tiers, `initNpl()` composition, and satisfier layout all still match). Editorial standards
held: no version pins ("in a recent release" framing), no named apps, no on-disk paths.

### Added
- `nexa-npl-smart-contracts/SKILL.md`: (1) Mental model — **NSL is dependency-based**: bindings
  are read-only, the compiler orders execution by data dependencies (and lays each rule out
  twice, as-written vs dataflow-weight-sorted, keeping the shorter), so cache multi-use
  extractions into `val`s. (2) Mental model — the **fourth arg slot, `templateArgs`**, and the
  **universality tradeoff** (per-instance data in holder args keeps one constant template hash
  across instances; `NC*` constants bake in). The corpus had shown `templateArgs = null` in
  every example without ever explaining the slot. (3) Pattern 9 — the new **prevout visible-arg
  accessors** and the cross-input commitment-check idiom they enable. (4) A new **Pattern 11 —
  the enforcer/follower split**: one dust-bond input carrying full whole-output-set validation,
  lightweight followers proving the enforcer's presence via `getPrevoutContractId` +
  `getPrevoutVisibleArg`; why per-input full validation blows past the relay size limit; the
  input-order and every-rule-must-fully-enforce security rules; noted it resolves the existing
  "absolute-output covenants cannot be trustlessly batched" caveat. (5) A security bullet: if
  other contracts pin your template hash, every rule you add is part of *their* security.
  (6) Frontmatter description + trigger keywords/tasks extended for all of the above.
- `nexa-npl-smart-contracts/stateTransitions.md`: (1) a **"Designing rules that avoid the
  error"** section (cache struct/introspection reads; rule independence + oracle-free
  timeout rules; declare only used args; prefer intermediate variables; redesign before deep
  hand-derived permutations) — distilled from the library's own smart-contract development
  guide. (2) A **"Deep stacks, generator roster, compile diagnostics"** section: Int position
  labels (>255-item stacks; `SD(ByteArray,…)` reads bytes as unsigned labels 0–255, use the
  `IntArray` form beyond; the `stackScripts.bin` cache stays byte-per-label),
  `StructuredDecompositionTransform`, and the `StackXformDiag` /
  `withCompileStackDiagnostics` / static per-rule size-and-ROLL/PICK analyzers.
- `nexa-npl-smart-contracts/dslReference.md`: §1 interface-abstraction semantics (project-level
  `face` declares rule signatures with optional default bodies; `import` + `implement`;
  `NplException` when an unimplemented rule has no default); §7/§9 the prevout visible-arg
  accessors; §10 `DynamicStackTransformRegistry.register(...)` as public app-side API, the
  grown generator roster, generated-transition caching back into `stackX`, and the Int-label
  note; plus a `StackXformDiag` line.
- `nexa-script-machine-testing/SKILL.md`: a new **Pattern 10 — validate every input of a
  multi-input spend via the two-phase init** (`ScriptMachine()` + seat
  template/constraint/satisfier + `inputIdx`/`tx`/`coins` + `initialize(true)`), why full-tx
  `coins` makes cross-input introspection resolve, the caveat that this path skips the
  constructor's group-conservation check, and the runtime stack-high-water measurement loop;
  pointer to NPL's published test-source harness. Trigger keywords extended.
- `nexa-tokens-and-groups/SKILL.md` + `nexa-script-machine-testing/SKILL.md`: the existing
  "cannot isolate a `verifySameGroup` bypass in a replay test" claims sharpened to "…with the
  tx-based constructors", with the two-phase init named as the isolation escape hatch (the
  prior claim stays true for the constructors it described; no PRIOR needed).
- `nexa-debugging-onchain-errors/SKILL.md`: a new symptom row — many-covenant-input tx too
  large to relay → enforcer/follower; the `Cannot find state transition` row's fix updated to
  the project-side registration.
- `INDEX.md`: npl row extended (dependency model, templateArgs/universality, cross-input reads,
  enforcer/follower, project-side transition registration, compile diagnostics); script-machine
  row gains the multi-input two-phase init.

### Corrected
- `nexa-npl-smart-contracts/SKILL.md` ("When compilation fails") and `stateTransitions.md`
  ("Resolving it"): the claim that fixing `Cannot find state transition` requires (or "is
  sometimes") **editing NPL's own source**. The library README's "Handling missing state
  transitions" section states you do not need to: `stackX` is a public top-level `val` and
  `DynamicStackTransformRegistry.register(generator)` is public — register the transition or
  transformer **from your own project** after the init scaffold, before `compile()`; editing
  `addSpecificTransitions`/`addWarriorContractTransitions` is only the upstreaming path. Both
  spots corrected with `<!-- PRIOR -->` + `> **Revision note:**` (fold in the next
  consolidation pass). Confidence: high — README + `register` in `dynstackx.kt` + public
  `stackX` in `nslxlat.kt`, all current source. The debugging-skill symptom row and the
  stateTransitions "operational gotchas" bullet were aligned (additive edits, no PRIOR).

### Flagged for review
- None placed in skill bodies.

### New skills created
- None. Everything slots into the existing NPL/script-machine/tokens/debugging skills per the
  anti-fragmentation stance.

### Notes for the next agent
- **Library observations kept out of skill bodies** (issue-tracker material): (1) the NPL
  README's transformer example gates `stackX.check` behind a `ValidateTransitions` flag that
  does not exist in source (the real flag is `DoubleCheckTransitions`, a top-level `val = true`
  in `nslxlat.kt`) — the skills use the real name; worth a library README fix. (2) The README's
  dependency snippet still shows a pinned `org.nexa:npl:0.1.0` while the repo is past 0.1.1 —
  the corpus's placeholder policy already avoids this. (3) Pass 19's noted compile-log noise
  (`println("Successfully generated dynamic stack transform…")`) is now gated behind `debug` —
  resolved upstream, no skill change needed.
- **Verified-but-unwritten NPL surface left for a future pass:** (a) the five-contract
  betting-design taxonomy in the library's test-tree `smartContractsREADME.md` — the
  identity-validation vs output-validation *trust-model* contrast and the **facilitator**
  (third-party-claims-for-a-fee) pattern are real design content; Pass 29 took the
  universality axis and the compilability guidance but left the facilitator pattern and the
  NexaArgs-hash vs raw-pubkey-hash constraint-mock nuance unwritten. (b) `NexaExtend(npl) { }`
  (extend an existing project) is listed in dslReference §1 but has no usage guidance.
  (c) `nvmEval`/`nvmRun`/`EvalConfig` (off-VM rule evaluation) — still open since Pass 19.
  (d) `StackXformDiag`'s per-rule trace probes (`traceNsl`/`probeEarly`/`probeLate`) are
  documented only at the "they exist" level. (e) The warrior-v3 test file also demonstrates
  seat/range-checking via `thisIndex()` bounds and group-pinning output-i-to-input-i — folded
  only summarily into Pattern 11's "input order is part of the protocol".
- **Consolidation debt:** this pass added two `<!-- PRIOR -->`/revision-note blocks (npl SKILL
  + stateTransitions.md) — fold them next consolidation pass. The Pass-28-noted maintainer
  debt (G7/G8/G11/G12 labels, the 0.5.67 provenance note in the tokens melt section) remains
  open.
- **What I did NOT touch:** the compose, flows, ktor, wallet-connection, wallet-lifecycle,
  identity, locktime, project-setup, rpc, electrum, and capd skills (a fan-out read confirmed
  they carry no NPL-API claims beyond cross-references, all still valid). The two `examples/`
  stub dirs remain the standing backlog — note that NPL's own test tree now has three
  progressively harder worked contract families (secret sale → betting/delegation →
  enforcer/follower batch escrow) that make those examples increasingly writable.

## Pass 30 — 2026-07-15 — Claude (Fable 5)

**nexarpc re-grounding pass: swept the `org.nexa:nexarpc` repository's git history and full
source (`NexaRpc.kt`, `JvmNexaRpc.kt`, the test suite, build config) for changes since the
corpus's nexarpc-grounded passes (12/21, early June) and for undocumented surface.** The history
since then contains exactly one substantive drop, and it is a direct response to Pass 12: the
`tokenBalance` copy-paste bug Pass 12 reported to the maintainer ("melt" instead of "balance")
was fixed upstream in early June, and the optional `address` parameter is now passed through.
Everything else in recent history is toolchain/dependency hardening with no API surface (Kotlin
compiler bumps, Ktor/serialization/dokka bumps, a build-script `getWalletInfo`-adjacent fix).
The full-source sweep settled one long-open corpus discrepancy and surfaced several genuinely
undocumented facts, all added below. A corpus-wide fan-out search collected every
nexarpc-touching claim in the other skills; all matched current source except one line in
`errorCodeReference.md` (corrected below). Editorial standards held: no version pins ("a recent
release" / "mid-2026 fix" framing for genuine API-evolution facts), no named apps, no on-disk
paths.

### Resolved (previously flagged by Pass 21)
- **The `getstat*` discrepancy is settled: the SKILL body was right.** Pass 21's
  `rpcMethodReference.md` claimed the `NexaRpc` interface exposes only pool/peer reads "rather
  than" the `getstat`/`getstatlist` series and told readers to distrust the typed `getstat*`
  helpers. Current `NexaRpc.kt` declares the full series — `getstat`, `getstatlist`, and all four
  typed helpers (`getstatInt`/`getstatIntRange`/`getstatDouble`/`getstatDoubleRange`), with the
  optional `statistic`/`series`/`count`/`verbose` parameters — and the library's own test suite
  exercises them (including the `-v` verbose form and the code `-1` missing-name error the SKILL
  documents). The series was added upstream well before Pass 21, so that pass most likely read a
  stale checkout. The reference's caveat blockquote is replaced (prior text preserved in a
  `<!-- PRIOR -->` comment + revision note) by a full "Node statistics" section with the
  verified signatures.

### Corrected
- `nexa-rpc-node-client/regtestHarness.kt`: the `broadcast` helper's catch branch documented
  "folding already-known into success" but unconditionally rethrew
  (`throw e.takeIf { false } ?: e`) — the code contradicted its own stated contract (noticed by
  Pass 28, left alone then as out-of-scope). Now returns `HashId?` with `null` on
  already-known (the node returns no txid in that reply), usage comment updated; prior branch
  preserved in a comment with a revision note. Confidence: high — the contradiction is
  mechanical.
- `nexa-debugging-onchain-errors/errorCodeReference.md`: the claim that "connection, auth, and
  node-side errors all surface" as `NexaRpcException`. Verified against `JvmNexaRpc._calls`:
  only RPC-level failures are wrapped; transport failures propagate raw (e.g.
  `java.net.ConnectException` on JVM — which `nexa-transaction-construction` already documented
  correctly), as does a kotlinx `SerializationException` on reply-shape drift. `<!-- PRIOR -->`
  + revision note. Confidence: high.

### Added
- `nexa-rpc-node-client/SKILL.md`: (1) the **public suspend `_`-prefixed coroutine variants** —
  every blocking `NexaRpc` method is `runBlocking { }` around a public `suspend` twin on
  `JvmNexaRpc` (`_getblockcount()`, `_sendrawtransaction(...)`, `_calls(...)`, …); added to the
  mental model and as an alternative pattern under the blocking anti-pattern (hold the client as
  `JvmNexaRpc` in coroutine-native code instead of `withContext(Dispatchers.IO)`). (2) The two
  failure shapes that **bypass `NexaRpcException`** (raw transport exceptions;
  `SerializationException` on reply-shape drift — new node fields are safe because the client
  sets `ignoreUnknownKeys`). (3) A new anti-pattern: **`HashId` as a `HashMap`/`HashSet` key** —
  `HashId` overrides `equals` (content) but not `hashCode`, so hashed containers miss; key by
  `toHex()`. (4) Pattern 8 gains `tokenBalance(groupId, address?)` usage plus the API-evolution
  note about the pre-fix melt-verb bug. (5) Pattern 1 gains a **per-chain RPC port** note
  (18332 = regtest convention/factory default; the library's own tests reach a testnet node on
  7229; always read `rpcport` from the node's `nexa.conf`). (6) Pattern 2 gains `BlockInfo`'s
  chain-linking fields (`previousblockhash` — a recent addition, `null` at genesis —
  `ancestorhash`, `nextblockhash`). (7) Pattern 4 gains the `Txout.value`-is-a-lossy-`Double`
  caveat (no `satoshi` twin, unlike `Unspent`) and the `scriptPubKey.argsHash` tie-in to
  `nexa-identity-and-addresses`. (8) Setup gains the **multiplatform** fact (JVM/Android/
  iOS/macOS/Linux/Windows; `JvmNexaRpc` is a historical name, the implementation is common code)
  and the **`toHex()`/`fromHex()` extension-ambiguity** trap when wildcard-importing both
  `org.nexa.nexarpc.*` and libnexakotlin. (9) Trigger keywords extended (`tokenBalance`, the
  suspend variants, RPC ports).
- `nexa-rpc-node-client/rpcMethodReference.md`: the "Node statistics" section (above); the
  suspend-variant and multiplatform notes in the factory section; `BlockInfo`'s
  linking/`status`/`onMainChain` fields; the `gettransactiondetails` = `getrawtransaction <hash>
  true` wire fact (why it works for any known tx); the full `Txout`/`ScriptPubKey` shape with
  the lossy-`Double` caveat; the `tokenBalance` return shape + API-evolution note; the
  `NexaRpcException`-bypass failure shapes; the `HashId` no-`hashCode` warning.
- `nexa-ktor-server-integration/SKILL.md`: a comment on the `app.cfg` `nexa_rpc_port` example
  line — the RPC port is per-chain (18332 regtest convention, testnet conventionally 7229, read
  `rpcport` from `nexa.conf`, match `default_chain`). Partially closes Pass 27's open follow-up
  about the testnet-chain/regtest-port pairing in that example.
- `INDEX.md`: rpc row extended (suspend `_` variants, `tokenBalance`).

### Flagged for review
- None placed in skill bodies.

### New skills created
- None. Everything slots into `nexa-rpc-node-client` + the two cross-skill touch-ups, per the
  anti-fragmentation stance.

### Notes for the next agent
- **Verified accurate against current nexarpc source (no edit needed — don't re-check):** the
  `NexaRpcFactory.create` defaults (`http://127.0.0.1:18332/`, `regtest`/`regtest`); the
  fresh-`HttpClient`-per-call / `Connection: close` model; the 401
  `"Unauthorized (bad rpc username/password)"` message; `HashId` construct/display reversal and
  content `equals`; the `Unspent.satoshi`-vs-`amount` warning (the library's own comment);
  `gettransaction` = wallet-only vs `gettransactiondetails` = any known tx; `getutxo` wrapping
  `gettxout`; the `sendrawtransaction`/`enqueuerawtransaction` split; `generate`
  regtest/testnet-only; the whole token/signmessage/signdata/capd method set; `getstat` series
  vocabulary, `-v` prepending, code `-1` on missing name, and the typed helpers' skip-what-
  doesn't-fit behavior. The corpus-wide fan-out found every nexarpc claim in the other skills
  accurate (tx-construction's `ConnectException` note, locktime's `mediantime`, tokens'
  `tokenNew` mapping, debugging's 401 decoder).
- **Library observations kept out of skill bodies** (issue-tracker material): (1) the interface
  KDoc on `sendrawtransaction`/`enqueuerawtransaction` says "@return nothing" but both return
  the `HashId` (the skills document the real behavior); (2) `_calls` does
  `println(e.toString())` before rethrowing any exception — stdout noise a server can't
  suppress via the library; (3) the README/`doc/Module.md` still show the pre-migration
  `Nexa.NexaRpc` package and `Nexa:NexaRpc` coordinates (Pass 12 noted this; still true —
  the skills already say to trust the `org.nexa.*` forms); (4) `HashId` lacking a `hashCode`
  override (documented as a corpus anti-pattern, but the right fix is upstream).
- **Testnet RPC port evidence, for whoever finalizes Pass 27's follow-up:** the claim "testnet
  RPC = 7229" rests on the nexarpc test suite (its testnet test connects to `:7229` with
  `testnet`/`testnet` credentials — though its println message contradicts itself by saying
  "regtest"). I framed all corpus mentions as convention + "read `rpcport` from `nexa.conf`"
  rather than asserting per-chain defaults for mainnet, which remain unverified here.
- **Consolidation debt:** this pass added two `<!-- PRIOR -->`/revision-note blocks
  (`rpcMethodReference.md`, `errorCodeReference.md`) and one PRIOR comment in
  `regtestHarness.kt` — fold them next consolidation pass. I stripped the dangling "(G7 vs
  G11)" gap-report label from the rpc SKILL's code-73 table row (it resolved to nothing in the
  corpus); the remaining G-labels Pass 28 catalogued in `nexa-transaction-construction`,
  `nexa-wallet-lifecycle-and-chain`, `nexa-tokens-and-groups`, and `nexa-debugging-onchain-errors`
  (plus the 0.5.67 provenance note in the tokens melt section) are still open for the
  consolidation pass.
- **What I did NOT touch:** all other skills — the fan-out search confirmed their nexarpc
  claims are accurate, so nothing needed reopening beyond the two cross-skill edits above.
  SKILLUPDATES.md Gaps B and C (NFT media retrieval; `decodeToImageBitmap`) remain open for a
  libnexaapp-grounded pass, per Pass 28's notes. The two `examples/` stub dirs remain the
  standing backlog.

## Pass 31 — 2026-07-15 — Claude (Fable 5)

**scriptmachine re-grounding pass: swept the NexaScriptmachineKotlin repository's git history and
full source (`scriptmachine.kt`, `init.kt`, `nativebinding.kt`, `Test.kt`, README/Module docs,
build config) for changes since the corpus's scriptmachine-grounded passes (13/14, early June) and
for undocumented surface.** Chronology finding: the library's most recent commit predates the
Pass-13/14 grounding, so no new API landed since — but two of Pass 13/14's claims did not survive
re-verification, and the most recent substantive commit (the early-2026 "fix issues with providing
2 tx + nice error messages" drop, plus its Feb follow-up dep bumps) carried developer-facing
diagnostics no prior pass had captured. Both corrections were verified beyond the scriptmachine
repo itself: the native-library question against libnexakotlin's `nativeLibLoader.kt` (`loadInternal`
extracts `nativeLibs/*` from the jar; dated well before the skill existed) AND a published
`libnexakotlin-jvm` jar's actual contents; the conservation-check question against the identical
native `createTemplateContext` call in both init paths AND NPL's published diagnostics harness
comment. A fan-out sweep read the rest of the corpus for scriptmachine-touching claims; all other
claims held (constructor forms, `next`/`step` semantics, error-string signals, success criteria,
stack grammar, registers/BMD, clone/copy/dump — every `Test.kt` assertion cross-checked). Editorial
standards held: no version pins ("a recent release" framing), no named apps, no on-disk paths.

### Corrected
- `nexa-script-machine-testing/SKILL.md` (Mental model + the `Initialize()` anti-pattern) — **the
  "you must supply an external `libnexa` on the JVM library path" claim was wrong, and the skill
  contradicted itself** (mental model said "the jar does NOT ship the native VM"; the anti-pattern
  said "the artifact bundles a copy"). Verified behavior: scriptmachine's no-arg `Initialize()` →
  libnexakotlin `initializeLibNexa("")` → `loadInternal()`, which **extracts the bundled
  `nativeLibs/libnexa.{so,dylib,dll}`** (+ arm/musl/x86 variants) **from the libnexakotlin-jvm jar**
  into `<working dir>/lib/`, `System.load`s it, and falls back to `libnexa_musl.so`; the
  library-path search only runs for a non-empty variant name, which `Initialize()` never passes.
  `UnsatisfiedLinkError` causes re-framed accordingly (platform not covered, unwritable working
  dir, self-supplied build without `--enable-javacashlib`). PRIOR + revision notes in both spots.
- Same correction propagated (each with PRIOR + revision note): `nexa-project-setup/SKILL.md`
  (the scriptmachine paragraph), `nexa-debugging-onchain-errors/SKILL.md` (the
  `UnsatisfiedLinkError` symptom row + the `Initialize()` code comment; PRIOR block placed after
  the table since HTML comments break GFM tables), `nexa-debugging-onchain-errors/runbookBrokenBuild.md`
  (step-5 bullet), `nexa-script-machine-testing/contractSpendTestHarness.kt` and
  `nexa-npl-smart-contracts/compileAndPrintTemplate.kt` (header comments, PRIOR kept inline).
- `nexa-script-machine-testing/SKILL.md` (Pattern 4 note + Pattern 10 caveat (a)) and
  `nexa-tokens-and-groups/SKILL.md` (the `verifySameGroup` defense-in-depth note) — **the Pass-29
  claim that the two-phase init "skips the constructor's group-conservation check" is wrong.**
  `initialize(true)` with a seated template calls the *identical* native `createTemplateContext`
  the tx-based constructors use; the real difference is the coins array — the constructors
  fabricate zero-value placeholder prevouts for every input except the one under test, so
  balanced multi-input grouped spends are *falsely* rejected as melts, while the two-phase init
  supplies the real prevout set. Consequences corrected: a clean Pattern-10 run *does* include the
  conservation check (against the coins you seat), and no `ScriptMachine` path can exercise a
  `verifySameGroup` bypass in isolation. NPL's own harness comment ("aren't *tripped* by the
  chain-level group-balance check") says the same. PRIOR + revision notes in all three spots.

### Added
- `nexa-script-machine-testing/SKILL.md`: (1) **Constructor-time diagnostics for malformed
  template spends** (from the recent "nice error messages" release) — a decode table for the
  missing/non-push **hidden-args** `ScriptException` (with its "you may have forgotten the hidden
  args script" hint), the "at least 1 push" / "must be a push instruction (of the template
  script)" errors, the `Prevout template hash / input template mismatch` tolerant-mode
  `scriptErr`, and the `Constraint instruction N is not a push!` pre-check — plus the **argsHash
  parse rule** behind them (constraint push consumed only when the prevout commits to an
  argsHash; input layouts for with/without-hidden-args templates). (2) A matching new
  anti-pattern ("Omitting (or bloating) the hidden-args push when the prevout commits to an
  argsHash"). (3) Two-tx constructor behaviors: first-dependency-only (+ the `Processing output N
  being spent by input M` stdout line; loop Pattern 10 for the rest), the `No spend`
  `ScriptMachineException` for unrelated txs and the override-script fallback (assumes second tx
  = context, first tx output 0 = prevout), and the optional trailing `chainSelector` (rules
  currently identical across Nexa chains). (4) Pattern 5 depth: `cont(relativePos)`, settable
  `pos`, `modify(offset, OP)`, `clearStatus()`. (5) Pattern 9 depth: `ScriptMachineDump` /
  `ScriptMachine` are kotlinx-`@Serializable` (Base64-wrapping custom serializers) so
  `Json.encodeToString(sm.dump())` is the persist-to-file idiom; plus **seeding synthetic
  stacks** — `loadStacks` (Int/Long/ByteArray/BigInteger; appends; bignums pushed sign-magnitude
  via `BIN2BIGNUM`), `replaceStacks` (overwrites stacks, preserves position), `getBinaryStack`
  typed read-back — the "unit-test a template fragment against an arbitrary stack" capability
  Pass 14 left unwritten. (6) Setup: `Initialize()` needs a writable working dir (extraction
  target) and recent releases build with a **Java 21 toolchain** (older JDKs fail with
  `UnsupportedClassVersionError`); `BigInteger` import added. (7) Stale-doc note extended: the
  package docs' example calls `goScript(false)` (now `next(false)`) and repeats the superseded
  "must be in your path" native-lib claim. (8) Trigger keywords: `loadStacks`/`replaceStacks`/
  `getBinaryStack` and the three new constructor error strings.
- `nexa-debugging-onchain-errors/SKILL.md`: three new symptom rows — `No spend / These
  transaction are not related by a spend`; the hidden-args `ScriptException`; `Prevout template
  hash / input template mismatch` (tolerant-mode continuation caveat included).
- `INDEX.md`: script-machine row extended (bundled native lib, constructor diagnostics,
  synthetic stacks).

### Flagged for review
- None placed in skill bodies.

### New skills created
- None. Everything slots into the existing script-machine/tokens/project-setup/debugging skills.

### Notes for the next agent
- **Verified accurate against current scriptmachine source + `Test.kt` (no edit needed — don't
  re-check):** the `"No error(0)"`/clean-main-stack success signals; `eval`'s Boolean-is-not-
  pass/fail; the `next()` Triple semantics and per-constructor call counts (two-tx and
  `advance=true` pre-run satisfier+constraint; triple-script/`advance=false` run nothing);
  `ALT_STACK_LOADED` between satisfier and template; `"all scripts completed"`; the
  `getStackItemText` grammar incl. `BYTES 0 false 0` vs `""` and BIGNUM sign rendering; the
  register API (`INTEGER(DEC)`/`BIGNUM(DEC)`/`BIGNUM`/`BYTES` item types); trailing-`80`/`00`
  sign-magnitude; breakpoint mechanics (illegal-opcode overwrite, step/cont restore, the
  step-at-breakpoint and cont-rerun-last-line quirks are still visible in the suite);
  `clone`/`copy`/`dump`/`fromDump`; `ScriptMachineResources` fields and persistence/reset
  semantics; `setLimits` → "Stack total length limit exceeded"; `POST_UPGRADE_MANDATORY_SCRIPT_
  VERIFY_FLAGS = 0x1f05476f` applied by every creation path; `analyze2Tx`; `parseTemplateSpend`
  (incl. the null-txo "probably a constraint script" guessing); `tolerant` default true; two-tx
  either-order tolerance (asserted by the suite's `twoTx` test).
- **Chronology note:** the scriptmachine repo's last commit (a dep bump) and the diagnostics drop
  both predate Passes 13/14, so the corrections above were misreadings at grounding time, not
  library drift. The lesson from Pass 30 repeats: when a skill and its library disagree, check
  *both* directions — Pass 13 read `initScripts`'s error paths but framed the native-load story
  from the README's stale claim, and Pass 29 read NPL's harness comment as "skips the check."
- **Library observations kept out of skill bodies** (issue-tracker material): (1) the two-tx
  constructor `println`s "Processing output N being spent by input M" unconditionally — stdout
  noise a test suite can't suppress via the library (documented as expected output, not flagged);
  (2) the repo README/Module docs still show `Nexa:NexaScriptMachine` coordinates,
  `ScriptMachine.Initialize()`, `goScript`, and the pre-bundling native-lib claim (skills updated;
  a library-docs refresh would help); (3) `loadInternal`'s platform-name logic only special-cases
  macOS x86 (`libnexa_x86.dylib`) — the jar bundles `libnexa_arm32.so`/`libnexa_arm64.so` but the
  loader never selects them, so ARM-Linux likely fails to load the bundled lib (the skill's
  "platform not covered" cause covers this without asserting the internals); (4) `initialize(false)`
  (no advance) routes through native `create` rather than `createTemplateContext` — whether the
  group-balance check also runs there was NOT determined (the corrected claims are all about the
  `initialize(true)`/ctor path, which is what the corpus documents).
- **Verified-but-unwritten scriptmachine surface left for a future pass:** (a) `probablyConstraintScript`
  (public helper behind the null-txo parse guessing); (b) the `p2pkt` top-level val (the actual
  well-known-template-1 script: `FROMALTSTACK CHECKSIGVERIFY`) — could anchor a "what P2PKT
  actually executes" note; (c) `ScriptMachineEnvironment` as a harness-building block beyond the
  one-liner it gets; (d) `setupCurrentScript()` (public, used by `copy`/`fromDump`) — internals.
- **What I did NOT touch:** all other skills — the fan-out sweep confirmed their scriptmachine
  claims (constructor forms, `next(false)`+`step()` loops, error-string signals) are accurate;
  wallet-lifecycle's "you also need scriptmachine's `Initialize()` to compile/run contracts"
  template comment is accurate as-is. SKILLUPDATES.md Gaps B and C (NFT media retrieval;
  `decodeToImageBitmap`) remain open for a libnexaapp-grounded pass, per Pass 28/30 notes. The
  consolidation debt now includes this pass's PRIOR/revision blocks (script-machine SKILL ×3
  spots, tokens SKILL, project-setup, debugging SKILL + runbook, two `.kt` headers) alongside the
  G-labels and 0.5.67 provenance note Passes 28/30 catalogued. The two `examples/` stub dirs
  remain the standing backlog.

## Pass 32 — 2026-07-15 — Claude (Fable 5)

**Consolidation pass (fifth one; Passes 3, 9, 15, and 20 were the prior four).** No new technical
content. This pass cleans up the audit trail accumulated since Pass 20 and re-affirms the
maintainer's three editorial standards (deprioritize version specifics; de-anchor from named
applications; remove on-disk vs off-disk distinctions) as the law for skill bodies going forward.
I read the corpus (INDEX, all 31 prior CHANGELOG passes including every "Notes for the next
agent," and the affected skill files in full) and swept every file for audit artifacts and
framing violations before editing. The debt was exactly what Passes 28–31 catalogued as they
deferred it: the `<!-- PRIOR -->`/`> **Revision note:**` blocks from the four re-grounding
passes (29 npl, 30 nexarpc, 31 scriptmachine, plus the Pass-22 `decimal_places` block Pass 24
had relocated), and the maintainer's direct-commit artifacts (dangling G-labels, one version-pin
provenance note). Every folded correction had held up across subsequent passes; no open
questions remained behind any wrapper.

### Corrected (audit-trail folded, corrected content kept)
- `nexa-npl-smart-contracts/SKILL.md` ("When compilation fails") and `stateTransitions.md`
  ("Resolving it"): folded the Pass-29 PRIOR/revision blocks on the register-from-your-own-project
  fix for `Cannot find state transition`. The corrected substance (both `stackX` and
  `DynamicStackTransformRegistry` are public API; registering at startup in your project is the
  intended path per the library README; editing NPL source is only the upstreaming route) stays as
  plain prose in both spots.
- `nexa-script-machine-testing/SKILL.md` (four spots): folded the Pass-31 native-library blocks —
  the Mental-model "Where the native VM actually comes from" section and the `Initialize()`
  anti-pattern keep the corrected bundled-extraction story (the VM ships in the
  `libnexakotlin-jvm` jar's `nativeLibs/`, auto-extracted to `<working dir>/lib/`; the JVM library
  path is only consulted for a non-default `initializeLibNexa(variant)`); the Pattern-4
  conservation blockquote and the Pattern-10 caveat keep the corrected two-phase-init facts, with
  the revision note's one extra consequence ("no `ScriptMachine` path runs a
  conservation-violating spend's scripts, so a `verifySameGroup` bypass cannot be exercised in
  isolation") folded into the parenthetical itself. The Pattern-10 caveat's dangling "see the
  revision note in Pattern 4" pointer was retargeted to the surviving constructor-time note.
- `nexa-tokens-and-groups/SKILL.md`: folded the matching Pass-31 two-phase-init PRIOR/revision
  block in the `verifySameGroup` defense-in-depth note (same substance as above, kept as prose).
- `nexa-project-setup/SKILL.md`: folded the Pass-31 bundled-native-lib PRIOR/revision block; the
  never-consults-the-library-path nuance from the revision note is now one clause in the prose.
- `nexa-debugging-onchain-errors/SKILL.md`: deleted the Pass-31 PRIOR/revision block after the
  symptom table (the corrected `UnsatisfiedLinkError` row already carries the real causes).
  `runbookBrokenBuild.md`: folded its PRIOR/revision pair; the "library path is not consulted"
  fact moved into the bullet. `errorCodeReference.md`: folded the Pass-30 PRIOR/revision pair by
  integrating the revision note's substantive content into the prose — transport failures
  (`java.net.ConnectException`) and reply-shape `SerializationException` bypass
  `NexaRpcException`; only auth/node-side RPC failures are wrapped.
- `nexa-rpc-node-client/rpcMethodReference.md`: replaced the Pass-30 PRIOR/revision pair (the
  settled `getstat*` question — the series IS in the interface; the SKILL body was right) with the
  one surviving sentence ("use `calls`/`callje` only for RPCs with no typed wrapper"); the "Node
  statistics" section below it already carries the verified signatures. `regtestHarness.kt`:
  removed the PRIOR comment block above `broadcast` (the corrected null-on-already-known contract
  is fully described in the function's own KDoc).
- `nexa-tokens-and-groups/tokenMetadataReference.md`: folded the Pass-22 `decimal_places`
  PRIOR/revision block (relocated here by Pass 24). The corrected picture (on-chain genesis
  OP_RETURN, not the TDD dictionary) is already stated in the intro and the "Where
  `decimal_places` actually lives" blockquote; the wrapper was pure bookkeeping.
- Two `.kt` header comments (`nexa-npl-smart-contracts/compileAndPrintTemplate.kt`,
  `nexa-script-machine-testing/contractSpendTestHarness.kt`): dropped the inline `PRIOR:` tails;
  the corrected bundled-native-VM requirement line stays.

### Reframed (maintainer-commit debt from the dev-testing gotchas, per Pass 28/30 notes)
- Stripped the dangling gap-report labels: `(G7)`, `(G8)`, `(G11)`, `(G12)` from four
  `nexa-debugging-onchain-errors` decoder/table entries, and `G11/`, `the G7` from the two
  code-73 discussions in `nexa-transaction-construction` (the cross-references they decorated
  remain; the labels resolved to nothing inside the corpus).
- `nexa-tokens-and-groups` melt-remint section: removed the "Verified against
  `libnexakotlin-jvm 0.5.67` `wallet.kt`" provenance (standard-1/3 artifact); now reads "In
  libnexakotlin's completer (`wallet.kt`), …" — the library-source pointer standard 3 permits,
  without the version pin.
- `nexa-transaction-construction` RPC-broadcast note: dropped the "Confirmed on testnet:"
  provenance framing; the substantive claim (via RPC the same covenant spend is accepted with no
  code-73) stays as plain prose.

### Added
- None. Consolidation pass: no new patterns, anti-patterns, mental models, insights, or skills.

### Flagged for review
- None. Every question the folded wrappers documented is settled and stated as fact in the prose
  (register-from-own-project; bundled native lib; two-phase-init conservation semantics;
  `getstat*` present; transport-bypass exception shapes; `decimal_places` on-chain).

### Deleted
- All `<!-- PRIOR: ... -->` comments and `> **Revision note:**` blocks remaining in any skill
  body, reference doc, or `.kt` template (thirteen files touched). After this pass a repo-wide
  grep for `PRIOR` / `Revision note` / `Review needed` / `⚠` / `G7|G8|G11|G12` / `0.5.67` across
  everything except this CHANGELOG returns nothing.

### INDEX
- No change. The "Where to find canonical sources" section (created Pass 3, extended Passes
  17/22) still carries no on-disk paths, no named applications, and no concrete version pins,
  and already matches this brief's Step 3. Re-verified rather than churned.

### New skills created
- None. Per the consolidation brief.

### Notes for the next agent

**The three editorial standards are the law for skill bodies and INDEX. Do not re-introduce what
this and the prior consolidation passes (3, 9, 15, 20) removed.** Restated so they cannot be lost:

1. **Deprioritize version specifics.** Skill bodies and `nexa-project-setup`'s `[versions]` block
   use placeholders (`"<latest>"`, relationship comments) plus a pointer to the GitLab Maven
   registry — not pinned Nexa-library version numbers. Library coordinates (`group:artifact`) and
   repository URLs stay. A concrete version appears only where it marks a genuine API-surface
   change (the `millinow → epochMilliSeconds` rename, framed as "renamed in a release") or a
   genuine version-specific behavior (the kotlinx-serialization 1.10.0 CBOR caveat). "Verified
   against version X" provenance is not one of those — extract the claim, drop the pin. The
   third-party JUnit pins remain a deliberate, documented exception. Trust the published POM.

2. **De-anchor from named applications.** Skill bodies and INDEX describe Nexa *infrastructure*
   (libnexakotlin, libnexaapp, NPL, scriptmachine, nexarpc, mpthreads, the `org.wallywallet:wew`
   library, electrum clients, and the Wally wallet with its TDPP/nexid/Trickle Pay protocol) and
   *patterns* extracted from real apps — never the apps themselves by name, and never internal
   process labels (gap-report G-numbers, pass numbers, "confirmed in our testing"). The Wally
   wallet and Trickle Pay are protocol infrastructure and may be named; applications built on the
   stack may not. Named apps may appear only here in the CHANGELOG, for historical reasoning.

3. **Remove on-disk vs off-disk distinctions.** Skill bodies assume no checkout layout on the
   reader's machine. Name the library and its Maven coordinate / GitLab project; INDEX is the
   authoritative "where to look" map. Light pointers into *library* source files ("libnexakotlin's
   `wallet.kt`", "NPL's `dynstackx.kt`") are acceptable "where to find" guidance; machine paths
   are not. Universal developer paths (`~/.gradle/caches`, `~/.m2`) and wire-format concepts
   (`SerializationType.DISK`, the wallet's on-disk database) remain in bounds.

**How the debt accrued this cycle (and the healthy pattern to keep).** Passes 28–31 followed the
established convention well: each added at most a few clearly-marked PRIOR/revision blocks for
genuinely reversible corrections and left explicit CHANGELOG notes telling this pass exactly where
they were. That made this cleanup mechanical. The one new debt *class* this cycle came from a
direct maintainer commit (dev-testing gotchas with G-labels and a version-pin provenance note) —
excellent substance, internal framing; the substance is all retained. If future hand-edits land
with similar labels, fold the labels and keep the content, as here.

**Substance preserved.** No technical content, pattern, anti-pattern, mental model, or code
example was added or removed this pass — only audit framing. The "where did this go?" lookups:
the transport/serialization `NexaRpcException`-bypass facts are now plain prose in
`errorCodeReference.md` (and were already in the rpc SKILL body); the "no ScriptMachine path can
exercise a `verifySameGroup` bypass in isolation" consequence is inside the Pattern-4
parenthetical in `nexa-script-machine-testing` and the defense-in-depth note in
`nexa-tokens-and-groups`; the never-consults-the-library-path nuance is one clause in
`nexa-project-setup`'s scriptmachine paragraph and `runbookBrokenBuild.md`'s bullet.

**Untouched targets that remain.** The two `examples/` stub dirs
(`nexa-npl-smart-contracts/examples/`, `nexa-tokens-and-groups/examples/`) are still the standing
backlog (compile-and-verify gated, per Pass 21's deferral). `SKILLUPDATES.md` Gaps B and C (NFT
media/artwork retrieval via `AssetManager`; `decodeToImageBitmap` for runtime raster bytes)
remain open for a libnexaapp-grounded content pass, per Pass 28/30/31 notes. Neither was in scope
for a consolidation pass.

## Pass 33 — 2026-07-15 — Claude (Fable 5)

**libnexaapp re-grounding pass: swept the LibNexaApp repository's git history and full source
(all four modules — library/:app, composeLibrary/:compose, server/:server, plus the shared/
sharedBackend source dirs) for changes since the corpus's libnexaapp-grounded passes and for
undocumented surface — and closed the maintainer gap report's remaining Gaps B and C.**
History finding: the library's most recent commit is 2026-06-11 (dep bumps); the substantive
window since 2026-05-01 contains exactly three API-facing drops — (a) mid-May, the weighted-sash
support (`CCSash.add(obj, pos, weight: Float? = null)`); (b) late May, the session-abandonment
grace period + functional-flow last-value replay + the reusable UI components and `SvgImage`
expect/actual (the components the corpus already documented; the root-package import claim
re-verified — `commonUIComponents.kt` has no package declaration); (c) a `Double.format`
large-value overflow fix. Source finding: the `org.nexa.assets` subsystem (untouched since the
corpus's grounding window) matches the maintainer gap report exactly, and one corpus claim did
not survive verification — the tokens skill's assertion that the `/assets` response side is app
code (libnexaapp ships a complete built-in implementation). Every claim added this pass was
verified by reading the actual source (`handleAssets`, `checkAssetChallenge`, `extractNftData`,
`loadCardFile`, `serverCfg.kt`, `definedFlows.kt`, `wallywalletorgapi.kt`, `tdpp.kt`,
`composing.kt`, `imagetools.kt`, module build files), not from the survey summaries alone.
Editorial standards held: no version pins ("in a recent release" framing), no named apps, no
on-disk paths (the `nexaAssets/` `data`/`cache` layout is the library's own storage format).

### Corrected
- `nexa-tokens-and-groups/SKILL.md` Pattern 8 (the closing parenthetical): the claim that "the
  asset-list parsing types and the proof-verification helper are app/protocol code you
  implement." libnexaapp ships the wire types (`TricklePayAssetList`/`TricklePayAssetInfo`), the
  proof-verification helper (`checkAssetChallenge`), and a complete built-in `POST /assets`
  handler (`handleAssets`) installed by `installWalletRoutes`. Corrected with `<!-- PRIOR -->` +
  revision note; the prior text's still-true core (never trust a bare `outpointHash`) is
  preserved in the note. Confidence: high — read directly from `routeController.kt`/`tdpp.kt`.

### Added
- `nexa-tokens-and-groups/SKILL.md`: (1) a new **"The built-in server side of this flow"**
  subsection under Pattern 8 — `handleAssets` parses/filters (ungrouped + fenced skips),
  verifies each proof by relaying it to the **trusted P2P node** (a tx-validation message;
  input-validity + constraint-script + outpoint checks), records
  `session.assets[gid] = OwnedAssetInfo(...)` (with its `ai: AssetInfo` hook into the artwork
  pipeline), and notifies browser tabs via `WALLET_HAS_ASSET` (client:
  `flowConnector.walletOwnsAssetHandler`); requires the `initBlockchain` global. Two caveats
  documented: the built-in check does **not** compare the proof's OP_RETURN host/challenge
  commitments against the issued `chalby` (enforce challenge binding yourself if replay matters),
  and the client helper `getWalletAssets` is an unimplemented `TODO()` stub. (2) `ALL_ASSET_FILTER`
  (the ready-made match-any-grouped-output pattern) and the "null challenge = no proof requested"
  fact on `requestAssetsUri`. (3) A new **Pattern 8b — Displaying an NFT's artwork** (closes
  SKILLUPDATES **Gap B**): `assetManager.track`/`AssetInfo.load`/`getNftFile` (zip hash-verified
  against `gid.subgroupData()`), the zip layout (`cardf`/`cardb`/`public`/`owner`/`info.json` →
  `NexaNFTv2`), `loadState` progression, the **large-media-flushed-to-disk rule**
  (`MAX_UNCACHED_FILE_SIZE`, ~20 KB default; byte fields null; read `iconUri`/`publicMediaCache`
  via `assetManager.loadCardFile`), the absent ImageBitmap accessors, the `.td`/`.ai`/`.zip`
  storage layout, the serve-bytes route guidance (incl. that the registered `/api/asset/image`
  route does not respond with bytes), and the creation-side tooling (`makeNftyZip`/`checkNftyZip`/
  `generateCardFile`, `NFTCreationData`). (4) A matching anti-pattern (null byte fields for
  loaded NFTs) and a Setup bullet for the `:server` artifact. Frontmatter description + triggers
  extended.
- `nexa-ktor-server-integration/SKILL.md`: a new **"The libnexaapp server globals"** pattern —
  the four `serverCfg.kt` globals `initBlockchain` sets (`chainSelector`/`blockchain`/
  `assetDataDir`/`cacheDataDir`), the two subsystems that silently depend on it (built-in
  `/assets` verification via `blockchain!!.net.getNode()`; `assetManager` storage), the
  `{ blockchain!!.net.getElectrum() }` supplier guidance, and a `respondBytes` media route
  example (Gap B's serving half). Mental-model route list annotated: `/assets` is a complete
  built-in implementation; `/api/asset/image` is registered but doesn't return image bytes.
- `nexa-compose-ui-design/SKILL.md` (closes SKILLUPDATES **Gap C**): (1) Pattern 7 gains
  **"Runtime-fetched raster bytes"** — CMP's `ByteArray.decodeToImageBitmap()`
  (`org.jetbrains.compose.resources`, works on every target incl. wasmJs) as the way to render
  fetched PNG/JPG (NFT artwork), with the `runCatching` + SVG-fallback idiom; libnexaapp's
  `makeImageBitmap(bytes, w, h, scaleMode)` documented as **JVM-only** (plain `jvmMain` function,
  no expect/actual); clarified that `svgFromString` is app-level code, not a library export.
  (2) A matching anti-pattern ("decoding runtime bytes with `makeImageBitmap` / hand-rolled skiko
  decoder"). (3) Pattern 6 gains the concrete weighted-sash semantics (`add(obj, pos, weight)`;
  weight is initial-only — drag or persisted size reverts the pane to fixed Dp). (4) Pattern 5
  gains the client price feed behind `NexaInputField.exchangeRate` —
  `getNexaExchangeRate(fiat, force) { rate, loadTime -> }` (throttled wallywallet.org poll,
  USD/USDT only, null on failure). (5) The `Double.format` large-value fix noted as an
  API-evolution fact. Frontmatter + trigger keywords extended.
- `nexa-compose-ui-design/componentReference.md`: `makeImageBitmap` row (JVM-only + the
  `decodeToImageBitmap` alternative), the weighted-sash detail on the `CCSash` row, the
  `getNexaExchangeRate` note under the high-level components, the `Double.format` fix note.
- `nexa-server-state-and-flows/SKILL.md`: (1) functional flows — the recent-release
  **last-value replay on bind** (per-session + global caches; late-joining tabs receive the last
  `aset` value; consequence: don't treat pushes as strictly one-shot — dedupe by payload id if
  exactly-once matters; this is how `walletConnected` survives a page reload). (2) A new
  **"Beyond flows: notifications and app messages"** section — `sendNotification` /
  `NotificationDataType` (`WALLET_HAS_ASSET`, `APP_SPECIFIC`+), client `walletOwnsAssetHandler` /
  `unsolicitedAppSpecificDataHandler`, `setAppMessageHandler`/`sendAppMessage` (first byte ≥ 0x80;
  values below reserved), and the client `flowConnector.connected` socket-health flow (distinct
  from `walletConnected`). Triggers extended.
- `nexa-wallet-connection/SKILL.md`: (1) the **session-abandonment grace period** (recent
  release: ~5 s wait for a browser to reconnect before `handleAbandoned()` disconnects the
  wallet; a page refresh no longer drops the wallet connection). (2) The `/assets` callback
  paragraph now points at the built-in handler before the reader writes their own. (3) A note
  that `svgFromString` in the QR client snippet is an app-level helper, not a library export.
- `nexa-debugging-onchain-errors/SKILL.md`: two new symptom rows — NFT artwork serves 0 bytes
  though `loadState == COMPLETED` (the disk-flush rule), and built-in `/assets` verification /
  `AssetManager` failing because `initBlockchain` was never called.
- `INDEX.md`: tokens, compose, flows, and ktor rows extended for all of the above.

### Flagged for review
- None placed in skill bodies.

### New skills created
- None. Everything slots into the existing tokens/ktor/compose/flows/wallet-connection/debugging
  skills, per the anti-fragmentation stance.

### Notes for the next agent
- **Library observations kept out of skill bodies** (issue-tracker material): (1) the
  `GET /api/asset/image` handler reads `ai.iconBytes` into a local and never responds — the
  skills say "doesn't respond with image bytes" without asserting the internals; (2) client
  `getWalletAssets(filter)` is `TODO()` with a comment about WASM signature validation
  (documented as "don't call it" in the tokens skill); (3) `loginWalletUri`'s `proto` parameter
  handling looks buggy — a non-null `proto` yields `pr = "https"` regardless of the value passed
  (`else "https"` where `else proto` was presumably intended); (4) the composeLibrary declares
  `iosArm64()`/`iosSimulatorArm64()` targets but I could find **no iOS `actual` for the
  `SvgImage` expect** (actuals exist for jvm/android/wasmJs only) — if real, iOS compilation of
  the compose module is broken; verify before touching the compose skill's iOS-targets claim
  (I left that claim alone since the targets *are* declared); (5) `NexInFiat` in
  `wallywalletorgapi.kt` is inside a block comment — only `getNexaExchangeRate` is live API
  (the skills document only the live one); (6) the built-in `/assets` handler's lack of
  OP_RETURN host/challenge verification is documented in the tokens skill as a behavior fact +
  security caveat, but upstream adding that check would be the real fix.
- **Verified accurate against current libnexaapp source (no edit needed — don't re-check):**
  the root-package (no package declaration) location of `LightModeToggle`/`ConnectWalletButton`/
  `NexaInputField`; the `SvgImage(resource, assetName, modifier, tint)` expect signature and the
  Android-only real-SVG path (androidsvg); `installWalletRoutes(externalUrl, session_handler,
  walletRoutes)` and its route set incl. no `/tx`//`/_share`; `initBlockchain(chain: Blockchain,
  assetDir: File, cacheDir: File)`; `createQrSvg(qrData, maxSzInPix, oneCol, classes, forceSize,
  onclick)`; the `walletUriFormats.md` builder signatures (`connectWalletUri`/`loginWalletUri`/
  `requestAssetsUri`/`sendPaymentUri` incl. the inverted `sendPaymentUri` polarity);
  `registerLibNexaAppFlows()` registering `walletConnected` (GLOBAL/TOCLIENT); the duplicate-name
  throw messages; `SESSION_HEADER_COOKIE_NAME`; `AssetManager.getTokenDesc(chain, groupId, getEc,
  forceReload)` doing the parent-group hop + TDD hash-verify + `.td` caching (Pass 28's Gap-A
  coverage re-confirmed from the libnexaapp side).
- **Chronology note:** LibNexaApp's last commit predates this pass by a month; the corrections
  and additions here are grounding gaps, not library drift. The three API-facing drops in the
  May–June window (weighted sash; abandonment grace + flow replay; `Double.format` fix) are all
  now documented.
- **Consolidation debt:** this pass added one `<!-- PRIOR -->`/revision-note block
  (`nexa-tokens-and-groups` Pattern 8) — fold it next consolidation pass.
- **Verified-but-unwritten libnexaapp surface left for a future pass:** (a) the client-side
  HTTP helpers in `serverAccess.kt` (`aGetFromServer`/`asGetFromServer`/`postToServer` family,
  `setupServerConnection`/`customServerConnection`, `maxReadSize`/timeout globals) — the flows
  and wallet-connection skills use them in snippets but the surface itself is undocumented;
  (b) the `Prefize<T>` preference property delegate and the `prefs` expect/actual model beyond
  the one-liner it gets; (c) `nftTools.kt`'s deeper surface (`generateCardFile`'s FFMPEG/IMMAG
  external-tool hooks, `tokenDecimalMode`/`tokenAmountString`); (d) the `zip.kt` standalone ZIP
  reader (`EfficientFile`/`zipForeach`) as general-purpose API; (e) `NexaTdpp`/`NexaLogin`/
  `NexaRegister` etc. in `nexaWalletRequests.kt` — a client-of-wallet TDPP layer (partly
  `expect`-declared, mobile-oriented) that no skill covers; (f) `ElectrumClientFactory(blockchain)`
  in `assets.kt` (a reconnecting electrum-supplier factory — a nice alternative to the inline
  `{ bc.net.getElectrum() }` lambda). None are load-bearing for the current skills.
- **What I did NOT touch:** the wallet-lifecycle, identity, tx-construction, npl, script-machine,
  rpc, electrum, capd, locktime, and project-setup skills — a corpus-wide grep confirmed their
  libnexaapp claims (the `initBlockchain` thin-wrapper note, the `identity`-is-a-library-field /
  `userNexaAddress`-is-app-code split, the `millinow`→`epochMilliSeconds` compatibility story)
  all match current source. `SKILLUPDATES.md` Gaps A, B, and C are now all closed (A by Pass 28
  from the libnexakotlin side + this pass's ktor-side globals; B and C this pass) — the
  maintainer can retire that file. The two `examples/` stub dirs remain the standing backlog.

## Pass 34 — 2026-07-15 — Claude (Fable 5)

**Wally-wallet re-grounding pass: swept the WallyWallet repository's full source (the wallet-side
TDPP session engine, the nexid identity op handlers, the long-poll access handler, the URI/paste
dispatcher, the asset/challenge builder, the CAPD order-book module) and its recent git history
(~260 commits since March) for wallet-side protocol behavior the corpus documents only from the
server side.** This is the first pass grounded in the protocol *counterparty*: nearly every
wallet-connection claim in the corpus was written from libnexaapp/spec evidence, and reading the
wallet let me verify them end-to-end and capture the behaviors only the wallet side reveals. One
long-standing corpus claim was falsified (the `TDPP_FLAG_*` importability, corrected in four
files), one mechanism attribution was corrected (who maps push paths to callback paths), and the
rest is additive: the long-poll wire protocol, the rejection-callback convention, the
callback-response-body contract, the wallet's multi-account domain binding, per-op request
parameters, and several libnexakotlin wallet APIs the wallet exercises that no skill documented
(`abortTransaction`, observer handles, `fastForward`, `getCurrentDestination`). History-derived
context: the recent WallyWallet feature work (auto fast-sync, TDPP domain→account binding on
`op=reg`, `hide_asset_details` handling, TDPP tx-monitoring dedupe) matched and informed the
additions. Editorial standards held: no app names in skill bodies beyond the protocol-infra
names the standards permit (Wally, Trickle Pay), no version pins ("recent Wally releases"
framing for current-enforcement facts), no on-disk paths.

### Corrected
- `nexa-wallet-connection/SKILL.md` (two spots: the cheat-sheet flags note and the flags-bitfield
  section), `nexa-wallet-connection/walletUriFormats.md`, `nexa-transaction-construction/SKILL.md`
  (Pattern 2 note), `nexa-debugging-onchain-errors/SKILL.md` (symptom row): **the claim that the
  `TDPP_FLAG_*` names are "not a guaranteed importable symbol from any Nexa library — define your
  own" is wrong.** All six (`NOFUND`/`NOPOST`/`NOSHUFFLE`/`PARTIAL`/`FUND_GROUPS`/
  `HIDE_ASSET_DETAILS`) are documented top-level `const val`s in libnexakotlin's `utils.kt`
  (common code), added mid-2025 — long before the corpus's grounding passes — and the Wally
  wallet imports them from there. Each spot corrected with `<!-- PRIOR -->` + revision note (the
  debugging-table PRIOR placed after the table, per the comments-break-GFM-tables convention);
  the fallback advice (protocol-fixed literals still work) retained. Confidence: high — read
  directly from libnexakotlin source + its git history + a consumer.
- `nexa-wallet-connection/walletUriFormats.md` ("Push vs route path asymmetry"): the claim that
  "the universal-link/deep-link layer bridges" the `/share`→`/_share` path difference. Verified
  against the wallet: the deep-link layer only unwraps `http(s)://<any-host>/<scheme>/<rest>` →
  `<scheme>://<rest>` (any host works; `w.nexa.org` is convention, not mechanism); the wallet's
  own op handlers hardcode `/_lp` and `/_share` as callback targets, the nexid callback reuses
  the URI's own path, and the other ops keep their un-underscored paths. Same observable
  behavior, corrected mechanism; PRIOR + revision note. Confidence: high.

### Added
- `nexa-wallet-connection/SKILL.md`: (1) **The long-poll wire protocol** — a new pattern:
  `GET /_lp?cookie=<id>&i=<count>`; reply `A` (accepted, first poll) / `Q` (server disconnect —
  what `disconnectWallet()` pushes) / empty (re-poll) / any other body = the pushed URI handled
  as a scan/paste (tdpp, nexid, or bare BIP21); HTTP 400/404 is fatal → the wallet tells the
  user to re-scan; the wallet **persists long-polls and auto-reconnects for ~30 min across app
  restarts** (so servers see old-cookie `/_lp` reconnects); one poll per host:port. (2) **The
  `/tx` response-body contract** — the wallet parses your callback body: exact `unknown session`
  → session-gone UX; substring `invalid`/`error`/`rejected` (case-insensitive) → user-facing
  failure warning even if the tx broadcast; else advisory success (a completed tx's definitive
  success is the wallet observing it in the mempool, ~60 s timeout); respond promptly (few-second
  wallet timeouts). (3) **Rejection callbacks** — `resultcode=300` pings on user/auto deny (a
  `/tx` GET with no `tx` param is a rejection, not malformed; `/sendto` posts `resultCode` 300);
  `resultcode=200` on acceptances that send it. (4) **Callback-path mapping** note under the
  callback table. (5) Trickle Pay section extended: domains are keyed **(host, `topic`)**; the
  `/reg` parameter set (`addr`, `uoa`, `maxper/descper` … `maxmonth/descmonth`, finest-unit
  amounts, merge-takes-the-larger on re-reg); **each registration binds to ONE wallet account**
  (set at nexid `op=reg` / first share) and all later requests are served from it (an `/assets`
  reply covers one account, not the wallet); auto-pay also covers in-limit `/tx` completions;
  token-spending txs always prompt; **currently-enforced gates are `maxper` + the master
  auto-enable** (periodic caps stored but not yet enforced — don't design assuming they clamp);
  the `rproto` default-`http` footgun. (6) Flags section: how the wallet maps wire flags onto
  `TxCompletionFlags` (base `FUND_NATIVE|SIGN|BIND_OUTPUT_PARAMETERS`; `NOFUND` clears,
  `PARTIAL`/`FUND_GROUPS` add; `NOPOST`/`HIDE_ASSET_DETAILS` never reach the completer);
  `NOSHUFFLE` is a guarantee you can't observe (current builds don't reorder anyway — set it
  regardless); the **`tx64=<base64url>`** alternative to `tx=<hex>`. (7) nexid depth: a bare
  `op=login` works only for already-registered hosts (why `loginWalletUri` emits the upsert
  `op=reg`); `op=reg` binds the domain to the account, `op=info` never registers; granted
  identity-info fields arrive as **top-level keys in the POST JSON body**; a declined mandatory
  field blocks the login with no callback; repeat-reg perm-widening routes through the full
  permission screen; `op=sign` extras (`signhex=` binary form, `addr=` key selection, the
  clipboard JSON). (8) Cheat-sheet rows for `/sendto` (numbered `amt0`/`addr0` pairs, **amounts
  in satoshis** unlike BIP21) and `/address` (`blockchain=` param spelling, `unique=true`,
  stable per-domain "main pay address" vs `/share`'s rotating current address). (9) The `chalby`
  **8–64 byte rule** (outside it: enumeration with silently-null proofs) and
  never-returns-authorities note in the `/assets` paragraph. (10) Three new anti-patterns
  (error-flavored success bodies; treating the no-`tx` rejection ping as malformed; raw `tdpp://`
  push without `rproto=https`). Frontmatter + body triggers extended.
- `nexa-wallet-connection/walletUriFormats.md`: the rejection-callback note under the ops table;
  a new "Additional per-op request parameters" section (`/sendto`, `/address`, `/reg`, `/tx`
  incl. `tx64`, the all-ops `topic`/`rproto`/`sig` params, the `chalby` size rule).
- `nexa-transaction-construction/SKILL.md`: (1) **`wallet.abortTransaction(tx)`** — release a
  completed-but-unbroadcast tx's reserved UTXOs (the targeted counterpart of boot-time
  `cleanReserved()`; what Wally calls when the user declines a completed proposal). (2) Observer
  machinery facts: `setOnWalletChange` **returns an `Int` handle** and registers (not replaces);
  `removeOnWalletChange(handle)`; most callbacks pass `txs = null`, so per-operation observers
  should re-check via `wallet.getTx(idem)` (the wallet's own submit-confirm pattern);
  `TransactionHistory.relatedTo` as the persisted annotation map. (3) Pattern 1: the wallet
  honors BIP21 `label`/`message` and **rounds sub-satoshi amounts up**. Triggers extended.
- `nexa-wallet-lifecycle-and-chain/SKILL.md`: (1) **`Bip44Wallet.fastForward(progress):
  Objectify<Boolean>`** — the Rostrum/electrum derivation-path fast sync that injects discovered
  history and jumps `syncedHeight` to the tip; abort handle semantics; progress strings
  ("start"…"finished"); the trust trade-off vs SPV scanning; the ~1-day-behind heuristic recent
  Wally releases use to offer/auto-trigger it. (2) `getCurrentDestination()` — current receive
  destination without rotating — added to Pattern 4 beside `getNewAddress()`. Triggers extended.
- `nexa-tokens-and-groups/SKILL.md` Pattern 8: four wallet-side `/assets` behaviors — one-account
  enumeration (multi-account binding), authorities never returned, the per-domain asset-info
  ACCEPT/ASK/DENY policy, and proofs not reserving the UTXO (re-verify at settlement); plus the
  `chalby` 8–64 byte sizing rule.
- `nexa-capd-messaging/SKILL.md`: a new "Public protocol channels" subsection under Pattern 2 —
  the magic-prefix + indicative-summary-fields + authoritative-partial-tx idiom for a public
  order book over raw `CapdMsg` (framed generically per editorial standard 2), the
  indicative-vs-authoritative trust rule, the priority-margin etiquette
  (`setPowTargetHarderThanPriority(priority + 0.1)`), and the tie-in to the half-tx offer in
  `nexa-transaction-construction`. Triggers extended.
- `nexa-debugging-onchain-errors/SKILL.md`: four new symptom rows — `/tx` with no `tx` param
  (`resultcode=300` = user rejection); wallet shows failure though the tx broadcast (response-body
  tokens); wallet keeps saying "refresh the QR" (`/_lp` 400/404, incl. after auto-reconnect);
  `/assets` proofs all null (`chalby` size).
- `nexa-ktor-server-integration/applicationModuleTemplate.kt`: `/tx` handler comments extended
  with the rejection-ping and response-body-contract rules (body unchanged — it already replied
  a neutral `ok`).
- `INDEX.md`: wallet-connection and wallet-lifecycle rows extended for the above.

### Flagged for review
- None placed in skill bodies.

### New skills created
- None. Everything slots into the existing wallet-connection/tx-construction/lifecycle/tokens/
  capd/debugging skills, per the anti-fragmentation stance.

### Notes for the next agent
- **Verified accurate against the wallet source (no edit needed — don't re-check):** the nexid
  challenge string `<host><portString>_nexid_<op>_<challenge>` incl. the empty-portString rule
  for 80/443; `proto` absent → `http` fallback; `&connect` honored only on login/reg and only
  when not already polling that host:port; the request-signing canonicalization (sig dropped,
  keys sorted, values URL-encoded — note the wallet code carries a comment wanting *form*
  encoding while implementing percent-encoding, so avoid spaces/exotic chars in signed param
  values); signature verification pinned to the **registered** domain `addr` (a param `addr` is
  only used for a not-yet-registered domain); insecure `sendto` rejected outright; registration
  (`/reg`) requiring a valid sig; the challenge-tx construction (nVersion mask, OP_RETURN with
  host as first push — host only, no port — and the random-interleave of the challenge bytes,
  odd-indexed = yours); `af` filters matched against the full constraint script with multiple
  `af` params supported; the `/sendto` reply JSON `{resultCode, txid, txidem, tx, error}` (txid =
  id, txidem = idem); token-spending proposals always prompting; the `flags`→`TxCompletionFlags`
  mapping; `inamt` required when `NOFUND` clear (missing → the wallet rejects the push as a bad
  link); `/share?info=address` returning the account's current receive address and
  `/share?info=clipboard` prompting a clipboard share.
- **Wallet observations kept out of skill bodies** (issue-tracker material, don't re-derive):
  (1) the long-poll URL is built as `"/_lp" + cookieString + "&i=N"`, which yields a malformed
  `/_lp&i=N` when no cookie is present (pushes always carry one, so unreachable in practice);
  (2) `TdppDomain.maxday/maxweek/maxmonth` enforcement is a wallet-side TODO (documented in the
  skill as "not yet enforced" without the TODO framing); (3) the wallet defines a local
  `TDPP_FLAG_FUND_GROUPS = 16` that shadows the libnexakotlin export it otherwise imports;
  (4) output shuffling is a "TODO shuffle outputs" in libnexakotlin's completer — the NOSHUFFLE
  guidance in the skills is written to survive that landing.
- **Chronology note:** the falsified `TDPP_FLAG_*` claim was wrong *at grounding time* (the
  constants predate the corpus by ~10 months) — another instance of Pass 30/31's lesson: when a
  skill asserts a library-surface negative ("X is not exported"), verify against the library
  source, not against recall.
- **Consolidation debt:** this pass added PRIOR/revision blocks in `nexa-wallet-connection`
  SKILL.md (×2), `walletUriFormats.md` (×2), `nexa-transaction-construction` (×1, inline
  parenthetical form), and `nexa-debugging-onchain-errors` (×1, after-table placement) — fold
  them next consolidation pass.
- **Verified-but-unwritten wallet-side surface left for a future pass:** (a) the wallet's
  per-account identity registry APIs on libnexakotlin's `Wallet` (`lookupIdentityDomain`,
  `lookupIdentityInfo`, `IdentityDomain.useIdentity` / per-domain-unique identities) — the
  wallet-connection skill documents the protocol effects but the *library* surface (for apps
  embedding their own wallet + nexid) is undocumented; (b) `payproto_21_72.kt` shows a
  BIP70/72-style JSON payment protocol exists but is Android/JVM-only and stubbed elsewhere —
  not corpus-ready; (c) the wallet's recovery-search constants (derivation-path search depths,
  the non-incremental-address workaround height) hint at recovery edge cases
  `nexa-wallet-lifecycle-and-chain` doesn't cover; (d) `Wallet.getTxo(outpoint)` and
  `walletDestination(addr)` are small undocumented lookups the wallet leans on.
- **What I did NOT touch:** the npl, script-machine, rpc, electrum, compose, flows, ktor (body),
  locktime, identity, and project-setup skills — the wallet sweep surfaced no contradictions
  with them (the wallet's own `org.nexa.assets` copy matches the libnexaapp surface Pass 33
  documented; its price-API mirrors the documented `getNexaExchangeRate`). The two `examples/`
  stub dirs remain the standing backlog. SKILLUPDATES.md remains retire-able per Pass 33.

## Pass 35 — 2026-07-15 — Claude (Fable 5)

**NiftyArt re-grounding pass: swept the NiftyArt repository's full server source (~15K lines:
Application/session/TDPP+Wallet controllers/trade handler/mint tooling/sockets) and its git
history (the TICKET-117/147–168 series through 2026-01) — the first pass grounded in a complete
production application rather than a library or the wallet.** What makes this app corpus-valuable
is what it does WITHOUT: it uses no libnexaapp at all (hand-rolled sessions, wallet routes,
long-poll, `/assets` verification, browser WebSocket protocol) and its frontend is Vue/Nuxt, not
Compose — so it grounds the "the protocol is implementable without the framework" half of the
corpus and exposes libnexakotlin surface the framework normally hides. Every API claim added was
re-verified against **current** libnexakotlin main (the app pins an old 0.4.x, so nothing was
copied blind; e.g. its `GroupInfo.tokenAmt` is today's `tokenAmount`). One long-standing corpus
claim was falsified and corrected across three files; the rest is additive. Editorial standards
held: no app names in skill bodies (patterns framed as marketplace/order-book neutrals), no
version pins, no on-disk paths.

### Corrected
- `nexa-electrum-monitoring/SKILL.md` (Pattern 3 + Pattern 6 monitoring loop + the "not found as
  a state" anti-pattern), `electrumMethodReference.md` (getUtxo paragraph + `ElectrumNotFound`
  exception-table row), `addressWatcherTemplate.kt` (the outpoint-spend branch): **the claim that
  `getUtxo` throws `ElectrumNotFound` for a spent outpoint ("catch it to mean already spent or
  never existed") is wrong — the exception idiom never fires for a real spend.** A spent outpoint
  RETURNS a result with `status == "spent"` and the spending tx in `spent`
  (`GetUtxoSpentInfo(height, tx_hash, tx_pos)`); the underlying Rostrum `blockchain.utxo.get`
  errors only when the tx/output does not exist. Verified three ways: the Rostrum protocol
  documentation ("Returns error if transaction or output does not exist"; status values
  `spent`/`unspent`), the library's own `GetUtxoSpentInfo` type (pointless if spent threw), and
  NiftyArt's production firm-offer invalidation loop branching on `status != "unspent"`. All
  spots corrected with `<!-- PRIOR -->` + revision notes (the template with an inline `PRIOR:`
  comment); the watcher template's spend branch now checks `status == "spent"` and treats
  `ElectrumNotFound` as a watch-list bug. The corpus's own Pattern-3 text already said "inspect
  r.spent to learn whether/where it has been spent" — it was internally inconsistent; now it
  agrees with itself.

### Added
- `nexa-transaction-construction/SKILL.md`: (1) **Pattern 6b — validate a returned or partial tx
  against a trusted node without broadcasting**: `P2pClient.sendTxVal(tx) { reply }` (via
  `wallet.blockchain.req.net.getp2p()`), the bounded-wait wrapper (the callback never fires
  without a txval-capable node — always timeout), and the three reply shapes: empty (cannot
  validate ≠ valid), the **plain non-JSON text `transaction already in mempool`** (success —
  check before JSON-parsing), and JSON via `parseTxValReply(): TxValReply` (isValid/isMineable/
  isStandard, metadata size/txfee/txfeeneeded, tx-level `errors`, per-input `inputs_flags` /
  `inputs_mandatoryFlags` incl. each input's constraint/outpoint/satisfier echo). Partial-tx
  judging rules: filter `min fee not met` (the completer pays later), require every
  `inputs_mandatoryFlags` input valid, branch on `input-does-not-exist`/`inconsistent input
  value`. Noted this is the same P2P message libnexaapp's `/assets` verification rides.
  (2) **Pattern 6c — verifying a returned proposal**: every non-parameterized proposal output
  must survive verbatim; sign ONLY inputs you proposed (match by outpoint, restore your
  `Spendable`, top-level `signInput(tx, idx, sigHashType)`) — never sign an input you didn't
  propose; then validate via 6b. (3) A named **parameterized-outputs** subsection under Pattern 6:
  `OP.TMPL_SCRIPT` (+`TMPL_DATA`/`TMPL_PUBKEYHASH`) placeholder outputs the completing wallet's
  `BIND_OUTPUT_PARAMETERS` fills (`OP.PUSHFALSE, OP.TMPL_SCRIPT` native form; grouped form
  cross-ref to the tokens mint half-tx), and `SatoshiScript.parameterized()` as the
  detection/verification predicate. (4) **Store-and-serve offers**: persist a verified half-tx
  offer (DISK serialization), rehydrate + re-annotate prevouts before pushing (a deserialized
  input's `spendable.amount` is -1 / `priorOutScript` empty — look prevouts up via
  `blockchain.req.getTx` or electrum), push to any buyer with `flags=0` + `inamt=` the input sum,
  invalidate via the electrum sweep, keep one asset input per offer; `iTransaction.inputTotal`/
  `outputTotal` noted (they throw on undiscovered prevouts, making the re-annotation step loud).
  Frontmatter + triggers extended.
- `nexa-wallet-connection/SKILL.md`: (1) a new pattern **"Implementing the wallet-facing routes
  without libnexaapp"** — the server-side `/_lp` behavior grounded from a production
  implementation (reply `A` on `i=0`; hold the poll ~5 s on a per-session queue then answer with
  the pushed URI or an empty body; `Q` to disconnect; 400/404 semantics), the
  **wallet-silent-vs-disconnected** rule (last-poll heuristic ≈ hold+tolerance; a silent wallet is
  probably a backgrounded phone — do NOT log out or clear identity/assets), and QR-friendly
  `A–Z0–9` cookie alphabets (QR alphanumeric mode). (2) A new pattern **"A request can be
  delivered by QR scan alone"** — any tdpp:// request incl. a full embedded partial tx works with
  no long-poll session (chat-bot/kiosk/printed flows); callbacks still correlate by cookie; QR
  density notes (tx64). (3) The `/address`-as-lightweight-registration idiom (stable
  per-(host,topic) address as a durable user key). (4) A pointer from the `/tx` callback pattern
  to tx-construction 6b/6c verification. Triggers extended.
- `nexa-wallet-lifecycle-and-chain/SKILL.md`: (1) Pattern 5 — the **construct-before-start** chain
  wiring (`GetCnxnMgr(chain, start=false)` + `GetBlockchain(chain, cm, start=false)` +
  `exclusiveNodes` + `start()`s) for servers that must never touch public seeders; the
  **electrum-channel pool** (`getElectrum`/`returnElectrum`, recycle-on-timeout); and
  `MultiNodeCnxnMgr.getElectrumServerCandidate` (force the electrum endpoint; `exclusiveNodes`
  pins only the P2P side). (2) Pattern 4 — `sync(maxWait)` blocking startup gate with
  `chainstate` (syncedDate/Height/Hash) progress. (3) A new subsection **"Enumerating spendable
  UTXOs"**: `forEachUtxo` (early-stop lambda), `filterInputs(minAmt, minConfirms, filter)`
  semantics verified from source (no filter ⇒ ungrouped/uncontracted only; with filter you see
  everything and return the counted amount, 0 to exclude; unspent/unreserved checks applied for
  you), and `Spendable.reserved`. (4) Pattern 3 — `wallet.resume()` after open, and
  `deleteWalletFile(...)` corrupt-file recovery with the destructive-action caution. Triggers
  extended.
- `nexa-tokens-and-groups/SKILL.md`: (1) Pattern 9's authority-pool paragraph gains the concrete
  loop: count **unreserved** (`reserved == 0L`) MINT authorities on the **parent** group
  excluding the BATON; top up below a small floor with a generous split (dozens–100, dust-cheap);
  the named exceptions (`WalletNotEnoughTokenBalanceException` → split + retry once;
  `WalletAuthorityException` → nothing to split from, config problem); and the defensive
  `tx.inputs.size > 0` post-completion check. (2) The mint half-tx push now states the
  `inamt=` requirement (flags=0 ⇒ NOFUND clear) and points the `/tx` continuation at 6b/6c
  validation. (3) Pattern 8 gains the **token-gated authorization** use-case (a verified holding
  as the credential; snapshot caveat).
- `nexa-server-state-and-flows/SKILL.md`: a new section **"When the frontend isn't Kotlin"** —
  flowConnector's client half is KMP-only, so Vue/React/JS frontends hand-roll a WebSocket + JSON
  envelope; ground rules from production (per-session socket LISTS — one per tab, session dead
  only when empty; serialized writes + snapshot-before-broadcast; own ping/pong + idle timeout;
  push wallet connected/disconnected/silent transitions explicitly). Negative-triggers block
  annotated.
- `nexa-ktor-server-integration/SKILL.md`: mental model gains "**libnexaapp's wallet routes are a
  convenience, not a requirement**" with the pointer to the wallet-connection implementation
  guide.
- `nexa-electrum-monitoring/SKILL.md`: Pattern 6 gains the **stored-offer invalidation sweep**
  use-case (per-input getUtxo status checks; rate-limiting; recycle-the-pooled-connection on
  timeout). Triggers extended.
- `nexa-debugging-onchain-errors/SKILL.md`: three new symptom rows — empty `sendTxVal` reply
  (no txval node / timeout; empty ≠ valid); `SerializationException` on the plain-text
  `transaction already in mempool` reply; spend-watcher-never-fires (the getUtxo status
  correction).
- `INDEX.md`: tx-construction, electrum, wallet-connection, wallet-lifecycle, and flows rows
  extended for all of the above.

### Flagged for review
- None placed in skill bodies.

### New skills created
- None. Everything slots into the existing tx-construction/wallet-connection/lifecycle/tokens/
  flows/ktor/electrum/debugging skills, per the anti-fragmentation stance.

### Notes for the next agent
- **Verified accurate against the app + current libnexakotlin (no edit needed — don't re-check):**
  the corpus's mint-on-demand half-tx recipe (tokens Pattern 9) matches the app's implementation
  almost line-for-line (add grouped+TMPL_SCRIPT output → complete to pull the authority → remove
  the output → add fee output → PARTIAL-sign → re-add the output); the duplicate-`/tx`-GET
  idempotency guidance (the app added exactly the recommended per-session "already processing"
  flag in a Dec-2025 bugfix); respond-promptly-then-do-slow-work (another Dec-2025 fix moved file
  caching out of the `/tx` handler into a background job); the `TDPP_FLAG_*` values (the app
  defines its own consts — the corpus's permitted fallback); `topic=`-scoped registrations and
  `/address?blockchain=…&unique=…` (bot flows); the challenge-tx odd-indexed-byte extraction; the
  `A`/`Q`/empty long-poll protocol from Pass 34 (this pass added the server-side half);
  `getnewaddress()` as a documented alias of `getNewAddress()`.
- **App observations kept OUT of skill bodies** (issue-tracker / not-corpus material): (1) the
  app's own `/assets` handler verifies proofs via txval (input validity + constraint + outpoint)
  but never calls its OWN `verifySignedChallengeTx` (host + challenge binding) — the same gap
  Pass 33 documented for libnexaapp's built-in handler, so the corpus caveat ("enforce challenge
  binding yourself") is doubly grounded; (2) the app's half-tx sell comment says multi-input
  sells need a "SIGHASH_0THRU" mode that doesn't exist — possibly stale versus the
  `firstnSighash`/`appendableSighash` builders Pass 28 documented in `txCompletionReference.md`;
  I kept the skill text to "one asset input per stored offer is the well-trodden shape" rather
  than asserting impossibility; (3) the app deletes-and-recreates a corrupt wallet file at boot
  and then exits to print the new address — the skill documents the API with a destructive-action
  caution instead of the app's exact policy; (4) `GroupInfo.tokenAmt` → `tokenAmount` and other
  0.4.x-era differences were NOT added as API-evolution notes (pre-1.0 churn, per the
  version-specifics standard).
- **The app's `possibleNfty()` predicate is identical to the corpus's `looksLikeNft()`**
  (tokens Pattern 1) — independent convergence, no edit.
- **Verified-but-unwritten surface left for a future pass:** (a) `iTransaction.fee`/`feeRate`
  properties (same discovered-prevouts precondition as `inputTotal`); (b) `Blockchain.nearTip`
  and `CnxnMgr.p2pCnxns` (per-peer logName/aveLatency/bytesSent/bytesReceived) as a
  diagnostics surface, plus `CommonWallet.debugDump()`; (c) the `wew` library exports small
  utilities apps lean on (`later`/`laterJob`, `dateTimeFormatter`, `take`) — the corpus's WEW
  coverage is accounts/CLI only; (d) `RequestMgr` as a type (the corpus now uses
  `blockchain.req.getTx` once, but the broader request-manager surface — `getUtxo(list)`, block
  requests — is undocumented; note `getp2p()` is suspend); (e) the node-side requirement for
  txval (what nexa.conf enables REQ_TXVAL) is unverified — the skills say "a trusted node that
  offers the tx-validation service" without naming a config knob; verify against the node before
  documenting one.
- **Consolidation debt:** this pass added PRIOR/revision blocks in
  `nexa-electrum-monitoring/SKILL.md` (×3 spots), `electrumMethodReference.md` (×1), and an
  inline `PRIOR:` comment in `addressWatcherTemplate.kt` — fold them next consolidation pass.
- **Chronology note:** NiftyArt's last commit is 2026-01-01 and it pins libnexakotlin 0.4.x, so
  treat its exact API spellings as historical; everything added here was re-checked against
  current library main first. The falsified getUtxo claim dates to the corpus's early electrum
  grounding — the recurring lesson (Passes 30/31/34) holds: verify library-behavior negatives
  against the underlying protocol docs, not recall.
- **What I did NOT touch:** the npl, script-machine, rpc, capd, locktime, identity, compose, and
  project-setup skills — the app sweep surfaced no contradictions with them (its NPL usage is
  nil; its QR generation is app-local zxing, not libnexaapp's `createQrSvg`, so the QR-bug
  guidance stands unchallenged). The two `examples/` stub dirs remain the standing backlog.
  SKILLUPDATES.md remains retire-able per Pass 33. Other sibling apps (NexaWarriors, StarterApp,
  w.nexa.org) remain unswept — natural candidates for the next app-grounded pass.

## Pass 36 — 2026-07-15 — Claude (Fable 5)

**StarterApp re-grounding pass: swept the StarterApp repository (the clone-and-rename starter/
wallet-connection template) — full source of all four modules plus its git history, focusing on
the `starter_update` branch that carries the July feature wave (UI overhaul, electrum-gated
token/NFT metadata routes, chain-status/last-payment flows, disconnect hygiene, testnet
defaults, an NPL secret-sale demo test).** This is the corpus's home app — the maintainer's
SKILLUPDATES gap report was written from it, so Passes 28/33 had already absorbed its
asset-metadata patterns; what remained was the surrounding infrastructure surface the app
exercises that no pass had documented. Every claim added was verified against the actual
library sources (libnexakotlin `chainSelector.kt`/`cnxnmgr.kt`/`platformJvm.kt`/`iWallet.kt`,
libnexaapp `routes.kt`/`routeController.kt`/`session.kt`/`serverAccess.kt`/
`wallywalletorgapi.kt`/`commonUIComponents.kt`), not from the app alone. One important find is
**in-flight library surface**: the libnexaapp `component_update` branch (unmerged at sweep
time, but published as the local build the app compiles against) adds a package declaration to
the high-level compose components — invalidating the corpus's root-package import guidance for
newer artifacts — plus a new `LoadAssetsButton` and a `NexaInputField` parameter; documented as
API evolution with both-sides guidance rather than a correction, since released artifacts still
have the old layout. Editorial standards held: no app names in skill bodies, no version pins
("a newer libnexaapp update" framing), no on-disk paths.

### Corrected
- None. No existing corpus claim was falsified; no `<!-- PRIOR -->` blocks introduced. (The
  compose root-package guidance remains true for released artifacts and was *extended* with the
  in-flight package move, not corrected.)

### Added
- `nexa-transaction-construction/SKILL.md`: **`ChainSelector.explorer(path)`** as the library
  alternative to the hand-rolled explorer-URL `when` (member function since mid-2024: requires a
  leading `/`, rejects `://`/`@` injection, regtest → localhost), plus the adjacent
  `uriScheme`/`currencyCode`/`isMainNet` members (`uriScheme` = the property form of the
  `chainToURI[...]` lookup the corpus snippets use). Existing hand-rolled example kept.
- `nexa-wallet-lifecycle-and-chain/SKILL.md`: (1) **`net.p2pCnxns: List<P2pClient>`** — gate
  wallet sends on `isNotEmpty()` (clear error instead of a stall), the polled chain-status-flow
  idiom (connected/chain/height every few seconds; `MutableStateFlow` equality-dedupe keeps the
  wire quiet), and the per-peer diagnostics fields. (2) **`dataDirectory`** (JVM-only
  libnexakotlin global) — prefixes all library file opens (wallet DBs, logs); set before opening
  wallets; **must end with `/`** (it is a string prefix). (3) The `getnewaddress()`
  bitcoin-RPC-capitalization alias parenthetical in Pattern 4. Triggers extended.
- `nexa-wallet-connection/SKILL.md`: (1) a new pattern **"the built-in `/api/wallet/*` trigger
  routes"** — `tdpp?msg=` (generic frontend→wallet push relay), `assets?filter=` (one-call
  asset round trip with a fresh random 8-byte challenge per request), `connectText` (connect URI
  as text; `?uri` for the raw-scheme form), `disconnect`. (2) The **`allowWalletConnection`
  reconnect gate**: `disconnectWallet()`/`handleAbandoned()` set it false and `/_lp` then
  answers `Q` (this is what stops the wallet's ~30-min auto-reconnect after a deliberate
  disconnect); generating a fresh connect/login URI re-enables it. (3) The **clear-your-own-
  session-state rule**: the library clears only its own fields on disconnect — override BOTH
  `disconnectWallet()` and `handleAbandoned()` (distinct paths), call `super`, clear app fields;
  writing the cleared value into a flowed field pushes it to clients. (4) QR display rules near
  the QR patterns (dark modules on light background — no "reverse" QR; keep the quiet zone;
  `alwaysDark`-on-`alwaysLight`). Triggers extended.
- `nexa-tokens-and-groups/SKILL.md` Pattern 8: the **browser-side one-call trigger**
  (`GET /api/wallet/assets?filter=`) and **client-side hygiene** for the accumulated asset store
  (dedupe by `outpointHash`; clear before a re-request; clear on `walletConnected == false` —
  the server does not re-push on reconnect, so a different wallet on the same session would
  keep rendering the previous wallet's assets; clear group-keyed metadata/media caches too).
- `nexa-server-state-and-flows/SKILL.md`: (1) a new section **"The client's HTTP helpers"**
  (`serverAccess.kt`, verified signatures): `setupServerConnection`/`customServerConnection`,
  `getFromServer` (suspend `String?` + handler overload), `aGetFromServer`/`asGetFromServer`,
  `asGet` (absolute URL), `postToServer`, the `sessionId`/`coScope`/`sessionHttpClient`/
  `timeoutInMs`/`maxReadSize` globals, and the session-header plugin fact (HTTP requests carry
  the same session id the WebSocket uses) — closes Pass 33's open item (a). (2) The
  `MutableStateFlow` equality-dedupe note (poll loops only emit on change). (3) The
  notification-fed-client-state hygiene pointer. Triggers extended.
- `nexa-compose-ui-design/SKILL.md`: (1) the **package-move API evolution** (Mental-model note,
  Setup import comment, and a version caveat on the existing root-package anti-pattern): a newer
  libnexaapp update declares `package org.nexa.libnexaapp.compose` on the high-level components,
  flipping the import direction — with the "try qualified first, fall back to unqualified"
  artifact test. (2) **`LoadAssetsButton(showIcons, aspectRatio, darkmode, onClickLoad)`**
  (renders only while `walletConnected`; pairs with the `/api/wallet/assets` trigger).
  (3) **The inverted exchange-rate direction**: `getNexaExchangeRate` returns fiat-per-NEXA
  (bid/ask midpoint) while `NexaInputField.exchangeRate` is consumed as crypto-per-fiat
  (`crypto = fiat × rate`) — pass `1.0 / price`; treat `0.0` as not-loaded. (4) `NexaInputField`'s
  new `supplementalButtonText` quick-fill parameter (label IS the entered value; replaces `All`).
  (5) **`launchApplink(link)`** (compose package, expect/actual) — the "open in wallet on this
  device" affordance beside a connect QR. (6) The **desktop init** `initLibNexaApp(prefIdentifier)`
  (`org.nexa.libnexaapp.client`; Java prefs under `~/.java/.userPrefs/<id>`) alongside the
  existing Android note. (7) An alternative Pattern-4 theming idiom (app tokens via a
  `CompositionLocal`, one scheme instance synced to the library's global `design` flow —
  no casts). (8) QR quiet-zone/no-reverse rules folded into the alwaysLight/alwaysDark security
  bullet. Frontmatter + trigger keywords extended.
- `nexa-compose-ui-design/componentReference.md`: `LoadAssetsButton` row, the
  `supplementalButtonText` parameter, the rate-direction warning, a `launchApplink` row, and the
  package-evolution note on the root-package section header.
- `nexa-ktor-server-integration/SKILL.md`: the route list now flags the four browser-facing
  trigger routes (pointer to the wallet-connection pattern); the `startercfg.json` pattern gains
  the `dataDir`→`dataDirectory` deployment note. `applicationModuleTemplate.kt`: the `AppSession`
  now shows the two disconnect-path overrides clearing the app-level field.
- `nexa-debugging-onchain-errors/SKILL.md`: three new symptom rows — unqualified/qualified
  compose-component import fails after a libnexaapp change (package migration); previous
  wallet's assets still rendering after a wallet switch (client asset-store hygiene + the
  both-overrides rule); wallet can't reconnect after a server-side disconnect until a fresh QR
  (the `allowWalletConnection` gate, working as designed).
- `INDEX.md`: wallet-lifecycle, wallet-connection, tx-construction, flows, compose, and tokens
  rows extended for all of the above.

### Flagged for review
- None placed in skill bodies.

### New skills created
- None. Everything slots into the existing lifecycle/connection/tokens/flows/compose/ktor/
  tx-construction/debugging skills, per the anti-fragmentation stance.

### Notes for the next agent
- **The component package move is documented from an UNMERGED library branch.** The libnexaapp
  `component_update` branch (package declaration on `commonUIComponents.kt`, `LoadAssetsButton`,
  `supplementalButtonText`, a `ConnectWalletButton` resize fix) was published only as a local
  build at sweep time; libnexaapp main still has the root-package layout. The compose skill
  documents both sides neutrally ("a newer libnexaapp update"). When the branch merges and a
  release ships, a future pass can shift the emphasis (qualified import becomes the default
  guidance, root-package becomes the legacy caveat) — do not remove the legacy side until the
  old artifacts stop mattering.
- **Verified accurate against library source this pass (no edit needed — don't re-check):**
  `connectWallet(call/session, uriVariant, proto)` returns the full universal-link (or raw
  `tdpp://…/lp`) connect URI and sets `allowWalletConnection = true`; the `/_lp` handler's `Q`
  on a disallowed session; `handleAbandoned()`/`disconnectWallet()` clearing identity +
  long-poll channel + setting the gate false; the `/api/wallet/assets` route's per-request
  random 8-byte challenge (the minimum of the documented 8–64 rule); `p2pCnxns` as a public
  `List<P2pClient>` on `CnxnMgr`; `dataDirectory` KDoc ("all file opens are prefixed…use a
  trailing /"); `ChainSelector.explorer`'s require-checks; `getNexaExchangeRate` computing the
  bid/ask midpoint and supporting USD/USDT only; `NexaInputField`'s `crypto = fiat × rate` math;
  `Wallet.getnewaddress()` alias with its "classic bitcoin RPC capitalization" comment.
- **App observations kept OUT of skill bodies** (app-specific or issue-tracker material):
  (1) the app's client extracts the connect link by splitting the `connectSvg` SVG text on `'`
  (fragile — the documented `connectText` route and `X-Login-Link`-style headers are the clean
  paths; not corpus-worthy as a pattern); (2) the app sets `dataDirectory` from config without
  guaranteeing the trailing `/` — the corpus documents the trailing-slash requirement, the app
  may want the defensive `endsWith("/")` fix; (3) the libnexaapp `/api/wallet/connectText`
  route's own comment says it returns "the path (no schema or domain)" but the implementation
  returns a full URI — the corpus documents the real behavior; a library comment fix would help;
  (4) the app's reward/faucet cooldown, RollingNumber odometer, slide-out ActionPanel layout,
  and glass-card design tokens are application design, not infrastructure patterns; (5) the
  balance-delta heuristic for "payment received" (publish explorer link when balance rises) was
  left undocumented — the corpus's `incomingIdxes`/`confirmedHeight` pattern is the more precise
  primitive and documenting a cruder alternative would muddy it.
- **Chronology note:** the app's July commits bump libnexakotlin across the
  `millinow → epochMilliSeconds` rename and to post-`tokenBalance`-fix versions — all already
  documented; no drift surfaced. The NPL demo test in the app is essentially the corpus's own
  secret-reveal contract (npl skill Pattern 1) — independent convergence, no edit.
- **Verified-but-unwritten surface left for a future pass:** (a) `session.activeWalletConnection`
  and the wallet long-poll bookkeeping fields (`lastWalletConnection`, `longPollCount`,
  `walletMessageCount`) on `NexaAppSession` — the wallet-connection skill documents behavior, not
  these fields; (b) the client `SessionHeaderPlugin`/`newSessionId` mechanics beyond the one-line
  mention (oneSessionPerBrowser semantics, wasm cookie interplay); (c) platform HTTP quirks the
  app handles in its own `getFromServer2` (manual 301/302 following on desktop, an iOS/native
  "TLS sessions not supported" HTTP fallback) — possibly library-worthy upstream rather than
  corpus-worthy; (d) `DesignScheme` fields the compose library added around the component update
  were not re-swept field-by-field.
- **Consolidation debt:** none added this pass (no PRIOR/revision blocks). The Pass 33–35 blocks
  remain the open fold list for the next consolidation pass. The two `examples/` stub dirs
  remain the standing backlog. SKILLUPDATES.md remains retire-able per Pass 33. Remaining
  unswept sibling apps: NexaWarriors, WallyWallet's w.nexa.org site code (WallyWallet itself was
  Pass 34), for future app-grounded passes.

## Pass 37 — 2026-07-20 — Claude (Fable 5)

**Spec re-grounding pass: swept the Nexa specification repository (the sibling `specification`
project behind `spec.nexa.org`) — the full `docs/` tree plus its git history through 2026-04 —
and folded the spec content the corpus was missing into the skills.** The recent spec commits
mapped almost one-to-one onto the corpus's known blind spots: the TDPP/DPP page absorbed four
waves of changes (base64url tx encoding 2026-01, fund_groups 2026-02, reply/result-code
clarifications 2026-03, hide_asset_details 2026-04) of which the corpus had already absorbed the
flag bits and `tx64` but not the reply semantics; the script-template page gained a **Scriptlets**
section (2025-12) and three worked byte-level examples; and two whole spec features flagged as
"verified-but-unwritten" by the Pass ~22 spec pass — **read-only transaction inputs** and
**OP_EXEC/scriptlets** — were still absent from the corpus. Library-facing claims were verified
against the local libnexakotlin / libnexaapp / NPL sources before writing (e.g. `NexaTxInput.type`
exists and serializes first; `OP.EXEC`/`OP.JUMP`/full `TMPL_*` family exported; libnexaapp's live
`/_identity` handler verifies only the plain-`sig` form — the commented-out `ctxsig` path never
runs). Editorial standards held: spec pages cited by URL, no version pins, no on-disk paths, no
app names.

### Added
- `nexa-transaction-construction/SKILL.md`: **Pattern 7 — read-only inputs**
  (spec `script/read-only-inputs/`): the input type byte (0 = UTXO, 1 = READONLY;
  libnexakotlin's `NexaTxInput.type` is the wire slot, hand-built — the completer only makes
  spends), the wire rules (amount/sequence must be 0; excluded from input totals; ≥1 normal
  input required), empty-vs-valid satisfier semantics (a valid signed read-only input =
  ownership proof without spending; contracts must introspect satisfier length non-zero),
  grouped assets not counted except the BATON-authority activation, the ROTOTI/confirmed-only
  rule (a same-block or unconfirmed UTXO cannot be read read-only), the `…/ALL` sighash
  recommendation, and the shared oracle/state-UTXO parallel-read use case. Frontmatter +
  trigger keywords extended.
- `nexa-npl-smart-contracts/SKILL.md`: (1) **Pattern 12 — scriptlets** (spec
  `addresses/scriptTemplates/` § Scriptlets + `op_exec/`): holder-supplied script pushed as a
  hidden/visible arg and run by the template via `OP_EXEC` (holder-chosen locks — multisig,
  timelock — without changing the template hash), the `code params N M EXEC` stack contract,
  isolated-subscript-stack rationale (satisfier/constraint code is antagonistic), the
  count-toward-all-limits rule and the MAX_EXEC_DEPTH=3 / MAX_OP_EXEC=20 caps (may rise — never
  rely on them to fail), `OP.EXEC` (0xed)/`OP.JUMP` (0x65) as the libnexakotlin exports, and a
  note that the corpus's NPL DSL surface has no dedicated scriptlet builder. Also the two
  adjacent limit facts: the template script is never pushed on the VM stack (exempt from
  per-item limits) and hidden args may collectively exceed stack limits once re-split.
  (2) Security: the spec's hash160-vs-hash256 guidance on the existing hash160 bullet — prefer
  hash256 when the hash preimage is multi-party (Wagner's birthday attack). (3) A read-only-
  inputs cross-ref in Related skills. Frontmatter + triggers extended.
- `nexa-wallet-connection/SKILL.md`: (1) **the full DPP result-code vocabulary** beyond 200/300
  (`/tx`: 201 filled-but-missing-sigs, 202 unmodified, 203 not final, 204 cannot post; any op:
  301 sig failed, 302 pubkey required, 303 unsupported, 304 insufficient balance) and the
  spec's **replies-are-optional-and-may-arrive-days-late** framing (keep your own timeout, but
  make very-late callbacks benign). (2) In the nexid pattern: the **`ctxsig` challenge-tx reply
  form** (spec requires servers to accept either; libnexaapp's built-in handler verifies only
  plain `sig` — custom handlers must verify the challenge tx themselves), the defined
  login-response codes (incl. 200-with-"bad signature" and 401), and the **≥33 login attempts**
  rule (wallet recovery probes several identities — don't invalidate the challenge early), plus
  the base64→base64url decode-fallback note. (3) The PARTIAL flags-table row now carries the
  spec's signing intent (extendable sighash so others can add inputs/outputs). (4) `/sendto`
  bullet: `chain=` mandatory, N starts at 0 and is not the output index. (5) A short paragraph
  naming `rpath` (reply-path override) and the `/jsonpay` op. Frontmatter + triggers extended.
- `nexa-wallet-connection/walletUriFormats.md`: `jsonpay` + nexid `ctxsig` rows in the ops
  table; `rpath`/`reason` in the all-ops bullet; `/sendto` chain/numbering rules; the signing
  canonicalization sharpened to the spec's *form* encoding (space = `+`, keys unique) and the
  base64url decode fallback.
- `nexa-tokens-and-groups/SKILL.md`: (1) Pattern 9 gains **"Reusing a BATON authority without
  consuming it (read-only inputs)"** — powers granted iff BATON flag + valid non-empty satisfier,
  the authority UTXO survives (vs the consume-and-recreate pool), the `…/ALL` sighash and
  require-a-nonempty-satisfier cautions, and the completer-doesn't-build-these note.
  (2) Mental model: the literal grouped-output wire layout (`<group id push><2/4/8-byte LE
  quantity push>` first, `OP_0` = ungrouped — why a group can't hide in conditional code), with
  a pointer to the spec's byte-level examples. (3) Related refs: the **Token Secrets** protocol
  sketch (`tokensecret/`) with its honest-issuer caveats. Triggers extended.
- `nexa-transaction-construction/txCompletionReference.md`: one paragraph tying the TDPP
  `partial` wire flag to the extendable-sighash model (the wallet-side mirror of
  `appendableSighash`).
- `nexa-debugging-onchain-errors/SKILL.md`: two new symptom rows — a `/tx` callback with
  resultcode 201/202/203/204 (not a rejection; what each means), and a rejected/ineffective
  hand-built read-only input (amount/sequence not 0, no normal input, unconfirmed referenced
  UTXO, empty satisfier proves nothing).
- `INDEX.md`: wallet-connection, tx-construction, npl, and tokens rows extended for all of the
  above.

### Corrected
- None. No existing corpus claim was falsified by the spec sweep — the TDPP flag bits, `tx64`,
  challenge-transaction format, sighash model, reject codes, CAPD priority decay, and the
  TDD/genesis commitments all re-verified clean against the current spec pages. No
  `<!-- PRIOR -->` blocks were introduced this pass.

### Flagged for review
- None placed in skill bodies.

### New skills created
- None. Read-only inputs and scriptlets slot naturally into tx-construction and npl (with
  tokens carrying the BATON slice), per the anti-fragmentation stance.

### Notes for the next agent
- **Recent-spec-commit → corpus mapping (all now absorbed or deliberately skipped):**
  hide_asset_details (2026-04) and fund_groups (2026-02) flag bits were already in the corpus
  (Pass ≤36); base64url `tx64` (2026-01) likewise; the 2026-03 "clarify replies" wave is what
  this pass's result-code/late-reply additions absorb; the 2025-12 scriptlet section and
  script-template examples are absorbed via npl Pattern 12 and the tokens wire-layout note; the
  2025-08 OP_GROUP removal needed nothing (the corpus never referenced OP_GROUP).
- **Spec-internal staleness confirmed again** (per the Pass 22 note): `tokens/grouptokens.md`
  still carries legacy "20 or 32 byte" / "z prefix" / "cashaddr type 2" language and a
  Google-Docs theory-of-operation link; the corpus stays grounded on the newer addressing docs
  (32 bytes, "t" prefix). Its consensus-validation sketch (single-mint group = hash256 of the
  spent outpoint; mint-melt group mechanics) was NOT added to the corpus — it is genesis-time
  internals the libraries handle, and the page itself warns it describes the old format.
- **Deliberately not documented from the spec** (candidates for a future pass if demand
  appears): `script/script-registers.md` (OP_STORE/OP_LOAD, 32 registers) is marked
  **Draft/0.1** — the script-machine skill's `setRegister`/`getRegister` keyword mentions
  suffice until it hardens; the full non-OP_PARSE introspection opcode enumeration, OP_JUMP
  semantics, `bignum`/`OP_SETBMD` deep dive, and `negative_op_roll_op_pick` remain the same
  "avoid over-deepening" call earlier passes made (script-machine covers BMD keywords);
  `nexid.md`'s `ava`-avatar-as-NFT-group-address nuance and the op=sign genesis-address
  use-case were left at the current level of detail; the DPP registration `supports` bitmap is
  spec'd as a *possible future* wallet response, so it was not documented as live behavior.
- **Library-verification notes for what WAS added:** `NexaTxInput.type: Byte = 0` exists in
  libnexakotlin and serializes first (the sizeNoScript comment names the order); no library
  code was found that *sets* type=1, consistent with the "hand-built technique" framing — if a
  future libnexakotlin adds first-class read-only-input or scriptlet builders, upgrade the
  wording from "hand-built" to the API. The `ctxsig` claim was checked both ways: libnexaapp's
  request types carry a `ctxsig` field only in commented-out client code, and the live
  `postIdentity` handler verifies `p.sig` only — that is why the skill says "custom handlers
  must verify the challenge tx themselves" rather than pointing at a library helper.
- **Wallet-behavior caveat on the new result codes:** the 201/202/203/204 vocabulary is
  spec-grounded; this pass did not re-sweep the Wally wallet source to confirm which of them
  current builds actually emit. If a future wallet-grounded pass finds some are never sent,
  keep the rows (they are protocol-legal) but note observed behavior.
- **Consolidation debt:** none added (no PRIOR blocks this pass). The Pass 33–35 PRIOR blocks
  remain the open fold list; the two `examples/` stub dirs remain the standing backlog;
  SKILLUPDATES.md remains retire-able per Pass 33. Remaining unswept sibling apps: NexaWarriors,
  w.nexa.org. The spec repo itself is now swept through commit 65f8c61 (2026-04-28) — a future
  pass can diff from there.

## Pass 38 — 2026-07-21 — Claude (Fable 5)

**Consolidation pass (sixth one; Passes 3, 9, 15, 20, and 32 were the prior five).** No new
technical content. This pass cleans up the audit trail accumulated since Pass 32 and re-affirms
the maintainer's three editorial standards (deprioritize version specifics; de-anchor from named
applications; remove on-disk vs off-disk distinctions) as the standing law for skill bodies and
INDEX. I read the corpus (INDEX, the CHANGELOG including every recent "Notes for the next agent,"
and the affected skill files in full) and swept every file for audit artifacts and framing
violations before editing. The debt was exactly what Passes 33–35 catalogued as they deferred it
(Passes 36 and 37 added none): the `<!-- PRIOR -->`/`> **Revision note:**` blocks from the
libnexaapp, wallet, and application re-grounding passes. Every folded correction had held up
across the subsequent passes (Pass 35 independently re-grounded the Pass-33 `/assets` caveat;
Passes 36/37 re-verified the TDPP flag and electrum claims without contradiction); no open
question remained behind any wrapper, so nothing needed to survive as a developer-facing caveat.

### Corrected (audit-trail folded, corrected content kept)
- `nexa-wallet-connection/SKILL.md` (the cheat-sheet flags note and the flags-bitfield section),
  `nexa-wallet-connection/walletUriFormats.md` (the flags table intro),
  `nexa-transaction-construction/SKILL.md` (Pattern 2 note): folded the Pass-34 PRIOR/revision
  blocks on `TDPP_FLAG_*` importability. The corrected substance stays as plain prose: all six
  flags are documented top-level `const val`s in libnexakotlin's `utils.kt` (common code) —
  import them rather than re-defining; defining matching `const val`s remains harmless since the
  wire values are protocol-fixed.
- `nexa-wallet-connection/walletUriFormats.md` ("Push vs route path asymmetry"): folded the
  Pass-34 PRIOR/revision pair on who maps push paths to callback paths. The corrected mechanism
  (the wallet's own op handlers hardcode `/_lp`/`/_share`; the nexid callback reuses the URI's
  path) stays, and the revision note's substantive deep-link fact was folded into the prose as a
  parenthetical (the universal-link layer only unwraps `http(s)://<any-host>/<scheme>/<rest>` →
  `<scheme>://<rest>`; any host works — `w.nexa.org` is convention, not mechanism).
- `nexa-debugging-onchain-errors/SKILL.md`: deleted the Pass-34 after-the-table PRIOR/revision
  pair for the `TDPP_FLAG_*` symptom row — the corrected row (pin/typo causes) already stands on
  its own in the table.
- `nexa-electrum-monitoring/SKILL.md` (Pattern 3, the Pattern 6 monitoring loop, and the
  "not found as a state" anti-pattern), `electrumMethodReference.md` (the `GetUtxoResult`
  paragraph), `addressWatcherTemplate.kt` (the outpoint-spend branch comment): folded the
  Pass-35 PRIOR/revision blocks on `getUtxo` spend detection. The corrected substance stays as
  plain prose everywhere: a spent outpoint RETURNS a result with `status == "spent"` and the
  spending tx in `spent`; `ElectrumNotFound` fires only for an outpoint the server doesn't know
  (never existed / bad hex / wrong chain) and is never the spend signal. Cross-references that
  pointed at "the revision note in Pattern 3" now point at Pattern 3 itself.
- `nexa-tokens-and-groups/SKILL.md` Pattern 8: replaced the Pass-33 PRIOR/revision pair with a
  prose parenthetical carrying both surviving facts — libnexaapp ships the complete `/assets`
  response side (`TricklePayAssetList`/`TricklePayAssetInfo`, `checkAssetChallenge`, the built-in
  `handleAssets` route), making the hand-rolled steps the custom/override path; and the
  load-bearing caveat that a bare `outpointHash` is never proof of ownership.

### Added
- None. Consolidation pass: no new patterns, anti-patterns, mental models, insights, or skills.

### Flagged for review
- None. Every question the folded wrappers documented is settled and stated as fact in the prose
  (flags importable; wallet-side path mapping; `getUtxo` spent-status semantics; built-in
  `/assets` handler).

### Deleted
- All `<!-- PRIOR: ... -->` comments and `> **Revision note:**` blocks remaining in any skill
  body, reference doc, or `.kt` template (eight files touched). After this pass a repo-wide grep
  for `PRIOR` / `Revision note` / `Review needed` / `⚠` across everything except this CHANGELOG
  returns nothing.

### Reframings re-checked (no change needed — already compliant)
- **Version specifics:** no Nexa-library version pins anywhere in skill bodies; the third-party
  JUnit pins in `nexa-project-setup` remain the deliberate, documented exception, and the
  kotlinx-serialization 1.10.0 caveat remains as a genuine version-specific behavior note.
- **Named applications:** no application names in any skill body. `w.nexa.org`, the Wally wallet,
  and Trickle Pay are protocol infrastructure and stay; "the starter app" in the compose skill is
  generic framing (the template you cloned), not a repo reference.
- **On-disk vs off-disk:** no machine paths in any skill body. Universal developer paths
  (`~/.gradle/caches`, `~/.m2`, `~/.java/.userPrefs`) and wire/storage concepts
  (`SerializationType.DISK`, the wallet's on-disk database, the asset-cache disk flush) remain in
  bounds, as do light pointers into library source files (`utils.kt`, `qr.kt`, `wallet.kt`).

### INDEX
- No change. The "Where to find canonical sources" section already matches the consolidation
  brief's Step 3 (libraries with Maven coordinates and GitLab registry locations, the spec for
  protocol facts, the published POM for transitive pins — no local paths, no named apps, no
  version pins). Re-verified rather than churned, as Pass 32 did.

### New skills created
- None. Per the consolidation brief.

### Notes for the next agent

**The three editorial standards remain the law for skill bodies and INDEX going forward. Do not
re-introduce what the six consolidation passes (3, 9, 15, 20, 32, 38) removed:**

1. **Deprioritize version specifics.** No pinned Nexa-library version numbers in skill bodies;
   coordinates (`group:artifact`) and repository pointers stay; version placeholders
   (`"<latest>"`) plus "look it up in the GitLab Maven registry" replace pins in Setup/TOML/Gradle
   snippets. A concrete version appears only where it marks a genuine API-surface change or
   version-specific behavior. "Verified against version X" provenance is not one of those —
   extract the claim, drop the pin. The JUnit pins are the standing documented exception.

2. **De-anchor from named applications.** Skill bodies and INDEX describe Nexa infrastructure and
   patterns framed in neutral domains (an order book, a marketplace listing, a vesting schedule) —
   never applications by name and never internal process labels. The Wally wallet, Trickle Pay,
   and `w.nexa.org` are protocol infrastructure and may be named. Named apps may appear only here
   in the CHANGELOG, as historical record.

3. **Remove on-disk vs off-disk distinctions.** Skill bodies assume nothing about the reader's
   checkout layout; INDEX's "Where to find canonical sources" is the authoritative "where to
   look" map. Light pointers into library source files are fine; machine paths are not; universal
   developer paths and wire-format/storage concepts remain in bounds.

**How the debt accrued this cycle (keep the pattern).** Passes 33–35 each added a few
clearly-marked PRIOR/revision blocks for genuinely reversible corrections and catalogued them
precisely in their notes; Passes 36–37 added none and re-affirmed the fold list. That made this
cleanup mechanical and safe. Future re-grounding passes should keep doing exactly that: mark
reversible corrections inline, catalogue them in the CHANGELOG, and let the next consolidation
pass fold them once they've held up.

**Substance preserved.** No technical content was added or removed — only audit framing. The
"where did it go?" lookups: the deep-link unwrap fact is a parenthetical in
`walletUriFormats.md`'s path-asymmetry section; the never-trust-a-bare-`outpointHash` caveat is
in the tokens Pattern-8 parenthetical; the `getUtxo`/`ElectrumNotFound` semantics are plain prose
in electrum Pattern 3, the Pattern-6 loop comments, the anti-pattern, the method reference, and
the watcher template.

**Untouched targets that remain.** The two `examples/` stub dirs
(`nexa-npl-smart-contracts/examples/`, `nexa-tokens-and-groups/examples/`) are still the standing
backlog (compile-and-verify gated, per Pass 21). `SKILLUPDATES.md` at the repo root remains
retire-able by the maintainer (all three gaps closed, per Pass 33). Remaining unswept sibling
apps for future re-grounding passes: NexaWarriors and the w.nexa.org site code. The spec repo is
swept through 2026-04-28 (Pass 37); the next spec pass can diff from there.
