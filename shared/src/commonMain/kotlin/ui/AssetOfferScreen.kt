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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ionspin.kotlin.bignum.decimal.toBigDecimal
import info.bitcoinunlimited.www.wally.*
import info.bitcoinunlimited.www.wally.ui.theme.WallyModalOutline
import info.bitcoinunlimited.www.wally.ui.views.MpMediaView
import io.github.alexzhirkevich.qrose.options.QrErrorCorrectionLevel
import io.github.alexzhirkevich.qrose.rememberQrCodePainter
import kotlinx.coroutines.flow.MutableStateFlow
import org.nexa.assets.AssetInfo
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

abstract class AssetOfferViewModel (initOffer: AssetOffer): ViewModel()
{
    val offer = MutableStateFlow(initOffer)

    abstract fun observePartialTransaction()
    abstract fun purchaseComplete()
}

class AssetOfferViewModelImpl(initOffer: AssetOffer) : AssetOfferViewModel(initOffer)
{
    init {
        observePartialTransaction()
    }

    override fun observePartialTransaction()
    {
        wallyApp!!.focusedAccount.value!!.wallet.setOnWalletChange { wallet, txhistory ->
            val txHistory: TransactionHistory? = txhistory?.first()
            val tx: iTransaction? = txHistory?.tx
            if (tx != null)
                purchaseComplete()
        }
    }

    override fun purchaseComplete()
    {
        displayNotice(S.purchaseComplete, persistAcrossScreens = 2)
        nav.go(ScreenId.Home)
    }

    override fun onCleared()
    {
        super.onCleared()
    }
}

class AssetOfferViewModelFake(initOffer: AssetOffer) : AssetOfferViewModel(initOffer)
{
    override fun observePartialTransaction()
    {

    }

    override fun purchaseComplete()
    {

    }
}

@Composable
fun AssetOfferScreen(nav: ScreenNav, offer: AssetOffer, viewModel: AssetOfferViewModel = viewModel { AssetOfferViewModelImpl(offer) })
{
    val asset = offer.asset
    val nft = asset.nft
    if (offer.uri.length > 2953)
    {
        LogIt.info("Asset Offer is too long: ${offer.uri.length}\n${offer.uri}")
        nav.back()
        displayError(S.ConsolidationNeeded)
        return
    }
    val qrcodePainter = rememberQrCodePainter(offer.uri, errorCorrectionLevel = QrErrorCorrectionLevel.Low)
    val assetName = asset.nameObservable.collectAsState()
    val preferenceDB = getSharedPreferences(TEST_PREF + PREFERENCE_FILE_NAME, PREF_MODE_PRIVATE)
    val devMode = preferenceDB.getBoolean(DEV_MODE_PREF, false)
    val name = (if ((nft != null) && (nft.title.length > 0)) nft.title else assetName.value) ?: ""
    // If the offer is a unique non-fungible token/asset then the amount should not be displayed
    val itemText = if (offer.assetQty == 1L && offer.uniqueAsset)
        name
    // Display the amount if the offer is for a fungible token/asset
    else
        asset.tokenDecimalFromFinestUnit(offer.assetQty).toPlainString() + " " + name

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
        MpMediaView(null, asset.iconBytes.collectAsState().value, asset.iconUri.toString(), hideMusicView = true) { mi, draw ->
            // aspectRatio() sizes the Surface to the largest media-shaped rect that fits, so the border hugs the media
            val ar = if (mi.width > 0 && mi.height > 0) mi.width.toFloat()/mi.height.toFloat() else 1f
            val surfShape = RoundedCornerShape(20.dp)
            Box(Modifier.fillMaxWidth().height(150.dp), contentAlignment = Alignment.Center) {
                Surface(shape = surfShape, border = WallyModalOutline, modifier = Modifier.aspectRatio(ar))
                {
                    draw(null)
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        Text(
          i18n(S.scanToBuy),
          style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
          color = MaterialTheme.colorScheme.primary,
          textAlign = TextAlign.Center
        )
        Text(
          itemText,
          style = MaterialTheme.typography.headlineMedium,
          color = MaterialTheme.colorScheme.primary,
          textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(16.dp))
        Text(
          i18n(S.nexaOfferAmount) % mapOf("amount" to offer.priceFormatted),
          style = MaterialTheme.typography.headlineMedium,
          color = MaterialTheme.colorScheme.primary,
          textAlign = TextAlign.Center
        )
        if (devMode)
        {
            val mod = Modifier.clickable { setTextClipboard(asset.groupId.toString())}
            Text("Group ID", fontWeight = FontWeight.Bold, modifier = mod)
            Text(asset.groupId.toString(), modifier = mod )
            val mod1 = Modifier.clickable { setTextClipboard(offer.uri) }
            Text("Offer URI", fontWeight = FontWeight.Bold, modifier = mod1)
            Text(offer.uri, modifier = mod1)
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
    }
}