package info.bitcoinunlimited.www.wally.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import info.bitcoinunlimited.www.wally.*
import info.bitcoinunlimited.www.wally.ui.theme.wallyTranslucentPurple


@Composable
fun ThumbButton(icon: ImageVector, textRes: Int, modifier: Modifier = Modifier, color: Color = Color.White)
{
    Column(
      modifier = modifier.wrapContentHeight().wrapContentWidth(),
      horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(8.dp))
        Icon(icon, contentDescription = i18n(textRes), tint = color)
        Spacer(Modifier.height(4.dp))
        Text(i18n(textRes), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelSmall, color = color)
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
fun ThumbButtonFAB(pasteIcon: ImageVector = Icons.Outlined.ContentPaste, onResult: (String) -> Unit, onScanQr: () -> Unit, clipmgr: ClipboardManager = LocalClipboardManager.current)
{
    Row(
      modifier = Modifier.wrapContentHeight().fillMaxWidth(),
      horizontalArrangement = Arrangement.Center
    ) {
        Row(
          modifier = Modifier.wrapContentHeight()
            .wrapContentWidth()
            .shadow(elevation = 8.dp, shape = RoundedCornerShape(32.dp))
            .background(wallyTranslucentPurple, shape = RoundedCornerShape(32.dp)),
          horizontalArrangement = Arrangement.Center
        ) {
            val deadPadding = 3.dp
            val deadMod = Modifier.padding(start = deadPadding, end = deadPadding)
            val butMod = Modifier // Use this to see the hitboxes: .background(Color(0x20000000))
            Spacer(Modifier.width(6.dp))
            if (platform().hasGallery)
                Box(modifier = deadMod)
                {
                    ThumbButton(
                      icon = Icons.Outlined.DocumentScanner,
                      textRes = S.imageQr,
                      modifier = butMod.clickable {
                          ImageQrCode { qrContent ->
                              qrContent?.let {
                                  clearAlerts()
                                  onResult(it)
                              }
                          }
                      }.padding(start = 4.dp, end = 4.dp))
                }
            if (platform().hasQrScanner)
                Box(modifier = deadMod)
                {
                    ThumbButton(
                      icon = Icons.Outlined.QrCodeScanner,
                      textRes = S.scanQr,
                      modifier = butMod.clickable {
                          clearAlerts()
                          onScanQr()
                      }.padding(start = 8.dp, end = 8.dp))
                }
            if (!platform().usesMouse)
                Box(modifier = deadMod)
                {
                    ThumbButton(
                      icon = pasteIcon,
                      textRes = S.paste,
                      modifier = butMod.clickable {
                          clearAlerts()
                          val clipText = clipmgr.getText()?.text
                          if (clipText != null && clipText != "")
                              onResult(clipText)
                          else
                              displayNotice(S.pasteIsEmpty)
                      }.padding(start = 12.dp, end = 12.dp)
                    )
                }
            Spacer(Modifier.width(5.dp))
        }
    }
}