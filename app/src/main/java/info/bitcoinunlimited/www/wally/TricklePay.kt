// Copyright (c) 2019 Andrew Stone Consulting (qq9wwnuw4eukyh5g34ckg5vk4aaxnvr04vkspyv850)
// Distributed under the MIT software license, see the accompanying file COPYING or http://www.opensource.org/licenses/mit-license.php.
package info.bitcoinunlimited.www.wally

import android.app.Activity
import android.content.*
import android.content.Intent.CATEGORY_BROWSABLE
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
import java.util.logging.Logger
import java.math.BigDecimal


private val LogIt = Logger.getLogger("BU.wally.TricklePay")


// Must be top level for the serializer to handle it
@Keep
@kotlinx.serialization.Serializable
data class TricklePayAssetInfo(val outpointHash: String, val amt: Long, val prevout: String, val proof: String? = null)

@Keep
@kotlinx.serialization.Serializable
data class TricklePayAssetList(val assets: List<TricklePayAssetInfo>)


class TricklePayEmptyFragment : Fragment()
{
    public lateinit var ui:TricklePayEmptyBinding
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View?
    {
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
    private lateinit var adapter: GuiList<TdppDomain, TpDomainBinder>
    private lateinit var linearLayoutManager: LinearLayoutManager
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View?
    {
        ui = TricklePayMainBinding.inflate(inflater)
        return ui.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?)
    {
        super.onViewCreated(view, savedInstanceState)
        val tpAct = getActivity() as TricklePayActivity
        adapter = GuiList(ui.GuiTricklePayList, listOf(), tpAct, {
            val ui = TpDomainListItemBinding.inflate(LayoutInflater.from(it.context), it, false)
            TpDomainBinder(ui)
        })
        adapter.rowBackgroundColors = WallyRowColors //arrayOf(0xFFEEFFEE.toInt(), 0xFFBBDDBB.toInt())
        linearLayoutManager = LinearLayoutManager(tpAct)
        ui.GuiTricklePayList.layoutManager = linearLayoutManager
    }

    fun populate()
    {
        val app = wallyApp
        if (app!=null)
        {
            val domains = ArrayList(app.tpDomains.domains.values)
            adapter.set(domains)
        }
    }

}

/* Handle a trickle pay Registration */
class TricklePayRegFragment : Fragment()
{
    public lateinit var ui:TricklePayRegBinding
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

            if (d.maxper == -1L)
            {
                ui.GuiAutospendLimitEntry0.set(i18n(R.string.unspecified))
            }
            else
            {
                ui.GuiAutospendLimitEntry0.set(account.format(account.fromFinestUnit(d.maxper)))
            }
            ui.currencyUnit0.text = account.currencyCode
            ui.GuiAutospendLimitDescription0.text = d.descper

            if (d.maxday == -1L)
                ui.GuiAutospendLimitEntry1.set(i18n(R.string.unspecified))
            else
                ui.GuiAutospendLimitEntry1.set(account.format(account.fromFinestUnit(d.maxday)))
            ui.currencyUnit1.text = account.currencyCode
            ui.GuiAutospendLimitDescription1.text = d.descday

            if (d.maxweek == -1L)
                ui.GuiAutospendLimitEntry2.set(i18n(R.string.unspecified))
            else
                ui.GuiAutospendLimitEntry2.set(account.format(account.fromFinestUnit(d.maxweek)))
            ui.currencyUnit2.text = account.currencyCode
            ui.GuiAutospendLimitDescription2.text = d.descweek

            if (d.maxmonth == -1L)
                ui.GuiAutospendLimitEntry3.set(i18n(R.string.unspecified))
            else
                ui.GuiAutospendLimitEntry3.set(account.format(account.fromFinestUnit(d.maxmonth)))
            ui.currencyUnit3.text = account.currencyCode
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
    public var tpSession: TricklePaySession? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View?
    {
        ui = TricklePayCustomTxBinding.inflate(inflater)
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

    fun populate(sess: TricklePaySession)
    {
        tpSession = sess
        updateUI()
    }

    fun updateUI()
    {
        val sess = tpSession ?: return

        val tpc = sess.topic.let {
            if (it == null) ""
            else ":" + it
        }
        ui.GuiTricklePayEntity.text = sess.host + tpc

        val acc = sess.getRelevantAccount()

        ui.GuiCustomTxBlockchain.text = acc.nameAndChain

        val a = sess.proposalAnalysis
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

        // Change the title if the request is for a partial transaction
        if ((sess.tflags and TDPP_FLAG_PARTIAL) != 0)
        {
            ui.GuiSpecialTxTitle.text = i18n(R.string.IncompleteTpTransactionFrom)
        }

    }
}

/* Handle a trickle pay Registration */
class TricklePaySendToFragment : Fragment()
{
    public lateinit var ui: TricklePaySendToBinding
    public var tpSession: TricklePaySession? = null
    public var tpActivity: TricklePayActivity? = null


    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View?
    {
        ui = TricklePaySendToBinding.inflate(inflater)
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

    fun populate(pactivity: TricklePayActivity, sess: TricklePaySession)
    {
        tpSession = sess
        tpActivity = pactivity
        updateUI()
    }

    fun updateUI()
    {
        val sess = tpSession
        if (sess == null) return

        try
        {
        val u: Uri = sess.proposalUri ?: return
        val sa: List<Pair<PayAddress, Long>> = sess.proposedDestinations ?: return

        val tpc = tpSession?.topic.let {
            if (it == null) ""
            else ":" + it
        }

        ui.GuiSendToTricklePayEntity.text = u.authority + tpc

        val acc = sess.getRelevantAccount()
        ui.GuiSendToBlockchain.text = acc.nameAndChain

        var total = 0L
        for (it in sa)
        {
            total += it.second
        }
            ui.GuiSendToCost.text = acc.cryptoFormat.format(acc.fromFinestUnit(total)) + " " + acc.currencyCode
            ui.GuiSendToPurpose.text = sess.reason ?: ""

        val ars = sess.askReasons
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
    public var tpActivity: TricklePayActivity? = null
    public var tpSession: TricklePaySession? = null

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
        return tpSession!!.assetInfoList!!
    }

    fun populate(pactivity: TricklePayActivity, sess: TricklePaySession)
    {
        tpActivity = pactivity
        tpSession = sess
        updateUI()
    }

    fun updateUI()
    {
        val sess = tpSession // if we don't have a session yet clear it out
        if (sess == null)
        {
            ui.GuiTricklePayEntity.text = ""
            ui.GuiAssetHandledByAccount.text = ""
            ui.GuiAssetAcceptQ3.text = ""
            return
        }

        val tpc = sess.topic.let {
            if (it == null) ""
            else ":" + it
        }
        ui.GuiTricklePayEntity.text = sess.host + tpc

        val acc = sess.getRelevantAccount()
        ui.GuiAssetHandledByAccount.text = acc.nameAndChain
        ui.GuiAssetAcceptQ3.text = i18n(R.string.TpAssetMatches) % mapOf("num" to (sess.assetInfoList?.assets?.size ?: 0).toString())
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
                activity.setSelectedInfoDomain(it)
                activity.displayFragment(R.id.GuiTricklePayReg)
            }
        }
    }
}

@kotlinx.coroutines.ExperimentalCoroutinesApi
class TricklePayActivity : CommonNavActivity()
{
    public lateinit var ui: ActivityTricklePayBinding
    override var navActivityId = R.id.navigation_trickle_pay

    /** The currency selected as the unit of account during registration/configuration */
    var regCurrency: String = ""
    var regAddress: String = ""

    var tpSession: TricklePaySession? = null
    // var proposedTx: iTransaction? = null
    // var proposalAnalysis: TxAnalysisResults? = null
    // var proposalUri: Uri? = null
    // var proposedDestinations: List<Pair<PayAddress, Long>>? = null
    // var proposalCookie: String? = null
    // var host: String? = null
    // var port: Int = 80
    // var topic: String? = null
    // var sig: String? = null
    // var cookie: String? = null
    // var reason: String? = null
    var rproto: String? = null
    var rpath: String? = null
    var chainSelector: ChainSelector? = null
    var visibleFragment: Int = -1

    var accepted: Boolean = false
    var pinTries = 0

    // We must have an app by the time an activity is created
    val tpDomains = wallyApp!!.tpDomains

    fun setSelectedInfoDomain(d: TdppDomain)
    {
        tpSession?.host = d.domain
        tpSession?.topic = d.topic
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

        notInUI {
            wallyApp?.tpDomains?.load()
            (fragment(R.id.GuiTricklePayMain) as TricklePayMainFragment).populate()
        }

        enableMenu(this, SHOW_TRICKLEPAY_PREF) // If you ever drop into this activity, show it in the menu
    }

    override fun onResume()
    {
        super.onResume()
        //if (!launchedFromRecent() && (intent.scheme != null)) // its null if normal app startup
        if (intent.scheme != null)
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

    fun handleTxAutopay(uri: Uri)
    {
        val sess = tpSession ?: throw UnavailableException()  // you must have created a session and parsed the common fields first

        val action = sess.handleTxAutopay(uri)

        if (action == TdppAction.ASK)
        {
            val frag = fragment(R.id.GuiTricklePayCustomTx) as TricklePayCustomTxFragment
            frag.populate(sess)
            // Change the title if the request is for a partial transaction
            if ((sess.tflags and TDPP_FLAG_PARTIAL) != 0)
            {
                frag.ui.GuiSpecialTxTitle.text = i18n(R.string.IncompleteTpTransactionFrom)
            }

            laterUI {
                displayFragment(R.id.GuiTricklePayCustomTx)
                // Ok now that we've displayed what we can, let's show the problem.
                val tmp = sess.proposalAnalysis?.completionException
                if (tmp != null) displayException(tmp)
            }
        }
        else if (action == TdppAction.ACCEPT)
        {
            sess.acceptSpecialTx()
            clearIntentAndFinish(notice=R.string.TpRequestAutoAccept)
        }
        else // if (action == TdppAction.DENY)
        {
            clearIntentAndFinish(notice=R.string.TpRequestAutoDeny)
        }
    }


    fun handleSendToAutopay(uri: Uri)
    {
        val sess = tpSession ?: throw UnavailableException()  // you must have created a session and parsed the common fields first

        // If user has already accepted, then ok, just move to completing the ASK
        // You may need to go thru this multiple times when an account is locked so we need to launch the PIN entry activity
        val action = if (sess.accepted == true) TdppAction.ASK else try
        {
            sess.handleSendToAutopay(uri)
        }
        catch(e:BUExceptionI)
        {
            wallyApp?.displayException(e)
            finish()
            return
        }

        if (action == TdppAction.ASK)
        {
            val act = sess.getRelevantAccount()

            if (sess.accepted == true)
            {
                if (act.locked)
                {
                    launchPinEntry()
                    return
                }
                sess.acceptSendToRequest()
                clearIntentAndFinish(notice=R.string.TpSendRequestAccepted)
            }
            else laterUI {  // If it wasn't automatically accepted or rejected, ask
                val frag:TricklePaySendToFragment = fragment(R.id.GuiTricklePaySendTo)
                frag.populate(this, sess)
                displayFragment(R.id.GuiTricklePaySendTo)
            }
        }
        else if (action == TdppAction.ACCEPT)
        {
            sess.acceptSendToRequest()
            clearIntentAndFinish(notice=R.string.TpRequestAutoAccept)
        }
        else // if (action == TdppAction.DENY)
        {
            clearIntentAndFinish(notice=R.string.TpRequestAutoDeny)
        }
    }


    fun handleAssetInfoRequest(uri: Uri)
    {
        val sess = tpSession ?: throw UnavailableException()  // you must have created a session and parsed the common fields first

        // If user has already accepted, then ok, just move to completing the ASK
        // You may need to go thru this multiple times when an account is locked so we need to launch the PIN entry activity
        val action = if (sess.accepted == true) TdppAction.ASK else try
        {
            sess.handleAssetInfoRequest(uri)
        }
        catch(e:BUExceptionI)
        {
            displayFragment(R.id.GuiTricklePayEmpty)
            wallyApp?.displayException(e)
            clearIntentAndFinish()
            return
        }

        if (action == TdppAction.DENY)
        {
            displayFragment(R.id.GuiTricklePayAssetRequest)
            clearIntentAndFinish(error=R.string.TpRequestAutoDeny)
        }
        else if (action == TdppAction.ASK)
        {
            (fragment(R.id.GuiTricklePayAssetRequest) as TricklePayAssetRequestFragment).populate(this, sess)
            // ask for confirmation
            displayFragment(R.id.GuiTricklePayAssetRequest)
        }
        if (action == TdppAction.ACCEPT)
        {
            displayFragment(R.id.GuiTricklePayAssetRequest)
            sess.acceptAssetRequest()
            clearIntentAndFinish(notice=R.string.TpRequestAutoAccept)
        }
    }


    fun handleShareRequest(uri: Uri)
    {
        val sess = tpSession ?: throw UnavailableException()  // you must have created a session and parsed the common fields first
        sess.handleShareRequest(uri) {
            finish()
        }
    }

    fun handleRegistration(uri: Uri): Boolean
    {
        val sess = tpSession ?: throw UnavailableException()  // you must have created a session and parsed the common fields first

        val address = uri.getQueryParameter("addr")
        if (address == null)
        {
            laterUI {
                displayFragment(R.id.GuiTricklePayMain)
                clearIntentAndFinish(error = R.string.badLink)
            }
            return false
        }

        if (sess.sigOk == true)  // Registration requires a good signature
        {
            regAddress = address
            // TODO allow currency UOA to be changed during registration
            regCurrency = uri.getQueryParameter("uoa") ?: TDPP_DEFAULT_UOA

            var d = sess.domain
            if (d != null)
            {
                d.merge(uri)
            }
            else {
                d = TdppDomain(uri)
                sess.newDomain = true
                sess.domain = d
            }

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


    //? Handle tdpp intents
    fun handleNewIntent(receivedIntent: Intent)
    {
        // Automatically go back from the parent (wally main) activity to whoever called us
        // In this case we'll do this when the intent is coming from browsing a local web site
        val autoClose:Boolean = if (receivedIntent.categories != null) receivedIntent.categories.contains(CATEGORY_BROWSABLE) else false
        val iuri = receivedIntent.toUri(0).toUri()

        wallyApp?.denotify(receivedIntent)
        try
        {
            if (receivedIntent.scheme == TDPP_URI_SCHEME)
            {
                val sess = TricklePaySession(tpDomains)
                tpSession = sess
                sess.parseCommonFields(iuri, autoCreateDomain = false)
                val h = sess.host
                val t = sess.topic
                val path = iuri.getPath()
                LogIt.info(sourceLoc() + "Trickle Pay Intent host=${h} path=${path}")
                LogIt.info(sourceLoc() + "Full Intent=${iuri.toString()}")
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

                if (sess.sigOk == false)  // it is never correct to send a bad signature, even if signatures are optional
                {
                    displayFragment(R.id.GuiTricklePayMain)
                    wallyApp?.displayError(R.string.badSignature)
                    clearIntentAndFinish()
                }

                later() later@ // Can't do in UI because domains MUST be loaded first
                {
                    if (path == "/reg")  // Handle registration
                    {
                        handleRegistration(iuri)
                        return@later
                    }

                    // For all other commands, if the domain is unregistered create an entry for it but with no permissions
                    val domain = sess.domain ?: tpDomains.loadCreateDomain(h, t ?: "")
                    if (sess.domain == null) sess.domain = domain

                    if (path != "")
                    {
                        if (path == "/reg")
                        {
                            // already handled before domain saved
                        }
                        else if (path == "/sendto")
                        {
                            if (sess.accepted == true) sess.acceptSendToRequest()  // must have asked for PIN
                            else
                            {
                                LogIt.info("address autopay")
                                handleSendToAutopay(iuri)
                            }
                        }
                        else if (path == "/tx")
                        {
                            LogIt.info("tx autopay")
                            if (accepted) onSignSpecialTx(null)  // must have asked for PIN so we had to launch the pin entry intent
                            else
                                handleTxAutopay(iuri)
                        }
                        else if (path == "/assets")
                        {
                            LogIt.info("asset request")
                            if (autoClose) wallyApp?.finishParent=1
                            handleAssetInfoRequest(iuri)
                        }
                        else if (path == "/share")
                        {
                            LogIt.info("info request (reverse QR)")
                            if (autoClose) wallyApp?.finishParent=1
                            handleShareRequest(iuri)
                        }
                        else if (path == "/jsonpay")
                        {
                            LogIt.info("json autopay")
                        }
                        else if (path == "/lp")
                        {
                            LogIt.info(sourceLoc() + ": Start long Poll to ${h}")
                            wallyApp?.accessHandler?.startLongPolling(sess.replyProtocol, sess.hostAndPort, sess.cookie)
                            if (autoClose) wallyApp?.finishParent=1
                            clearIntentAndFinish(null, R.string.connectionEstablished)
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
                displayFragment(R.id.GuiTricklePayMain)
                displayError("bad link " + receivedIntent.scheme)
            }
        }
        catch (e: BUExceptionI)
        {
            wallyApp?.displayException(e)
            clearIntentAndFinish()
        }
        catch (e: BUException)
        {
            handleThreadException(e)
            wallyApp?.displayError(R.string.unknownError, e.toString())
            clearIntentAndFinish()
        }
    }

    override fun onBackPressed()
    {
        try
        {
            synchronized(tpDomains)
            {
                if (visibleFragment == R.id.GuiTricklePayReg)
                {
                    registrationUiToDomain()
                }
            }
        } catch (e: Exception)
        {
            LogIt.warning(sourceLoc() + "Unexpected Exception")
            displayException(e)
        }
        super.onBackPressed()
    }

    fun clearIntentAndFinish(error: Int? = null, notice: Int? = null, up: Boolean = false)
    {
        wallyApp?.denotify(intent)
        if (error != null) wallyApp?.displayError(error)
        if (notice != null) wallyApp?.displayNotice(notice)
        setResult(Activity.RESULT_OK, intent)
        tpSession = null
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
        val frag = fm.findFragmentById(id) as? T
        if (frag == null) throw UiUnavailableException()
        return frag
    }


    /** Move the UI data into the actual domain data structure */
    fun registrationUiToDomain()
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

        tpDomains.insert(d)
    }

    // Trickle pay registration handlers
    @Suppress("UNUSED_PARAMETER")
    fun onAcceptTpReg(view: View?)
    {
        LogIt.info("accept trickle pay registration")
        try
        {
            registrationUiToDomain()
            later {
                tpDomains.save()  // can't save in UI thread
                clearIntentAndFinish(notice = R.string.TpRegAccepted)
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
        tpDomains.clear()
        later {
            tpDomains.save()  // can't save in UI thread
            laterUI {
                clearIntentAndFinish(notice = R.string.removed)
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
            tpDomains.remove(d)
            later {
                  tpDomains.save()
                  laterUI {
                      clearIntentAndFinish(notice = R.string.removed)
                  }
              }
        }
        else
        {
            if (tpSession?.newDomain == true)
                clearIntentAndFinish(notice = R.string.TpRegDenied)
            else
                clearIntentAndFinish(notice = R.string.TpRegUnchanged)
        }
    }

    @Suppress("UNUSED_PARAMETER")
    fun onDoneTp(view: View?)
    {
        synchronized(tpDomains)
        {
            registrationUiToDomain()
            later {
                tpDomains.save()  // can't save in UI thread
                laterUI {
                    clearIntentAndFinish(up = true)
                }
            }
        }
    }

    fun launchPinEntry()
    {
        if (pinTries < 2)
        {
            if (pinTries > 0) wallyApp?.displayError(R.string.InvalidPIN)
            val intent = Intent(this, UnlockActivity::class.java)
            pinTries += 1
            startActivity(intent)
        }
        else
        {
            wallyApp?.displayError(R.string.InvalidPIN)
            finish()
        }
    }
    // Trickle pay transaction handlers
    @Suppress("UNUSED_PARAMETER")
    fun onSignSpecialTx(view: View?)
    {
        try
        {
            LogIt.info("accept trickle pay special transaction")
            accepted = true
            val sess = tpSession
            tpSession = null
            if (sess != null)
            {
                val pTx = sess.proposedTx
                val panalysis = sess.proposalAnalysis
                if ((pTx != null) && (panalysis != null))
                {
                    if (panalysis.account.locked)
                    {
                        launchPinEntry()
                        return
                    }
                    sess.acceptSpecialTx()
                    clearIntentAndFinish()
                    return
                }
            }
            return
        }
        catch (e: BUExceptionI)
        {
            wallyApp?.displayException(e)
            finish()
        }
        catch (e: BUException)
        {
            handleThreadException(e)
            wallyApp?.displayError(R.string.unknownError, e.toString())
            finish()
        }
    }

    fun onDenySpecialTx(@Suppress("UNUSED_PARAMETER") view: View?)
    {
        LogIt.info("deny trickle pay special transaction")
        // give back any inputs we grabbed to fulfill this tx
        val sess = tpSession
        if (sess != null)
        {
            val acc = sess.getRelevantAccount()
            sess.proposedTx?.let { acc.wallet.abortTransaction(it) }
            sess.proposedTx = null
        }
        displayFragment(R.id.GuiTricklePayMain)
        tpSession = null
        clearIntentAndFinish(notice = R.string.TpTxDenied)
    }

    fun onAcceptSendToRequest(@Suppress("UNUSED_PARAMETER") view: View?)
    {
        try
        {
            accepted = true
            tpSession?.acceptSendToRequest()
            tpSession = null
            clearIntentAndFinish(notice = R.string.TpSendRequestAccepted)
        }
        catch (e: BUExceptionI)
        {
            wallyApp?.displayException(e)
            clearIntentAndFinish()
        }
        catch (e: BUException)
        {
            handleThreadException(e)
            if (e.errCode != -1)
            {
                wallyApp?.displayError(e.errCode, e.message ?: "")
            }
            else
            {
                 wallyApp?.displayError(R.string.unknownError, e.toString())
            }
            clearIntentAndFinish()
        }
    }

    fun onRejectSendToRequest(@Suppress("UNUSED_PARAMETER") view: View?)
    {
        rejectSendToRequest()
    }

    fun rejectSendToRequest(reason: Int = R.string.TpSendRequestDenied)
    {
        tpSession = null
        LogIt.info("rejected send request")
        clearIntentAndFinish(notice = reason)
    }

    fun onAcceptAssetRequest(@Suppress("UNUSED_PARAMETER") view: View?)
    {
        displayNotice(R.string.Processing, time = 4900)
        tpSession?.acceptAssetRequest()
        tpSession = null
        clearIntentAndFinish(notice = R.string.TpAssetRequestAccepted)
    }

    fun onDenyAssetRequest(@Suppress("UNUSED_PARAMETER") view: View?)
    {
        LogIt.info("rejected asset request")
        displayFragment(R.id.GuiTricklePayMain)
        tpSession = null
        clearIntentAndFinish(notice = R.string.TpAssetRequestDenied)
    }

}
