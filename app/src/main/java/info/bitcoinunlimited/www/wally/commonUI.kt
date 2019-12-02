// Copyright (c) 2019 Andrew Stone Consulting (qq9wwnuw4eukyh5g34ckg5vk4aaxnvr04vkspyv850)
// Distributed under the MIT software license, see the accompanying file COPYING or http://www.opensource.org/licenses/mit-license.php.
package info.bitcoinunlimited.www.wally

import android.app.Activity
import android.content.Intent
import android.view.MenuItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

const val SPLITBILL_MESSAGE = "info.bitcoinunlimited.www.wally.splitbill"
const val TRICKLEPAY_MESSAGE = "info.bitcoinunlimited.www.wally.tricklepay"
const val EXCHANGE_MESSAGE = "info.bitcoinunlimited.www.wally.exchange"
const val SETTINGS_MESSAGE = "info.bitcoinunlimited.www.wally.settings"
const val INVOICES_MESSAGE = "info.bitcoinunlimited.www.wally.exchange"
const val IDENTITY_MESSAGE = "info.bitcoinunlimited.www.wally.settings"


/** Do whatever you pass within the user interface context, synchronously */
fun <RET> doUI(fn: suspend () -> RET): RET
{
    return runBlocking(Dispatchers.Main) {
        fn()
    }
}

/** Do whatever you pass within the user interface context, asynchronously */
fun asyncUI(fn: suspend () -> Unit): Unit
{
    GlobalScope.launch(Dispatchers.Main) {
        fn()
    }
}


// note to stop multiple copies of activities from being launched, use android:launchMode="singleTask" in the activity definition in AndroidManifest.xml
fun bottomNavSelectHandler(item: MenuItem, ths: Activity):Boolean
{
    when (item.itemId)
    {
        R.id.navigation_identity ->
        {
            val message = "" // anything extra we want to send
            val intent = Intent(ths, IdentityActivity::class.java).apply {
                putExtra(IDENTITY_MESSAGE, message)
            }
            ths.startActivity(intent)
            return@bottomNavSelectHandler true
        }

        R.id.navigation_trickle_pay ->
        {
            val message = "" // anything extra we want to send
            val intent = Intent(ths, TricklePayActivity::class.java).apply {
                putExtra(TRICKLEPAY_MESSAGE, message)
            }
            ths.startActivity(intent)
            return@bottomNavSelectHandler true
        }

        R.id.navigation_exchange ->
        {
            val message = "" // anything extra we want to send
            val intent = Intent(ths, ExchangeActivity::class.java).apply {
                putExtra(EXCHANGE_MESSAGE, message)
            }
            ths.startActivity(intent)
            return@bottomNavSelectHandler true
        }

        R.id.navigation_invoices ->
        {
            val message = "" // anything extra we want to send
            val intent = Intent(ths, InvoicesActivity::class.java).apply {
                putExtra(INVOICES_MESSAGE, message)
            }
            ths.startActivity(intent)
            return@bottomNavSelectHandler true
        }

        R.id.navigation_home ->
        {
            val intent = Intent(ths, MainActivity::class.java)
            ths.startActivity(intent)
            return@bottomNavSelectHandler true

            /* This goes back */
            /*
            if (!(ths is MainActivity))
            {
                ths.finish()
                return@bottomNavSelectHandler true
            }
            return false
            */
        }
/*
        R.id.navigation_identity ->
        {
            val message = "" // anything extra we want to send
            val intent = Intent(ths, IdentityActivity::class.java).apply {
                putExtra(IDENTITY_MESSAGE, message)
            }
            ths.startActivity(intent)
            return@bottomNavSelectHandler true
        }
        */

    }

    return false
}