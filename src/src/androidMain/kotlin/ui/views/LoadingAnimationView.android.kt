package info.bitcoinunlimited.www.wally.ui.views

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import com.airbnb.lottie.compose.*


@Composable
actual fun LoadingAnimationContent()
{
    val spec = LottieCompositionSpec.JsonString(loadingAnimation)
    val composition by rememberLottieComposition(spec)
    val progress by animateLottieCompositionAsState(composition, iterations = LottieConstants.IterateForever)

    LottieAnimation(
      composition = composition,
      progress = progress
    )
}
