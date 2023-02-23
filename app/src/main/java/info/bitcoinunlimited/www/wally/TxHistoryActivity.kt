// Copyright (c) 2019 Andrew Stone Consulting (qq9wwnuw4eukyh5g34ckg5vk4aaxnvr04vkspyv850)
// Distributed under the MIT software license, see the accompanying file COPYING or http://www.opensource.org/licenses/mit-license.php.
package info.bitcoinunlimited.www.wally

import android.content.*
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.os.TransactionTooLargeException
import android.view.*
import android.view.ViewTreeObserver.OnGlobalLayoutListener
import android.webkit.WebViewClient
import androidx.appcompat.widget.ShareActionProvider
import androidx.core.content.ContextCompat
import androidx.core.view.MenuItemCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import bitcoinunlimited.libbitcoincash.*
import info.bitcoinunlimited.www.wally.databinding.ActivityTxHistoryBinding
import info.bitcoinunlimited.www.wally.databinding.AddressListItemBinding
import info.bitcoinunlimited.www.wally.databinding.TxHistoryListItemBinding
import kotlinx.coroutines.delay
import java.lang.RuntimeException
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.logging.Logger


private val LogIt = Logger.getLogger("BU.wally.TxHistory")

fun TransactionHistory.toCSV(): String
{
    val rcvWalletAddr = StringBuilder()
    val rcvForeignAddr = StringBuilder()
    for (i in 0 until tx.outputs.size)
    {
        val out = tx.outputs[i]
        if (incomingIdxes.contains(i.toLong()))
        {
            rcvWalletAddr.append(" " + (out.script.address?.toString() ?: ""))
        }
        else
        {
            rcvForeignAddr.append(" " + (out.script.address?.toString() ?: ""))
        }
    }

    val spentWalletAddr = StringBuilder()
    val spentForeignAddr = StringBuilder()
    for (i in 0L until tx.inputs.size)
    {
        val inp = tx.inputs[i.toInt()]
        val idx = outgoingIdxes.find({ it == i })
        if (idx != null)
        {
            if (idx < spentTxos.size)
                rcvWalletAddr.append(" " + (spentTxos[idx.toInt()].script.address?.toString() ?: "") )
            else
            {
                LogIt.info(sourceLoc() + " data consistency error")
            }
        }
        else
        {
            rcvForeignAddr.append(" " + inp)
        }
    }

    val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")
    val ldt = LocalDateTime.ofInstant(Instant.ofEpochMilli(date), ZoneId.systemDefault())
    val fdate = ldt.format(formatter)
    val ret = StringBuilder()
    ret.append(fdate)
    ret.append(",")
    ret.append(incomingAmt - outgoingAmt)
    ret.append(",")
    ret.append(if (incomingAmt > outgoingAmt) "received" else "payment")
    ret.append(",")
    ret.append(tx.idem.toHex())
    ret.append(",")
    ret.append(basisOverride?.let { serializeFormat.format(it) } ?: "")
    ret.append(",")
    ret.append(saleOverride?.let { serializeFormat.format(it) } ?: "")
    ret.append(",")
    ret.append(priceWhenIssued.let { serializeFormat.format(it) } ?: "")
    ret.append(",")
    ret.append(priceWhatFiat)
    ret.append(",")
    ret.append(spentWalletAddr.toString())
    ret.append(",")
    ret.append(spentForeignAddr.toString())
    ret.append(",")
    ret.append(rcvWalletAddr.toString())
    ret.append(",")
    ret.append(rcvForeignAddr.toString())
    ret.append(",")
    ret.append("\"" + note + "\"")
    ret.append(",\n")
    return ret.toString()
}

fun TransactionHistoryHeaderCSV(): String
{
    val ret = StringBuilder()
    ret.append("date")
    ret.append(",")
    ret.append("amount (Satoshi NEX)")
    ret.append(",")
    ret.append("change")
    ret.append(",")
    ret.append("transaction and index")
    ret.append(",")
    ret.append("basis")
    ret.append(",")
    ret.append("sale")
    ret.append(",")
    ret.append("price")
    ret.append(",")
    ret.append("fiat currency")
    ret.append(",")
    ret.append("spent wallet addresses")
    ret.append(",")
    ret.append("spent foreign addresses")
    ret.append(",")
    ret.append("received addresses")
    ret.append(",")
    ret.append("sent to addresses")
    ret.append(",")

    ret.append("note")
    ret.append(",\n")
    return ret.toString()
}



class TxHistoryBinder(val ui: TxHistoryListItemBinding): GuiListItemBinder<TransactionHistory>(ui.root)
{
    // Fill the view with this data
    override fun populate()
    {
        // abstract fun getBalanceIn(dest: PayAddress): Long

        val formatter = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM).withZone(ZoneId.systemDefault())

        val activity = (view.context as TxHistoryActivity)
        val act = activity.account
        if (act == null) return

        data?.let()
        { data ->
            val amt = data.incomingAmt - data.outgoingAmt
            val ldt = LocalDateTime.ofInstant(Instant.ofEpochMilli(data.date), ZoneId.systemDefault())
            val fdate = ldt.format(formatter)
            ui.GuiTxDate.text = fdate
            ui.GuiValueCrypto.text = act.cryptoFormat.format(act.fromFinestUnit(amt))

            val sendIm = appContext?.let { ContextCompat.getDrawable(it.context, R.drawable.ic_sendarrow) }
            val recvIm = appContext?.let { ContextCompat.getDrawable(it.context, R.drawable.ic_receivearrow) }
            ui.GuiTxId.text = data.tx.idem.toHex()

            val addrs = mutableListOf<String>()
            if (data.incomingAmt > data.outgoingAmt)  // receive
            {
                if (recvIm != null) ui.GuiSendRecvImage.setImageDrawable(recvIm)
                for (i in data.incomingIdxes)
                {
                    if (i < data.tx.outputs.size)
                    {
                        val out = data.tx.outputs[i.toInt()]
                        val tp = out.script.parseTemplate(out.amount)
                        if (tp != null)
                        {
                            if (tp.groupInfo == null) addrs.add(out.script.address?.toString() ?: "")
                            // TODO I received a token
                        }
                        else
                            addrs.add(out.script.address?.toString() ?: "")
                    }
                }
            }
            else  // Send
            {
                if (sendIm != null) ui.GuiSendRecvImage.setImageDrawable(sendIm)
                // For a send, we want to show all the addresses we sent TO, so all the addresses that are NOT ours
                for (i in 0L until data.tx.outputs.size)
                {
                    if (!data.incomingIdxes.contains(i))
                    {
                        addrs.add(data.tx.outputs[i.toInt()].script.address?.toString() ?: "")
                    }
                }
            }

            if (addrs.size > 0) {ui.GuiAddress.text = addrs[0]; ui.GuiAddress.visibility = View.VISIBLE}
            if (addrs.size > 1) {ui.GuiAddress1.text = addrs[1]; ui.GuiAddress1.visibility = View.VISIBLE}
            if (addrs.size > 2) {ui.GuiAddress2.text = addrs[2]; ui.GuiAddress2.visibility = View.VISIBLE}
            if (addrs.size > 3) {ui.GuiAddress3.text = addrs[3] + "..."; ui.GuiAddress3.visibility = View.VISIBLE}
            ui.GuiTxNote.text = data.note
            ui.GuiTxNote.visibility = if (data.note != "") View.VISIBLE else View.GONE

            val obj = data
            if (obj.priceWhatFiat != "")
            {
                val netFiat = CurrencyDecimal(amt) * obj.priceWhenIssued
                ui.GuiValueFiat.text = fiatFormat.format(netFiat) + " " + obj.priceWhatFiat

                ui.GuiTxCostBasisOrProfitLoss.text.clear()
                if (amt > 0)
                {
                    if (obj.basisOverride != null)
                        ui.GuiTxCostBasisOrProfitLoss.text.append(fiatFormat.format(obj.basisOverride))
                    else
                        ui.GuiTxCostBasisOrProfitLoss.text.append(fiatFormat.format(netFiat))
                    ui.GuiBasisText.text = appResources?.getText(R.string.CostBasis)
                }
                else
                {
                    val capgain = obj.capGains()
                    ui.GuiTxCostBasisOrProfitLoss.text.append(fiatFormat.format(capgain))
                    if (capgain >= BigDecimal.ZERO)
                    {
                        ui.GuiBasisText.text = appResources?.getText(R.string.CapitalGain)
                    }
                    else
                    {
                        ui.GuiBasisText.text = appResources?.getText(R.string.CapitalLoss)
                    }

                }
            }
            else
            {
                ui.GuiValueFiat.text = ""
            }

        }

        // Alternate colors for each row in the list
        val Acol: Int = appContext?.let { ContextCompat.getColor(it.context, R.color.rowA) } ?: 0xFFEEFFEE.toInt()
        val Bcol: Int = appContext?.let { ContextCompat.getColor(it.context, R.color.rowB) } ?: 0xFFBBDDBB.toInt()
        if ((pos and 1) == 0)
            view.background = ColorDrawable(Acol)
        else
            view.background = ColorDrawable(Bcol)
    }

    /** Click on the history, show web details */
    override fun onClick(v: View)
    {
        changed()
        val activity = (view.context as TxHistoryActivity)
        val idx = pos
        val d = data
        if (d == null) return

        synchronized(activity.viewSync)
        {
            val account = activity.account
            if (account == null) return

            LogIt.info("onclick: " + idx + " " + activity.showingDetails)
            if (!activity.showingDetails)
            {
                LogIt.info("set showingDetails")
                activity.showingDetails = true
                val itemHeight = v.height
                val heightButOne = activity.navHeight + activity.listHeight - itemHeight

                activity.linearLayoutManager.scrollToPositionWithOffset(idx, 0)
                activity.ui.GuiTxHistoryList.layoutParams.height = itemHeight
                activity.ui.GuiTxHistoryList.requestLayout()
                activity.ui.GuiTxHistoryList.invalidate()
                activity.ui.GuiTxWebView.layoutParams.height = heightButOne
                activity.ui.GuiTxWebView.requestLayout()
                activity.ui.container.requestLayout()
                activity.ui.navView.visibility = View.GONE
                activity.ui.GuiTxWebView.visibility = View.VISIBLE
                val url = account.transactionInfoWebUrl(d.tx.id.toHex())
                url?.let {
                    activity.ui.GuiTxWebView.loadUrl(url)
                }
            }
            else
            {
                activity.showDetails(false)
            }
            activity.linearLayoutManager.requestLayout()
        }
    }
}

data class AddressInfo(val address: PayAddress, val givenOut: Boolean, val amountHeld: Long, val totalReceived: Long, val firstRecv: Long, val lastRecv: Long)


class AddressListBinder(val ui: AddressListItemBinding): GuiListItemBinder<AddressInfo>(ui.root)
{
    // Fill the view with this data
    override fun populate()
    {
        val formatter = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM).withZone(ZoneId.systemDefault())

        val d = data
        val activity = (view.context as TxHistoryActivity)
        val act = activity.account
        if ((act == null)||(d==null))
        {
            ui.GuiSendRecvImage.visibility = View.GONE
            for (x in listOf(ui.GuiTxDate, ui.GuiTxDateLast,ui.GuiValueCrypto,ui.GuiTotalReceived ))
            {
                x.text = ""
                x.visibility =View.GONE
            }
            return
        }

        ui.GuiAddress.text = d.address.toString()
        ui.GuiValueCrypto.text = act.cryptoFormat.format(act.fromFinestUnit(d.amountHeld))

        for (x in listOf(ui.GuiTxDate, ui.GuiTxDateLast,ui.GuiValueCrypto,ui.GuiTotalReceived ))
            x.visibility = View.VISIBLE

        if ((d.amountHeld > 0)||(d.totalReceived > 0))
        {
            ui.GuiSendRecvImage.visibility = View.VISIBLE
            ui.GuiSendRecvImage.setImageResource(R.drawable.ic_receivearrow)
            ui.GuiValueCrypto.text = i18n(R.string.balance) + " " + act.cryptoFormat.format(act.fromFinestUnit(d.amountHeld))
            ui.GuiTotalReceived.text = i18n(R.string.total) + " " + act.cryptoFormat.format(act.fromFinestUnit(d.totalReceived))

            if (d.firstRecv != Long.MIN_VALUE)
            {
                if (d.firstRecv == d.lastRecv)  // only one receive
                {
                    val ldt = LocalDateTime.ofInstant(Instant.ofEpochMilli(d.firstRecv), ZoneId.systemDefault())
                    ui.GuiTxDate.text = ldt.format(formatter)
                    ui.GuiTxDateLast.text = ""
                }
                else
                {
                    val fdt = LocalDateTime.ofInstant(Instant.ofEpochMilli(d.firstRecv), ZoneId.systemDefault())
                    val ldt = LocalDateTime.ofInstant(Instant.ofEpochMilli(d.lastRecv), ZoneId.systemDefault())
                    ui.GuiTxDate.text = ldt.format(formatter)
                    ui.GuiTxDateLast.text = fdt.format(formatter)
                }
            }
            else
            {
                assert(false)  // should never happen if some amount is held
                ui.GuiTxDate.text = ""
                ui.GuiTxDateLast.text = ""
            }
        }
        else
        {
            ui.GuiSendRecvImage.visibility = View.GONE
            for (x in listOf(ui.GuiTxDate, ui.GuiTxDateLast,ui.GuiValueCrypto,ui.GuiTotalReceived ))
            {
                x.text = ""
                x.visibility =View.GONE
            }
        }

        //ui.GuiTxNote.text = data.note
        ui.GuiTxNote.visibility = View.GONE
    }

    /** Click on the history, show web details */
    override fun onClick(v: View)
    {
        changed()
        val activity = (view.context as TxHistoryActivity)
        val idx = pos
        val d = data
        if (d == null) return

        synchronized(activity.viewSync)
        {
            val account = activity.account
            if (account == null) return

            LogIt.info("onclick: " + idx + " " + activity.showingDetails)
            if (!activity.showingDetails)
            {
                LogIt.info("set showingDetails")
                activity.ui.navView.visibility = View.GONE
                activity.showingDetails = true
                val itemHeight = v.height
                val heightButOne = activity.navHeight + activity.listHeight - itemHeight

                activity.addressListLayoutManager.scrollToPositionWithOffset(idx, 0)
                activity.ui.GuiTxHistoryList.visibility = View.GONE
                activity.ui.GuiAddressList.layoutParams.height = itemHeight
                activity.ui.GuiAddressList.requestLayout()
                activity.ui.GuiAddressList.invalidate()
                activity.ui.GuiTxWebView.layoutParams.height = heightButOne
                activity.ui.GuiTxWebView.requestLayout()
                activity.ui.container.requestLayout()
                activity.ui.GuiTxWebView.visibility = View.VISIBLE

                val url = account.addressInfoWebUrl(d.address.toString())
                url?.let {
                    activity.ui.GuiTxWebView.loadUrl(url)
                }
            }
            else
            {
                activity.showDetails(false)
            }
            activity.addressListLayoutManager.requestLayout()
        }
    }

}


class TxHistoryActivity : CommonNavActivity()
{
    public lateinit var ui: ActivityTxHistoryBinding
    lateinit var linearLayoutManager: LinearLayoutManager
    //private lateinit var adapter: TxHistoryRecyclerAdapter
    private lateinit var adapter: GuiList<TransactionHistory, TxHistoryBinder>
    private lateinit var addressListAdapter: GuiList<AddressInfo, AddressListBinder>
    lateinit var addressListLayoutManager: LinearLayoutManager
    private var shareActionProvider: ShareActionProvider? = null
    var listHeight: Int = 0
    var navHeight: Int = 0

    override var navActivityId = R.id.home

    val viewSync = ThreadCond()
    var showingDetails = false
    var walletName: String? = null
    var historyCSV: String = ""

    val webLoading: String = i18n(R.string.WebLoadingPage)

    var account: Account? = null
    val addresses : MutableList<AddressInfo> = mutableListOf()

    override fun onCreate(savedInstanceState: Bundle?)
    {
        navViewMenuId = R.menu.bottom_account_history_menu  // Show a different bottom menu
        super.onCreate(savedInstanceState)
        ui = ActivityTxHistoryBinding.inflate(layoutInflater)
        setContentView(ui.root)

        linearLayoutManager = LinearLayoutManager(this)
        ui.GuiTxHistoryList.layoutManager = linearLayoutManager
        addressListLayoutManager = LinearLayoutManager(this)
        ui.GuiAddressList.layoutManager = addressListLayoutManager

        ui.GuiTxHistoryList.addOnScrollListener(object : RecyclerView.OnScrollListener()
        {
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int)
            {
                super.onScrollStateChanged(recyclerView, newState)
            }
        }
        )

        walletName = intent.getStringExtra("WalletName")
        if (walletName == null)  // Don't know what to show
        {
            finish()
            return
        }

        // Start up in the addresses tab
        val tab = intent.getStringExtra("tab")
        if (tab == "addresses")
        {
            ui.GuiTxWebView.visibility = View.GONE
            ui.GuiTxHistoryList.visibility = View.GONE
            ui.GuiAddressList.visibility = View.VISIBLE
        }

        ui.GuiTxWebView.settings.javaScriptEnabled = true
        // To use an external browser, don't set this
        ui.GuiTxWebView.webViewClient = WebViewClient()
        ui.GuiTxWebView.loadData(webLoading,"text/html; charset=UTF-8", null)

        ui.root.viewTreeObserver.addOnGlobalLayoutListener(object: OnGlobalLayoutListener {
            override fun onGlobalLayout()
            {
                listHeight = max(listHeight, ui.GuiTxHistoryList.measuredHeight)
                navHeight = max(navHeight, ui.navView.measuredHeight)
            }
        }
        )


        laterUI {
            walletName?.let { walName ->
                if (application !is WallyApp)
                {
                    finish()
                }
                else
                {
                    val app = (application as WallyApp)
                    val coin = app.accounts[walName]
                    if (coin != null)
                    {
                        setTitle(i18n(R.string.title_activity_tx_history) % mapOf("account" to walName))
                        val wallet = coin.wallet
                        val historyList: List<TransactionHistory> = wallet.txHistory.values.sortedBy { it.date }.reversed()
                        account = coin
                        adapter = GuiList(ui.GuiTxHistoryList, historyList, this, {
                            val ui = TxHistoryListItemBinding.inflate(LayoutInflater.from(it.context), it, false)
                            TxHistoryBinder(ui)
                        })

                        val csv = StringBuilder()
                        csv.append(TransactionHistoryHeaderCSV())
                        for (h in historyList)
                        {
                            csv.append(h.toCSV())
                        }

                        // Set up the share intent
                        historyCSV = csv.toString()
                        val receiveAddrSendIntent: Intent = Intent().apply {
                            action = Intent.ACTION_SEND
                            putExtra(Intent.EXTRA_TEXT, historyCSV)
                            type = "text/*"
                        }

                        later {
                            fillAddressList()
                            laterUI {
                                addressListAdapter = GuiList(ui.GuiAddressList, addresses, this, {
                                    val ui = AddressListItemBinding.inflate(LayoutInflater.from(it.context), it, false)
                                    AddressListBinder(ui)
                                })
                                addressListAdapter.rowBackgroundColors = WallyRowColors
                            }
                        }

                        // copy the info to the clipboard
                        val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
                        try
                        {
                            val clip = ClipData.newPlainText("text", historyCSV)
                            clipboard.setPrimaryClip(clip)
                        }
                        catch (e: RuntimeException)
                        {
                            // Unexpected Exception: java.lang.RuntimeException: android.os.TransactionTooLargeException
                            // This is irritating now because every clipboard set is toasted onscreen
                            //val clip = ClipData.newPlainText("text", "history is too large for clipboard")
                            //clipboard.setPrimaryClip(clip)
                        }


                        try
                        {
                            // can't set this until the options menu is created, which happens sometime after onCreate
                            while (shareActionProvider == null) delay(200)
                            shareActionProvider?.setShareIntent(receiveAddrSendIntent)
                        }
                        catch (e: RuntimeException)
                        {
                            // too big for sharing
                        }

                    }
                    else  // coin disappeared out of under this activity
                        finish()
                }
            }
        }
    }


    override fun onBackPressed()
    {
        if (showingDetails)
        {
            showDetails(false)

        }
        else super.onBackPressed()
    }

    fun showDetails(show: Boolean)
    {
        if (show)
        {
            // TODO
        }
        else
        {
            showingDetails = false
            ui.navView.visibility = View.VISIBLE
            if (ui.GuiAddressList.visibility != View.GONE)
            {
                ui.GuiAddressList.layoutParams.height = listHeight
                ui.GuiAddressList.invalidate()
                ui.GuiAddressList.requestLayout()
            }
            else
            {
                ui.GuiTxHistoryList.layoutParams.height = listHeight
                ui.GuiTxHistoryList.invalidate()
                ui.GuiTxHistoryList.requestLayout()
            }

            ui.GuiTxWebView.visibility = View.GONE
            ui.GuiTxWebView.requestLayout()
            ui.GuiTxWebView.loadData(webLoading,"text/html; charset=UTF-8", null)
            ui.container.requestLayout()
        }
    }

    fun fillAddressList()
    {
        val acc = account
        if (acc == null) return

        val addrs = acc.wallet.allAddresses
        addresses.clear()
        for (a in addrs)
        {
            val used = acc.wallet.isAddressGivenOut(a)
            val holding = acc.wallet.getBalanceIn(a)
            val totalReceived = acc.wallet.getBalanceIn(a, false)

            val os = a.outputScript()
            var first = Long.MAX_VALUE
            var last = Long.MIN_VALUE
            for (txh in acc.wallet.txHistory)
            {
                var amt = 0L
                for (out in txh.value.tx.outputs)
                {
                    if (os contentEquals out.script)
                    {
                        amt += out.amount
                        break
                    }
                }
                if (amt > 0)
                {
                    if (first > txh.value.date) first = txh.value.date
                    if (last < txh.value.date) last = txh.value.date
                }
            }

            if (used)
                addresses.add(AddressInfo(a, used, holding, totalReceived, first, last))
        }
        addresses.sortWith(object:  Comparator<AddressInfo> {
            override fun compare(a: AddressInfo?, b: AddressInfo?): Int
            {
                if (a == null) return -1
                if (b == null) return 1
                // First sort by what's in the addresses
                if ((a.amountHeld > 0)||(b.amountHeld > 0))
                {
                    if (a.amountHeld > b.amountHeld) return -1
                    if (b.amountHeld > a.amountHeld) return 1
                    return a.address.toString().compareTo(b.address.toString())
                }
                // Next sort by the what used to be in the addresses
                if ((a.totalReceived > 0) || (b.totalReceived > 0))
                {
                    if (a.totalReceived > b.totalReceived) return -1
                    if (b.totalReceived > a.totalReceived) return 1
                    return a.address.toString().compareTo(b.address.toString())
                }
                // Finally in lexographical order of address
                return a.address.toString().compareTo(b.address.toString())
            }
        })
    }

    // not being called! (except when the back button is pressed)
    override fun onOptionsItemSelected(item: MenuItem): Boolean
    {
        when (item.getItemId())
        {
            R.id.menu_item_share ->
            {
                var clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
                var clip = ClipData.newPlainText("text", historyCSV)
                clipboard.setPrimaryClip(clip)
            }
        }
        return super.onOptionsItemSelected(item)
    }

    /** Inflate the options menu */
    override fun onCreateOptionsMenu(menu: Menu): Boolean
    {
        val inflater: MenuInflater = menuInflater

        inflater.inflate(R.menu.options_menu, menu)

        // Locate MenuItem with ShareActionProvider
        val item = menu.findItem(R.id.menu_item_share)
        // Fetch and store ShareActionProvider
        shareActionProvider = MenuItemCompat.getActionProvider(item) as? ShareActionProvider
        item.setVisible(true)
        shareActionProvider

        super.onCreateOptionsMenu(menu)
        menu.findItem(R.id.settings)?.setVisible(false)
        menu.findItem(R.id.help)?.setVisible(false)
        menu.findItem(R.id.unlock)?.setVisible(false)
        return true
    }

    override fun bottomNavSelectHandler(item: MenuItem): Boolean
    {
        when (item.itemId)
        {
            R.id.navigation_tx_history ->
            {
                ui.GuiTxHistoryList.visibility = View.VISIBLE
                ui.GuiAddressList.visibility = View.GONE
                ui.GuiTxWebView.visibility = View.GONE  // if you switch away, then remove the web view
                return true
            }

            R.id.navigation_addresses ->
            {
                ui.GuiTxWebView.visibility = View.GONE
                ui.GuiTxHistoryList.visibility = View.GONE
                ui.GuiAddressList.visibility = View.VISIBLE
                return true
            }
        }
        return super.bottomNavSelectHandler(item)
    }

}
