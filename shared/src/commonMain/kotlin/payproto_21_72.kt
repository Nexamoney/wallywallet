package info.bitcoinunlimited.www.wally

import org.nexa.libnexakotlin.*
import com.ionspin.kotlin.bignum.decimal.*
import io.ktor.http.*

open class Bip70Exception(details: Int) : LibNexaException(appI18n(details), "payment protocol error")

// These functions should be completely convertable to a common implementation.  However, right now I don't have a good mechanism to test it
// (a json pay server is required, and the only test one is bitpay for BCH).
// So leaving it untouched the Java/Android code right now, and throwing unimplemented on the other platforms.
expect fun processJsonPay(bip72: String): ProspectivePayment
expect fun completeJsonPay(pip: ProspectivePayment, tx: iTransaction)

class ProspectivePayment
{
    var crypto: ChainSelector? = null
    var context: String? = null  //!< How to continue this payment process
    var memo: String? = null
    var paymentId: String? = null
    val outputs = mutableListOf<iTxOutput>()
    var totalSatoshis: Long = 0L
    var feeSatPerByte: BigDecimal? = null
}

