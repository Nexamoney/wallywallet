// Copyright (c) 2019 Andrew Stone Consulting (qq9wwnuw4eukyh5g34ckg5vk4aaxnvr04vkspyv850)
// Distributed under the MIT software license, see the accompanying file COPYING or http://www.opensource.org/licenses/mit-license.php.
package info.bitcoinunlimited.www.wally

import org.nexa.libnexakotlin.*
import org.nexa.threads.millinow
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.content.res.Resources
import android.graphics.Rect
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.util.Log
import android.util.TypedValue
import android.view.*
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat.setWindowInsetsAnimationCallback
import androidx.core.view.WindowInsetsAnimationCompat
import androidx.core.view.WindowInsetsCompat
import info.bitcoinunlimited.www.wally.ui.ScreenId
import info.bitcoinunlimited.www.wally.ui.isSoftKeyboardShowing
import info.bitcoinunlimited.www.wally.ui.nav
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
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

open class KeyboardToggleListener(private val activity: Activity, private val onKeyboardToggleAction: (shown: Boolean) -> Unit) : ViewTreeObserver.OnGlobalLayoutListener
{
    var root: View = (activity.findViewById(android.R.id.content) as View).getRootView()
    var lastHeight = root.height
    var maxHeight = lastHeight
    var currentlyShown = false
    val rect = Rect()
    init {
        root.viewTreeObserver.addOnGlobalLayoutListener(this)
    }

    override fun onGlobalLayout()
    {
        root = (activity.findViewById(android.R.id.content) as View).getRootView()
        root.getWindowVisibleDisplayFrame(rect)
        val curHeight = (rect.bottom - rect.top)
        val screenHeight = (activity.findViewById(android.R.id.content) as View).getRootView().height
        if (screenHeight > maxHeight) maxHeight = screenHeight
        val maxDiff = maxHeight - curHeight
        if (maxDiff > maxHeight*0.15)
        {
            if (!currentlyShown)
            {
                currentlyShown = true
                onKeyboardToggleAction.invoke(true)
            }
        }
        else if (currentlyShown)
        {
            currentlyShown = false
            onKeyboardToggleAction.invoke(false)
        }
    }

    fun remove()
    {
        val iam = this
        root.viewTreeObserver?.run {
            removeOnGlobalLayoutListener(iam)
        }
    }
}

fun View.dpToPx(dp: Float): Int = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, resources.displayMetrics).roundToInt()

@SuppressLint("Registered")
open class CommonActivity : AppCompatActivity()
{
    var errorSync = object {}
    var origTitle = String()  //* The app's actual title (I will sometimes overwrite it with a temporary error message)
    var origTitleBackground: ColorDrawable? = null  //* The app's title background color (I will sometimes overwrite it with a temporary error message)
    var errorCount = 0 // Used to make sure one error's clear doesn't prematurely clear out a different problem
    var currentNumShowing = 0
    var currentAlertPersist = 0
    var newUiJob: Job? = null

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
        displayMetrics = resources.displayMetrics

        origTitle = title.toString()
        actionBarId = resources.getIdentifier("action_bar", "id", packageName)
        val titlebar: View = findViewById(actionBarId) // R.id.action_bar)
        origTitleBackground = ColorDrawable(ContextCompat.getColor(applicationContext, R.color.titleBackground))

        origTitleBackground?.let { titlebar.background = it }  // Set the title background color here, so we don't need to match the background defined in some resource file
    }

    fun onSoftKeyboard(shown: Boolean)
    {
        isSoftKeyboardShowing.value = shown
    }

    override fun setTitle(title: CharSequence?)
    {
        synchronized(errorSync)
        {
            origTitle = title.toString()
            if (currentNumShowing == 0) super.setTitle(title)
        }
    }

    var softKeyboardShown = false  // assume that it starts not shown (see AndroidManifest.xml android:windowSoftInputMode)
    var softkeyboardChangeHandler:KeyboardToggleListener? = null

    override fun onStart()
    {
        currentActivity = this

        val view: View = findViewById(android.R.id.content)
        val rootView = view.rootView
        var currentScreenJob: Job? = null

        softkeyboardChangeHandler = KeyboardToggleListener(this) { onSoftKeyboard(it) }

        setWindowInsetsAnimationCallback(rootView, object : WindowInsetsAnimationCompat.Callback(DISPATCH_MODE_CONTINUE_ON_SUBTREE) {
            override fun onProgress(insets: WindowInsetsCompat, runningAnimations: MutableList<WindowInsetsAnimationCompat>): WindowInsetsCompat
            {
                return insets
            }

            override fun onStart(animation: WindowInsetsAnimationCompat, bounds: WindowInsetsAnimationCompat.BoundsCompat): WindowInsetsAnimationCompat.BoundsCompat
            {
                if ((animation.typeMask and WindowInsetsCompat.Type.ime()) > 0)  // soft keyboard thing
                {
                    softKeyboardShown = !softKeyboardShown
                    onSoftKeyboard(softKeyboardShown)
                }
                return super.onStart(animation, bounds)
            }
            override fun onEnd(animation: WindowInsetsAnimationCompat)
            {
                super.onEnd(animation)
            }

        })

        fun useNewUiNavBar()
        {
            if (nav.currentScreen.value == ScreenId.Home)
            {
                supportActionBar?.setDisplayHomeAsUpEnabled(false)
                supportActionBar?.setHomeAsUpIndicator(R.drawable.wally_logo_small)
            }
            currentScreenJob = nav.currentScreen.onEach {
                if (nav.currentScreen.value == ScreenId.Home)
                {
                    supportActionBar?.setDisplayHomeAsUpEnabled(true)
                    supportActionBar?.setHomeAsUpIndicator(R.drawable.wally_logo_small)
                }
                else
                {
                    supportActionBar?.setDisplayHomeAsUpEnabled(true)
                    supportActionBar?.setHomeAsUpIndicator(0)
                }
            }.launchIn(this.coGuiScope)
        }

        useNewUiNavBar()

        // Cancel the new UI job when Android app comes back into the foreground and onResume runs again
        newUiJob?.cancel()

        super.onStart()
    }

    override fun onDestroy()
    {
        // Clear the top-level currentActivity reference so the destroyed Activity becomes
        // eligible for garbage collection. The identity check avoids clobbering a replacement
        // Activity whose onStart() may have already registered itself before this runs.
        if (currentActivity === this) currentActivity = null
        coMiscCtxt.cancel()
        coGuiScope.cancel()
        coMiscScope.cancel()
        super.onDestroy()
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

    var lastPauseMillis = millinow()
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
        if (millinow() - lastPauseMillis > LOCK_IF_PAUSED_FOR_MILLIS)
        {
            wallyApp?.lockAccounts()
        }
    }

    override fun onPause()
    {
        super.onPause()
        isRunning = false
        lastPauseMillis = millinow()
    }

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
        val ret = super.onCreateOptionsMenu(menu)

        for (i in 0 until menu.size())
        {
            val mi = menu.getItem(i)
            if (mi.title != "unlock") menu.getItem(i).setVisible(menuHidden == 0)
            else if (!keepShowingLock) menu.getItem(i).setVisible(menuHidden == 0)
        }
        if (menuHidden == 0) keepShowingLock = false  // Reset this every time we show the full menu

        return ret
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
                    invalidateOptionsMenu()
                    // If we are finishing what is currently being shown,
                    // or nothing is being shown then set it all back
                    if (currentNumShowing == 0 || errorCount == errNo || errNo == null)
                    {
                        super.setTitle(origTitle)
                        currentAlertPersist = 0
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
            if (alert.msg == "")  // If the msg is empty it means to wipe out the shown alert
            {
                // But only wipe persistent ones if the "wipe" alert is also marked as persistent
                if (alert.persistAcrossScreens >= currentAlertPersist) finishShowingNotice()
            }
            else
            {
                val myError = synchronized(errorSync)
                {
                    super.setTitle(alert.msg)
                    lastErrorString = alert.msg
                    errorCount += 1
                    menuHidden += 1
                    currentNumShowing += 1
                    currentAlertPersist = alert.persistAcrossScreens
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
            super.setTitle(err)
            val errorColor = ContextCompat.getColor(applicationContext, R.color.error)
            val myError = synchronized(errorSync)
            {
                lastErrorString = err
                errorCount += 1
                menuHidden += 1
                currentNumShowing += 1
                currentAlertPersist = 1  // default alert behavior is to persist across screens (if automatic screen change)
                alerts.add(Alert(err, details, AlertLevel.ERROR, trace))
                invalidateOptionsMenu()
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
            val titlebar: View = findViewById(actionBarId)
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
                currentAlertPersist = 1
                errorCount
            }
            delay(time)
            finishShowingNotice(myError)
            if (then != null) then()
        }
    }

    /** Do whatever you pass within the user interface context, asynchronously */
    fun laterUI(fn: suspend () -> Unit): Unit
    {
        coGuiScope.launch(exceptionHandler) {
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

    fun isKeyboardShown(): Boolean
    {
        val root = (findViewById(android.R.id.content) as View).getRootView()
        return isKeyboardShown(root)
    }
}
