package info.bitcoinunlimited.www.wally.ui2.views

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import org.jetbrains.skia.Rect
import org.jetbrains.skia.skottie.Animation
import org.jetbrains.skia.sksg.InvalidationController
import kotlin.math.roundToInt


@Composable
actual fun LoadingAnimationContent()
{
    // Please note that it's NOT a part of Compose itself, but API of unstable skiko library that is used under the hood.
    // See:
    // - https://github.com/JetBrains/compose-multiplatform/issues/362
    // - https://github.com/JetBrains/compose-multiplatform/issues/3152

    loadingAnimation?.let {
        val animation = Animation.makeFromString(it)
        InfiniteAnimation(animation, Modifier.fillMaxSize())
    }
}

@Composable
fun InfiniteAnimation(animation: Animation, modifier: Modifier)
{
    val infiniteTransition = rememberInfiniteTransition()
    val time by infiniteTransition.animateFloat(
      initialValue = 0f,
      targetValue = animation.duration,
      animationSpec = infiniteRepeatable(
        animation = tween((animation.duration * 1000).roundToInt(), easing = LinearEasing),
        repeatMode = RepeatMode.Restart
      )
    )
    val invalidationController = remember { InvalidationController() }

    /*
     FIXME: https://github.com/JetBrains/compose-multiplatform/issues/3149
      Animation type doesn't trigger re-drawing the canvas because of incorrect detection
      "stability" of external types.
      Adding _any_ mutable state into `drawIntoCanvas` scope resolves the issue.

      Workaround for iOS/Web: move this line into `drawIntoCanvas` block.
     */
    animation.seekFrameTime(time, invalidationController)
    Canvas(modifier) {
        drawIntoCanvas {
            animation.render(
              canvas = it.nativeCanvas,
              dst = Rect.makeWH(size.width, size.height)
            )
        }
    }
}
