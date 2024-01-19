// Copyright (c) 2019 Andrew Stone Consulting (qq9wwnuw4eukyh5g34ckg5vk4aaxnvr04vkspyv850)
// Distributed under the MIT software license, see the accompanying file COPYING or http://www.opensource.org/licenses/mit-license.php.

package info.bitcoinunlimited.www.wally

import android.app.*
import android.app.PendingIntent.CanceledException
import android.content.Intent
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.service.notification.StatusBarNotification
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import io.ktor.client.*
import io.ktor.client.engine.android.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.nexa.libnexakotlin.*
import java.io.DataOutputStream
import java.io.FileNotFoundException
import java.io.IOException
import java.net.ConnectException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.security.spec.InvalidKeySpecException
import java.util.*
import java.util.concurrent.Executors
import kotlin.coroutines.CoroutineContext

import com.eygraber.uri.*

const val NORMAL_NOTIFICATION_CHANNEL_ID = "n"
const val PRIORITY_NOTIFICATION_CHANNEL_ID = "p"

private val LogIt = GetLog("BU.wally.app")

val SupportedBlockchains =
    mapOf(
      "NEXA" to ChainSelector.NEXA,
      "BCH (Bitcoin Cash)" to ChainSelector.BCH,
      "TNEX (Testnet Nexa)" to ChainSelector.NEXATESTNET,
      "RNEX (Regtest Nexa)" to ChainSelector.NEXAREGTEST,
      "TBCH (Bitcoin Cash)" to ChainSelector.BCHTESTNET,
      "RBCH (Bitcoin Cash)" to ChainSelector.BCHREGTEST
    )

val ChainSelectorToSupportedBlockchains = SupportedBlockchains.entries.associate { (k, v) -> v to k }

// What is the default wallet and blockchain to use for most functions (like identity)
val PRIMARY_CRYPTO = if (REG_TEST_ONLY) ChainSelector.NEXAREGTEST else ChainSelector.NEXA

var wallyAndroidApp: WallyApp? = null

var brokenMode: Boolean = false

// in app init, we change the lbbc integers to our own resource ids.  So this translation is likely unnecessary
val i18nLbc = mapOf(
  RinsufficentBalance to S.insufficentBalance,
  RbadWalletImplementation to S.badWalletImplementation,
  RdataMissing to S.PaymentDataMissing,
  RwalletAndAddressIncompatible to S.chainIncompatibleWithAddress,
  RnotSupported to S.notSupported,
  Rexpired to S.expired,
  RsendMoreThanBalance to S.sendMoreThanBalance,
  RbadAddress to S.badAddress,
  RblankAddress to S.blankAddress,
  RblockNotForthcoming to S.blockNotForthcoming,
  RheadersNotForthcoming to S.headersNotForthcoming,
  RbadTransaction to S.badTransaction,
  RfeeExceedsFlatMax to S.feeExceedsFlatMax,
  RexcessiveFee to S.excessiveFee,
  Rbip70NoAmount to S.badAmount,
  RdeductedFeeLargerThanSendAmount to S.deductedFeeLargerThanSendAmount,
  RwalletDisconnectedFromBlockchain to S.walletDisconnectedFromBlockchain,
  RsendDust to S.sendDustError,
  RnoNodes to S.NoNodes,
  RwalletAddressMissing to S.badAddress,
  RunknownCryptoCurrency to S.unknownCryptoCurrency,
  RsendMoreTokensThanBalance to S.insufficentTokenBalance
)

actual fun platformNotification(message:String, title: String?, onclickUrl:String?)
{
    // TODO
}

class ActivityLifecycleHandler(private val app: WallyApp) : Application.ActivityLifecycleCallbacks
{
    override fun onActivityPaused(act: Activity)
    {
    }

    override fun onActivityStarted(act: Activity)
    {
        /*
        //if (app.currentActivity is CommonActivity)
        try
        {
            app.currentActivity = act as CommonNavActivity
        } catch (e: Throwable)  // Some other activity (QR scanner)
        {
        }

         */
    }

    override fun onActivityDestroyed(act: Activity)
    {
    }

    override fun onActivitySaveInstanceState(act: Activity, b: Bundle)
    {

    }

    override fun onActivityStopped(act: Activity)
    {
    }

    override fun onActivityCreated(act: Activity, b: Bundle?)
    {
    }

    override fun onActivityResumed(act: Activity)
    {
        /*
        //if (app.currentActivity is CommonActivity)
        try
        {
            app.currentActivity = act as CommonNavActivity
        } catch (e: Throwable)  // Some other activity (QR scanner)
        {
        }

         */
    }
}

class WallyApp : Application.ActivityLifecycleCallbacks, Application()
{
    init
    {
        RinsufficentBalance = S.insufficentBalance
        RbadWalletImplementation = S.badWalletImplementation
        RdataMissing = S.PaymentDataMissing
        RwalletAndAddressIncompatible = S.chainIncompatibleWithAddress
        RnotSupported = S.notSupported
        Rexpired = S.expired
        RsendMoreThanBalance = S.sendMoreThanBalance
        RbadAddress = S.badAddress
        RblankAddress = S.blankAddress
        RblockNotForthcoming = S.blockNotForthcoming
        RheadersNotForthcoming = S.headersNotForthcoming
        RbadTransaction = S.badTransaction
        RfeeExceedsFlatMax = S.feeExceedsFlatMax
        RexcessiveFee = S.excessiveFee
        Rbip70NoAmount = S.badAmount
        RdeductedFeeLargerThanSendAmount = S.deductedFeeLargerThanSendAmount
        RwalletDisconnectedFromBlockchain = S.walletDisconnectedFromBlockchain
        RsendDust = S.sendDustError
        RnoNodes = S.NoNodes
        RbadCryptoCode = S.badCryptoCode
        RneedNonexistentAuthority = S.needNonexistentAuthority
        RwalletAddressMissing = S.badAddress
        RunknownCryptoCurrency = S.unknownCryptoCurrency
        RsendMoreTokensThanBalance = S.sendMoreTokensThanBalance
    }

    var commonApp = CommonApp()
    val focusedAccount
      get() = commonApp.focusedAccount

    // Set to true if this is the first time this app has ever been run
    var firstRun = false
    // Current notification ID
    var notifId = 0

    protected val coMiscCtxt: CoroutineContext = Executors.newFixedThreadPool(6).asCoroutineDispatcher()
    protected val coMiscScope: CoroutineScope = kotlinx.coroutines.CoroutineScope(coMiscCtxt)

    companion object
    {
        // Used to load the 'native-lib' library on application startup.
        init
        {
            //System.loadLibrary("native-lib")
            System.loadLibrary("nexalight")
            appI18n = { libErr: Int -> i18n(i18nLbc[libErr] ?: libErr) }
        }
    }

    val init = org.nexa.libnexakotlin.initializeLibNexa()

    // Use currentActivity global
    //var currentActivity: CommonNavActivity? = null

    // Track notifications
    val notifs: MutableList<Triple<Int, PendingIntent, Intent>> = mutableListOf()

    /** Activity stacks don't quite work.  If task A uses an implicit intent launches a child wally activity, then finish() returns to A
     * if wally wasn't previously running.  But if wally was currently running, it returns to wally's Main activity.
     * Since the implicit activity wasn't launched for result, we can't return an indicator that wally main should finish().
     * Whenever wally resumes, if finishParent > 0, it will immediately finish. */
    var finishParent = 0


    /** Return what account a particular GUI element is bound to or null if its not bound */
    fun accountFromGui(view: View): Account?
    {
        var act:Account? = null
        try
        {
            act = commonApp.accountLock.lock {
                for (a in commonApp.accounts.values)
                {
                    if ((a.tickerGUI.reactor is TextViewReactor<String>) && (a.tickerGUI.reactor as TextViewReactor<String>).gui == view) return@lock a
                    if ((a.balanceGUI.reactor is TextViewReactor<String>) && (a.balanceGUI.reactor as TextViewReactor<String>).gui == view) return@lock a
                    if ((a.unconfirmedBalanceGUI.reactor is TextViewReactor<String>) && (a.unconfirmedBalanceGUI.reactor as TextViewReactor<String>).gui == view) return@lock a
                    if ((a.infoGUI.reactor is TextViewReactor<String>) && (a.infoGUI.reactor as TextViewReactor<String>).gui == view) return@lock a
                }
                null
            }
        } catch (e: Exception)
        {
            LogIt.warning("Exception in accountFromGui: " + e.toString())
            handleThreadException(e)
        }
        return act
    }


    fun handlePostLogin(loginReqParam: String, jsonBody: String)
    {
        var loginReq = loginReqParam
        var forwarded = 0

        postloop@ while (forwarded < 3)
        {
            LogIt.info("sending registration reply: " + loginReq)
            try
            {
                //val body = """[1,2,3]"""  // URLEncoder.encode("""[1,2,3]""","UTF-8")
                val req: HttpURLConnection = URL(loginReq).openConnection() as HttpURLConnection
                req.requestMethod = "POST"
                req.setRequestProperty("Content-Type", "application/json")
                req.setRequestProperty("Accept", "*/*")
                req.setRequestProperty("Content-Length", jsonBody.length.toString())
                req.setConnectTimeout(HTTP_REQ_TIMEOUT_MS)
                req.doOutput = true
                req.useCaches = false
                val os = DataOutputStream(req.outputStream)
                //os.write(jsonBody.toByteArray())
                os.writeBytes(jsonBody.toString())
                os.flush()
                os.close()
                val resp = req.inputStream.bufferedReader().readText()
                LogIt.info("reg response code:" + req.responseCode.toString() + " response: " + resp)
                if ((req.responseCode >= 200) and (req.responseCode < 300))
                {
                    displayNotice(resp)
                    return
                }
                else if ((req.responseCode == 301) or (req.responseCode == 302))  // Handle URL forwarding
                {
                    loginReq = req.getHeaderField("Location")
                    forwarded += 1
                    continue@postloop
                }
                else
                {
                    displayNotice(resp)
                    return
                }
            } catch (e: java.net.SocketTimeoutException)
            {
                LogIt.info("SOCKET TIMEOUT:  If development, check phone's network.  Ensure you can route from phone to target!  " + e.toString())
                displayError(R.string.connectionException)
                return
            } catch (e: IOException)
            {
                LogIt.info("registration IOException: " + e.toString())
                displayError(R.string.connectionAborted)
                return
            } catch (e: FileNotFoundException)
            {
                LogIt.info("registration FileNotFoundException: " + e.toString())
                displayError(R.string.badLink)
                return
            } catch (e: java.net.ConnectException)
            {
                displayError(R.string.connectionException)
                return
            } catch (e: Throwable)
            {
                displayError(R.string.unknownError)
                return
            }
            break@postloop  // Only way to actually loop is to get a http 301 or 302
        }
    }

    /** Execute a login request to a 3rd party web site via the nexid protocl.  This is done within the app context so that the login activity can return before the async login process
     * is completed.
     */
    fun handleLogin(loginReqParam: String)
    {
        var loginReq = loginReqParam
        var forwarded = 0
        getloop@ while (forwarded < 3)
        {
            LogIt.info(sourceLoc() +": login reply: " + loginReq)
            try
            {
                val req: HttpURLConnection = URL(loginReq).openConnection() as HttpURLConnection
                req.setConnectTimeout(HTTP_REQ_TIMEOUT_MS)
                val resp = req.inputStream.bufferedReader().readText()
                LogIt.info("login response code:" + req.responseCode.toString() + " response: " + resp)
                if ((req.responseCode >= 200) and (req.responseCode < 250))
                {
                    displayNotice(resp)
                    return
                }
                else if ((req.responseCode == 301) or (req.responseCode == 302))  // Handle URL forwarding (often switching from http to https)
                {
                    loginReq = req.getHeaderField("Location")
                    forwarded += 1
                    continue@getloop
                }
                else
                {
                    displayNotice(resp)
                    return
                }
            } catch (e: FileNotFoundException)
            {
                displayError(R.string.badLink, loginReq)
            } catch (e: IOException)
            {
                displayError(R.string.connectionAborted, loginReq)
            } catch (e: java.net.ConnectException)
            {
                displayError(R.string.connectionException)
            }

            break@getloop  // only way to actually loop is to hit a 301 or 302
        }
    }


    private fun createNotificationChannel()
    {
        // Create the NotificationChannel, but only on API 26+ because
        // the NotificationChannel class is new and not in the support library
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
        {
            val name = "Wally Wallet" //getString(R.string.channel_name)
            val descriptionText = "Wally Wallet Notifications" // getString(R.string.channel_description)
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(NORMAL_NOTIFICATION_CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            val channelp = NotificationChannel(PRIORITY_NOTIFICATION_CHANNEL_ID, name, NotificationManager.IMPORTANCE_HIGH).apply {
                description = descriptionText
            }
            // Register the channel with the system
            val notificationManager: NotificationManager =
              applicationContext.getSystemService(NOTIFICATION_SERVICE) as NotificationManager

            notificationManager.createNotificationChannel(channel)
            notificationManager.createNotificationChannel(channelp)
        }
    }

    // Called when the application is starting, before any other application objects have been created.
    // Overriding this method is totally optional!
    override fun onCreate()
    {
        LogIt.info("------------  WALLY APP CREATED  ---------------")
        super.onCreate()
        appResources = getResources()
        displayMetrics = getResources().getDisplayMetrics()
        val locales = resources.configuration.locales
        for (idx in 0 until locales.size())
        {
            val loc = locales[idx]
            LogIt.info("Locale: ${loc.language} ${loc.country}")
            if (setLocale(loc.language, loc.country)) break
        }
        wallyAndroidApp = this
        wallyApp = commonApp
        commonApp.onCreate()

        // Add the Wally Wallet server to our list of Electrum/Rostrum connection points
        nexaElectrum.add(0, IpPort("rostrum.wallywallet.org", DEFAULT_NEXA_TCP_ELECTRUM_PORT))

        registerActivityLifecycleCallbacks(ActivityLifecycleHandler(this))  // track the current activity
        createNotificationChannel()

        /*
        var myClipboard = getSystemService(AppCompatActivity.CLIPBOARD_SERVICE) as ClipboardManager
        myClipboard.addPrimaryClipChangedListener(object:  ClipboardManager.OnPrimaryClipChangedListener {
            override fun onPrimaryClipChanged()
            {
                // This is not essential, so don't crash if something is wrong
                // In particular, some users get android.os.DeadSystemRuntimeException on android 13, implying an Android bug
                try
                {
                    val tmp = myClipboard.getPrimaryClip()
                    if (tmp != null) currentClip = tmp
                }
                catch (e: Exception)
                {
                    logThreadException(e, "primary clipboard object changed")
                }
            }

        })
        */
        updateClipboardCache()
    }

    fun updateClipboardCache()  // modern android doesn't let you track the clipboard like this for security reasons
    {
        /*
        var myClipboard = getSystemService(AppCompatActivity.CLIPBOARD_SERVICE) as ClipboardManager
        val tmp = myClipboard.getPrimaryClip()
        if (tmp != null) currentClip = tmp

        //  Because background apps monitor the clipboard and steal data, you can no longer access the clipboard unless you are foreground.
        //  However, (and this is probably a bug) if you are becoming foreground, like an activity just completed and returned to you
        //  in onActivityResult, then your activity hasn't been foregrounded yet :-(.  So I need to delay
        //  Wait for this app to regain the input focus
        //  https://developer.android.com/reference/android/content/ClipboardManager#hasPrimaryClip()
        //  If the application is not the default IME or the does not have input focus getPrimaryClip() will return false.
        laterUI {
            delay(250)
            var myClipboard = getSystemService(AppCompatActivity.CLIPBOARD_SERVICE) as ClipboardManager
            val tmp = myClipboard.getPrimaryClip()
            if (tmp != null) currentClip = tmp
        }

         */
    }

    // Called by the system when the device configuration changes while your component is running.
    // Overriding this method is totally optional!
    override fun onConfigurationChanged(newConfig: Configuration)
    {
        super.onConfigurationChanged(newConfig)
    }

    // This is called when the overall system is running low on memory,
    // and would like actively running processes to tighten their belts.
    // Overriding this method is totally optional!
    override fun onLowMemory()
    {
        super.onLowMemory()
    }


    /** TODO: Move to commonApp
     * Automatically handle this intent if its something that can be done without user intervention.
    Returns true if it was handled, false if user-intervention needed.
    * */
    var autoPayNotificationId = -1
    fun autoHandle(intentUri: String): Boolean
    {
        return commonApp.handleTdpp(Uri.parse(intentUri))
    }


    /** send a casual popup message that's not a notification */
    fun toast(Rstring: Int) = toast(i18n(Rstring))
    fun toast(s: String)
    {
        looper.handler.post {
            val t = Toast.makeText(this, s, Toast.LENGTH_SHORT)
            val y = displayMetrics.heightPixels
            t.setGravity(android.view.Gravity.TOP, 0, y / 15)
            t.show()
        }
    }

    class LooperThread : Thread()
    {
        lateinit var handler: Handler
        override fun run()
        {
            Looper.prepare()
            handler = object : Handler(Looper.myLooper()!!)
            {
            }
            Looper.loop()
        }

        init{
            start()
        }
    }

    val looper = LooperThread()


    /** Remove a notification that was installed using the notify() function */
    fun denotify(intent: Intent)
    {
        val nid = intent.getIntExtra("wallyNotificationId", -1)
        if (nid != -1) denotify(nid)
    }

    /* Remove a notification */
    fun denotify(id: Int)
    {
        notifs.removeIf { it.first == id }  // clear out our local record of this intent
        with(NotificationManagerCompat.from(this))
        {
            cancel(id)
        }
    }

    fun activeNotifications(): Array<StatusBarNotification>
    {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        val n = nm.activeNotifications
        n.sortBy({ it.postTime })
        n.filter { it.packageName == this.packageName }
        return n
    }

    /** Either automatically trigger an intent to be handled, or return the intent the activity should handle, or return null if no intents pending */
    fun getNotificationIntent(): Intent?
    {
        //val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        //val notifs = nm.activeNotifications
        val sysnotifs = activeNotifications()
        var idx = 0
        while(idx < sysnotifs.size)
        {
            val sbn = sysnotifs[idx]
            val id = sbn.id
            val n = sbn.notification
            LogIt.info(sourceLoc() + "onResume handle notification intent:" + n.contentIntent.toString())
            try
            {
                n.contentIntent.send()
                return null
            }
            catch(e:CanceledException)
            {
                idx++
            }
            finally
            {
                denotify(id)
            }
        }

        // If the user turned notifications off for this app, there won't be any but we still need to process incoming requests
        if (notifs.isNotEmpty())
        {
            val n = notifs[0]
            notifs.removeAt(0)
            return(n.third)
        }

        return null
    }

    /** Create a notification of a pending intent */
    fun notify(intent: Intent, content: String, activity: AppCompatActivity, actionRequired: Boolean = true, overwrite: Int = -1): Int
    {
        return notify(intent, "Wally Wallet", content, activity, actionRequired, overwrite)
    }

    fun notifyPopup(intent: Intent, title: String, content: String, activity: AppCompatActivity, actionRequired: Boolean = true, overwrite: Int = -1): Int
    {
        return notify(intent, title, content, activity, actionRequired, overwrite, NotificationCompat.PRIORITY_HIGH)
    }
    /** Create a notification of a pending intent */
    fun notify(intent: Intent, title: String, content: String, activity: AppCompatActivity, actionRequired: Boolean = true, overwrite: Int = -1, priority: Int = NotificationCompat.PRIORITY_DEFAULT): Int
    {
        // Save the notification id into the Intent so we can remove it when needed
        val nid = if (overwrite == -1) notifId++ else overwrite  // reminder: this is a post-increment!
        intent.putExtra("wallyNotificationId", nid)

        val pendingIntent = PendingIntent.getActivity(activity, nid, intent, PendingIntent.FLAG_IMMUTABLE)
        var builder = NotificationCompat.Builder(activity, if (priority == NotificationCompat.PRIORITY_DEFAULT) NORMAL_NOTIFICATION_CHANNEL_ID else PRIORITY_NOTIFICATION_CHANNEL_ID)
          //.setSmallIcon(R.drawable.ic_notifications_black_24dp)
          .setSmallIcon(R.mipmap.ic_wally)
          .setContentTitle(title)
          .setContentText(content)
          .setPriority(priority)
          .setContentIntent(pendingIntent)
          .setAutoCancel(true)

        notifs.add(Triple(nid,pendingIntent, intent))
        with(NotificationManagerCompat.from(this))
        {
            try
            {
                notify(nid, builder.build())
            }
            catch(e:SecurityException)
            {
                // We don't have permission to send notifications
            }
            return nid
        }
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?)
    {
    }

    override fun onActivityStarted(activity: Activity)
    {
    }

    override fun onActivityResumed(activity: Activity)
    {
    }
    override fun onActivityPostResumed(activity: Activity)
    {
        updateClipboardCache()
    }

    override fun onActivityPaused(activity: Activity)
    {
    }

    override fun onActivityStopped(activity: Activity)
    {
    }

    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle)
    {
    }

    override fun onActivityDestroyed(activity: Activity)
    {
    }

}