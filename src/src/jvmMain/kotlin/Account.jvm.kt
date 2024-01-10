package info.bitcoinunlimited.www.wally

import info.bitcoinunlimited.www.wally.ui.accountChangedNotification
import info.bitcoinunlimited.www.wally.ui.views.uiData
import org.nexa.libnexakotlin.GetLog
import org.nexa.libnexakotlin.launch
import org.nexa.libnexakotlin.toByteArray
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

private val LogIt = GetLog("BU.wally.account.jvm")

actual fun EncodePIN(actName: String, pin: String, size: Int): ByteArray
{
    val salt = "wally pin " + actName
    val skf = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA512")
    val secretkey = PBEKeySpec(pin.toCharArray(), salt.toByteArray(), 2048, 512)
    val seed = skf.generateSecret(secretkey)
    return seed.encoded.slice(IntRange(0, size - 1)).toByteArray()
}

actual fun onChanged(account: Account, force: Boolean)
{
    account.uiData()
    launch { accountChangedNotification.send(account.name) }
}