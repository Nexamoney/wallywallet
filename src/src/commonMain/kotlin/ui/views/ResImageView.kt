package info.bitcoinunlimited.www.wally.ui.views

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import info.bitcoinunlimited.www.wally.ui.theme.MpIcon
import org.jetbrains.compose.resources.*


@OptIn(ExperimentalResourceApi::class, InternalResourceApi::class)
@Composable
fun ResImageView(resPath: String, modifier: Modifier, description: String? = null)
{
    if (resPath.endsWith(".xml", true) || resPath.endsWith(".png", true) )
    {
        val dr = DrawableResource(id = resPath, items = setOf(ResourceItem(offset = 0L, qualifiers = setOf(), path = resPath, size = 45L )))
        val tmp = painterResource(dr)
        Image(painter = tmp, contentDescription = description, modifier = modifier)
    }
    else
    {
        BoxWithConstraints(modifier) {
            @Composable fun Dp.dpToPx() = with(LocalDensity.current) { this@dpToPx.toPx().toInt() }
            val x = maxWidth.dpToPx()
            val y = maxHeight.dpToPx()
            val bmp = try
            {
                MpIcon(resPath, x,y)
            }
            catch (e: Exception)
            {
                null
            }
            if (bmp != null) Image(bmp, contentDescription = description, modifier = modifier, contentScale = ContentScale.Fit)
            else Spacer(modifier = modifier)
        }
    }
}
