// Copyright (c) 2019 Andrew Stone Consulting (qq9wwnuw4eukyh5g34ckg5vk4aaxnvr04vkspyv850)
// Distributed under the MIT software license, see the accompanying file COPYING or http://www.opensource.org/licenses/mit-license.php.
package info.bitcoinunlimited.www.wally
import org.nexa.libnexakotlin.*
import android.annotation.SuppressLint
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.graphics.Rect
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.util.TypedValue
import android.view.*
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.core.view.ViewCompat.setWindowInsetsAnimationCallback
import androidx.core.view.WindowInsetsAnimationCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.navigation.NavigationBarView
import kotlinx.coroutines.*
import java.time.Instant
import java.util.*
import java.util.concurrent.Executors

import kotlin.coroutines.CoroutineContext
import kotlin.math.roundToInt

private val LogIt = GetLog("BU.wally.commonActivity")

var currentActivity: CommonActivity? = null
public var appResources: Resources? = null


enum class SoftKey
{
    ALL,
    THOUSAND,
    MILLION,
    CLEAR;

    infix fun or(other: SoftKey) = SoftKeys.of(this, other)
}

typealias SoftKeys = EnumSet<SoftKey>

infix fun SoftKeys.allOf(other: SoftKeys) = this.containsAll(other)
infix fun SoftKeys.or(other: SoftKey) = SoftKeys.of(other, *this.toTypedArray())


fun isKeyboardShown(root: View): Boolean
{
    val rect = Rect()
    root.getWindowVisibleDisplayFrame(rect)

    val heightDiff = root.height - rect.bottom
    val keyboardShown = heightDiff > root.dpToPx(200f)
    LogIt.info("keyboard shown: " + keyboardShown.toString())
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
    open var navActivityId: Int = -1 //* Change this in derived classes to identify which navBar item this activity is
    var navViewMenuId: Int? = null //* change this in derived classes to pick a different bottom nav menu

    override fun onSoftKeyboard(shown: Boolean)
    {
        //val navView: BottomNavigationView = findViewById(R.id.nav_view)
        //navView.visibility = if (shown) View.GONE else View.VISIBLE
    }

    override fun onCreate(savedInstanceState: Bundle?)
    {
        super.onCreate(savedInstanceState)

    }

    override fun onStart()
    {
        super.onStart()
    }

    override fun onResume()
    {
        super.onResume()
    }

}

@SuppressLint("Registered")
open class CommonActivity : AppCompatActivity()
{
    var errorSync = object {}
    var origTitle = String()  //* The app's actual title (I will sometimes overwrite it with a temporary error message)
    var origTitleBackground: ColorDrawable? = null  //* The app's title background color (I will sometimes overwrite it with a temporary error message)
    var errorCount = 0 // Used to make sure one error's clear doesn't prematurely clear out a different problem
    var currentNumShowing = 0

    protected val coGuiScope = MainScope()
    protected val coMiscCtxt: CoroutineContext = Executors.newFixedThreadPool(4).asCoroutineDispatcher()
    protected val coMiscScope: CoroutineScope = kotlinx.coroutines.CoroutineScope(coMiscCtxt)

    // for GUI automated testing
    var lastErrorId = 0
    var lastErrorString = ""

    var isRunning = false

    var actionBarId:Int = 0

    override fun onCreate(savedInstanceState: Bundle?)
    {
        setTheme(R.style.WallyActionBarStyle)
        super.onCreate(savedInstanceState)

        origTitle = title.toString()
        actionBarId = resources.getIdentifier("action_bar", "id", packageName)
        val titlebar: View = findViewById(actionBarId) // R.id.action_bar)
        origTitleBackground = ColorDrawable(ContextCompat.getColor(applicationContext, R.color.titleBackground))

        origTitleBackground?.let { titlebar.background = it }  // Set the title background color here, so we don't need to match the background defined in some resource file
        titlebar.setOnClickListener {
            onTitleBarTouched()
        }
    }

    open fun splash(shown: Boolean)
    {

    }

    open fun onSoftKeyboard(shown: Boolean)
    {
    }

    override fun setTitle(title: CharSequence?)
    {
        synchronized(errorSync)
        {
            origTitle = title.toString()
            if (currentNumShowing == 0) super.setTitle(title)
        }
    }
    open fun onTitleBarTouched()
    {
    }

    override fun onStart()
    {
        currentActivity = this

        val view: View = findViewById(android.R.id.content)
        val rootView = view.rootView

        setWindowInsetsAnimationCallback(rootView, object : WindowInsetsAnimationCompat.Callback(DISPATCH_MODE_CONTINUE_ON_SUBTREE) {
            override fun onProgress(insets: WindowInsetsCompat, runningAnimations: MutableList<WindowInsetsAnimationCompat>): WindowInsetsCompat
            {
                return insets
            }

            override fun onStart(animation: WindowInsetsAnimationCompat, bounds: WindowInsetsAnimationCompat.BoundsCompat): WindowInsetsAnimationCompat.BoundsCompat
            {

                if ((animation.typeMask and WindowInsetsCompat.Type.ime()) > 0)  // soft keyboard thing
                {
                    if (isKeyboardShown(rootView))
                        onSoftKeyboard(true)
                }
                return super.onStart(animation, bounds)
            }
            override fun onEnd(animation: WindowInsetsAnimationCompat)
            {
                super.onEnd(animation)
                //if (!isKeyboardShown(rootView))
                onSoftKeyboard(isKeyboardShown(rootView))
                // val showingKeyboard = rootView.rootWindowInsets.isVisible(WindowInsets.Type.ime())
            }

        })

        getSupportActionBar()?.setDisplayHomeAsUpEnabled(true)
        getSupportActionBar()?.setDisplayShowHomeEnabled(true)

        super.onStart()
    }

    override fun onDestroy()
    {
        coMiscCtxt.cancel()
        coGuiScope.cancel()
        coMiscScope.cancel()
        super.onDestroy()
    }

    fun displayPendingTopbarMessages()
    {
    }

    override fun onNewIntent(intent: Intent)
    {
        super.onNewIntent(intent)
        // An intent with no dataString just launches the program; it does not provide any paste info
        // To handle QR scanning and other input methods, we ALWAYS use data to navigate never the intent itself.
        // so nothing to do if there is no dataString
        val s = intent.dataString
        LogIt.info("Handling intent data: $s  Intent ${intent.toUri(0)}")
        s?.let { wallyApp?.handlePaste(it) }
    }

    override fun onResume()
    {
        super.onResume()

        // This code pops out of this activity if the child requested it.  This is needed when an external intent directly
        // spawns a child activity of wally's main activity, but upon completion of that child we want to drop back to the
        // spawner not to wally's main screen
        wallyAndroidApp?.let {
            val fp = it.finishParent
            if (fp > 0)
            {
                it.finishParent = fp -1
            }
        }

        isRunning = true
    }

    override fun onPause()
    {
        super.onPause()
        isRunning = false
    }

    /*
    // see https://stackoverflow.com/questions/13135545/android-activity-is-using-old-intent-if-launching-app-from-recent-task
    fun launchedFromRecent(): Boolean
    {
        val flags: Int = intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY
        return flags == Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY
    }

     */

    override fun onOptionsItemSelected(item: MenuItem): Boolean
    {
        when (item.getItemId())
        {
            android.R.id.home ->
            {
                onBackPressed()
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }


    private fun prepareDisplayExceptionString(exc: Exception): Pair<String,String>
    {
        var details = ""
        var displayString: String
        var stack: String? = null
        val buExc = exc as? LibNexaException
        if (buExc != null)
        {
            if (buExc.severity != ErrorSeverity.Expected)
            {
                stack = Log.getStackTraceString(buExc)
                LogIt.severe(buExc.shortMsg + ":" + buExc.message)
                LogIt.severe(stack)
            }
            if (buExc.errCode != -1)  // errCode replaces (is redundant to) the shortMsg
            {
                displayString = i18n(buExc.errCode)
                details = "Details: " + buExc.message + "\n"
            }
            else
            {
                displayString = buExc.shortMsg ?: buExc.message ?: getString(R.string.unknownError)
                if (buExc.shortMsg != null) details = "Details: " + buExc.message + "\n"
            }
        }
        else
        {
            // Log all non-BU exceptions because we don't know if they are expected
            stack = Log.getStackTraceString(exc)
            LogIt.severe(exc.toString())
            LogIt.severe(stack)

            displayString = exc.message ?: getString(R.string.unknownError)
        }
        return Pair(displayString, details)
    }

    fun displayException(exc: Exception)
    {
        val (displayString, details) = prepareDisplayExceptionString(exc)
        displayError(displayString, details)
    }

    /** Display a specific error string rather than what the exception recommends, and offer the exception as details */
    fun displayException(resource: Int, exc: Exception, expected: Boolean = false)
    {
        val (displayString, details) = prepareDisplayExceptionString(exc)
        displayError(resource, displayString + "\n" + details)
    }


    /** Display an short error string on the title bar, and then clear it after a bit */
    fun displayError(resource: Int)
    {
        lastErrorId = resource
        if (resource == R.string.NoAccounts || resource == R.string.InvalidPIN || resource == R.string.accountLocked) keepShowingLock = true
        displayError(getString(resource))
    }

    /** Display an short error string on the title bar, and then clear it after a bit */
    fun displayError(resource: Int, details: Int? = null, then: (() -> Unit)? = null)
    {
        lastErrorId = resource
        if (resource == R.string.NoAccounts || resource == R.string.InvalidPIN || resource == R.string.accountLocked) keepShowingLock = true
        if (details == null)
            displayError(i18n(resource), null, then)
        else
            displayError(i18n(resource), i18n(details), then)
    }
    /** Display an short error string on the title bar, and then clear it after a bit */
    fun displayError(resource: Int, details: String, then: (() -> Unit)? = null)
    {
        lastErrorId = resource
        if (resource == R.string.NoAccounts || resource == R.string.InvalidPIN || resource == R.string.accountLocked) keepShowingLock = true
        displayError(i18n(resource), details, then)
    }

    var keepShowingLock = false
    var menuHidden = 0
    override fun onCreateOptionsMenu(menu: Menu): Boolean
    {
        var ret = super.onCreateOptionsMenu(menu)

        for (i in 0 until menu.size())
        {
            val mi = menu.getItem(i)
            if (mi.title != "unlock") menu.getItem(i).setVisible(menuHidden == 0)
            else if (!keepShowingLock) menu.getItem(i).setVisible(menuHidden == 0)
        }
        if (menuHidden == 0) keepShowingLock = false  // Reset this every time we show the full menu

        return ret
    }

    fun initializeHelpOption(menu: Menu)
    {
        val item4 = menu.findItem(R.id.help)
        if (item4 != null)
        {
            val temp = Intent(Intent.ACTION_VIEW)
            temp.setData(Uri.parse("http://www.wallywallet.org/help"))
            item4.intent = temp
        }
    }

    /** If no parameter or null is passed, stop showing whatever is being shown */
    @Synchronized
    fun finishShowingNotice(errNo: Int? = null)
    {
        laterUI {
            synchronized(errorSync)
            {
                if (currentNumShowing > 0)
                {
                    currentNumShowing -= 1
                    val titlebar: View = findViewById(actionBarId)
                    if (menuHidden > 0) menuHidden -= 1
                    if (errNo == 0)
                    {
                        menuHidden = 0
                    } // Abort all errors shown (returned from other activity)
                    if (errorCount == errNo || errNo == null)
                    {
                        invalidateOptionsMenu()
                        super.setTitle(origTitle)
                        origTitleBackground?.let { titlebar.background = it }
                    }
                }
            }
        }
    }

    /** Display an short error string on the title bar, and then clear it after a bit */
    fun displayAlert(alert: Alert)
    {
        // val trace = stackTraceWithout(mutableSetOf("displayError\$default","displayError","displayNotice"))
        laterUI {
            // This coroutine has to be limited to this thread because only the main thread can touch UI views
            // Display the error by changing the title and title bar color temporarily
            val titlebar: View = findViewById(actionBarId)
            if (alert.msg == "") finishShowingNotice()
            else
            {
                val myError = synchronized(errorSync)
                {
                    super.setTitle(alert.msg)
                    lastErrorString = alert.msg
                    errorCount += 1
                    menuHidden += 1
                    currentNumShowing += 1
                    invalidateOptionsMenu()
                    titlebar.background = ColorDrawable(alert.level.color().toArgb())
                    errorCount
                }
                delay(alert.longevity ?: alert.level.longevity())
                finishShowingNotice(myError)
            }
        }

    }

    /** Display an short error string on the title bar, and then clear it after a bit */
    fun displayError(err: String, details: String? = null, then: (() -> Unit)? = null)
    {
        val trace = stackTraceWithout(mutableSetOf("displayError\$default","displayError","displayNotice"))
        laterUI {
            // This coroutine has to be limited to this thread because only the main thread can touch UI views
            // Display the error by changing the title and title bar color temporarily
            val titlebar: View = findViewById(actionBarId)
            val myError = synchronized(errorSync)
            {
                super.setTitle(err)
                lastErrorString = err
                errorCount += 1
                menuHidden += 1
                currentNumShowing += 1
                alerts.add(Alert(err, details, AlertLevel.ERROR, trace))
                invalidateOptionsMenu()
                val errorColor = ContextCompat.getColor(applicationContext, R.color.error)
                titlebar.background = ColorDrawable(errorColor)
                errorCount
            }
            delay(ERROR_DISPLAY_TIME)
            finishShowingNotice(myError)
            if (then != null) then()
        }

    }

    /** Display an short notification string on the title bar, and then clear it after a bit */
    fun displayNotice(resource: Int, details: Int? = null, time: Long = NOTICE_DISPLAY_TIME, then: (() -> Unit)? = null)
    {
        if (details == null) displayNotice(i18n(resource), null, time, then)
        else displayNotice(i18n(resource), i18n(details), time, then)
    }

    /** Display an short notification string on the title bar, and then clear it after a bit.
     * This is a common variant because the notification string is "canned" but the details may not be (for example QR contents) */
    fun displayNotice(resource: Int, details: String, time: Long = NOTICE_DISPLAY_TIME, then: (() -> Unit)? = null) = displayNotice(i18n(resource), details, time, then)

    /** Display an short notification string on the title bar, and then clear it after a bit */
    fun displayNotice(resource: Int, time: Long = NOTICE_DISPLAY_TIME, then: (() -> Unit)? = null) = displayNotice(i18n(resource), null, time, then)

    /** Display an short notification string on the title bar, and then clear it after a bit */
    fun displayNotice(msg: String, details: String? = null, time: Long = NOTICE_DISPLAY_TIME, then: (() -> Unit)? = null)
    {
        val trace = stackTraceWithout(mutableSetOf("displayError","displayNotice"))
        laterUI {
            // This coroutine has to be limited to this thread because only the main thread can touch UI views
            // Display the error by changing the title and title bar color temporarily
            var titlebar: View = findViewById(actionBarId)
            val errorColor = ContextCompat.getColor(applicationContext, R.color.notice)
            val myError = synchronized(errorSync)
            {
                super.setTitle(msg)

                alerts.add(Alert(msg, details, AlertLevel.NOTICE, trace))
                menuHidden += 1
                invalidateOptionsMenu()
                actionBar?.setDisplayHomeAsUpEnabled(false)
                titlebar.background = ColorDrawable(errorColor)
                errorCount += 1
                currentNumShowing += 1
                errorCount
            }
            delay(time)
            finishShowingNotice(myError)
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

    fun amIbackground(): Boolean
    {
        return isRunning==false
    }

    /** Do whatever you pass but not within the user interface context, asynchronously */
    fun later(fn: suspend () -> Unit): Unit
    {
        coMiscScope.launch {
            try
            {
                fn()
            } catch (e: Exception) // Uncaught exceptions will end the app
            {
                LogIt.info(sourceLoc() + ": General exception handler (should be caught earlier!)")
                handleThreadException(e)
            }
        }
    }

    /** Do whatever you pass within the user interface context, asynchronously */
    fun laterUI(fn: suspend () -> Unit): Unit
    {
        coGuiScope.launch {
            try
            {
                fn()
            }
            catch (e: CancellationException)  // coroutine got cancelled -- I'm ok with that
            {
            }
            catch (e: Exception)  // Uncaught exceptions will end the app
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

    fun copyTextToClipboard(v: TextView, label: String = "")
    {
        val addr = v.text
        try
        {
            var clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.clearPrimaryClip()

            if (addr != null)
            {
                val clip = ClipData.newPlainText(label, addr)
                clipboard.setPrimaryClip(clip)
                //clipboard.setText(addr)

                // visual bling that indicates text copied
                v.text = i18n(R.string.copiedToClipboard)
                // Set it back to the address after awhile
                laterUI {
                    delay(3000)
                    v.text = addr
                }
            }
            else throw UnavailableException(R.string.receiveAddressUnavailable)
        } catch (e: Exception)
        {
            displayException(e)
        }
    }


    /** If the derived activity supports a soft keyboard layout, then it will override this function to show them */
    open fun setVisibleSoftKeys(which: SoftKeys)
    {

    }
}
