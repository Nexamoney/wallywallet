# Accessibility for a libnexaapp Compose UI

Accessibility is **more** the developer's job in a libnexaapp UI than in a typical
Compose app, for one structural reason: libnexaapp's components are built on Compose
`foundation` (`BasicText`, the custom `BasicButton`, `clickable` `Box`es), **not** on
Material. Material `Button`/`Switch`/`Icon` inject correct semantics, roles, and a 48dp
minimum touch target automatically; `foundation` primitives do **not**. So when you compose
from `BasicButton`, `SvgImage`, and bare `clickable` modifiers, **you own the semantics, the
roles, and the touch-target sizing** — nothing adds them for you.

## Content descriptions for images

Every meaningful image needs a text alternative; purely decorative imagery should be
explicitly null so a screen reader skips it.

- **Decorative** → `contentDescription = null`.
- **Meaningful** → a localized label (resolve via `xlat`, see the main skill's Pattern 7).

> **libnexaapp trap:** the library's `icon(drawable, …)` and `img(drawable, …)` helpers
> **hardcode `contentDescription = null`** (they exist to fix the web async-redraw issue,
> not to carry semantics). That is correct for a decorative glyph, but for a *meaningful*
> image do not rely on them to announce anything — attach a description yourself:

```kotlin
// Meaningful icon that conveys state — give it an accessible name via semantics:
Box(Modifier.semantics { contentDescription = xlat(Res.string.wallet_connected) }) {
    icon(Res.drawable.wallet)   // visual only; the Box carries the description
}
```

For `SvgImage(resource, assetName, modifier, tint)`, confirm whether your platform's actual
maps `assetName` to an accessibility description; if it does not, wrap it in a
`Modifier.semantics { contentDescription = … }` for meaningful art.

## The semantics API

Use `Modifier.semantics { }` to add or override what assistive tech reports.

| Property | Purpose | Example |
| --- | --- | --- |
| `contentDescription` | Screen-reader announcement | `"Receive address QR"` |
| `role` | Declare an interactive role | `Role.Button`, `Role.Switch`, `Role.Tab`, `Role.Image` |
| `stateDescription` | Current state | `"Connected"`, `"3 of 5"` |
| `heading()` | Mark a section heading | — |

Because a `BasicButton` is a styled `Box`, **declare its role and a click label yourself**:

```kotlin
BasicButton(
    onClick = { send() },
    modifier = Modifier.semantics { role = Role.Button },
) { BasicText("Send", color = { de.buttonContentColor }, style = de.buttonTextStyle) }

// A custom clickable surface (not a button component):
Box(Modifier
    .clickable(onClickLabel = xlat(Res.string.open_tx_details)) { open(tx.id) }
    .semantics { role = Role.Button }
) { /* … */ }
```

### Grouping vs replacing semantics

- `Modifier.semantics(mergeDescendants = true) { }` — collapse a logical cluster (icon +
  amount + ticker) into one announcement while keeping the child text.
- `Modifier.clearAndSetSemantics { contentDescription = … }` — replace verbose
  auto-generated descendant semantics with one custom string.

## Touch targets (a real libnexaapp caveat)

The minimum comfortable touch target is **48 × 48 dp**. Enforce it on custom interactive
elements with `Modifier.minimumInteractiveComponentSize()`.

> **libnexaapp deliberately shrinks some controls below the minimum.** Several library
> controls override `LocalMinimumInteractiveComponentSize` to ~0–1 dp so they can be small
> (the `NexaInputField` assist buttons are ~24 dp tall; the sash `Handle` is intentionally
> thin). That is fine for **pointer/desktop** density, but on **touch** form factors it makes
> those targets hard to hit. So: keep compact controls compact only where a pointer is
> available (gate on `appDim` / `isMobile()`), and make sure every **primary** action a
> finger must hit still meets 48 dp. Do not blanket-shrink interactive elements.

## Color and contrast

WCAG AA minimums: **4.5:1** for normal text, **3:1** for large text (≥18 sp, or ≥14 sp bold).

- Build contrast-safe pairs **into your `DesignScheme`** — pick body-text/background pairs
  that clear the ratio in *both* the light and dark scheme, and verify them (the design
  editor, main skill Pattern 8, makes this easy to eyeball). Deriving text via
  `brand.brightness(0.95f)` over a `brightness(0.10f)` background, as in the template, gives
  you headroom; a mid-brightness-on-mid-brightness pairing usually fails.
- **Never use color as the only signal.** Pair a status color with an icon or text label, so
  a colorblind user (or a grayscale screenshot) still reads it:

```kotlin
Row(verticalAlignment = Alignment.CenterVertically) {
    icon(if (confirmed) Res.drawable.done else Res.drawable.bug)   // shape carries meaning
    BasicText(if (confirmed) "Confirmed" else "Pending", color = { de.bodyText }, style = de.bodyStyle)
}
```

- Keep **QR codes and other machine-scanned graphics** on the scheme's `alwaysLight` /
  `alwaysDark` colors (main skill Security section), never on themed body colors — a
  dark-mode QR drawn in low-contrast theme colors can become unscannable.

## Where accessibility data lives

Keep accessibility **strings** out of your screen state: hold a **semantic key** (an enum,
or a `StringResource`) in the state your `flowConnector`/`MutableStateFlow` exposes, and
resolve it to a localized label with `xlat` **in the composable**. This keeps state
locale-independent and testable, and resolution at the presentation boundary (consistent
with how the main skill drives everything off the `design` flow).

For a row with several actions (favorite / share / delete), expose them as discoverable
named actions so a screen-reader user need not hunt for each control:

```kotlin
Modifier.semantics {
    customActions = listOf(
        CustomAccessibilityAction(xlat(Res.string.copy_address)) { copy(); true },
        CustomAccessibilityAction(xlat(Res.string.share))        { share(); true },
    )
}
```

## Do / Don't

**Do**
- Add `role` + a click label to every `BasicButton` / custom `clickable` surface (foundation
  gives you none for free).
- Give meaningful images a description via `semantics` (don't rely on `icon`/`img`, which pass
  `null`).
- Keep primary touch actions ≥ 48 dp even though the library lets you go smaller.
- Pair status color with an icon or text.
- Resolve a11y strings via `xlat`; keep semantic keys in state.

**Don't**
- Assume a `BasicButton`/`BasicText` announces itself like a Material component — it doesn't.
- Ship a meaningful image through `icon`/`img` and expect a screen reader to describe it.
- Convey state with color alone.
- Draw QR/scannable graphics in themed colors instead of `alwaysLight`/`alwaysDark`.
- Put localized accessibility text in your state holder — keep it in the UI layer.