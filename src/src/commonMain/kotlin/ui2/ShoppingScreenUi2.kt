package info.bitcoinunlimited.www.wally.ui2

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import info.bitcoinunlimited.www.wally.*
import info.bitcoinunlimited.www.wally.ui2.theme.WallyDivider
import info.bitcoinunlimited.www.wally.ui2.theme.WallyShoppingRowColors
import info.bitcoinunlimited.www.wally.ui2.views.MpMediaView
import info.bitcoinunlimited.www.wally.ui2.views.WallyBoldText
import okio.FileNotFoundException

@Composable
fun ShoppingDestination.composeUi2()
{
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
                MpMediaView(null, it.readByteArray(), name) { mi, draw ->
                    draw(Modifier.size(64.dp).background(Color.Transparent))
                }
            }
        }
        Spacer(Modifier.width(10.dp))

        Column {
            Text(explain, Modifier.fillMaxWidth())
            if (sd.destinationType == DestinationType.OTHER)
                Button(onClick = { openUrl(sd.url) }) {
                    Text(sd.buttonText, style = MaterialTheme.typography.labelSmall.copy(color = Color.White))
                }
            else if (sd.destinationType == DestinationType.EXCHANGE)
                ClickableLink(sd.buttonText)
        }
    }
}

@Composable
fun ClickableLink(url: String) {
    val annotatedString = buildAnnotatedString {
        append(url)
        addStyle(
          style = SpanStyle(
            color = Color.Blue,
            textDecoration = TextDecoration.Underline,
            fontSize = 16.sp
          ),
          start = 0,
          end = url.length
        )
        addStringAnnotation(
          tag = "URL",
          annotation = url,
          start = 0,
          end = url.length
        )
    }

    Text(
      text = annotatedString,
      modifier = Modifier
        .padding(8.dp)
        .clickable {
            annotatedString.getStringAnnotations(tag = "URL", start = 0, end = url.length)
              .firstOrNull()?.let { annotation ->
                  openUrl(annotation.item) // Opens the URL in a browser
              }
        },
      style = TextStyle(
        fontSize = 16.sp,
        color = Color.Blue,
        textDecoration = TextDecoration.Underline
      )
    )
}

@Composable
fun ShoppingScreenUi2()
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
                    it.composeUi2()
                    //Text(it.explain, Modifier.fillMaxWidth())
                }
            }
        }
    }

}