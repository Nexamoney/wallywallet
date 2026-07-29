---
name: nexa-compose-ui-design
description: "Builds the front-end UI of a Nexa app with libnexaapp's Compose Multiplatform design library (org.nexa.libnexaapp:compose) so it looks clean, modern, branded, and consistent across phone/desktop/web — instead of hand-rolling ad-hoc, ugly screens. Use when styling or laying out any Nexa app UI: defining a theme/color palette, adding dark/light mode, building buttons/inputs/toggles, making the layout responsive, or wiring a wallet-connect button. Triggers: Compose, Compose Multiplatform, CMP, DesignScheme, the global `design` flow, darkMode, dark/light mode, theme, theming, palette, branding, NexaApp, appDim, aspect ratio, responsive layout, BasicButton, IconTextButton, RowButton, ThinIconTextButton, LottieButton, LightModeToggle, ConnectWalletButton, LoadAssetsButton, NexaInputField (incl. the inverted exchangeRate direction and supplementalButtonText), launchApplink, initLibNexaApp, vsash/hsash/CCSash, color utilities (mix/brightness/normalize/complementary/inverse), the design editor, SvgImage, xlat, decodeToImageBitmap/makeImageBitmap (rendering runtime-fetched raster/NFT image bytes), 'make the UI look good/professional', 'style my Nexa app', 'the starter UI is ugly'. Also covers UI quality fundamentals on this foundation-based stack: accessibility (semantics, role, contentDescription, 48dp touch targets, contrast, color-not-alone), Compose mechanics and performance (recomposition/three-phase model, state stability, side effects, modifier order, CompositionLocal, LazyColumn keys, scroll state), and loading/refresh UX (skeleton vs spinner, preserving content and user input, inline validation). See nexa-server-state-and-flows for the flowConnector state layer the UI binds to, nexa-wallet-connection for the wallet protocol behind ConnectWalletButton, and nexa-project-setup for wiring the dependency."
---

# Nexa Compose UI and design system (libnexaapp compose library)

## When to use this skill

Trigger whenever a developer is building or styling the **front end** of a Nexa
application — the visible Compose UI — and wants it to look clean, modern, and
consistent rather than ad-hoc. Concretely trigger on:

- Keywords: Compose, Compose Multiplatform, CMP, `DesignScheme`, the global
  `design` flow, `darkMode`, dark/light mode, theme, theming, color palette,
  branding, `NexaApp`, `appDim`, `DimensionMonitor`, aspect ratio, responsive,
  adaptive layout, `BasicButton`, `RowButton`, `IconTextButton`,
  `ThinIconTextButton`, `LottieButton`, `LightModeToggle`, `ConnectWalletButton`,
  `NexaInputField`, `CCSash`/`CCFracSash`/`vsash`/`hsash`, the color utilities
  (`mix`/`brightness`/`normalize`/`newAlpha`/`complementary`/`inverse`/`toHexString`),
  the design editor, `SvgImage`, `icon`/`img`, `xlat`, `Brush.solidColor`,
  `Double.format`, `pxToDp`, `decodeToImageBitmap`, `makeImageBitmap`, "render
  fetched/NFT image bytes", `getNexaExchangeRate` (the NEXA price feed for
  `NexaInputField` — mind the inverted rate direction), `LoadAssetsButton`,
  `launchApplink` ("open in wallet" same-device link), `supplementalButtonText`,
  `initLibNexaApp` (desktop prefs init). Also: accessibility, `semantics`, `Role`,
  `contentDescription`, touch target, contrast, WCAG; recomposition, three-phase,
  `remember`/`mutableStateOf`/`mutableIntStateOf`, `LaunchedEffect`/`DisposableEffect`,
  stability, modifier order, `CompositionLocal`, `LazyColumn`/`LazyRow`, list keys,
  `derivedStateOf`, scroll state; loading state, skeleton, shimmer, inline validation,
  preserve input.
- Tasks: "make my Nexa app look good / professional / modern", "the starter UI
  is ugly, give me a clean baseline", "add a theme / color scheme", "add dark
  mode", "style the buttons", "build a responsive layout that works on phone and
  desktop and web", "add a connect-wallet button", "build a crypto amount input
  field", "add a resizable split pane", "derive a palette from my brand color",
  "make my Nexa UI accessible / screen-reader friendly", "my screen recomposes too
  much / is janky", "key my transaction list", "show a loading/refresh state without
  wiping the screen", "validate a form field inline".

**Negative triggers** — do NOT use this skill for:
- The reactive **state** layer behind the UI (`flowConnector`, per-session vs
  global flows, WebSocket sync) — use `nexa-server-state-and-flows`. This skill
  is about *rendering*; that one is about the *data the UI renders*.
- The **wallet connection protocol** (TDPP/nexid URIs, QR generation, callbacks).
  `ConnectWalletButton` is documented here as a UI element, but the protocol it
  triggers is in `nexa-wallet-connection`.
- App-architecture frameworks not used by the libnexaapp stack — MVI/MVVM
  ViewModel scaffolding, Jetpack Navigation, Hilt/Koin DI. This skill covers the
  Nexa design library and the general Compose *mechanics, accessibility, and
  loading-state UX* that apply to it (Patterns 9–11), but not those frameworks;
  libnexaapp drives screen state through `flowConnector` (`nexa-server-state-and-flows`),
  not per-screen ViewModels.
- Wiring the Gradle dependency / targets — see `nexa-project-setup`; only the
  coordinate and import surface are covered here.

## Mental model

libnexaapp ships a dedicated **Compose Multiplatform design library** (Maven
coordinate `org.nexa.libnexaapp:compose`, package `org.nexa.libnexaapp.compose`).
It exists so every Nexa app shares one clean, branded, responsive look instead of
each app reinventing buttons and colors. The single most important habit:

> **Do not hardcode colors, shapes, and sizes per screen. Centralize them in a
> `DesignScheme`, drive the whole UI off the global `design` flow, and compose
> screens out of the library's components.** Most "ugly Nexa app" problems are an
> app that ignored the design library and scattered raw `Color(0xFF…)` literals
> and bare Material defaults across its screens.

Four structural facts:

1. **There is one global theme flow.** `design: MutableStateFlow<DesignScheme>`
   (in `org.nexa.libnexaapp.compose`) holds the active scheme. Every library
   component (`BasicButton`, `IconTextButton`, …) reads it via
   `design.collectAsState()`. Reassign `design.value` and the whole UI restyles
   at once. A `DesignScheme` is an **open class** — you subclass it to add your
   app's own tokens (window/body background, body text style, outline color, …)
   and your own composables read your subclass's fields.

2. **You derive a palette; you don't hand-pick it.** The color extension
   functions (`Color.mix`, `.brightness`, `.normalize`, `.complementary`,
   `.inverse`, `.newAlpha`) let you generate a coherent set of related colors from
   one or two base colors. A scheme built by *deriving* shades from a brand color
   looks intentional; a scheme of unrelated hand-typed hexes looks like the
   starter app.

3. **The app is responsive by construction.** You wrap your root composable in
   `NexaApp { … }`, which runs `appDim.monitor()`. `appDim` (a `DimensionMonitor`)
   publishes `aspectRatio`, `size`, `sizePx`, and `density` as flows. Components
   already adapt to it (e.g. buttons collapse their label to just an icon as the
   screen narrows). Your layout should branch on `appDim.collectAspectRatio()` /
   `collectDpSize()` rather than hardcoding a phone or a desktop layout.

4. **The library leans on Compose `foundation`, not `material3`.** It uses
   `BasicText` and its own `BasicButton` rather than Material components (Material3
   is pulled in but kept to a minimum — it is large). Prefer the library's themed
   components and `BasicText` styled from your `DesignScheme` over dropping raw
   `androidx.compose.material3` widgets onto a screen; the Material defaults are a
   common source of the off-brand look.

A note on packages, because it bites people: the **buttons, color utilities,
layout primitives, and helpers** are in `org.nexa.libnexaapp.compose`, but the
three high-level components **`LightModeToggle`, `ConnectWalletButton`, and
`NexaInputField` are declared in the root (default) package** — import them
*unqualified* (`import ConnectWalletButton`), not under the `compose` package.

> **API evolution — the root-package quirk is being fixed.** A newer libnexaapp update adds the
> missing `package org.nexa.libnexaapp.compose` declaration to the file holding these
> components (and adds a fourth one, `LoadAssetsButton` — Pattern 5). Against such builds the
> imports invert: `import org.nexa.libnexaapp.compose.ConnectWalletButton` resolves and the
> unqualified import does not. The reliable rule: try the qualified `…compose.` import first on
> a current artifact; if it doesn't resolve, your artifact predates the move — import
> unqualified. (Same drill as `nexa-project-setup` § "Verifying API signatures".)

## Setup and versions

The design library is a separate published artifact from the rest of libnexaapp.
Pin versions per `nexa-project-setup` (look up the current version in the GitLab
Maven registry rather than copying a number here):

- **Coordinate:** `org.nexa.libnexaapp:compose` (GitLab Maven project `73565187`,
  the same project that publishes the libnexaapp `:app` and `:server` artifacts).
  It transitively brings in the libnexaapp `:library` artifact, Compose
  (`runtime`/`ui`/`foundation`/`material3`/`components.resources`/`animation`),
  the compottie Lottie wrapper, and bignum.
- **Targets:** the library publishes for JVM (desktop), `wasmJs` (web), Android,
  and iOS (`iosArm64`/`iosSimulatorArm64`). The same UI code in `commonMain`
  drives all of them.

```kotlin
// build.gradle.kts (composeApp / shared UI module), versions via nexa-project-setup
commonMain.dependencies {
    implementation("org.nexa.libnexaapp:compose:<latest>")
    // plus the Compose Multiplatform plugin + compose.* deps your module needs
}
```

Imports you will use constantly:

```kotlin
import org.nexa.libnexaapp.compose.NexaApp        // responsive root wrapper
import org.nexa.libnexaapp.compose.appDim          // DimensionMonitor (aspect ratio, size)
import org.nexa.libnexaapp.compose.DesignScheme    // open theme class (subclass it)
import org.nexa.libnexaapp.compose.design          // MutableStateFlow<DesignScheme>
import org.nexa.libnexaapp.compose.BasicButton     // themed buttons
import org.nexa.libnexaapp.compose.IconTextButton
import org.nexa.libnexaapp.compose.mix             // color derivation: also brightness, normalize,
import org.nexa.libnexaapp.compose.brightness      //   complementary, inverse, newAlpha, toHexString
import org.nexa.libnexaapp.compose.solidColor      // Brush.solidColor(color)
import org.nexa.libnexaapp.compose.SvgImage        // expect/actual SVG (Android has no native SVG)
import org.nexa.libnexaapp.compose.xlat            // string-resource translation + %-templating
import org.nexa.libnexaapp.compose.vsash           // draggable split panes (also hsash, CCSash)

// NOTE: these three are in the ROOT package — import them UNQUALIFIED:
import ConnectWalletButton
import LightModeToggle
import NexaInputField
// (newer libnexaapp builds move them INTO org.nexa.libnexaapp.compose — see the API-evolution
//  note in the Mental model; on those builds import them qualified, alongside LoadAssetsButton
//  and launchApplink)
```

On **Android**, the library needs an init call in your `Activity.onCreate`
(`androidInitLibNexaApp(...)`) before the Compose content is set, or theme/prefs
lookups fail — see `nexa-project-setup` for the platform bootstrap. The **desktop/JVM**
counterpart is `initLibNexaApp(prefIdentifier)` (`org.nexa.libnexaapp.client`): call it once in
`main()` before the Compose window launches; it wires the `prefs` backend to Java preferences
(stored under `~/.java/.userPrefs/<prefIdentifier>`). `prefs`
(`org.nexa.libnexaapp.client.prefs`) backs the persisted UI state (dark-mode
choice, sash sizes).

## Core patterns

### Pattern 1: Wrap your app in `NexaApp { }` so it is responsive

`NexaApp` installs the `appDim` dimension monitor around your content. Do this
once, at the very top of your UI tree, before any screen draws.

```kotlin
@Composable
fun App() {
    NexaApp {                  // runs appDim.monitor() — required for responsive components
        MainScreen()
    }
}
```

Anywhere inside, read the live geometry as flows and adapt the layout instead of
assuming a form factor:

```kotlin
val ar = appDim.collectAspectRatio().value        // width / height
val dp = appDim.collectDpSize().value             // DpSize of the drawable area
if (ar >= 1.0f) WideLayout() else NarrowLayout()  // desktop/landscape vs phone/portrait
```

The library's own buttons already use `appDim` internally — e.g. `IconTextButton`
shows icon + full label when wide, a short label when narrower, and icon-only when
very narrow — so they degrade gracefully without extra work from you.

### Pattern 2: Define a `DesignScheme` for your app (light + dark)

Subclass `DesignScheme` to add your app's tokens, then build a light and a dark
instance. `DesignScheme`'s base fields cover buttons (`buttonShape`, `buttonBkg:
Brush`, `buttonContentColor`, `buttonTextStyle`) and text (`normalTextStyle`),
plus `alwaysLight`/`alwaysDark` (colors that must stay light/dark regardless of
theme — needed for things like QR codes).

```kotlin
class AppDesignScheme(
    val windowBackground: Color,
    val bodyBackground: Color,
    val bodyOutline: Color,
    val bodyText: Color,
    val bodyTextEmphasis: Color,
    val titleTextStyle: TextStyle,
    val bodyTextStyle: TextStyle,
    buttonBkg: Brush,
    buttonContentColor: Color,
) : DesignScheme(
    buttonShape = RoundedCornerShape(10.dp),
    buttonBkg = buttonBkg,
    buttonContentColor = buttonContentColor,
    buttonTextStyle = TextStyle(fontSize = 18.sp),
    normalTextStyle = TextStyle(fontSize = 14.sp),
)
```

See `designSchemeTemplate.kt` in this folder for a complete, copy-pasteable
light+dark scaffold including the dark-mode flow and the sync to the global
`design` flow described in Pattern 4.

### Pattern 3: Derive the palette from a base color instead of hand-typing hexes

Pick one or two brand colors and *generate* the related shades. This is what makes
a scheme look coherent ("derived design scheme") rather than arbitrary.

```kotlin
val brand = Color(0xFF1A3B3A)                       // one brand color

val buttonBkg = Brush.linearGradient(               // a subtle two-stop gradient from the brand color
    listOf(
        brand.mix(Color.Yellow.mix(Color.White, 0.20f), 0.6f),
        brand.mix(Color.White, 0.20f),
    ),
    start = Offset(0f, 0f), end = Offset(50f, 100f),
)

val darkPanel  = brand.brightness(0.15f)            // same hue, very dark   (0 = black, 1 = white)
val lightPanel = brand.brightness(0.96f)            // same hue, near white
val accent     = brand.complementary()              // opposite hue for highlights
val faintLine  = brand.newAlpha(0.25f)              // translucent divider
```

The full toolkit (all `Color` extension functions): `mix(other, fraction)`,
`brightness(fraction)`, `normalize()` (push the brightest channel to full),
`newAlpha(a)`, `complementary()` (180° hue rotation), `inverse()`, and
`toHexString()` (handy when copying a tuned color back out of the design editor —
Pattern 8). For a solid-color "brush" where the API wants a `Brush`, use
`Brush.solidColor(color)`.

### Pattern 4: Drive the whole UI off the global `design` flow (and dark mode)

The library components render from the global `design: MutableStateFlow<DesignScheme>`.
Keep an app-level dark-mode flow, and update `design.value` whenever it flips, so
the library buttons restyle together with your own composables.

```kotlin
// app-level, persisted so the choice survives restarts:
val darkMode = MutableStateFlow(prefs.getBoolean("dark", true))

@Composable
fun InstallTheme() {
    LaunchedEffect(Unit) {
        darkMode.collectLatest { isDark ->
            design.value = if (isDark) darkDesign else lightDesign   // restyles ALL library components
        }
    }
}
```

In your own composables, read whichever scheme you keep and pull colors/styles
from it — never inline a literal:

```kotlin
val de = design.collectAsState().value as AppDesignScheme
Box(Modifier.fillMaxSize().background(de.windowBackground).padding(20.dp)) {
    Box(Modifier.background(de.bodyBackground, de.buttonShape)
                .border(2.dp, de.bodyOutline, de.buttonShape)) {
        BasicText("Balance", color = { de.bodyText }, style = de.titleTextStyle)
    }
}
```

Add a ready-made theme switch with `LightModeToggle` (a polished animated
sun/moon control):

```kotlin
val dm by darkMode.collectAsState()
LightModeToggle(darkmode = dm, onClickToggle = { darkMode.update { !it } })
```

Alternative pattern, preferred when the `as AppDesignScheme` casts get noisy: expose your scheme
to your own composables through a **`CompositionLocal`** and keep the global `design` flow as the
library-facing sync target. One `installTheme(dark)` function assigns the chosen scheme to both
(your `CompositionLocalProvider(localDesign provides scheme)` at the top of the tree, and
`design.value = scheme` for the library components); your screens then read
`localDesign.current.bodyText` with no cast, while `BasicButton`/`IconTextButton` restyle from
the flow as before. Because your scheme *subclasses* `DesignScheme`, the single instance carries
both the base button tokens and your app tokens — the two consumers just reach it by different
routes. (Both idioms are valid; the flow-only form in this pattern stays the simpler default.)

### Pattern 5: Compose screens from the library's themed components

Reach for these before writing a raw widget. They already read `design`, adapt to
`appDim`, and carry the Nexa look. (Full signatures in `componentReference.md`.)

- **Buttons** (`org.nexa.libnexaapp.compose`): `BasicButton(onClick, modifier) { content }`
  is the base — themed background/shape, adaptive padding. `RowButton` lays its
  content in a `Row`. `IconTextButton(icon, text, …)` pairs an icon with a label
  that collapses as the screen narrows (overloads take a `DrawableResource` or a
  `Painter`, and a `StringResource` or `String`+`shortText`). `ThinIconTextButton`
  is a slim variant sized to the text. `LottieButton` is an animated button
  (attach Lottie effects to hover/press/enable states); it is a `Composing` object
  — build it, then call `.compose()`.

```kotlin
BasicButton(onClick = { send() }) {
    BasicText("Send", color = { de.buttonContentColor }, style = de.buttonTextStyle)
}
IconTextButton(ico = Res.drawable.wallet, text = Res.string.connect) { connect() }
```

- **Wallet connect** (root package): `ConnectWalletButton(showIcons, aspectRatio,
  darkmode, onClickConnect, onClickDisconnect)` renders the connect/disconnect
  state automatically by observing `walletConnected`. Wire its callbacks to the
  TDPP flow in `nexa-wallet-connection`.

- **Load assets** (added in the same libnexaapp update that moves these components into the
  `compose` package — see the Mental-model evolution note): `LoadAssetsButton(showIcons,
  aspectRatio, darkmode, onClickLoad)` is `ConnectWalletButton`'s sibling for the token/NFT
  portfolio flow — it renders **only while `walletConnected` is true** (nothing otherwise), so
  you can drop it unconditionally into a header. Wire `onClickLoad` to trigger the `/assets`
  round trip — typically one client GET to `/api/wallet/assets`
  (`nexa-wallet-connection` § trigger routes; consumption in `nexa-tokens-and-groups` Pattern 8).

- **Amount entry** (root package): `NexaInputField(cryptoCurrency, fiatCurrency,
  exchangeRate, cryptoBalance, textColor, outlineColor, textSize, cryptoAmountFlow)`
  is a full dual crypto/fiat input — tap to swap which side is authoritative, with
  Clear / Thousand / Million / Billion / All assist buttons. It writes the parsed
  crypto amount into the `MutableStateFlow<BigDecimal>` you pass. For the
  `exchangeRate` argument, the client library ships a ready-made NEXA price feed:
  `getNexaExchangeRate(fiat, force = false) { rate, loadTime -> … }`
  (`org.nexa.libnexaapp.client`, `wallywalletorgapi.kt`) — it polls the public
  wallywallet.org price API with built-in throttling/caching; only the USD/USDT pair
  is currently supported, and `rate` is null on failure (keep your last good value).

  > **Mind the rate direction — the two APIs are inverted.** `getNexaExchangeRate` hands you the
  > **fiat price of 1 NEXA** (bid/ask midpoint, e.g. USD-per-NEXA), but `NexaInputField`'s
  > `exchangeRate` parameter is consumed as **crypto units per 1 fiat unit** (its conversion is
  > `crypto = fiat × rate`). Pass `1.0 / price`, not `price` — and treat `0.0` as "no rate yet"
  > (the field then leaves the fiat side blank rather than dividing by zero). Passing the
  > un-inverted value produces amounts wrong by the square of the price, which looks plausible
  > at first glance on a near-1.0 test rate.

  The same libnexaapp update adds an optional `supplementalButtonText: String? = null` tail
  parameter: a custom quick-fill assist button whose **label is the value it enters** (tapping it
  sets the input to that text and pushes it through the flow) — e.g. pass `"5000"` for a
  one-tap standard amount. It replaces the `All` button in flows where "the whole balance" isn't
  meaningful.

- **Open the wallet by link:** `launchApplink(link: String)` (`org.nexa.libnexaapp.compose`, an
  `expect`/`actual` — browser window on wasmJs, `Intent` on Android, desktop browser on JVM)
  follows a URL/app link. Its Nexa use: an "open in wallet / get a wallet" button next to the
  connect QR, launched with the same universal-link connect URI the QR encodes — the same-device
  alternative to scanning (`nexa-wallet-connection`).

- **Text and images:** prefer `BasicText` styled from your scheme over Material
  `Text`. Use `SvgImage(resource, assetName, modifier, tint)` for SVGs (it is an
  `expect/actual` because Android has no native SVG rendering). Use `icon(...)` /
  `img(...)` for drawable resources — see Pattern 7 for why these exist.

### Pattern 6: Resizable split panes with `vsash` / `hsash`

For desktop-style multi-pane layouts, the sash containers give you draggable,
collapsible dividers whose sizes **persist** (keyed by each child's `id()`):

```kotlin
val layout = vsash(sidebar, mainPanel, inspector)   // vertical dividers => columns
// hsash(...) for horizontal dividers => stacked rows
layout.compose()
```

`vsash`/`hsash` build a `CCSash` (fixed-dp spacing). `CCFracSash` is the
fraction-based variant (panes sized as a fraction of the container — better when
the window itself resizes). Both come from the `Composing` mini-framework
("object-oriented Compose": a layout that needs to *retain state* is an object
implementing `Composing` with a `compose()` method, rather than a bare
`@Composable`). The newer weighted-container support lets a pane take a `weight`
so it absorbs leftover space: `CCSash.add(obj, pos, weight = 0.6f)` (added in a
recent release) lays that pane out proportionally via `Modifier.weight` — but the
weight is a *starting* disposition, not a permanent one. The moment the user drags
a sash (or a persisted size for that pane exists in `prefs`), the measured size is
captured and the pane reverts to fixed-Dp sizing. Use sashes for desktop/web; on a
narrow phone an aspect-ratio branch (Pattern 1) to a single-column layout usually
reads better.

### Pattern 7: Resource, translation, and formatting helpers

- **Translatable text:** `xlat(Res.string.key)` resolves a string resource, and
  supports `$key` templating — `xlat(Res.string.greeting, "name" to user)` fills
  `"Hello $name"`. The library's text components call `xlat` on `StringResource`
  arguments for you.
- **Async-resource redraw workaround:** use the library's `icon(drawable, …)` and
  `img(drawable, …)` rather than calling `painterResource` directly inside an
  `Image`. On web (`wasmJs`), `painterResource` does **not** recompose when the
  image finishes loading asynchronously, so a plain `Image(painterResource(...))`
  can render blank; `icon`/`img` poll until the intrinsic size is known and then
  redraw. This is a real cross-platform trap, not a style preference.
- **Number formatting:** `Double.format(decimals)` gives fixed-decimal display
  (e.g. amounts). `pxToDp(px)` and `Dp.toPx()` convert between pixels and Dp when
  you must (e.g. matching a Lottie shape drawn in pixels). (A recent release fixed
  `Double.format` for large values — it previously overflowed `Int` when scaling.)
- **Runtime-fetched raster bytes (PNG/JPG — e.g. NFT artwork, downloaded icons):**
  the resource helpers above only cover *bundled* assets. For image **bytes fetched at
  runtime** (an NFT's card front served by your server per `nexa-tokens-and-groups`
  Pattern 8b, a remote icon), use Compose Multiplatform's own
  **`ByteArray.decodeToImageBitmap()`** (`org.jetbrains.compose.resources`,
  `@OptIn(ExperimentalResourceApi::class)`, available since CMP 1.7) — it decodes raster
  bytes into an `ImageBitmap` on **every** CMP target including `wasmJs` and Android, with
  no extra dependency (the `components-resources` artifact you already use for `Res`).
  Do **not** reach for libnexaapp's `makeImageBitmap(bytes, width, height, scaleMode)` in
  common code: it is a plain **JVM-only** function (compose `jvmMain`, Skia-based) with no
  wasm/Android/iOS counterpart, so client code calling it won't compile off the desktop.
  And don't hand-roll a skiko `expect/actual` raster decoder — `decodeToImageBitmap`
  superseded that need. Decode defensively and fall back for SVG payloads (SVG is not a
  raster format, so `decodeToImageBitmap` throws on it):

  ```kotlin
  // commonMain — render fetched media: raster → ImageBitmap, else try SVG, else placeholder
  val bitmap = remember(mediaBytes) {
      mediaBytes?.let { runCatching { it.decodeToImageBitmap() }.getOrNull() }
  }
  // For SVG bytes, render via the SVG path instead (the library's SvgImage covers *bundled*
  // SVG resources; string/byte-level SVG rendering is an app-level helper you write on the
  // same underlying platform SVG facilities — there is no library svgFromString).
  ```

### Pattern 8: Tune the scheme live with the design editor (development workflow)

On the JVM/desktop target the library ships a **design editor**: a reflective
panel that walks a `DesignScheme`'s color properties and gives you a color picker
for each, so you can adjust the palette while the app runs and immediately see the
effect. The workflow is: run the desktop build, open the editor, tune colors,
then copy the resulting values (`Color.toHexString()`) back into your
`DesignScheme` definitions so they are baked into the build. It is a design-time
tool (JVM-only — it depends on Kotlin reflection and a desktop color-picker), not
something you ship enabled in production.

### Pattern 9: Make foundation-based components accessible (you own this)

Because libnexaapp's components are built on Compose `foundation` (`BasicText`, the
custom `BasicButton`, `clickable` `Box`es) and **not** Material, they do **not** get
the automatic semantics, roles, and 48 dp touch targets that Material widgets inject.
Accessibility is therefore the developer's responsibility here in ways it isn't in a
Material app. The essentials:

- Declare a **role and click label** on every `BasicButton` / custom `clickable`:
  `Modifier.semantics { role = Role.Button }`, `clickable(onClickLabel = xlat(...))`.
- Give **meaningful images a description** — and note the library's `icon`/`img`
  helpers hardcode `contentDescription = null` (decorative), so wrap a meaningful one in
  `Modifier.semantics { contentDescription = xlat(...) }`.
- Keep **primary touch targets ≥ 48 dp**, even though the library deliberately shrinks
  some controls (assist buttons, sash handles) below that via
  `LocalMinimumInteractiveComponentSize` — fine for pointer/desktop, risky on touch.
- Build **contrast-safe** body/background pairs into your `DesignScheme` (WCAG AA: 4.5:1
  normal, 3:1 large), and **never signal state by color alone** — pair it with an icon or
  label. Keep QR/scannables on `alwaysLight`/`alwaysDark`.

Full guidance — semantics API, `mergeDescendants`/`clearAndSetSemantics`, custom actions,
the do/don't list — is in `accessibilityReference.md`.

### Pattern 10: Keep the UI correct and fast (Compose mechanics)

A few general Compose Multiplatform rules keep a Nexa screen from recomposing the whole
tree on every keystroke. They are true on every target the app runs on:

- **Keep logic out of composable bodies.** Parsing, validation, fee/amount math, and data
  loading belong in the state holder that feeds the screen (a `MutableStateFlow<ScreenState>`
  or a `flowConnector` flow — see `nexa-server-state-and-flows`), not inline. Store
  *canonical* values; derive display values at the presentation boundary.
- **Keep screen state stable**: immutable `data class` + immutable collections (a `var`,
  `MutableList`, or stored lambda defeats Compose's skipping). Use `mutableIntStateOf` /
  `mutableFloatStateOf` for UI-local primitives; reserve `rememberSaveable` for small
  UI-local state only.
- **Slice state**: collect screen state once near the top and pass each leaf only what it
  needs; reading the whole state high in the tree cascades recomposition to every child.
- **Defer fast-changing reads** to a later phase (`Modifier.offset { }` for layout-phase
  position, `Modifier.graphicsLayer { }` for draw-phase visual changes) instead of reading
  them in composition.
- **List keys**: always key `LazyColumn`/`LazyRow` items by a stable domain id (e.g.
  `key = { it.txid }`), keep scroll state local (`rememberLazyListState()`), and don't
  filter/sort inside the list body.

Depth — the three-phase model, the full side-effect table, `CompositionLocal` guidance, the
resources/localization model, `expect/actual` placement, testing, and a performance
anti-pattern table — is in `composeFundamentalsReference.md`.

### Pattern 11: Loading and refresh states that don't wreck the UI

A wallet/finance UI is a trust product; the screen must feel stable and never throw away
what the user is looking at. Rules:

- **Never wipe content to show a spinner on refresh.** Keep the last good content visible
  and show an inline "updating" affordance; a full-screen spinner that replaces a populated
  screen reads as data loss and causes layout jumps. Reserve spinners for first loads with
  no known layout; prefer a **skeleton** when the layout is known.
- **Never clear edited fields** (or the last good result) because a request is in flight or
  failed. Preserve user input across refreshes and errors.
- **Validate inline, next to the field**, as the user edits — don't scream errors on
  untouched fields, don't collapse layout when an error appears, and disable submit only
  when truly impossible (and make the reason visible).
- **Apply local state instantly**; debounce only the expensive async work. Compute cheap
  deterministic outputs (a local fee estimate) immediately and refine in the background.

```kotlin
@Composable fun ResultSlot(quote: Quote?, refreshing: Boolean) {
    // stable-height slot so nothing jumps; old content stays during refresh
    Box(Modifier.fillMaxWidth().heightIn(min = 180.dp)) {
        when {
            quote != null -> QuoteContent(quote, refreshing)   // keep content; show inline indicator
            refreshing    -> QuoteSkeleton()                    // known layout → skeleton, not spinner
            else          -> EmptyState()
        }
    }
}
```

## Common mistakes and anti-patterns

### Hardcoding colors and shapes per screen instead of using a `DesignScheme`

**Wrong:** every screen sprinkles `Color(0xFF202060)`, `RoundedCornerShape(8.dp)`,
and `18.sp` inline.
*Result: no two screens match, dark mode is impossible to add later, and a
rebrand means editing dozens of files. This is the single biggest cause of the
"ugly, inconsistent Nexa app" the design library exists to prevent.*

**Right:** put every color/shape/text-style token in a `DesignScheme` subclass,
build light + dark instances, and read them via `design.collectAsState()`.

### Importing the high-level components under the `compose` package

**Wrong:**
```kotlin
import org.nexa.libnexaapp.compose.ConnectWalletButton   // does not resolve
```
*`ConnectWalletButton`, `LightModeToggle`, and `NexaInputField` are in the root
(default) package.*

**Right:** import them unqualified — `import ConnectWalletButton`. (The buttons,
color utilities, and helpers *are* under `org.nexa.libnexaapp.compose`; only those
three high-level components are not.)

**Version caveat:** this anti-pattern applies to artifacts from before the package fix — a newer
libnexaapp update moves these components *into* `org.nexa.libnexaapp.compose`, flipping the
resolution the other way (the unqualified import stops resolving). See the API-evolution note in
the Mental model for how to tell which side your artifact is on.

### Dropping raw Material3 widgets onto a screen

**Wrong:** building primary UI from bare `androidx.compose.material3.Button` /
`Text` with default colors.
*The Material defaults are off-brand for a Nexa app, and Material3 is a heavy
dependency the library deliberately keeps to a minimum. Mixing default-Material
surfaces with the library's themed components is exactly the inconsistent look to
avoid.*

**Right:** use `BasicButton`/`IconTextButton`/… and `BasicText` styled from your
`DesignScheme`. Reach for a Material component only for something the library does
not provide, and even then style it from your scheme.

### Forgetting to wrap the app in `NexaApp { }`

**Wrong:** rendering screens directly, never installing `appDim`.
*`appDim`'s flows stay at their defaults, so every component that adapts to screen
size (the buttons, your own aspect-ratio branches) behaves as if the window were
square/1.0 aspect ratio. Layouts won't adapt between phone, desktop, and web.*

**Right:** wrap the whole UI tree once in `NexaApp { … }`.

### Updating only your own scheme and not the global `design` flow

**Wrong:** flipping dark mode by swapping the colors your own composables read,
but never reassigning `design.value`.
*Your panels go dark while every `BasicButton`/`IconTextButton` stays in the old
theme, because the library components read the global `design` flow, not your
app's scheme variable.*

**Right:** in the same place you switch your app scheme, set
`design.value = if (isDark) darkDesign else lightDesign` (Pattern 4).

### Calling `painterResource` directly for images that must appear on web

**Wrong:** `Image(painterResource(Res.drawable.logo), null)` as the only image
path.
*On `wasmJs` the painter can finish loading after first composition without
triggering a redraw, leaving a blank space.*

**Right:** use the library's `icon(...)` / `img(...)` (or `SvgImage` for SVGs),
which handle the async-load redraw.

### Decoding runtime image bytes with `makeImageBitmap` (or a hand-rolled skiko decoder)

**Wrong:** calling libnexaapp's `makeImageBitmap(bytes, w, h, mode)` from `commonMain`
to render fetched PNG/JPG bytes, or writing your own skiko `expect/actual` raster
decoder because "platform image work goes behind expect/actual".
*`makeImageBitmap` exists only in the compose library's JVM source set — the wasmJs and
Android targets won't compile against it — and a hand-rolled decoder duplicates what CMP
already ships.*

**Right:** `ByteArray.decodeToImageBitmap()` from `org.jetbrains.compose.resources`
(CMP ≥ 1.7) in common code, wrapped in `runCatching` with an SVG/placeholder fallback
(Pattern 7). Reserve `makeImageBitmap` for desktop-only code that needs its scaling modes.

### Hardcoding a phone or a desktop layout

**Wrong:** a single fixed layout with pixel sizes tuned for one device.
*It will be cramped on a phone or sparse on a desktop, and unusable on web at an
unexpected window size.*

**Right:** branch on `appDim.collectAspectRatio()` / `collectDpSize()` (Pattern 1)
and use weighted/`fillMax*` modifiers and sashes so the layout flexes.

### Assuming a `BasicButton`/`BasicText` is accessible like a Material widget

**Wrong:** a screen full of `BasicButton`s and `clickable` `Box`es with no
`role`, no click label, and meaningful icons pushed through `icon(...)` (which
passes `contentDescription = null`).
*Foundation primitives carry no semantics, so a screen reader sees unlabeled,
role-less tap targets and announces nothing useful.*

**Right:** add `Modifier.semantics { role = Role.Button }` + `onClickLabel`, and
describe meaningful images yourself (Pattern 9 / `accessibilityReference.md`).

### Shrinking a primary touch target below 48 dp

**Wrong:** reusing the library's compact controls (or its
`LocalMinimumInteractiveComponentSize` override) for a primary action a finger must
hit on a phone.
*The library makes assist buttons and sash handles small on purpose for desktop
density; that's too small to reliably tap on touch.*

**Right:** keep primary touch actions ≥ 48 dp (gate compact controls on
`appDim`/`isMobile()`), per Pattern 9.

### Signalling state with color alone

**Wrong:** a status dot that is green when confirmed and red when pending, with no
other cue.
*Colorblind users and grayscale contexts can't tell them apart, and themed colors
may not even clear the contrast minimum.*

**Right:** pair the color with an icon or text label, and build contrast-safe pairs
into the `DesignScheme` (Pattern 9).

### Putting business logic in a composable body

**Wrong:** parsing amounts, computing fees, or loading data inline in a
`@Composable`.
*It forks the source of truth, can't be unit-tested, and re-runs on every
recomposition.*

**Right:** keep it in the state holder that feeds the screen (a
`MutableStateFlow<ScreenState>` / `flowConnector` flow); the composable only renders
canonical state (Pattern 10).

### Unstable screen state / un-keyed lists

**Wrong:** storing a `MutableList` or a lambda in screen state, or a `LazyColumn`
with no `key`.
*Mutable state defeats Compose's skipping (everything recomposes), and missing keys
make item state jump and animations break when the list changes.*

**Right:** immutable `data class` + immutable collections; key list items by a
stable domain id; keep scroll state local (Pattern 10 / `composeFundamentalsReference.md`).

### Wiping the screen with a full-screen spinner on refresh

**Wrong:** replacing populated content with a `CircularProgressIndicator` whenever a
refresh is in flight, or clearing edited fields on error.
*It reads as data loss, jumps the layout, and destroys user trust in a finance UI.*

**Right:** keep the last good content with an inline "updating" affordance, preserve
user input, and use a stable-height slot (Pattern 11).

## Security considerations

- **The UI is a renderer, not a trust boundary.** Never gate a security decision
  on UI state alone. Whether a wallet is connected (`ConnectWalletButton` /
  `walletConnected`), what amount is shown in a `NexaInputField`, or what a screen
  displays must all be re-validated server-side / against the chain before any
  value moves. Confirmation-depth and amount checks belong in the transaction
  layer (`nexa-transaction-construction`), not the button's `onClick`.
- **Display formatting is not on-chain truth.** `Double.format` and a token's
  display decimals are presentation only; do value math on the raw integer
  on-chain quantities (see `nexa-tokens-and-groups`). A UI that scales an amount
  for display must not feed the scaled number back into a send.
- **Respect `alwaysLight`/`alwaysDark` for machine-read graphics.** QR codes and
  similar scannable elements must keep real light/dark contrast regardless of
  theme; render them from `design`'s `alwaysLight`/`alwaysDark` colors, not from
  themed body colors, or a dark-mode QR can become unscannable. Two further QR
  display rules: keep **dark modules on a light background** — an inverted
  ("reverse") QR may scan on your phone and fail on plenty of others — and keep the
  **quiet zone** (real padding of the light background around the modules; don't
  crop or decorate up to the module edge).
- **Ship the design editor disabled.** It is a JVM development tool that uses
  reflection to mutate the live scheme; keep it behind a debug flag and out of
  production builds.
- **Localize via `xlat`, and don't put secrets in resources.** UI string
  resources are shipped in the client bundle (especially on web); treat them as
  public.

## Related skills and references

- `nexa-server-state-and-flows` — the `flowConnector` reactive-state layer the UI
  binds to (global vs per-session flows over WebSocket). This skill renders;
  that one supplies the data — the screen state holder that Patterns 10–11 keep
  logic in is typically backed by a `flowConnector` flow. The compose library also
  re-exports a client-side `flowConnector` binding for Compose.
- `nexa-wallet-connection` — the TDPP/nexid protocol behind `ConnectWalletButton`:
  connect/login URIs, QR generation, and the HTTP callbacks the button's
  `onClickConnect` kicks off.
- `nexa-project-setup` — adding the `org.nexa.libnexaapp:compose` dependency, the
  Compose Multiplatform plugin/targets, and the Android `onCreate` init the
  library needs.
- `nexa-tokens-and-groups` — token display decimals vs on-chain integer amounts,
  relevant whenever a `NexaInputField` or balance view shows a token.
- `nexa-transaction-construction` — where amounts entered in the UI actually get
  funded/signed/broadcast, and the finality rules a UI must respect before
  showing "paid".

### Supporting files in this folder

- `designSchemeTemplate.kt` — a complete copy-pasteable light+dark
  `AppDesignScheme` scaffold: base-color derivation via the color utilities, a
  persisted `darkMode` flow, and the sync to the global `design` flow.
- `componentReference.md` — the component/utility catalog: every button overload,
  the three root-package components, the color extension functions, the `appDim`
  surface, the sash containers, and the resource/format helpers, with signatures.
- `accessibilityReference.md` — making foundation-based components accessible: why
  it falls on you (no Material auto-semantics), content descriptions (and the
  `icon`/`img` `null`-description trap), the semantics API, touch targets (and the
  library's sub-48dp override), contrast, and color-not-alone. Backs Pattern 9.
- `composeFundamentalsReference.md` — general Compose Multiplatform mechanics framed
  for libnexaapp: the three-phase model, keeping logic out of composables, state
  stability, the side-effect table, `CompositionLocal` vs the global `design`/`appDim`
  flows, lists/keys, resources/localization, `expect/actual` placement, testing, and a
  performance anti-pattern table. Backs Patterns 10–11.

### Supporting files in this folder (to be created)

- `examples/` — full worked screens: a themed dashboard with dark-mode toggle and
  wallet-connect header, a send screen built around `NexaInputField`, and a
  desktop multi-pane layout using `vsash`.