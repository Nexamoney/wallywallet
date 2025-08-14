package info.bitcoinunlimited.www.wally.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import info.bitcoinunlimited.www.wally.*
import info.bitcoinunlimited.www.wally.ui.theme.WallyDivider
import info.bitcoinunlimited.www.wally.ui.views.WallyNumericInputFieldBalance
import kotlinx.coroutines.flow.MutableStateFlow
import org.nexa.libnexakotlin.*

private val LogIt = GetLog("BU.wally.createAssetOffer")


class CreateAssetOfferViewModel(apc: AssetPerAccount): ViewModel() {
    val asset: MutableStateFlow<AssetInfo> = MutableStateFlow(apc.assetInfo)
    val name: MutableStateFlow<String> = MutableStateFlow((if ((asset.value.nft != null) && (asset.value.nft?.title?.isNotEmpty() == true)) asset.value.nft?.title else asset.value.nameObservable.value ?: apc.groupInfo.groupId.toStringNoPrefix()) ?: "")
    val uniqueAsset :MutableStateFlow<Boolean> = MutableStateFlow(apc.groupInfo.isSubgroup() && apc.groupInfo.tokenAmount == 1L)
    val amt: MutableStateFlow<String> = MutableStateFlow<String>(tokenAmountString(apc.groupInfo.tokenAmount, apc.assetInfo.tokenInfo?.genesisInfo?.decimal_places))
    val nexaPrice: MutableStateFlow<String> = MutableStateFlow("")
    val assetQty: MutableStateFlow<String> = MutableStateFlow("1")

    init {
        asset.value = apc.assetInfo
    }

    fun reset()
    {
        nexaPrice.value = ""
        assetQty.value = "1"
        amt.value = ""
        name.value = ""
    }

    data class OfferTx(
      val tx: iTransaction,
      val uri: String,
      val address: PayAddress
    )

    fun CommonWallet.createOfferTx(gid: GroupId, grpAmt: Long, nativeAmt: Long, MIN_CONFIRMS:Int = 0): OfferTx
    {
        // Create a transaction for whatever chain this wallet is running on
        val tx = txFor(chainSelector)
        // This is the address the buyer pays to.
        val myAddress = getNewAddress()
        // Add an output for this amount of tokens, just to get txCompleter to fund it.  I will delete this output so that the taker can supply it
        tx.add(txOutputFor(myAddress, grpAmt, gid))
        // Here I'm saying "complete this transaction by funding (adding inputs for) any tokens that are being sent", and just above I added an output to send some tokens.
        // So this call is going to populate the transaction with inputs that pull in the tokens needed, AND may add an token change payment to myself if it had to pull in too many.
        txCompleter(tx, MIN_CONFIRMS, TxCompletionFlags.FUND_GROUPS, changeAddress = myAddress)
        assert(tx.inputs.size > 0)
        // ok get rid of that output.  We CANNOT clear because txCompleter may have added an output of token change.
        tx.outputs.removeAt(0)
        // Now add the output we really want, which is a payment in native coin
        tx.add(txOutputFor(nativeAmt, myAddress))

        // Sign this transaction "partially"
        txCompleter(tx, MIN_CONFIRMS, TxCompletionFlags.PARTIAL or TxCompletionFlags.SIGN or TxCompletionFlags.BIND_OUTPUT_PARAMETERS, changeAddress = myAddress)
        // Now add an output to tell the "taker" how to grab the what was offered.
        // This ought to be optional; a wallet can analyze the transaction's inputs to see what's being offered.  But doing that requires doing a UTXO look up to find out what
        // assets the inputs are offering.  This is how we just tell the "taker" what's being offered.  Note that if we are lying, the tx will be bad so the "taker" is not trusting
        // us.
        tx.add(txOutputFor(chainSelector, dust(chainSelector), SatoshiScript.grouped(chainSelector, gid, grpAmt).add(OP.TMPL_SCRIPT)))

        // For legacy (BCH) compatibility reasons, the TDPP protocol requires a parameter "inamt" that says how much coin is being input to the transaction.  In Nexa, we could
        // just read that out of the inputs.
        var inAmt = 0L
        for (i in tx.inputs)
        {
            inAmt += i.spendable.amount
        }

        val tdppFlags = 0L  // No fancy "taker" flags are needed
        // use the applink style of URL
        val tdpp = "tdpp:///tx?chain=${chainToURI[tx.chainSelector]}&inamt=$inAmt&flags=$tdppFlags&tx=${tx.toHex()}"

        return OfferTx(tx, tdpp, myAddress)
    }

    fun createOffer()
    {
        // val tx = txFor(ChainSelector.NEXA)
        // val inputs = txInputFor()
        val groupId = asset.value.groupId

        val price = nexaPrice.value.toLongOrNull()
        if (price == null || price <= 0L)
        {
            displayWarning(i18n(S.setTheNexaPrice))
            return
        }
        val priceInSatoshis = price * 100
        val assetQty = assetQty.value.toLongOrNull()
        if (assetQty == null || assetQty <= 0L)
        {
            displayWarning(i18n(S.setTheAssetAmount))
            return
        }
        val account = wallyApp!!.focusedAccount.value!!.wallet

        try {
            val offer = account.createOfferTx(groupId, assetQty, priceInSatoshis)
            val offerUri = offer.uri
            val offerTx = offer.tx
            val offerAddress = offer.address

            if (nexaPrice.value.isEmpty()) return
            offerUri.let {
                val offer = AssetOffer(it, price, asset.value, offerAddress, offerTx, assetQty = assetQty, uniqueAsset = uniqueAsset.value)
                displayNotice(S.offerCreated, persistAcrossScreens = 2)
                nav.go(ScreenId.AssetOffer, data = offer)
            }
        } catch (e: WalletNotEnoughTokenBalanceException) {
            displayWarning(e.shortMsg ?: "")
        }
    }
}

@Composable
fun CreateAssetOfferScreen(asset: AssetPerAccount, viewModel: CreateAssetOfferViewModel = viewModel { CreateAssetOfferViewModel(asset) })
{
    val assetInfo = asset.assetInfo
    val nft = assetInfo.nft
    val group = asset.groupInfo.groupId.toStringNoPrefix()
    LaunchedEffect(asset)
    {
        if (viewModel.asset.value != asset.assetInfo)
            viewModel.reset()
        viewModel.asset.value = asset.assetInfo
        viewModel.uniqueAsset.value = asset.groupInfo.isSubgroup() && asset.groupInfo.tokenAmount == 1L
        viewModel.amt.value = tokenAmountString(asset.groupInfo.tokenAmount, asset.assetInfo.tokenInfo?.genesisInfo?.decimal_places)
        viewModel.name.value = (if ((nft != null) && (nft.title.isNotEmpty() == true)) nft.title else assetInfo.nameObservable.value ?: group)
    }

    CreateAssetOffer(viewModel)
}

@Composable
fun CreateAssetOffer(viewModel: CreateAssetOfferViewModel)
{
    val asset = viewModel.asset.collectAsState().value
    val uniqueAsset = viewModel.uniqueAsset.collectAsState().value
    val amountFocusRequester = remember { FocusRequester() }

    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(16.dp)
        .verticalScroll(rememberScrollState()),
      horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
          text = i18n(S.CreateAnOffer),
          style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
          color = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(16.dp))

        WallyDivider()

        Spacer(modifier = Modifier.height(16.dp))

        AssetView(asset, Modifier.weight(1f))

        Spacer(modifier = Modifier.height(16.dp))

        if (!uniqueAsset)
        {
            Text(i18n(S.AssetAmountName) % mapOf("amount" to viewModel.amt.collectAsState().value, "assetName" to viewModel.name.collectAsState().value))
            WallyNumericInputFieldAsset(
              amountString = viewModel.assetQty.collectAsState().value,
              label = i18n(S.amountPlain),
              placeholder = i18n(S.enterAmount) % mapOf("assetName" to (asset.name ?: "")),
              decimals = asset.tokenInfo?.genesisInfo?.decimal_places ?: 0,
              isReadOnly = false,
              hasIosDoneButton = true,
              onValueChange = {
                  viewModel.assetQty.value = it
              }
            )
        }

        WallyNumericInputFieldBalance(
          mod = Modifier.testTag("offerAmountInput"),
          amount = viewModel.nexaPrice.collectAsState().value,
          label = i18n(S.Price),
          placeholder = i18n(S.NexaPrice),
          isReadOnly = false,
          hasIosDoneButton = true,
          hasButtonRow = false,
          focusRequester = amountFocusRequester
        ) {
            viewModel.nexaPrice.value = it
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Button(
              onClick = {
                  viewModel.createOffer()
              },
              colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {

                Text(i18n(S.confirm))
            }
            OutlinedButton(
              onClick = {
                  viewModel.reset()
                  nav.back()
              }
            ) {
                Text(i18n(S.cancel))
            }
        }
    }
}
