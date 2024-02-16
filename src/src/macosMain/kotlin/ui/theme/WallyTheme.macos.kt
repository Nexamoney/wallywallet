package info.bitcoinunlimited.www.wally.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import org.nexa.libnexakotlin.UnimplementedException

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