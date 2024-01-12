package info.bitcoinunlimited.www.wally

import android.graphics.Bitmap
import android.graphics.Paint
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import info.bitcoinunlimited.www.wally.databinding.AccountListItemBinding
import java.time.Instant

import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

import com.ionspin.kotlin.bignum.decimal.*
import info.bitcoinunlimited.www.wally.ui.triggerAccountsChanged
import info.bitcoinunlimited.www.wally.ui.views.uiData
import org.nexa.libnexakotlin.*
//import org.nexa.walletoperations.*

private val LogIt = GetLog("BU.wally.AccountAndroid")

private val uiBindingMap = mutableMapOf<Account, AccountListItemBinding?>()
private val currentReceiveQRMap = mutableMapOf<Account, Bitmap?>()
private val updateReceiveAddressUIMap = mutableMapOf<Account, ((Account) -> Unit)?>()

/** Store the PIN encoded.  However, note that for short PINs a dictionary attack is very feasible */
actual fun EncodePIN(actName: String, pin: String, size: Int): ByteArray
{
    val salt = "wally pin " + actName
    val skf = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA512")
    val secretkey = PBEKeySpec(pin.toCharArray(), salt.toByteArray(), 2048, 512)
    val seed = skf.generateSecret(secretkey)
    return seed.encoded.slice(IntRange(0, size - 1)).toByteArray()
}


var Account.uiBinding: AccountListItemBinding?
    get() = uiBindingMap[this]
    set(value) {
        uiBindingMap[this] = value
    }

var Account.currentReceiveQR: Bitmap?
    get() = currentReceiveQRMap[this]
    set(value) {
        currentReceiveQRMap[this] = value
    }

// If this coin's receive address is shown on-screen, this is not null
var Account.updateReceiveAddressUI: ((Account) -> Unit)?
    get() = updateReceiveAddressUIMap[this]
    set(value) {
        updateReceiveAddressUIMap[this] = value
    }

/** Disconnect from the UI and clear the UI */
fun Account.detachUI()
{
    dbgAssertGuiThread()
    tickerGUI("")
    balanceGUI("")
    unconfirmedBalanceGUI("")
    infoGUI("")

    uiBinding = null
    tickerGUI.reactor = null
    balanceGUI.reactor = null
    unconfirmedBalanceGUI.reactor = null
    infoGUI.reactor = null

}

@Suppress(SUP)
//? Set the user interface elements for this cryptocurrency
fun Account.setUI(ui: AccountListItemBinding, al: GuiAccountList, icon: ImageView?, ticker: TextView?, balance: TextView?, unconf: TextView?, infoView: TextView?)
{
    uiBinding = ui
    // if (ticker != null) tickerGUI.reactor = TextViewReactor<String>(ticker)
    if (ticker != null) tickerGUI.reactor = TextViewReactor<String>(ticker, 0L, { s:String, paint: Paint ->
        // val desiredWidth = (displayMetrics.scaledDensity*97).toInt()
        val desiredWidth = spToPx(95f)
        paint.setTextSizeForWidth(s,desiredWidth, 28)
    })
    else tickerGUI.reactor = null
    if (balance != null) balanceGUI.reactor = TextViewReactor<String>(balance, 0L, { s:String, paint: Paint ->
        val desiredWidth =  (displayMetrics.widthPixels.toFloat()/2.2f).toInt()
        paint.setTextSizeForWidth(s,desiredWidth, 30)
    })
    else balanceGUI.reactor = null
    if (unconf != null) unconfirmedBalanceGUI.reactor = TextViewReactor<String>(unconf, TextViewReactor.GONE_IF_EMPTY,
      { s:String, paint: Paint ->
          val desiredWidth = displayMetrics.widthPixels
          paint.setTextSizeForWidth(s,desiredWidth, 18)
      })
    else unconfirmedBalanceGUI.reactor = null

    if (infoView != null) infoGUI.reactor = TextViewReactor<String>(infoView)
    else infoGUI.reactor = null
}

data class ReceiveInfoResult(val addrString: String?, val qr: Bitmap?)

suspend fun Account.ifUpdatedReceiveInfo(sz: Int, refresh: (String, Bitmap) -> Unit) = onUpdatedReceiveInfo(sz, refresh)

suspend fun Account.onUpdatedReceiveInfo(sz: Int, refresh: ((String, Bitmap) -> Unit)): Unit
{
    currentReceive.let {
        var addr: PayAddress? = it?.address

        var qr = currentReceiveQR
        var genNew = if (addr == null)
            true
        else
        {
            // If we have an address, then if re-use is true don't get another one
            if ((flags and ACCOUNT_FLAG_REUSE_ADDRESSES) > 0U) false
            // Otherwise get another one if our balance on this address is nonzero
            else addr.let { syncNotInUI { (wallet.getBalanceIn(it) > 0) } }
        }

        if (genNew)
        {
            val ret = wallet.newDestination()
            currentReceive = ret
            saveAccountAddress()
            currentReceiveQR = null // force regeneration
            addr = ret.address
        }

        // regenerate the QR from the address
        if (currentReceiveQR == null)
        {
            qr = textToQREncode(addr.toString(), sz + 200)
            currentReceiveQR = qr
        }

        if ((addr != null)&&(qr != null))  // Should always be true if we get here
            refresh.invoke(addr.toString(), qr)

    }
}

//? Return a string and bitmap that corresponds to the current receive address, with a suggested quantity specified in the URI's standard units, i.e BCH.
//? Provide qty in this currency code's units (i.e. mBCH)
fun Account.receiveInfoWithQuantity(qty: BigDecimal, sz: Int, refresh: ((ReceiveInfoResult) -> Unit))
{
    launch {
        val addr = currentReceive?.address
        val uri:String = addr.toString() + "?amount=" + (if (wallet.chainSelector.isBchFamily) BchFormat.format(toPrimaryUnit(qty)) else NexaFormat.format(toPrimaryUnit(qty)))
        val qr = textToQREncode(uri, sz)
        refresh(ReceiveInfoResult(uri, qr))
    }
}

fun Account.updateUI(force: Boolean)
{
    var delta = balance - confirmedBalance

    val chainstate = wallet.chainstate
    var unconfBalStr = ""
    if (chainstate != null)
    {
        if (chainstate.isSynchronized(1, 60 * 60))  // ignore 1 block desync or this displays every time a new block is found
        {
            unconfBalStr =
              if (CURRENCY_ZERO == unconfirmedBalance)
                  ""
              else
                  i18n(R.string.incoming) % mapOf(
                    "delta" to (if (delta > BigDecimal.ZERO) "+" else "") + format(delta),
                    "unit" to currencyCode
                  )

            laterUI {
                balanceGUI.setAttribute("strength", "normal")
                if (delta > BigDecimal.ZERO) unconfirmedBalanceGUI.setAttribute("color", R.color.colorCredit)
                else unconfirmedBalanceGUI.setAttribute("color", R.color.colorDebit)
            }
        }
        else
        {
            unconfBalStr = if (chainstate.syncedDate <= 1231416000) i18n(R.string.unsynced)  // for fun: bitcoin genesis block
            else i18n(R.string.balanceOnTheDate) % mapOf("date" to epochToDate(chainstate.syncedDate))
            laterUI {
                balanceGUI.setAttribute("strength", "dim")
                unconfirmedBalanceGUI.setAttribute("color", R.color.unsyncedStatusColor)
            }
        }
    }
    else
    {
        laterUI {
            balanceGUI.setAttribute("strength", "dim")
            //unconfirmedBalanceGUI(i18n(R.string.walletDisconnectedFromBlockchain), force)
            uiBinding?.balanceUnconfirmedValue?.text = i18n(R.string.walletDisconnectedFromBlockchain)
        }
    }


    balanceGUI(format(balance), force)
    unconfirmedBalanceGUI(unconfBalStr, force)

    // If we got something in a receive address, then show a new one
    updateReceiveAddressUI?.invoke(this)

    if (chainstate != null)
    {
        val cnxnLst = wallet.chainstate?.chain?.net?.mapConnections() { it.name }

        val trying: List<String> = if (chainstate.chain.net is MultiNodeCnxnMgr) (chainstate.chain.net as MultiNodeCnxnMgr).initializingCnxns.map { it.name } else listOf()
        val peers = cnxnLst?.joinToString(", ") + (if (trying.isNotEmpty()) (" " + i18n(R.string.trying) + " " + trying.joinToString(", ")) else "")

        val infoStr = i18n(R.string.at) + " " + (wallet.chainstate?.syncedHash?.toHex()?.take(8) ?: "") + ", " + (wallet.chainstate?.syncedHeight
          ?: "") + " " + i18n(R.string.of) + " " + (wallet.chainstate?.chain?.curHeight
          ?: "") + " blocks, " + (wallet.chainstate?.chain?.net?.size ?: "") + " peers\n" + peers
        infoGUI(infoStr, force)  // since numPeers takes cnxnLock
    }
    else
    {
        infoGUI(i18n(R.string.walletDisconnectedFromBlockchain), force)
    }

    tickerGUI(name, force)

    laterUI {
        if (fiatPerCoin > BigDecimal.ZERO)
        {
            var fiatDisplay = balance * fiatPerCoin
            uiBinding?.info?.let { it.visibility = View.VISIBLE; it.text = i18n(R.string.approximatelyT) % mapOf("qty" to FiatFormat.format(fiatDisplay), "fiat" to fiatCurrencyCode) }
        }
        else uiBinding?.info?.let { it.visibility = View.GONE }
    }

    // Decide whether to show the assets nav
    if (!devMode)  // because always shown in dev mode
    {
        val now: Long = Instant.now().toEpochMilli()
        if (lastAssetCheck + (8L * 1000L) < now)  // Don't check too often
        {
            val act = currentActivity
            if (act != null && act is CommonNavActivity)
            {
                //val prefDB = act.getSharedPreferences(i18n(R.string.preferenceFileName), Context.MODE_PRIVATE)
                //val showingAssets = prefDB.getBoolean(SHOW_ASSETS_PREF, false)
                if (!act.isShowingAssetsNavButton())  // only check if not currently showing the assets nav
                {
                    if (hasAssets()) laterUI {
                        act.setAssetsNavVisible(true)  // once there are assets, we show the nav, but we don't change the prefs so if the assets are sent, asset nav will disappear on the next run
                    }
                }
            }
        }
    }
}

fun Account.getReceiveQR(sz: Int): Bitmap
{
    var im = currentReceiveQR
    val cr = currentReceive
    if ((im == null) && (cr != null))
    {
        im = textToQREncode(cr.address.toString(), sz + 200)
        currentReceiveQR = im
    }

    return im!! // It must be not null because if null I set it
}

/**
 * This can be called either when the app as been paused, or early during app initialization
 * so we need to check to see if the is an actual resume-after-pause, or an initial startup
 *
 * Should call onResume() to update model
 */
fun Account.onResumeAndroid()
{
    onResume()
    if (started)
    {
        updateUI(true)
    }
}

/** Call whenever the state of this account has changed so needs to be redrawn.  Or on first draw (with force = true) */
var accountOnChangedLater = false
actual fun onChanged(account: Account, force: Boolean)
{
    /*
    // While I'm updating the screen is not responsive (e.g. onclick does not work).  This is probably a compose bug.  But regardless
    // updating only 3 times a second is fast enough and consumes a lot less CPU which results in lower battery drain.
    triggerRecompose.value = (millinow()/333).toInt()
    // I need to make sure that the last change gets updated.  so if this change didn't cause an update
    // I need to schedule an update to trigger later in case this is the last update for awhile.
    // But I don't want to launch 100s of coroutines to do this...
    if (!accountOnChangedLater)
    {
        accountOnChangedLater = true
        later {
            delay(500L)
            accountOnChangedLater = false
            triggerRecompose.value = (millinow()/333).toInt()
        }
    }
     */

    laterUI {
        account.uiBinding?.let {
            if (account.lockable)
            {
                it.lockIcon.visibility = View.VISIBLE
                if (account.locked)
                    it.lockIcon.setImageResource(R.drawable.ic_lock)
                else
                    it.lockIcon.setImageResource(R.drawable.ic_unlock)
            }
            else
                it.lockIcon.visibility = View.GONE
        }
    }
    later {
        account.changeAsyncProcessing()
        account.updateUI(force)
        triggerAccountsChanged(account)
    }

}

