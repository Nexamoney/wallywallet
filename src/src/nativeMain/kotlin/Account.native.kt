package info.bitcoinunlimited.www.wally

import info.bitcoinunlimited.www.wally.ui.triggerAccountsChanged
import org.nexa.libnexakotlin.*

actual fun EncodePIN(actName: String, pin: String, size: Int): ByteArray {
    val salt = "wally pin " + actName
    val skf = SecretKeyFactoryCommon.getInstance("PBKDF2WithHmacSHA512", FallbackProvider())
    val secretkey = PBEKeySpecCommon(pin.toCharArray(), salt.toByteArray(), 2048, 512)
    val seed = skf.generateSecret(secretkey)
    return seed.encoded.slice(IntRange(0, size - 1)).toByteArray()
}

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