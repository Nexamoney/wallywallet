package info.bitcoinunlimited.www.wally.ui2.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

val WallyRoundedCorner = 16.dp

val Shapes = Shapes(
    small = RoundedCornerShape(4.dp),
    medium = RoundedCornerShape(4.dp),
    large = RoundedCornerShape(0.dp)
)

fun Modifier.wallyTile(col: Color):Modifier
{
    return this.shadow(
  elevation = 4.dp,
  shape = RoundedCornerShape(WallyRoundedCorner),
  clip = false,
)
  .clip(RoundedCornerShape(WallyRoundedCorner))
  .background(col)
  .wrapContentHeight()
  .fillMaxWidth(0.95f)
  .background(
    Brush.linearGradient(
      colors = listOf(
        col,
        Color.White.copy(alpha = 0.2f)
      ),
      start = Offset(0f, 0f),
      end = Offset(Float.POSITIVE_INFINITY, 0f)
    )
  )
  .padding(
    horizontal = 8.dp,
    vertical = 8.dp
  )
}

@Composable
fun WallyDivider()
{
    HorizontalDivider(color = listDividerFg, thickness = 2.dp)
}

@Composable
fun WallyHalfDivider()
{
    Row {
        Spacer(Modifier.weight(0.33f))
        HorizontalDivider(Modifier.weight(0.33f), color = listDividerFg, thickness = 2.dp)
        Spacer(Modifier.weight(0.33f))
    }
}