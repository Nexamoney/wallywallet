package info.bitcoinunlimited.www.wally.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import info.bitcoinunlimited.www.wally.*
import info.bitcoinunlimited.www.wally.ui.theme.*
import info.bitcoinunlimited.www.wally.ui.views.ResImageView
import okio.FileNotFoundException

@Composable
fun ShoppingDestination.compose()
{
    val uriHandler = LocalUriHandler.current
    val sd = this
    Row {
        val name = sd.icon

        if (name != null)
        {
            val imageBytes = try
            {
                getResourceFile(name)
            }
            catch(e: FileNotFoundException)
            {
                null
            }
            imageBytes?.let {
                MpMediaView(it.readByteArray(), name) { mi, draw ->
                    draw(Modifier.size(64.dp).background(Color.Transparent))
                }
            }
        }
        Spacer(Modifier.width(10.dp))

        Column {
            Text(explain, Modifier.fillMaxWidth())
            WallyBoringTextButton(sd.buttonText) {
                uriHandler.openUri(sd.url)
            }
        }
    }
}


@Composable
fun ShoppingScreen(nav: ScreenNav)
{
    Column(
      modifier = Modifier.fillMaxWidth().fillMaxHeight(1f),
      horizontalAlignment = Alignment.Start,
      verticalArrangement = Arrangement.SpaceEvenly)
    {
        WallyBoldText(S.ShoppingWarning)
        WallyDivider()

        LazyColumn(modifier = Modifier.fillMaxWidth().fillMaxHeight(1f)) {
            itemsIndexed(initialShopping) { index, it ->
                // padding here adds a small gap between each entry that is not filled with the row color
                Spacer(Modifier.fillMaxWidth().padding(0.dp, 1.dp))
                // This padding adds a little margin between the content and the background color
                Box(Modifier.fillMaxWidth().background(WallyShoppingRowColors[index % WallyShoppingRowColors.size]).padding(2.dp, 2.dp)) {
                    it.compose()
                    //Text(it.explain, Modifier.fillMaxWidth())
                }
            }
        }
    }

}