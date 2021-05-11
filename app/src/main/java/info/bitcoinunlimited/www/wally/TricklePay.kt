// Copyright (c) 2019 Andrew Stone Consulting (qq9wwnuw4eukyh5g34ckg5vk4aaxnvr04vkspyv850)
// Distributed under the MIT software license, see the accompanying file COPYING or http://www.opensource.org/licenses/mit-license.php.
package info.bitcoinunlimited.www.wally

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.View.GONE
import android.view.View.VISIBLE
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import androidx.annotation.Keep
import androidx.core.net.toUri
import androidx.fragment.app.Fragment
import bitcoinunlimited.libbitcoincash.*
import bitcoinunlimited.libbitcoincash.rem
import io.ktor.client.*
import io.ktor.client.engine.android.*
import io.ktor.client.engine.cio.*
import io.ktor.client.features.*
import io.ktor.client.features.json.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import kotlinx.android.synthetic.main.activity_trickle_pay.*
import kotlinx.android.synthetic.main.trickle_pay_custom_tx.*
import kotlinx.android.synthetic.main.trickle_pay_reg.*
import kotlinx.android.synthetic.main.trickle_pay_reg.GuiCustomTxCost
import kotlinx.android.synthetic.main.trickle_pay_reg.GuiTricklePayEntity
import java.lang.Exception
import java.net.SocketTimeoutException
import java.net.URL
import java.util.logging.Logger

val TDPP_URI_SCHEME = "tdpp"
val TDPP_DEFAULT_UOA = "BCH"
val TDPP_DEFAULT_PROTOCOL = "http:"

// TODO: extract tdpp protocol stuff into a library
const val TDPP_FLAG_NOFUND = 1
const val TDPP_FLAG_NOPOST = 2
const val TDPP_FLAG_NOSHUFFLE = 4
const val TDPP_FLAG_PARTIAL = 8

private val LogIt = Logger.getLogger("bu.TricklePay")

// Must be top level for the serializer to handle it
@Keep
@kotlinx.serialization.Serializable
data class TricklePayAssetInfo(val script: String, val txid: String, val idx: Int, val amt: Long)

@Keep
@kotlinx.serialization.Serializable
data class TricklePayAssetList(val assets: List<TricklePayAssetInfo>)

// Structured data type to make it cleaner to return tx analysis data from the analysis function.
// otherInputSatoshis: BCH being brought into this transaction by other participants
data class TxAnalysisResults(
    val receivingSats: Long,
    val sendingSats: Long,
    val receivingTokenTypes: Long,
    val sendingTokenTypes: Long,
    val imSpendingTokenTypes: Long,  // The tx spends this number of token TYPES currently controlled by this wallet
    val otherInputSatoshis: Long?,  // If this is null, I'm not funding this tx (its likely a partial tx)
    val myInputSatoshis: Long,
    val ttInfo: Map<GroupId, Long>,
    val completionException: Exception?
)

/* Information about payment delegations that have been accepted by the user
* To be stored/retrieved */
data class TdppDomain(
    @cli(Display.Simple, "Address of entity") var domain: String,
    @cli(Display.Simple, "Topic") var topic: String,
    @cli(Display.Simple, "Signing address") var addr: String,
    @cli(Display.Simple, "Currency") var uoa: String,
    // These are stored in the finest unit of the unit of account of the currency, i.e. Satoshis or cents.
    // But OFC they should be displayed in a more reasonable unit
    @cli(Display.Simple, "Maximum automatic payment") var maxper: Long,
    @cli(Display.Simple, "Maximum automatic per day") var maxday: Long,
    @cli(Display.Simple, "Maximum automatic per week") var maxweek: Long,
    @cli(Display.Simple, "Maximum automatic per month") var maxmonth: Long,
    var descper: String,
    var descday: String,
    var descweek: String,
    var descmonth: String,
    @cli(Display.Simple, "enable/disable all automatic payments to this entity") var automaticEnabled: Boolean
) : BCHserializable()
{
    constructor(stream: BCHserialized) : this("", "", "", "", -1, -1, -1, -1, "", "", "", "", false)
    {
        BCHdeserialize(stream)
    }

    override fun BCHserialize(format: SerializationType): BCHserialized //!< Serializer
    {
        return BCHserialized(format) + domain + topic + addr + uoa + maxper + maxday + maxweek + maxmonth + descper + descday + descweek + descmonth + automaticEnabled
    }

    override fun BCHdeserialize(stream: BCHserialized): BCHserialized //!< Deserializer
    {
        domain = stream.deString()
        topic = stream.deString()
        addr = stream.deString()
        uoa = stream.deString()
        maxper = stream.deint64()
        maxday = stream.deint64()
        maxweek = stream.deint64()
        maxmonth = stream.deint64()
        descper = stream.deString()
        descday = stream.deString()
        descweek = stream.deString()
        descmonth = stream.deString()
        automaticEnabled = stream.deboolean()
        return stream
    }

}


class TricklePayEmptyFragment : Fragment()
{

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View?
    {
        val ret = View(this.context)
        return ret
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?)
    {
        super.onViewCreated(view, savedInstanceState)
    }
}


/* Handle a trickle pay Registration */
class TricklePayRegFragment : Fragment()
{
    var uri: Uri? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View?
    {
        val ret = inflater.inflate(R.layout.trickle_pay_reg, container, false)
        return ret
    }

    override fun onResume()
    {
        updateUI()
        super.onResume()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?)
    {
        super.onViewCreated(view, savedInstanceState)
        //view.findViewById<Button>(R.id.button_first).setOnClickListener {
        //    findNavController()?.navigate(R.id.action_Welcome1_to_Welcome2)
        //}
    }

    data class Data2Widgets(val amtParam: String, val descParam: String, val entry: EditText, val desc: TextView)

    fun populate(puri: Uri)
    {
        uri = puri
        updateUI()
    }

    fun updateUI()
    {
        val u: Uri = uri ?: return

        val topic = u.getQueryParameter("topic").let {
            if (it == null) ""
            else ":" + it
        }
        GuiTricklePayEntity.text = u.authority + topic

        val d2w = listOf(Data2Widgets("maxper", "descper", GuiAutospendLimitEntry0, GuiAutospendLimitDescription0),
            Data2Widgets("maxday", "descday", GuiAutospendLimitEntry1, GuiAutospendLimitDescription1),
            Data2Widgets("maxweek", "descweek", GuiAutospendLimitEntry2, GuiAutospendLimitDescription2),
            Data2Widgets("maxmonth", "descmonth", GuiAutospendLimitEntry3, GuiAutospendLimitDescription3)
        )

        for (d in d2w)
        {
            val amount: ULong? = u.getQueryParameter(d.amtParam).let {
                if (it == null) null
                else it.toULong()
            }
            val desc: String = u.getQueryParameter(d.descParam) ?: ""
            d.entry.text.clear()
            if (amount != null)
            {
                d.entry.text.append(amount.toString())
            }
            d.desc.text = desc
        }


    }
}

/* Handle a trickle pay Registration */
class TricklePayCustomTxFragment : Fragment()
{
    var uri: Uri? = null
    var tx: BCHtransaction? = null
    var analysis: TxAnalysisResults? = null
    var tpActivity: TricklePayActivity? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View?
    {
        val ret = inflater.inflate(R.layout.trickle_pay_custom_tx, container, false)
        return ret
    }

    override fun onResume()
    {
        updateUI()
        super.onResume()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?)
    {
        super.onViewCreated(view, savedInstanceState)
    }

    fun populate(pactivity: TricklePayActivity, puri: Uri, ptx: BCHtransaction, panalysis: TxAnalysisResults)
    {
        uri = puri
        tx = ptx
        analysis = panalysis
        tpActivity = pactivity
        updateUI()
    }

    fun updateUI()
    {
        val u: Uri = uri ?: return

        val chain = u.getQueryParameter("chain").let {
            if (it == null) ""
            // raise error, chain param is mandatory
        }

        val topic = u.getQueryParameter("topic").let {
            if (it == null) ""
            else ":" + it
        }
        GuiTricklePayEntity.text = u.authority + topic

        val acc = tpActivity!!.getRelevantAccount()

        val a = analysis
        if (a != null)
        {
            // what's being paid to me - what I'm contributing.  So if I pay out then its a negative number
            val netSats = a.receivingSats - a.myInputSatoshis

            if (netSats > 0)
            {
                GuiCustomTxCost.text = (i18n(R.string.receiving) + " " + acc.cryptoFormat.format(acc.fromFinestUnit(netSats)) + " " + acc.currencyCode)
            }
            else if (netSats < 0)
            {
                val txt = i18n(R.string.sending) + " " + acc.cryptoFormat.format(acc.fromFinestUnit(-netSats)) + " " + acc.currencyCode
                LogIt.info(txt)
                GuiCustomTxCost.text = txt
            }
            else
            {
                GuiCustomTxCost.text = i18n(R.string.nothing)
            }

            if (a.otherInputSatoshis != null)
            {
                val fee = (a.myInputSatoshis + a.otherInputSatoshis) - (a.receivingSats + a.sendingSats)
                GuiCustomTxFee.text = (i18n(R.string.ForAFeeOf) % mapOf("fee" to acc.cryptoFormat.format(acc.fromFinestUnit(fee)), "units" to acc.currencyCode))
            }
            else
            {
                GuiCustomTxFee.text = ""
            }

            if (a.receivingTokenTypes > 0)
            {
                if (a.sendingTokenTypes > 0)
                {
                    // This is not strictly true.  The counterparty could hand you a transaction that both supplies a token and spends that token to themselves...
                    GuiCustomTxTokenSummary.text = i18n(R.string.TpExchangingTokens)
                }
                else
                {
                    GuiCustomTxTokenSummary.text = i18n(R.string.TpReceivingTokens)
                }
            }
            else
            {
                if ((a.sendingTokenTypes > 0)||(a.imSpendingTokenTypes>0))
                {
                    GuiCustomTxTokenSummary.text = i18n(R.string.TpSendingTokens)
                }
            }

        }
    }
}

/* Handle a trickle pay Registration */
class TricklePayAssetRequestFragment : Fragment()
{
    var uri: Uri? = null
    var tpActivity: TricklePayActivity? = null
    var assets: List<TricklePayAssetInfo>? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View?
    {
        val ret = inflater.inflate(R.layout.trickle_pay_asset_request, container, false)
        return ret
    }

    override fun onResume()
    {
        updateUI()
        super.onResume()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?)
    {
        super.onViewCreated(view, savedInstanceState)
    }

    fun selectedAssets(): TricklePayAssetList
    {
        return TricklePayAssetList(assets!!)
    }

    fun populate(pactivity: TricklePayActivity, puri: Uri, passets: List<TricklePayAssetInfo>)
    {
        uri = puri
        tpActivity = pactivity
        assets = passets
        updateUI()
    }

    fun updateUI()
    {
        val u: Uri = uri ?: return

        val chain = u.getQueryParameter("chain").let {
            if (it == null) ""
            // raise error, chain param is mandatory
        }

        val topic = u.getQueryParameter("topic").let {
            if (it == null) ""
            else ":" + it
        }
        GuiTricklePayEntity.text = u.authority + topic

        val acc = tpActivity!!.getRelevantAccount()
    }
}


fun ConstructTricklePayRequest(entity: String, topic: String?, operation: String, signWith: PayDestination, uoa: String?, maxPer: ULong?, maxDay: ULong?, maxWeek: ULong?, maxMonth: ULong?): Uri
{
    val uri = Uri.Builder()

    uri.scheme("tdpp")
    uri.path("//" + entity + "/" + operation)

    // These are coding bugs in the app; you should not have provided a payment destination that does not have a signature
    val secret = signWith.secret ?: throw IdentityException("Wallet failed to provide an identity with a secret", "bad wallet", ErrorSeverity.Severe)
    val address = signWith.address ?: throw IdentityException("Wallet failed to provide an identity with an address", "bad wallet", ErrorSeverity.Severe)

    // NOTE, append query parameters in sorted order so that the signature string is correct!
    uri.appendQueryParameter("addr", address.toString())
    if (maxDay != null) uri.appendQueryParameter("maxday", maxDay.toString())
    if (maxMonth != null) uri.appendQueryParameter("maxmonth", maxMonth.toString())
    if (maxPer != null) uri.appendQueryParameter("maxper", maxPer.toString())
    if (maxWeek != null) uri.appendQueryParameter("maxweek", maxWeek.toString())

    if (topic != null) uri.appendQueryParameter("topic", topic)
    if (uoa != null) uri.appendQueryParameter("uoa", uoa)

    val signThis = uri.build().toString()
    LogIt.info(signThis)
    val sig = Wallet.signMessage(signThis.toByteArray(), secret)
    if (sig.size == 0) throw IdentityException("Wallet failed to provide a signable identity", "bad wallet", ErrorSeverity.Severe)
    val sigStr = Codec.encode64(sig)
    uri.appendQueryParameter("sig", sigStr)
    return uri.build()
}

fun VerifyTdppSignature(uri: Uri): Boolean?
{
    val addressStr = uri.getQueryParameter("addr")
    if (addressStr == null) return null
    val sig = uri.getQueryParameter("sig")
    if (sig == null) return null

    // recast the URI into one with the parameters in the proper order, and no sig
    val suri = Uri.Builder()
    suri.scheme(uri.scheme)
    suri.authority(uri.authority)
    suri.path(uri.path)
    val orderedParams = uri.queryParameterNames.toList().sorted()
    for (p in orderedParams)
    {
        if (p == "sig") continue
        suri.appendQueryParameter(p, uri.getQueryParameter(p))
    }

    val verifyThis = suri.build().toString()

    val pa = PayAddress(addressStr)

    LogIt.info("verification for: " + verifyThis + " Address: " + addressStr)
    LogIt.info("Message hex: " + verifyThis.toByteArray().toHex())
    LogIt.info("Raw Address: " + pa.data.toHex())
    val sigBytes = Codec.decode64(sig)
    LogIt.info("Sig: " + sigBytes.toHex())
    val result = Wallet.verifyMessage(verifyThis.toByteArray(), pa.data, sigBytes)
    if (result == null || result.size == 0)
    {
        LogIt.info("verification failed for: " + verifyThis + " Address: " + addressStr)
        Wallet.verifyMessage(verifyThis.toByteArray(), pa.data, sigBytes)
        return false
    }
    LogIt.info("verification good for: " + verifyThis + " Address: " + addressStr)
    return true
}

fun generateAndLogSomeTricklePayRequests(application: WallyApp)
{
    val act = application.primaryAccount
    val wallet = act.wallet
    val identityDest: PayDestination = wallet.destinationFor(Bip44Wallet.COMMON_IDENTITY_SEED)

    var uri = ConstructTricklePayRequest("testapp", "testtopic", "reg", identityDest, "BCH", 1000000UL, null, null, 100000000UL)
    LogIt.info(uri.toString())
    if (VerifyTdppSignature(uri) == true)
    {
        LogIt.info("Sig Verified")
    }
    else
    {
        VerifyTdppSignature(uri)
    }
    var uri2 = Uri.parse(uri.toString())
    if (VerifyTdppSignature(uri2) == true)
    {
        LogIt.info("Sig Verified")
    }
    else
    {
        VerifyTdppSignature(uri)
    }


}


class TricklePayActivity : CommonNavActivity()
{
    override var navActivityId = R.id.navigation_trickle_pay

    var db: KvpDatabase? = null

    var domains: MutableMap<String, TdppDomain> = mutableMapOf()

    val SER_VERSION: Byte = 1.toByte()

    /** The currency selected as the unit of account during registration/configuration */
    var regCurrency: String = ""
    var regAddress: String = ""

    var tflags: Int = 0
    var proposedTx: BCHtransaction? = null
    var proposalUri: Uri? = null
    var proposalCookie: String? = null
    var host: String? = null
    var port: Int = 80
    var topic: String? = null
    var sig: String? = null
    var cookie: String? = null
    var rproto: String? = null
    var rpath: String? = null
    var chainSelector: ChainSelector? = null

    override fun onCreate(savedInstanceState: Bundle?)
    {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_trickle_pay)
        if (wallyApp == null) wallyApp = (getApplication() as WallyApp)

        if (db == null)
        {
            val ctxt = PlatformContext(applicationContext)
            db = OpenKvpDB(ctxt, "wallyData")
        }

        load()
    }

    fun load()
    {
        notInUI {
            db?.let {
                try
                {
                    val ser = it.get("tdppDomains")
                    if (ser.size != 0) // No data saved
                    {
                        val bchser = BCHserialized(ser, SerializationType.DISK)
                        val ver = bchser.debytes(1)[0]
                        if (ver == SER_VERSION)
                            domains = bchser.demap({ it.deString() }, { TdppDomain(it) })
                    }
                }
                catch (e: DataMissingException)
                {
                    LogIt.info("benign: no TDPP domains registered yet")
                }
            }
        }
    }

    fun save()
    {
        val ser = BCHserialized.uint8(SER_VERSION)
        ser.add(BCHserialized.map(domains, { BCHserialized(SerializationType.DISK).add(it) }, { it.BCHserialize() }, SerializationType.DISK))
        db?.let {
            it.set("tdppDomains", ser.flatten())
        }
    }

    fun getRelevantWallet(): Wallet = getRelevantAccount().wallet


    fun getRelevantAccount(): Account
    {
        // Get a handle on the relevant wallets
        val walChoices = wallyApp!!.accountsFor(chainSelector!!)

        if (walChoices.size == 0)
        {
            throw WalletInvalidException()
        }

        // TODO associate an account with a trickle pay
        // For now, grab the first sorted
        val walSorted = walChoices.toList().sortedBy { it.name }
        val wal = walSorted[0]
        return wal
    }

    override fun onResume()
    {
        super.onResume()
        // Process the intent that caused this activity to resume
        if (intent.scheme != null)  // its null if normal app startup
        {
            handleNewIntent(intent)
        }
        else displayFragment(GuiTricklePayMain)
    }

    fun displayFragment(frag: Fragment)
    {
        val fragments = listOf(GuiTricklePayMain, GuiTricklePayReg, GuiTricklePayAssetRequest, GuiTricklePayCustomTx, GuiTricklePayEmpty)

        var showedSomething = false
        for (f in fragments)
        {
            val v = f.view
            if (v == null)
            {
                LogIt.warning("NO VIEWS!!!")
            }
            else
            {
                if (f == frag)
                {
                    v.visibility = VISIBLE
                    showedSomething = true
                }
                else v.visibility = GONE
            }
        }
        if (!showedSomething)
        {
            LogIt.warning("Desired fragment is not in display list")
        }
    }

    fun analyzeCompleteAndSignTx(tx: BCHtransaction, inputSatoshis: Long?, flags: Int?): TxAnalysisResults
    {
        val wal = getRelevantWallet()

        // Find out info about the outputs
        var outputSatoshis: Long = 0
        var receivingSats: Long = 0
        var sendingSats: Long = 0
        var receivingTokenTypes: Long = 0
        var sendingTokenTypes: Long = 0
        var imSpendingTokenTypes: Long = 0
        var iFunded: Long = 0
        var ttInfo = mutableMapOf<GroupId, Long>()

        var cflags = TxCompletionFlags.FUND_NATIVE or TxCompletionFlags.SIGN or TxCompletionFlags.BIND_OUTPUT_PARAMETERS

        if (flags != null)
        {
            // If nofund flag is set turn off fund_native
            if ((flags and TDPP_FLAG_NOFUND) > 0) cflags = cflags and (TxCompletionFlags.FUND_NATIVE.inv())
            if ((flags and TDPP_FLAG_PARTIAL) > 0) cflags = cflags or TxCompletionFlags.PARTIAL
        }

        // Look at the inputs and match with UTXOs that I have, so I have the additional info required to sign this input
        val unspent = wal.unspent
        for ((idx,inp) in tx.inputs.withIndex())
        {
            val utxo = unspent[inp.spendable.outpoint]
            if (utxo != null)
            {
                tx.inputs[idx].spendable = utxo
            }
        }

        var completionException: Exception? = null
        try
        {
            // Someday the wallet might want to fund groups, etc but for now all it does is pay for txes because the wallet UX can only show that
            //(wal as CommonWallet).txCompleter2(tx, 0, cflags, inputSatoshis)
            wal.txCompleter(tx, 0, cflags, inputSatoshis)
        }
        catch(e: Exception)  // Try to report on the tx even if we can't complete it.
        {
            completionException = e
        }

        // LogIt.info("Completed tx: " + tx.toHex())

        for (inp in tx.inputs)
        {
            val address = inp.spendable.addr
            if ((address != null) && (wal.isWalletAddress(address)))
            {
                assert(inp.spendable.amount != -1L)  // Its -1 if I don't know the amount (in which case it ought to NOT be one of my inputs so should never happen)
                iFunded += inp.spendable.amount

                val iSuppliedTokens = inp.spendable.priorOutScript.groupInfo(inp.spendable.amount)
                if (iSuppliedTokens != null)
                {
                    imSpendingTokenTypes++
                    // TODO track tokens that I provided.
                }
            }
        }

        for (out in tx.outputs)
        {
            outputSatoshis += out.amount
            val address: PayAddress? = out.script.address
            val groupInfo: GroupInfo? = out.script.groupInfo(out.amount)

            if (address != null && wal.isWalletAddress(address))
            {
                receivingSats += out.amount
                if ((groupInfo != null) && (!groupInfo.isAuthority()))
                {
                    receivingTokenTypes++
                    ttInfo[groupInfo.groupId] = (ttInfo[groupInfo.groupId] ?: 0) + groupInfo.tokenAmt
                }
            }
            else
            {
                sendingSats += out.amount
                if ((groupInfo != null) && (!groupInfo.isAuthority()))
                {
                    sendingTokenTypes++
                    ttInfo[groupInfo.groupId] = (ttInfo[groupInfo.groupId] ?: 0) + groupInfo.tokenAmt
                }
            }
        }

        return TxAnalysisResults(receivingSats, sendingSats, receivingTokenTypes, sendingTokenTypes, imSpendingTokenTypes, inputSatoshis, iFunded, ttInfo, completionException)
    }

    fun parseCommonFields(uri: Uri)
    {
        proposalUri = uri
        host = uri.getHost()
        port = uri.port
        topic = uri.getQueryParameter("topic")
        cookie = uri.getQueryParameter("cookie")

        val chain = uri.getQueryParameter("chain")
        if (chain != null)
        {
            chainSelector = uriToChain[chain]
            if (chainSelector == null)
            {
                return displayError(R.string.badCryptoCode)
            }
        }
    }

    fun handleTxAutopay(uri: Uri)
    {
        proposalUri = uri

        val txHex = uri.getQueryParameter("tx")
        if (txHex == null)
        {
            return displayError(R.string.BadLink)
        }

        parseCommonFields(uri)

        tflags = uri.getQueryParameter("flags")?.toInt() ?: 0

        val inputSatoshis = uri.getQueryParameter("inamt")?.toLongOrNull()  // ?: return displayError(R.string.BadLink)

        // If we are funding then inputSatoshis must be provided
        if ((inputSatoshis == null) && ((tflags and TDPP_FLAG_NOFUND) == 0))
        {
            return displayError(R.string.BadLink)
        }

        val tx = BCHtransaction(chainSelector!!, BCHserialized(txHex.fromHex(), SerializationType.NETWORK))
        LogIt.info(sourceLoc() + ": Tx to autopay: " + tx.toHex())

        // Analyze and sign transaction
        val analysis = analyzeCompleteAndSignTx(tx, inputSatoshis, tflags)
        LogIt.info(sourceLoc() + ": Completed tx: " + tx.toHex())

        (GuiTricklePayCustomTx as TricklePayCustomTxFragment).populate(this, uri, tx, analysis)
        if ((tflags and TDPP_FLAG_PARTIAL) != 0)  // Change the title if the request is for a partial transaction
        {
            (GuiTricklePayCustomTx as TricklePayCustomTxFragment).GuiSpecialTxTitle.text = i18n(R.string.IncompleteTpTransactionFrom)
        }
        displayFragment(GuiTricklePayCustomTx)

        // Ok now that we've displayed what we can, let's throw the problem.
        if (analysis.completionException != null) throw analysis.completionException;
        // Or remember the completed transaction for accept/deny user confirmation.
        proposedTx = tx
    }

    fun handleAssetRequest(uri: Uri)
    {
        parseCommonFields(uri)

        val scriptTemplateHex = uri.getQueryParameter("af")
        if (scriptTemplateHex == null)
        {
            return displayError(R.string.BadLink)
        }

        val stemplate = BCHscript(chainSelector!!, scriptTemplateHex.fromHex())
        LogIt.info(sourceLoc() + ": Asset filter: " + stemplate.toHex())

        val wal = getRelevantWallet() as CommonWallet

        var matches = mutableListOf<TricklePayAssetInfo>()
        for ((outpoint, spendable) in wal.unspent)
        {
            if (spendable.spentHeight < 0)  // unspent
            {
                val constraint = spendable.priorOutScript
                if (constraint.matches(stemplate, true) != null)
                {
                    matches.add(TricklePayAssetInfo(constraint.flatten().toHex(), outpoint.txid.hash.toHex(), outpoint.idx.toInt(), spendable.amount))
                }
            }
        }
        val resp = TricklePayAssetList(matches)
        (GuiTricklePayAssetRequest as TricklePayAssetRequestFragment).populate(this, uri, resp.assets)

        // TODO, ask for confirmation
        displayFragment(GuiTricklePayAssetRequest)
    }

    fun handleRegistration(uri: Uri)
    {
        parseCommonFields(uri)
        val address = uri.getQueryParameter("addr")
        if (address == null)
        {
            return displayError(R.string.BadLink)
        }

        if (VerifyTdppSignature(uri) == true)
        {
            //var intent = Intent(this, TricklePayRegistrationActivity::class.java)
            //startActivityForResult(intent, TRICKLE_PAY_REG_OP_RESULT)

            regAddress = address
            // TODO allow currency UOA to be changed during registration
            regCurrency = uri.getQueryParameter("uoa") ?: TDPP_DEFAULT_UOA

            (GuiTricklePayReg as TricklePayRegFragment).populate(uri)
        }
        else
        {
            displayFragment(GuiTricklePayMain)
            displayError(R.string.badSignature)
            return
        }

        displayFragment(GuiTricklePayReg)
    }

    //? Handle tdpp intents
    fun handleNewIntent(receivedIntent: Intent)
    {
        val iuri: Uri = receivedIntent.toUri(0).toUri()  // URI_ANDROID_APP_SCHEME | URI_INTENT_SCHEME
        try
        {
            if (receivedIntent.scheme == TDPP_URI_SCHEME)
            {
                val host = iuri.getHost()
                val path = iuri.getPath()
                LogIt.info("Trickle Pay Intent host=${host} path=${path}")
                LogIt.info("Full Intent=${iuri.toString()}")
                if (path != null)
                {
                    if (path == "/reg")  // Handle registration
                    {
                        handleRegistration(iuri)
                    }
                    else if (path == "/sendto")
                    {
                        LogIt.info("address autopay")
                    }
                    else if (path == "/tx")
                    {
                        LogIt.info("tx autopay")
                        handleTxAutopay(iuri)
                    }
                    else if (path == "/assets")
                    {
                        LogIt.info("tx autopay")
                        handleAssetRequest(iuri)
                    }
                    else if (path == "/jsonpay")
                    {
                        LogIt.info("json autopay")
                    }
                    else if (path == "/lp")
                    {
                        LogIt.info("Long Poll to ${host}")
                        parseCommonFields(iuri)
                        val proto = rproto ?: TDPP_DEFAULT_PROTOCOL
                        val hostStr = host + ":" + port
                        wallyApp?.accessHandler?.startLongPolling(proto, hostStr, cookie)
                        clearIntentAndFinish(null, i18n(R.string.connectionEstablished))
                    }
                    else
                    {
                        displayFragment(GuiTricklePayEmpty)
                        displayError(i18n(R.string.unknownOperation) % mapOf("op" to path));
                    }
                }
                else
                {
                    displayFragment(GuiTricklePayEmpty)
                    displayError(i18n(R.string.unknownOperation) % mapOf("op" to "no operation"));
                }
            }
            else  // This should never happen because the AndroidManifest.xml Intent filter should match the URIs that we handle
            {
                displayError("bad link " + receivedIntent.scheme)
            }
        }
        catch (e: Exception)
        {
            LogIt.warning(e.toString())
            displayException(e)
        }
    }

    fun clearIntentAndFinish(error: String? = null, notice: String? = null)
    {
        if (error != null) intent.putExtra("error", error)
        if (error != null) intent.putExtra("notice", notice)
        setResult(Activity.RESULT_OK, intent)
        finish()
    }

    // Move the Ux data into the map
    fun RegUxToMap()
    {
        val domain = TdppDomain(GuiTricklePayEntity.toString(), GuiTricklePayTopic.toString(), regCurrency, regAddress,
            GuiAutospendLimitEntry0.text.toString().toLong(), GuiAutospendLimitEntry1.text.toString().toLong(),
            GuiAutospendLimitEntry2.text.toString().toLong(), GuiAutospendLimitEntry3.text.toString().toLong(),
            GuiAutospendLimitDescription0.toString(),
            GuiAutospendLimitDescription1.toString(),
            GuiAutospendLimitDescription2.toString(),
            GuiAutospendLimitDescription3.toString(),
            GuiEnableAutopay.isChecked)
    }

    // Trickle pay registration handlers
    fun onAcceptTpReg(view: View?)
    {
        LogIt.info("accept trickle pay registration")
        displayFragment(GuiTricklePayMain)
        /* wallyApp?.let {
            it.finishParent += 1
        } */
        RegUxToMap()
        later {
            save()  // can't save in UI thread
            clearIntentAndFinish(notice = i18n(R.string.TpRegAccepted))
        }
    }

    fun onDenyTpReg(view: View?)
    {
        LogIt.info("deny trickle pay registration")
        displayFragment(GuiTricklePayMain)
        /* wallyApp?.let {
            it.finishParent += 1
        } */
        clearIntentAndFinish(notice = i18n(R.string.TpRegDenied))
    }

    // Trickle pay transaction handlers
    fun onSignSpecialTx(view: View?)
    {
        LogIt.info("accept trickle pay special transaction")
        val pTx = proposedTx
        if (pTx != null)
        {
            val pUri = proposalUri!!  // if proposedTx != null uri must have something
            val pcookie = cookie
            proposedTx = null
            proposalUri = null
            cookie = null
            LogIt.info("sign trickle pay special transaction")
            displayFragment(GuiTricklePayMain)

            later {
                val proto = "http:"
                val host = pUri.host + ":" + pUri.port
                val cookieString = if (pcookie != null) "&cookie=$pcookie" else ""
                val req = URL(proto + "//" + host + "/tx?tx=${pTx.toHex()}${cookieString}")
                val data = try
                {
                    req.readText()
                }
                catch (e: java.io.FileNotFoundException)
                {
                    LogIt.info("Error submitting transaction: " + e.message)
                    displayError(i18n(R.string.WebsiteUnavailable))
                    return@later
                }
                catch (e: Exception)
                {
                    LogIt.info("Error submitting transaction: " + e.message)
                    displayError(i18n(R.string.WebsiteUnavailable))
                    return@later
                }
                LogIt.info(sourceLoc() + " TP response to the response: " + data)

                clearIntentAndFinish(notice = i18n(R.string.TpTxAccepted))
            }
        }
    }

    fun onDenySpecialTx(view: View?)
    {
        LogIt.info("deny trickle pay special transaction")
        displayFragment(GuiTricklePayMain)
        //wallyApp?.let {
        //    it.finishParent += 1
        //}
        clearIntentAndFinish(notice = i18n(R.string.TpTxDenied))
    }

    fun onAcceptAssetRequest(view: View?)
    {
        LogIt.info("accepted asset request")
        displayNotice(R.string.Processing, time = 4900)

        val proto = rproto ?: TDPP_DEFAULT_PROTOCOL
        val host = host + ":" + port
        val cookieString = if (cookie != null) "&cookie=$cookie" else ""
        val assets = (GuiTricklePayAssetRequest as TricklePayAssetRequestFragment).selectedAssets()
        val url = proto + "//" + host + "/assets?" + cookieString
        later {
            LogIt.info("responding to server")
            val client = HttpClient(Android)
            {
                install(JsonFeature)
                install(HttpTimeout) { requestTimeoutMillis = 5000 }
            }
            val json = io.ktor.client.features.json.defaultSerializer()

            try
            {

                val response: HttpResponse = client.post(url) {
                    val tmp = json.write(assets)
                    LogIt.info("JSON response ${tmp.contentLength} : " + tmp.toString())
                    body = json.write(assets)
                }
                val respText = response.readText()
                clearIntentAndFinish(notice = respText)
            }
            catch (e: SocketTimeoutException)
            {
                displayError(R.string.connectionException)
            }
            client.close()
        }

        // A successful connection to the server will auto-close this activity
    }

    fun onDenyAssetRequest(view: View?)
    {
        LogIt.info("rejected asset request")
        displayFragment(GuiTricklePayMain)

        clearIntentAndFinish(notice = i18n(R.string.TpAssetRequestDenied))
    }


}
