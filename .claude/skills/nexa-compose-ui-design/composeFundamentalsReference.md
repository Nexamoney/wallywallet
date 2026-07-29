# Compose mechanics that keep a Nexa UI correct and fast

These are general Compose Multiplatform fundamentals — true on every target a Nexa app runs
on (jvmDesktop / wasmJs / Android / iOS) — framed for the libnexaapp way of building UI
(Compose `foundation`, screen state held in `MutableStateFlow` / the `flowConnector` reactive
layer, the global `design`/`appDim` flows). They are the difference between a UI that
recomposes the whole screen on every keystroke and one that stays smooth.

## The three-phase frame

Every frame runs three phases: **Composition** (run composables, read state — a state read
here recomposes the whole reading scope), **Layout** (measure/place — can read state without
recomposing), **Drawing** (emit draw ops). Defer a frequently-changing read to a later phase
so it doesn't recompose:

```kotlin
// BAD: reads the value in composition → recomposes every frame the offset changes
Box(Modifier.offset(x.dp, 0.dp))
// GOOD: reads in the layout phase → no recomposition
Box(Modifier.offset { IntOffset(x.value.toInt(), 0) })
// GOOD: visual-only change reads in the draw phase
Box(Modifier.graphicsLayer { alpha = animatedAlpha.value })
```

This is the same principle libnexaapp's own components apply (e.g. `BasicButton` derives its
padding from a measured size rather than recomposing on every layout change).

## Keep logic out of composable bodies

A composable should **render state and emit callbacks**, nothing more. Parsing, validation,
amount math, and data loading belong in the state holder that feeds the screen (a
`MutableStateFlow<ScreenState>`, or a `flowConnector` flow — see `nexa-server-state-and-flows`),
not inline in a `@Composable`. Logic in a composable body forks the source of truth, can't be
unit-tested, and re-executes on every recomposition.

```kotlin
// BAD: parsing + business math in the composable
@Composable fun Row(amountText: String) {
    val amt = amountText.toDoubleOrNull() ?: 0.0      // parse in UI
    val fee = amt * 0.001                              // business rule in UI
    BasicText("Fee: $fee", color = { de.bodyText }, style = de.bodyStyle)
}
// GOOD: the state holder exposes canonical, already-derived values; the UI just renders them
@Composable fun Row(state: SendState) {
    BasicText("Fee: ${state.fee}", color = { de.bodyText }, style = de.bodyStyle)
}
```

Store **canonical** values in state and derive *display* values at the presentation boundary
(scale token amounts by their decimals only for display — see `nexa-tokens-and-groups`).
Don't store `total` + `formattedTotal` + `hasTotal`; keep one and compute the rest.

## State stability and primitives

Compose skips recomposing a composable whose inputs are unchanged and **stable**. Help it:

- Screen/business state: **immutable `data class`** with immutable collections. A `var`,
  a `MutableList`, or a lambda stored in state defeats skipping and forces recomposition.
- UI-local state: use the **typed** holders to avoid boxing — `mutableIntStateOf(0)`,
  `mutableFloatStateOf(0f)` (not `mutableStateOf<Int>()`); `mutableStateListOf()` for an
  observable UI-local list. Reserve these for ephemeral visual concerns (scroll, focus,
  expansion), not business data.
- Small UI-local state that must survive process recreation: `rememberSaveable` (with a
  `Saver` for custom types). Screen business state belongs in the state holder, not here.
- `@Stable` / `@Immutable` annotations let the compiler treat your own types as skippable
  when they truly are.

## Side effects — what to use and where

| API | Use it for |
| --- | --- |
| `LaunchedEffect(key)` | Coroutine tied to composition; reruns when `key` changes. Keyed `Unit` = run once. Collect screen effects at the top of the screen. |
| `DisposableEffect(key)` | Register + `onDispose { }` cleanup (listeners, observers). Always pair. |
| `rememberCoroutineScope()` | Launch UI-local async from a callback (scroll animation, snackbar). Prefer routing real actions to your state holder. |
| `rememberUpdatedState(v)` | Capture the latest value inside a long-running effect without restarting it. |
| `produceState` / `snapshotFlow` | Bridge imperative/Compose state into a `State`/`Flow`. Prefer the state holder's `StateFlow` for screen state. |
| `SideEffect { }` | Publish Compose state to a non-Compose object after each successful composition. Use sparingly. |

Collect your screen state with `collectAsState()` (what libnexaapp's components use). On
Android/CMP you may instead use `collectAsStateWithLifecycle()` (from
`lifecycle-runtime-compose`) to pause collection in the background — verify it supports your
KMP targets before using it in `commonMain`.

## Modifiers and reuse

- **Modifier order is meaningful** (applied left→right): `Modifier.background(c).padding(8.dp)`
  ≠ `Modifier.padding(8.dp).background(c)`.
- **Always accept a `modifier: Modifier = Modifier` parameter** on a reusable composable and
  apply it to the root, so callers can size/position it.
- **Slot pattern:** take `@Composable` lambda parameters for flexible containers (a card that
  accepts a `title` and `content` slot) rather than threading every leaf through props.

## CompositionLocal — and why libnexaapp uses global flows instead

`CompositionLocal` suits *theming and platform handles* (`LocalDensity`, `LocalWindowInfo`)
and rarely-changing cross-cutting values. Don't use it for frequently-changing values
(widespread recomposition) or for things only 1–2 levels deep (pass directly). Note that
libnexaapp deliberately exposes its theme (`design`) and geometry (`appDim`) as **global
`MutableStateFlow`s** read with `collectAsState()`, rather than as `CompositionLocal`s — a
pragmatic choice that lets non-composable code update them; follow that existing pattern
rather than introducing a competing theme `CompositionLocal`.

## Lists and grids

For any scrolling list (transaction history, token list, address book):

- **Always supply a stable `key` by a domain id**, never the list index — without it,
  Compose mis-associates item state and animations jump when the list changes:
  ```kotlin
  LazyColumn {
      items(txs, key = { it.txid }) { tx -> TxRow(tx) }
  }
  ```
- Use `contentType` when a list mixes row shapes, so Compose can reuse layouts.
- **Keep scroll state local** (`rememberLazyListState()`) — never in screen state. Derive
  signals (e.g. "show scroll-to-top") with `derivedStateOf` so you don't recompose per pixel.
- Don't `filter`/`sort` inside the `LazyColumn` body — do it in the state holder and feed the
  finished list in.
- For small fixed lists, a plain `Column` is fine; reach for `LazyColumn` when the count is
  large or unbounded.

## Animation state is UI-local

Animation progress, shimmer alpha, expansion toggles are **local UI state**, never screen
business state. (For richer button/effect animation, use libnexaapp's own Lottie system —
`LottieButton`/`AniEvents` — see the main skill's component coverage, rather than hand-rolling.)

## Resources and localization

libnexaapp uses Compose Multiplatform resources, so the standard `composeResources` model
applies (the main skill's `xlat` wraps `stringResource`):

- Put shared assets under `commonMain/composeResources/`: `drawable/`, `font/`, `values/`
  (strings/plurals/arrays), `files/` (raw). Access via the generated typed `Res`
  (`Res.string.key`, `Res.drawable.icon`) for compile-time safety — never Android `R` in
  `commonMain`.
- **Qualifiers** select variants automatically: locale (`values-es/`, `values-es-rMX/`),
  theme (`drawable-dark/`), density (`drawable-xxhdpi/`). `stringResource`/`xlat` pick the
  right locale at runtime with no code change.
- **Plurals**: `pluralStringResource(Res.plurals.k, count, count)`. Templates: `%1$s`/`%1$d`.
- Reminder (main skill Pattern 7): `painterResource` doesn't redraw after async load on web —
  use the library's `icon`/`img`/`SvgImage` for drawables.

## Cross-platform placement

Share UI and presentation logic in `commonMain`; put genuinely platform-specific *behavior*
behind `expect/actual` (libnexaapp itself does this for `SvgImage`, `launchApplink`,
`CopyToClipboard`, `platform()`). Model a platform capability as a function/flow the common
code calls, not as a platform type leaking into screen state. Don't abstract prematurely —
share the business logic first, introduce an `expect/actual` only for a real platform
difference.

## Testing the UI layer

- A `flowConnector`/`MutableStateFlow`-backed state holder is testable in `commonTest` by
  driving inputs and asserting the emitted state (Turbine is the common helper for `Flow`
  assertions). Prefer testing the **state holder** over the rendered UI.
- For composable tests, `runComposeUiTest` works across CMP targets. Assert on semantics
  (the same labels/roles from `accessibilityReference.md`), not on pixels.
- Pure validators/formatters (amount parsing, fee math) are plain functions — test them
  directly.

## Performance anti-patterns (quick table)

| Anti-pattern | Why it hurts | Fix |
| --- | --- | --- |
| Business logic / parsing in a composable body | forks source of truth, reruns per recomposition, untestable | move to the state holder / `flowConnector` logic |
| Mutable collections or lambdas stored in screen state | defeats skipping → more recomposition | immutable `data class` + immutable collections |
| Reading whole screen state high in the tree | recomposition cascades to all children | slice state; pass each leaf only what it needs |
| Missing/index-based `LazyColumn` keys | item state jumps, broken animations | stable `key` by domain id |
| `mutableStateOf<Int>()` for a counter | boxing on every read/write | `mutableIntStateOf()` |
| Reading a fast-changing value in composition (offset/alpha) | recomposes every frame | defer to layout (`offset { }`) or draw (`graphicsLayer { }`) |
| No-op state emissions (copy when nothing changed) | wasted recomposition | guard unchanged values before updating |
| Animation flags in screen state (`shakeCount`, `alpha`) | pollutes business state | keep animation state local |