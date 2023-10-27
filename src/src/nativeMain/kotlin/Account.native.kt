package info.bitcoinunlimited.www.wally

import info.bitcoinunlimited.www.wally.ui.views.triggerRecompose
import org.nexa.libnexakotlin.FallbackProvider
import org.nexa.libnexakotlin.PBEKeySpecCommon
import org.nexa.libnexakotlin.SecretKeyFactoryCommon
import org.nexa.libnexakotlin.toByteArray

actual fun EncodePIN(actName: String, pin: String, size: Int): ByteArray {
    val salt = "wally pin " + actName
    val skf = SecretKeyFactoryCommon.getInstance("PBKDF2WithHmacSHA512", FallbackProvider())
    val secretkey = PBEKeySpecCommon(pin.toCharArray(), salt.toByteArray(), 2048, 512)
    val seed = skf.generateSecret(secretkey)
    return seed.encoded.slice(IntRange(0, size - 1)).toByteArray()
}

actual fun onChanged(account: Account, force: Boolean)
{
    triggerRecompose.value = triggerRecompose.value + 1
}