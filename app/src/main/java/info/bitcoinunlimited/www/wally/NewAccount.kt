package info.bitcoinunlimited.www.wally


import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.ArrayAdapter
import android.widget.MultiAutoCompleteTextView
import android.widget.MultiAutoCompleteTextView.Tokenizer
import bitcoinunlimited.libbitcoincash.*
import kotlinx.android.synthetic.main.activity_new_account.*
import java.util.logging.Logger
import kotlin.concurrent.thread

private val LogIt = Logger.getLogger("BU.wally.NewAccount")

//* how many addresses to search in a particular derivation path
val DERIVATION_PATH_SEARCH_DEPTH = 10

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

    var app: WallyApp? = null
    var recoveryChange = 0

    var earliestActivity: Long? = 1577836800 // TODO support entry of recovery date

    val nonstandardActivity = mutableListOf<Pair<Bip44Wallet.HdDerivationPath, HDActivityBracket>>()

    var processingThread: Thread? = null
    var lock = ThreadCond()

    var nameOk = false
    var pinOk = true

    override fun onCreate(savedInstanceState: Bundle?)
    {
        app = (getApplication() as WallyApp)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_new_account)

        val blockchains = ArrayAdapter(this, android.R.layout.simple_spinner_item, SupportedBlockchains.keys.toTypedArray())
        GuiBlockchainSelector?.setAdapter(blockchains)
        GuiBlockchainSelector?.setSelection("NEXA")

        val adapter: ArrayAdapter<String> = ArrayAdapter<String>(this, android.R.layout.simple_dropdown_item_1line, englishWordList)
        val textView = findViewById<MultiAutoCompleteTextView>(R.id.GuiAccountRecoveryPhraseEntry)
        textView.setAdapter(adapter)
        textView.setTokenizer(CharTokenizer(' '))

        GuiBlockchainOk.setImageResource(R.drawable.ic_check)
        GuiRecoveryPhraseOk.setImageResource(R.drawable.ic_check)  // empty recovery phrase is valid (means create a new one)

        GuiAccountNameEntry.addTextChangedListener(object : TextWatcher
        {
            override fun afterTextChanged(p0: Editable?)
            {
                dbgAssertGuiThread()
                if (p0.isNullOrBlank())
                {
                    GuiAccountNameOk.setImageResource(android.R.drawable.ic_delete)
                    nameOk = false
                    return
                }

                if (p0.length > 8)
                {
                    GuiAccountNameOk.setImageResource(android.R.drawable.ic_delete)
                    nameOk = false
                    return
                }

                val proposedName = p0.toString()
                if (app?.accounts?.contains(proposedName) ?: false == true)
                {
                    GuiAccountNameOk.setImageResource(android.R.drawable.ic_delete)
                    nameOk = false
                    return

                }
                GuiAccountNameOk.setImageResource(R.drawable.ic_check)
                nameOk = true
            }

            override fun beforeTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int)
            {
            }

            override fun onTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int)
            {
            }
        })

        GuiPINEntry.addTextChangedListener(object : TextWatcher
        {
            override fun afterTextChanged(p0: Editable?)
            {
                dbgAssertGuiThread()
                if (p0.isNullOrBlank())
                {
                    GuiPINOk.setImageResource(R.drawable.ic_check)
                    PinProtectsSpending.text = i18n(R.string.PinSpendingUnprotected)
                    pinOk = true
                    return
                }

                PinProtectsSpending.text = i18n(R.string.PinSpendingProtected)
                if (p0.length < 4)
                {
                    GuiPINOk.setImageResource(android.R.drawable.ic_delete)
                    pinOk = false
                    return
                }

                GuiPINOk.setImageResource(R.drawable.ic_check)
                pinOk = true
            }

            override fun beforeTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int)
            {
            }

            override fun onTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int)
            {
            }
        })


        GuiAccountRecoveryPhraseEntry.addTextChangedListener(object : TextWatcher
        {
            override fun afterTextChanged(p: Editable?)
            {
                recoveryChange++
                // TODO: explain the problem
                dbgAssertGuiThread()

                // clear the status for regeneration if the phrase is ok
                GuiNewAccountStatus.text = ""
                GuiNewAccountError.text = ""

                // If Recovery phrase is blank we'll generate one so that's OK
                if (p.isNullOrBlank())
                {
                    GuiRecoveryPhraseOk.setImageResource(R.drawable.ic_check)
                    return
                }

                // Check recovery phrase validity and be unhappy if its not good
                val txt: String = p.toString().trim()
                val words = txt.split(' ')
                if (words.size < 12)  // TODO support other size recovery keys
                {
                    GuiRecoveryPhraseOk.setImageResource(android.R.drawable.ic_delete)
                    GuiNewAccountError.text = i18n(R.string.NotEnoughRecoveryWords)
                    return
                }
                if (words.size > 12)  // TODO support other size recovery keys
                {
                    GuiRecoveryPhraseOk.setImageResource(android.R.drawable.ic_delete)
                    GuiNewAccountError.text = i18n(R.string.TooManyRecoveryWords)
                    return
                }
                val incorrectWords = Bip39InvalidWords(words)
                if (incorrectWords.size > 0)
                {
                    GuiRecoveryPhraseOk.setImageResource(android.R.drawable.ic_delete)
                    GuiNewAccountError.text = i18n(R.string.IncorrectRecoveryWords) % mapOf("words" to incorrectWords.joinToString(","))
                    return
                }
                GuiRecoveryPhraseOk.setImageResource(R.drawable.ic_check)

                // If the recovery phrase is good, let's peek at the blockchain to see if there's activity
                thread(true, true, null, "peekWallet")
                {
                    try
                    {
                        peekActivity(words.joinToString(" "), SupportedBlockchains[GuiBlockchainSelector.selectedItem]!!)
                    } catch (e: Exception)
                    {
                        LogIt.severe("wallet peek error: " + e.toString())
                    }
                }

            }

            override fun beforeTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int)
            {
            }

            override fun onTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int)
            {
            }
        })

    }


    fun searchActivity(ec: ElectrumClient, chainSelector: ChainSelector, count: Int, secretDerivation: (Int) -> ByteArray): Pair<Long, Int>?
    {
        var index = 0
        while (index < count)
        {
            val newSecret = secretDerivation(index)

            val dest = Pay2PubKeyHashDestination(chainSelector, UnsecuredSecret(newSecret))  // Note, if multiple destination types are allowed, the wallet load/save routines must be updated
            //LogIt.info(sourceLoc() + " " + name + ": New Destination " + tmp.toString() + ": " + dest.address.toString())

            try
            {
                val use = ec.getFirstUse(dest.outputScript(), 10000)
                if (use.block_hash != null)
                {
                    if (use.block_height != null)
                    {
                        val headerBin = ec.getHeader(use.block_height!!)
                        val blkHeader = BlockHeader(BCHserialized(headerBin, SerializationType.HASH))
                        return Pair(blkHeader.time, use.block_height!!)
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
        return null
    }

    // Note that this returns the last time and block when a new address was FIRST USED, so this may not be what you wanted
    data class HDActivityBracket(val startTime: Long, val startBlockHeight: Int, val lastTime: Long, val lastBlockHeight: Int, val lastAddressIndex: Int)

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

            val dest = Pay2PubKeyHashDestination(chainSelector, UnsecuredSecret(newSecret))  // Note, if multiple destination types are allowed, the wallet load/save routines must be updated
            //LogIt.info(sourceLoc() + " " + name + ": New Destination " + tmp.toString() + ": " + dest.address.toString())

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
            val blkHeader = BlockHeader(BCHserialized(headerBin, SerializationType.HASH))
            startTime = blkHeader.time
        }
        if (true)
        {
            val headerBin = ec.getHeader(lastBlock)
            val blkHeader = BlockHeader(BCHserialized(headerBin, SerializationType.HASH))
            lastTime = blkHeader.time
        }

        return HDActivityBracket(startTime, startBlock, lastTime, lastBlock, lastFoundIndex)
    }


    fun peekActivity(secretWords: String, chainSelector: ChainSelector)
    {
        laterUI {
            GuiNewAccountStatus.text = i18n(R.string.NewAccountSearchingForTransactions)
        }

        val (svr, port) = try
        {
            ElectrumServerOn(chainSelector)
        } catch (e: BadCryptoException)
        {
            LogIt.info("peek not supported for this blockchain")
            return
        }

        val ec = try
        {
            ElectrumClient(chainSelector, svr, port)
        } catch (e: java.io.IOException) // covers java.net.ConnectException, UnknownHostException and a few others that could trigger
        {
            try
            {
                if (chainSelector == ChainSelector.BCH)
                    ElectrumClient(chainSelector, LAST_RESORT_BCH_ELECTRS)
                else throw e
            } catch (e: java.io.IOException)
            {
                laterUI {
                    GuiNewAccountStatus.text = i18n(R.string.ElectrumNetworkUnavailable)
                }
                return
            }
        }
        ec.start()

        // val features = ec.features()

        val passphrase = "" // TODO: support a passphrase
        val secret = GenerateBip39Seed(secretWords, passphrase)

        val addressDerivationCoin = Bip44AddressDerivationByChain(chainSelector)

        var earliestActivityP =
          searchActivity(ec, chainSelector, DERIVATION_PATH_SEARCH_DEPTH, { AddressDerivationKey.Hd44DeriveChildKey(secret, AddressDerivationKey.BIP44, addressDerivationCoin, 0, 0, it) })

        val Bip44Msg = if (earliestActivityP != null)
        {
            earliestActivity = earliestActivityP.first - 1 // -1 so earliest activity is just before the activity
            i18n(R.string.Bip44ActivityNotice) + " " + i18n(R.string.FirstUseDateHeightInfo) % mapOf(
              "date" to epochToDate(earliestActivityP.first),
              "height" to earliestActivityP.second.toString()
            )
        }
        else i18n(R.string.NoBip44ActivityNotice)

        // Look in non-standard places for activity
        val BTCactivity =
          bracketActivity(ec, chainSelector, DERIVATION_PATH_SEARCH_DEPTH, { AddressDerivationKey.Hd44DeriveChildKey(secret, AddressDerivationKey.BIP44, AddressDerivationKey.BTC, 0, 0, it) })
        var BTCchangeActivity: HDActivityBracket?
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

        laterUI { GuiNewAccountStatus.text = Bip44Msg + "\n" + Bip44BTCMsg }
    }

    fun recoverAccountPhase2(name: String, flags: ULong, pin: String, secretWords: String, chainSelector: ChainSelector)
    {
        // TODO Bip39 passphrase support
        if (secretWords.length > 0)
        {
            val words = secretWords.split(' ')
            if (words.size != 12)
            {
                laterUI { GuiRecoveryPhraseOk.setImageResource(android.R.drawable.ic_delete) }
                displayError(R.string.invalidRecoveryPhrase)
                return
            }
            val incorrectWords = Bip39InvalidWords(words)
            if (incorrectWords.size > 0)
            {
                laterUI { GuiRecoveryPhraseOk.setImageResource(android.R.drawable.ic_delete) }
                displayError(R.string.invalidRecoveryPhrase)
                return
            }
            app!!.recoverAccount(name, flags, pin, secretWords, chainSelector, earliestActivity, nonstandardActivity)
            finish()
        }
    }


    fun onCreateAccount(@Suppress("UNUSED_PARAMETER") v: View?)
    {
        LogIt.info("Create account button")
        val secretWords = GuiAccountRecoveryPhraseEntry.text.toString().trim()
        val chainName: String = GuiBlockchainSelector.selectedItem.toString()
        val chainSelector = SupportedBlockchains[chainName]!!  // !! must work because I made the spinner from this map
        val name = GuiAccountNameEntry.text.toString()
        val pin: String = GuiPINEntry.text.toString()
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

        val flags: ULong = if (PinHidesAccount.isChecked()) ACCOUNT_FLAG_HIDE_UNTIL_PIN else ACCOUNT_FLAG_NONE
        if (secretWords.length > 0)
        {
            processingThread = thread(true, true, null, "newAccount") { recoverAccountPhase2(name, flags, pin, secretWords, chainSelector) }
        }
        else
        {
            later {
                app!!.newAccount(name, flags, pin, secretWords, chainSelector)
                finish()
            }  // Can't happen in GUI thread
            // TODO some OK feedback

        }
    }
}
