package bitcoinunlimited.wally.guiTestImplementation

import Nexa.NexaRpc.NexaRpcFactory
import android.app.Activity
import android.content.*
import android.content.Context.CLIPBOARD_SERVICE
import android.content.res.Configuration
import android.net.Uri
import android.provider.Settings.Global.getString
import android.view.View
import android.widget.Switch
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.*
import androidx.test.espresso.Espresso.onData
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.*
import androidx.test.espresso.assertion.ViewAssertions
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import bitcoinunlimited.libbitcoincash.*
import info.bitcoinunlimited.www.wally.*
//import kotlinx.android.synthetic.main.activity_identity.*
// import kotlinx.android.synthetic.main.activity_main.*
//import kotlinx.android.synthetic.main.trickle_pay_reg.*
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import org.hamcrest.CoreMatchers.instanceOf
import org.hamcrest.Matchers.*
import org.junit.Test
import org.junit.runner.RunWith
import java.lang.Thread.sleep
import java.math.BigDecimal
import java.util.*
import java.util.logging.Logger
import info.bitcoinunlimited.www.wally.R.id as GuiId
import info.bitcoinunlimited.www.wally.R  // so we can compare strings with what is on the screen
import org.hamcrest.Matcher
import java.net.URLEncoder


val LogIt = Logger.getLogger("GuiTest")

class TestTimeoutException(what: String): Exception(what)

//val REGTEST_RPC_PORT=18332
//val REGTEST_P2P_PORT=18444

val REGTEST_P2P_PORT=7327
val REGTEST_RPC_PORT=7328


fun ViewInteraction.isGone() = getViewAssertion(ViewMatchers.Visibility.GONE)
fun ViewInteraction.isVisible() = getViewAssertion(ViewMatchers.Visibility.VISIBLE)
fun ViewInteraction.isInvisible() = getViewAssertion(ViewMatchers.Visibility.INVISIBLE)

private fun getViewAssertion(visibility: ViewMatchers.Visibility): ViewAssertion? {
    return ViewAssertions.matches(ViewMatchers.withEffectiveVisibility(visibility))
}

/*  helper test function not currently used */
/*
fun generateAndLogSomeTricklePayRequests(application: WallyApp)
{
    val act = application.primaryAccount
    val wallet = act.wallet
    val identityDest: PayDestination = wallet.destinationFor(Bip44Wallet.COMMON_IDENTITY_SEED)

    var uri = ConstructTricklePayRequest("testapp", "testtopic", "reg", identityDest, "BCH", 1000000UL, null, null, 100000000UL)
    LogIt.info(uri.toString())
    if (VerifyTdppSignature(uri) == true)
    {
        LogIt.info("Sig Verified")
    }
    else
    {
        VerifyTdppSignature(uri)
    }
    var uri2 = Uri.parse(uri.toString())
    if (VerifyTdppSignature(uri2) == true)
    {
        LogIt.info("Sig Verified")
    }
    else
    {
        VerifyTdppSignature(uri)
    }
}
 */

@RunWith(AndroidJUnit4::class)
class GuiTest
{
    fun setLocale(locale: Locale, app: WallyApp)
    {
        Locale.setDefault(locale)
        val res = app.getBaseContext().getResources()
        val config: Configuration = res.getConfiguration()
        config.locale = locale
        res.updateConfiguration(config, res.getDisplayMetrics())
    }

    fun clipboardText(): String
    {
        val clipboard = wallyApp!!.getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        val clip: ClipData = clipboard.getPrimaryClip() ?: return ""
        if (clip.itemCount == 0) return ""
        val item = clip.getItemAt(0)
        val text = item.text.toString().trim()
        return text
    }

    fun clickSpinnerItem(entity: Int, item: String)
    {
        onView(withId(entity)).perform(click())
        onData(allOf(instanceOf(String::class.java), equalTo(item)))
           // .inAdapterView(withId(entity))  // redundant
            .perform(click())
    }

    fun clickSpinnerItem(listRes: Int, position: Int)
    {
        onData(anything())
            .inAdapterView(withId(listRes))
            .atPosition(position).perform(click())
    }

    fun waitUntilLayoutHas(entity: Int, timeout: Int=10000)
    {
        var count = timeout
        while(true) try
        {
            onView(withId(entity)) //.perform(clearText())
            break
        }
        catch (e: NoMatchingViewException)
        {
            Thread.sleep(100)
            count-=100
            if (count < 0 ) throw TestTimeoutException("Timout waiting for view to contain ${entity}")
        }
    }

    fun waitFor(timeout: Int = 10000, checkIt: ()->Boolean)
    {
        var count = timeout
        while(!checkIt())
        {
            Thread.sleep(100)
            count-=100
            if (count < 0 ) throw TestTimeoutException("Timout waiting for predicate")
        }
    }

    fun<T:Activity> waitForActivity(timeout: Int = 10000, activityScenario: ActivityScenario<T>, checkIt: (act: T)->Boolean)
    {

        waitFor(timeout)
        {
            var result = false
            activityScenario.onActivity { result = checkIt(it) }
            result
        }
    }

    fun retryUntilLayoutCan(timeout: Int=10000, doit:()->Unit)
    {
        var count = timeout
        while(true) try
        {
            doit()
            break
        }
        catch (e: NoMatchingViewException)
        {
            Thread.sleep(100)
            count-=100
            if (count < 0 ) throw TestTimeoutException("Timout waiting for layout")
        }
        catch (e: PerformException)
        {
            Thread.sleep(100)
            count-=100
            if (count < 0 ) throw TestTimeoutException("Timout waiting for Expresso perform")
        }
    }

    fun createNewAccount(name: String, chainSelector: ChainSelector)
    {
        // Switch to a different activity
        while(true) try {
            onView(withId(GuiId.GuiNewAccount)).perform(click())
            break
        }
        catch (e: NoMatchingViewException)
        {
            Thread.sleep(1000)
        }

        //onView(withId(GuiId.GuiCreateAccountButton)).perform(click())
        //val act = currentActivity!!
        // TODO test invalid account names.  But right now this is going to work because its autofilled in
        // check(act.lastErrorId == R.string.invalidAccountName)
        //onView(withId(GuiId.GuiAccountNameEntry)).perform(typeText(name), pressImeActionButton(), pressBack())
        clickSpinnerItem(GuiId.GuiBlockchainSelector, ChainSelectorToSupportedBlockchains[chainSelector]!!)

        onView(withId(GuiId.GuiCreateAccountButton)).perform(click())
    }

    @Test fun testRpc()
    {
        LogIt.info("This test requires a full node running on regtest")

        // Set up RPC connection
        val rpcConnection = "http://" + SimulationHostIP + ":" + REGTEST_RPC_PORT

        val nexaRpc = NexaRpcFactory.create(rpcConnection)

        // Try the hard way (manual parsing returned parameter)
        val retje = nexaRpc.callje("listunspent")
        val result = (retje as JsonObject)["result"] as JsonArray
        check(result.size > 0)  // This could fail if you haven't created at least 101 blocks

        // Try the easy way
        val ret = nexaRpc.listunspent()
        check(ret.size > 0)
    }

    @Test fun testBottomNavigation()
    {
        val activityScenario: ActivityScenario<MainActivity> = ActivityScenario.launch(MainActivity::class.java)
        activityScenario.moveToState(Lifecycle.State.RESUMED)
        var app: WallyApp? = null
        activityScenario.onActivity { app = (it.application as WallyApp) }
        assert(app != null)

        val ctxt = PlatformContext(app!!.applicationContext)
        walletDb = OpenKvpDB(ctxt, dbPrefix + "TESTbip44walletdb")

        onView(withId(R.id.navigation_home)).perform(click())

        /*
        onView(allOf(withId(R.id.navigation_identity), isDisplayed())).let {
            it.perform( object : ViewAction
            {
                override fun getDescription(): String
                {
                    return ""
                }
                override fun getConstraints(): Matcher<View>
                {
                    return hasFocus()
                }
                override fun perform(uiController: UiController?, view: View?)
                {
                    if (view != null)
                    {
                        click()
                    }
                }
            })
        }
        */

        onView(withId(R.id.navigation_trickle_pay)).perform(click())
        //onView(withId(R.id.navigation_assets)).perform(click())
        onView(withId(R.id.navigation_shopping)).perform(click())
        onView(withId(R.id.navigation_home)).perform(click())
        // onView(withId(R.id.navigation_identity)).perform(click())
        pressBack()
        onView(withId(R.id.navigation_trickle_pay)).perform(click())
        pressBack()
        //onView(withId(R.id.navigation_assets)).perform(click())
        //pressBack()
        onView(withId(R.id.navigation_shopping)).perform(click())
        pressBack()

        onView(withId(R.id.navigation_trickle_pay)).perform(click())
        pressBack()
        onView(withId(R.id.navigation_trickle_pay)).perform(click())
        pressBack()
        onView(withId(R.id.navigation_trickle_pay)).perform(click())
        pressBack()
        onView(withId(R.id.navigation_trickle_pay)).perform(click())
        pressBack()
        onView(withId(R.id.navigation_home)).perform(click())
        sleep(1000)
        activityScenario.close()
        LogIt.info("Completed!")
    }

    @Test fun testTricklePayRegistration()
    {
        val activityScenarioM: ActivityScenario<MainActivity> = ActivityScenario.launch(MainActivity::class.java)
        activityScenarioM.moveToState(Lifecycle.State.RESUMED)
        val app = wallyApp!!
        val ctxt = PlatformContext(app.applicationContext)
        walletDb = OpenKvpDB(ctxt, dbPrefix + "TESTbip44walletdb")
        val wdb = walletDb!!

        val tw = Bip44Wallet(wdb,"testframework", ChainSelector.NEXA, "quantum curve elephant soccer faculty cheese merge medal vault damage sniff purpose")
        val dest = tw.destinationFor("")
        // TODO Uri.encode vs URLEncoder
        var uriStr:String = "tdpp://www.yoursite.com/reg?addr=" + URLEncoder.encode(dest.address!!.toString(), "UTF-8") + "&descday=" + URLEncoder.encode("desc2 space test", "UTF-8") + "&descper=desc1&descweek=week&maxday=10000&maxper=1000&maxweek=100000&topic=thisisatest&uoa=NEX"
        val tosign = uriStr.toByteArray()
        println("signing text: ${uriStr}")
        println("signing hex: ${tosign.toHex()}")
        val sig = Wallet.signMessage(uriStr.toByteArray(), dest.secret!!.getSecret())
        val uriSig64=Uri.encode(Codec.encode64(sig))
        uriStr = uriStr + "&sig=$uriSig64"

        println("trickle pay example: " + uriStr)

        val tent = Intent(Intent.ACTION_VIEW, Uri.parse(uriStr))
        val activityScenario: ActivityScenario<TricklePayActivity> = ActivityScenario.launch(tent)
        activityScenario.moveToState(Lifecycle.State.RESUMED)

        try
        {
            app.primaryAccount
        }
        catch (e:PrimaryWalletInvalidException)
        {
            // Wallet has no accounts
            check(alerts[alerts.size - 1].msg == i18n(R.string.NoAccounts))

            // add an account and try again
            app.newAccount("NEX1", ACCOUNT_FLAG_NONE, "", ChainSelector.NEXA)
            val act = app.primaryAccount
            activityScenario.recreate()
        }


        // push all the buttons
        activityScenario.onActivity {
            val fm = it.getSupportFragmentManager()
            val fragG = fm.findFragmentById(R.id.GuiTricklePayReg)
            val frag = fragG as TricklePayRegFragment
            val s: String = frag.ui.GuiTricklePayEntity.text.toString()
            check(s == "www.yoursite.com")
            check(frag.ui.GuiTricklePayTopic.text.toString() == "thisisatest")
            check(frag.ui.GuiAutospendLimitDescription0.text.toString() == "desc1")
            check(frag.ui.GuiAutospendLimitDescription1.text.toString() == "desc2 space test")
            check(frag.ui.GuiAutospendLimitDescription2.text.toString() == "week")
        }

        onView(withId(GuiId.TpAssetInfoRequestHandlingButton)).perform(click())
        onView(withId(GuiId.TpAssetInfoRequestHandlingButton)).perform(click())
        onView(withId(GuiId.TpAssetInfoRequestHandlingButton)).perform(click())

        onView(withId(GuiId.GuiEnableAutopay)).perform(click())
        onView(withId(GuiId.GuiEnableAutopay)).perform(click())

        onView(withId(GuiId.GuiAutospendLimitEntry0)).perform(clearText(),typeText("123"), pressImeActionButton())
        onView(withId(GuiId.GuiAutospendLimitEntry1)).perform(clearText(),typeText("234"), pressImeActionButton())
        onView(withId(GuiId.GuiAutospendLimitEntry2)).perform(clearText(),typeText("345"), pressImeActionButton())
        onView(withId(GuiId.GuiAutospendLimitEntry3)).perform(clearText(),typeText("456"), pressImeActionButton())

        onView(withId(GuiId.GuiTpRegisterRequestAccept)).perform(click())  // this will close the activity

        // Get back into it
        val asc: ActivityScenario<TricklePayActivity> = ActivityScenario.launch(TricklePayActivity::class.java)
        asc.moveToState(Lifecycle.State.RESUMED)

        // Check that new registration was accepted
        asc.onActivity {
            check(it.tpDomains.size > 0)
        }

        // OK get rid of what we created
        onView(withId(GuiId.GuiTpDeleteAll)).perform(click())
        asc.close()
        activityScenario.close()
        activityScenarioM.close()
        println("worked!")
    }

    @Test fun testIdentityActivity()
    {
        val activityScenario: ActivityScenario<IdentityActivity> = ActivityScenario.launch(IdentityActivity::class.java)

        // There will be no accounts, so check proper error
        activityScenario.onActivity {
            check(it.ui.commonIdentityAddress.text == i18n(R.string.NoAccounts))
        }

        val app = {
            var a: WallyApp? = null
            activityScenario.onActivity { a = (it.application as WallyApp) }
            assert(a != null)
            a!!
        }()

        val ctxt = PlatformContext(app.applicationContext)
        walletDb = OpenKvpDB(ctxt, dbPrefix + "TESTbip44walletdb")
        val wdb = walletDb!!

        val act = try
        {
            app.primaryAccount
        }
        catch (e:PrimaryWalletInvalidException)
        {
            app.newAccount("NEX1", ACCOUNT_FLAG_NONE, "", ChainSelector.NEXA)
        }


        // Setup is complete so now show the activity
        activityScenario.recreate()
        activityScenario.moveToState(Lifecycle.State.RESUMED)

        var addr:String = ""
        activityScenario.onActivity {
            val s:String = it.ui.commonIdentityAddress.text.toString()
            check(s.startsWith("nexa:"))
            addr = s
        }

        check(addr.startsWith("nexa:"))

        onView(withId(GuiId.commonIdentityAddress)).perform(click())
        activityScenario.onActivity {
            val s:String = it.ui.commonIdentityAddress.text.toString()
            check(s == i18n(R.string.copiedToClipboard))
        }
        val cp = clipboardText()
        check(cp == addr)
    }

    @Test fun testSettingsDevMode()
    {
        // Start up a particular Activity.  In this case "Settings"
        val activityScenario: ActivityScenario<Settings> = ActivityScenario.launch(Settings::class.java)
        activityScenario.moveToState(Lifecycle.State.RESUMED)

        // Grab the instance of our app
        var app: WallyApp? = null
        activityScenario.onActivity { app = (it.application as WallyApp) }
        assert(app != null)

        // Get access to what is happening in the back end
        // Everything needs to be inside onActivity, to schedule it within the activity context
        var preferenceDB: SharedPreferences? = null
        activityScenario.onActivity {
            preferenceDB = it.getSharedPreferences(it.getString(R.string.preferenceFileName), Context.MODE_PRIVATE)
        }

        // This test should work regardless of the current setting, so figure that out first
        var origDevMode = devMode

        // check that the global matches the switch state
        if (origDevMode)
            onView(withId(GuiId.GuiDeveloperInfoSwitch)).check { v, exc -> check((v as Switch).isChecked) }
        else
            onView(withId(GuiId.GuiDeveloperInfoSwitch)).check { v, exc -> check(!(v as Switch).isChecked) }

        // You look in the activity's layout .xml file to find the name of the UI object you want to manipulate
        onView(withId(GuiId.GuiDeveloperInfoSwitch)).perform(click())

        // Now you can check by looking though the "back end" that it worked.
        // I'm going to check both the global variable and the preferences database in this case because
        // the dev mode switch should modify both of them.
        activityScenario.onActivity {
            check(devMode != origDevMode )
            val ret = preferenceDB!!.getBoolean(DEV_MODE_PREF, false)
            check(ret == devMode)
        }

        // Set it back to whatever it was
        onView(withId(GuiId.GuiDeveloperInfoSwitch)).perform(click())
        // check that the global matches the switch state
        if (origDevMode)
            onView(withId(GuiId.GuiDeveloperInfoSwitch)).check { v, exc -> check((v as Switch).isChecked) }
        else
            onView(withId(GuiId.GuiDeveloperInfoSwitch)).check { v, exc -> check(!(v as Switch).isChecked) }
    }

    @Test fun testHomeActivity()
    {
        val cs = ChainSelector.NEXAREGTEST
        val activityScenario: ActivityScenario<MainActivity> = ActivityScenario.launch(MainActivity::class.java)
        activityScenario.moveToState(Lifecycle.State.RESUMED)
        var app: WallyApp? = null
        activityScenario.onActivity { app = (it.application as WallyApp) }
        assert(app != null)

        val ctxt = PlatformContext(app!!.applicationContext)
        walletDb = OpenKvpDB(ctxt, dbPrefix + "TESTbip44walletdb")
        val wdb = walletDb!!

        // Clean up any prior run
        deleteWallet(wdb, "rNEX1", cs)
        deleteWallet(wdb, "rNEX2", cs)

        // Clean up old headers  ONLY NEEDED IF YOU RECREATE REGTEST NETWORK but reuse an emulator
        //deleteBlockHeaders("mRbch1", dbPrefix, appContext!!)
        //deleteBlockHeaders("mRbch2", dbPrefix, appContext!!)

        // supply this wallet with coins
        val rpcConnection = "http://" + SimulationHostIP + ":" + REGTEST_RPC_PORT
        LogIt.info("Connecting to: " + rpcConnection)
        var rpc = NexaRpcFactory.create(rpcConnection)
        var peerInfo = rpc.getpeerinfo()
        check(peerInfo.size >= 0  && peerInfo.size <= 10)  // Lots of stuff could be connected if you are actively working

        // Generate blocks until we get coins to spend. This is needed inside the ci testing.
        // But the code checks first so that lots of extra blocks aren't created during dev testing
        var rpcBalance = rpc.getbalance()
        LogIt.info(rpcBalance.toPlainString())
        while (rpcBalance < BigDecimal(50))
        {
            rpc.generate(1)
            rpcBalance = rpc.getbalance()
        }

        // Opens the send portion of the window
        onView(withId(GuiId.sendButton)).perform(click())  // Note if your phone is in a uninterruptable mode (like settings or notifications) then you'll get a spurious exception here
        // Clear because other tests might have left stuff in these (and check that sending is invalid when fields cleared)
        activityScenario.onActivity { it.ui.sendQuantity.text.clear() }
        // now do an actual send (with nothing filled in)
        onView(withId(GuiId.sendButton)).perform(click())  // Note if your phone is in a uninterruptable mode (like settings or notifications) then you'll get a spurious exception here

        // If you come in to this routine clean, you'll get badCryptoCode, but if you have accounts defined, you'll get badAmount
        activityScenario.onActivity { check(it.lastErrorId == R.string.badAmount || it.lastErrorId == R.string.badCryptoCode) }

        activityScenario.onActivity { it.ui.sendQuantity.text.append("11") }
        activityScenario.onActivity { it.ui.sendToAddress.text.clear() }
        onView(withId(GuiId.sendButton)).perform(click())
        // If you come in to this routine clean, you'll get badCryptoCode, but if you have accounts defined, you'll get badAddress
        activityScenario.onActivity { check(it.lastErrorId == R.string.badAddress  || it.lastErrorId == R.string.badCryptoCode) }

        activityScenario.onActivity { it.ui.sendQuantity.text.clear() }



        createNewAccount("rNEX1", cs)
        sleep(4000)
        // waitForActivity(10000, activityScenario) { app?.accounts["rNEX1"]?.cnxnMgr == null }
        app!!.accounts["rNEX1"]!!.cnxnMgr.exclusiveNodes(setOf(SimulationHostIP + ":" + REGTEST_P2P_PORT))
        activityScenario.onActivity { currentActivity == it }  // Clicking should bring us back to main screen
        createNewAccount("rNEX2", cs)
        activityScenario.onActivity { currentActivity == it }  // Clicking should bring us back to main screen

        peerInfo = rpc.getpeerinfo()
        check(peerInfo.size > 0)  // My accounts should be connected

        /* Send negative tests */
        retryUntilLayoutCan(){
            onView(withId(GuiId.sendToAddress)).perform(
                typeText("bad address"),
                pressImeActionButton()
            )
        }

        onView(withId(GuiId.sendQuantity)).perform(typeText("1.0"), pressImeActionButton())
        onView(withId(GuiId.sendButton)).perform(click())
        activityScenario.onActivity { check(it.lastErrorId == R.string.badAddress) }

        onView(withId(GuiId.sendQuantity)).perform(clearText(),typeText("xyz"), pressImeActionButton())
        onView(withId(GuiId.sendButton)).perform(click())
        activityScenario.onActivity { check(it.lastErrorId == R.string.badAmount) }

        // - can't be typed in the amount field
        onView(withId(GuiId.sendQuantity)).perform(
            clearText(),
            typeText("-1"),
            pressImeActionButton()
        ).check(matches(withText("1")))

        clickSpinnerItem(GuiId.recvIntoAccount, "rNEX1")
        var recvAddr: String = ""
        activityScenario.onActivity { recvAddr = it.ui.receiveAddress.text.toString() }

        // Copy the receive addr, and paste it into the destination
        onView(withId(GuiId.receiveAddress)).perform(click())
        onView(withId(GuiId.pasteFromClipboardButton)).perform(click())
        onView(withId(GuiId.sendToAddress)).check(matches(withText(recvAddr)))

        onView(withId(GuiId.sendQuantity)).perform(clearText(),typeText("100000000"), pressImeActionButton())
        clickSpinnerItem(GuiId.sendAccount, "rNEX1")
        onView(withId(GuiId.sendButton)).perform(click())
        waitForActivity(10000, activityScenario) { it.lastErrorString == i18n(R.string.insufficentBalance) }

        // Load coins
        clickSpinnerItem(GuiId.recvIntoAccount, "rNEX1")
        do {
            activityScenario.onActivity { recvAddr = it.ui.receiveAddress.text.toString() }
            if (recvAddr.contentEquals(i18n(R.string.copiedToClipboard))) Thread.sleep(200)
        } while(recvAddr.contentEquals(i18n(R.string.copiedToClipboard)))

        // RPC specifies in NEX, wallet in KEX
        var txHash = rpc.sendtoaddress(recvAddr, BigDecimal("1000000"))
        LogIt.info("SendToAddress RPC result: " + txHash.toString())

        TODO()
        /*
        waitForActivity(30000, activityScenario)
        {
            it.ui.balanceUnconfirmedValue2.text == "*1,000*"
        }


        // once we've received anything on an address, it should change to the next one
        activityScenario.onActivity { check(recvAddr != it.ui.receiveAddress.text.toString()) }

        // confirm it
        val blockHash = rpc.generate(1)
        txHash = blockHash[0]
        LogIt.info("Generate RPC result: " + txHash.toString())

        // See confirmation flow in the UX
        waitForActivity(30000, activityScenario)
        {
          (it.balanceUnconfirmedValue2.text == "") && (it.balanceValue2.text == "1,000")
        }

        // Now send from 1 to 2
        clickSpinnerItem(GuiId.sendAccount, "rNEX1")  // Choose the account
        clickSpinnerItem(GuiId.recvIntoAccount, "rNEX2")  // Read the receive address
        activityScenario.onActivity { recvAddr = it.ui.receiveAddress.text.toString() }
        // Write the receive address in
        onView(withId(GuiId.sendToAddress)).perform(clearText(), typeText(recvAddr), pressImeActionButton())
        onView(withId(GuiId.sendQuantity)).perform(clearText(),typeText("500"), pressImeActionButton())
        // Send the coins
        onView(withId(GuiId.sendButton)).perform(click())

        waitForActivity(30000, activityScenario)
        {
            it.balanceUnconfirmedValue3.text == "*500*"
        }

         */

        LogIt.info("Completed!")
    }
}