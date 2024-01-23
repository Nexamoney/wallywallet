package info.bitcoinunlimited.www.wally.ui.views

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

var loadingAnimation:String? = null

@Composable
fun LoadingAnimation()
{
    Box(Modifier.size(50.dp)) {
        LoadingAnimationContent()
    }
}
@Composable
expect fun LoadingAnimationContent()
