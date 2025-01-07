package info.bitcoinunlimited.www.wally.ui.theme

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap

@Composable
expect fun WallyTheme(
  darkTheme: Boolean,
  dynamicColor: Boolean,
  content: @Composable () -> Unit
)