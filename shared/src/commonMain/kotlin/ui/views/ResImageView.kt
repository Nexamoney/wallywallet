package info.bitcoinunlimited.www.wally.ui.views

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import info.bitcoinunlimited.www.wally.later
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.InternalResourceApi
import org.jetbrains.compose.resources.ResourceItem
import org.nexa.libnexakotlin.GetLog
import org.nexa.libnexakotlin.sourceLoc

private val LogIt = GetLog("wally.resimage")

@OptIn(InternalResourceApi::class)
@Composable
fun ResImageView(resPath: String, modifier: Modifier, description: String? = null)
{
    if (resPath.endsWith(".xml", true) || resPath.endsWith(".png", true) )
    {
        val dr = DrawableResource(id = resPath, items = setOf(ResourceItem(offset = 0L, qualifiers = setOf(), path = resPath, size = 45L )))
        //val tmp = painterResource(resPath, androidx.compose.ui.res.ResourceLoader.Default)
        val tmp = org.jetbrains.compose.resources.painterResource(dr)
        Image(painter = tmp, contentDescription = description, modifier = modifier.testTag("res_image"))
    }
    else
    {
        BoxWithConstraints(modifier) {
            @Composable fun Dp.dpToPx() = with(LocalDensity.current) { this@dpToPx.toPx().toInt() }
            val x = maxWidth.dpToPx()
            val y = maxHeight.dpToPx()
            val bmp = remember(resPath, x, y) { mutableStateOf<ImageBitmap?>(null) }
            LaunchedEffect(resPath, x, y) {
                later {
                    try
                    {
                        bmp.value = MpIcon(resPath, x, y)
                    }
                    catch (e: Exception)
                    {
                        LogIt.warning(sourceLoc() + ": icon decode of $resPath failed: $e")
                    }
                }
            }
            val b = bmp.value
            if (b != null) Image(b, contentDescription = description, modifier = modifier, contentScale = ContentScale.Fit)
            else Spacer(modifier = modifier)
        }
    }
}
