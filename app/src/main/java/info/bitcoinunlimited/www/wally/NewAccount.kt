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
                    return
                }

                if (p0.length > 8)
                {
                    GuiAccountNameOk.setImageResource(android.R.drawable.ic_delete)
                    return
                }

                val proposedName = p0.toString()
                if (app?.accounts?.contains(proposedName) ?: false == true)
                {
                    GuiAccountNameOk.setImageResource(android.R.drawable.ic_delete)
                    return

                }
                GuiAccountNameOk.setImageResource(R.drawable.ic_check)
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
                if (p.isNullOrBlank())
                {
                    GuiRecoveryPhraseOk.setImageResource(R.drawable.ic_check)
                    return
                }

                val txt:String = p.toString()
                val words = txt.split(' ')
                if (words.size != 12)  // TODO support other size recovery keys
                {
                    GuiRecoveryPhraseOk.setImageResource(android.R.drawable.ic_delete)
                    return
                }
                val incorrectWords = Bip39InvalidWords(words)
                if (incorrectWords.size > 0)
                {
                    GuiRecoveryPhraseOk.setImageResource(android.R.drawable.ic_delete)
                    return
                }
                GuiRecoveryPhraseOk.setImageResource(R.drawable.ic_check)
            }

            override fun beforeTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {
            }

            override fun onTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {
            }
        })

    }

    fun recoverAccountPhase2(name: String, secretWords: String, chainSelector: ChainSelector)
    {
        val passphrase = ""  // TODO
        val secretSize = 64
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

            //val wallet = Bip44Wallet(GuiAccountNameEntry.text.toString(), SupportedBlockchains[chainName]!!, secret)

            /*
            // TODO seed only works for mainnet
            val ec = ElectrumClient(chainSelector, "electrumserver.seed.bitcoinunlimited.net")

            //val dest = runBlocking { wallet.newDestination() }

            val secret = GenerateBip39Seed(words.joinToString(" ") { it }, passphrase, secretSize)

            var index = 0
            val addressSecret = AddressDerivationKey.Hd44DeriveChildKey(secret, AddressDerivationKey.BIP43, AddressDerivationKey.ANY, AddressDerivationKey.hardened(0), 0, index)
            val dest = Pay2PubKeyHashDestination(chainSelector, addressSecret)

            val hist = ec.getHistory(dest.outputScript(), 30000)

            LogIt.info(hist.size.toString())
             */

            app!!.recoverAccount(name, secretWords, chainSelector )
            finish()
        }

    }

    fun onCreateAccount(v: View?)
    {
        LogIt.info("Create account button")
        val secretWords = GuiAccountRecoveryPhraseEntry.text.toString()
        val chainName: String = GuiBlockchainSelector.selectedItem.toString()
        val chainSelector = SupportedBlockchains[chainName]!!  // !! must work because I made the spinner from this map
        val name = GuiAccountNameEntry.text.toString()
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
