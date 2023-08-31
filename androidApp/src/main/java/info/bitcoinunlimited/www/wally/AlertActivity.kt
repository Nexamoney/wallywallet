// Copyright (c) 2019 Andrew Stone Consulting (qq9wwnuw4eukyh5g34ckg5vk4aaxnvr04vkspyv850)
// Distributed under the MIT software license, see the accompanying file COPYING or http://www.opensource.org/licenses/mit-license.php.
package info.bitcoinunlimited.www.wally

import org.nexa.libnexakotlin.*
import org.nexa.threads.*
import android.content.Context
import android.content.SharedPreferences
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import info.bitcoinunlimited.www.wally.databinding.ActivityAlertsBinding
import info.bitcoinunlimited.www.wally.databinding.AlertListItemBinding
import info.bitcoinunlimited.www.wally.databinding.InfoeditrowBinding
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.*
import java.util.logging.Logger
import kotlin.collections.ArrayList


private val LogIt = Logger.getLogger("BU.wally.Alert")

class AlertBinder(val ui: AlertListItemBinding, val activity: AlertActivity): GuiListItemBinder<Alert>(ui.root)
{
    // Fill the view with this data
    override fun populate()
    {
        val a = data
        if (a != null)
        {
            ui.GuiAlertText.text = a.msg
            ui.GuiAlertDate.text = activity.formatter.format(a.date)
        }
    }

    override fun backgroundColor(highlight:Boolean):Long
    {
        val a = data
        var col = if (a == null) 0xFF808080
        else if (a.level >= EXCEPTION_LEVEL)
            if (highlight) 0xFFFFC080 else 0xFFFF8080
        else if (a.level >= ERROR_LEVEL)
            if (highlight) 0xFFFFE080 else 0xFFFFD080
        else if ((pos and 1) == 0)
            if (highlight) 0xFFFFFFEE else 0xFFEEEEEE
        else
            if (highlight) 0xFFFFFFEE else 0xFFBBDDBB
        return col

        //ui.root.background = ColorDrawable(col.toInt())
    }

    /** Click on the history, show web details */
    override fun onClick(v: View)
    {
        synchronized(activity.viewSync)
        {
            LogIt.info("onclick: " + pos + " " + activity.showingDetails)
            val d = data
            if (d == null) return@synchronized  // its an empty row
            if (!activity.showingDetails)
            {
                val some = 5
                LogIt.info("set showingDetails")
                activity.showingDetails = true
                val itemsHeight = v.height * some
                val heightButSome = activity.ui.GuiAlertList.height - itemsHeight

                activity.linearLayoutManager.scrollToPositionWithOffset(pos, 0)
                activity.ui.GuiAlertList.layoutParams.height = itemsHeight
                activity.ui.GuiAlertList.requestLayout()
                activity.ui.GuiAlertList.invalidate()
                activity.ui.GuiAlertView.layoutParams.height = heightButSome
                activity.ui.GuiAlertView.requestLayout()
                var detailsText = if ((d.details != null)&&(d.details != ""))
                {
                    d.details
                }
                else
                {
                    i18n(R.string.noAdditionalDetails)
                }

                if (devMode)
                {
                    val traceString = d.trace.map { "[" + it.fileName + ":" + it.lineNumber + "] " + it.className.split(".").last() + "." + it.methodName }.joinToString("\n")
                    detailsText = detailsText + "\n\n" + traceString
                }
                activity.ui.GuiAlertView.text = detailsText
                activity.ui.container.requestLayout()
                activity.highlight(pos)
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
                activity.dehighlight()
            }
            activity.linearLayoutManager.requestLayout()
        }
    }
}

class AlertActivity : CommonNavActivity()
{
    public lateinit var ui : ActivityAlertsBinding
    lateinit var linearLayoutManager: LinearLayoutManager
    private lateinit var adapter: GuiList<Alert, AlertBinder>
    var listHeight: Int = 0

    val formatter: DateTimeFormatter = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT)
      .withLocale(Locale.getDefault())
      .withZone(ZoneId.systemDefault())

    override var navActivityId = R.id.navigation_home

    val viewSync = Gate()
    var showingDetails = false

    fun highlight(idx: Int)
    {
        adapter.highlight(idx)
    }

    fun dehighlight()
    {
        adapter.dehighlight()
    }

    override fun onCreate(savedInstanceState: Bundle?)
    {
        super.onCreate(savedInstanceState)
        ui = ActivityAlertsBinding.inflate(layoutInflater)
        setContentView(ui.root)

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
            adapter = GuiList(ui.GuiAlertList, alerts, this, { vg ->
                val ui = AlertListItemBinding.inflate(LayoutInflater.from(vg.context), vg, false)
                AlertBinder(ui, this)
            })


        }
    }

}
