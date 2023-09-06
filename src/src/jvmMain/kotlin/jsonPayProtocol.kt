package info.bitcoinunlimited.www.wally

/** This module implements the JSON payment protocol defined by BitPay here: https://github.com/bitpay/jsonPaymentProtocol/blob/master/v2/specification.md
 *  Its possible to test this API manually using: https://test.bitpay.com/m/36668/checkout, and https://bitpay.com/demos
 * */
import org.nexa.libnexakotlin.*
import com.ionspin.kotlin.bignum.decimal.*
import kotlinx.serialization.json.*
import java.io.FileNotFoundException
import java.net.HttpURLConnection
import java.net.URL
import kotlin.text.Charsets.UTF_8

private val LogIt = GetLog("BU.wallet")


fun HttpURLConnection.post(data: String, headers: Map<String, String>, timeout: Int = 10000) = post(data.toByteArray(UTF_8), headers, timeout)

fun HttpURLConnection.post(data: ByteArray, headers: Map<String, String>, timeout: Int = 10000)
{
    instanceFollowRedirects = false
    setRequestMethod("POST")
    connectTimeout = timeout
    doOutput = true
    doInput = true
    useCaches = false

    setRequestProperty("charset", "utf-8")
    setRequestProperty("Content-length", data.size.toString())

    for ((k, v) in headers)
    {
        setRequestProperty(k, v)
    }

    connect()
    outputStream.write(data)
    outputStream.flush()
    outputStream.close()
}

actual fun processJsonPay(bip72: String): ProspectivePayment
{
    var ret = ProspectivePayment()
    LogIt.info(sourceLoc() + ": BIP70 payment protocol: " + bip72)
    val preambleRequest = URL(bip72)

    val currencies: MutableList<String> = mutableListOf()
    ret.context = bip72
    if (true)
    {
        val cxn = preambleRequest.openConnection() as HttpURLConnection
        cxn.setRequestProperty("Content-Type", "application/bitcoin-paymentrequest")
        cxn.setRequestProperty("Content-Transfer-Encoding", "binary")
        cxn.setRequestProperty("Accept", "application/payment-options")
        cxn.setRequestProperty("x-paypro-version", "2")
        //cxn.connectTimeout()
        cxn.connect()
        LogIt.info(cxn.responseCode.toString())
        val preamble: String = try
        {
            cxn.inputStream.use { it.reader().use { reader -> reader.readText() } }
        }
        catch (e: FileNotFoundException)
        {
            throw Bip70Exception(Rexpired)
        }
        LogIt.info(preamble)

        val preambleJ: JsonElement = Json.decodeFromString(preamble)
        ret.memo = preambleJ.jsonObject["memo"]?.jsonPrimitive?.content

        for (choice in preambleJ.jsonObject["paymentOptions"]?.jsonArray ?: mutableListOf<JsonObject>())
        {
            val currencyCode: String = (if (choice.jsonObject["network"]?.jsonPrimitive?.content == "test") "T" else "") + choice.jsonObject["currency"]?.jsonPrimitive?.content
            currencies.add(currencyCode)
        }

        ret.paymentId = preambleJ.jsonObject["paymentId"]?.jsonPrimitive?.content
    }

    LogIt.info(currencies.toString())
    var knownCurrency = false
    var chainSelector = ChainSelector.NEXA

    /* TODO:
    if (currencies.contains("BCH"))
    {
        someBCH = true
        chainSelector = ChainSelector.BCHMAINNET
    }
     */
    if (currencies.contains("NEXA"))
    {
        knownCurrency = true
        chainSelector = ChainSelector.NEXA
    }
    else if (currencies.contains("tNEXA"))
    {
        knownCurrency = true
        chainSelector = ChainSelector.NEXATESTNET
    }
    else if (currencies.contains("rNEXA"))
    {
        knownCurrency = true
        chainSelector = ChainSelector.NEXAREGTEST
    }

    if (knownCurrency)
    {
        ret.crypto = chainSelector
        //val lastSlash = bip72.lastIndexOf("/") + 1
        //val reqUrl = bip72.substring(0,lastSlash) + ":" + bip72.substring(lastSlash, bip72.length)
        val paymentInstructions = URL(bip72) // + "?chain=BCH&currency=BCH")
        val cxn = paymentInstructions.openConnection() as HttpURLConnection

        try
        {
            // Note we are supposed to pass BCH even if in testnet
            cxn.post("""{ "chain": "BCH", "currency": "BCH"}""", mapOf("Content-Type" to "application/payment-request", "x-paypro-version" to "2"))
        }
        catch (e: Exception)
        {
            throw Bip70Exception(RnotSupported)
        }

        val responseCode = cxn.responseCode
        LogIt.info(responseCode.toString())
        if (cxn.responseCode >= 400)
        {
            throw Bip70Exception(RnotSupported)
        }
        if (cxn.responseCode > 299)
        {
            throw Bip70Exception(RnotSupported)
        }

        val instr: String = cxn.inputStream.use { it.reader().use { reader -> reader.readText() } }
        LogIt.info(instr)

        val instrJ: JsonElement = Json.decodeFromString(instr)
        val idelve = instrJ.jsonObject["instructions"]?.jsonArray?.get(0)?.jsonObject
        if (idelve != null)
        {
            try
            {
                val s = idelve.get("requiredFeePerByte")?.jsonPrimitive?.content
                if (s != null) ret.feeSatPerByte = CurrencyDecimal(s)
            }
            catch (e: kotlin.TypeCastException)
            {
                // leave the field as null to use the default fee
            }
            idelve.get("outputs")?.jsonArray?.let { outs ->
                for (ou in outs)
                {
                    val o = ou.jsonObject
                    if (o["amount"] == null) throw Bip70Exception(Rbip70NoAmount) // Output has no specified amount
                    val amount: Long = o["amount"].toString().toLong()
                    ret.totalSatoshis += amount

                    var payAddrString = o["address"]?.jsonPrimitive?.content ?: ""
                    if (!payAddrString.contains(":")) // address prefix was not provided so put one in
                    {
                        payAddrString = chainToURI[chainSelector] + ":" + payAddrString
                    }
                    val payAddr: PayAddress = PayAddress(payAddrString)
                    ret.outputs.add(NexaTxOutput(chainSelector, amount, payAddr.outputScript()))
                }
            }

            return ret
        }
    }
    throw Bip70Exception(RnotSupported)
}

actual fun completeJsonPay(pip: ProspectivePayment, tx: iTransaction)
{
    val cs = tx.chainSelector
    val txSize = tx.size
    var txUnsigned = txFor(cs)

    // Copy most fields to txUnsigned, except for the inputs
    txUnsigned.version = tx.version
    txUnsigned.lockTime = tx.lockTime
    // Add output array by reference since we aren't touching them
    txUnsigned.setOutputs(tx.outputs)

    // Copy the input script array without the input script
    for (i in tx.inputs)
    {
        val inp:iTxInput = i.copy()
        inp.script = SatoshiScript(cs, SatoshiScript.Type.PUSH_ONLY, byteArrayOf())
        txUnsigned.add(inp)
    }

    val temp = chainToCurrencyCode[pip.crypto]!!
    val cc = if (temp == "TBCH") "BCH" else temp  // workaround for how the json payment protocol specifies the mainnet chain even in testnet

    if (true)
    {
        val paymentInstructions = URL(pip.context)
        val cxn = paymentInstructions.openConnection() as HttpURLConnection

        val unsignedHexTx = txUnsigned.toHex()
        try
        {
            cxn.post("""{"chain":"${cc}","transactions":[{"tx":"$unsignedHexTx", "weightedSize":$txSize}]}""", mapOf("Content-Type" to "application/payment-verification", "x-paypro-version" to "2"))
        }
        catch (e: Exception)
        {
            throw Bip70Exception(RnotSupported)
        }

        if ((cxn.responseCode < 200) || (cxn.responseCode >= 300))
        {
            LogIt.info("Response: ${cxn.responseCode}: ${cxn.responseMessage}")
            val err: String = cxn.errorStream.use { it.reader().use { reader -> reader.readText() } }
            LogIt.info("Error info: $err")
            throw Bip70Exception(RnotSupported)
        }

        if (true)
        {
            val instr: String = cxn.inputStream.use { it.reader().use { reader -> reader.readText() } }
            LogIt.info(instr)

            val instrJ: JsonElement = Json.decodeFromString(instr)
            val memo: String = instrJ.jsonObject["memo"]?.jsonPrimitive?.content ?: ""
            LogIt.info(memo)
        }
    }

    if (true)
    {
        val paymentInstructions = URL(pip.context)
        val cxn = paymentInstructions.openConnection() as HttpURLConnection
        // Now send the final transaction
        val signedHexTx = tx.toHex()
        LogIt.info("bitpayJson Tx: " + signedHexTx)
        try
        {
            cxn.post("""{"chain":"${cc}","transactions":[{"tx":"$signedHexTx"}]}""", mapOf("Content-Type" to "application/payment", "x-paypro-version" to "2"))
        }
        catch (e: Exception)
        {
            throw Bip70Exception(RnotSupported)
        }

        if ((cxn.responseCode < 200) || (cxn.responseCode >= 300))
        {
            LogIt.info("Response: ${cxn.responseCode}: ${cxn.responseMessage}")
            val err: String = cxn.errorStream.use { it.reader().use { reader -> reader.readText() } }
            LogIt.info("Error info: $err")
            throw Bip70Exception(RnotSupported)
        }

        if (true)
        {
            val instr: String = cxn.inputStream.use { it.reader().use { reader -> reader.readText() } }
            LogIt.info(instr)

            val instrJ: JsonElement = Json.decodeFromString(instr)
            val memo: String = instrJ.jsonObject["memo"]?.jsonPrimitive?.content ?: ""
            LogIt.info(memo)
        }
    }
    // If an exception isn't thrown this function succeeded
}
