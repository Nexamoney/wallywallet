// Copyright (c) 2019 Andrew Stone Consulting (qq9wwnuw4eukyh5g34ckg5vk4aaxnvr04vkspyv850)
// Distributed under the MIT software license, see the accompanying file COPYING or http://www.opensource.org/licenses/mit-license.php.

@file:OptIn(ExperimentalUnsignedTypes::class)

package info.bitcoinunlimited.www.wally

import android.app.*
import android.app.PendingIntent.CanceledException
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.os.*
import android.service.notification.StatusBarNotification
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
import info.bitcoinunlimited.www.wally.ui.views.loadingAnimation
import org.nexa.threads.Mutex
import org.nexa.threads.setThreadName
import java.lang.Exception

const val DEBUG_VM = true
var brokenMode: Boolean = false

// Includes both screen off AND app minimized
// This has to be long enough to not be tiresome if you are browsing some other app to interact with wally
const val LOCK_IF_PAUSED_FOR_MILLIS = 10*60*1000
// We want it to auto-lock if you minimize your screen and forget to lock, but it cannot be instant or its very irritating if you accidentally hit the blank button
const val LOCK_IF_SCREEN_OFF_FOR_MILLIS = 20*1000

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
    val androidContext = (appContext() as android.content.Context)
    var id = androidContext.resources.getIdentifier(resFile, "raw", androidContext.packageName)
    val strs = androidContext.resources.openRawResource(id).readBytes()
    if (strs.size == 0) return null
    else return strs.decodeUtf8()
}

fun loadTextResource(@RawRes resId: Int):String?
{
    val androidContext = (appContext() as android.content.Context)
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
        if (backgroundOnly == false) return Result.success()
        val skip = backgroundLock.lock {
            if (backgroundCount > 0) true
            else
            {
                initializeLibNexa()
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

class ScreenStateReceiver : BroadcastReceiver()
{
    var lastScreenOffTime = millinow()
    override fun onReceive(context: Context, intent: Intent)
    {
        when (intent.action)
        {
            Intent.ACTION_SCREEN_OFF -> {
                lastScreenOffTime = millinow()
            }
            Intent.ACTION_SCREEN_ON -> {
                if (millinow() - lastScreenOffTime > LOCK_IF_SCREEN_OFF_FOR_MILLIS)
                {
                    lastScreenOffTime = millinow()
                    wallyApp?.lockAccounts()
                }
            }
        }
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

    companion object
    {
        init
        {
            //System.loadLibrary("native-lib")
            System.loadLibrary("nexalight")
            org.nexa.libnexakotlin.initializeLibNexa()
        }
    }

    fun runningTest(): Boolean
    {
        return try
        {
            Class.forName("androidx.test.platform.app.InstrumentationRegistry")
            true
        }
        catch (e: ClassNotFoundException)
        {
            false
        }
        catch (e: Exception)
        {
            false
        }
    }


    var commonApp = CommonApp(runningTest())
    init
    {
        if (runningTest())
        {
            LogIt.warning(sourceLoc()+": NOTE, App launched in test mode!")
        }
        wallyApp = commonApp
    }

    // Current notification ID
    var notifId = 0

    protected val coMiscCtxt: CoroutineContext = Executors.newFixedThreadPool(6).asCoroutineDispatcher()
    protected val coMiscScope: CoroutineScope = kotlinx.coroutines.CoroutineScope(coMiscCtxt)


    // Track notifications
    val notifs: MutableList<Triple<Int, PendingIntent, Intent>> = mutableListOf()

    /** Activity stacks don't quite work.  If task A uses an implicit intent launches a child wally activity, then finish() returns to A
     * if wally wasn't previously running.  But if wally was currently running, it returns to wally's Main activity.
     * Since the implicit activity wasn't launched for result, we can't return an indicator that wally main should finish().
     * Whenever wally resumes, if finishParent > 0, it will immediately finish. */
    var finishParent = 0
    /** used to determine whether this app is backgrounded or not */
    var activityCount = 0
    /** when was this app left */
    var appDefocusedAtTime = millinow()
    val screenState = ScreenStateReceiver()

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
        LogIt.info(sourceLoc() + ": ------------  WALLY APP CREATED  ---------------")
        val files: Array<String> = fileList()
        LogIt.info(sourceLoc() +" App Files ${files.joinToString(", ")}")

        val dbs: Array<String> = databaseList()
        LogIt.info(sourceLoc() +" Databases ${dbs.joinToString(", ")}")

        if (DEBUG_VM)
        {
            StrictMode.setVmPolicy(StrictMode.VmPolicy.Builder().detectLeakedClosableObjects().detectActivityLeaks()
              .penaltyLog().build()  // .penaltyDeath()
            )
        }

        super.onCreate()

        // Add the Wally Wallet server to our list of Electrum/Rostrum connection points
        nexaElectrum.add(0, IpPort("rostrum.wallywallet.org", DEFAULT_NEXA_TCP_ELECTRUM_PORT))

        createNotificationChannel()
        registerReceiver(screenState, IntentFilter(Intent.ACTION_SCREEN_ON))
        registerReceiver(screenState, IntentFilter(Intent.ACTION_SCREEN_OFF))

        appResources = getResources()
        displayMetrics = getResources().getDisplayMetrics()
        val locales = resources.configuration.locales
        var localeSet = false
        for (idx in 0 until locales.size())
        {
            val loc = locales[idx]
            LogIt.info("Locale: ${loc.language} ${loc.country}")
            if (setLocale(loc.language, loc.country))
            {
                localeSet = true
                break
            }
        }
        // If I do not have any translations for their locales then default to english
        if (!localeSet) setLocale("en","US")
        wallyAndroidApp = this
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

    /** Returns true is this app is visible on the screen
     * (not background, not locked screen)
     * */
    fun visible(): Boolean
    {
        return (activityCount > 0)
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
        return notify(intent, i18n(S.app_long_name), content, activity, actionRequired, overwrite)
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
        val builder = NotificationCompat.Builder(activity, if (priority == NotificationCompat.PRIORITY_DEFAULT) NORMAL_NOTIFICATION_CHANNEL_ID else PRIORITY_NOTIFICATION_CHANNEL_ID)
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
        activityCount++
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
        activityCount--
    }

    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle)
    {
    }

    override fun onActivityDestroyed(activity: Activity)
    {
    }

}