// Copyright (c) 2019 Andrew Stone Consulting (qq9wwnuw4eukyh5g34ckg5vk4aaxnvr04vkspyv850)
// Distributed under the MIT software license, see the accompanying file COPYING or http://www.opensource.org/licenses/mit-license.php.
package info.bitcoinunlimited.www.wally

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import bitcoinunlimited.libbitcoincash.*
import bitcoinunlimited.libbitcoincash.ErrorSeverity
import com.google.android.material.bottomnavigation.BottomNavigationView
import kotlinx.coroutines.*
import java.lang.Exception
import java.util.logging.Logger
import android.app.Activity
import android.content.Intent
import android.content.res.Resources
import android.graphics.Rect
import android.net.Uri
import android.util.TypedValue
import android.view.KeyEvent
import android.view.Menu
import android.view.ViewTreeObserver
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import bitcoinunlimited.libbitcoincash.handleThreadException
import java.time.Instant
import java.util.concurrent.Executors
import kotlin.coroutines.CoroutineContext
import kotlin.math.roundToInt

private val LogIt = Logger.getLogger("BU.wally.commonActivity")

var currentActivity: CommonActivity? = null
public var appResources: Resources? = null

// TODO translate libbitcoincash error codes to our i18n strings
val lbcMap = mapOf<Int, Int>(RinsufficentBalance to R.string.insufficentBalance)

const val EXCEPTION_LEVEL = 200
const val ERROR_LEVEL = 100
const val NOTICE_LEVEL = 50

data class Alert(val msg: String, val details: String?, val level: Int, val date: Instant = Instant.now())

val alerts = arrayListOf<Alert>()

// Lookup strings in strings.xml
fun i18n(id: Int): String
{
    if (id == -1) return ""
    try
    {
        val s = appResources?.getString(id)
        if (s != null) return s
    } catch (e: Resources.NotFoundException)
    {
    }

    LogIt.severe("Missing strings.xml translation for " + id.toString() + "(0x" + id.toString(16));
    return "STR" + id.toString()
}

fun isKeyboardShown(root: View): Boolean
{
    val rect = Rect()
    root.getWindowVisibleDisplayFrame(rect)

    val heightDiff = root.height - rect.bottom
    val keyboardShown = heightDiff > root.dpToPx(200f)
    return keyboardShown
}

open class KeyboardToggleListener(private val root: View, private val onKeyboardToggleAction: (shown: Boolean) -> Unit) : ViewTreeObserver.OnGlobalLayoutListener
{
    private var shown = isKeyboardShown(root)
    override fun onGlobalLayout()
    {
        var rect = Rect()
        root.getWindowVisibleDisplayFrame(rect)

        val heightDiff = root.height - rect.bottom
        val keyboardShown = heightDiff > root.dpToPx(200f)
        if (shown != keyboardShown)
        {
            onKeyboardToggleAction.invoke(keyboardShown)
            shown = keyboardShown
        }
    }

    fun remove()
    {
        var iam = this
        root.viewTreeObserver?.run {
            removeOnGlobalLayoutListener(iam)
        }
    }
}

fun View.dpToPx(dp: Float): Int = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, resources.displayMetrics).roundToInt()

@SuppressLint("Registered")
open class CommonNavActivity : CommonActivity()
{
    private val onNavigationItemSelectedListener = BottomNavigationView.OnNavigationItemSelectedListener { item -> bottomNavSelectHandler(item, this) }
    open var navActivityId: Int = -1 //* Change this in derived classes to identify which navBar item this activity is

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
}

@SuppressLint("Registered")
open class CommonActivity : AppCompatActivity()
{
    var errorSync = object {}
    var origTitle = String()  //* The app's actual title (I will sometimes overwrite it with a temporary error message)
    var origTitleBackground: ColorDrawable? = null  //* The app's title background color (I will sometimes overwrite it with a temporary error message)
    var errorCount = 0 // Used to make sure one error's clear doesn't prematurely clear out a different problem

    @kotlinx.coroutines.ExperimentalCoroutinesApi
    protected val coGuiScope = MainScope()

    @kotlinx.coroutines.ExperimentalCoroutinesApi
    protected val coMiscCtxt: CoroutineContext = Executors.newFixedThreadPool(4).asCoroutineDispatcher()

    @kotlinx.coroutines.ExperimentalCoroutinesApi
    protected val coMiscScope: CoroutineScope = kotlinx.coroutines.CoroutineScope(coMiscCtxt)

    // for GUI automated testing
    var lastErrorId = 0
    var lastErrorString = ""

    override fun onCreate(savedInstanceState: Bundle?)
    {
        super.onCreate(savedInstanceState)

        origTitle = title.toString()
        val titlebar: View = findViewById(R.id.action_bar)
        origTitleBackground = ColorDrawable(ContextCompat.getColor(applicationContext, R.color.titleBackground))

        origTitleBackground?.let { titlebar.background = it }  // Set the title background color here, so we don't need to match the background defined in some resource file

        titlebar.setOnClickListener {
            LogIt.info("title button pressed")
            if (this is AlertActivity)
            {
                finish()  // If you click the header bar when looking at the error messages, then go back
            }
            else
            {
                var intent = Intent(this, AlertActivity::class.java)  // Otherwise start up the alert activity
                startActivity(intent)
            }
        }

    }

    override fun onStart()
    {
        currentActivity = this
        super.onStart()
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
        var stack: String? = null
        var details: String = ""
        if (buExc != null)
        {
            if (buExc.severity != ErrorSeverity.Expected)
            {
                stack = Log.getStackTraceString(buExc)
                LogIt.severe(buExc.shortMsg + ":" + buExc.message)
                LogIt.severe(stack)
            }
            displayString = buExc.shortMsg ?: buExc.message ?: getString(R.string.unknownError)
            if (buExc.shortMsg != null) details = "Details: " + buExc.message + "\n"
        }
        else
        {
            // Log all non-BU exceptions because we don't know if they are expected
            stack = Log.getStackTraceString(exc)
            LogIt.severe(exc.toString())
            LogIt.severe(stack)

            displayString = exc.message ?: getString(R.string.unknownError)
        }
        displayError(displayString, details + i18n(R.string.devDebugInfoHeader) + ":\n" + stack)
    }

    /** Display a specific error string rather than what the exception recommends, and offer the exception as details */
    fun displayException(resource: Int, exc: Exception)
    {
        var displayString: String
        val buExc = exc as? BUException
        var stack: String? = null
        var details: String = ""
        if (buExc != null)
        {
            if (buExc.severity != ErrorSeverity.Expected)
            {
                stack = Log.getStackTraceString(buExc)
                LogIt.severe(buExc.shortMsg + ":" + buExc.message)
                LogIt.severe(stack)
            }
            displayString = buExc.shortMsg ?: buExc.message ?: getString(R.string.unknownError)
            if (buExc.shortMsg != null) details = "Details: " + buExc.message + "\n"
        }
        else
        {
            // Log all non-BU exceptions because we don't know if they are expected
            stack = Log.getStackTraceString(exc)
            LogIt.severe(exc.toString())
            LogIt.severe(stack)

            displayString = exc.message ?: getString(R.string.unknownError)
        }
        displayError(resource, displayString + "\n" + details + i18n(R.string.devDebugInfoHeader) + ":\n" + stack)
    }


    /** Display an short error string on the title bar, and then clear it after a bit */
    fun displayError(resource: Int)
    {
        lastErrorId = resource
        displayError(getString(resource))
    }

    /** Display an short error string on the title bar, and then clear it after a bit */
    fun displayError(resource: Int, details: Int? = null, then: (() -> Unit)? = null)
    {
        lastErrorId = resource
        if (details == null)
            displayError(i18n(resource), null, then)
        else
            displayError(i18n(resource), i18n(details), then)
    }
    /** Display an short error string on the title bar, and then clear it after a bit */
    fun displayError(resource: Int, details: String, then: (() -> Unit)? = null)
    {
        lastErrorId = resource
        displayError(i18n(resource), details, then)
    }

    var menuHidden = 0
    override fun onCreateOptionsMenu(menu: Menu): Boolean
    {
        var ret = super.onCreateOptionsMenu(menu)

        for (i in 0 until menu.size())
            menu.getItem(i).setVisible(menuHidden == 0)

        return ret
    }

    /** Display an short error string on the title bar, and then clear it after a bit */
    fun displayError(err: String, details: String? = null, then: (() -> Unit)? = null)
    {
        laterUI {
            // This coroutine has to be limited to this thread because only the main thread can touch UI views
            // Display the error by changing the title and title bar color temporarily
            val titlebar: View = findViewById(R.id.action_bar)
            val myError = synchronized(errorSync)
            {
                setTitle(err)
                lastErrorString = err
                errorCount += 1
                menuHidden += 1
                alerts.add(Alert(err, details, ERROR_LEVEL))
                invalidateOptionsMenu()
                val errorColor = ContextCompat.getColor(applicationContext, R.color.error)
                titlebar.background = ColorDrawable(errorColor)
                errorCount
            }
            delay(ERROR_DISPLAY_TIME)
            synchronized(errorSync)
            {
                menuHidden -= 1
                invalidateOptionsMenu()
                if (errorCount == myError)
                {
                    setTitle(origTitle)
                    origTitleBackground?.let { titlebar.background = it }
                }
            }
            if (then != null) then()
        }

    }

    /** Display an short notification string on the title bar, and then clear it after a bit */
    fun displayNotice(resource: Int, details: Int? = null)
    {
        if (details == null) displayNotice(i18n(resource), null)
        else displayNotice(i18n(resource), i18n(details))
    }

    /** Display an short notification string on the title bar, and then clear it after a bit.
     * This is a common variant because the notification string is "canned" but the details may not be (for example QR contents) */
    fun displayNotice(resource: Int, details: String) = displayNotice(i18n(resource), details)

    /** Display an short notification string on the title bar, and then clear it after a bit */
    fun displayNotice(resource: Int, time: Long = NOTICE_DISPLAY_TIME, then: (() -> Unit)? = null) = displayNotice(i18n(resource), null, time, then)

    /** Display an short notification string on the title bar, and then clear it after a bit */
    fun displayNotice(msg: String, details: String? = null, time: Long = NOTICE_DISPLAY_TIME, then: (() -> Unit)? = null)
    {
        laterUI {
            // This coroutine has to be limited to this thread because only the main thread can touch UI views
            // Display the error by changing the title and title bar color temporarily
            var titlebar: View = findViewById(R.id.action_bar)
            val errorColor = ContextCompat.getColor(applicationContext, R.color.notice)
            val myError = synchronized(errorSync)
            {
                setTitle(msg);
                alerts.add(Alert(msg, details, NOTICE_LEVEL))
                titlebar.background = ColorDrawable(errorColor)
                errorCount += 1
                errorCount
            }
            delay(time)
            synchronized(errorSync)
            {
                if (myError == errorCount)
                {
                    setTitle(origTitle)
                    origTitleBackground?.let { titlebar.background = it }
                }
            }
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

    fun handleAnyIntent(intentUri: String): Boolean
    {
        val uri = intentUri.split(":")[0]

        if (uri == IDENTITY_URI_SCHEME)
        {
            LogIt.info("starting identity operation activity")
            var intent = Intent(this, IdentityOpActivity::class.java)
            intent.data = Uri.parse(intentUri)
            val nid = wallyApp?.notify(intent, "Identity Request", this)
            intent.extras?.putIntegerArrayList("notificationId",arrayListOf(nid))
            startActivityForResult(intent, IDENTITY_OP_RESULT)
        }
        else if (uri == TDPP_URI_SCHEME)
        {
            var intent = Intent(this, TricklePayActivity::class.java)
            intent.data = Uri.parse(intentUri)
            wallyApp?.notify(intent, "Trickle Pay Request", this)
            startActivityForResult(intent, TRICKLEPAY_RESULT)
        }
        else
        {
            return false
        }
        return true
    }

    /** Do whatever you pass but not within the user interface context, asynchronously */
    @kotlinx.coroutines.ExperimentalCoroutinesApi
    fun later(fn: suspend () -> Unit): Unit
    {
        coMiscScope.launch {
            try
            {
                fn()
            } catch (e: Exception) // Uncaught exceptions will end the app
            {
                handleThreadException(e)
            }
        }
    }

    /** Do whatever you pass within the user interface context, asynchronously */
    @kotlinx.coroutines.ExperimentalCoroutinesApi
    fun laterUI(fn: suspend () -> Unit): Unit
    {
        coGuiScope.launch {
            try
            {
                fn()
            } catch (e: Exception)  // Uncaught exceptions will end the app
            {
                handleThreadException(e)
            }
        }
    }

    fun onKeyboardToggle(v: EditText, onKeyboardToggleAction: (shown: Boolean) -> Unit): KeyboardToggleListener
    {
        val root = findViewById<View>(android.R.id.content)
        val l = KeyboardToggleListener(root, onKeyboardToggleAction)
        root?.viewTreeObserver?.run {
            addOnGlobalLayoutListener(l)
        }

        v.setOnKeyListener( { _, keyCode, event ->
        if(event.getAction() == KeyEvent.ACTION_DOWN)
        {
            if (keyCode == KeyEvent.KEYCODE_BACK)
            {
                onKeyboardToggleAction(false)
            }
            if (keyCode == KeyEvent.KEYCODE_ENTER)
            {
                onKeyboardToggleAction(false)
            }
        }
        false
    })
        return l
    }

    fun isKeyboardShown(): Boolean
    {
        val root = (findViewById(android.R.id.content) as View).getRootView()
        return isKeyboardShown(root)
    }

    fun showKeyboard()
    {
        if (!isKeyboardShown())
        {
            var view = currentFocus
            // If no view currently has focus, create a new one, just so we can grab a window token from it
            if (view == null)
            {
                view = View(this)
            }
            val imm: InputMethodManager = getSystemService(Activity.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(view,0)
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
                val clip = ClipData.newPlainText("text", addr)
                clipboard.setPrimaryClip(clip)

                // visual bling that indicates text copied
                v.text = i18n(R.string.copied)
                // Set it back to the address after awhile
                asyncUI {
                    delay(3000);
                    v.text = addr
                }
            }
            else throw UnavailableException(R.string.receiveAddressUnavailable)
        } catch (e: Exception)
        {
            displayException(e)
        }
    }
}
