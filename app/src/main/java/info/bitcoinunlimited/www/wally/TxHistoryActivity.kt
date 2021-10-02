// Copyright (c) 2019 Andrew Stone Consulting (qq9wwnuw4eukyh5g34ckg5vk4aaxnvr04vkspyv850)
// Distributed under the MIT software license, see the accompanying file COPYING or http://www.opensource.org/licenses/mit-license.php.
package info.bitcoinunlimited.www.wally

import android.content.Context
import android.content.SharedPreferences
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import bitcoinunlimited.libbitcoincash.*
import kotlinx.android.synthetic.main.activity_tx_history.*
import kotlinx.android.synthetic.main.tx_history_list_item.view.*
import java.math.BigDecimal
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.logging.Logger
import kotlin.collections.ArrayList

private val LogIt = Logger.getLogger("bitcoinunlimited.TxHistoryActivity")


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
            txid = obj.txHash
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

class TxHistoryActivity : CommonNavActivity()
{
    lateinit var linearLayoutManager: LinearLayoutManager
    private lateinit var adapter: TxHistoryRecyclerAdapter
    var listHeight: Int = 0

    override var navActivityId = R.id.home

    val viewSync = ThreadCond()
    var showingDetails = false
    var walletName: String? = null

    override fun onCreate(savedInstanceState: Bundle?)
    {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_tx_history)

        linearLayoutManager = LinearLayoutManager(this)
        GuiTxHistoryList.layoutManager = linearLayoutManager

        // Remember the original height so that it can be restored when we move it
        listHeight = GuiTxHistoryList.layoutParams.height

        var activity = this

        GuiTxHistoryList.addOnScrollListener(object : RecyclerView.OnScrollListener()
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

        GuiTxWebView.settings.javaScriptEnabled = true


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
                        setTitle(i18n(R.string.title_activity_tx_history) % mapOf("walname" to walName));
                        val wallet = coin.wallet
                        val history = wallet.paymentHistory.values
                        val historyList: List<PaymentHistory> = history.sortedBy { it.date }.filter { !it.isChange }.reversed()
                        val txhist: ArrayList<PaymentHistory> = ArrayList(historyList)
                        LogIt.info("tx history count:" + txhist.size.toString())
                        //LogIt.info(wallet.allIdentityDomains().map { it.domain }.toString())
                        adapter = TxHistoryRecyclerAdapter(this, txhist, coin)
                        GuiTxHistoryList.adapter = adapter

                        //val dest = wallet.destinationFor(Bip44Wallet.COMMON_IDENTITY_SEED)
                        //commonIdentityAddress.text = dest.address.toString()
                    }
                    else  // coin disappeared out of under this activity
                        finish()
                }
            }
        }
    }

}
