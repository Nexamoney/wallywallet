package info.bitcoinunlimited.www.wally.ui.theme

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

val Shapes = Shapes(
    small = RoundedCornerShape(4.dp),
    medium = RoundedCornerShape(4.dp),
    large = RoundedCornerShape(0.dp)
)

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