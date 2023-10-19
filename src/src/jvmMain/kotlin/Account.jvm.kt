package info.bitcoinunlimited.www.wally

import org.nexa.libnexakotlin.toByteArray
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

actual fun EncodePIN(actName: String, pin: String, size: Int): ByteArray
{
    val salt = "wally pin " + actName
    val skf = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA512")
    val secretkey = PBEKeySpec(pin.toCharArray(), salt.toByteArray(), 2048, 512)
    val seed = skf.generateSecret(secretkey)
    return seed.encoded.slice(IntRange(0, size - 1)).toByteArray()
}