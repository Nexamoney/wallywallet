package info.bitcoinunlimited.www.wally

import io.ktor.client.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json
import kotlin.random.Random
import org.nexa.libnexakotlin.*
import org.nexa.libnexakotlin.simpleapi.NexaScript

import com.eygraber.uri.*
import info.bitcoinunlimited.www.wally.ui.*
import io.ktor.http.Url
import io.ktor.utils.io.errors.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.Serializable
import org.nexa.threads.Mutex
import org.nexa.threads.millisleep

private val LogIt = GetLog("BU.wally.tpsess")

val TDPP_DEFAULT_PROTOCOL = "http"

const val TDPP_FLAG_FUND_GROUPS = 16

// Must be top level for the serializer to handle it
//@Keep
@Serializable
data class TricklePayAssetInfo(val outpointHash: String, val amt: Long, val prevout: String, val proof: String? = null)

//@Keep
@Serializable
data class TricklePayAssetList(val assets: List<TricklePayAssetInfo>)

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
  val completionException: Exception?
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
        if (this == DENY) return i18n(S.deny)
        if (this == ASK) return i18n(S.ask)
        if (this == ACCEPT) return i18n(S.accept)
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
        return BCHserialized(format).add(domain).add(topic).add(addr).add(uoa)
          .addInt64(maxper).addInt64(maxday).addInt64(maxweek).addInt64( maxmonth)
          .add(descper).add(descday).add(descweek).add(descmonth)
          .add(automaticEnabled).add(maxperExceeded.v).add(maxdayExceeded.v).add(maxweekExceeded.v).add(maxmonthExceeded.v)
          .add(assetInfo.v).add(balanceInfo.v)
          .add(mainPayAddress).add(lastPayAddress).add(accountName)
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
        domain = uri.host ?: throw NotUriException()
        topic = uri.getQueryParameter("topic") ?: ""
        addr = uri.getQueryParameter("addr") ?: ""
        getParam(uri, "maxper", "descper").let { if (maxper < it.first) maxper = it.first; descper = it.second }
        getParam(uri, "maxday", "descday").let { if (maxday < it.first) maxday = it.first; descday = it.second }
        getParam(uri, "maxweek", "descweek").let { if (maxweek < it.first) maxweek = it.first; descweek = it.second }
        getParam(uri, "maxmonth", "descmonth").let { if (maxmonth < it.first) maxmonth = it.first; descmonth = it.second }
    }

    fun load(uri: Uri)
    {
        domain = uri.host ?: throw NotUriException()
        topic = uri.getQueryParameter("topic") ?: ""
        addr = uri.getQueryParameter("addr") ?: ""
        getParam(uri, "maxper", "descper").let { maxper = it.first; descper = it.second }
        getParam(uri, "maxday", "descday").let { maxday = it.first; descday = it.second }
        getParam(uri, "maxweek", "descweek").let { maxweek = it.first; descweek = it.second }
        getParam(uri, "maxmonth", "descmonth").let { maxmonth = it.first; descmonth = it.second }
    }
}

class TricklePayDomains()
{
    val SER_VERSION: Byte = 1.toByte()

    val dataLock = Mutex()

    var db: KvpDatabase? = null

    fun domainKey(host: String, topic: String? = null): String = host + "/" + (topic ?: "")
    var domains: MutableStateFlow<Map<String, TdppDomain>> = MutableStateFlow(mapOf())
    var domainsLoaded: Boolean = false

    val size:Int
        get() = domains.value.size

    fun insert(d: TdppDomain)
    {
        val tmp = domains.value.toMutableMap()
        tmp[domainKey(d.domain, d.topic)] = d
        domains.value = tmp
        save()
    }

    fun remove(d: TdppDomain)
    {
        val tmp = domains.value.toMutableMap()
        tmp.remove(domainKey(d.domain, d.topic))
        domains.value = tmp
        save()
    }

    fun clear()
    {
        val tmp = domains.value.toMutableMap()
        tmp.clear()
        domains.value = tmp
        save()
    }

    /** Load domain if it exists or create it */
    fun loadCreateDomain(host: String, topic:String): TdppDomain
    {
        return dataLock.lock {
            if (!domainsLoaded) load()
            var d = domains.value[domainKey(host, topic)]
            if (d == null)
            {
                // delay and try again because load() cannot happen in the gui thread
                d = TdppDomain(host, topic)
                val tmp = domains.value.toMutableMap()
                tmp[domainKey(host, topic)] = d
                domains.value = tmp
                save()
            }
            d
        }
    }

    /** Load domain if it exists or create it */
    fun loadDomain(host: String, topic:String): TdppDomain?
    {
        return dataLock.lock {
            if (!domainsLoaded) load()
            val d = domains.value[domainKey(host, topic)]
            d
        }
    }

    fun load()
    {
        laterJob {
            dataLock.lock {
                if (db == null)
                {
                    db = openKvpDB("wallyData")
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
                                domains.value = bchser.demap({ it.deString() }, { TdppDomain(it) })
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
        laterJob {
            dataLock.lock {
                if (domainsLoaded)  // If we save the domains before we load them, we'll erase them!
                {
                    val ser = BCHserialized.uint8(SER_VERSION)
                    ser.add(BCHserialized.map(domains.value,
                      {
                          BCHserialized(SerializationType.DISK).add(it)
                      },
                      {
                          it.BCHserialize()
                      }, SerializationType.DISK))
                    db?.set("tdppDomains", ser.toByteArray())
                }
            }
        }
    }
}

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

@OptIn(ExperimentalUnsignedTypes::class)
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
    val orderedParams = uri.queryMap().keys.toList().sorted()
    val queryParam = mutableListOf<String>()
    for (p in orderedParams)
    {
        if (p == "sig") continue
        val tmp = uri.getQueryParameter(p)
        if (tmp == null) continue
        val tmp2 = tmp.urlEncode()
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
    val result = libnexa.verifyMessage(verifyThis.toByteArray(), pa.data, sigBytes)
    if (result == null || result.size == 0)
    {
        LogIt.info("verification failed for: " + verifyThis + " Address: " + addressStr)
        return false
    }
    LogIt.info("verification good for: " + verifyThis + " Address: " + addressStr)
    return true
}

class TricklePaySession(val tpDomains: TricklePayDomains, val whenDone: ((String, String, Boolean?)->Unit)?= null)
{
    var accepted: Boolean = false  // did the user accept this payment request
    var newDomain: Boolean = false // was a domain created during this session?
    var editDomain: Boolean = false // user just wants to look at and edit this domain (no changes are coming from the outside!)
    var proposalUrl: Uri? = null
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
    var proposedDomainChanges: TdppDomain? = null

    val isSecureRequest:Boolean
        get()
        {
            // If a signature was provided, then that decides it.
            sigOk?.let { return it }
            // Otherwise look at the protocol
            if (proposalUrl?.scheme == "https") return true
            return false
        }

    // produce the correct reply protocol (without the :)
    val replyProtocol:String
        get()
        {
            rproto?.let {return it }
            proposalUrl?.let {
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
            if (h == null) throw TdppException(S.UnknownDomainRegisterFirst, "no domain specified")
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
        proposalUrl = uri
        val h = uri.host
        if (h == null) throw TdppException(S.UnknownDomainRegisterFirst, "no domain specified")
        host = h
        port = uri.port
        topic = uri.getQueryParameter("topic")
        cookie = uri.getQueryParameter("cookie")
        reason = uri.getQueryParameter("reason")

        val addr = uri.getQueryParameter("addr")

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
        domain = run {
            val d = tpDomains.loadDomain(h, topic ?: "") ?: run {
                if (!autoCreateDomain) throw TdppException(S.UnknownDomainRegisterFirst, "no domain specified")
                val d2 = tpDomains.loadCreateDomain(h, topic ?: "")
                d2.addr = addr ?: ""
                newDomain = true
                d2
            }
            newDomain = false
            d
        }

        sigOk = VerifyTdppSignature(uri, domain?.addr)
    }

    fun getRelevantAccount(preferredAccount: String? = null): Account
    {
        if (preferredAccount != null && preferredAccount != "")  // Prefer the account associated with this domain
        {
            val act = wallyApp!!.accounts[preferredAccount]
            if (act != null)
            {
                if (chainSelector == null || act.chain.chainSelector == chainSelector) return act
            }
        }

        // Get a handle on the relevant wallets
        var act = wallyApp!!.focusedAccount.value
        if (act != null)
        {
            if (chainSelector == null || act.chain.chainSelector == chainSelector) return act
        }

        try
        {
            act = wallyApp!!.primaryAccount
            if (chainSelector == null || act.chain.chainSelector == chainSelector) return act
        }
        catch(e:PrimaryWalletInvalidException)
        {
            // pass thru
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
        val d = domain ?: return
        val act = getRelevantAccount()
        val wal = act.wallet
        val domainSigningAddress = PayAddress(d.addr)
        val p = proposedDestinations ?: return
        // Prevent double send
        proposedDestinations = null

        val tx = wal.send(p, false, i18n(S.title_activity_trickle_pay) + " " + domainAndTopic + ". " + reason)
        val txh = wal.getTx(tx.idem)
        if (txh != null)  // mark this as belonging to us
        {
            txh.relatedTo["tdpp_${d.addr}"] = (proposalUrl?.toString() ?: "").encodeUtf8()
        }
        @Serializable data class TricklePaySendToReply(val resultCode:Int, val txid:String, val txidem: String, val tx: String, val error: String)
        val postData = TricklePaySendToReply(200, tx.id.toHex(), tx.idem.toHex(), tx.toHex(), "")
        val js = Json

        val postStr = js.encodeToString(TricklePaySendToReply.serializer(), postData)
        val urlStr = replyProtocol + "://" + hostAndPort + "/sendto?" + cookieParam
        respondWith(urlStr, postStr)
    }

    fun respondWith(url: String, postResp: String)
    {
        val wd = whenDone
        if (wd != null) wd.invoke(url, postResp, true)
        else
        {
            wallyApp?.later {
                LogIt.info("responding to server")
                val client = HttpClient()
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
                    displayNotice(S.accept, respText)
                }
                catch (e: IOException)
                {
                    displayError(S.connectionException)
                }
                client.close()
            }
        }
    }

    fun acceptAddressRequest(): String
    {
        LogIt.info("accepted address request")
        val url = replyProtocol + "://" + hostAndPort + "/address?" + cookieParam

        val d = domain
        if (d == null) throw TdppException(S.BadWebLink, "bad domain")

        // Once you've associated an address with this domain, you've also associated an account!
        val acc = getRelevantAccount(d.accountName)
        val wal = acc.wallet

        // If requester wants a unique address just give one.  Otherwise give the main address (if we have one; if not, make one)
        val addr:String = if (uniqueAddress)
        {
            wal.getCurrentDestination().address.toString()
        }
        else
        {
            if (d.mainPayAddress != "") d.mainPayAddress
            else
            {
                val tmp = wal.getCurrentDestination().address
                d.mainPayAddress = tmp.toString()
                tmp.toString()
            }
        }

        d.lastPayAddress = addr
        d.accountName = acc.name

        respondWith(url, addr)
        tpDomains.save()  // Because I changed the lastPayAddress and maybe mainPayAddress
        return "Sent to: " + url
    }

    fun acceptAssetRequest():String
    {
        LogIt.info("accepted asset request")
        val assets = assetInfoList ?: return "no assets"

        val url = replyProtocol + "://" + hostAndPort + "/assets?" + cookieParam

        val wd = whenDone
        if (wd != null)
        {
            val js = Json
            val tmp = js.encodeToString(TricklePayAssetList.serializer(), assets)
            wd(url, tmp, true)
        }
        else // do the default accept action (send a response back to the server)
        {
            wallyApp?.later {
                LogIt.info("responding to server")
                val client = HttpClient()
                {
                    install(ContentNegotiation) {
                        json()
                    }
                    install(HttpTimeout) { requestTimeoutMillis = 5000 }
                }

                val js = Json
                try
                {
                    val response: HttpResponse = client.post(url) {
                        val tmp = js.encodeToString(TricklePayAssetList.serializer(), assets)
                        LogIt.info("JSON response ${tmp.length} : " + tmp.toString())
                        setBody(tmp)
                    }
                    val respText = response.bodyAsText()
                    // notice shown right when button pressed: displayNotice(S.TpAssetRequestAccepted, respText)
                }
                catch (e: IOException)
                {
                    displayError(S.connectionException)
                }
                client.close()
            }
        }
        return "Sent to: " + url
    }


    /** If breakIt is true, a bad transaction is generated (for testing) */
    fun acceptSpecialTx(breakIt:Boolean = false)
    {
        LogIt.info(sourceLoc() + ": accept trickle pay special transaction")
        accepted = true
        val pTx = proposedTx
        val panalysis = proposalAnalysis

        if ((pTx != null)&&(panalysis != null))
        {
            if (breakIt)
            {
                val choice = (0..1).random()
                when (choice)
                {
                    // remove an input
                    0 ->  pTx.inputs.removeAt((0 until pTx.inputs.size).random())
                    // remove a signature
                    1 ->
                    {
                        val inp = pTx.inputs.random()
                        inp.script = NexaScript()
                    }
                    // remove an output; this should work because some sig should have signed the whole tx (up to this point if partial)
                    2 ->
                    {
                        pTx.outputs.removeAt((0 until pTx.outputs.size).random())
                    }
                }
                LogIt.info(sourceLoc() + "Breaking this TDPP special transaction response: ${pTx.toHex()}")
            }

            proposedTx = null
            proposalUrl = null
            LogIt.info(sourceLoc() + ": sign trickle pay special transaction")

            // TODO: put a record of this transaction somewhere so when it is completed by the server we can annotate the history with a reason.
            // This is tricky because it may not be fully formed so idem will change.  Maybe look up by signature
            // tx reason: i18n(R.string.title_activity_trickle_pay) + " " + domainAndTopic + ". " + reason)

            wallyApp?.let { app ->
                // Post this transaction if the TDPP protocol suggests that I do so (its complete)
                // (And I'm not deliberately creating a bad transaction)
                if (((tflags and TDPP_FLAG_NOPOST) == 0)&&(!breakIt)) try
                    {
                        var completed = pTx.inputs.isNotEmpty()  // It can't be completed if there are no inputs at all
                        for (inp in pTx.inputs)
                        {
                            if (inp.script.size == 0)
                            {
                                LogIt.warning(sourceLoc() +": TDPP special transaction: Counterparty indicated that I could post the completed transaction, but they still need to sign")
                                completed = false
                            }
                        }
                        if (completed)
                        {
                            val wallet = getRelevantAccount(domain?.accountName).wallet
                            wallet.send(pTx)
                            // Now asynchronously wait for the wallet to process the tx, and then annotate it
                            laterJob {
                                var retries = 0
                                while(retries<20)
                                {
                                    val txRecord = wallet.getTx(pTx.idem)
                                    if (txRecord != null)
                                    {
                                        // TODO I could put some interesting info here
                                        txRecord.relatedTo["TDPP"] = byteArrayOf(1)
                                        break
                                    }
                                    millisleep(300U)
                                    retries++
                                }
                            }
                        }
                    }
                    catch(e:Exception)  // Its possible that the tx is partial but the caller didn't set the bit, so if the tx is rejected ignore
                    {
                        logThreadException(e)
                    }

                // And hand it back to the requester...
                // grab temps because activity could go away
                val rp = replyProtocol
                val hp = hostAndPort
                val cp = cookieParam
                val txHex = pTx.toHex()
                //LogIt.info("Sending special tx response: ${pTx.toHex()}")
                //pTx.debugDump()
                laterJob {
                    val req = Url(rp + "://" + hp + "/tx?tx=$txHex&$cp")
                    LogIt.info("Sending special tx response: ${req}")
                    val data = try
                    {
                        req.readText(HTTP_REQ_TIMEOUT_MS)
                    }
                    catch (e: IOException)
                    {
                        LogIt.info("Error submitting transaction: " + e.message)
                        displayError(S.WebsiteUnavailable)
                        return@laterJob
                    }
                    catch (e: Exception)
                    {
                        LogIt.info("Error submitting transaction: " + e.message)
                        displayError(S.WebsiteUnavailable)
                        return@laterJob
                    }
                    LogIt.info(sourceLoc() + " TP response to the response: " + data)
                    // if the other side did not like the transaction it will return invalid
                    if (data == "unknown session")
                    {
                        displayWarning(i18n(S.TpNoSession),i18n(S.TpNoSession))
                    }
                    else if (data.contains("invalid"))
                    {
                        displayWarning(i18n(S.staleTransaction),i18n(S.staleTransactionDetails))
                    }
                    else if (data.contains("error"))
                        displayWarning(i18n(S.reject))
                    else
                       displayNotice(S.TpTxAccepted)
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
            else askReasons.add(i18n(S.TpExceededMaxPer))
        }

        // TODO maxday, maxweek, maxmonth

        if (!domain.automaticEnabled) askReasons.add(i18n(S.TpAutomaticDisabled))

        if (askReasons.size == 0)
        {
            return TdppAction.ACCEPT
        }
        return TdppAction.ASK
    }

    /* Process a tx completion proposal, and decide what to do about it */
    fun handleTxAutopay(uri: Uri): TdppAction
    {
        proposalUrl = uri
        proposedTx = null
        proposedDestinations = null

        val txHex = uri.getQueryParameter("tx")
        if (txHex == null)
        {
            throw TdppException(S.BadWebLink, "missing tx parameter")
        }

        parseCommonFields(uri)

        tflags = uri.getQueryParameter("flags")?.toInt() ?: 0

        // If we are funding then inputSatoshis must be provided
        val inputSatoshis = uri.getQueryParameter("inamt")?.toLongOrNull()  // ?: return displayError(R.string.BadLink)
        if ((inputSatoshis == null) && ((tflags and TDPP_FLAG_NOFUND) == 0))
        {
            throw TdppException(S.BadWebLink, "missing inamt parameter")
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
        if (!isSecureRequest)
        {
            val d = domain
            if (d == null || d.addr.length == 0) throw TdppException(S.UnknownDomainRegisterFirst, "")
            else throw TdppException(S.ignoredInsecureRequest, "")
        }

        while (true)
        {
            val amtS = uri.getQueryParameter("amt" + count.toString())
            val addrS = uri.getQueryParameter("addr" + count.toString())

            // if one exists but not the other, request is bad
            if ((amtS != null) xor (addrS != null))
            {
                throw DataMissingException(i18n(S.BadWebLink))
                // return displayError(R.string.BadLink, "missing parameter")
            }
            // if either do not exist, done
            if ((amtS == null) || (addrS == null)) break

            val amt = amtS.toLong()
            if (amt <= 0) throw BadAmountException(S.Amount)
            val addrPa = PayAddress(addrS)
            addrAmt.add(Pair(addrPa, amt.toLong()))
            if (chainSelector == null) chainSelector = addrPa.blockchain
            else if (addrPa.blockchain != chainSelector)  // You can only send on one blockchain at once
            {
                throw BadCryptoException(i18n(S.badCryptoCode))
            }
            val priorTotal = total
            total += amt
            if (total < priorTotal)
            {
                throw BadAmountException(S.Amount)
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
        if (d == null) throw TdppException(S.BadWebLink, "bad domain")

        val bc = uri.getQueryParameter("blockchain") ?: chainToURI[ChainSelector.NEXA]
        chainSelector = uriToChain[bc]
        if (chainSelector == null) throw TdppException(S.BadWebLink, "unknown blockchain")

        uniqueAddress = uri.getQueryParameter("unique").toBoolean() ?: false

        return TdppAction.ACCEPT
    }

    fun handleAssetInfoRequest(uri: Uri): TdppAction
    {
        parseCommonFields(uri)
        val d = domain
        if (d == null) throw TdppException(S.BadWebLink, "bad domain")

        if (d.assetInfo == TdppAction.DENY) return TdppAction.DENY

        val scriptTemplateHexList = uri.getQueryParameters("af")
        if (scriptTemplateHexList.isEmpty())
        {
            throw TdppException(S.BadWebLink, "missing 'af' parameter")
        }

        val chalbyStr = uri.getQueryParameter("chalby")
        val chalby = chalbyStr?.fromHex()

        val acc = getRelevantAccount()
        val wal = acc.wallet
        val challengerId = host?.toByteArray()

        val matches = mutableListOf<TricklePayAssetInfo>()

        for (scriptTemplateHex in scriptTemplateHexList)
        {
            val stemplate = SatoshiScript(chainSelector!!, SatoshiScript.Type.SATOSCRIPT, scriptTemplateHex.fromHex())
            LogIt.info(sourceLoc() + ": Asset filter: " + stemplate.toHex() + " ASM: " + stemplate.toAsm())

            wal.forEachUtxo { spendable ->
                val constraint = spendable.priorOutScript
                if (constraint.matches(stemplate, true) != null)
                {
                    val outpoint = spendable.outpoint!!
                    val serPrevout = spendable.prevout.BCHserialize(SerializationType.NETWORK).toHex()
                    // For now, also check that this output is an asset, not nexa.  Later we may want to allow provable nexa amounts...
                    val tmpl = constraint.parseTemplate(spendable.amount)
                    val gi = tmpl?.groupInfo
                    if (gi != null)
                    {
                        matches.add(
                          TricklePayAssetInfo(
                            outpoint.toHex(), spendable.amount, serPrevout,
                            if (chalby != null && challengerId != null) makeChallengeTx(spendable, challengerId, chalby)?.toHex() else null,
                          )
                        )
                    }
                }
                else
                {
                    LogIt.info("Rejected: " + constraint.toHex() + "  ASM: " + constraint.toAsm())
                    //val tryAgain = constraint.matches(stemplate, true)
                    //LogIt.info(tryAgain.toString())
                }
                false
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
            whatInfo = "address"
        }

        val url = replyProtocol + "://" + hostAndPort + "/_share?" + cookieParam

        if (whatInfo == "address")
        {
            val addr = getRelevantAccount().currentReceive?.address?.toString()
            if (addr != null)
            {
                whenDone?.invoke(url, addr.toString(), true) ?: run {
                    wallyApp?.post(url, {
                        it.setBody(addr.toString())
                    })
                }

                then?.invoke(S.Address)
            }
            else
            {
                displayError(S.NoAccounts)
            }
        }
        else if (whatInfo == "clipboard")
        {
            triggerClipboardAction {clipText ->
                if (clipText == null) then?.invoke(S.pasteIsEmpty)
                else
                {
                    whenDone?.invoke(url, clipText, true) ?: run {
                        wallyApp?.post(url) { hrb ->
                            hrb.setBody(clipText)
                        }
                    }
                    then?.invoke(S.clipboard)
                }
            }
        }
        else
        {
            then?.invoke(-1)
        }
    }


    /** Will parse the string request and attempt an autopay.  Returns ASK if user needs to be asked.  Returns ACCEPT or DENY if
     * no need to ask and the request was accepted or denied.*/
    fun attemptAutopay(iuri: Uri): TdppAction
    {
        //val iuri: Uri = Uri.parse(req)
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
    fun attemptSpecialTx(iuri: Uri): TdppAction
    {
        //val iuri: Uri = Uri.parse(req)
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
        for ((idx, inp) in tx.inputs.withIndex())
        {
            val utxo = wal.getTxo(inp.spendable.outpoint!!)
            if (utxo != null)
            {
                tx.inputs[idx].spendable = utxo
            }
        }

        // Complete and sign the transaction
        var completionException: Exception? = null
        try
        {
            val oneAddr:PayAddress? = this.domain?.lastPayAddress?.let {
                if (it == "") null else PayAddress(it) }

            wal.txCompleter(tx, 0, cflags, inputSatoshis, destinationAddress = oneAddr)
        }
        catch (e: Exception)  // Try to report on the tx even if we can't complete it.
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
                        myInputTokenInfo[iSuppliedTokens.groupId] = (myInputTokenInfo[iSuppliedTokens.groupId] ?: 0) + iSuppliedTokens.tokenAmount
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
                    receivingTokenInfo[groupInfo.groupId] = (receivingTokenInfo[groupInfo.groupId] ?: 0) + groupInfo.tokenAmount
                }
            }
            else
            {
                sendingSats += out.amount
                if ((groupInfo != null) && (!groupInfo.isAuthority()))
                {
                    sendingTokenTypes++
                    sendingTokenInfo[groupInfo.groupId] = (receivingTokenInfo[groupInfo.groupId] ?: 0) + groupInfo.tokenAmount
                }
            }
        }

        // Net ignores anything others are doing
        var myNetTokenInfo = mutableMapOf<GroupId, Long>()

        // Start with all that I received
        for ((k,v) in receivingTokenInfo.iterator())
        {
            val tmp = myNetTokenInfo.get(k) ?: 0L
            myNetTokenInfo[k] = tmp + v
        }
        // Subtract out what I spent
        for ((k,v) in myInputTokenInfo.iterator())
        {
            myNetTokenInfo[k] = (myNetTokenInfo.get(k) ?: 0L) - v
        }

        return TxAnalysisResults(act, receivingSats, sendingSats, receivingTokenTypes, sendingTokenTypes, imSpendingTokenTypes, inputSatoshis, iFunded, myInputTokenInfo, sendingTokenInfo, receivingTokenInfo, myNetTokenInfo, completionException)
    }
}


fun HandleTdpp(iuri: Uri, then: ((String, String, Boolean?)->Unit)?= null): Boolean
{
    // For certain screens, just ignore duplicate requests
    if (nav.currentScreen.value == ScreenId.SpecialTxPerm)
    {
        val ctp = nav.curData.value as? TricklePaySession
        if ((ctp != null)&&(ctp.proposalUrl == iuri))
        {
            return false
        }
    }

    val bkg = wallyApp!!.amIbackground()  // if the app is backgrounded, we need to notify and not just change the GUI
    val scheme = iuri.scheme
    val path = iuri.path
    if (scheme?.lowercase() == TDPP_URI_SCHEME)
    {
        val tp = TricklePaySession(wallyApp!!.tpDomains, then)
        tp.parseCommonFields(iuri, true)
        val address = iuri.getQueryParameter("addr")
        if (path == "/reg")  // Handle registration
        {
            tp.proposedDomainChanges?.addr?.let {paddr ->
                tp.domain?.addr?.let {
                    if (it != paddr)  // If we have all this info, they better be the same
                    {
                        throw TdppException(S.badAddress, "Domain signing address is inconsistent with what was registered.")
                    }
                }
            }

            if (tp.sigOk == true)  // Registration requires a good signature
            {
                val d = TdppDomain(iuri)
                tp.proposedDomainChanges = d
            }
            else
            {
                displayError(S.badSignature)
                return false
            }

            launch {
                externalDriver.send(GuiDriver(ScreenId.TpSettings, tpSession = tp))
            }
            return true
        }
        if (path == "/sendto")
        {
            try
            {
                val result = tp.attemptAutopay(iuri)
                val acc = tp.getRelevantAccount()
                val amtS: String = acc.format(acc.fromFinestUnit(tp.totalNexaSpent)) + " " + acc.currencyCode
                when(result)
                {
                    TdppAction.ASK ->
                    {
                        if (bkg) platformNotification(i18n(S.PaymentRequest), i18n(S.AuthAutopay) % mapOf("domain" to tp.domainAndTopic, "amt" to amtS), iuri.toString())
                        nav.go(ScreenId.SendToPerm, data = tp)
                        return false
                    }
                    TdppAction.ACCEPT ->
                    {
                        // TODO since this was auto-accepted, uri should go to the configuration page to stop auto-accepting
                        platformNotification(i18n(S.AuthAutopayTitle), i18n(S.AuthAutopay) % mapOf("domain" to tp.domainAndTopic, "amt" to amtS))
                        return true
                    }

                    TdppAction.DENY -> return true  // true because "autoHandle" returns whether the intent was "handled" automatically -- denial is handling it
                }
            }
            catch (e:WalletNotEnoughBalanceException)
            {
                // TODO where to go when clicked
                platformNotification(i18n(S.insufficentBalance), e.shortMsg ?: e.message ?: i18n(S.unknownError), null, AlertLevel.ERROR)
            }
            catch (e:WalletNotEnoughTokenBalanceException)
            {
                // TODO where to go when clicked
                platformNotification(i18n(S.insufficentBalance), e.shortMsg ?: e.message ?: i18n(S.unknownError), null, AlertLevel.ERROR)
            }
            catch (e:WalletException)
            {
                displayUnexpectedException(e)
            }
        }
        if (path == "/lp")  // we are already connected which is how this being called in the app context
        {
            //displayNotice(S.connected)
            wallyApp?.accessHandler?.startLongPolling(tp.replyProtocol, tp.hostAndPort, tp.cookie)
            displaySuccess(S.trying)
            return true
        }
        if (path == "/share")
        {
            tp.handleShareRequest(iuri) {
                if (it != -1)
                {
                    val msg: String = i18n(S.SharedNotification) % mapOf("what" to i18n(it))
                    displayNotice(msg)
                }
                else displayError(S.badQR)
            }
        }
        else if (path == "/address")
        {
            val result = tp.handleAddressInfoRequest(iuri)

            when(result)
            {
                TdppAction.ASK ->  // ADDRESS
                {
                    TODO("always accept for now")
                    /*
                    var intent = Intent(this, TricklePayActivity::class.java)
                    intent.data = Uri.parse(intentUri)
                    if (act != null) autoPayNotificationId =
                      notifyPopup(intent, i18n(R.string.TpAssetInfoRequest), i18n(R.string.fromColon) + tp.domainAndTopic, act, false, autoPayNotificationId)
                    return false

                     */
                }
                TdppAction.ACCEPT -> // ADDRESS
                {
                    tp.acceptAddressRequest()
                    return true
                }

                TdppAction.DENY -> return true  // true because "autoHandle" returns whether the intent was "handled" automatically -- denial is handling it
            }
        }
        else if (path == "/assets")
        {
            // Repeated asset info requests makes no sense, just ignore if I'm already in the asset info page
            if (nav.currentScreen.value == ScreenId.AssetInfoPerm)
            {
                return false
            }
            val result = tp.handleAssetInfoRequest(iuri)

            when(result)
            {
                TdppAction.ASK ->  // ASSETS
                {
                    nav.go(ScreenId.AssetInfoPerm, data = tp)
                }
                TdppAction.ACCEPT -> // ASSETS
                {
                    tp.acceptAssetRequest()
                    return true
                }

                TdppAction.DENY -> return true  // true because "autoHandle" returns whether the intent was "handled" automatically -- denial is handling it
            }
        }
        else if (path == "/tx")
        {
            val result = tp.attemptSpecialTx(iuri)
            when(result)
            {
                TdppAction.ASK ->  // special tx
                {
                    nav.go(ScreenId.SpecialTxPerm, data = tp)
                    return false
                }
                TdppAction.ACCEPT ->  // special tx auto accepted
                {
                    tp.acceptSpecialTx()
                }
                // special tx auto-deny
                TdppAction.DENY -> return true  // true because "autoHandle" returns whether the intent was "handled" automatically -- denial is handling it
            }
        }

    }
    return false
}
