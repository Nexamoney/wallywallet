package info.bitcoinunlimited.www.wally.ui.views

import androidx.annotation.RawRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import com.airbnb.lottie.compose.*
import info.bitcoinunlimited.www.wally.Objectify
import org.nexa.libnexakotlin.appContext
import org.nexa.libnexakotlin.decodeUtf8
import org.nexa.libnexakotlin.launch
import java.io.File
import info.bitcoinunlimited.www.wally.*
import info.bitcoinunlimited.www.wally.R

@Composable
actual fun LoadingAnimationContent()
{
    val tmp = loadingAnimation
    if (tmp != null)
    {
        val spec = LottieCompositionSpec.JsonString(tmp)
        val composition by rememberLottieComposition(spec)
        val progress by animateLottieCompositionAsState(composition, iterations = LottieConstants.IterateForever)

        LottieAnimation(
          composition = composition,
          progress = progress
        )
    }
}
