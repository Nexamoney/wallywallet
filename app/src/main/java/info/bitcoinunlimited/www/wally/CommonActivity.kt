// Copyright (c) 2019 Andrew Stone Consulting (qq9wwnuw4eukyh5g34ckg5vk4aaxnvr04vkspyv850)
// Distributed under the MIT software license, see the accompanying file COPYING or http://www.opensource.org/licenses/mit-license.php.
package info.bitcoinunlimited.www.wally

import android.content.ClipData
import android.content.ClipboardManager
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import bitcoinunlimited.libbitcoincash.BUException
import bitcoinunlimited.libbitcoincash.ErrorSeverity
import bitcoinunlimited.libbitcoincash.i18n
import com.google.android.material.bottomnavigation.BottomNavigationView
import kotlinx.coroutines.*
import java.lang.Exception
import java.util.logging.Logger
import android.app.Activity
import androidx.core.app.ComponentActivity.ExtraData
import androidx.core.content.ContextCompat.getSystemService
import android.icu.lang.UCharacter.GraphemeClusterBreak.T
import android.view.inputmethod.InputMethodManager
import java.util.concurrent.Executors
import kotlin.coroutines.CoroutineContext

private val LogIt = Logger.getLogger("bitcoinunlimited.commonActivity")

open class CommonActivity : AppCompatActivity()
{
    private val onNavigationItemSelectedListener = BottomNavigationView.OnNavigationItemSelectedListener { item -> bottomNavSelectHandler(item, this) }

    var origTitle = String()  //* The app's actual title (I will sometimes overwrite it with a temporary error message)
    var origTitleBackground: ColorDrawable? = null  //* The app's title background color (I will sometimes overwrite it with a temporary error message)

    open var navActivityId = -1 //* Change this in derived classes to identify which navBar item this activity is

    protected val coGuiScope = MainScope()

    protected val coMiscCtxt: CoroutineContext = Executors.newFixedThreadPool(2).asCoroutineDispatcher()
    protected val coMiscScope: CoroutineScope = kotlinx.coroutines.CoroutineScope(coMiscCtxt)

    override fun onCreate(savedInstanceState: Bundle?)
    {
        super.onCreate(savedInstanceState)

        origTitle = title.toString()
        var titlebar: View = findViewById(R.id.action_bar)
        origTitleBackground = ColorDrawable(ContextCompat.getColor(applicationContext, R.color.titleBackground))

        origTitleBackground?.let { titlebar.background = it }  // Set the title background color here, so we don't need to match the background defined in some resource file

        titlebar.setOnClickListener {
            LogIt.info("title button pressed")
            // TODO connect to a popup window that shows recent errors
        }

    }

    override fun onStart()
    {
        super.onStart()

        // Finding a UI element has to happen after the derived class has inflated the view, so it cannot be in onCreate.
        val navView: BottomNavigationView = findViewById(R.id.nav_view)
        navView.setOnNavigationItemSelectedListener(null)
        if (navActivityId >= 0)  // This will both change the selection AND switch to that activity if it is different than the current one!
            navView.selectedItemId = navActivityId
        navView.setOnNavigationItemSelectedListener(onNavigationItemSelectedListener)

    }

    override fun onDestroy()
    {
        coMiscCtxt.cancel()
        coGuiScope.cancel()
        coMiscScope.cancel()
        super.onDestroy()

    }

    fun displayException(exc: Exception)
    {
        var displayString: String
        val buExc = exc as? BUException
        if (buExc != null)
        {
            if (buExc.severity != ErrorSeverity.Expected)
            {
                val stack = Log.getStackTraceString(buExc)
                LogIt.severe(buExc.shortMsg + ":" + buExc.message)
                LogIt.severe(stack)
            }
            displayString = buExc.shortMsg ?: buExc.message ?: getString(R.string.unknownError)
        }
        else
        {
            // Log all non-BU exceptions because we don't know if they are expected
            val stack = Log.getStackTraceString(exc)
            LogIt.severe(exc.toString())
            LogIt.severe(stack)

            displayString = exc.message ?: getString(R.string.unknownError)
        }

        displayError(displayString)
    }

    /** Display an short error string on the title bar, and then clear it after a bit */
    fun displayError(resource: Int) = displayError(getString(resource))

    /** Display an short error string on the title bar, and then clear it after a bit */
    fun displayError(err: String, then: (()->Unit)? = null)
    {
        laterUI {
            // This coroutine has to be limited to this thread because only the main thread can touch UI views
            // Display the error by changing the title and title bar color temporarily
            setTitle(err);

            var titlebar: View = findViewById(R.id.action_bar)
            val errorColor = ContextCompat.getColor(applicationContext, R.color.error)
            titlebar.background = ColorDrawable(errorColor)

            delay(ERROR_DISPLAY_TIME)
            setTitle(origTitle)
            origTitleBackground?.let { titlebar.background = it }
            if (then != null) then()
        }

    }

    /** Display an short notification string on the title bar, and then clear it after a bit */
    fun displayNotice(resource: Int) = displayNotice(getString(resource))

    /** Display an short notification string on the title bar, and then clear it after a bit */
    fun displayNotice(msg: String, then: (()->Unit)? = null, time: Long = NOTICE_DISPLAY_TIME)
    {
        laterUI {
            // This coroutine has to be limited to this thread because only the main thread can touch UI views
            // Display the error by changing the title and title bar color temporarily
            setTitle(msg);

            var titlebar: View = findViewById(R.id.action_bar)
            val errorColor = ContextCompat.getColor(applicationContext, R.color.notice)
            titlebar.background = ColorDrawable(errorColor)

            delay(time)
            setTitle(origTitle)
            origTitleBackground?.let { titlebar.background = it }
            if (then != null) then()
        }
    }

    /** Do whatever you pass within the user interface context, synchronously */
    fun <RET> doUI(fn: suspend () -> RET): RET
    {
        return runBlocking(Dispatchers.Main) {
            fn()
        }

    }


    /** Do whatever you pass within the user interface context, asynchronously */
    fun later(fn: suspend () -> Unit): Unit
    {
        coMiscScope.launch {
            fn()
        }
    }

    /** Do whatever you pass within the user interface context, asynchronously */
    fun laterUI(fn: suspend () -> Unit): Unit
    {
        coGuiScope.launch {
            fn()
        }
    }

    fun hideKeyboard()
    {
        val imm = getSystemService(Activity.INPUT_METHOD_SERVICE) as InputMethodManager
        //Find the currently focused view, so we can grab the correct window token from it.
        var view = currentFocus
        //If no view currently has focus, create a new one, just so we can grab a window token from it
        if (view == null)
        {
            view = View(this)
        }
        imm.hideSoftInputFromWindow(view.windowToken, 0)
    }

    fun copyTextToClipboard(v: TextView)
    {
        val addr = v.text
        try
        {
            var clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager

            if (addr != null)
            {
                var clip = ClipData.newPlainText("text", addr)
                clipboard.primaryClip = clip

                // visual bling that indicates text copied
                v.text = i18n(R.string.copied)
                // Set it back to the address after awhile
                GlobalScope.launch {
                    delay(3000);
                    v.text = addr
                }
            }
            else throw UnavailableException(R.string.receiveAddressUnavailable)
        }
        catch (e: Exception)
        {
            displayException(e)
        }
    }
}
