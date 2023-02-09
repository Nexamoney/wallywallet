// Copyright (c) 2019 Andrew Stone Consulting (qq9wwnuw4eukyh5g34ckg5vk4aaxnvr04vkspyv850)
// Distributed under the MIT software license, see the accompanying file COPYING or http://www.opensource.org/licenses/mit-license.php.
package info.bitcoinunlimited.www.wally

import android.content.*
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.*
import androidx.appcompat.widget.ShareActionProvider
import androidx.core.content.ContextCompat
import androidx.core.view.MenuItemCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import bitcoinunlimited.libbitcoincash.*
import info.bitcoinunlimited.www.wally.databinding.ActivityTxHistoryBinding
import info.bitcoinunlimited.www.wally.databinding.TxHistoryListItemBinding
import kotlinx.coroutines.delay
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.logging.Logger


private val LogIt = Logger.getLogger("BU.wally.TxHistory")

/*
private class TxHistoryRecyclerAdapter(private val activity: TxHistoryActivity, private val domains: ArrayList<PaymentHistory>, private val account: Account) : RecyclerView.Adapter<TxHistoryRecyclerAdapter.TxHistoryDomainHolder>()
{

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TxHistoryRecyclerAdapter.TxHistoryDomainHolder
    {
        val inflatedView = parent.inflate(R.layout.tx_history_list_item, false)
        return TxHistoryDomainHolder(activity, inflatedView, account)
    }

    override fun getItemCount(): Int = domains.size


    override fun onBindViewHolder(holder: TxHistoryRecyclerAdapter.TxHistoryDomainHolder, position: Int)
    {
        val item = domains[position]
        holder.bind(item, position)
    }

    class TxHistoryDomainHolder(private val activity: TxHistoryActivity, private val view: View, private val account: Account) : RecyclerView.ViewHolder(view), View.OnClickListener
    {
        private var id: PaymentHistory? = null
        val sendIm = appContext?.let { ContextCompat.getDrawable(it.context, R.drawable.ic_sendarrow) }
        val recvIm = appContext?.let { ContextCompat.getDrawable(it.context, R.drawable.ic_receivearrow) }
        var idx = 0
        var txid: Hash256? = null
        var showDev: Boolean

        var priorHeight: Int = 0

        init
        {
            val prefs: SharedPreferences = activity.getSharedPreferences(activity.getString(R.string.preferenceFileName), Context.MODE_PRIVATE)
            showDev = prefs.getBoolean(SHOW_DEV_INFO, false)

            view.setOnClickListener(this)
        }

        /** Click on the history, show web details */
        override fun onClick(v: View)
        {
            synchronized(activity.viewSync)
            {
                LogIt.info("onclick: " + idx + " " + activity.showingDetails)
                if (!activity.showingDetails)
                {
                    LogIt.info("set showingDetails")
                    activity.showingDetails = true
                    val itemHeight = v.height
                    val heightButOne = activity.GuiTxHistoryList.height - itemHeight

                    activity.linearLayoutManager.scrollToPositionWithOffset(idx, 0)
                    activity.GuiTxHistoryList.layoutParams.height = itemHeight
                    activity.GuiTxHistoryList.requestLayout()
                    activity.GuiTxHistoryList.invalidate()
                    activity.GuiTxWebView.layoutParams.height = heightButOne
                    activity.GuiTxWebView.requestLayout()
                    activity.container.requestLayout()


                    val url = account.transactionInfoWebUrl(txid?.toHex())
                    url?.let {
                        activity.GuiTxWebView.loadUrl(url)
                    }
                }
                else
                {
                    activity.showingDetails = false
                    activity.container.requestLayout()
                    activity.GuiTxHistoryList.layoutParams.height = activity.listHeight
                    activity.GuiTxHistoryList.invalidate()
                    activity.GuiTxHistoryList.requestLayout()

                    activity.GuiTxWebView.layoutParams.height = 1
                    activity.GuiTxWebView.requestLayout()
                    activity.GuiTxWebView.loadUrl("about:blank")
                }
                activity.linearLayoutManager.requestLayout()
            }

            /* kicks you to the browser
            var intent = Intent(v.context, DomainIdentitySettings::class.java)
            intent.putExtra("domainName", this.id?.domain )
            v.context.startActivity(intent)
             */
        }

        fun bind(obj: PaymentHistory, pos: Int)
        {
            idx = pos
            id = obj
            txid = obj.txIdem
            view.GuiTxId.text = txid?.toHex() ?: ""
            view.GuiTxId.visibility = if (showDev) View.VISIBLE else View.GONE
            view.GuiTxNote.text = obj.note
            view.GuiTxNote.visibility = if (obj.note != "") View.VISIBLE else View.GONE
            val fmt = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM).withZone(ZoneId.systemDefault())
            val epochSec = Instant.ofEpochSecond(obj.date / 1000)
            view.GuiTxDate.text = fmt.format(epochSec)
            val netAmt = account.fromFinestUnit(obj.amount)
            view.GuiValueCrypto.text = account.cryptoFormat.format(netAmt)

            view.GuiAddress.text = obj.address.toString()

            if (obj.isInflow)
            {
                if (recvIm != null)
                    view.GuiSendRecvImage.setImageDrawable(recvIm)
            }
            else
            {
                if (sendIm != null)
                    view.GuiSendRecvImage.setImageDrawable(sendIm)
            }


            if (obj.priceWhatFiat != "")
            {
                val netFiat = CurrencyDecimal(obj.amount) * obj.priceWhenIssued
                view.GuiValueFiat.text = fiatFormat.format(netFiat) + " " + obj.priceWhatFiat

                view.GuiTxCostBasisOrProfitLoss.text.clear()
                if (obj.isInflow)
                {
                    if (obj.basisOverride != null)
                        view.GuiTxCostBasisOrProfitLoss.text.append(fiatFormat.format(obj.basisOverride))
                    else
                        view.GuiTxCostBasisOrProfitLoss.text.append(fiatFormat.format(netFiat))
                    view.GuiBasisText.text = appResources?.getText(R.string.CostBasis)
                }
                else
                {
                    val capgain = obj.capGains()
                    view.GuiTxCostBasisOrProfitLoss.text.append(fiatFormat.format(capgain))
                    if (capgain >= BigDecimal.ZERO)
                    {
                        view.GuiBasisText.text = appResources?.getText(R.string.CapitalGain)
                    }
                    else
                    {
                        view.GuiBasisText.text = appResources?.getText(R.string.CapitalLoss)
                    }

                }
            }
            else
            {
                view.GuiValueFiat.text = ""
            }

            // Alternate colors for each row in the list
            val Acol: Int = appContext?.let { ContextCompat.getColor(it.context, R.color.rowA) } ?: 0xFFEEFFEE.toInt()
            val Bcol: Int = appContext?.let { ContextCompat.getColor(it.context, R.color.rowB) } ?: 0xFFBBDDBB.toInt()
            //val Acol = 0xFFEEFFEE.toInt()
            //val Bcol = 0xFFBBDDBB.toInt()

            if ((pos and 1) == 0)
            {
                view.background = ColorDrawable(Acol)
            }
            else
            {
                view.background = ColorDrawable(Bcol)
            }

        }
    }

}
*/

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
                val heightButOne = activity.ui.GuiTxHistoryList.height - itemHeight

                activity.linearLayoutManager.scrollToPositionWithOffset(idx, 0)
                activity.ui.GuiTxHistoryList.layoutParams.height = itemHeight
                activity.ui.GuiTxHistoryList.requestLayout()
                activity.ui.GuiTxHistoryList.invalidate()
                activity.ui.GuiTxWebView.layoutParams.height = heightButOne
                activity.ui.GuiTxWebView.requestLayout()
                activity.ui.container.requestLayout()


                val url = account.transactionInfoWebUrl(d.tx.id.toHex())
                url?.let {
                    activity.ui.GuiTxWebView.loadUrl(url)
                }
            }
            else
            {
                activity.showingDetails = false
                activity.ui.container.requestLayout()
                activity.ui.GuiTxHistoryList.layoutParams.height = activity.listHeight
                activity.ui.GuiTxHistoryList.invalidate()
                activity.ui.GuiTxHistoryList.requestLayout()

                activity.ui.GuiTxWebView.layoutParams.height = 1
                activity.ui.GuiTxWebView.requestLayout()
                activity.ui.GuiTxWebView.loadUrl("about:blank")
            }
            activity.linearLayoutManager.requestLayout()
        }

        /* kicks you to the browser
        var intent = Intent(v.context, DomainIdentitySettings::class.java)
        intent.putExtra("domainName", this.id?.domain )
        v.context.startActivity(intent)
         */
    }

}

class TxHistoryActivity : CommonNavActivity()
{
    public lateinit var ui: ActivityTxHistoryBinding
    lateinit var linearLayoutManager: LinearLayoutManager
    //private lateinit var adapter: TxHistoryRecyclerAdapter
    private lateinit var adapter: GuiList<TransactionHistory, TxHistoryBinder>
    private var shareActionProvider: ShareActionProvider? = null
    var listHeight: Int = 0

    override var navActivityId = R.id.home

    val viewSync = ThreadCond()
    var showingDetails = false
    var walletName: String? = null
    var historyCSV: String = ""

    var account: Account? = null

    override fun onCreate(savedInstanceState: Bundle?)
    {
        super.onCreate(savedInstanceState)
        ui = ActivityTxHistoryBinding.inflate(layoutInflater)
        setContentView(ui.root)

        linearLayoutManager = LinearLayoutManager(this)
        ui.GuiTxHistoryList.layoutManager = linearLayoutManager

        // Remember the original height so that it can be restored when we move it
        listHeight = ui.GuiTxHistoryList.layoutParams.height

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

        ui.GuiTxWebView.settings.javaScriptEnabled = true


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

                        // copy the info to the clipboard
                        val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
                        try
                        {
                            val clip = ClipData.newPlainText("text", historyCSV)
                            clipboard.setPrimaryClip(clip)
                        }
                        catch (e: android.os.TransactionTooLargeException)
                        {
                            val clip = ClipData.newPlainText("text", "transaction history is too large for clipboard")
                            clipboard.setPrimaryClip(clip)
                        }

                        // can't set this until the options menu is created, which happens sometime after onCreate
                        while (shareActionProvider == null) delay(200)
                        shareActionProvider?.setShareIntent(receiveAddrSendIntent)
                    }
                    else  // coin disappeared out of under this activity
                        finish()
                }
            }
        }
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

}
