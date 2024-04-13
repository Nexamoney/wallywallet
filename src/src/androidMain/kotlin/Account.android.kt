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
}

/** Call whenever the state of this account has changed so needs to be redrawn.  Or on first draw (with force = true) */
var accountOnChangedLater = false
actual fun onChanged(account: Account, force: Boolean)
{
    later {
        account.changeAsyncProcessing()
        triggerAccountsChanged(account)
    }
}

