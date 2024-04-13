// Copyright (c) 2019 Andrew Stone Consulting (qq9wwnuw4eukyh5g34ckg5vk4aaxnvr04vkspyv850)
// Distributed under the MIT software license, see the accompanying file COPYING or http://www.opensource.org/licenses/mit-license.php.

package info.bitcoinunlimited.www.wally

import android.app.*
import android.app.PendingIntent.CanceledException
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.*
import android.service.notification.StatusBarNotification
import android.view.View
import android.widget.Toast
import androidx.annotation.RawRes
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.*
import androidx.work.PeriodicWorkRequest.Companion.MIN_PERIODIC_INTERVAL_MILLIS
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.asCoroutineDispatcher
import org.nexa.libnexakotlin.*
import java.util.concurrent.Executors
import kotlin.coroutines.CoroutineContext
import com.eygraber.uri.*
import info.bitcoinunlimited.www.wally.ui.views.loadingAnimation
import org.nexa.threads.Mutex
import org.nexa.threads.setThreadName


const val DEBUG_VM = true
var brokenMode: Boolean = false

const val NORMAL_NOTIFICATION_CHANNEL_ID = "n"
const val PRIORITY_NOTIFICATION_CHANNEL_ID = "p"
const val BACKGROUND_PERIOD_MSEC = MIN_PERIODIC_INTERVAL_MILLIS

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

actual fun platformNotification(message:String, title: String?, onclickUrl:String?, severity: AlertLevel)
{
    when (severity)
    {
        AlertLevel.CLEAR ->
        {
            // TODO remove the notification
        }
        AlertLevel.SUCCESS ->
        {
            if (title != null) displaySuccess(title, message)
            else displaySuccess(message)
        }
        AlertLevel.NOTICE ->
        {
            if (title != null) displayNotice(title, message)
            else displayNotice(message)
        }
        AlertLevel.WARN ->
        {
            if (title != null) displayWarning(title, message)
            else displayWarning(message)
        }
        AlertLevel.ERROR, AlertLevel.EXCEPTION ->
        {
            if (title != null) displayError(title, message)
            else displayError(message)
        }
    }

    // TODO actually use platform level notifications
}

fun loadTextResource(resFile: String):String?
{
    val androidContext = (appContext() as android.content.Context)!!
    var id = androidContext.resources.getIdentifier(resFile, "raw", androidContext.packageName)
    val strs = androidContext.resources.openRawResource(id).readBytes()
    if (strs.size == 0) return null
    else return strs.decodeUtf8()
}

fun loadTextResource(@RawRes resId: Int):String?
{
    val androidContext = (appContext() as android.content.Context)!!
    val strs = androidContext.resources.openRawResource(resId).readBytes()
    if (strs.size == 0) return null
    else return strs.decodeUtf8()
}

/** Load all graphics resources (images, animations, etc) */
fun initializeGraphicsResources()
{
    launch {
        // loadingAnimation = loadTextResource("loading_animation.json")
        loadingAnimation = loadTextResource(R.raw.loading_animation)
    }
}


val backgroundLock = Mutex("background")
var backgroundCount = 0

class BackgroundSync(appContext: Context, workerParams: WorkerParameters): Worker(appContext, workerParams)
{
    var cancelled = false
    override fun doWork(): Result
    {
        val skip = backgroundLock.lock {
            if (backgroundCount > 0) true
            else
            {
                backgroundCount++
                false
            }
        }
        if (skip) return Result.retry()
        LogIt.info("Starting background work")
        backgroundSync {}
        backgroundLock.lock { backgroundCount-- }
        if (cancelled) return Result.retry()
        return Result.success()
    }

    override fun onStopped()
    {
        LogIt.info("Cancelled background work")
        cancelled = true
        // program will be unloaded, we don't actually want to stop syncing until then, so not going to call
        // cancelBackgroundSync()
        super.onStopped()
    }
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
        if (DEBUG_VM)
        {
            StrictMode.setVmPolicy(StrictMode.VmPolicy.Builder().detectLeakedClosableObjects().detectActivityLeaks()
              .penaltyLog().build()  // .penaltyDeath()
            )
        }

        super.onCreate()

        // Add the Wally Wallet server to our list of Electrum/Rostrum connection points
        nexaElectrum.add(0, IpPort("rostrum.wallywallet.org", DEFAULT_NEXA_TCP_ELECTRUM_PORT))

        registerActivityLifecycleCallbacks(ActivityLifecycleHandler(this))  // track the current activity
        createNotificationChannel()

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
        return HandleTdpp(Uri.parse(intentUri))
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
            setThreadName("WallyAppEventLoop")
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