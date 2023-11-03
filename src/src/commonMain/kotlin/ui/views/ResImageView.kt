package info.bitcoinunlimited.www.wally.ui.views

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.painterResource


@OptIn(ExperimentalResourceApi::class)
@Composable
fun ResImageView(resPath: String, modifier: Modifier, description: String? = null) {
    Image(
      painter = painterResource(resPath),
      contentDescription = description,
      modifier = modifier
    )
}
