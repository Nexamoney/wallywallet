# libnexaapp compose library — component & utility reference

Catalog of the public UI surface of `org.nexa.libnexaapp:compose`. Confirm exact
signatures against the resolved artifact before relying on a detail (see
`nexa-project-setup` § "Verifying API signatures"); this is a navigational map,
not a contract. Unless noted, everything is in package `org.nexa.libnexaapp.compose`.

## Theme

| Symbol | Form | Notes |
| --- | --- | --- |
| `DesignScheme` | `open class` | Base theme tokens: `alwaysLight`, `alwaysDark` (Colors that stay light/dark regardless of theme — for QR codes), `buttonShape: Shape`, `buttonContentColor: Color`, `buttonBkg: Brush`, `buttonTextStyle: TextStyle`, `normalTextStyle: TextStyle`. **Subclass it** to add app tokens. |
| `defaultDesignScheme` | `val DesignScheme` | The built-in default scheme (a brand-tinted gradient button). |
| `design` | `MutableStateFlow<DesignScheme>` | The active theme every library component reads. Reassign `.value` to restyle the whole UI. |

## Responsive layout (`fit.kt` / `init.kt`)

| Symbol | Form | Notes |
| --- | --- | --- |
| `NexaApp(ux)` | `@Composable` | Root wrapper; runs `appDim.monitor(ux)`. Wrap the whole UI in it once. |
| `appDim` | `val DimensionMonitor` | Global screen-geometry monitor. |
| `DimensionMonitor.monitor(ux)` | `@Composable` | Measures the drawable area (used by `NexaApp`). |
| `appDim.collectAspectRatio()` | `@Composable State<Float>` | width / height. |
| `appDim.collectDpSize()` | `@Composable State<DpSize>` | Drawable area in Dp. |
| `appDim.collectPxSize()` | `@Composable State<IntSize>` | Drawable area in pixels. |
| `appDim.collectDensity()` | `@Composable State<Density>` | Display density / font scale. |
| `defaultIconSize` | `MutableStateFlow<Dp>` | Default icon size for `IconTextButton` (defaults to 50.dp). |

## Buttons (`buttons.kt`)

| Symbol | Signature (abridged) | Notes |
| --- | --- | --- |
| `BasicButton` | `(onClick, modifier = Modifier, content: @Composable () -> Unit)` | Base themed button: reads `design` for shape/background; adaptive horizontal padding shrinks with width. |
| `RowButton` | `(onClick, modifier = Modifier, content: @Composable RowScope.() -> Unit)` | `BasicButton` whose content is laid out in a `Row`. |
| `IconTextButton` | `(ico: DrawableResource, text: StringResource, iconSize = defaultIconSize, modifier, tint, onClick)` | Icon + label; hides the label as the aspect ratio narrows (`appDim`). |
| `IconTextButton` | `(ico: Painter, text: StringResource, …)` | Same, taking a `Painter`. |
| `IconTextButton` | `(ico: DrawableResource, text: String, shortText: String, …)` | Shows `text` when wide, `shortText` when narrower, icon-only when very narrow. |
| `IconTextButton` | `(ico: Painter, text: String, shortText: String, …)` | Same, taking a `Painter`. |
| `ThinIconTextButton` | `(ico: DrawableResource, text: StringResource, modifier, textStyle, tint, onClick)` | Slim button ~50% taller than the text. |

## Lottie / animated buttons (`lottieButton.kt`)

| Symbol | Form | Notes |
| --- | --- | --- |
| `LottieButton` | `class(onClick, dynamics: LottieAnimations, content)` : `Composing` | Animated button. Configure `clip`, `contentPadding`, `backgroundBrush`/`backgroundClip`, `disabled: MutableStateFlow<String?>`. Call `.compose()` to render; `.refresh()` after changing dynamics. |
| `LottieAnimations` | `class` | Map of `AniEvents` → `LottiePlacedAnimation`. |
| `AniEvents` | `enum` | `isEnabled/isDisabled/isFocused`, transitions `onEnabled/onDisabled/onHovered/onUnhovered/onFocused/onUnfocused/onPress/onRelease`, `any`. |
| `LottiePlacedAnimation` | `data class` | A Lottie tied to an event with z-index, `ContentScale`, offset, repeat, detached flag. Constructors accept a `LottieComposition`, a `LottieCompositionSpec`, or a JSON `String`; `fromResource(...)` / `fromParameterizedResource(...)` load from bytes. |

`LottieButton` and the sash containers are part of the **`Composing`** mini-framework
(`composing.kt`): `interface Composing { @Composable fun compose(); fun id(): String? }`,
`Composer(id, run)` to wrap a plain `@Composable` as a `Composing`, and
`TriggeredRefresh` for objects that recompose on `.refresh()`.

## High-level components — **ROOT (default) package**

Import these UNQUALIFIED (`import ConnectWalletButton`), not under `…compose`.
**API evolution:** a newer libnexaapp update moves them *into* `org.nexa.libnexaapp.compose`
(adding `LoadAssetsButton` in the same change) — on those artifacts import them qualified; see
the SKILL.md Mental-model note for how to tell which side your artifact is on.

| Symbol | Signature (abridged) | Notes |
| --- | --- | --- |
| `LightModeToggle` | `(darkmode: Boolean, onClickToggle: () -> Unit)` | Animated sun/moon theme switch. |
| `ConnectWalletButton` | `(showIcons: Boolean = true, aspectRatio: Float, darkmode: Boolean, onClickConnect, onClickDisconnect)` | Observes `walletConnected` and renders connect vs disconnect state. Wire callbacks to `nexa-wallet-connection`. |
| `LoadAssetsButton` | `(showIcons: Boolean = true, aspectRatio: Float, darkmode: Boolean, onClickLoad: () -> Unit)` | Added with the package move. Renders **only while `walletConnected` is true**. Wire `onClickLoad` to the `/assets` trigger (`GET /api/wallet/assets` — `nexa-wallet-connection`). |
| `NexaInputField` | `(cryptoCurrency, fiatCurrency, exchangeRate: Double, cryptoBalance: Double, textColor, outlineColor, textSize: TextUnit, cryptoAmountFlow: MutableStateFlow<BigDecimal>, supplementalButtonText: String? = null)` | Dual crypto/fiat amount entry with swap and Clear/Thousand/Million/Billion/All assist buttons; writes the parsed crypto amount into `cryptoAmountFlow`. `supplementalButtonText` (added with the package move) is a quick-fill assist button whose label IS the value it enters — replaces `All` where a whole-balance fill isn't meaningful. |

For `NexaInputField`'s `exchangeRate`, the client library (re-exported by the compose artifact)
ships `getNexaExchangeRate(fiat, force = false, handleRate: (Double?, loadTime: Long) -> Unit)`
in `org.nexa.libnexaapp.client` — a throttled/cached poll of the public wallywallet.org price
API (USD/USDT pair only; `rate` null on failure). **Directions are inverted between the two
APIs:** the feed returns fiat-per-NEXA (the USD price of 1 NEXA, bid/ask midpoint), while
`exchangeRate` is consumed as crypto-per-fiat (`crypto = fiat × rate`) — pass `1.0 / price`.

## Color utilities (`color.kt`) — `Color` extensions

| Function | Returns | Meaning |
| --- | --- | --- |
| `Color.mix(color, fraction)` | `Color` | Blend `color` in at `fraction` (0..1). |
| `Color.brightness(fraction)` | `Color` | Same hue at brightness `fraction` (0 = black, 1 = white). |
| `Color.normalize()` | `Color` | Scale so the brightest channel is full (1f). |
| `Color.newAlpha(a)` | `Color` | Same color, alpha replaced (0 = transparent). |
| `Color.complementary()` | `Color` | 180° hue rotation (HSL). |
| `Color.inverse()` | `Color` | `(1-r, 1-g, 1-b)`. |
| `Color.toHexString()` | `String` | `0xAARRGGBB`-style hex (copy values out of the design editor). |
| `Hsl` | `data class(hue, saturation, lightness)` | Returned by the internal HSL conversion. |

## Brushes, images, text, formatting (`uiPrimitives.kt` / `useful.kt`)

| Symbol | Form | Notes |
| --- | --- | --- |
| `Brush.solidColor(color)` | `Brush` | A one-color brush where an API wants a `Brush` (`SolidColorBrush`). |
| `SvgImage(resource, assetName, modifier, tint)` | `@Composable expect` | SVG rendering across platforms (Android has no native SVG). |
| `icon(drawable, colorFilter, modifier)` | `@Composable` | Draw a drawable resource; polls for async load so it redraws (web `painterResource` doesn't). |
| `img(drawable, modifier)` | `@Composable` | Same as `icon` without a color filter. |
| `xlat(s: StringResource, vararg kw)` | `@Composable String` | Resolve a string resource; supports `$key` templating via `kw`. |
| `String % map` | `String` | `%`-style template fill: `"Hi $name" % mapOf("name" to "x")`. |
| `Double.format(decimals)` | `String` | Fixed-decimal formatting for display. (Fixed for large values in a recent release — previously overflowed `Int`.) |
| `makeImageBitmap(bytes, width, height, scaleMode)` | `ImageBitmap?` — **JVM-only** (`jvmMain`) | Skia-based raster decode+scale with a JVM-local `ScaleMode` enum. Not `expect/actual` — no wasm/Android/iOS counterpart. In `commonMain`, decode runtime bytes with CMP's `ByteArray.decodeToImageBitmap()` (`org.jetbrains.compose.resources`) instead. |
| `pxToDp(px)` / `Dp.toPx()` | `@Composable Dp` / `Int` | Pixel ↔ Dp conversion. |
| `epochMsToDateString(ms, short)` | `String` | Epoch millis → local date string. |
| `launchApplink(link: String)` | `expect fun` (`org.nexa.libnexaapp.compose`) | Follow a URL/app link: browser window on wasmJs, `Intent` on Android, desktop browser on JVM. The "open in wallet on this device" affordance next to a connect QR (`nexa-wallet-connection`). |
| `isMobile()` / `platform()` / `platformDebugString()` | `expect fun` | Platform info — use sparingly; prefer `appDim` for layout decisions. |

## Layout — sash split panes (`composing.kt`)

| Symbol | Form | Notes |
| --- | --- | --- |
| `vsash(vararg items: Composing)` | `CCSash` | Vertical dividers → resizable columns. |
| `hsash(vararg items: Composing)` | `CCSash` | Horizontal dividers → resizable stacked rows. |
| `CCSash(orientation, vararg items)` | `class` : `Composing` | Dp-spaced draggable/collapsible panes; sizes persist to `prefs` by child `id()`. Supports per-pane `weight` to absorb leftover space via `add(obj, pos, weight: Float? = null)` (recent release); the weight is initial-only — a user drag (or a persisted size) captures the measured size and reverts the pane to fixed Dp. |
| `CCFracSash(orientation)` | `class` : `Composing` | Fraction-based variant (panes sized as a fraction of the container — better when the window resizes). |
| `Handle(orientation, bkg, onClick, modifier, content)` | `@Composable` | The drag/snap handle drawn on a sash. |

## Design editor (`designEditor.kt`, JVM/desktop only)

Reflectively enumerates a `DesignScheme`'s color properties and presents a color
picker per property so you can tune the palette at runtime, then copy values
(`toHexString()`) back into your scheme. Depends on Kotlin reflection and a desktop
color-picker; **development tool only — keep it out of production builds.**