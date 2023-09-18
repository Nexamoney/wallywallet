package info.bitcoinunlimited.www.wally.ui.theme

import androidx.compose.runtime.Composable

@Composable
expect fun WallyTheme(
  darkTheme: Boolean,
  dynamicColor: Boolean,
  content: @Composable () -> Unit
)