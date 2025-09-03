package info.bitcoinunlimited.www.wally.ui.views

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import info.bitcoinunlimited.www.wally.*
import info.bitcoinunlimited.www.wally.ui.ScreenId
import info.bitcoinunlimited.www.wally.ui.nav
import info.bitcoinunlimited.www.wally.ui.theme.wallyPurple
import info.bitcoinunlimited.www.wally.ui.theme.wallyPurpleExtraLight
import org.nexa.libnexakotlin.*


class AssetListItemViewModel(): ViewModel()
{
    fun getHost (docUrl: String?): String?
    {
        if (docUrl != null)
        {
            val url = com.eygraber.uri.Url.parseOrNull(docUrl)
            val host = try
            {
                url?.host  // although host is supposedly not null, I can get "java.lang.IllegalArgumentException: Url requires a non-null host"
            }
            catch (e: IllegalArgumentException)
            {
                null
            }
            return host
        }
        return null
    }
}

@Composable
fun AssetListItem(asset: AssetPerAccount, tx: RecentTransactionUIData)
{
    val viewModel = viewModel { AssetListItemViewModel() }
    val assetInfo = asset.assetInfo
    val assetName = assetInfo.nameObservable.collectAsState().value
    val nft = assetInfo.nftObservable.collectAsState().value
    val name = (if ((nft != null) && (nft.title.isNotEmpty())) nft.title else assetName)

    ListItem(
      colors = ListItemDefaults.colors(
        containerColor = wallyPurpleExtraLight
      ),
      // Show receive or send
      leadingContent = {
          Row {
              Icon(
                tx.icon,
                tx.contentDescription,
              )
              Text(
                text = tx.type
              )
          }
      },
      // Show the asset information
      headlineContent = {
          Row(
            modifier = Modifier.fillMaxWidth(),
          ){
              Column(
                modifier = Modifier.weight(1f)
              ) {
                  nft?.series?.let {
                      Text(
                          text = it,
                          style = MaterialTheme.typography.labelMedium.copy(
                              color = wallyPurple
                          )
                      )
                  }
                  name?.let {
                      Text(
                          text = it,
                          style = MaterialTheme.typography.bodyLarge.copy(
                              color = wallyPurple,
                              fontWeight = FontWeight.Bold
                          )
                      )
                  }
                  viewModel.getHost(assetInfo.docUrl)?.let {
                      Text(
                          text = it,
                          style = MaterialTheme.typography.labelSmall.copy(
                              color = wallyPurple,
                              fontStyle = FontStyle.Italic
                          )
                      )
                  }
              }
          }
      },
      // Show the asset image and quantity
        trailingContent = {
            Column (
                modifier = Modifier
                    .wrapContentSize()
                    // If the user click on the asset image, go to the page that shows that asset in detail.
                    .clip(RoundedCornerShape(16.dp)).clickable {
                        nav.go(ScreenId.Assets, assetInfo.groupId.toByteArray())
                    },
              horizontalAlignment = Alignment.CenterHorizontally
            ) {
                var iconSize = 60.dp
                if (asset.groupInfo.tokenAmount != 1L)
                {
                    // Show asset quantity transferred
                    Text(
                      text = asset.tokenDecimalFromFinestUnit(asset.groupInfo.tokenAmount).toPlainString(),
                      fontWeight = FontWeight.Bold,
                      fontSize = 18.sp,
                      color = wallyPurple,
                    )
                    iconSize = 42.dp  // This assumes the user hasn't scaled.  But if the user is scaling the fonts its probably better for the user if we use more vertical space for each item
                }
                MpMediaView(assetInfo.iconImage, assetInfo.iconBytes, assetInfo.iconUri?.toString(), hideMusicView = true) { mi, draw ->
                    val m = Modifier.background(Color.Transparent).size(iconSize)
                    draw(m)
                }
            }
        }
    )
}

@Composable
fun RecentTransactionListItem(tx: RecentTransactionUIData)
{
    ListItem(
      colors = ListItemDefaults.colors(
        containerColor = wallyPurpleExtraLight
      ),
      // Show whether the Nexa was sent or received
      leadingContent = {
          Row {
              Icon(
                tx.icon,
                tx.contentDescription,
              )
              Text(
                text = tx.type
              )
          }
      },
      // Show Nexa transfer details like amount and date
      headlineContent = {
          Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
          ) {
              Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.End
              ) {
                  Row(
                      verticalAlignment = Alignment.CenterVertically
                  ) {
                      ResImageView("icons/nexa_icon.png", Modifier.size(16.dp), "Blockchain icon")
                      Spacer(Modifier.width(8.dp))
                      // Nexa transferred
                      Text(
                        text = tx.amount,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = wallyPurple,
                      )
                  }
                  Spacer(modifier = Modifier.height(4.dp))
                  // Date of transfer
                  Text(
                    text = tx.date,
                    fontSize = 12.sp,
                    color = wallyPurple
                  )
              }
          }
      }
    )
}

@Composable
fun AssetCarouselItemNameOverlay(name: String, maxWidth: Dp, modifier: Modifier = Modifier)
{
    Box(
      modifier = modifier
        .fillMaxHeight()
        .widthIn(max = maxWidth)
        .background(
          brush = Brush.verticalGradient(
            colors = listOf(Color.Transparent, Color.Black),
            startY = 50f,
            endY = 200f,
          )
        )
    ) {
        Text(
          text = name,
          color = Color.White,
          fontWeight = FontWeight.Bold,
          fontSize = MaterialTheme.typography.labelSmall.fontSize,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          modifier = Modifier.align(Alignment.BottomStart)
            .wrapContentWidth()
            .padding(start = 8.dp, bottom = 4.dp)
        )
    }
}

@Composable
fun AssetCarouselItem(asset: AssetInfo, hasNameOverLay: Boolean = false, leadSpacing: Dp = 0.dp)
{
    val iconImage = asset.iconImageState.collectAsState().value
    val nft = asset.nft
    val maxSize = 60.dp

    Box (
      modifier = Modifier.padding(leadSpacing, 0.dp, 0.dp,0.dp)
        .wrapContentSize()
        .clip(RoundedCornerShape(16.dp)).clickable {
            nav.go(ScreenId.Assets, asset.groupId.toByteArray())
        },
    ) {
        MpMediaView(iconImage, asset.iconBytes, asset.iconUri?.toString(), hideMusicView = true) { mi, draw ->
            val m = Modifier.background(Color.Transparent).size(maxSize).clickable {
                nav.go(ScreenId.Assets, asset.groupId.toByteArray())
            }
            draw(m)
        }
        if (hasNameOverLay)
            AssetCarouselItemNameOverlay(
              name = nft?.title ?: asset.name ?: "",
              maxWidth = maxSize,
              modifier = Modifier.matchParentSize().clickable {
                  nav.go(ScreenId.Assets, asset.groupId.toByteArray())
              }
            )
    }
}
