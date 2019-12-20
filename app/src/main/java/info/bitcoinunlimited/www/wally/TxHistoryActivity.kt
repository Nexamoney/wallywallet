// Copyright (c) 2019 Andrew Stone Consulting (qq9wwnuw4eukyh5g34ckg5vk4aaxnvr04vkspyv850)
// Distributed under the MIT software license, see the accompanying file COPYING or http://www.opensource.org/licenses/mit-license.php.
package info.bitcoinunlimited.www.wally

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


private class TxHistoryRecyclerAdapter(private val domains: ArrayList<PaymentHistory>, private val coin:Coin) : RecyclerView.Adapter<TxHistoryRecyclerAdapter.TxHistoryDomainHolder>()
{

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TxHistoryRecyclerAdapter.TxHistoryDomainHolder
    {
        val inflatedView = parent.inflate(R.layout.tx_history_list_item, false)
        return TxHistoryDomainHolder(inflatedView, coin)
    }

    override fun getItemCount(): Int = domains.size


    override fun onBindViewHolder(holder: TxHistoryRecyclerAdapter.TxHistoryDomainHolder, position: Int)
    {
        val item = domains[position]
        holder.bind(item, position)
    }


    class TxHistoryDomainHolder(private val view: View, private val coin: Coin) : RecyclerView.ViewHolder(view), View.OnClickListener
    {
        private var id: PaymentHistory? = null

        init
        {
            view.setOnClickListener(this)
        }

        override fun onClick(v: View)
        {
            /*
            var intent = Intent(v.context, DomainIdentitySettings::class.java)
            intent.putExtra("domainName", this.id?.domain )
            v.context.startActivity(intent)
             */
        }

        fun bind(obj: PaymentHistory, pos: Int)
        {
            this.id = obj
            view.GuiTxId.text = obj.txHash!!.toHex()
            val fmt = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM).withZone(ZoneId.systemDefault())
            val epochSec = Instant.ofEpochSecond(obj.date/1000)
            view.GuiTxDate.text = fmt.format(epochSec)
            val netAmt = coin.fromFinestUnit(obj.amount)
            view.GuiValueCrypto.text = coin.cryptoFormat.format(netAmt)

            view.GuiAddress.text = obj.address.toString()

            if (obj.isInflow == false)
            {
                val sendIm = appContext?.let { ContextCompat.getDrawable(it.context, R.drawable.ic_sendarrow) }
                if (sendIm != null)
                {
                    view.GuiSendRecvImage.setImageDrawable(sendIm)
                }
            }

            if (obj.priceWhatFiat != "")
            {
                val netFiat = CurrencyDecimal(obj.amount)*obj.priceWhenIssued
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
            val Acol:Int = appContext?.let { ContextCompat.getColor(it.context, R.color.rowA) } ?: 0xFFEEFFEE.toInt()
            val Bcol:Int = appContext?.let { ContextCompat.getColor(it.context, R.color.rowB) } ?: 0xFFBBDDBB.toInt()

            if ((pos and 1)==0)
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

class TxHistoryActivity : CommonActivity()
{
    private lateinit var linearLayoutManager: LinearLayoutManager
    private lateinit var adapter: TxHistoryRecyclerAdapter

    override var navActivityId = R.id.home

    var walletName:String? = null

    override fun onCreate(savedInstanceState: Bundle?)
    {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_tx_history)

        linearLayoutManager = LinearLayoutManager(this)
        GuiTxHistoryList.layoutManager = linearLayoutManager

        walletName = intent.getStringExtra("WalletName")

        laterUI {
            walletName?.let { walName ->
                val app = (application as WallyApp)
                val coin = app.coins[walName]
                val wallet = coin!!.wallet
                val history = wallet.paymentHistory.values
                val historyList: List<PaymentHistory> = history.sortedBy { it.date }.filter { !it.isChange }.reversed()
                val txhist: ArrayList<PaymentHistory> = ArrayList(historyList)
                LogIt.info("tx history count:" + txhist.size.toString())
                //LogIt.info(wallet.allIdentityDomains().map { it.domain }.toString())
                adapter = TxHistoryRecyclerAdapter(txhist, coin)
                GuiTxHistoryList.adapter = adapter

                //val dest = wallet.destinationFor(Bip44Wallet.COMMON_IDENTITY_SEED)
                //commonIdentityAddress.text = dest.address.toString()
            }
        }
    }

}
