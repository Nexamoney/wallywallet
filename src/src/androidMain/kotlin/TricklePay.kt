// Copyright (c) 2019 Andrew Stone Consulting (qq9wwnuw4eukyh5g34ckg5vk4aaxnvr04vkspyv850)
// Distributed under the MIT software license, see the accompanying file COPYING or http://www.opensource.org/licenses/mit-license.php.
package info.bitcoinunlimited.www.wally

import android.app.Activity
import android.content.*
import android.content.Intent.CATEGORY_BROWSABLE
import android.content.Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.Keep
import androidx.core.app.NavUtils
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import info.bitcoinunlimited.www.wally.databinding.*
import io.ktor.http.*
import org.nexa.libnexakotlin.libnexa

import com.eygraber.uri.*
import org.nexa.libnexakotlin.*
import org.nexa.threads.Mutex

private val LogIt = GetLog("BU.wally.TricklePay")


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
        super.onResume()
        try
        {
            updateUI()
        }
        catch(e: WalletInvalidException)
        {
            val tpAct = getActivity() as TricklePayActivity
            wallyApp?.displayError(R.string.NoAccounts)
            tpAct.finish()
        }

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

            if (d.accountName != "")
                ui.textView7.text = i18n(R.string.TpAssociatedAccount) % mapOf("act" to d.accountName)
            else ui.textView7.visibility = View.GONE
            if (d.mainPayAddress != "")
                ui.TpAssociatedAddressField.text = i18n(R.string.TpAssociatedAddress) % mapOf("addr" to d.mainPayAddress)
            else ui.TpAssociatedAddressField.visibility = View.GONE
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
    public var tpActivity: TricklePayActivity? = null

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

    fun populate(activity: TricklePayActivity)
    {
        tpActivity = activity
        updateUI()
    }

    fun updateUI()
    {
        if (brokenMode) ui.GuiTpSpecialTxBreak.visibility = View.VISIBLE
        else ui.GuiTpSpecialTxBreak.visibility = View.GONE

        val sess = tpActivity?.tpSession ?: return

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

            var receivingTokenTypes = 0L
            var spendingTokenTypes = 0L
            var provingTokenTypes = 0L
            for ((k,v) in a.myNetTokenInfo)
            {
                if (v > 0) receivingTokenTypes++
                else if (v < 0) spendingTokenTypes++
                else provingTokenTypes++
            }

            var summary = if (receivingTokenTypes > 0)
            {
                if (spendingTokenTypes > 0)
                {
                    i18n(R.string.TpExchangingTokens) % mapOf("tokSnd" to a.sendingTokenTypes.toString(), "tokRcv" to a.receivingTokenTypes.toString())
                }
                else
                {
                    i18n(R.string.TpReceivingTokens) % mapOf("tokRcv" to a.receivingTokenTypes.toString())
                }
            }
            else
            {
                if (spendingTokenTypes > 0)
                {
                    i18n(R.string.TpSendingTokens) % mapOf("tokSnd" to spendingTokenTypes.toString())
                }
                else ""
            }

            if (provingTokenTypes > 0) summary = summary + "\n" + (i18n(R.string.TpShowingTokens) % mapOf("tokReveal" to provingTokenTypes.toString()))

            ui.GuiCustomTxTokenSummary.text = summary

            var error:String? = null
            if ((receivingTokenTypes == 0L)&&(spendingTokenTypes == 0L)&&(provingTokenTypes==0L)&&(netSats == 0L))
            {
                error = i18n(R.string.TpHasNoPurpose)
            }

            a.completionException?.let { error = it.message }

            if (error != null)  // If there's an exception, the only possibility is to abort
            {
                ui.GuiCustomTxErrorHeading.visibility = View.VISIBLE
                ui.GuiTpSpecialTxAccept.visibility = View.GONE
                ui.GuiCustomTxError.text = error
                ui.DeleteButton.text = i18n(R.string.cancel)
            }
            else
            {
                ui.GuiCustomTxErrorHeading.visibility = View.GONE
                ui.GuiTpSpecialTxAccept.visibility = View.VISIBLE
                ui.GuiCustomTxError.text = ""
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

    fun populate(pactivity: TricklePayActivity)
    {
        tpActivity = pactivity
        updateUI()
    }

    fun updateUI()
    {
        val sess = tpActivity?.tpSession ?: return

        try
        {
        val u: Uri = sess.proposalUrl ?: return
        val sa: List<Pair<PayAddress, Long>> = sess.proposedDestinations ?: return

        val tpc = sess.topic.let {
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
        return tpActivity!!.tpSession!!.assetInfoList!!
    }

    fun populate(pactivity: TricklePayActivity)
    {
        tpActivity = pactivity
        updateUI()
    }

    fun updateUI()
    {
        val sess = tpActivity?.tpSession // if we don't have a session yet clear it out
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
    val sig = libnexa.signMessage(signThis.toByteArray(), secret.getSecret())
    if (sig == null || sig.size == 0) throw IdentityException("Wallet failed to provide a signable identity", "bad wallet", ErrorSeverity.Severe)
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
        activity.viewSync.synchronized {
            LogIt.info("onclick: " + pos + " " + activity.showingDetails)
            data?.let {
                activity.setSelectedInfoDomain(it)
                activity.displayFragment(R.id.GuiTricklePayReg)
            }
        }
    }
}

class TricklePayActivity : CommonNavActivity()
{
    public lateinit var ui: ActivityTricklePayBinding
    override var navActivityId = R.id.navigation_trickle_pay

    var goHomeWhenDone = false

    /** The currency selected as the unit of account during registration/configuration */
    var regCurrency: String = ""
    var regAddress: String = ""

    var tpSession: TricklePaySession? = null
    var visibleFragment: Int = -1

    // var accepted: Boolean = false
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

    val viewSync = Mutex()
    var showingDetails = false

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
            frag.populate(this)
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
        catch(e:LibNexaExceptionI)
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
                frag.populate(this)
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

    fun handleAddressInfoRequest(uri: Uri)
    {
        val sess = tpSession
        if (sess == null)
        {
            LogIt.info(sourceLoc() + ": Address request NO SESSION!")
            throw UnavailableException()
        }  // you must have created a session and parsed the common fields first

        // If user has already accepted, then ok, just move to completing the ASK
        // You may need to go thru this multiple times when an account is locked so we need to launch the PIN entry activity
        val action = if (sess.accepted == true) TdppAction.ASK else try
        {
            sess.handleAddressInfoRequest(uri)
        }
        catch(e:LibNexaExceptionI)
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
            TODO("always accept address requests for now")
        }
        if (action == TdppAction.ACCEPT)
        {
            displayFragment(R.id.GuiTricklePayEmpty)
            val details = sess.acceptAddressRequest()
            clearIntentAndFinish(notice=R.string.TpRequestAutoAccept, details=details)
        }
    }

    fun handleAssetInfoRequest(uri: Uri)
    {
        val sess = tpSession
        if (sess == null)
        {
            LogIt.info(sourceLoc() + ": Asset request NO SESSION!")
            throw UnavailableException()
        }  // you must have created a session and parsed the common fields first

        // If user has already accepted, then ok, just move to completing the ASK
        // You may need to go thru this multiple times when an account is locked so we need to launch the PIN entry activity
        val action = if (sess.accepted == true) TdppAction.ASK else try
        {
            sess.handleAssetInfoRequest(uri)
        }
        catch(e:LibNexaExceptionI)
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
            (fragment(R.id.GuiTricklePayAssetRequest) as TricklePayAssetRequestFragment).populate(this)
            // ask for confirmation
            displayFragment(R.id.GuiTricklePayAssetRequest)
        }
        if (action == TdppAction.ACCEPT)
        {
            displayFragment(R.id.GuiTricklePayAssetRequest)
            val details = sess.acceptAssetRequest()
            clearIntentAndFinish(notice=R.string.TpRequestAutoAccept, details=details)
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
        val autoClose:Boolean = if (receivedIntent.categories != null)
        {
            (receivedIntent.categories.contains(CATEGORY_BROWSABLE) && ((receivedIntent.flags and FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY) == 0))
        }
        else false

        //val iuri = receivedIntent.toUri(0).toUri()
        val iuri = Uri.parse(receivedIntent.toUri(0).toString())

        if ((receivedIntent.flags and FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY) != 0) goHomeWhenDone = true

        wallyApp?.denotify(receivedIntent)
        try
        {
            if (receivedIntent.scheme == TDPP_URI_SCHEME)
            {
                var sess = if (tpSession != null)
                {
                    // Get the current session if the uri matches
                    if (tpSession?.proposalUrl != iuri) tpSession = null
                    tpSession
                } else null

                if (sess == null)
                {
                    sess = TricklePaySession(tpDomains)
                    sess.parseCommonFields(iuri, autoCreateDomain = false)
                    tpSession=sess
                }

                val h = sess.host
                val t = sess.topic
                val path = iuri.path
                LogIt.info(sourceLoc() + "Trickle Pay Intent host=${h} path=${path}")
                LogIt.info(sourceLoc() + "Full Intent=${iuri.toString()}")
                if (h == null)
                {
                    wallyApp?.displayError(R.string.BadWebLink, "no host provided")
                    clearIntentAndFinish()
                    return
                }
                if (path == null)
                {
                    wallyApp?.displayError(R.string.badLink,i18n(R.string.unknownOperation) % mapOf("op" to "no operation"))
                    clearIntentAndFinish()
                    return
                }

                if (sess.sigOk == false)  // it is never correct to send a bad signature, even if signatures are optional
                {
                    wallyApp?.displayError(R.string.badSignature)
                    clearIntentAndFinish()
                    return
                }

                later() later@ // Can't do in UI because domains MUST be loaded first
                {
                    try
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
                                    LogIt.info(sourceLoc() + ": address autopay")
                                    handleSendToAutopay(iuri)
                                }
                            }
                            else if (path == "/tx")
                            {
                                LogIt.info(sourceLoc() + ": tx autopay")
                                if (sess.accepted) onSignSpecialTx(null)  // must have asked for PIN so we had to launch the pin entry intent
                                else
                                    handleTxAutopay(iuri)
                            }
                            else if (path == "/assets")
                            {
                                LogIt.info(sourceLoc() + ": asset request")
                                if (autoClose) wallyApp?.finishParent = 1
                                handleAssetInfoRequest(iuri)
                            }
                            else if (path == "/address")
                            {
                                LogIt.info(sourceLoc() + ": address request")
                                if (autoClose) wallyApp?.finishParent = 1
                                handleAddressInfoRequest(iuri)
                            }
                            else if (path == "/share")
                            {
                                LogIt.info(sourceLoc() + ": info request (reverse QR)")
                                if (autoClose) wallyApp?.finishParent = 1
                                handleShareRequest(iuri)
                            }
                            else if (path == "/jsonpay")
                            {
                                LogIt.info(sourceLoc() + ": json autopay")
                            }
                            else if (path == "/lp")
                            {
                                LogIt.info(sourceLoc() + ": Start long Poll to ${h}")
                                wallyApp?.accessHandler?.startLongPolling(sess.replyProtocol, sess.hostAndPort, sess.cookie)
                                if (autoClose) wallyApp?.finishParent = 1
                                clearIntentAndFinish(null, R.string.connectionEstablished, details = "Connected to ${h}")
                                return@later
                            }
                            else
                            {
                                wallyApp?.displayError(R.string.badLink,i18n(R.string.unknownOperation) % mapOf("op" to path))
                                clearIntentAndFinish()
                                return@later
                            }
                        }
                        else
                        {
                            wallyApp?.displayError(R.string.badLink, i18n(R.string.unknownOperation) % mapOf("op" to "no operation"))
                            clearIntentAndFinish()
                            return@later
                        }
                    }
                    catch (e: WalletInvalidException)
                    {
                        wallyApp?.displayError(R.string.NoAccounts, R.string.badCryptoCode)
                        clearIntentAndFinish()
                        return@later
                    }
                }
            }
            else  // This should never happen because the AndroidManifest.xml Intent filter should match the URIs that we handle
            {
                wallyApp?.displayError(R.string.badLink,sourceLoc() + ": bad link " + receivedIntent.scheme)
                clearIntentAndFinish()
                return
            }
        }
        catch (e: LibNexaExceptionI)
        {
            wallyApp?.displayException(e)
            clearIntentAndFinish()
        }
        catch (e: LibNexaException)
        {
            handleThreadException(e)
            wallyApp?.displayError(R.string.unknownError, e.toString())
            clearIntentAndFinish()
        }
        catch (e: Exception)
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

    fun clearIntentAndFinish(error: Int? = null, notice: Int? = null, up: Boolean = false, details: String? = null )
    {
        wallyApp?.denotify(intent)
        if (error != null) if (details != null) wallyApp?.displayError(error, details) else wallyApp?.displayError(error)
        if (notice != null) if (details != null) wallyApp?.displayNotice(notice, details) else wallyApp?.displayNotice(notice)
        setResult(Activity.RESULT_OK, intent)
        tpSession = null
        if (goHomeWhenDone)
        {
            startActivity(Intent(this, MainActivity::class.java).setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP))
        }
        else if (up)  // parent
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
            d.maxper = account.toFinestUnit(s.toCurrency(account.chain.chainSelector))

        s = GuiTricklePayReg.GuiAutospendLimitEntry1.text.toString()
        if (s == i18n(R.string.unspecified) || s == "") d.maxday = -1
        else
            d.maxday = account.toFinestUnit(s.toCurrency(account.chain.chainSelector))

        s = GuiTricklePayReg.GuiAutospendLimitEntry2.text.toString()
        if (s == i18n(R.string.unspecified) || s == "") d.maxweek = -1
        else
            d.maxweek = account.toFinestUnit(s.toCurrency(account.chain.chainSelector))

        s = GuiTricklePayReg.GuiAutospendLimitEntry3.text.toString()
        if (s == i18n(R.string.unspecified) || s == "") d.maxmonth = -1
        else
            d.maxmonth = account.toFinestUnit(s.toCurrency(account.chain.chainSelector))

        d.automaticEnabled = GuiTricklePayReg.GuiEnableAutopay.isChecked

        tpDomains.insert(d)
    }

    // Trickle pay registration handlers
    @Suppress("UNUSED_PARAMETER")
    fun onAcceptTpReg(view: View)
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
    fun onChangeTpAssetInfoHandling(view: View)
    {
        val frag: TricklePayRegFragment = fragment(R.id.GuiTricklePayReg)
        val domain = frag.domain ?: return
        domain.assetInfo++
        frag.ui.TpAssetInfoRequestHandlingButton.text = domain.assetInfo.toString()
    }

    @Suppress("UNUSED_PARAMETER")
    fun onTpDeleteRegs(view: View)
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
    fun onDenyTpReg(view: View)
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
    fun onDoneTp(view: View)
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
    fun onSignSpecialTx(view: View?) = SignSpecialTx()

    fun SignSpecialTx(breakIt:Boolean = false)
    {
        try
        {
            LogIt.info("accept trickle pay special transaction")
            val sess = tpSession
            if (sess != null)
            {
                sess.accepted = true
                val pTx = sess.proposedTx
                val panalysis = sess.proposalAnalysis
                if ((pTx != null) && (panalysis != null))
                {
                    if (panalysis.account.locked)
                    {
                        launchPinEntry()
                        return
                    }
                    else
                    {
                        tpSession = null
                        sess.acceptSpecialTx(breakIt)
                        clearIntentAndFinish()
                        return
                    }
                }
            }
            return
        }
        catch (e: LibNexaExceptionI)
        {
            wallyApp?.displayException(e)
            finish()
        }
        catch (e: LibNexaException)
        {
            handleThreadException(e)
            wallyApp?.displayError(R.string.unknownError, e.toString())
            finish()
        }
    }

    fun onBreakSpecialTx(view: View?) = SignSpecialTx(true)


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

    fun onAcceptSendToRequest(@Suppress("UNUSED_PARAMETER") view: View)
    {
        try
        {
            tpSession?.let { it.accepted = true }
            tpSession?.acceptSendToRequest()
            tpSession = null
            clearIntentAndFinish(notice = R.string.TpSendRequestAccepted)
        }
        catch (e: LibNexaExceptionI)
        {
            wallyApp?.displayException(e)
            clearIntentAndFinish()
        }
        catch (e: LibNexaException)
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

    fun onRejectSendToRequest(@Suppress("UNUSED_PARAMETER") view: View)
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
        val details = tpSession?.acceptAssetRequest()
        tpSession = null
        clearIntentAndFinish(notice = R.string.TpAssetRequestAccepted, details = details)
    }

    fun onDenyAssetRequest(@Suppress("UNUSED_PARAMETER") view: View?)
    {
        LogIt.info("rejected asset request")
        displayFragment(R.id.GuiTricklePayMain)
        tpSession = null
        clearIntentAndFinish(notice = R.string.TpAssetRequestDenied)
    }

}
