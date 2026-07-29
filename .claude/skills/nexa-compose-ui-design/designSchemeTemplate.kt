/*
 * designSchemeTemplate.kt — a copy-pasteable light + dark theme scaffold for a Nexa app
 * built on libnexaapp's compose library (org.nexa.libnexaapp:compose).
 *
 * What this demonstrates (see nexa-compose-ui-design/SKILL.md):
 *   - Subclassing the open DesignScheme to add app-specific tokens.
 *   - DERIVING a coherent palette from one brand color with the color utilities
 *     (mix / brightness / newAlpha / complementary) instead of hand-typing hexes.
 *   - A persisted darkMode flow.
 *   - Syncing the GLOBAL `design` flow so the library's own components
 *     (BasicButton, IconTextButton, ...) restyle together with your composables.
 *
 * Replace `brand` with your real brand color and tune in the design editor (JVM).
 * No version numbers here on purpose — pin the dependency per nexa-project-setup.
 */

package com.example.app.ui   // <-- your app package

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update

// libnexaapp compose library:
import org.nexa.libnexaapp.compose.DesignScheme
import org.nexa.libnexaapp.compose.design            // global MutableStateFlow<DesignScheme>
import org.nexa.libnexaapp.compose.brightness
import org.nexa.libnexaapp.compose.complementary
import org.nexa.libnexaapp.compose.mix
import org.nexa.libnexaapp.compose.newAlpha
import org.nexa.libnexaapp.compose.solidColor

// Persisted preferences provided by libnexaapp's client module:
import org.nexa.libnexaapp.client.prefs

/**
 * Your app's scheme. Extends DesignScheme (which already defines the button + base-text
 * tokens the library components read) with the extra tokens YOUR composables need.
 */
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
    // base DesignScheme tokens the library's BasicButton/IconTextButton/etc. consume:
    buttonShape = RoundedCornerShape(10.dp),
    buttonBkg = buttonBkg,
    buttonContentColor = buttonContentColor,
    buttonTextStyle = TextStyle(fontSize = 18.sp),
    normalTextStyle = TextStyle(fontSize = 14.sp),
    // alwaysLight / alwaysDark default to White / Black — keep them for QR codes etc.
)

/** Build one scheme by DERIVING every shade from a single brand color + a dark flag. */
private fun schemeFrom(brand: Color, dark: Boolean): AppDesignScheme {
    val sans = FontFamily.SansSerif
    val title = TextStyle(fontSize = 32.sp, fontFamily = sans)
    val body = TextStyle(fontSize = 16.sp, fontFamily = sans)

    val window = if (dark) brand.brightness(0.10f) else brand.brightness(0.97f)
    val panel = if (dark) brand.brightness(0.16f) else brand.brightness(0.93f)
    val text = if (dark) brand.brightness(0.95f) else brand.brightness(0.20f)
    val outline = brand.newAlpha(if (dark) 0.45f else 0.30f)
    val accent = brand.complementary()

    // A subtle two-stop gradient from the brand color reads as "designed":
    val buttonBkg = Brush.linearGradient(
        listOf(brand.mix(Color.White, if (dark) 0.05f else 0.15f), brand.mix(Color.Black, 0.10f)),
        start = Offset(0f, 0f), end = Offset(50f, 100f),
    )

    return AppDesignScheme(
        windowBackground = window,
        bodyBackground = panel,
        bodyOutline = outline,
        bodyText = text,
        bodyTextEmphasis = accent,
        titleTextStyle = title.copy(color = text),
        bodyTextStyle = body.copy(color = text),
        buttonBkg = buttonBkg,
        buttonContentColor = if (dark) Color.White else Color.White,
    )
}

private val BRAND = Color(0xFF1A3B3A)               // <-- your brand color
val lightDesign = schemeFrom(BRAND, dark = false)
val darkDesign = schemeFrom(BRAND, dark = true)

/** Persisted so the user's choice survives restarts. */
val darkMode = MutableStateFlow(prefs.getBoolean("dark", true))

fun toggleDarkMode() = darkMode.update { !it }

/**
 * Call once near the top of your UI (inside NexaApp { ... }). It pushes the right scheme
 * into the GLOBAL `design` flow whenever dark mode changes, so the library's own
 * components restyle along with your composables. Read `design.collectAsState().value`
 * (cast to AppDesignScheme) in your screens.
 */
@Composable
fun InstallTheme() {
    LaunchedEffect(Unit) {
        darkMode.collectLatest { isDark ->
            design.value = if (isDark) darkDesign else lightDesign
            prefs.edit().put("dark", isDark).commit()
        }
    }
}