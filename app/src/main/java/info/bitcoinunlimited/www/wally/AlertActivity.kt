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
import info.bitcoinunlimited.www.wally.databinding.ActivityAlertsBinding
import info.bitcoinunlimited.www.wally.databinding.AlertListItemBinding
//import kotlinx.android.synthetic.main.activity_alerts.*
//import kotlinx.android.synthetic.main.activity_tx_history.*
//import kotlinx.android.synthetic.main.activity_tx_history.container
//import kotlinx.android.synthetic.main.alert_list_item.view.*
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.*
import java.util.logging.Logger
import kotlin.collections.ArrayList

private val LogIt = Logger.getLogger("BU.wally.Alert")


private class AlertRecyclerAdapter(private val activity: AlertActivity, private val alerts: ArrayList<Alert>) : RecyclerView.Adapter<AlertRecyclerAdapter.AlertDomainHolder>()
{
    lateinit var ui: AlertListItemBinding
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AlertRecyclerAdapter.AlertDomainHolder
    {
        val view = parent.inflate(R.layout.alert_list_item, false)

        return AlertDomainHolder(activity, view)
    }

    override fun getItemCount(): Int = alerts.size


    override fun onBindViewHolder(holder: AlertRecyclerAdapter.AlertDomainHolder, position: Int)
    {
        val item = alerts[alerts.size - 1 - position]
        holder.bind(item, position)
    }

    class AlertDomainHolder(private val activity: AlertActivity, private val view: View) : RecyclerView.ViewHolder(view), View.OnClickListener
    {
        private var alert: Alert? = null
        var idx = 0
        var showDev: Boolean

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
                    val some = 5
                    LogIt.info("set showingDetails")
                    activity.showingDetails = true
                    val itemsHeight = v.height * some
                    val heightButSome = activity.ui.GuiAlertList.height - itemsHeight

                    activity.linearLayoutManager.scrollToPositionWithOffset(idx, 0)
                    activity.ui.GuiAlertList.layoutParams.height = itemsHeight
                    activity.ui.GuiAlertList.requestLayout()
                    activity.ui.GuiAlertList.invalidate()
                    activity.ui.GuiAlertView.layoutParams.height = heightButSome
                    activity.ui.GuiAlertView.requestLayout()
                    if (alert?.details != null)
                    {
                        activity.ui.GuiAlertView.text = alert?.details
                    }
                    else
                    {
                        activity.ui.GuiAlertView.text = i18n(R.string.noAdditionalDetails)
                    }
                    activity.ui.container.requestLayout()
                }
                else
                {
                    activity.showingDetails = false
                    activity.ui.container.requestLayout()
                    activity.ui.GuiAlertList.layoutParams.height = activity.listHeight
                    activity.ui.GuiAlertList.invalidate()
                    activity.ui.GuiAlertList.requestLayout()

                    activity.ui.GuiAlertView.layoutParams.height = 1
                    activity.ui.GuiAlertView.requestLayout()
                    activity.ui.GuiAlertView.text = ""
                }
                activity.linearLayoutManager.requestLayout()
            }

            /* kicks you to the browser
            var intent = Intent(v.context, DomainIdentitySettings::class.java)
            intent.putExtra("domainName", this.id?.domain )
            v.context.startActivity(intent)
             */
        }

        fun bind(obj: Alert, pos: Int)
        {
            idx = pos
            alert = obj
            view.GuiAlertText.text = obj.msg
            view.GuiAlertDate.text = activity.formatter.format(obj.date)

            val a = alert
            var col = if (a == null) 0xFF808080
            else if (a.level >= EXCEPTION_LEVEL)
                0xFFFF8080
            else if (a.level >= ERROR_LEVEL)
                0xFFFFD080
            else if ((pos and 1) == 0)
                0xFFEEEEEE
            else
                0xFFBBDDBB

            /*
            // Alternate colors for each row in the list
            val Acol: Int = appContext?.let { ContextCompat.getColor(it.context, R.color.rowA) } ?: 0xFFEEFFEE.toInt()
            val Bcol: Int = appContext?.let { ContextCompat.getColor(it.context, R.color.rowB) } ?: 0xFFBBDDBB.toInt()
            if ((pos and 1) == 0)
            {
            }
            else
            {
            }
             */

            view.background = ColorDrawable(col.toInt())
        }
    }

}

class AlertActivity : CommonNavActivity()
{
    lateinit var linearLayoutManager: LinearLayoutManager
    private lateinit var adapter: AlertRecyclerAdapter
    lateinit var ui: ActivityAlertsBinding
    var listHeight: Int = 0

    val formatter = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT)
      .withLocale(Locale.getDefault())
      .withZone(ZoneId.systemDefault())

    override var navActivityId = R.id.home

    val viewSync = ThreadCond()
    var showingDetails = false

    override fun onCreate(savedInstanceState: Bundle?)
    {
        super.onCreate(savedInstanceState)
        ui = ActivityAlertsBinding.inflate(layoutInflater)
        setContentView(R.layout.activity_alerts)

        linearLayoutManager = LinearLayoutManager(this)
        ui.GuiAlertList.layoutManager = linearLayoutManager

        // Remember the original height so that it can be restored when we move it
        listHeight = ui.GuiAlertList.layoutParams.height

        ui.GuiAlertList.addOnScrollListener(object : RecyclerView.OnScrollListener()
        {
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int)
            {
                super.onScrollStateChanged(recyclerView, newState)
            }
        }
        )

        laterUI {
            setTitle(i18n(R.string.title_activity_alert_history))
            adapter = AlertRecyclerAdapter(this, alerts)
            ui.GuiAlertList.adapter = adapter
        }
    }

}
