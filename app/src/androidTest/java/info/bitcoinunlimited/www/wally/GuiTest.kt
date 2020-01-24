package bitcoinunlimited.wally.guiTestImplementation

import android.app.Activity
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onData
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.NoMatchingViewException
import androidx.test.espresso.action.ViewActions.*
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.action.ViewActions
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.BoundedMatcher
import androidx.test.espresso.matcher.ViewMatchers.withText

import androidx.test.runner.AndroidJUnit4
import bitcoinunlimited.libbitcoincash.*
import info.bitcoinunlimited.www.wally.*
import kotlinx.android.synthetic.main.activity_main.*
import org.hamcrest.CoreMatchers.`is`
import org.hamcrest.CoreMatchers.instanceOf
import org.junit.Test
import org.junit.runner.RunWith
import java.util.EnumSet.allOf
import java.util.logging.Logger
import info.bitcoinunlimited.www.wally.R.id as GuiId

import org.hamcrest.Description
import org.hamcrest.Matcher
import org.hamcrest.Matchers.*
import wf.bitcoin.javabitcoindrpcclient.BitcoinJSONRPCClient
import java.lang.Exception
import java.math.BigDecimal


val LogIt = Logger.getLogger("GuiTest")

class TestTimeoutException(what: String): Exception(what)

val REGTEST_RPC_USER="z"
val REGTEST_RPC_PASSWORD="z"
val REGTEST_RPC_PORT=18332

@RunWith(AndroidJUnit4::class)
class GuiTest
{
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

        onView(withId(GuiId.GuiCreateAccountButton)).perform(click())
        val act = currentActivity!!
        check(act.lastErrorId == R.string.invalidAccountName)

        onView(withId(GuiId.GuiAccountNameEntry)).perform(typeText(name), pressImeActionButton(), pressBack())
        clickSpinnerItem(GuiId.GuiBlockchainSelector, ChainSelectorToSupportedBlockchains[chainSelector]!!)

        onView(withId(GuiId.GuiCreateAccountButton)).perform(click())
    }

    @Test fun testHomeActivity()
    {

        val activityScenario: ActivityScenario<MainActivity> = ActivityScenario.launch(MainActivity::class.java)
        activityScenario.moveToState(Lifecycle.State.RESUMED)
        var app: WallyApp? = null
        activityScenario.onActivity { app = (it.application as WallyApp) }

        // Clean up any prior run
        deleteWallet("mRbch1", ChainSelector.BCHREGTEST)
        deleteWallet("mRbch2", ChainSelector.BCHREGTEST)

        // Clean up old headers  ONLY NEEDED IF YOU RECREATE REGTEST NETWORK but reuse an emulator
        //deleteBlockHeaders("mRbch1", dbPrefix, appContext!!)
        //deleteBlockHeaders("mRbch2", dbPrefix, appContext!!)

        // supply this wallet with coins
        val rpcConnection = "http://" + REGTEST_RPC_USER + ":" + REGTEST_RPC_PASSWORD + "@" + SimulationHostIP + ":" + REGTEST_RPC_PORT
        LogIt.info("Connecting to: " + rpcConnection)
        var rpc = BitcoinJSONRPCClient(rpcConnection)
        var peerInfo = rpc.peerInfo
        check(peerInfo.size == 0)  // Nothing should be connected

        // Generate blocks until we get coins to spend. This is needed inside the ci testing.
        // But the code checks first so that lots of extra blocks aren't created during dev testing
        var rpcBalance = rpc.getBalance()
        LogIt.info(rpcBalance.toPlainString())
        while (rpcBalance < BigDecimal(50))
        {
            rpc.generate(1)
            rpcBalance = rpc.getBalance()
        }

        //val scenario = launchActivity<IdentityActivity>()
        onView(withId(GuiId.sendButton)).perform(click())
        activityScenario.onActivity { check(it.lastErrorId == R.string.badCryptoCode) }


        createNewAccount("mRbch1", ChainSelector.BCHREGTEST)
        activityScenario.onActivity { currentActivity == it }  // Clicking should bring us back to main screen
        createNewAccount("mRbch2", ChainSelector.BCHREGTEST)
        activityScenario.onActivity { currentActivity == it }  // Clicking should bring us back to main screen

        peerInfo = rpc.peerInfo
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

        clickSpinnerItem(GuiId.recvCoinType, "mRbch1")
        var recvAddr: String = ""
        activityScenario.onActivity { recvAddr = it.receiveAddress.text.toString() }

        // Copy the receive addr, and paste it into the destination
        onView(withId(GuiId.receiveAddress)).perform(click())
        onView(withId(GuiId.destAddrPasteButton)).perform(click())
        onView(withId(GuiId.sendToAddress)).check(matches(withText(recvAddr)))

        onView(withId(GuiId.sendQuantity)).perform(clearText(),typeText("100000000"), pressImeActionButton())
        onView(withId(GuiId.sendButton)).perform(click())
        //activityScenario.onActivity { waitFor(1000000) { it.lastErrorString == i18n(R.string.insufficentBalance) } }
        waitForActivity(10000, activityScenario) { it.lastErrorString == i18n(R.string.insufficentBalance) }

        // Load coins
        clickSpinnerItem(GuiId.recvCoinType, "mRbch1")
        do {
            activityScenario.onActivity { recvAddr = it.receiveAddress.text.toString() }
            if (recvAddr.contentEquals(i18n(R.string.copied))) Thread.sleep(200)
        } while(recvAddr.contentEquals(i18n(R.string.copied)))

        var rpcResult = rpc.sendToAddress(recvAddr, BigDecimal.ONE)
        var txHash = Hash256(rpcResult)
        LogIt.info("SendToAddress RPC result: " + txHash.toString())

        waitForActivity(30000, activityScenario)
        {
            it.balanceUnconfirmedValue2.text == "(1,000)"
        }


        // once we've received anything on an address, it should change to the next one
        activityScenario.onActivity { check(recvAddr != it.receiveAddress.text.toString()) }

        // confirm it
        val blockHash = rpc.generate(1)
        txHash = Hash256(blockHash[0])
        LogIt.info("Generate RPC result: " + txHash.toString())

        // See confirmation flow in the UX
        waitForActivity(30000, activityScenario)
        {
            (it.balanceUnconfirmedValue2.text == "") && (it.balanceValue2.text == "1,000")
        }

        // Now send from 1 to 2
        clickSpinnerItem(GuiId.sendCoinType, "mRbch1")  // Choose the account
        clickSpinnerItem(GuiId.recvCoinType, "mRbch2")  // Read the receive address
        activityScenario.onActivity { recvAddr = it.receiveAddress.text.toString() }
        // Write the receive address in
        onView(withId(GuiId.sendToAddress)).perform(clearText(), typeText(recvAddr), pressImeActionButton())
        onView(withId(GuiId.sendQuantity)).perform(clearText(),typeText("500"), pressImeActionButton())
        // Send the coins
        onView(withId(GuiId.sendButton)).perform(click())

        waitForActivity(30000, activityScenario)
        {
            it.balanceUnconfirmedValue3.text == "(500)"
        }

        LogIt.info("Completed!")
    }
}