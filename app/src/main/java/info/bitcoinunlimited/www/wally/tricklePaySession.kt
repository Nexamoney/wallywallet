package info.bitcoinunlimited.www.wally
import android.content.ClipData
import android.content.ClipboardManager
import android.net.Uri
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat.getSystemService
import androidx.core.net.toUri
import java.util.logging.Logger
import bitcoinunlimited.libbitcoincash.*
import io.ktor.client.*
import io.ktor.client.engine.android.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json
import java.net.SocketTimeoutException
import java.net.URL
import java.net.URLEncoder
import kotlin.random.Random

private val LogIt = Logger.getLogger("BU.wally.tpsess")

val TDPP_URI_SCHEME = "tdpp"
val TDPP_DEFAULT_UOA = "NEX"
val TDPP_DEFAULT_PROTOCOL = "http"

const val TDPP_FLAG_NOFUND = 1
const val TDPP_FLAG_NOPOST = 2
const val TDPP_FLAG_NOSHUFFLE = 4
const val TDPP_FLAG_PARTIAL = 8
const val TDPP_FLAG_FUND_GROUPS = 16

fun makeChallengeTx(sp: Spendable, challengerId: ByteArray, chalby: ByteArray): iTransaction?
{
    if (chalby.size < 8 || chalby.size > 64) return null
    val rb = Random.nextBytes(chalby.size)
    val moddedChal = ByteArray(chalby.size * 2)
    for (i in 0 until chalby.size)
    {
        moddedChal[i * 2] = rb[i]
        moddedChal[(i * 2) + 1] = chalby[i]
    }
    val cs = sp.chainSelector
    val tx = txFor(cs)
    tx.add(txInputFor(sp))
    tx.add(txOutputFor(cs, 0, SatoshiScript(cs, SatoshiScript.Type.SATOSCRIPT, OP.RETURN, OP.push(challengerId), OP.push(moddedChal))))
    (tx as NexaTransaction).version = OWNERSHIP_CHALLENGE_VERSION_MASK
    signTransaction(tx)
    return tx
}

// Structured data type to make it cleaner to return tx analysis data from the analysis function.
// otherInputSatoshis: BCH being brought into this transaction by other participants
data class TxAnalysisResults(
  val account : Account,
  val receivingSats: Long,  // I am receiving this amount of satoshis (NOT the net amount: use receivingSats - myInputSatoshis to determine that)
  val sendingSats: Long,
  val receivingTokenTypes: Long,
  val sendingTokenTypes: Long,
  val imSpendingTokenTypes: Long,  // The tx spends this number of token TYPES currently controlled by this wallet
  val otherInputSatoshis: Long?,  // If this is null, I'm not funding this tx (its likely a partial tx)
  val myInputSatoshis: Long,

  val myInputTokenInfo: Map<GroupId, Long>,  // This wallet inputting these tokens into this transaction
  val sendingTokenInfo: Map<GroupId, Long>,  // This wallet is sending these tokens to another wallet
  val receivingTokenInfo: Map<GroupId, Long>,  // This wallet is receiving these tokens
  val myNetTokenInfo: Map<GroupId, Long>,  // If < 0 this wallet is spending these tokens.  If > 0 this wallet is receiving tokens.  If == 0 (verses undefined) the wallet presented (sent to itself) the token type
  val completionException: java.lang.Exception?
)


/* Information about payment delegations that have been accepted by the user
* To be stored/retrieved */
enum class TdppAction(val v: Byte)
{
    DENY(0),
    ASK(1),
    ACCEPT(2);

    companion object
    {
        fun of(v: Byte): TdppAction
        {
            return when (v)
            {
                DENY.v -> DENY
                ASK.v -> ASK
                ACCEPT.v -> ACCEPT
                else -> throw DeserializationException("Deserialization error in Trickle Pay data")
            }
        }
    }

    operator fun inc(): TdppAction
    {
        if (this == DENY) return ASK
        if (this == ASK) return ACCEPT
        if (this == ACCEPT) return DENY
        else return ASK
    }

    override fun toString(): String
    {
        if (this == DENY) return i18n(R.string.deny)
        if (this == ASK) return i18n(R.string.ask)
        if (this == ACCEPT) return i18n(R.string.accept)
        return ""
    }
}

data class TdppDomain(
  @cli(Display.Simple, "Address of entity") var domain: String,
  @cli(Display.Simple, "Topic") var topic: String,
  @cli(Display.Simple, "Signing address") var addr: String,
  @cli(Display.Simple, "Currency") var uoa: String,
  // These are stored in the finest unit of the unit of account of the currency, i.e. Satoshis or cents.
  // But OFC they should be displayed in a more reasonable unit
  @cli(Display.Simple, "Maximum automatic payment") var maxper: Long,
  @cli(Display.Simple, "Maximum automatic per day") var maxday: Long,
  @cli(Display.Simple, "Maximum automatic per week") var maxweek: Long,
  @cli(Display.Simple, "Maximum automatic per month") var maxmonth: Long,
  var descper: String,
  var descday: String,
  var descweek: String,
  var descmonth: String,
  @cli(Display.Simple, "enable/disable all automatic payments to this entity") var automaticEnabled: Boolean,
  @cli(Display.Simple, "Single payment address") var mainPayAddress: String,
  @cli(Display.Simple, "Last payment address") var lastPayAddress: String,
  @cli(Display.Simple, "Associated account") var accountName: String,

) : BCHserializable
{
    @cli(Display.Simple, "Maximum automatic payment exeeded action")
    var maxperExceeded: TdppAction = TdppAction.ASK

    @cli(Display.Simple, "Maximum automatic payment per day exeeded action")
    var maxdayExceeded: TdppAction = TdppAction.ASK

    @cli(Display.Simple, "Maximum automatic payment per week exeeded action")
    var maxweekExceeded: TdppAction = TdppAction.ASK

    @cli(Display.Simple, "Maximum automatic payment per month exeeded action")
    var maxmonthExceeded: TdppAction = TdppAction.ASK

    @cli(Display.Simple, "Asset information query")
    var assetInfo: TdppAction = TdppAction.ASK

    @cli(Display.Simple, "Balance information query")
    var balanceInfo: TdppAction = TdppAction.ASK

    constructor(uri: Uri) : this("", "", "", "", -1, -1, -1, -1, "", "", "", "", false,"","","")
    {
        load(uri)
    }

    // This is the implicit registration constructor -- it does not authorize any automatic payments.
    constructor(_domain: String, _topic: String) : this(_domain, _topic, "", "", 0, 0, 0, 0, "", "", "", "", false, "","","")
    {
    }

    constructor(stream: BCHserialized) : this("", "", "", "", -1, -1, -1, -1, "", "", "", "", false,"","","")
    {
        BCHdeserialize(stream)
    }

    override fun BCHserialize(format: SerializationType): BCHserialized //!< Serializer
    {
        return BCHserialized(format) + domain + topic + addr + uoa +
          maxper + maxday + maxweek + maxmonth +
          descper + descday + descweek + descmonth +
          automaticEnabled +
          maxperExceeded.v + maxdayExceeded.v + maxweekExceeded.v + maxmonthExceeded.v +
          assetInfo.v + balanceInfo.v +
          mainPayAddress + lastPayAddress + accountName
    }

    override fun BCHdeserialize(stream: BCHserialized): BCHserialized //!< Deserializer
    {
        domain = stream.deString()
        topic = stream.deString()
        addr = stream.deString()
        uoa = stream.deString()

        maxper = stream.deint64()
        maxday = stream.deint64()
        maxweek = stream.deint64()
        maxmonth = stream.deint64()

        descper = stream.deString()
        descday = stream.deString()
        descweek = stream.deString()
        descmonth = stream.deString()

        automaticEnabled = stream.deboolean()

        maxperExceeded = TdppAction.of(stream.debyte())
        maxdayExceeded = TdppAction.of(stream.debyte())
        maxweekExceeded = TdppAction.of(stream.debyte())
        maxmonthExceeded = TdppAction.of(stream.debyte())

        assetInfo = TdppAction.of(stream.debyte())
        balanceInfo = TdppAction.of(stream.debyte())

        mainPayAddress = stream.deString()
        lastPayAddress = stream.deString()
        accountName = stream.deString()

        return stream
    }

    fun getParam(u: Uri, amtP: String, descP: String): Pair<Long, String>
    {
        val amount: Long = u.getQueryParameter(amtP).let {
            if (it == null) -1
            else it.toLong()
        }
        val desc: String = u.getQueryParameter(descP) ?: ""
        return Pair(amount, desc)
    }

    /* pick the bigger of what I've chosen and what is requested by the URI */
    fun merge(uri: Uri)
    {
        domain = uri.getHost() ?: throw NotUriException()
        topic = uri.getQueryParameter("topic") ?: ""
        addr = uri.getQueryParameter("addr") ?: ""
        getParam(uri, "maxper", "descper").let { if (maxper < it.first) maxper = it.first; descper = it.second }
        getParam(uri, "maxday", "descday").let { if (maxday < it.first) maxday = it.first; descday = it.second }
        getParam(uri, "maxweek", "descweek").let { if (maxweek < it.first) maxweek = it.first; descweek = it.second }
        getParam(uri, "maxmonth", "descmonth").let { if (maxmonth < it.first) maxmonth = it.first; descmonth = it.second }
    }

    fun load(uri: Uri)
    {
        domain = uri.getHost() ?: throw NotUriException()
        topic = uri.getQueryParameter("topic") ?: ""
        addr = uri.getQueryParameter("addr") ?: ""
        getParam(uri, "maxper", "descper").let { maxper = it.first; descper = it.second }
        getParam(uri, "maxday", "descday").let { maxday = it.first; descday = it.second }
        getParam(uri, "maxweek", "descweek").let { maxweek = it.first; descweek = it.second }
        getParam(uri, "maxmonth", "descmonth").let { maxmonth = it.first; descmonth = it.second }
    }
}

class TricklePayDomains(val app: WallyApp)
{
    val SER_VERSION: Byte = 1.toByte()

    var db: KvpDatabase? = null

    fun domainKey(host: String, topic: String? = null): String = host + "/" + (topic ?: "")
    var domains: MutableMap<String, TdppDomain> = mutableMapOf()
    var domainsLoaded: Boolean = false

    val size:Int
        get() = domains.size

    fun insert(d: TdppDomain)
    {
        domains[domainKey(d.domain, d.topic)] = d
        notInUI { save() }
    }

    fun remove(d: TdppDomain)
    {
        domains.remove(domainKey(d.domain, d.topic))
    }

    fun clear()
    {
        domains.clear()
    }

    /** Load domain if it exists or create it */
    fun loadCreateDomain(host: String, topic:String): TdppDomain
    {
        return synchronized(domains)
        {
            if (!domainsLoaded) load()
            var d = domains[domainKey(host, topic)]
            if (d == null)
            {
                // delay and try again because load() cannot happen in the gui thread
                d = TdppDomain(host, topic)
                domains[domainKey(host, topic)] = d
                save()
            }
            d
        }
    }

    /** Load domain if it exists or create it */
    fun loadDomain(host: String, topic:String): TdppDomain?
    {
        return synchronized(domains)
        {
            if (!domainsLoaded) load()
            var d = domains[domainKey(host, topic)]
            d
        }
    }

    fun load()
    {
        notInUI {
            synchronized(domains)
            {
                if (db == null)
                {
                    val ctxt = PlatformContext(app)
                    db = OpenKvpDB(ctxt, "wallyData")
                }

                db?.let {
                    try
                    {
                        val ser = it.get("tdppDomains")
                        if (ser.size != 0) // No data saved
                        {
                            val bchser = BCHserialized(ser, SerializationType.DISK)
                            val ver = bchser.debytes(1)[0]
                            if (ver == SER_VERSION)
                                domains = bchser.demap({ it.deString() }, { TdppDomain(it) })
                        }
                    } catch (e: DataMissingException)
                    {
                        domainsLoaded = true
                        LogIt.info("benign: no TDPP domains registered yet")
                    } catch (e: Exception)
                    {
                        domainsLoaded = true  // well we tried anyway
                        logThreadException(e, "TDPP domain data corruption (or updated)", sourceLoc())
                    }
                }
                domainsLoaded = true
            }
        }
    }

    fun save()
    {
        notInUI {
            synchronized(domains)
            {
                if (domainsLoaded)  // If we save the domains before we load them, we'll erase them!
                {
                    val ser = BCHserialized.uint8(SER_VERSION)
                    ser.add(BCHserialized.map(domains,
                      {
                          BCHserialized(SerializationType.DISK).add(it)
                      },
                      {
                          it.BCHserialize()
                      }, SerializationType.DISK))
                    db?.let {
                        it.set("tdppDomains", ser.flatten())
                    }
                }
            }
        }
    }
}


fun VerifyTdppSignature(uri: Uri, addressParam:String? = null): Boolean?
{
    val addressStr = if ((addressParam==null) || (addressParam=="")) uri.getQueryParameter("addr") else addressParam
    if (addressStr == null) return null
    val sig = uri.getQueryParameter("sig")
    if (sig == null) return null

    // recast the URI into one with the parameters in the proper order, and no sig
    val suri = Uri.Builder()
    suri.scheme(uri.scheme)
    suri.encodedAuthority(uri.authority)
    suri.path(uri.path)
    val orderedParams = uri.queryParameterNames.toList().sorted()
    val queryParam = mutableListOf<String>()
    for (p in orderedParams)
    {
        if (p == "sig") continue
        val tmp = uri.getQueryParameter(p)
        val tmp2 = URLEncoder.encode(tmp, "utf-8")
        queryParam.add(p + "=" + tmp2)
        // this does normal URL encoding (e.g. %20 for space) not form encoding (e.g. + for space).  But we need form encoding
        //suri.appendQueryParameter(p, uri.getQueryParameter(p))
    }
    suri.encodedQuery(queryParam.joinToString("&"))

    val verifyThis = suri.build().toString()

    val pa = PayAddress(addressStr)

    LogIt.info("verification for: " + verifyThis + " Address: " + addressStr)
    LogIt.info("Message hex: " + verifyThis.toByteArray().toHex())
    LogIt.info("Raw Address: " + pa.data.toHex())
    val sigBytes = try
    {
        Codec.decode64(sig)
    } catch (e: IllegalStateException)
    {
        LogIt.info("Verification failed for: " + verifyThis + " Address: " + addressStr + " Cannot decode64 sig.")
        return false
    }
    LogIt.info("Sig: " + sigBytes.toHex())
    val result = Wallet.verifyMessage(verifyThis.toByteArray(), pa.data, sigBytes)
    if (result == null || result.size == 0)
    {
        LogIt.info("verification failed for: " + verifyThis + " Address: " + addressStr)
        return false
    }
    LogIt.info("verification good for: " + verifyThis + " Address: " + addressStr)
    return true
}

class TricklePaySession(val tpDomains: TricklePayDomains)
{
    var accepted: Boolean = false  // did the user accept this payment request
    var newDomain: Boolean = false // was a domain created during this session?
    var proposalUri: Uri? = null
    var host: String? = null
    var port: Int = 80
    var topic: String? = null
    var sigOk: Boolean? = null  // null means no sig was supplied
    var cookie: String? = null
    var reason: String? = null
    var rproto: String? = null
    var chainSelector: ChainSelector? = null
    var proposedDestinations: List<Pair<PayAddress, Long>>? = null
    val askReasons = mutableListOf<String>()

    var tflags: Int = 0  // tx flags
    var proposedTx: iTransaction? = null
    var proposalAnalysis: TxAnalysisResults? = null
    var assetInfoList:TricklePayAssetList? = null

    var totalNexaSpent: Long = 0  // How much does this proposal spend in nexa satoshis

    var uniqueAddress: Boolean = false

    var domain: TdppDomain? = null

    val isSecureRequest:Boolean
        get()
        {
            // If a signature was provided, then that decides it.
            sigOk?.let { return it }
            // Otherwise look at the protocol
            if (proposalUri?.scheme == "https") return true
            return false
        }

    // produce the correct reply protocol (without the :)
    val replyProtocol:String
        get()
        {
            rproto?.let {return it }
            proposalUri?.let {
                val scheme = it.scheme
                if ((scheme!=null)&&(scheme != TDPP_URI_SCHEME)) return scheme
            }
            return TDPP_DEFAULT_PROTOCOL
        }

    val domainAndTopic: String
        get() {
            val d = domain?.domain ?: ""
            val t = topic ?: domain?.topic ?: ""
            return d + (if (t != "") (":" + t) else "")
        }

    val hostAndPort:String
        get()
        {
            val h = host
            val p = port
            if (h == null) throw TdppException(R.string.UnknownDomainRegisterFirst, "no domain specified")
            else if (p == -1)
                return h
            else return h + ":" + p
        }

    val cookieParam:String
        get()
        {
            val c = cookie
            return if (c != null) "cookie=$c" else ""
        }

    fun parseCommonFields(uri: Uri, autoCreateDomain: Boolean = true)
    {
        proposalUri = uri
        val h = uri.getHost()
        if (h == null) throw TdppException(R.string.UnknownDomainRegisterFirst, "no domain specified")
        host = h
        port = uri.port
        topic = uri.getQueryParameter("topic")
        cookie = uri.getQueryParameter("cookie")
        reason = uri.getQueryParameter("reason")

        val chain = uri.getQueryParameter("chain")
        if (chain != null)
        {
            chainSelector = uriToChain[chain]
            if (chainSelector == null)
            {
                throw BadCryptoException()
            }
        }

        tpDomains.load()
        if (autoCreateDomain)
            domain = tpDomains.loadCreateDomain(h,topic ?: "")
        else
            domain = tpDomains.loadDomain(h,topic ?: "")

        sigOk = VerifyTdppSignature(uri, domain?.addr)
    }

    fun getRelevantAccount(preferredAccount: String? = null): Account
    {
        if (preferredAccount != null && preferredAccount != "")  // Prefer the account associated with this domain
        {
            val act = wallyApp!!.accounts[preferredAccount]
            if (act != null) return act
        }

        // Get a handle on the relevant wallets
        var act = wallyApp!!.focusedAccount
        if (act != null)
        {
            if (chainSelector == null || act.chain.chainSelector == chainSelector) return act
        }

        act = wallyApp!!.primaryAccount
        if (act != null)
        {
            if (chainSelector == null || act.chain.chainSelector == chainSelector) return act
        }


        val walChoices = wallyApp!!.accountsFor(chainSelector ?: ChainSelector.NEXA)
        if (walChoices.size == 0)
        {
            throw WalletInvalidException()
        }

        // TODO associate an account with a trickle pay
        // For now, grab the first sorted
        val walSorted = walChoices.toList().sortedBy { it.name }
        act = walSorted[0]
        return act
    }


    /* Issue the sendto style transaction */
    fun acceptSendToRequest()
    {
        val act = getRelevantAccount()
        val wal = act.wallet
        val p = proposedDestinations

        if (p == null)
            return

        wal.send(p, false, i18n(R.string.title_activity_trickle_pay) + " " + domainAndTopic + ". " + reason)
        proposedDestinations = null
    }

    fun respondWith(url: String, postResp: String)
    {
        wallyApp?.later {
            LogIt.info("responding to server")
            val client = HttpClient(Android)
            {
                install(ContentNegotiation) {
                    json()
                }
                install(HttpTimeout) { requestTimeoutMillis = 5000 }
            }

            try
            {
                val response: HttpResponse = client.post(url) {
                    setBody(postResp)
                }
                val respText = response.bodyAsText()
                wallyApp?.displayNotice(R.string.accept, respText)
            }
            catch (e: SocketTimeoutException)
            {
                wallyApp?.displayError(R.string.connectionException)
            }
            client.close()
        }
    }

    fun acceptAddressRequest(): String
    {
        LogIt.info("accepted address request")
        val url = replyProtocol + "://" + hostAndPort + "/address?" + cookieParam

        val d = domain
        if (d == null) throw TdppException(R.string.BadLink, "bad domain")

        // Once you've associated an address with this domain, you've also associated an account!
        val acc = getRelevantAccount(d.accountName)
        val wal = acc.wallet

        // If requester wants a unique address just give one.  Otherwise give the main address (if we have one; if not, make one)
        val addr:String = if (uniqueAddress)
        {
            wal.getNewAddress().toString()
        }
        else
        {
            if (d.mainPayAddress != "") d.mainPayAddress
            else
            {
                val tmp = wal.getNewAddress()
                d.mainPayAddress = tmp.toString()
                tmp.toString()
            }
        }

        d.lastPayAddress = addr
        d.accountName = acc.name

        respondWith(url, addr)

        wallyApp?.later {  // Because I changed the lastPayAddress and maybe mainPayAddress
            tpDomains.save()
        }

        return "Sent to: " + url
    }

    fun acceptAssetRequest():String
    {
        LogIt.info("accepted asset request")
        val assets = assetInfoList ?: return "no assets"

        val url = replyProtocol + "://" + hostAndPort + "/assets?" + cookieParam

        wallyApp?.later {
            LogIt.info("responding to server")
            val client = HttpClient(Android)
            {
                install(ContentNegotiation) {
                    json()
                }
                install(HttpTimeout) { requestTimeoutMillis = 5000 }
            }

            val js = Json {}
            try
            {
                val response: HttpResponse = client.post(url) {
                    val tmp = js.encodeToString(TricklePayAssetList.serializer(), assets)
                    LogIt.info("JSON response ${tmp.length} : " + tmp.toString())
                    setBody(tmp)
                }
                val respText = response.bodyAsText()
                wallyApp?.displayNotice(R.string.accept, respText)
            }
            catch (e: SocketTimeoutException)
            {
                wallyApp?.displayError(R.string.connectionException)
            }
            client.close()
        }
        return "Sent to: " + url
    }


    fun acceptSpecialTx()
    {
        LogIt.info("accept trickle pay special transaction")
        accepted = true
        val pTx = proposedTx
        val panalysis = proposalAnalysis
        if ((pTx != null)&&(panalysis != null))
        {
            proposedTx = null
            proposalUri = null
            LogIt.info("sign trickle pay special transaction")

            // TODO: put a record of this transaction somewhere so when it is completed by the server we can annotate the history with a reason.
            // This is tricky because it may not be fully formed so idem will change.  Maybe look up by signature
            // tx reason: i18n(R.string.title_activity_trickle_pay) + " " + domainAndTopic + ". " + reason)

            wallyApp?.let { app ->
                // Post this transaction if the TDPP protocol suggests that I do so (its complete)
                if ((tflags and TDPP_FLAG_NOPOST) == 0) try
                    {
                        getRelevantAccount(domain?.accountName).wallet.send(pTx)
                    }
                    catch(e:Exception)  // Its possible that the tx is partial but the caller didn't set the bit, so if the tx is rejected ignore
                    {
                    }

                // And hand it back to the requester...
                // grab temps because activity could go away
                val rp = replyProtocol
                val hp = hostAndPort
                val cp = cookieParam
                app.later {
                    val req = URL(rp + "://" + hp + "/tx?tx=${pTx.toHex()}&${cp}")
                    LogIt.info("Sending special tx response: ${req}")
                    val data = try
                    {
                        req.readText()
                    }
                    catch (e: java.io.FileNotFoundException)
                    {
                        LogIt.info("Error submitting transaction: " + e.message)
                        app.displayError(R.string.WebsiteUnavailable)
                        return@later
                    }
                    catch (e: java.lang.Exception)
                    {
                        LogIt.info("Error submitting transaction: " + e.message)
                        app.displayError(R.string.WebsiteUnavailable)
                        return@later
                    }
                    LogIt.info(sourceLoc() + " TP response to the response: " + data)
                    app.displayNotice(R.string.TpTxAccepted)
                }
            }
        }
    }

    /* Based on this session, should I ACCEPT, ASK, or DENY? */
    fun determineAction(domain: TdppDomain?): TdppAction
    {
        if (domain == null) return TdppAction.DENY
        askReasons.clear()

        val pa = proposalAnalysis
        if (pa != null)
        {
            // If I'm sending tokens, always ask -- this is too complicated to do automatically right now
            // Clearly, any automatic spend must be based on the token type...
            if (pa.imSpendingTokenTypes > 0) return TdppAction.ASK
        }

        if (totalNexaSpent > domain.maxper)
        {
            if (domain.maxperExceeded == TdppAction.DENY)
            {
                return TdppAction.DENY
            }
            else askReasons.add(i18n(R.string.TpExceededMaxPer))
        }

        // TODO maxday, maxweek, maxmonth

        if (!domain.automaticEnabled) askReasons.add(i18n(R.string.TpAutomaticDisabled))

        if (askReasons.size == 0)
        {
            return TdppAction.ACCEPT
        }
        return TdppAction.ASK
    }

    /* Process a tx completion proposal, and decide what to do about it */
    fun handleTxAutopay(uri: Uri): TdppAction
    {
        proposalUri = uri
        proposedTx = null
        proposedDestinations = null

        val txHex = uri.getQueryParameter("tx")
        if (txHex == null)
        {
            throw TdppException(R.string.BadLink, "missing tx parameter")
        }

        parseCommonFields(uri)

        tflags = uri.getQueryParameter("flags")?.toInt() ?: 0

        // If we are funding then inputSatoshis must be provided
        val inputSatoshis = uri.getQueryParameter("inamt")?.toLongOrNull()  // ?: return displayError(R.string.BadLink)
        if ((inputSatoshis == null) && ((tflags and TDPP_FLAG_NOFUND) == 0))
        {
            throw TdppException(R.string.BadLink, "missing inamt parameter")
        }

        val tx = txFor(chainSelector!!, BCHserialized(txHex.fromHex(), SerializationType.NETWORK))
        LogIt.info(sourceLoc() + ": Tx to autopay: " + tx.toHex())

        // Analyze and sign transaction
        val analysis = analyzeCompleteAndSignTx(tx, inputSatoshis, tflags)
        LogIt.info(sourceLoc() + ": Completed tx: " + tx.toHex())
        proposedTx = tx  // save the final tx to be issued later if user agrees and no problems
        proposalAnalysis = analysis
        totalNexaSpent = analysis.myInputSatoshis - analysis.receivingSats
        var action = determineAction(domain)
        if (action == TdppAction.ACCEPT) action = TdppAction.ASK
        return action
    }

    fun handleSendToAutopay(uri: Uri): TdppAction
    {
        val addrAmt = mutableListOf<Pair<PayAddress, Long>>()
        var count = 0
        var total = 0L
        parseCommonFields(uri)
        if (!isSecureRequest) throw TdppException(R.string.ignoredInsecureRequest, "")
        while (true)
        {
            val amtS = uri.getQueryParameter("amt" + count.toString())
            val addrS = uri.getQueryParameter("addr" + count.toString())

            // if one exists but not the other, request is bad
            if ((amtS != null) xor (addrS != null))
            {
                throw DataMissingException(i18n(R.string.BadLink))
                // return displayError(R.string.BadLink, "missing parameter")
            }
            // if either do not exist, done
            if ((amtS == null) || (addrS == null)) break

            val amt = amtS.toLong()
            if (amt <= 0) throw BadAmountException(R.string.Amount)
            addrAmt.add(Pair(PayAddress(addrS), amt.toLong()))
            val priorTotal = total
            total += amt
            if (total < priorTotal)
            {
                throw BadAmountException(R.string.Amount)
            }  // amounts wrapped around
            count++
        }

        totalNexaSpent = total
        proposedDestinations = addrAmt
        return determineAction(domain)
    }

    fun handleAddressInfoRequest(uri: Uri): TdppAction
    {
        parseCommonFields(uri)
        val d = domain
        if (d == null) throw TdppException(R.string.BadLink, "bad domain")

        val bc = uri.getQueryParameter("blockchain") ?: chainToURI[ChainSelector.NEXA]
        chainSelector = uriToChain[bc]
        if (chainSelector == null) throw TdppException(R.string.BadLink, "unknown blockchain")

        uniqueAddress = uri.getQueryParameter("unique").toBoolean() ?: false

        return TdppAction.ACCEPT
    }

    fun handleAssetInfoRequest(uri: Uri): TdppAction
    {
        parseCommonFields(uri)
        val d = domain
        if (d == null) throw TdppException(R.string.BadLink, "bad domain")

        if (d.assetInfo == TdppAction.DENY) return TdppAction.DENY

        val scriptTemplateHex = uri.getQueryParameter("af")
        if (scriptTemplateHex == null)
        {
            throw TdppException(R.string.BadLink, "missing 'af' parameter")
        }

        val chalbyStr = uri.getQueryParameter("chalby")
        val chalby = chalbyStr?.fromHex()

        val stemplate = SatoshiScript(chainSelector!!, SatoshiScript.Type.SATOSCRIPT, scriptTemplateHex.fromHex())
        LogIt.info(sourceLoc() + ": Asset filter: " + stemplate.toHex())


        val acc = getRelevantAccount()
        val wal = acc.wallet
        val challengerId = host?.toByteArray()

        var matches = mutableListOf<TricklePayAssetInfo>()
        for ((outpoint, spendable) in wal.txos)
        {
            if (spendable.spentHeight < 0)  // unspent
            {
                val constraint = spendable.priorOutScript
                if (constraint.matches(stemplate, true) != null)
                {
                    val serPrevout = spendable.prevout.BCHserialize(SerializationType.NETWORK).toHex()
                    matches.add(
                      TricklePayAssetInfo(
                        outpoint.toHex(), spendable.amount, serPrevout,
                        if (chalby != null && challengerId != null) makeChallengeTx(spendable, challengerId, chalby)?.toHex() else null,
                      )
                    )
                }
            }
        }
        assetInfoList = TricklePayAssetList(matches)

        return d.assetInfo
    }

    fun handleShareRequest(uri: Uri, then: ((what: Int)->Unit)? = null)
    {
        parseCommonFields(uri)

        /*  TODO -- right now always accepted
        if (domain.infoRequest == TdppAction.DENY)
        {
            displayFragment(R.id.GuiTricklePayMain)
            return displayError(R.string.TpRequestAutoDeny)
        }
         */

        var whatInfo = uri.getQueryParameter("info")
        if (whatInfo == null)
        {
            whatInfo = "clipboard"
        }

        val url = replyProtocol + "://" + hostAndPort + "/_share?" + cookieParam

        if (whatInfo == "address")
        {
            val addr = getRelevantAccount().currentReceive?.address?.toString()
            if (addr != null)
            {
                wallyApp?.post(url, {
                    it.setBody(addr.toString())
                })
                then?.invoke(R.string.Address)
            }
            else
            {
                wallyApp?.displayError(R.string.NoAccounts)
            }
        }
        else if (whatInfo == "clipboard")
        {
            var clip: ClipData? = null // wallyApp?.currentClip
            if (clip == null)
            {
                laterUI {
                    //  Because background apps monitor the clipboard and steal data, you can no longer access the clipboard unless you are foreground.
                    //  However, (and this is probably a bug) if you are becoming foreground, like an activity just completed and returned to you
                    //  in onActivityResult, then your activity hasn't been foregrounded yet :-(.  So I need to delay
                    //  Wait for this app to regain the input focus
                    //  https://developer.android.com/reference/android/content/ClipboardManager#hasPrimaryClip()
                    //  If the application is not the default IME or the does not have input focus getPrimaryClip() will return false.
                    delay(250)
                    // We need to be in the foreground to read the clipboard which is why I prefer the cached clipboard
                    try
                    {
                        //var myClipboard = getSystemService(wallyApp!!, AppCompatActivity.CLIPBOARD_SERVICE) as ClipboardManager
                        var myClipboard = wallyApp?.currentActivity?.getSystemService(AppCompatActivity.CLIPBOARD_SERVICE) as ClipboardManager
                        clip = myClipboard.getPrimaryClip()
                        val item = if (clip?.itemCount != 0) clip?.getItemAt(0) else null
                        val text = item?.text?.toString() ?: i18n(R.string.pasteIsEmpty)
                        wallyApp?.post(url, { it.setBody(text) })
                        then?.invoke(R.string.clipboard)
                    }
                    catch (e: Exception)
                    {
                    }

                }
            }
            /*
            val item = if (clip?.itemCount != 0) clip?.getItemAt(0) else null
            val text = item?.text?.toString() ?: i18n(R.string.pasteIsEmpty)
            wallyApp?.post(url, { it.setBody(text) })
                then?.invoke(R.string.clipboard)

             */
            /*
            laterUI {  // We need to be in the foreground to read the clipboard
                var myClipboard = wallyApp?.currentActivity?.getSystemService(AppCompatActivity.CLIPBOARD_SERVICE) as ClipboardManager
                val clip: ClipData? = myClipboard.getPrimaryClip()
                val item = if (clip?.itemCount != 0) clip?.getItemAt(0) else null
                val text = item?.text?.toString() ?: i18n(R.string.pasteIsEmpty)
                wallyApp?.post(url, { it.setBody(text) })
                then?.invoke()
            } */
        }
        else
        {
            then?.invoke(-1)
        }
    }


    /** Will parse the string request and attempt an autopay.  Returns ASK if user needs to be asked.  Returns ACCEPT or DENY if
     * no need to ask and the request was accepted or denied.*/
    fun attemptAutopay(req: String): TdppAction
    {
        val iuri: Uri = req.toUri()
        try
        {
            parseCommonFields(iuri, false)
        }
        catch(e: TdppException)
        {
            return TdppAction.DENY
        }
        var result = handleSendToAutopay(iuri)
        if (result == TdppAction.ACCEPT)
            acceptSendToRequest()
        return result
    }

    /** Will parse the string request and attempt to autopay a special transaction.  Returns ASK if user needs to be asked.  Returns ACCEPT or DENY if
     * no need to ask and the request was accepted or denied.*/
    fun attemptSpecialTx(req: String): TdppAction
    {
        val iuri: Uri = req.toUri()
        try
        {
            parseCommonFields(iuri, false)
        }
        catch(e: TdppException)
        {
            return TdppAction.DENY
        }
        var result = handleTxAutopay(iuri)
        if (result == TdppAction.ACCEPT)
            acceptSpecialTx()
        return result
    }


    fun analyzeCompleteAndSignTx(tx: iTransaction, inputSatoshis: Long?, flags: Int?): TxAnalysisResults
    {
        val act = getRelevantAccount()
        val wal = act.wallet

        // Find out info about the outputs
        var outputSatoshis: Long = 0
        var receivingSats: Long = 0
        var sendingSats: Long = 0
        var receivingTokenTypes: Long = 0
        var sendingTokenTypes: Long = 0
        var imSpendingTokenTypes: Long = 0
        var iFunded: Long = 0
        var receivingTokenInfo = mutableMapOf<GroupId, Long>()
        var sendingTokenInfo = mutableMapOf<GroupId, Long>()
        var myInputTokenInfo = mutableMapOf<GroupId, Long>()

        var cflags = TxCompletionFlags.FUND_NATIVE or TxCompletionFlags.SIGN or TxCompletionFlags.BIND_OUTPUT_PARAMETERS

        if (flags != null)
        {
            // If nofund flag is set turn off fund_native
            if ((flags and TDPP_FLAG_NOFUND) > 0) cflags = cflags and (TxCompletionFlags.FUND_NATIVE.inv())
            if ((flags and TDPP_FLAG_PARTIAL) > 0) cflags = cflags or TxCompletionFlags.PARTIAL
            if ((flags and TDPP_FLAG_FUND_GROUPS) > 0) cflags = cflags or TxCompletionFlags.FUND_GROUPS
        }

        // Look at the inputs and match with UTXOs that I have, so I have the additional info required to sign this input
        val txos = wal.txos
        for ((idx, inp) in tx.inputs.withIndex())
        {
            val utxo = txos[inp.spendable.outpoint]
            if (utxo != null)
            {
                tx.inputs[idx].spendable = utxo
            }
        }

        // Complete and sign the transaction
        var completionException: java.lang.Exception? = null
        try
        {
            val oneAddr:PayAddress? = this.domain?.lastPayAddress?.let {
                if (it == "") null else PayAddress(it) }

            wal.txCompleter(tx, 0, cflags, inputSatoshis, destinationAddress = oneAddr)
        }
        catch (e: java.lang.Exception)  // Try to report on the tx even if we can't complete it.
        {
            completionException = e
        }

        // LogIt.info("Completed tx: " + tx.toHex())

        for (inp in tx.inputs)
        {
            val address = inp.spendable.addr
            if ((address != null) && (wal.isWalletAddress(address)))  // Is this coming from this wallet?
            {
                assert(inp.spendable.amount != -1L)  // Its -1 if I don't know the amount (in which case it ought to NOT be one of my inputs so should never happen)
                iFunded += inp.spendable.amount

                val iSuppliedTokens = inp.spendable.priorOutScript.groupInfo(inp.spendable.amount)
                if (iSuppliedTokens != null)
                {
                    imSpendingTokenTypes++
                    if (!iSuppliedTokens.isAuthority())
                        myInputTokenInfo[iSuppliedTokens.groupId] = (myInputTokenInfo[iSuppliedTokens.groupId] ?: 0) + iSuppliedTokens.tokenAmt
                }
            }
        }

        for (out in tx.outputs)
        {
            outputSatoshis += out.amount
            val address: PayAddress? = out.script.address
            val groupInfo: GroupInfo? = out.script.groupInfo(out.amount)

            if (address != null && wal.isWalletAddress(address))
            {
                receivingSats += out.amount
                if ((groupInfo != null) && (!groupInfo.isAuthority()))
                {
                    receivingTokenTypes++
                    receivingTokenInfo[groupInfo.groupId] = (receivingTokenInfo[groupInfo.groupId] ?: 0) + groupInfo.tokenAmt
                }
            }
            else
            {
                sendingSats += out.amount
                if ((groupInfo != null) && (!groupInfo.isAuthority()))
                {
                    sendingTokenTypes++
                    sendingTokenInfo[groupInfo.groupId] = (receivingTokenInfo[groupInfo.groupId] ?: 0) + groupInfo.tokenAmt
                }
            }
        }

        // Net ignores anything others are doing
        var myNetTokenInfo = mutableMapOf<GroupId, Long>()

        // Start with all that I received
        for ((k,v) in receivingTokenInfo.iterator())
        {
            myNetTokenInfo[k] = myNetTokenInfo.getOrDefault(k, 0L) + v
        }
        // Subtract out what I spent
        for ((k,v) in myInputTokenInfo.iterator())
        {
            myNetTokenInfo[k] = myNetTokenInfo.getOrDefault(k, 0L) - v
        }



        return TxAnalysisResults(act, receivingSats, sendingSats, receivingTokenTypes, sendingTokenTypes, imSpendingTokenTypes, inputSatoshis, iFunded, myInputTokenInfo, sendingTokenInfo, receivingTokenInfo, myNetTokenInfo, completionException)
    }

}