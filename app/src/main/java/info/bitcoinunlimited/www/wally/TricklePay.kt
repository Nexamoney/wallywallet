// Copyright (c) 2019 Andrew Stone Consulting (qq9wwnuw4eukyh5g34ckg5vk4aaxnvr04vkspyv850)
// Distributed under the MIT software license, see the accompanying file COPYING or http://www.opensource.org/licenses/mit-license.php.
package info.bitcoinunlimited.www.wally

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.Keep
import androidx.core.app.NavUtils
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import bitcoinunlimited.libbitcoincash.*
import bitcoinunlimited.libbitcoincash.rem
import info.bitcoinunlimited.www.wally.databinding.*
import io.ktor.client.*
import io.ktor.client.engine.android.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import java.lang.Exception
import java.net.SocketTimeoutException
import java.net.URL
import java.util.logging.Logger
import java.math.BigDecimal
import java.net.URLEncoder
import kotlin.random.Random

val TDPP_URI_SCHEME = "tdpp"
val TDPP_DEFAULT_UOA = "NEX"
val TDPP_DEFAULT_PROTOCOL = "http:"

// TODO: extract tdpp protocol stuff into a library
const val TDPP_FLAG_NOFUND = 1
const val TDPP_FLAG_NOPOST = 2
const val TDPP_FLAG_NOSHUFFLE = 4
const val TDPP_FLAG_PARTIAL = 8

private val LogIt = Logger.getLogger("BU.wally.TricklePay")


// Must be top level for the serializer to handle it
@Keep
@kotlinx.serialization.Serializable
data class TricklePayAssetInfo(val outpointHash: String, val amt: Long, val prevout: String, val proof: String? = null)

@Keep
@kotlinx.serialization.Serializable
data class TricklePayAssetList(val assets: List<TricklePayAssetInfo>)

fun makeChallengeTx(sp: Spendable, challengerId: ByteArray, chalby: ByteArray): iTransaction?
{
    if (chalby.size < 8 || chalby.size > 64) return null
    val rb = Random.nextBytes(chalby.size)
    val moddedChal = ByteArray(chalby.size * 2)
    for (i in 0 until chalby.size)
    {
        moddedChal[i * 2] = rb[i]
        moddedChal[(i * 2) + 1] = chalby[i]
    }
    val cs = sp.chainSelector
    val tx = txFor(cs)
    tx.add(txInputFor(sp))
    tx.add(txOutputFor(cs, 0, SatoshiScript(cs, SatoshiScript.Type.SATOSCRIPT, OP.RETURN, OP.push(challengerId), OP.push(moddedChal))))
    (tx as NexaTransaction).version = OWNERSHIP_CHALLENGE_VERSION_MASK
    signTransaction(tx)
    return tx
}

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
enum class TdppAction(val v: Byte)
{
    DENY(0),
    ASK(1),
    ACCEPT(2);

    companion object
    {
        fun of(v: Byte): TdppAction
        {
            return when (v)
            {
                DENY.v -> DENY
                ASK.v -> ASK
                ACCEPT.v -> ACCEPT
                else -> throw DeserializationException("Deserialization error in Trickle Pay data")
            }
        }
    }

    operator fun inc(): TdppAction
    {
        if (this == DENY) return ASK
        if (this == ASK) return ACCEPT
        if (this == ACCEPT) return DENY
        else return ASK
    }

    override fun toString(): String
    {
        if (this == DENY) return i18n(R.string.deny)
        if (this == ASK) return i18n(R.string.ask)
        if (this == ACCEPT) return i18n(R.string.accept)
        return ""
    }
}

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
) : BCHserializable
{
    @cli(Display.Simple, "Maximum automatic payment exeeded action")
    var maxperExceeded: TdppAction = TdppAction.ASK

    @cli(Display.Simple, "Maximum automatic payment per day exeeded action")
    var maxdayExceeded: TdppAction = TdppAction.ASK

    @cli(Display.Simple, "Maximum automatic payment per week exeeded action")
    var maxweekExceeded: TdppAction = TdppAction.ASK

    @cli(Display.Simple, "Maximum automatic payment per month exeeded action")
    var maxmonthExceeded: TdppAction = TdppAction.ASK

    @cli(Display.Simple, "Asset information query")
    var assetInfo: TdppAction = TdppAction.ASK

    @cli(Display.Simple, "Balance information query")
    var balanceInfo: TdppAction = TdppAction.ASK

    constructor(uri: Uri) : this("", "", "", "", -1, -1, -1, -1, "", "", "", "", false)
    {
        load(uri)
    }

    // This is the implicit registration constructor -- it does not authorize any automatic payments.
    constructor(_domain: String, _topic: String) : this(_domain, _topic, "", "", 0, 0, 0, 0, "", "", "", "", false)
    {
    }

    constructor(stream: BCHserialized) : this("", "", "", "", -1, -1, -1, -1, "", "", "", "", false)
    {
        BCHdeserialize(stream)
    }

    override fun BCHserialize(format: SerializationType): BCHserialized //!< Serializer
    {
        return BCHserialized(format) + domain + topic + addr + uoa +
          maxper + maxday + maxweek + maxmonth +
          descper + descday + descweek + descmonth +
          automaticEnabled +
          maxperExceeded.v + maxdayExceeded.v + maxweekExceeded.v + maxmonthExceeded.v +
          assetInfo.v + balanceInfo.v
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

        maxperExceeded = TdppAction.of(stream.debyte())
        maxdayExceeded = TdppAction.of(stream.debyte())
        maxweekExceeded = TdppAction.of(stream.debyte())
        maxmonthExceeded = TdppAction.of(stream.debyte())

        assetInfo = TdppAction.of(stream.debyte())
        balanceInfo = TdppAction.of(stream.debyte())

        return stream
    }

    fun getParam(u: Uri, amtP: String, descP: String): Pair<Long, String>
    {
        val amount: Long = u.getQueryParameter(amtP).let {
            if (it == null) -1
            else it.toLong()
        }
        val desc: String = u.getQueryParameter(descP) ?: ""
        return Pair(amount, desc)
    }


    fun load(uri: Uri)
    {
        domain = uri.getHost() ?: throw NotUriException()
        topic = uri.getQueryParameter("topic") ?: ""
        addr = uri.getQueryParameter("addr") ?: ""
        getParam(uri, "maxper", "descper").let { maxper = it.first; descper = it.second }
        getParam(uri, "maxday", "descday").let { maxday = it.first; descday = it.second }
        getParam(uri, "maxweek", "descweek").let { maxweek = it.first; descweek = it.second }
        getParam(uri, "maxmonth", "descmonth").let { maxmonth = it.first; descmonth = it.second }
    }
}


class TricklePayEmptyFragment : Fragment()
{
    public lateinit var ui:TricklePayEmptyBinding
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View?
    {
        //val ret = inflater.inflate(R.layout.trickle_pay_empty, container, false)
        ui = TricklePayEmptyBinding.inflate(inflater)
        return ui.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?)
    {
        super.onViewCreated(view, savedInstanceState)
    }
}

class TricklePayMainFragment : Fragment()
{
    public lateinit var ui:TricklePayMainBinding
    //private lateinit var adapter: TricklePayRecyclerAdapter
    private lateinit var adapter: GuiList<TdppDomain, TpDomainBinder>
    private lateinit var linearLayoutManager: LinearLayoutManager
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View?
    {
        ui = TricklePayMainBinding.inflate(inflater)
        //val ret = inflater.inflate(R.layout.trickle_pay_main, container, false)
        return ui.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?)
    {
        super.onViewCreated(view, savedInstanceState)
        val tpAct = getActivity() as TricklePayActivity
        //adapter = TricklePayRecyclerAdapter(tpAct)
        adapter = GuiList(ui.GuiTricklePayList, listOf(), tpAct, {
            val ui = TpDomainListItemBinding.inflate(LayoutInflater.from(it.context), it, false)
            TpDomainBinder(ui)
        })
        adapter.rowBackgroundColors = arrayOf(0xFFEEFFEE.toInt(), 0xFFBBDDBB.toInt())


        linearLayoutManager = LinearLayoutManager(tpAct)
        ui.GuiTricklePayList.layoutManager = linearLayoutManager
    }

    fun populate()
    {
        val tpAct = getActivity() as TricklePayActivity
        val domains = ArrayList(tpAct.domains.values)
        adapter.set(domains)
        /*
        ui.GuiTricklePayList.adapter = null
        ui.GuiTricklePayList.layoutManager = null

        ui.GuiTricklePayList.adapter = adapter
        ui.GuiTricklePayList.layoutManager = linearLayoutManager
        adapter.notifyDataSetChanged()
        linearLayoutManager.requestLayout()

         */
    }

}

/* Handle a trickle pay Registration */
class TricklePayRegFragment : Fragment()
{
    public lateinit var ui:TricklePayRegBinding
    //var uri: Uri? = null
    public var domain: TdppDomain? = null
    public var editingReg: Boolean = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View?
    {
        ui = TricklePayRegBinding.inflate(inflater)
        return ui.root
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


    fun populate(d: TdppDomain, editingRegistration: Boolean = true)
    {
        editingReg = editingRegistration
        domain = d
        updateUI()
    }


    fun updateUI()
    {
        val d = domain
        if (d != null)
        {
            if (d.uoa == "") d.uoa = "nexa"
            if (d.uoa == "NEX") d.uoa = "nexa"
            val accountLst = wallyApp!!.accountsFor(d.uoa)
            if (accountLst.size == 0)
            {
                throw WalletInvalidException()
            }
            val account = accountLst[0]  // Just pick the first compatible wallet

            ui.GuiTricklePayEntity.text = d.domain
            ui.GuiTricklePayTopic.text = d.topic
            ui.TpAssetInfoRequestHandlingButton.text = d.assetInfo.toString()

            ui.GuiEnableAutopay.setChecked(d.automaticEnabled)

            ui.GuiAutospendLimitEntry0.text.clear()
            if (d.maxper == -1L)
            {
                ui.GuiAutospendLimitEntry0.text.append(i18n(R.string.unspecified))
            }
            else
            {
                ui.GuiAutospendLimitEntry0.text.append(account.format(account.fromFinestUnit(d.maxper)))
            }
            ui.GuiAutospendLimitDescription0.text = d.descper

            ui.GuiAutospendLimitEntry1.text.clear()
            if (d.maxday == -1L)
                ui.GuiAutospendLimitEntry1.text.append(i18n(R.string.unspecified))
            else
                ui.GuiAutospendLimitEntry1.text.append(account.format(account.fromFinestUnit(d.maxday)))
            ui.GuiAutospendLimitDescription1.text = d.descday

            ui.GuiAutospendLimitEntry2.text.clear()
            if (d.maxweek == -1L)
                ui.GuiAutospendLimitEntry2.text.append(i18n(R.string.unspecified))
            else
                ui.GuiAutospendLimitEntry2.text.append(account.format(account.fromFinestUnit(d.maxweek)))
            ui.GuiAutospendLimitDescription2.text = d.descweek

            ui.GuiAutospendLimitEntry3.text.clear()
            if (d.maxmonth == -1L)
                ui.GuiAutospendLimitEntry3.text.append(i18n(R.string.unspecified))
            else
                ui.GuiAutospendLimitEntry3.text.append(account.format(account.fromFinestUnit(d.maxmonth)))
            ui.GuiAutospendLimitDescription3.text = d.descmonth
        }

        if (editingReg)
        {
            ui.GuiAcceptRegTitle.text = i18n(R.string.EditTpRegistration)
            ui.GuiTpRegisterRequestAccept.setVisibility(View.GONE)
            ui.GuiTpDenyRegisterRequest.text = i18n(R.string.remove)
            ui.GuiTpDenyRegisterRequest.setVisibility(View.VISIBLE)
            ui.GuiTpOkRegisterRequest.setVisibility(View.VISIBLE)
        }
        else
        {
            ui.GuiAcceptRegTitle.text = i18n(R.string.AcceptTpRegistration)
            ui.GuiTpOkRegisterRequest.setVisibility(View.GONE)
            ui.GuiTpRegisterRequestAccept.setVisibility(View.VISIBLE)
            ui.GuiTpDenyRegisterRequest.setVisibility(View.VISIBLE)
        }
    }
}

/* Handle a trickle pay Registration */
class TricklePayCustomTxFragment : Fragment()
{
    public lateinit var ui: TricklePayCustomTxBinding
    public var uri: Uri? = null
    public var tx: iTransaction? = null
    public var analysis: TxAnalysisResults? = null
    public var tpActivity: TricklePayActivity? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View?
    {
        ui = TricklePayCustomTxBinding.inflate(inflater)
        //val ret = inflater.inflate(R.layout.trickle_pay_custom_tx, container, false)
        return ui.root
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

    fun populate(pactivity: TricklePayActivity, puri: Uri, ptx: iTransaction, panalysis: TxAnalysisResults)
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

        val tpc = u.getQueryParameter("topic").let {
            if (it == null) ""
            else ":" + it
        }
        ui.GuiTricklePayEntity.text = u.authority + tpc

        val acc = tpActivity!!.getRelevantAccount()

        ui.GuiCustomTxBlockchain.text = chainToURI[acc.chain.chainSelector]

        val a = analysis
        if (a != null)
        {
            // what's being paid to me - what I'm contributing.  So if I pay out then its a negative number
            val netSats = a.receivingSats - a.myInputSatoshis

            if (netSats > 0)
            {
                ui.GuiCustomTxCost.text = (i18n(R.string.receiving) + " " + acc.cryptoFormat.format(acc.fromFinestUnit(netSats)) + " " + acc.currencyCode)
            }
            else if (netSats < 0)
            {
                val txt = i18n(R.string.sending) + " " + acc.cryptoFormat.format(acc.fromFinestUnit(-netSats)) + " " + acc.currencyCode
                LogIt.info(txt)
                ui.GuiCustomTxCost.text = txt
            }
            else
            {
                ui.GuiCustomTxCost.text = i18n(R.string.nothing)
            }

            if (a.otherInputSatoshis != null)
            {
                val fee = (a.myInputSatoshis + a.otherInputSatoshis) - (a.receivingSats + a.sendingSats)
                if (fee > 0)
                {
                    ui.GuiCustomTxFee.text = (i18n(R.string.ForAFeeOf) % mapOf("fee" to acc.cryptoFormat.format(acc.fromFinestUnit(fee)), "units" to acc.currencyCode))
                }
                else  // Almost certainly the requester is going to fill out more of this tx so the fee is kind of irrelevant.  TODO: only show the fee if this wallet is paying it (tx is complete)
                {
                    ui.GuiCustomTxFee.text = ""
                }
            }
            else
            {
                ui.GuiCustomTxFee.text = ""
            }

            // Expand the text to handle proving ownership of (that is, sending token to yourself)

            if (a.receivingTokenTypes > 0)
            {
                if (a.imSpendingTokenTypes > 0)
                {
                    // This is not strictly true.  The counterparty could hand you a transaction that both supplies a token and spends that token to themselves...
                    ui.GuiCustomTxTokenSummary.text = i18n(R.string.TpExchangingTokens) % mapOf("tokSnd" to a.sendingTokenTypes.toString(), "tokRcv" to a.receivingTokenTypes.toString())
                }
                else
                {
                    ui.GuiCustomTxTokenSummary.text = i18n(R.string.TpReceivingTokens) % mapOf("tokRcv" to a.receivingTokenTypes.toString())
                }
            }
            else
            {
                // This needs more thought.  imSpendingTokenTypes are the tokens that are being input into the transaction
                // sendingTokenTypes are those that are being output.
                if (a.imSpendingTokenTypes > 0)
                {
                    ui.GuiCustomTxTokenSummary.text = i18n(R.string.TpSendingTokens) % mapOf("tokSnd" to a.imSpendingTokenTypes.toString())
                }
                if (a.sendingTokenTypes > 0)
                {
                    ui.GuiCustomTxTokenSummary.text = i18n(R.string.TpSendingTokens) % mapOf("tokSnd" to a.sendingTokenTypes.toString())
                }
            }

        }
    }
}

/* Handle a trickle pay Registration */
class TricklePaySendToFragment : Fragment()
{
    public lateinit var ui: TricklePaySendToBinding
    public var uri: Uri? = null
    public var tpActivity: TricklePayActivity? = null
    public var askReasons: List<String>? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View?
    {
        ui = TricklePaySendToBinding.inflate(inflater)
        //val ret = inflater.inflate(R.layout.trickle_pay_send_to, container, false)
        return ui.root
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

    fun populate(pactivity: TricklePayActivity, puri: Uri, reasons: List<String>)
    {
        uri = puri
        askReasons = reasons
        tpActivity = pactivity
        updateUI()
    }

    fun updateUI()
    {
        try
        {
        val act = tpActivity ?: return  // resumed but not populated with data yet
        val u: Uri = uri ?: return
        val sa: List<Pair<PayAddress, Long>> = act.proposedDestinations ?: return

        val tpc = u.getQueryParameter("topic").let {
            if (it == null) ""
            else ":" + it
        }

        ui.GuiSendToTricklePayEntity.text = u.authority + tpc

        val acc = act.getRelevantAccount()
        ui.GuiSendToBlockchain.text = chainToURI[acc.chain.chainSelector]

        var total = 0L
        for (it in sa)
        {
            total += it.second
        }
            ui.GuiSendToCost.text = acc.cryptoFormat.format(acc.fromFinestUnit(total)) + " " + acc.currencyCode

            ui.GuiSendToPurpose.text = tpActivity?.reason ?: ""

        val ars = askReasons
        if (ars != null && ars.size > 0)
        {
            ui.GuiSendsToAskReasons.text = ars.joinToString("\n")
        }
        }
        catch(e:WalletInvalidException)
        {
            laterUI {
                tpActivity?.displayFragment(R.id.GuiTricklePayMain)
                tpActivity?.displayError(R.string.NoAccounts)
            }
        }
    }
}


/* Handle a trickle pay Registration */
class TricklePayAssetRequestFragment : Fragment()
{
    public lateinit var ui: TricklePayAssetRequestBinding
    public var uri: Uri? = null
    public var tpActivity: TricklePayActivity? = null
    public var assets: List<TricklePayAssetInfo>? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View?
    {
        ui = TricklePayAssetRequestBinding.inflate(inflater)
        //val ret = inflater.inflate(R.layout.trickle_pay_asset_request, container, false)
        return ui.root
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

        val tpc = u.getQueryParameter("topic").let {
            if (it == null) ""
            else ":" + it
        }
        ui.GuiTricklePayEntity.text = u.authority + tpc

        val acc = tpActivity!!.getRelevantAccount()
        ui.GuiAssetHandledByAccount.text = acc.name
        ui.GuiAssetAcceptQ3.text = i18n(R.string.TpAssetMatches) % mapOf("num" to (assets?.size ?: 0).toString())
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
    val sig = Wallet.signMessage(signThis.toByteArray(), secret.getSecret())
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
    suri.encodedAuthority(uri.authority)
    suri.path(uri.path)
    val orderedParams = uri.queryParameterNames.toList().sorted()
    val queryParam = mutableListOf<String>()
    for (p in orderedParams)
    {
        if (p == "sig") continue
        val tmp = uri.getQueryParameter(p)
        val tmp2 = URLEncoder.encode(tmp, "utf-8")
        queryParam.add(p + "=" + tmp2)
        // this does normal URL encoding (e.g. %20 for space) not form encoding (e.g. + for space).  But we need form encoding
        //suri.appendQueryParameter(p, uri.getQueryParameter(p))
    }
    suri.encodedQuery(queryParam.joinToString("&"))

    val verifyThis = suri.build().toString()

    val pa = PayAddress(addressStr)

    LogIt.info("verification for: " + verifyThis + " Address: " + addressStr)
    LogIt.info("Message hex: " + verifyThis.toByteArray().toHex())
    LogIt.info("Raw Address: " + pa.data.toHex())
    val sigBytes = try
    {
        Codec.decode64(sig)
    } catch (e: IllegalStateException)
    {
        LogIt.info("Verification failed for: " + verifyThis + " Address: " + addressStr + " Cannot decode64 sig.")
        return false
    }
    LogIt.info("Sig: " + sigBytes.toHex())
    val result = Wallet.verifyMessage(verifyThis.toByteArray(), pa.data, sigBytes)
    if (result == null || result.size == 0)
    {
        LogIt.info("verification failed for: " + verifyThis + " Address: " + addressStr)
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


class TpDomainBinder(val ui: TpDomainListItemBinding): GuiListItemBinder<TdppDomain>(ui.root)
{
    override fun populate()
    {
        ui.GuiTpDomainListHost.text = data?.domain
        ui.GuiTpDomainListTopic.text = data?.topic
    }

    override fun onClick(v: View)
    {
        val activity = v.context as TricklePayActivity
        synchronized(activity.viewSync)
        {
            LogIt.info("onclick: " + pos + " " + activity.showingDetails)
            data?.let {
                activity.setSelectedDomain(it)
                activity.displayFragment(R.id.GuiTricklePayReg)
            }
        }
    }
}

class TricklePayActivity : CommonNavActivity()
{
    public lateinit var ui: ActivityTricklePayBinding
    override var navActivityId = R.id.navigation_trickle_pay
    var db: KvpDatabase? = null

    fun domainKey(host: String, topic: String? = null): String = host + "/" + (topic ?: "")
    var domains: MutableMap<String, TdppDomain> = mutableMapOf()
    var domainsLoaded: Boolean = false

    val SER_VERSION: Byte = 1.toByte()

    /** The currency selected as the unit of account during registration/configuration */
    var regCurrency: String = ""
    var regAddress: String = ""

    var tflags: Int = 0
    var proposedTx: iTransaction? = null
    var proposalUri: Uri? = null
    var proposedDestinations: List<Pair<PayAddress, Long>>? = null
    var proposalCookie: String? = null
    var host: String? = null
    var port: Int = 80
    var topic: String? = null
    var sig: String? = null
    var cookie: String? = null
    var reason: String? = null
    var rproto: String? = null
    var rpath: String? = null
    var chainSelector: ChainSelector? = null
    var visibleFragment: Int = -1

    fun setSelectedDomain(d: TdppDomain)
    {
        host = d.domain
        topic = d.topic
        val frag: TricklePayRegFragment = fragment(R.id.GuiTricklePayReg)
        frag.populate(d)
    }

    val viewSync = ThreadCond()
    var showingDetails = false

    // Alternate colors for each row in the list
    val Acol: Int = appContext?.let { ContextCompat.getColor(it.context, R.color.rowA) } ?: 0xFFEEFFEE.toInt()
    val Bcol: Int = appContext?.let { ContextCompat.getColor(it.context, R.color.rowB) } ?: 0xFFBBDDBB.toInt()

    override fun onCreate(savedInstanceState: Bundle?)
    {
        super.onCreate(savedInstanceState)
        ui = ActivityTricklePayBinding.inflate(layoutInflater)
        setContentView(ui.root)

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
            synchronized(domains)
            {
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
                    } catch (e: DataMissingException)
                    {
                        domainsLoaded = true
                        LogIt.info("benign: no TDPP domains registered yet")
                    } catch (e: Exception)
                    {
                        domainsLoaded = true  // well we tried anyway
                        logThreadException(e, "TDPP domain data corruption (or updated)", sourceLoc())
                    }
                }
                domainsLoaded = true
                (fragment(R.id.GuiTricklePayMain) as TricklePayMainFragment).populate()
            }
        }
    }

    fun save()
    {
        notInUI {
            synchronized(domains)
            {
                if (domainsLoaded)  // If we save the domains before we load them, we'll erase them!
                {
                    val ser = BCHserialized.uint8(SER_VERSION)
                    ser.add(BCHserialized.map(domains,
                      {
                          BCHserialized(SerializationType.DISK).add(it)
                      },
                      {
                          it.BCHserialize()
                      }, SerializationType.DISK))
                    db?.let {
                        it.set("tdppDomains", ser.flatten())
                    }
                }
            }
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
        if (!launchedFromRecent() && (intent.scheme != null)) // its null if normal app startup
        {
            displayFragment(R.id.GuiTricklePayEmpty)
            handleNewIntent(intent)
        }
        else
        {
            displayFragment(R.id.GuiTricklePayMain)
        }
    }

    fun displayFragment(fragId: Int)
    {
        val fm = getSupportFragmentManager()
        val frag = fm.findFragmentById(fragId)
        val tpFrags = mutableListOf<Int>(R.id.GuiTricklePayMain, R.id.GuiTricklePayReg, R.id.GuiTricklePayCustomTx, R.id.GuiTricklePaySendTo, R.id.GuiTricklePayEmpty, R.id.GuiTricklePayAssetRequest)
        tpFrags.remove(fragId)
        val hideFrags = tpFrags.map { fm.findFragmentById(it)}
        if (frag != null)
        {
            visibleFragment = fragId
            val tx = fm.beginTransaction()
            for (f in hideFrags) if (f != null) tx.hide(f)
            tx.show(frag)
            tx.commit()
        }
    }

    fun analyzeCompleteAndSignTx(tx: iTransaction, inputSatoshis: Long?, flags: Int?): TxAnalysisResults
    {
        // Just explain why nothing will work
        //if (domains.size == 0)
        //    displayError(R.string.TpNoRegistrations)

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
        val txos = wal.txos
        for ((idx, inp) in tx.inputs.withIndex())
        {
            val utxo = txos[inp.spendable.outpoint]
            if (utxo != null)
            {
                tx.inputs[idx].spendable = utxo
            }
        }

        // Complete and sign the transaction
        var completionException: Exception? = null
        try
        {
            // Someday the wallet might want to fund groups, etc but for now all it does is pay for txes because the wallet UX can only show that
            //(wal as CommonWallet).txCompleter2(tx, 0, cflags, inputSatoshis)
            wal.txCompleter(tx, 0, cflags, inputSatoshis)
        }
        catch (e: Exception)  // Try to report on the tx even if we can't complete it.
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
        reason = uri.getQueryParameter("reason")

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

    fun handleTxAutopay(uri: Uri, domain: TdppDomain)
    {
        // Just explain why nothing will work
        if (domains.size == 0)
            displayError(R.string.TpNoRegistrations)

        proposalUri = uri
        proposedTx = null
        proposedDestinations = null

        val txHex = uri.getQueryParameter("tx")
        if (txHex == null)
        {
            return displayError(R.string.BadLink, "missing tx parameter")
        }

        parseCommonFields(uri)
        if (chainSelector == null)
        {
            displayFragment(R.id.GuiTricklePayMain)
            return displayError(R.string.badCryptoCode)  // Chain is a mandatory field
        }


        tflags = uri.getQueryParameter("flags")?.toInt() ?: 0

        val inputSatoshis = uri.getQueryParameter("inamt")?.toLongOrNull()  // ?: return displayError(R.string.BadLink)

        // If we are funding then inputSatoshis must be provided
        if ((inputSatoshis == null) && ((tflags and TDPP_FLAG_NOFUND) == 0))
        {
            displayFragment(R.id.GuiTricklePayMain)
            return displayError(R.string.BadLink, "missing inamt parameter")
        }

        val tx = txFor(chainSelector!!, BCHserialized(txHex.fromHex(), SerializationType.NETWORK))
        LogIt.info(sourceLoc() + ": Tx to autopay: " + tx.toHex())

        // Analyze and sign transaction
        val analysis = try
        {
            analyzeCompleteAndSignTx(tx, inputSatoshis, tflags)
        }
        catch (e: WalletInvalidException)  // probably don't have an account unlocked for this crypto
        {
            displayFragment(R.id.GuiTricklePayMain)
            return displayException(e)
        }
        LogIt.info(sourceLoc() + ": Completed tx: " + tx.toHex())

        val frag = fragment(R.id.GuiTricklePayCustomTx) as TricklePayCustomTxFragment
        frag.populate(this, uri, tx, analysis)
        // Change the title if the request is for a partial transaction
        if ((tflags and TDPP_FLAG_PARTIAL) != 0)
        {
            frag.ui.GuiSpecialTxTitle.text = i18n(R.string.IncompleteTpTransactionFrom)
        }

        if (analysis.completionException == null) proposedTx = tx
        laterUI {
            displayFragment(R.id.GuiTricklePayCustomTx)
            // Ok now that we've displayed what we can, let's show the problem.
            if (analysis.completionException != null) displayException(analysis.completionException)
        }
    }

    fun handleSendToAutopay(uri: Uri, domain: TdppDomain)
    {
        // Just explain why nothing will work
        if (domains.size == 0)
        {
            displayFragment(R.id.GuiTricklePayMain)
            return displayError(R.string.TpNoRegistrations)
        }

        proposalUri = uri
        proposedTx = null
        proposedDestinations = null

        val addrAmt = mutableListOf<Pair<PayAddress, Long>>()
        var count = 0
        var total = 0L
        while (true)
        {
            val amtS = uri.getQueryParameter("amt" + count.toString())
            val addrS = uri.getQueryParameter("addr" + count.toString())

            // if one exists but not the other, request is bad
            if ((amtS != null) xor (addrS != null))
            {
                displayFragment(R.id.GuiTricklePayMain)
                return displayError(R.string.BadLink, "missing parameter")
            }
            // if either do not exist, done
            if ((amtS == null) || (addrS == null)) break

            val amt = amtS.toLong()
            if (amt <= 0) return displayError(R.string.BadLink, "bad amount")
            addrAmt.add(Pair(PayAddress(addrS), amt.toLong()))
            val priorTotal = total
            total += amt
            if (total < priorTotal)
            {
                displayFragment(R.id.GuiTricklePayMain)
                return displayError(R.string.BadLink, "bad amount")
            }  // amounts wrapped around
            count++
        }

        parseCommonFields(uri)
        if (chainSelector == null)
        {
            displayFragment(R.id.GuiTricklePayMain)
            return displayError(R.string.badCryptoCode)  // Chain is a mandatory field
        }

        proposedDestinations = addrAmt

        val askReasons = mutableListOf<String>()

        if (total > domain.maxper)
        {
            if (domain.maxperExceeded == TdppAction.DENY)
            {
                displayFragment(R.id.GuiTricklePayMain)
                laterUI { rejectSendToRequest(R.string.TpSendAutoReject) }
                return
            }
            else askReasons.add(i18n(R.string.TpExceededMaxPer))
        }

        // TODO maxday, maxweek, maxmonth

        if (!domain.automaticEnabled) askReasons.add(i18n(R.string.TpAutomaticDisabled))

        if (askReasons.size == 0)
        {
            acceptSendToRequest()
            return
        }
        else
        {
            // If it wasn't automatically accepted or rejected, ask
            laterUI {
                val frag:TricklePaySendToFragment = fragment(R.id.GuiTricklePaySendTo)
                frag.populate(this, uri, askReasons)
                displayFragment(R.id.GuiTricklePaySendTo)
            }
        }
    }

    fun handleAssetRequest(uri: Uri, domain: TdppDomain)
    {
        if (domain.assetInfo == TdppAction.DENY)
        {
            displayFragment(R.id.GuiTricklePayMain)
            return displayError(R.string.TpRequestAutoDeny)
        }

        parseCommonFields(uri)

        val scriptTemplateHex = uri.getQueryParameter("af")
        if (scriptTemplateHex == null)
        {
            displayFragment(R.id.GuiTricklePayMain)
            return displayError(R.string.BadLink, "missing 'af' parameter")
        }

        val chalbyStr = uri.getQueryParameter("chalby")
        val chalby = chalbyStr?.fromHex()

        val stemplate = SatoshiScript(chainSelector!!, SatoshiScript.Type.SATOSCRIPT, scriptTemplateHex.fromHex())
        LogIt.info(sourceLoc() + ": Asset filter: " + stemplate.toHex())


        val wal = try
        {
            getRelevantWallet() as CommonWallet
        }
        catch (e: WalletInvalidException)
        {
            displayFragment(R.id.GuiTricklePayMain)
            clearIntentAndFinish(error = i18n(R.string.badCryptoCode))
            return
        }

        val challengerId = host?.toByteArray()

        var matches = mutableListOf<TricklePayAssetInfo>()
        for ((outpoint, spendable) in wal.txos)
        {
            if (spendable.spentHeight < 0)  // unspent
            {
                val constraint = spendable.priorOutScript
                if (constraint.matches(stemplate, true) != null)
                {
                    val serPrevout = spendable.prevout.BCHserialize(SerializationType.NETWORK).toHex()
                    matches.add(
                      TricklePayAssetInfo(
                        outpoint.toHex(), spendable.amount, serPrevout,
                        if (chalby != null && challengerId != null) makeChallengeTx(spendable, challengerId, chalby)?.toHex() else null,
                      )
                    )
                }
            }
        }
        val resp = TricklePayAssetList(matches)

        if (domain.assetInfo == TdppAction.ASK)
        {
            (fragment(R.id.GuiTricklePayAssetRequest) as TricklePayAssetRequestFragment).populate(this, uri, resp.assets)
            // ask for confirmation
            displayFragment(R.id.GuiTricklePayAssetRequest)
        }
        if (domain.assetInfo == TdppAction.ACCEPT)
        {
            displayNotice(R.string.TpRequestAutoAccept)
            acceptAssetRequest(resp)
        }
    }

    fun handleRegistration(uri: Uri): Boolean
    {
        parseCommonFields(uri)
        val address = uri.getQueryParameter("addr")
        if (address == null)
        {
            laterUI {
                displayFragment(R.id.GuiTricklePayMain)
                clearIntentAndFinish(error = i18n(R.string.badLink))
            }
            return false
        }

        if (VerifyTdppSignature(uri) == true)
        {
            //var intent = Intent(this, TricklePayRegistrationActivity::class.java)
            //startActivityForResult(intent, TRICKLE_PAY_REG_OP_RESULT)

            regAddress = address
            // TODO allow currency UOA to be changed during registration
            regCurrency = uri.getQueryParameter("uoa") ?: TDPP_DEFAULT_UOA

            val d = TdppDomain(uri)
            try
            {
                (fragment(R.id.GuiTricklePayReg) as TricklePayRegFragment).populate(d, false)
            }
            catch (e: WalletInvalidException)
            {
                laterUI {
                    displayFragment(R.id.GuiTricklePayMain)
                    displayError(R.string.NoAccounts)
                }
                return false
            }
        }
        else
        {
            laterUI {
                displayFragment(R.id.GuiTricklePayMain)
                displayError(R.string.badSignature)
            }
            return false
        }

        laterUI {
            displayFragment(R.id.GuiTricklePayReg)
        }
        return true
    }

    fun loadCreateDomain(host: String, topic:String): TdppDomain
    {
        return synchronized(domains)
        {
            if (!domainsLoaded) load()
            var d = domains[domainKey(host, topic)]
            if (d == null)
            {
                // delay and try again because load() cannot happen in the gui thread
                d = TdppDomain(host, topic)
                domains[domainKey(host, topic)] = d
                save()
            }
            d
        }
    }

    //? Handle tdpp intents
    fun handleNewIntent(receivedIntent: Intent)
    {
        val iuri: Uri = receivedIntent.toUri(0).toUri()  // URI_ANDROID_APP_SCHEME | URI_INTENT_SCHEME
        try
        {
            if (receivedIntent.scheme == TDPP_URI_SCHEME)
            {
                parseCommonFields(iuri)
                val h = host
                val t = topic
                val path = iuri.getPath()
                LogIt.info("Trickle Pay Intent host=${host} path=${path}")
                LogIt.info("Full Intent=${iuri.toString()}")
                if (h == null)
                {
                    displayFragment(R.id.GuiTricklePayMain)
                    displayError(R.string.BadLink, "no host provided")
                    return
                }
                if (path == null)
                {
                    displayFragment(R.id.GuiTricklePayMain)
                    displayError(i18n(R.string.unknownOperation) % mapOf("op" to "no operation"))
                    return
                }

                later() later@ // Can't do in UI because domains MUST be loaded first
                {
                    if (!domainsLoaded) load()
                    if (path == "/reg")  // Handle registration
                    {
                        handleRegistration(iuri)
                        return@later
                    }

                    // For all other commands, if the domain is unregistered create an entry for it but with no permissions
                    val domain = loadCreateDomain(h,t ?: "")

                    if (path != "")
                    {
                        if (path == "/reg")
                        {
                            // already handled before domain saved
                        }
                        else if (path == "/sendto")
                        {
                            LogIt.info("address autopay")
                            handleSendToAutopay(iuri, domain)
                        }
                        else if (path == "/tx")
                        {
                            LogIt.info("tx autopay")
                            handleTxAutopay(iuri, domain)
                        }
                        else if (path == "/assets")
                        {
                            LogIt.info("tx autopay")
                            handleAssetRequest(iuri, domain)
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
                            val hostStr = if (port > 0) host + ":" + port else (host ?: "")
                            wallyApp?.accessHandler?.startLongPolling(proto, hostStr, cookie)
                            clearIntentAndFinish(null, i18n(R.string.connectionEstablished))
                        }
                        else
                        {
                            displayFragment(R.id.GuiTricklePayMain)
                            displayError(i18n(R.string.unknownOperation) % mapOf("op" to path));
                        }
                    }
                    else
                    {
                        displayFragment(R.id.GuiTricklePayMain)
                        displayError(i18n(R.string.unknownOperation) % mapOf("op" to "no operation"));
                    }
                }
            }
            else  // This should never happen because the AndroidManifest.xml Intent filter should match the URIs that we handle
            {
                displayError("bad link " + receivedIntent.scheme)
            }
        } catch (e: Exception)
        {
            displayFragment(R.id.GuiTricklePayEmpty)
            LogIt.warning(e.toString())
            displayException(e)
        }
    }

    override fun onBackPressed()
    {
        try
        {
            synchronized(domains)
            {
                if (visibleFragment == R.id.GuiTricklePayReg)
                {
                    regUxToMap()
                    later {
                        save()  // can't save in UI thread
                    }
                }
            }
        } catch (e: Exception)
        {
            LogIt.warning(sourceLoc() + "Unexpected Exception")
            displayException(e)
        }
        super.onBackPressed()
    }

    fun clearIntentAndFinish(error: String? = null, notice: String? = null, up: Boolean = false)
    {
        wallyApp?.denotify(intent)
        if (error != null) intent.putExtra("error", error)
        if (error != null) intent.putExtra("notice", notice)
        setResult(Activity.RESULT_OK, intent)
        if (up)  // parent
        {
            NavUtils.navigateUpFromSameTask(this)
        }
        else  // back
        {
            finish()
        }
    }

    fun<T> fragment(id: Int):T
    {
        val fm = getSupportFragmentManager()
        val frag = fm.findFragmentById(id) as T
        if (frag == null) throw UiUnavailableException()
        return frag
    }


    // Move the Ux data into the map
    fun regUxToMap()
    {
        val frag: TricklePayRegFragment = fragment(R.id.GuiTricklePayReg)
        val GuiTricklePayReg: TricklePayRegBinding = frag.ui
        val d: TdppDomain = frag.domain ?: return
        val app = wallyApp
        if (app == null) return

        // find a compatible account for conversion
        val accountLst = app.accountsFor(d.uoa)
        if (accountLst.size == 0)
        {
            throw WalletInvalidException()
        }
        val account = accountLst[0]

        var s = GuiTricklePayReg.GuiAutospendLimitEntry0.text.toString()
        if (s == i18n(R.string.unspecified) || s == "") d.maxper = -1
        else
            d.maxper = account.toFinestUnit(BigDecimal(s))

        s = GuiTricklePayReg.GuiAutospendLimitEntry1.text.toString()
        if (s == i18n(R.string.unspecified) || s == "") d.maxday = -1
        else
            d.maxday = account.toFinestUnit(BigDecimal(s))

        s = GuiTricklePayReg.GuiAutospendLimitEntry2.text.toString()
        if (s == i18n(R.string.unspecified) || s == "") d.maxweek = -1
        else
            d.maxweek = account.toFinestUnit(BigDecimal(s))

        s = GuiTricklePayReg.GuiAutospendLimitEntry3.text.toString()
        if (s == i18n(R.string.unspecified) || s == "") d.maxmonth = -1
        else
            d.maxmonth = account.toFinestUnit(BigDecimal(s))

        d.automaticEnabled = GuiTricklePayReg.GuiEnableAutopay.isChecked

        domains[domainKey(d.domain, d.topic)] = d
    }

    // Trickle pay registration handlers
    @Suppress("UNUSED_PARAMETER")
    fun onAcceptTpReg(view: View?)
    {
        LogIt.info("accept trickle pay registration")
        try
        {
            regUxToMap()
            displayFragment(R.id.GuiTricklePayMain)

            later {
                save()  // can't save in UI thread
                clearIntentAndFinish(notice = i18n(R.string.TpRegAccepted))
            }
        } catch (e: NumberFormatException)
        {
            displayError(R.string.badAmount)
        }
    }

    @Suppress("UNUSED_PARAMETER")
    fun onChangeTpAssetInfoHandling(view: View?)
    {
        val frag: TricklePayRegFragment = fragment(R.id.GuiTricklePayReg)
        val domain = frag.domain ?: return
        domain.assetInfo++
        frag.ui.TpAssetInfoRequestHandlingButton.text = domain.assetInfo.toString()
    }

    @Suppress("UNUSED_PARAMETER")
    fun onTpDeleteRegs(view: View?)
    {
        LogIt.info("accept trickle pay registration")
        domains.clear()
        later {
            save()  // can't save in UI thread
            laterUI {
                clearIntentAndFinish(notice = i18n(R.string.removed))
            }
        }
    }

    @Suppress("UNUSED_PARAMETER")
    fun onDenyTpReg(view: View?)
    {
        LogIt.info("deny trickle pay registration")
        val frag: TricklePayRegFragment = fragment(R.id.GuiTricklePayReg)
        if (frag.editingReg)
        {
            val d: TdppDomain = frag.domain ?: return clearIntentAndFinish()  // should never happen
            domains.remove(domainKey(d.domain, d.topic))
            later {
                  save()
                  laterUI {
                      clearIntentAndFinish(notice = i18n(R.string.removed))
                  }
              }
        }
        else
        {
            clearIntentAndFinish(notice = i18n(R.string.TpRegDenied))
        }
    }

    @Suppress("UNUSED_PARAMETER")
    fun onDoneTp(view: View?)
    {
        synchronized(domains)
        {
            regUxToMap()
            later {
                save()  // can't save in UI thread
                laterUI {
                    clearIntentAndFinish(up = true)
                }
            }
        }
    }

    // Trickle pay transaction handlers
    @Suppress("UNUSED_PARAMETER")
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
            displayFragment(R.id.GuiTricklePayMain)

            later {
                val proto = "http:"
                val host = if (pUri.port > 0) pUri.host + ":" + pUri.port else pUri.host
                val cookieString = if (pcookie != null) "&cookie=$pcookie" else ""
                val req = URL(proto + "//" + host + "/tx?tx=${pTx.toHex()}${cookieString}")
                val data = try
                {
                    req.readText()
                } catch (e: java.io.FileNotFoundException)
                {
                    LogIt.info("Error submitting transaction: " + e.message)
                    displayError(i18n(R.string.WebsiteUnavailable))
                    return@later
                } catch (e: Exception)
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

    fun onDenySpecialTx(@Suppress("UNUSED_PARAMETER") view: View?)
    {
        LogIt.info("deny trickle pay special transaction")
        // give back any inputs we grabbed to fulfill this tx
        val wal = getRelevantWallet()
        proposedTx?.let { wal.abortTransaction(it) }
        proposedTx = null

        displayFragment(R.id.GuiTricklePayMain)
        clearIntentAndFinish(notice = i18n(R.string.TpTxDenied))
    }

    fun onAcceptSendToRequest(@Suppress("UNUSED_PARAMETER") view: View?)
    {
        acceptSendToRequest()
    }

    fun onRejectSendToRequest(@Suppress("UNUSED_PARAMETER") view: View?)
    {
        rejectSendToRequest()
    }

    fun acceptSendToRequest()
    {
        val wal = getRelevantWallet()
        val p = proposedDestinations
        if (p == null)
        {
            clearIntentAndFinish()  // should never happen because page will not show if no destinations
            return
        }
        wal.send(p, false, reason)
        proposedDestinations = null
        clearIntentAndFinish(notice = i18n(R.string.TpSendRequestAccepted))
    }

    fun rejectSendToRequest(reason: Int = R.string.TpSendRequestDenied)
    {
        proposedDestinations = null
        LogIt.info("rejected send request")
        clearIntentAndFinish(notice = i18n(reason))
    }


    fun onAcceptAssetRequest(@Suppress("UNUSED_PARAMETER") view: View?)
    {
        val frag: TricklePayAssetRequestFragment = fragment(R.id.GuiTricklePayAssetRequest)
        val assets = frag.selectedAssets()
        acceptAssetRequest(assets)
    }

    fun acceptAssetRequest(assets: TricklePayAssetList)
    {
        LogIt.info("accepted asset request")
        displayNotice(R.string.Processing, time = 4900)

        val proto = rproto ?: TDPP_DEFAULT_PROTOCOL
        val host = host + ":" + port
        val cookieString = if (cookie != null) "&cookie=$cookie" else ""

        val url = proto + "//" + host + "/assets?" + cookieString

        later {
            LogIt.info("responding to server")
            val client = HttpClient(Android)
            {
                install(ContentNegotiation) {
                    json()
                }
                install(HttpTimeout) { requestTimeoutMillis = 5000 }
            }
            val json = io.ktor.client.plugins.json.defaultSerializer()

            try
            {
                val response: HttpResponse = client.post(url) {
                    val tmp = json.write(assets)
                    LogIt.info("JSON response ${tmp.contentLength} : " + tmp.toString())
                    setBody(json.write(assets))
                }
                val respText = response.bodyAsText()
                clearIntentAndFinish(notice = respText)
            } catch (e: SocketTimeoutException)
            {
                displayError(R.string.connectionException)
            }
            client.close()
        }

        // A successful connection to the server will auto-close this activity
    }

    fun onDenyAssetRequest(@Suppress("UNUSED_PARAMETER") view: View?)
    {
        LogIt.info("rejected asset request")
        displayFragment(R.id.GuiTricklePayMain)

        clearIntentAndFinish(notice = i18n(R.string.TpAssetRequestDenied))
    }


}
