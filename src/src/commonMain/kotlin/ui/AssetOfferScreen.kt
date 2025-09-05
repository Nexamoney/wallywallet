package info.bitcoinunlimited.www.wally.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ionspin.kotlin.bignum.decimal.toBigDecimal
import info.bitcoinunlimited.www.wally.*
import info.bitcoinunlimited.www.wally.ui.theme.WallyModalOutline
import info.bitcoinunlimited.www.wally.ui.views.MpMediaView
import io.github.alexzhirkevich.qrose.rememberQrCodePainter
import kotlinx.coroutines.flow.MutableStateFlow
import org.nexa.libnexakotlin.ChainSelector
import org.nexa.libnexakotlin.GetLog
import org.nexa.libnexakotlin.PayAddress
import org.nexa.libnexakotlin.TransactionHistory
import org.nexa.libnexakotlin.iTransaction
import org.nexa.libnexakotlin.rem

private val LogIt = GetLog("BU.wally.assetOfferScreen")

data class AssetOffer(
  val uri: String,
  val price: Long,
  val asset: AssetInfo,
  val fromAddress: PayAddress,
  val transaction: iTransaction,
  val assetQty: Long = 1L,
  val uniqueAsset: Boolean,
  val iconStr: String = if (asset.iconUri != null) asset.iconUri.toString() else "",
  val priceFormatted: String = formatAmount(price.toBigDecimal(), ChainSelector.NEXA)
)

class AssetOfferViewModel(initOffer: AssetOffer) : ViewModel()
{
    val offer = MutableStateFlow(initOffer)

    init {
        observePartialTransaction()
    }

    fun observePartialTransaction()
    {
        wallyApp!!.focusedAccount.value!!.wallet.setOnWalletChange { wallet, txhistory ->
            val txHistory: TransactionHistory? = txhistory?.first()
            val tx: iTransaction? = txHistory?.tx
            if (tx != null)
                purchaseComplete()
        }
    }

    fun purchaseComplete()
    {
        displayNotice(S.purchaseComplete, persistAcrossScreens = 2)
        nav.go(ScreenId.Home)
    }

    override fun onCleared()
    {
        super.onCleared()
    }
}

@Composable
fun AssetOfferScreen(offer: AssetOffer, viewModel: AssetOfferViewModel = viewModel { AssetOfferViewModel(offer) })
{
    val asset = offer.asset
    val nft = asset.nft
    val qrcodePainter = rememberQrCodePainter(offer.uri)
    val assetName = asset.nameObservable.collectAsState()
    val preferenceDB = getSharedPreferences(TEST_PREF + i18n(S.preferenceFileName), PREF_MODE_PRIVATE)
    val devMode = preferenceDB.getBoolean(DEV_MODE_PREF, false)
    val name = (if ((nft != null) && (nft.title.length > 0)) nft.title else assetName.value) ?: ""
    // If the offer is a unique non-fungle token/asset then the amount should not be displayed
    val buyText = if (offer.assetQty == 1L && offer.uniqueAsset)
        i18n(S.scanToBuyOne) % mapOf("name" to name)
    // Display the amount if the offer is for a fungible token/asset
    else
        i18n(S.scanToBuySeveral) % mapOf("amount" to  asset.tokenDecimalFromFinestUnit(offer.assetQty).toPlainString(), "name" to name)

    // If the user leaves without the offer being taken, release the inputs back into the pool of usable UTXOs
    nav.onDepart {
        wallyApp!!.focusedAccount.value!!.wallet.abortTransaction(offer.transaction)
    }

    val mod = if (devMode)
    {
        Modifier
            .verticalScroll(rememberScrollState())
    }
    else
    {
        Modifier
    }
    LaunchedEffect(offer) {
        viewModel.offer.value = offer
        viewModel.observePartialTransaction()
    }

    Column(
      modifier = mod
        .wrapContentHeight()
        .fillMaxWidth()
        .padding(start = 48.dp, end = 48.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.Top
    ) {
        Spacer(Modifier.height(16.dp))
        Text(
          buyText,
          style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
          color = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(16.dp))
        Text(
          i18n(S.nexaOfferAmount) % mapOf("amount" to offer.priceFormatted),
          style = MaterialTheme.typography.headlineMedium,
          color = MaterialTheme.colorScheme.primary
        )
        if (devMode)
        {
            Text("Group ID", fontWeight = FontWeight.Bold)
            Text(asset.groupId.toString())
            val mod = Modifier.clickable { setTextClipboard(offer.uri) }
            Text("Offer URI", fontWeight = FontWeight.Bold, modifier = mod)
            Text(offer.uri, modifier = mod)
            val mod2 = Modifier.clickable { setTextClipboard(offer.transaction.id.toString()) }
            Text("Transaction ID", modifier = mod2)
            Text(offer.transaction.id.toString(), modifier = mod2)
        }
        Spacer(Modifier.height(16.dp))
        Image(
          painter = qrcodePainter,
          contentDescription = i18n(S.QrCode),
          modifier = Modifier
            .aspectRatio(1f) // Keeps the image square
            .background(Color.White)  // QR codes MUST have a white background and darker pixels, NOT the opposite (and yes this is to the spec)
            .testTag("offerQrCode")
            .clickable { setTextClipboard(offer.uri) }
        )

        Spacer(Modifier.height(16.dp))

        MpMediaView(null, asset.iconBytes, asset.iconUri.toString(), hideMusicView = true) { mi, draw ->
            // Fill the media available space's x or y with the media, but draw a nice box around that space.
            // Its is amazing that this is so hard.
            // My approach is to determine the aspect ratio (x/y)of the image, and the aspect ratio of the available space.
            // If the image AR is > the space AR, then the image is relatively wider than the space so we should fill max width, and
            // set the height as appropriate.  Otherwise do the equivalent but fill max height

            val ar = mi.width.toFloat()/mi.height.toFloat()
            val surfShape = RoundedCornerShape(20.dp)
            BoxWithConstraints(Modifier.fillMaxWidth().wrapContentHeight()) {
                // maxWidth and maxHeight provide the screen size
                // min W and H appears to provide not 0dp which makes sense but is trivial, but the minimum size of the Box with the modifiers
                // applied, in this case fillMaxSize(), so the size of the view
                val spaceAr = this.minWidth/this.minHeight

                val mod = if (ar >= spaceAr)  // media is wider than the space I have to show it in
                    Modifier.fillMaxWidth().aspectRatio(ar)
                else
                    Modifier.fillMaxHeight().aspectRatio(ar)  // media is taller than the space I have to show it in

                Surface(shape = surfShape, modifier = mod.align(Alignment.Center).border(WallyModalOutline, surfShape))
                {
                    draw(null)
                }
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}