// Copyright (c) 2019 Andrew Stone Consulting (qq9wwnuw4eukyh5g34ckg5vk4aaxnvr04vkspyv850)
// Distributed under the MIT software license, see the accompanying file COPYING or http://www.opensource.org/licenses/mit-license.php.
package info.bitcoinunlimited.www.wally
import bitcoinunlimited.libbitcoincash.*

import android.content.*
import android.os.Bundle
import android.view.*
import android.widget.*

import java.util.logging.Logger
import info.bitcoinunlimited.www.wally.databinding.ActivityAccountDetailsBinding


private val LogIt = Logger.getLogger("BU.wally.actdetails")

enum class ConfirmationFor
{
    Delete, Rediscover, RediscoverBlockchain, Reassess, RecoveryPhrase, PrimaryAccount
}

class AccountDetailsActivity: CommonNavActivity()
{
    private lateinit var ui: ActivityAccountDetailsBinding
    override var navActivityId = R.id.home
    var askingAbout: ConfirmationFor? = null
    var selectedAccount:Account? = null
    var pinTries = 0

    override fun onCreate(savedInstanceState: Bundle?)
    {
        super.onCreate(savedInstanceState)
        ui = ActivityAccountDetailsBinding.inflate(layoutInflater)
        setContentView(ui.root)

        wallyApp?.let { app ->
            val acc = app.focusedAccount
            selectedAccount = acc
            if (acc != null) updateAccount(acc)
            else finish()  // I don't have an account; not even sure how this activity was run
        }

        ui.GuiPINInvisibility.setOnCheckedChangeListener(CompoundButton.OnCheckedChangeListener { _, isChecked ->
            val coin = selectedAccount
            if (coin != null)
            {
                if (isChecked)
                    coin.flags = coin.flags or ACCOUNT_FLAG_HIDE_UNTIL_PIN
                else
                    coin.flags = coin.flags and ACCOUNT_FLAG_HIDE_UNTIL_PIN.inv()
                launch {  // Can't be in UI thread
                    coin.saveAccountFlags()
                }
            }

        })
    }

    override fun onResume()
    {
        super.onResume()
        val acc = selectedAccount
        if (acc == null)
        {
            wallyApp?.displayError(R.string.NoAccounts)
            finish()
            return
        }
        if (acc.locked)
        {
            if (pinTries == 0)
            {
                val intent = Intent(this, UnlockActivity::class.java)
                pinTries += 1
                startActivity(intent)
                return
            }
            else
            {
                wallyApp?.displayError(R.string.InvalidPIN)
                finish()
                return
            }
        }
    }


    fun updateAccount(acc: Account)
    {
        setTitle(i18n(R.string.title_activity_account_details) % mapOf("account" to acc.name))

        val primVis = if (wallyApp?.nullablePrimaryAccount == acc) View.GONE     // its already primary
        else if (acc.chain.chainSelector == ChainSelector.NEXA) View.VISIBLE     // All nexa accounts are candidates
        else if (devMode && acc.chain.chainSelector.isNexaFamily) View.VISIBLE   // all nexa family accounts are candidates in dev mode
        else View.GONE
        ui.GuiPrimaryAccountButton.visibility = primVis

        if (acc.encodedPin != null)
        {
            ui.GuiPINInvisibility.setEnabled(true)
            ui.GuiPINInvisibility.setChecked(acc.flags and ACCOUNT_FLAG_HIDE_UNTIL_PIN > 0UL)
        }
        else  // Account does not have a pin TODO: add ability to set/change the pin
        {
            ui.GuiPINInvisibility.visibility = View.GONE
        }
        val stat = acc.wallet.statistics()
        ui.NumAddresses.text = "  " + (i18n(R.string.AccountNumAddresses) % mapOf("num" to stat.numUsedAddrs.toString())) + "  "
        ui.NumTransactions.text = "  " +  (i18n(R.string.AccountNumTx) % mapOf("num" to stat.numTransactions.toString()))  + "  "
        ui.NumUtxos.text = "  " + (i18n(R.string.AccountNumUtxos) % mapOf("num" to stat.numUnspentTxos.toString())) + "  "

        ui.FirstLastSend.text = i18n(R.string.FirstLastSend) % mapOf(
          "first" to (if (stat.firstSendHeight == Long.MAX_VALUE) "never" else stat.firstSendHeight.toString()),
          "last" to (if (stat.lastSendHeight==0L) "never" else stat.lastSendHeight.toString()))
        ui.FirstLastReceive.text = i18n(R.string.FirstLastReceive) % mapOf(
          "first" to (if (stat.firstReceiveHeight == Long.MAX_VALUE) "never" else stat.firstReceiveHeight.toString()),
          "last" to (if (stat.lastReceiveHeight == 0L) "never" else stat.lastReceiveHeight.toString()))

        val chainstate = acc.wallet.chainstate
        if (chainstate != null)
        {

            val synced = if (chainstate.isSynchronized()) R.string.synced else R.string.unsynced

            ui.AccountBlockchainSync.sizedText(i18n(R.string.AccountBlockchainSync) % mapOf<String,String>(
              "sync" to i18n(synced),
              "chain" to chainstate.chain.name,
            ), -16, maxFontSizeInSp = 64)
            ui.AccountBlockchainDetails.sizedText(i18n(R.string.AccountBlockchainDetails) % mapOf<String,String>(
              "actBlock" to chainstate.syncedHeight.toString(),
              "actBlockDate" to epochToDate(chainstate.syncedDate),
              "chainBlockCount" to chainstate.chain.curHeight.toString()
              ), -16, maxFontSizeInSp = 40)
            val cnxnLst = chainstate.chain.net.mapConnections() { it.name }
            val trying:List<String> = if (chainstate.chain.net is MultiNodeCnxnMgr) (chainstate.chain.net as MultiNodeCnxnMgr).initializingCnxns.map { it.name } else listOf()
            val peers = cnxnLst.joinToString(", ") + if (trying.isNotEmpty()) (" " + i18n(R.string.trying) + " " + trying.joinToString(", ")) else ""
            ui.AccountBlockchainConnectionDetails.text =  i18n(R.string.AccountBlockchainConnectionDetails) % mapOf(
              "num" to cnxnLst.size.toString(),
              "names" to peers
            )
        }
        else
        {
            ui.AccountBlockchainDetails.text = i18n(R.string.walletDisconnectedFromBlockchain)
        }


    }

    @Suppress("UNUSED_PARAMETER")
    fun onClearIdentityDomains(v: View?)
    {
        val act = selectedAccount
        launch {
            if (act!=null) act.wallet.identityDomain.clear()
        }
    }

    @Suppress("UNUSED_PARAMETER")
    fun onYes(v: View?)
    {
        ui.ConfirmationConstraint.visibility = View.GONE
        ui.confirmationOps.visibility = View.VISIBLE

        val a = askingAbout
        if (a == null) return

        val act = selectedAccount

        askingAbout = null
        when (a)
        {
            ConfirmationFor.PrimaryAccount ->
            {
                if (act != null) wallyApp?.primaryAccount = act
            }
            ConfirmationFor.RediscoverBlockchain ->
            {
                if (act == null) return
                launch {
                    val bc = act.wallet.blockchain
                    // If you reset the wallet first, it'll start rediscovering the existing blockchain before it gets reset.
                    bc.rediscover()
                    wallyApp?.let {
                        for (c in it.accounts)  // Rediscover tx for EVERY wallet using this blockchain
                        {
                            if (c.value.wallet.blockchain == bc)
                                c.value.wallet.rediscover(true, true)
                        }
                    }

                }
                displayNotice(i18n(R.string.rediscoverNotice))
            }
            ConfirmationFor.Rediscover ->
            {
                if (act == null) return
                launch {
                    act.wallet.rediscover(true, false)
                    displayNotice(i18n(R.string.rediscoverNotice))
                }
            }
            ConfirmationFor.Reassess ->
            {
                if (act == null) return
                launch {
                    try
                    {
                        // TODO while we don't have Rostrum (electrum) we can't reassess, so just forget them under the assumption that they will be confirmed and accounted for, or are bad.
                        // coin.wallet.reassessUnconfirmedTx()
                        act.wallet.cleanUnconfirmed()
                        displayNotice(i18n(R.string.unconfAssessmentNotice))
                    } catch (e: Exception)
                    {
                        displayNotice(e.message ?: e.toString())
                    }
                }
            }
            ConfirmationFor.Delete ->
            {

                if (act == null) return
                act.detachUI()
                wallyApp?.deleteAccount(act)
                wallyApp?.accounts?.remove(act.name)  // remove this coin from any global access before we delete it
                act.wallet.stop()
                launch { // cannot access db in UI thread
                    wallyApp?.saveActiveAccountList()
                    selectedAccount?.delete()
                }
                displayNotice(i18n(R.string.accountDeleteNotice))
                // setupAccountSelection()  // reload this spinner since an account was removed
            }
            ConfirmationFor.RecoveryPhrase ->
            {
                // put it all back to its defaults
                ui.GuiConfirmationText2.text = ""
                ui.buttonNo.visibility = View.VISIBLE
                ui.buttonYes.text = i18n(R.string.yes)
            }
        }
    }

    @Suppress("UNUSED_PARAMETER")
    fun onNo(v: View?)
    {
        ui.GuiConfirmationText2.text = ""
        askingAbout = null
        ui.ConfirmationConstraint.visibility = View.GONE
        ui.confirmationOps.visibility = View.VISIBLE
    }

    fun showConfirmation()
    {
        ui.confirmationOps.visibility = View.GONE
        ui.ConfirmationConstraint.visibility = View.VISIBLE
    }

    @Suppress("UNUSED_PARAMETER")
    fun onConfirmationOps(v: View?): Boolean
    {
        return true
    }

    @Suppress("UNUSED_PARAMETER")
    fun onRediscoverBlockchain(v: View?): Boolean
    {
        // Strangely, if the contraint layout is touched, it calls this function
        if (v != ui.GuiRediscoverBlockchainButton) return false

        askingAbout = ConfirmationFor.RediscoverBlockchain
        ui.GuiConfirmationText.text = i18n(R.string.rediscoverBlockchainConfirmation)
        showConfirmation()
        return true
    }

    @Suppress("UNUSED_PARAMETER")
    fun onRediscoverWallet(v: View?): Boolean
    {
        // Strangely, if the contraint layout is touched, it calls this function
        if (v != ui.GuiRediscoverButton) return false
        askingAbout = ConfirmationFor.Rediscover
        ui.GuiConfirmationText.text = i18n(R.string.rediscoverConfirmation)
        showConfirmation()
        return true
    }

    @Suppress("UNUSED_PARAMETER")
    fun onViewRecoveryPhrase(v: View?)
    {
        askingAbout = ConfirmationFor.RecoveryPhrase
        val coin = selectedAccount
        if (coin == null) return
        ui.GuiConfirmationText.text = i18n(R.string.recoveryPhrase)
        val tmp = coin.wallet.secretWords.split(" ")
        val halfwords:Int = tmp.size/2

        ui.GuiConfirmationText2.text = tmp.subList(0,halfwords).joinToString(" ") + "\n" + tmp.subList(halfwords, tmp.size).joinToString(" ")
        showConfirmation()
        ui.buttonNo.visibility = View.GONE
        ui.buttonYes.text = i18n(R.string.done)
    }

    @Suppress("UNUSED_PARAMETER")
    /** Reassess unconfirmed transactions */
    public fun onAssessUnconfirmedButton(v: View)
    {
        askingAbout = ConfirmationFor.Reassess
        ui.GuiConfirmationText.text = i18n(R.string.reassessConfirmation)
        showConfirmation()
    }

    @Suppress("UNUSED_PARAMETER")
    /** Reassess unconfirmed transactions */
    public fun onSetAsPrimaryAccountButton(v: View)
    {
        askingAbout = ConfirmationFor.PrimaryAccount
        ui.GuiConfirmationText.text = i18n(R.string.primaryAccountConfirmation)
        showConfirmation()
    }

    @Suppress("UNUSED_PARAMETER")
    /** Delete a wallet account */
    public fun onDeleteAccountButton(v: View)
    {
        askingAbout = ConfirmationFor.Delete
        val coin = selectedAccount
        if (coin == null) return
        ui.GuiConfirmationText.text = i18n(R.string.deleteConfirmation) % mapOf("accountName" to coin.name, "blockchain" to coin.currencyCode)
        showConfirmation()
    }

    public fun onTxHistoryButton(v: View)
    {
        val acc = selectedAccount
        if (acc == null) return
        try
        {
            dbgAssertGuiThread()
            val intent = Intent(this, TxHistoryActivity::class.java)
            intent.putExtra("WalletName", acc.name)
            startActivity(intent)
        }
        catch (e: Exception)
        {
            LogIt.warning("Exception clicking on ticker name: " + e.toString())
        }
    }
}