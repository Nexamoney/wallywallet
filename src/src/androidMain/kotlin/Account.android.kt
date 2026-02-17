package info.bitcoinunlimited.www.wally

import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import info.bitcoinunlimited.www.wally.ui.triggerAccountsChanged
import org.nexa.libnexakotlin.*

private val LogIt = GetLog("BU.wally.AccountAndroid")

/** Store the PIN encoded.  However, note that for short PINs a dictionary attack is very feasible */
actual fun EncodePIN(actName: String, pin: String, size: Int): ByteArray
{
    val salt = "wally pin " + actName
    val skf = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA512")
    val secretkey = PBEKeySpec(pin.toCharArray(), salt.toByteArray(), 2048, 512)
    val seed = skf.generateSecret(secretkey)
    return seed.encoded.slice(IntRange(0, size - 1)).toByteArray()
}

/** Call whenever the state of this account has changed so needs to be redrawn.  Or on first draw (with force = true) */
actual fun onChanged(account: Account, force: Boolean)
{
    onetlater("accountChanged_${account.name}") {
        account.changeAsyncProcessing()
        triggerAccountsChanged(account)
    }
    onetlater("accountAssetMap_${account.name}") {
        account.constructAssetMap()
        triggerAccountsChanged(account)
    }
}

