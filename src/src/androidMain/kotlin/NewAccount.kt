package info.bitcoinunlimited.www.wally


import android.graphics.drawable.AnimatedVectorDrawable
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.AdapterView
import android.widget.AdapterView.OnItemSelectedListener
import android.widget.ArrayAdapter
import android.widget.MultiAutoCompleteTextView
import android.widget.MultiAutoCompleteTextView.Tokenizer
import info.bitcoinunlimited.www.wally.databinding.ActivityNewAccountBinding
import org.nexa.libnexakotlin.libnexa

import kotlin.concurrent.thread
import org.nexa.libnexakotlin.*
import org.nexa.threads.Mutex

private val LogIt = GetLog("BU.wally.NewAccount")

val chainToName: Map<ChainSelector, String> = mapOf(
  ChainSelector.NEXATESTNET to "tNexa", ChainSelector.NEXAREGTEST to "rNexa", ChainSelector.NEXA to "nexa",
  ChainSelector.BCH to "bch", ChainSelector.BCHTESTNET to "tBch", ChainSelector.BCHREGTEST to "rBch"
)

class CharTokenizer(val separator: Char) : Tokenizer
{

    // Returns the end of the token (minus trailing punctuation) that begins at offset cursor within text.
    override fun findTokenEnd(cs: CharSequence?, pos: Int): Int
    {
        if (cs == null) return pos
        var curPos = pos
        while (curPos < cs.length)
        {
            if (cs[curPos] == separator) return curPos - 1
            curPos++
        }
        return curPos - 1
    }

    // Returns the start of the token that ends at offset cursor within text.
    override fun findTokenStart(cs: CharSequence?, pos: Int): Int
    {
        if (cs == null) return pos
        var curPos = pos - 1
        while (curPos > 0)
        {
            if (cs[curPos] == separator) return curPos + 1
            curPos--
        }
        return 0
    }

    // Returns text, modified, if necessary, to ensure that it ends with a token terminator (for example a space or comma).
    override fun terminateToken(p: CharSequence?): CharSequence
    {
        if (p == null) return separator.toString()
        if (p.endsWith(" ")) return p
        return p.toString() + separator.toString()
    }
}

class NewAccount : CommonNavActivity()
{
    override var navActivityId = R.id.navigation_home
    private lateinit var ui:ActivityNewAccountBinding

    var app: WallyApp? = null
    var recoveryChange = 0

    /** The earliest activity on this account in seconds since the epoch */
    var earliestActivity: Long? = 1577836800 // TODO support entry of recovery date
    var earliestActivityHeight: Int = 0

    val nonstandardActivity = mutableListOf<Pair<Bip44Wallet.HdDerivationPath, HDActivityBracket>>()

    var processingThread: Thread? = null
    var lock = Mutex()

    var nameOk = false
    var pinOk = true
    var nameChangedByUser = false
    var codeChanged:Int = 0  // if a field is programatically changed, this is set to stop the callback from behaving like it was a user selected change

    // For some operations, require that the "create account" button be pressed twice
    var oked = 0

    var curPeek: Objectify<Boolean>? = null

    val peekThreads = ThreadGroup("account_lookahead")

    override fun onCreate(savedInstanceState: Bundle?)
    {
        app = (getApplication() as WallyApp)
        super.onCreate(savedInstanceState)
        ui = ActivityNewAccountBinding.inflate(layoutInflater)
        setContentView(ui.root)

        val blockchains = ArrayAdapter(this, R.layout.blockchain_selection_spinner, SupportedBlockchains.filter { devMode || it.value.isMainNet }.keys.toTypedArray())
        ui.GuiBlockchainSelector.setAdapter(blockchains)

        val adapter: ArrayAdapter<String> = ArrayAdapter<String>(this, R.layout.recovery_phrase_selection_spinner, englishWordList)
        val textView = findViewById<MultiAutoCompleteTextView>(R.id.GuiAccountRecoveryPhraseEntry)
        textView.setAdapter(adapter)
        textView.setTokenizer(CharTokenizer(' '))

        ui.GuiBlockchainOk.setImageResource(R.drawable.ic_check)
        ui.GuiRecoveryPhraseOk.setImageResource(R.drawable.ic_check)  // empty recovery phrase is valid (means create a new one)

        ui.GuiBlockchainSelector.setOnItemSelectedListener(object : OnItemSelectedListener {
            override fun onItemSelected(p0: AdapterView<*>?, p1: View?, p2: Int, p3: Long)
            {
                val bc = SupportedBlockchains[ui.GuiBlockchainSelector.selectedItem.toString()]
                // If the recovery phrase is good, let's peek at the blockchain to see if there's activity
                val p = ui.GuiAccountRecoveryPhraseEntry.text.toString()
                val words = processSecretWords(p)
                launchPeekFirstActivity(words, SupportedBlockchains[ui.GuiBlockchainSelector.selectedItem]!!)
                if (!nameChangedByUser)  // If the user has already put something in, then don't touch it
                {
                    val a = wallyApp
                    if ((bc != null)&&(a != null))
                    {
                        val proposedName =  chainToName[bc]
                        if ((proposedName!=null) && !a.accounts.contains(proposedName))  // If there's already a default choice, then don't offer one
                        {
                            codeChanged++
                            ui.GuiAccountNameEntry.set(proposedName)
                        }
                        else
                        {
                            ui.GuiAccountNameEntry.set("")  // If we set it then remove our name (because it refers to a different blockchain)
                        }
                    }
                }
            }

            override fun onNothingSelected(p0: AdapterView<*>?)
            {
                TODO("Not yet implemented")
            }

        })
        ui.GuiBlockchainSelector.setSelection("NEXA")


        ui.GuiAccountNameEntry.addTextChangedListener(object : TextWatcher
        {
            override fun afterTextChanged(p0: Editable?)
            {
                if (codeChanged==0) nameChangedByUser = true
                else codeChanged--  // a programmatic change will trigger one callback
                dbgAssertGuiThread()
                if (p0.isNullOrBlank())
                {
                    ui.GuiAccountNameOk.setImageResource(android.R.drawable.ic_delete)
                    nameOk = false
                    nameChangedByUser = false // If the user wipes out their name, we can resume proposing names
                    return
                }

                if (p0.length > 8)
                {
                    ui.GuiAccountNameOk.setImageResource(android.R.drawable.ic_delete)
                    nameOk = false
                    return
                }

                val proposedName = p0.toString()
                if (wallyApp?.accounts?.contains(proposedName) ?: false == true)
                {
                    ui.GuiAccountNameOk.setImageResource(android.R.drawable.ic_delete)
                    nameOk = false
                    return

                }
                ui.GuiAccountNameOk.setImageResource(R.drawable.ic_check)
                nameOk = true
            }

            override fun beforeTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int)
            {
            }

            override fun onTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int)
            {
            }
        })

        ui.GuiPINEntry.addTextChangedListener(object : TextWatcher
        {
            override fun afterTextChanged(p0: Editable?)
            {
                dbgAssertGuiThread()
                if (p0.isNullOrBlank())
                {
                    ui.GuiPINOk.setImageResource(R.drawable.ic_check)
                    ui.PinProtectsSpending.text = i18n(R.string.PinSpendingUnprotected)
                    pinOk = true
                    return
                }

                ui.PinProtectsSpending.text = i18n(R.string.PinSpendingProtected)
                if (p0.length > 0 && p0.length < 4)
                {
                    ui.GuiPINOk.setImageResource(android.R.drawable.ic_delete)
                    pinOk = false
                    return
                }

                ui.GuiPINOk.setImageResource(R.drawable.ic_check)
                pinOk = true
            }

            override fun beforeTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int)
            {
            }

            override fun onTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int)
            {
            }
        })


        ui.GuiAccountRecoveryPhraseEntry.addTextChangedListener(object : TextWatcher
        {
            override fun afterTextChanged(p: Editable?)
            {
                recoveryChange++
                dbgAssertGuiThread()

                // clear the status for regeneration if the phrase is ok
                ui.GuiNewAccountStatus.text = ""
                ui.GuiNewAccountError.text = ""

                // Phrase changed so forget prior earliest activity
                earliestActivity = null
                earliestActivityHeight = 0

                // If Recovery phrase is blank we'll generate one so that's OK
                if (p.isNullOrBlank())
                {
                    ui.GuiRecoveryPhraseOk.setImageResource(R.drawable.ic_check)
                    return
                }

                // Check recovery phrase validity and be unhappy if its not good
                val words = processSecretWords(p.toString())
                if (words.size < 12)  // TODO support other size recovery keys
                {
                    ui.GuiRecoveryPhraseOk.setImageResource(android.R.drawable.ic_delete)
                    ui.GuiNewAccountError.text = i18n(R.string.NotEnoughRecoveryWords)
                    return
                }
                if (words.size > 12)  // TODO support other size recovery keys
                {
                    ui.GuiRecoveryPhraseOk.setImageResource(android.R.drawable.ic_delete)
                    ui.GuiNewAccountError.text = i18n(R.string.TooManyRecoveryWords)
                    return
                }
                val incorrectWords = Bip39InvalidWords(words)
                if (incorrectWords.size > 0)
                {
                    ui.GuiRecoveryPhraseOk.setImageResource(android.R.drawable.ic_delete)
                    ui.GuiNewAccountError.text = i18n(R.string.IncorrectRecoveryWords) % mapOf("words" to incorrectWords.joinToString(","))
                    return
                }
                ui.GuiRecoveryPhraseOk.setImageResource(R.drawable.ic_check)

                launchPeekFirstActivity(words, SupportedBlockchains[ui.GuiBlockchainSelector.selectedItem]!!)
            }

            override fun beforeTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int)
            {
            }

            override fun onTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int)
            {
            }
        })

    }

    fun launchPeekFirstActivity(words: List<String>, cs: ChainSelector)
    {
        if (words.size != 12) return
        curPeek?.let { it.obj = true }
        val p = Objectify<Boolean>(false)
        curPeek = p
        // If the recovery phrase is good, let's peek at the blockchain to see if there's activity
        // thread(true, true, null, "peekWallet") // kotlin api does not offer stack size setting
        val th = Thread(peekThreads,
        {
            laterUI {
                ui.GuiNewAccountStatus.text = i18n(R.string.NewAccountSearchingForTransactions)
                val d: AnimatedVectorDrawable = getDrawable(R.drawable.ani_syncing) as AnimatedVectorDrawable // Insert your AnimatedVectorDrawable resource identifier
                ui.GuiStatusOk.setImageDrawable(d)
                d.start()
            }
            try
            {
                peekFirstActivity(words.joinToString(" "), cs, p)
            }
            catch (e: Exception)
            {
                laterUI { ui.GuiNewAccountStatus.text = i18n(R.string.NewAccountSearchFailure) }
                LogIt.severe("wallet peek error: " + e.toString())
                handleThreadException(e, "wallet peek error", sourceLoc())
            }
        }, "peekThread", 4*1024*1024)
        th.start()
    }

    fun launchPeekAllActivity(words: List<String>, cs: ChainSelector)
    {
        if (words.size != 12) return
        curPeek?.let { it.obj = true }
        val p = Objectify<Boolean>(false)
        curPeek = p
        // If the recovery phrase is good, let's peek at the blockchain to see if there's activity
        // thread(true, true, null, "peekWallet") // kotlin api does not offer stack size setting
        val th = Thread(peekThreads,
          {
              laterUI {
                  ui.GuiNewAccountStatus.text = i18n(R.string.NewAccountSearchingForTransactions)
                  val d: AnimatedVectorDrawable = getDrawable(R.drawable.ani_syncing) as AnimatedVectorDrawable // Insert your AnimatedVectorDrawable resource identifier
                  ui.GuiStatusOk.setImageDrawable(d)
                  d.start()
              }
              try
              {
                  peekFirstActivity(words.joinToString(" "), cs, p)
              }
              catch (e: Exception)
              {
                  laterUI { ui.GuiNewAccountStatus.text = i18n(R.string.NewAccountSearchFailure) }
                  LogIt.severe("wallet peek error: " + e.toString())
                  handleThreadException(e, "wallet peek error", sourceLoc())
              }
          }, "peekThread", 4*1024*1024)
        th.start()
    }


    fun searchAllActivity(ec: ElectrumClient, chainSelector: ChainSelector, count: Int, secretDerivation: (Int) -> ByteArray, activityFound: ((Long, Int) -> Boolean)? = null): List<TransactionHistory>
    {
        var index = 0
        var ret = mutableListOf<TransactionHistory>()
        while (index < count)
        {
            val newSecret = secretDerivation(index)
            val us = UnsecuredSecret(newSecret)

            val dests = mutableListOf<SatoshiScript>(Pay2PubKeyHashDestination(chainSelector, us, index.toLong()).outputScript())  // Note, if multiple destination types are allowed, the wallet load/save routines must be updated
            if (chainSelector.hasTemplates)
                dests.add(Pay2PubKeyTemplateDestination(chainSelector, us, index.toLong()).ungroupedOutputScript())

            for (dest in dests)
            {
                try
                {
                    val history = ec.getHistory(dest, 10000)
                    for (h in history)
                    {
                        val tx = ec.getTx(h.second)
                        val txh:TransactionHistory = TransactionHistory(chainSelector, tx)
                        val headerBytes = ec.getHeader(h.first)
                        val header = blockHeaderFor(chainSelector, BCHserialized(SerializationType.NETWORK, headerBytes))
                        if (header.validate(chainSelector))
                        {
                            txh.confirmedHeight = h.first.toLong()
                            txh.confirmedHash = header.hash
                            ret.add(txh)
                        }
                    }
                }
                catch (e: ElectrumNotFound)
                {
                }
            }
            index++
        }
        return ret
    }


    fun searchFirstActivity(ec: ElectrumClient, chainSelector: ChainSelector, count: Int, secretDerivation: (Int) -> ByteArray, activityFound: ((Long, Int) -> Boolean)? = null): Pair<Long, Int>?
    {
        var index = 0
        var ret: Pair<Long, Int>? = null
        while (index < count)
        {
            val newSecret = secretDerivation(index)
            val us = UnsecuredSecret(newSecret)

            val dests = mutableListOf<SatoshiScript>(Pay2PubKeyHashDestination(chainSelector, us, index.toLong()).outputScript())  // Note, if multiple destination types are allowed, the wallet load/save routines must be updated
            //LogIt.info(sourceLoc() + " " + name + ": New Destination " + tmp.toString() + ": " + dest.address.toString())
            if (chainSelector.hasTemplates)
                dests.add(Pay2PubKeyTemplateDestination(chainSelector, us, index.toLong()).ungroupedOutputScript())

            for (dest in dests)
            {
                try
                {
                    val use = ec.getFirstUse(dest, 10000)
                    if (use.block_hash != null)
                    {
                        val bh = use.block_height
                        if (bh != null)
                        {
                            LogIt.info("Found activity at index $index in ${dest.address.toString()}")
                            val headerBin = ec.getHeader(bh)
                            val blkHeader = blockHeaderFor(chainSelector, BCHserialized(headerBin, SerializationType.HASH))
                            if (ret == null || blkHeader.time < ret.first)
                            {
                                activityFound?.invoke(blkHeader.time, bh)
                                ret = Pair(blkHeader.time, bh)
                            }
                        }
                    }
                    else
                    {
                        LogIt.info("didn't find activity at index $index in ${dest.address.toString()}")
                    }
                }
                catch (e: ElectrumNotFound)
                {
                    LogIt.info("didn't find activity at index $index in ${dest.address.toString()}")
                }
            }
            index++
        }
        return ret
    }


    fun bracketActivity(ec: ElectrumClient, chainSelector: ChainSelector, giveUpGap: Int, secretDerivation: (Int) -> ByteArray): HDActivityBracket?
    {
        var index = 0
        var lastFoundIndex = 0
        var startTime = 0L
        var startBlock = 0
        var lastTime = 0L
        var lastBlock = 0

        while (index < lastFoundIndex + giveUpGap)
        {
            val newSecret = secretDerivation(index)

            val dest = Pay2PubKeyHashDestination(chainSelector, UnsecuredSecret(newSecret), index.toLong())  // Note, if multiple destination types are allowed, the wallet load/save routines must be updated

            try
            {
                val use = ec.getFirstUse(dest.outputScript(), 10000)
                if (use.block_hash != null)
                {
                    if (use.block_height != null)
                    {
                        lastFoundIndex = index
                        lastBlock = use.block_height!!
                        if (startBlock == 0) startBlock = use.block_height!!
                    }
                }
                else
                {
                    LogIt.info("didn't find activity")
                }
            } catch (e: ElectrumNotFound)
            {
                LogIt.info("didn't find activity")
            }
            index++
        }

        if (startBlock == 0) return null  // Safe to use 0 because no spendable tx in genesis block

        if (true)
        {
            val headerBin = ec.getHeader(startBlock)
            val blkHeader = blockHeaderFor(chainSelector, BCHserialized(headerBin, SerializationType.HASH))
            startTime = blkHeader.time
        }
        if (true)
        {
            val headerBin = ec.getHeader(lastBlock)
            val blkHeader = blockHeaderFor(chainSelector, BCHserialized(headerBin, SerializationType.HASH))
            lastTime = blkHeader.time
        }

        return HDActivityBracket(startTime, startBlock, lastTime, lastBlock, lastFoundIndex)
    }


    fun peekFirstActivity(secretWords: String, chainSelector: ChainSelector, aborter: Objectify<Boolean>)
    {
        val net = blockchains[chainSelector]?.net
        val ec = net?.getElectrum()
        if (ec == null)
        {
            laterUI {
                    ui.GuiNewAccountStatus.text = i18n(R.string.ElectrumNetworkUnavailable)
                    ui.GuiStatusOk.setImageResource(android.R.drawable.ic_delete)
            }
            return
        }
        try
        {

            if (aborter.obj) return

            val passphrase = "" // TODO: support a passphrase
            val secret = generateBip39Seed(secretWords, passphrase)

            val addressDerivationCoin = Bip44AddressDerivationByChain(chainSelector)

            LogIt.info("Searching in ${addressDerivationCoin}")
            var earliestActivityP =
              searchFirstActivity(ec, chainSelector, WALLET_RECOVERY_DERIVATION_PATH_SEARCH_DEPTH, {
                  libnexa.deriveHd44ChildKey(secret, AddressDerivationKey.BIP44, addressDerivationCoin, 0, false, it).first
              }, { time, height ->
                  laterUI {
                      ui.GuiNewAccountStatus.text = i18n(R.string.Bip44ActivityNotice) + " " + (i18n(R.string.FirstUseDateHeightInfo) % mapOf(
                        "date" to epochToDate(time),
                        "height" to height.toString())
                        )
                      synchronized(earliestActivityHeight) {
                          earliestActivity = time
                          earliestActivityHeight = height
                      }
                  }
                  true
              })

            if (aborter.obj) return

            LogIt.info("Searching in ${AddressDerivationKey.ANY}")
            // Look for activity in the identity and common location
            var earliestActivityId =
              searchFirstActivity(ec, chainSelector, WALLET_RECOVERY_IDENTITY_DERIVATION_PATH_SEARCH_DEPTH, {
                  libnexa.deriveHd44ChildKey(secret, AddressDerivationKey.BIP44, AddressDerivationKey.ANY, 0, false, it).first
              })
            if (aborter.obj) return

            // Set earliestActivityP to the lesser of the two
            if (earliestActivityP == null) earliestActivityP = earliestActivityId
            else
            {
                if ((earliestActivityId != null) && (earliestActivityId.first < earliestActivityP.first)) earliestActivityP = earliestActivityId
            }
            if (aborter.obj) return
            val Bip44Msg = if (earliestActivityP != null)
            {
                synchronized(earliestActivityHeight) {
                    earliestActivity = earliestActivityP.first - 1 // -1 so earliest activity is just before the activity
                    earliestActivityHeight = earliestActivityP.second
                }
                i18n(R.string.Bip44ActivityNotice) + " " + i18n(R.string.FirstUseDateHeightInfo) % mapOf(
                  "date" to epochToDate(earliestActivityP.first),
                  "height" to earliestActivityP.second.toString()
                )
            }
            else
            {
                synchronized(earliestActivityHeight) {
                    earliestActivity = null
                    earliestActivityHeight = 0
                }
                i18n(R.string.NoBip44ActivityNotice)
            }

            val Bip44BTCMsg = ""

            /*
        // Look in non-standard places for activity
        val BTCactivity =
          bracketActivity(ec, chainSelector, DERIVATION_PATH_SEARCH_DEPTH, { AddressDerivationKey.Hd44DeriveChildKey(secret, AddressDerivationKey.BIP44, AddressDerivationKey.BTC, 0, 0, it) })
        var BTCchangeActivity: HDActivityBracket?

        // This code checks whether coins exist on the Bitcoin derivation path to see if any prefork coins exist.  This is irrelevant for Nexa.
        // I'm leaving the code in though because someday we might want to share pubkeys between BTC/BCH and Nexa and in that case we'd need to use their derivation path.

        var Bip44BTCMsg = if (BTCactivity != null)
        {
            BTCchangeActivity =
              bracketActivity(ec, chainSelector, DERIVATION_PATH_SEARCH_DEPTH, { AddressDerivationKey.Hd44DeriveChildKey(secret, AddressDerivationKey.BIP44, AddressDerivationKey.BTC, 0, 1, it) })
            nonstandardActivity.clear()  // clear because peek can be called multiple times if the user changes the secret
            nonstandardActivity.add(Pair(Bip44Wallet.HdDerivationPath(null, AddressDerivationKey.BIP44, AddressDerivationKey.BTC, 0, 0, BTCactivity.lastAddressIndex), BTCactivity))
            if (BTCchangeActivity != null)
            {
                nonstandardActivity.add(Pair(Bip44Wallet.HdDerivationPath(null, AddressDerivationKey.BIP44, AddressDerivationKey.BTC, 0, 1, BTCchangeActivity.lastAddressIndex), BTCchangeActivity))
            }

            i18n(R.string.Bip44BtcActivityNotice) + " " + i18n(R.string.FirstUseDateHeightInfo) % mapOf(
              "date" to epochToDate(BTCactivity.startTime),
              "height" to BTCactivity.startBlockHeight.toString()
            )
        }
        else i18n(R.string.NoBip44BtcActivityNotice)
        */

            /*
        earliestActivityP = searchActivity(ec, chainSelector, DERIVATION_PATH_SEARCH_DEPTH, { AddressDerivationKey.Hd44DeriveChildKey(secret, AddressDerivationKey.BIP43, AddressDerivationKey.BTC, 0, 0, it) })
        var Bip44BTCMsg = if (earliestActivityP != null)
        {
            earliestActivity = earliestActivityP.first-1 // -1 so earliest activity is just before the activity
            i18n(R.string.Bip44BtcActivityNotice) + " " + i18n(R.string.FirstUseDateHeightInfo) % mapOf(
                "date" to epochToDate(earliestActivityP.first),
                "height" to earliestActivityP.second.toString())
        }
        else i18n(R.string.NoBip44BtcActivityNotice)
         */

            laterUI {
                ui.GuiNewAccountStatus.text = Bip44Msg + "\n" + Bip44BTCMsg
                if (earliestActivity != null) ui.GuiStatusOk.setImageResource(R.drawable.ic_check)
                else ui.GuiStatusOk.setImageResource(android.R.drawable.ic_delete)
            }
        }
        finally
        {
            net?.returnElectrum(ec)
        }
    }


    fun recoverAccountPhase2(name: String, flags: ULong, pin: String, secretWords: String, chainSelector: ChainSelector)
    {
        // TODO Bip39 passphrase support
        if (secretWords.length > 0)
        {
            val words = processSecretWords(secretWords)
            if (words.size != 12)
            {
                laterUI { ui.GuiRecoveryPhraseOk.setImageResource(android.R.drawable.ic_delete) }
                displayError(R.string.invalidRecoveryPhrase)
                return
            }
            val incorrectWords = Bip39InvalidWords(words)
            if (incorrectWords.size > 0)
            {
                laterUI { ui.GuiRecoveryPhraseOk.setImageResource(android.R.drawable.ic_delete) }
                displayError(R.string.invalidRecoveryPhrase)
                return
            }

            var ea : Long? = null
            var eah : Int = Int.MAX_VALUE
            synchronized(earliestActivityHeight) {
                ea = earliestActivity
                eah = earliestActivityHeight
            }
            if ((ea == null)&&(oked == 0))
            {
                oked +=1
                laterUI {
                    ui.GuiNewAccountError.text = i18n(R.string.creatingNoHistoryAccountWarning)
                    ui.GuiStatusOk.setImageResource(android.R.drawable.ic_delete)
                }
                displayNotice(R.string.areYouSure)
                return
            }
            val cleanedSecretWords = words.joinToString(" ")
            wallyApp!!.recoverAccount(name, flags, pin, cleanedSecretWords, chainSelector, ea, eah.toLong(), nonstandardActivity)
            finish()
        }
    }


    fun onCreateAccount(@Suppress("UNUSED_PARAMETER") v: View?)
    {

        LogIt.info("Create account button")
        val secretWords = ui.GuiAccountRecoveryPhraseEntry.text.toString().trim()
        val chainName: String = ui.GuiBlockchainSelector.selectedItem.toString()
        val chainSelector = SupportedBlockchains[chainName]!!  // !! must work because I made the spinner from this map
        val name = ui.GuiAccountNameEntry.text.toString()
        val pin: String = ui.GuiPINEntry.text.toString()
        if (nameOk == false)
        {
            displayError(R.string.invalidAccountName)
            return
        }
        if (pinOk == false)
        {
            displayError(R.string.InvalidPIN)
            return
        }

        val flags: ULong = if (ui.PinHidesAccount.isChecked()) ACCOUNT_FLAG_HIDE_UNTIL_PIN else ACCOUNT_FLAG_NONE
        // Any time secret words are given, this is an account recovery -- we need to check for prior activity.
        // If there are no given secret words, this is a probabilistically completely new account
        if (secretWords.length > 0)
        {
            processingThread = thread(true, true, null, "newAccount") { recoverAccountPhase2(name, flags, pin, secretWords, chainSelector) }
        }
        else
        {
            later {
                wallyApp!!.newAccount(name, flags, pin, chainSelector)
                finish()
            }  // Can't happen in GUI thread
            // TODO some OK feedback

        }
    }
}
