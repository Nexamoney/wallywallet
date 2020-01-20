package info.bitcoinunlimited.www.wally


import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.ArrayAdapter
import android.widget.MultiAutoCompleteTextView
import android.widget.MultiAutoCompleteTextView.Tokenizer
import android.widget.MultiAutoCompleteTextView.CommaTokenizer
import bitcoinunlimited.libbitcoincash.*
import kotlinx.android.synthetic.main.activity_new_account.*
import kotlinx.coroutines.runBlocking
import java.util.logging.Logger
import kotlin.concurrent.thread

private val LogIt = Logger.getLogger("bitcoinunlimited.NewAccount")

class CharTokenizer(val separator:Char):Tokenizer
{

    // Returns the end of the token (minus trailing punctuation) that begins at offset cursor within text.
    override fun findTokenEnd(cs: CharSequence?, pos: Int): Int
    {
        if (cs == null) return pos
        var curPos = pos
        while(curPos<cs.length)
        {
            if (cs[curPos] == separator) return curPos-1
            curPos++
        }
        return curPos-1
    }

    // Returns the start of the token that ends at offset cursor within text.
    override fun findTokenStart(cs: CharSequence?, pos: Int): Int
    {
        if (cs==null) return pos
        var curPos = pos-1
        while(curPos>0)
        {
            if (cs[curPos] == separator) return curPos+1
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

class NewAccount : CommonActivity()
{
    override var navActivityId = R.id.navigation_home

    var app: WallyApp? = null
    var recoveryChange = 0

    var processingThread: Thread? = null
    var lock = ThreadCond()

    var nameOk = false

    override fun onCreate(savedInstanceState: Bundle?)
    {
        app = (getApplication() as WallyApp)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_new_account)

        val blockchains = ArrayAdapter(this, android.R.layout.simple_spinner_item, SupportedBlockchains.keys.toTypedArray())
        GuiBlockchainSelector?.setAdapter(blockchains)

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

            override fun beforeTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {
            }

            override fun onTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {
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
                val txt:String = p.toString()
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
                  peekActivity(words.joinToString(" "), SupportedBlockchains[GuiBlockchainSelector.selectedItem]!!)
                }

            }

            override fun beforeTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {
            }

            override fun onTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {
            }
        })

    }

    fun peekActivity(secretWords:String, chainSelector: ChainSelector)
    {
        val (svr, port) = try
        {
            ElectrumServerOn(chainSelector)
        }
        catch (e:BadCryptoException)
        {
            LogIt.info ("peek not supported for this blockchain")
            return
        }

        val ec = try {
            ElectrumClient(chainSelector, svr, port)
        }
        catch(e:java.net.ConnectException)
        {
            laterUI {
                GuiNewAccountStatus.text = i18n(R.string.ElectrumNetworkUnavailable)
            }
            return
        }
        ec.start()

        val features = ec.features()

        val passphrase = "" // TODO: support a passphrase
        val secret = GenerateBip39Seed(secretWords, passphrase)

        var index = 0
        val addressDerivationCoin = Bip44AddressDerivationByChain(chainSelector)
        val newSecret = AddressDerivationKey.Hd44DeriveChildKey(secret, AddressDerivationKey.BIP43, addressDerivationCoin, 0, 0, index)

        val dest = Pay2PubKeyHashDestination(chainSelector, newSecret)  // Note, if multiple destination types are allowed, the wallet load/save routines must be updated
        //LogIt.info(sourceLoc() + " " + name + ": New Destination " + tmp.toString() + ": " + dest.address.toString())

        val use = ec.getFirstUse(dest.outputScript(), 10000)
        if (use.block_hash != null)
        {
            var dateInfo:String = ""
            if (use.block_height != null)
            {
                val headerBin = ec.getHeader(use.block_height!!)
                val blkHeader = BlockHeader(BCHserialized(headerBin, SerializationType.HASH))
                dateInfo = i18n(R.string.FirstUseDateHeightInfo) % mapOf("date" to epochToDate(blkHeader.time), "height" to use.block_height!!.toString())
            }
            laterUI {
                GuiNewAccountStatus.text = i18n(R.string.Bip44ActivityNotice) + " " + dateInfo
            }
            LogIt.info("found activity")
        }
        else
        {
            laterUI {
                GuiNewAccountStatus.text = i18n(R.string.NoBip44ActivityNotice)
            }
            LogIt.info("didn't find activity")
        }

        //LogIt.info(hist.size.toString())

    }

    fun recoverAccountPhase2(name: String, secretWords: String, chainSelector: ChainSelector)
    {
        val passphrase = ""  // TODO
        // val secretSize = 64
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

            app!!.recoverAccount(name, secretWords, chainSelector )
            finish()
        }

    }


    fun onCreateAccount(@Suppress("UNUSED_PARAMETER") v: View?)
    {
        LogIt.info("Create account button")
        val secretWords = GuiAccountRecoveryPhraseEntry.text.toString()
        val chainName: String = GuiBlockchainSelector.selectedItem.toString()
        val chainSelector = SupportedBlockchains[chainName]!!  // !! must work because I made the spinner from this map
        val name = GuiAccountNameEntry.text.toString()
        if (nameOk == false)
        {
            displayError(R.string.invalidAccountName)
            return
        }

        if (secretWords.length > 0)
        {
            processingThread = thread(true, true, null, "newAccount") { recoverAccountPhase2(name, secretWords, chainSelector ) }
        }
        else
        {

            later { app!!.newAccount(name, chainSelector); finish() }  // Can't happen in GUI thread
            // TODO some OK feedback

        }
    }
}
