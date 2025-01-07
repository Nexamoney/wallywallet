package info.bitcoinunlimited.www.wally.ui.theme

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import com.github.weisj.jsvg.parser.SVGLoader
import info.bitcoinunlimited.www.wally.getResourceFile
import info.bitcoinunlimited.www.wally.ui2.views.ResImageView
import io.ktor.http.*
import org.nexa.libnexakotlin.CannotLoadException
import org.nexa.libnexakotlin.UnimplementedException
import org.nexa.libnexakotlin.logThreadException
import java.awt.Graphics2D
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.min

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
