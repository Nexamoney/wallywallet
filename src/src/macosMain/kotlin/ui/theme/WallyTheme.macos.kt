package info.bitcoinunlimited.www.wally.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import info.bitcoinunlimited.www.wally.ui.isSoftKeyboardShowing
import org.nexa.libnexakotlin.UnimplementedException

actual fun NativeSplash(start: Boolean): Boolean
{
    return false
}

actual fun UxInTextEntry(boolean: Boolean)
{
    // macos has no soft keyboard
}

@Composable
actual fun WallyTheme(
    darkTheme: Boolean,
    dynamicColor: Boolean,
    content: @Composable () -> Unit
) {
    MaterialTheme(
      colorScheme = if (darkTheme) DarkColorPalette else LightColorPalette,
      typography = Typography(),
      shapes = Shapes(),
      content = content
    )
}

@Composable actual fun MpMediaView(mediaData: ByteArray?, mediaUri: String?, wrapper: @Composable (MediaInfo, @Composable (Modifier?) -> Unit) -> Unit):Boolean
{
    return false
}

actual fun MpIcon(mediaUri: String, widthPx: Int, heightPx: Int): ImageBitmap
{
    throw UnimplementedException("icons on macos native")
}