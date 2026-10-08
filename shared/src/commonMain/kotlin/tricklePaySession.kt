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
import org.nexa.nexacli.*
import org.nexa.libnexakotlin.simpleapi.NexaScript
import org.nexa.nexacli.*
import com.eygraber.uri.*
import info.bitcoinunlimited.www.wally.ui.*
import info.bitcoinunlimited.www.wally.ui.views.AccountPill
import info.bitcoinunlimited.www.wally.ui.views.AssetViewModel
import io.ktor.http.Url
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.io.IOException
import kotlinx.serialization.Serializable
import org.nexa.assets.AssetInfo
import org.nexa.assets.AssetPerAccount
import org.nexa.threads.Mutex
import org.nexa.threads.millisleep
import kotlin.concurrent.Volatile

private val LogIt = GetLog("BU.wally.tpsess")

val TDPP_DEFAULT_PROTOCOL = "http"

const val TDPP_FLAG_FUND_GROUPS = 16

@Serializable data class TricklePaySendToReply(val resultCode:Int, val txid:String, val txidem: String, val tx: String, val error: String)

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
  val assetViewModel: AssetViewModel,
  val completionException: Exception?,
  val foreignUnsignedInputs: List<Int> = listOf(),  // inputs that are not ours and lack signatures (when completion failed)
  val reservedCoinsWouldHelp: Boolean = false,  // completion failed for lack of funds, but would succeed using coins reserved by an earlier, never completed request from the same site
  val reservedCoinsIncludeTokens: Boolean = false  // some of those reserved coins carry tokens, so offering them is offering tokens
)
{
    /** Completion failed only because the wallet cannot (currently) supply enough coins or tokens.  Such a failure may resolve itself
     * as the wallet syncs, so the permission screen keeps re-checking it. */
    val isInsufficientFunds: Boolean
        get() = completionException is WalletNotEnoughBalanceException || completionException is WalletNotEnoughTokenBalanceException
}

/** How often the special transaction permission screen re-checks a proposal that could not be completed, when no wallet change event arrives */
const val TDPP_RECHECK_INTERVAL_MS = 5_000L

private val tdppSessionCounter = atomic(0)

/** Summary of the coins a wallet could put into a transaction right now.  Used to skip pointless re-analysis of a proposal: if nothing
 * that could be spent has changed since the last failed attempt, the attempt would fail the same way. */
private data class SpendableFingerprint(val freeNative: Long, val freeTokens: Map<GroupId, Long>, val utxoCount: Int, val reservedByPartials: Int)


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
                        LogIt.info(sourceLoc() + "Note that no TDPP domains are registered (this is expected if the user has not registered with any services)")
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
    tx.add(txInputFor(sp), SPENDABLE_UNRESERVED)  // We are just proving we CAN spend, so we do NOT want to reserve this UTXO
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
    var inputSatoshis: Long = 0  // satoshis being supplied by inputs in this tx
    var originalTx: iTransaction? = null  // The original (unsigned) transaction coming from the TDPP request
    @Volatile var proposedTx: iTransaction? = null
    var proposalAnalysis: MutableStateFlow<TxAnalysisResults?> = MutableStateFlow(null)
    var assetInfoList:TricklePayAssetList? = null

    var totalNexaSpent: Long = 0  // How much does this proposal spend in nexa satoshis

    // ---- Re-analysis of a special transaction proposal that could not be completed (see reanalyze) ----
    // Everything automatic is gated on originalTx != null, i.e. on the proposal having come from an actual TDPP request.
    val sessionId: Int = tdppSessionCounter.incrementAndGet()
    private val proposalLock = Mutex("tdppProposal")  // serialises analysis, accept, reject, supersede, release and retry
    private val busy = atomic(0)  // > 0 while an analysis or retry owns the proposal
    /** True while the proposal is being (re)analysed; the screen hides Accept and shows that a re-check is in progress */
    val analyzing = MutableStateFlow(false)
    /** The proposal has been decided (accepted, rejected, superseded) or cannot be completed any more: no more automatic work */
    @Volatile var proposalClosed = false
    /** Set when the screen goes away with the proposal undecided, so that coming back re-analyses instead of showing stale state */
    @Volatile var needsReanalysis = false
    /** The current proposal was completed with coins that an earlier partial transaction from the same site had reserved */
    val usingReservedCoins = MutableStateFlow(false)
    /** Bumped every time a new analysis is published (for tests) */
    val proposalRevision = MutableStateFlow(0)
    private val forceRequested = atomic(false)
    @Volatile private var lastFingerprint: SpendableFingerprint? = null
    // What the current (successful) attempt is holding on the user's behalf: the coins the completer reserved and the addresses it
    // consumed, in which wallet.  Released exactly once by releaseHeld(); a failed attempt holds nothing.
    @Volatile private var heldWallet: Wallet? = null
    @Volatile private var heldOutpoints: Set<iTxOutpoint> = emptySet()
    @Volatile private var boundAddresses: List<PayAddress> = emptyList()
    private val watchLock = Mutex("tdppWatch")
    @Volatile private var watching = false
    private var watchedWallet: Wallet? = null
    private var watchHandle = -1

    var uniqueAddress: Boolean = false

    var domain: TdppDomain? = null
    var proposedDomainChanges: TdppDomain? = null

    // Share the global focused-account flow (same pattern as IdentitySession) so that updates
    // from populateRelevantAccounts propagate to the wallet UI, not just this session's screens.
    val pill = AccountPill(wallyApp!!.focusedAccount)

    /** All the possible accounts that can be used for this identity session */
    var candidateAccounts: ListifyMap<String, Account>? = null
        set(v)
        {
            field = v
            pill.choices = v?.toList()
        }

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

    /** You need to clean up tx resources in this proposal if you are not going to accept it */
    fun abortProposal()
    {
        proposedTx?.let { tx->
            proposalAnalysis.value?.let { pa ->
                // A proposal built from a request only ever holds what the session recorded as held (see releaseHeld); anything else
                // in the tx is either not ours or was reserved by some other flow (an offer, say) and must stay reserved.
                if (originalTx != null) releaseHeld()
                else pa.account.wallet.abortTransaction(tx)
                proposalAnalysis.value = null
            }
            proposedTx = null
        }
    }

    // ---- Completion attempt bookkeeping ----

    /** Translate the TDPP request flags into transaction completion flags */
    private fun completionFlags(flags: Int?, sign: Boolean = true): Int
    {
        var cflags = TxCompletionFlags.FUND_NATIVE or TxCompletionFlags.BIND_OUTPUT_PARAMETERS
        if (sign) cflags = cflags or TxCompletionFlags.SIGN
        if (flags != null)
        {
            // If nofund flag is set turn off fund_native
            if ((flags and TDPP_FLAG_NOFUND) > 0) cflags = cflags and (TxCompletionFlags.FUND_NATIVE.inv())
            if ((flags and TDPP_FLAG_PARTIAL) > 0) cflags = cflags or TxCompletionFlags.PARTIAL
            if ((flags and TDPP_FLAG_FUND_GROUPS) > 0) cflags = cflags or TxCompletionFlags.FUND_GROUPS
        }
        return cflags
    }

    /** Look at the inputs and match with UTXOs that I have, so I have the additional info required to sign them */
    private fun bindOwnInputs(tx: iTransaction, wal: Wallet)
    {
        for ((idx, inp) in tx.inputs.withIndex())
        {
            val utxo = wal.getTxo(inp.spendable.outpoint!!)
            if (utxo != null)
            {
                tx.inputs[idx].spendable = utxo
            }
        }
    }

    /** A fresh copy of the transaction as the server sent it, ready for another completion attempt */
    private fun cloneOriginalTx(): iTransaction?
    {
        val o = originalTx ?: return null
        return txFor(o.chainSelector, BCHserialized(o.toByteArray(), SerializationType.NETWORK))
    }

    /** The address the domain asked all payments to go to, if any */
    private fun payAddressOverride(): PayAddress? = domain?.lastPayAddress?.let { if (it == "") null else PayAddress(it) }

    /** The outpoints the request itself named as inputs (ours or not); everything else in a completed tx was added by the completer */
    private fun requestNamedOutpoints(): Set<iTxOutpoint> = originalTx?.inputs?.mapNotNull { it.spendable.outpoint }?.toSet() ?: emptySet()

    /** The inputs the completer added to [tx] */
    private fun completerAddedOutpoints(tx: iTransaction): Set<iTxOutpoint>
    {
        val named = requestNamedOutpoints()
        return tx.inputs.mapNotNull { it.spendable.outpoint }.filter { it !in named }.toSet()
    }

    /** The wallet addresses that a completion attempt consumed (output binding and change), so they can be handed back if the attempt is discarded */
    private fun boundAddressesOf(wal: Wallet, tx: iTransaction, override: PayAddress?): List<PayAddress>
    {
        val originalAddrs = originalTx?.outputs?.mapNotNull { it.script.address }?.toSet() ?: emptySet()
        return tx.outputs.mapNotNull { it.script.address }.filter { wal.isWalletAddress(it) && it !in originalAddrs && it != override }.distinct()
    }

    /** Give the reservations on [outpoints] and the [addresses] back to [wal].
     *
     * This goes through [Wallet.abortTransaction] on a synthetic transaction holding only those inputs, so nothing else the wallet has
     * reserved is touched and no transaction history entry can be matched and deleted.  Callers must only pass what they know to be
     * reserved on their own behalf right now: the wallet does not record who reserved a coin, so releasing a coin twice would undo a
     * reservation somebody else took on it in between. */
    private fun giveBack(wal: Wallet, outpoints: Collection<iTxOutpoint>, addresses: List<PayAddress>)
    {
        val synthetic = txFor(wal.chainSelector)
        for (op in outpoints) wal.getTxo(op)?.let { synthetic.add(txInputFor(it), SPENDABLE_UNRESERVED) }
        if (synthetic.inputs.isEmpty() && addresses.isEmpty()) return
        wal.abortTransaction(synthetic, returnUnusedAddresses = addresses.ifEmpty { null })
    }

    /** Record what a successful attempt is holding: the inputs the completer added to [tx] and the addresses it consumed */
    private fun hold(wal: Wallet, tx: iTransaction, addresses: List<PayAddress>)
    {
        heldWallet = wal
        heldOutpoints = completerAddedOutpoints(tx)
        boundAddresses = addresses
    }

    /** Give back whatever the current attempt is holding.  Idempotent: after one call there is nothing left to release. */
    private fun releaseHeld()
    {
        val wal = heldWallet ?: return
        val ops = heldOutpoints
        val addrs = boundAddresses
        heldWallet = null
        heldOutpoints = emptySet()
        boundAddresses = emptyList()
        giveBack(wal, ops, addrs)
    }

    /** What the wallet could spend right now.  One pass over the UTXOs, no completion. */
    private fun spendableFingerprint(wal: Wallet): SpendableFingerprint
    {
        var native = 0L
        var count = 0
        val tokens = mutableMapOf<GroupId, Long>()
        wal.forEachUtxo { sp ->
            if (sp.isUnspent && sp.reserved == 0L)
            {
                count++
                val gi = sp.groupInfo()
                if (gi == null) native += sp.amount
                else if (!gi.isAuthority()) tokens[gi.groupId] = (tokens[gi.groupId] ?: 0L) + gi.tokenAmount
            }
            false
        }
        return SpendableFingerprint(native, tokens, count, TdppPartialLedger.candidates(wal, host, topic).size)
    }

    /** Run a completion attempt on [tx] and settle what it holds: a failed attempt gives everything back right away (and finds out
     * whether coins reserved by earlier partial transactions would have helped), a successful one is recorded as holding its
     * reservations and the addresses it consumed, to be given back by [releaseHeld] if the proposal is discarded.
     * Whatever the previous attempt held is released first. */
    private fun attemptCompletion(tx: iTransaction): TxAnalysisResults
    {
        releaseHeld()
        val res = analyzeCompleteAndSignTx(tx, inputSatoshis, tflags)
        val override = payAddressOverride()
        val addrs = boundAddressesOf(res.account.wallet, tx, override)
        if (res.completionException == null)
        {
            hold(res.account.wallet, tx, addrs)
            return res
        }
        // The completer leaves what it managed to pull in reserved (and in the tx) when it fails: hold nothing while waiting
        giveBack(res.account.wallet, completerAddedOutpoints(tx), addrs)
        if (!res.isInsufficientFunds) return res
        val wal = res.account.wallet
        val helps = reservedCoinsWouldHelp(wal, override)
        val tokens = helps && usableReservedCoins(wal, TdppPartialLedger.candidates(wal, host, topic)).any { it.groupInfo()?.isAuthority() == false }
        return res.copy(reservedCoinsWouldHelp = helps, reservedCoinsIncludeTokens = tokens)
    }

    /** Publish [result] as the current proposal */
    private fun publish(tx: iTransaction, result: TxAnalysisResults, fingerprint: SpendableFingerprint?)
    {
        proposedTx = tx
        proposalAnalysis.value = result
        totalNexaSpent = result.myInputSatoshis - result.receivingSats
        lastFingerprint = fingerprint
        proposalRevision.value = proposalRevision.value + 1
    }

    /** No more automatic work on this proposal, and nothing stays held by it */
    private fun closeProposal()
    {
        proposalClosed = true
        stopWatching()
        releaseHeld()
    }

    private fun beginBusy()
    {
        if (busy.incrementAndGet() == 1) analyzing.value = true
    }

    private fun endBusy()
    {
        if (busy.decrementAndGet() == 0) analyzing.value = false
    }

    /** Try again to complete the proposal from the request's original transaction.
     *
     * Only meaningful for a proposal that came from a request (originalTx != null) and has not been decided.  Unless [force]d, nothing
     * happens if the current analysis succeeded, or if it failed for lack of funds and nothing spendable has changed since.  A failed
     * attempt holds nothing afterwards, and the analysis flow is never set to null (the screen would go away), so the previous result
     * stays on screen until the new one replaces it.  Once the screen has gone away undecided (see [screenHidden]) nothing runs until
     * it comes back.
     *
     * Runs a transaction completion, so must not be called on the UI thread.
     * @return true if this call ran a completion attempt and it succeeded (false when nothing was attempted)
     */
    fun reanalyze(force: Boolean = false): Boolean
    {
        return proposalLock.lock {
            if (originalTx == null || accepted || proposalClosed) return@lock false
            // The screen went away with the proposal undecided (see screenHidden): nothing to show a result to, and a successful attempt
            // would hold coins nobody is looking at.  It is re-run when the screen comes back (startWatching).
            if (needsReanalysis && !watching) return@lock false
            val cur = proposalAnalysis.value
            val wal = try { getRelevantAccount().wallet } catch (e: WalletInvalidException) { return@lock false }
            var forced = force || needsReanalysis

            // If a coin the current proposal depends on has since been spent: a coin the request itself named means the server's
            // transaction can never be completed now (terminal), a coin the completer picked just means the funds moved (try again).
            proposedTx?.let { tx ->
                val named = requestNamedOutpoints()
                val heldW = heldWallet ?: wal
                for (inp in tx.inputs)
                {
                    val op = inp.spendable.outpoint ?: continue
                    val isNamed = op in named
                    if (!isNamed && op !in heldOutpoints) continue  // a failed attempt's pick: nothing of ours rides on it
                    val txo = heldW.getTxo(op) ?: continue
                    if (txo.isUnspent) continue
                    if (isNamed)
                    {
                        LogIt.info(sourceLoc() + ": input ${op.toHex()} named by the TDPP request has been spent; proposal cannot be completed")
                        closeProposal()
                        if (cur != null) proposalAnalysis.value = cur.copy(completionException = TdppException(S.staleTransaction, "input ${op.toHex()} was spent"), reservedCoinsWouldHelp = false)
                        return@lock false
                    }
                    forced = true
                }
            }
            if (!forced && cur != null && !cur.isInsufficientFunds) return@lock false  // Nothing to fix

            if (forced) releaseHeld()  // so that the fingerprint (and the attempt) see the coins the previous attempt was holding
            val fp = spendableFingerprint(wal)
            if (!forced && fp == lastFingerprint) return@lock false  // Same coins as the last failed attempt: it would fail the same way

            beginBusy()
            try
            {
                val tx = cloneOriginalTx() ?: return@lock false
                val result = try
                {
                    attemptCompletion(tx)
                }
                catch (e: WalletInvalidException)
                {
                    return@lock false
                }
                publish(tx, result, fp)
                LogIt.info(sourceLoc() + ": re-analysed TDPP proposal (revision ${proposalRevision.value}): " + (result.completionException?.let { "cannot complete: ${it.message?.substringBefore('\n')}" } ?: "can be completed"))
                // If the screen went away while we were working, its queued releaseIfUndecided runs right after us (it needs this lock)
                // and gives back what this attempt holds.
                needsReanalysis = false
                if (watching && watchedWallet !== result.account.wallet)  // The account changed under us: follow it
                {
                    stopWatching()
                    startWatching()
                }
                result.completionException == null
            }
            finally
            {
                endBusy()
            }
        }
    }

    /** Ask for [reanalyze] to run on a worker thread.  Requests are coalesced into the one queued or running job; a force requested while a
     * job is running makes it run once more before it finishes, so a forced request is never lost. */
    fun requestReanalysis(force: Boolean)
    {
        if (originalTx == null || accepted || proposalClosed) return
        if (force) forceRequested.value = true
        if (busy.value > 0 && !force) return
        onetlater("tdppReanalyze_$sessionId") {
            do
            {
                try
                {
                    reanalyze(forceRequested.getAndSet(false))
                }
                catch (e: Exception)
                {
                    logThreadException(e, "re-analysing TDPP proposal")
                }
            } while (forceRequested.value)
        }
    }

    /** While the permission screen is showing, re-check an uncompletable proposal whenever the wallet sees a transaction, and every
     * [TDPP_RECHECK_INTERVAL_MS] regardless (the fingerprint check keeps the periodic re-check cheap when nothing changed). */
    fun startWatching()
    {
        if (originalTx == null || proposalClosed) return
        watchLock.lock {
            if (watching) return@lock
            val wal = proposalAnalysis.value?.account?.wallet ?: try { getRelevantAccount().wallet } catch (e: WalletInvalidException) { return@lock }
            watching = true
            watchedWallet = wal
            // Only a real transaction event (non-null list) is worth an immediate re-check; header syncs, aborts and similar pass null
            watchHandle = wal.setOnWalletChange { _, changed -> if (changed != null) requestReanalysis(false) }
            wallyApp?.threadJobPool?.periodically("tdppRecheck_$sessionId", TDPP_RECHECK_INTERVAL_MS) { requestReanalysis(false) }
        }
        if (needsReanalysis) requestReanalysis(true)
    }

    fun stopWatching()
    {
        watchLock.lock {
            if (!watching) return@lock
            watching = false
            wallyApp?.threadJobPool?.stopPeriodicJob("tdppRecheck_$sessionId")
            watchedWallet?.let { if (watchHandle >= 0) it.removeOnWalletChange(watchHandle) }
            watchHandle = -1
            watchedWallet = null
        }
    }

    /** The permission screen went away (back, another screen on top, activity gone) with the proposal still undecided: stop watching now,
     * give back what the proposal is holding, and make sure coming back re-analyses instead of accepting stale state. */
    fun screenHidden()
    {
        if (originalTx == null) return
        stopWatching()
        tlater("tdppRelease_$sessionId") { releaseIfUndecided() }
    }

    /** Give back what the current attempt is holding but keep the analysis on screen; an undecided proposal is marked for re-analysis */
    fun releaseIfUndecided()
    {
        proposalLock.lock {
            if (originalTx == null) return@lock
            if (!accepted && !proposalClosed) needsReanalysis = true
            releaseHeld()
        }
    }

    /** A newer request from the same site replaced this proposal */
    fun supersede()
    {
        proposalLock.lock { closeProposal() }
    }

    /** Accept the proposal unless it is closed, being re-analysed, waiting for a re-analysis, or could not be completed.
     * Never blocks (safe on the UI thread).
     * @return false if the proposal was not accepted
     */
    fun tryAccept(breakIt: Boolean = false): Boolean
    {
        return proposalLock.trylock {
            if (busy.value > 0 || accepted || proposalClosed || (originalTx != null && needsReanalysis) || proposalAnalysis.value?.completionException != null) false
            else
            {
                acceptSpecialTx(breakIt)
                true
            }
        } ?: false
    }

    // ---- "Use reserved coins" ----

    /** The coins that earlier, never completed partial transactions from this same site are still holding reserved, restricted to the
     * kind the request could actually use: NEXA coins if the request lets the wallet fund NEXA, token coins only of the groups the
     * request's outputs carry and only if it lets the wallet fund groups. */
    private fun usableReservedCoins(wal: Wallet, entries: List<TdppPartialLedger.Entry>): List<Spendable>
    {
        val cflags = completionFlags(tflags)
        val wantsNative = (cflags and TxCompletionFlags.FUND_NATIVE) != 0
        val wantedGroups: Set<GroupId> = if ((cflags and TxCompletionFlags.FUND_GROUPS) != 0)
            originalTx?.outputs?.mapNotNull { it.script.groupInfo(it.amount) }?.filter { !it.isAuthority() }?.map { it.groupId }?.toSet() ?: emptySet()
        else emptySet()
        return TdppPartialLedger.stillReserved(wal, entries).filter { sp ->
            val gi = sp.groupInfo()
            if (gi == null) wantsNative else (!gi.isAuthority() && gi.groupId in wantedGroups)
        }
    }

    /** Would the request complete if the usable reserved coins were available?  Answered by a completion attempt that supplies those coins
     * as explicit inputs (explicit inputs are used as given, so their reservations are neither checked nor changed) and gives back
     * everything it selects afterwards.  The attempt only asks whether the transaction can be funded, so it neither signs nor runs the
     * final sanity checks (which would abort the whole trial transaction, reserved coins included). */
    private fun reservedCoinsWouldHelp(wal: Wallet, override: PayAddress?): Boolean
    {
        val cands = usableReservedCoins(wal, TdppPartialLedger.candidates(wal, host, topic))
        if (cands.isEmpty()) return false
        val trial = cloneOriginalTx() ?: return false
        bindOwnInputs(trial, wal)
        val keep = mutableSetOf<iTxOutpoint>()
        var extra = 0L
        for (sp in cands)
        {
            trial.add(txInputFor(sp), SPENDABLE_UNRESERVED)
            keep.add(sp.outpoint!!)
            if (sp.groupInfo() == null) extra += sp.amount  // a token coin's few native satoshis are not what the request is short of
        }
        val flags = completionFlags(tflags, sign = false) or TxCompletionFlags.PARTIAL
        val ok = try
        {
            wal.txCompleter(trial, 0, flags, inputSatoshis + extra, destinationAddress = override)
            true
        }
        catch (e: Exception)
        {
            LogIt.info(sourceLoc() + ": proposal would still not complete with ${cands.size} reserved coins: ${e.message?.substringBefore('\n')}")
            false
        }
        finally
        {
            giveBack(wal, completerAddedOutpoints(trial) - keep, boundAddressesOf(wal, trial, override))
        }
        if (ok) LogIt.info(sourceLoc() + ": proposal would complete with ${cands.size} coins reserved by earlier partial transactions from $host")
        return ok
    }

    /** Re-attempt the request, treating the usable coins that earlier partial transactions from this same site reserved as available.
     *
     * The coins are released only after the network confirms every one of them is still unspent: in the "wallet has not caught up
     * yet" case the earlier transaction may already have spent them, and releasing them then would just produce a transaction the
     * network rejects.  Nothing is sent to the server; a subsequent Accept replies to the request as usual.
     *
     * Does network I/O and a completion, so must not be called on the UI thread; see [requestRetryIgnoringReserved].
     * @return true if the request was re-attempted with the reserved coins and can now be accepted
     */
    fun retryIgnoringReserved(): Boolean
    {
        if (originalTx == null || proposalClosed || accepted) return false
        val wal = proposalAnalysis.value?.account?.wallet ?: return false
        if (busy.incrementAndGet() != 1)  // one retry (or analysis) at a time
        {
            busy.decrementAndGet()
            return false
        }
        analyzing.value = true
        try
        {
            val entries = TdppPartialLedger.candidates(wal, host, topic)
            val cands = usableReservedCoins(wal, entries)
            if (cands.isEmpty()) return false
            // 1. Ask the network while the coins are still reserved; every one of them needs an affirmative answer.  No connected node, or
            //    a node that cannot answer (an old protocol version), is the same as no answer: nothing is released.
            val nodes = wal.blockchain.net.getNodes()
            val reply = if (nodes.isEmpty()) listOf() else try
            {
                wal.blockchain.req.getUtxo(cands.map { it.outpoint!!.toByteArray(SerializationType.NETWORK) })
            }
            catch (e: Exception)
            {
                LogIt.info(sourceLoc() + ": could not ask the network about reserved coins: $e")
                listOf()
            }
            val ok = cands.filter { sp -> reply.firstOrNull { it.outpoint == sp.outpoint }?.let { it.exists && !it.spent } == true }
            if (ok.size != cands.size)
            {
                LogIt.info(sourceLoc() + ": ${cands.size - ok.size} of ${cands.size} reserved coins could not be confirmed unspent by ${nodes.size} nodes; keeping them reserved")
                displayNotice(S.TpReservedCoinsUnverified)
                return false
            }
            // 2. Release and re-analyse under the one lock so nobody else can grab the coins in between
            return proposalLock.lock {
                if (proposalClosed || accepted) return@lock false
                if (getRelevantAccount().wallet !== wal) return@lock false  // the account changed while we asked the network; the screen re-analyses
                TdppPartialLedger.release(wal, entries, ok.map { it.outpoint!! }.toSet())
                val completable = reanalyze(force = true)
                if (completable) usingReservedCoins.value = true
                completable
            }
        }
        finally
        {
            endBusy()
        }
    }

    /** Run [retryIgnoringReserved] on a worker thread (one at a time), reporting an unexpected failure instead of losing it */
    fun requestRetryIgnoringReserved()
    {
        onetlater("tdppUseReserved_$sessionId") {
            try
            {
                retryIgnoringReserved()
            }
            catch (e: Exception)
            {
                logThreadException(e, "retrying TDPP proposal with reserved coins")
                displayError(S.unknownError, e.message)
            }
        }
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
        rproto = uri.getQueryParameter("rproto")  // Load up the specific reply protocol, if there is one

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

        populateRelevantAccounts()
    }

    fun getRelevantAccount(preferredAccount: String? = null): Account
    {
        val preferred = if (!preferredAccount.isNullOrEmpty()) wallyApp?.accountLock?.lock { wallyApp?.accounts?.get(preferredAccount) } else null
        return preferred ?: pill.account.value ?: throw WalletInvalidException()
    }
    fun populateRelevantAccounts(preferredAccount: String? = null)
    {
        val cs = chainSelector
        // this api finds all accounts of the passed chainselector, and puts the selected one first
        val walChoices = if (cs == null) wallyApp!!.orderedAccounts(true) else wallyApp!!.accountsFor(cs)
        if (walChoices.size == 0)
        {
            throw WalletInvalidException()
        }
        pill.choices = walChoices
        // If this domain is bound to an account from a prior identity registration, prefer it so the
        // pill (and everything that reads from it) reflects the account that will actually serve the
        // request. Fall back to the focused account when no binding exists.
        val boundName = domain?.accountName
        val bound = if (boundName.isNullOrEmpty()) null else wallyApp!!.accountLock.lock { wallyApp!!.accounts[boundName] }
        val tmp = bound ?: wallyApp!!.preferredVisibleAccountOrNull()
        pill.account.value = if (walChoices.contains(tmp)) tmp else walChoices[0]
    }

    fun rejectSendToRequest()
    {
        val urlStr = "$replyProtocol://$hostAndPort/sendto?$cookieParam&resultcode=300"
        val postData = TricklePaySendToReply(300, "", "", "", "")
        val postStr = Json.encodeToString(TricklePaySendToReply.serializer(), postData)
        respondWithError(urlStr, postStr)
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
        val postData = TricklePaySendToReply(200, tx.id.toHex(), tx.idem.toHex(), tx.toHex(), "")
        val js = Json

        val postStr = js.encodeToString(TricklePaySendToReply.serializer(), postData)
        val urlStr = "$replyProtocol://$hostAndPort/sendto?$cookieParam"
        respondWith(urlStr, postStr)
    }

    fun respondWithError(url: String, postResp: String)
    {
        val wd = whenDone
        if (wd != null) wd.invoke(url, postResp, true)
        else
        {
            wallyApp?.later {
                val client = PlatformHttpClient()
                try
                {
                    client.post(url) { setBody(postResp) }
                }
                catch (_: Exception)
                {
                }
                client.close()
            }
        }
    }
    fun respondWith(url: String, postResp: String)
    {
        val wd = whenDone
        if (wd != null) wd.invoke(url, postResp, true)
        else
        {
            wallyApp?.later {
                LogIt.info("responding to server")
                val client = PlatformHttpClient()
                {
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

    fun rejectAddressRequest()
    {
        val url = "$replyProtocol://$hostAndPort/address?$cookieParam&resultcode=300"
        respondWithError(url, "")
    }

    fun acceptAddressRequest(): String
    {
        LogIt.info("accepted address request")
        val url = "$replyProtocol://$hostAndPort/address?$cookieParam&resultcode=200"

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

    fun rejectAssetRequest()
    {
        try
        {
            val url = replyProtocol + "://$hostAndPort/assets?$cookieParam&resultcode=300"
            respondWithError(url, "")
        }
        catch(_: Exception) // This is a best effort response
        {

        }
    }

    fun acceptAssetRequest():String
    {
        LogIt.info("accepted asset request")
        val assets = assetInfoList ?: TricklePayAssetList(listOf())

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
                val client = PlatformHttpClient()
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
                        LogIt.info("JSON response ${tmp.length} : ${tmp}")
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

    fun rejectSpecialTx()
    {
        val rp = replyProtocol
        val hp = hostAndPort
        val cp = cookieParam
        // Close right away (a running re-analysis then publishes into a closed proposal and its holdings are released below), but never
        // block the caller, which may be the UI thread, on a worker that is in the middle of a completion.
        proposalClosed = true
        stopWatching()
        val aborted = proposalLock.trylock { abortProposal(); true } ?: false
        if (!aborted) laterJob("tdppAbort_$sessionId") { proposalLock.lock { abortProposal() } }
        laterJob {
            val req = Url(rp + "://" + hp + "/tx?$cp&resultcode=300")  // 300 is user reject: https://spec.nexa.org/dpp/#pay-transaction-response
            LogIt.info("Sending special tx reject response: ${req}")
            try
            {
                req.readText(HTTP_REQ_TIMEOUT_MS)
            }
            catch (_: Exception)  // Best-effort reject notification but if it doesn't work out no big deal
            {
            }
        }
    }

    /**
     * Accept the proposed TDPP transaction.
     *
     * @param breakIt If true, mangle the signed tx for testing (drops a random input or blanks a
     *     random input's signature). Forces noPost — the mangled tx is not broadcast on chain.
     */
    fun acceptSpecialTx(breakIt: Boolean = false)
    {
        LogIt.info(sourceLoc() + ": accept trickle pay special transaction")
        proposalLock.lock {
            if (originalTx != null && proposalClosed)
            {
                LogIt.warning(sourceLoc() + ": ignoring accept of a TDPP proposal that is already closed")
                return@lock
            }
            accepted = true
            proposalClosed = true
            stopWatching()
            acceptSpecialTxLocked(breakIt)
        }
    }

    private fun acceptSpecialTxLocked(breakIt: Boolean)
    {
        val pTx = proposedTx
        val panalysis = proposalAnalysis

        if ((pTx != null)&&(panalysis.value != null))
        {
            // The wallet that funded and signed the proposal is the one that must submit it and that holds its reservations
            val analysisWallet = panalysis.value!!.account.wallet
            if (breakIt)
            {
                val choice = (0..2).random()
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
            // Consumed for real now: the reservations belong to the submitted transaction, the addresses are used
            val added = if (originalTx != null) heldOutpoints else emptySet()
            heldWallet = null
            heldOutpoints = emptySet()
            boundAddresses = emptyList()
            LogIt.info(sourceLoc() + ": sign trickle pay special transaction")

            // TODO: put a record of this transaction somewhere so when it is completed by the server we can annotate the history with a reason.
            // This is tricky because it may not be fully formed so idem will change.  Maybe look up by signature
            // tx reason: i18n(R.string.title_activity_trickle_pay) + " " + domainAndTopic + ". " + reason)

            wallyApp?.let { app ->
                val noPost = (tflags and TDPP_FLAG_NOPOST) != 0 || breakIt
                // TDPP_FLAG_PARTIAL means the originator intends to complete this tx later (e.g.,
                // a marketplace storing a firm offer until a buyer matches). Even if every input we
                // can see is signed, the tx is not yet broadcastable as-is.
                val isPartial = (tflags and TDPP_FLAG_PARTIAL) != 0
                var completed = !isPartial && pTx.inputs.isNotEmpty()
                for (inp in pTx.inputs)
                {
                    if (inp.script.size == 0)
                    {
                        LogIt.warning(sourceLoc() + ": TDPP special transaction: Transaction incomplete, counterparty still needs to sign")
                        completed = false
                    }
                }
                // A partial transaction (or one the server keeps to itself) leaves the coins the completer reserved for it held with nothing
                // in the wallet to say by whom; remember them so a later request from the same site can offer to reuse them if the server
                // abandons the transaction.
                if ((!completed || noPost) && added.isNotEmpty()) TdppPartialLedger.record(analysisWallet, host, topic, pTx, added)

                submitTdppCompletion(
                  pTx = pTx,
                  wallet = analysisWallet,
                  completed = completed,
                  noPost = noPost,
                  replyProtocol = replyProtocol,
                  hostAndPort = hostAndPort,
                  cookieParam = cookieParam,
                )
            }
        }
    }

    /* Based on this session, should I ACCEPT, ASK, or DENY? */
    fun determineAction(domain: TdppDomain?): TdppAction
    {
        if (domain == null) return TdppAction.DENY
        askReasons.clear()

        val pa = proposalAnalysis.value
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
            // A proposal that could not be completed cannot be paid automatically; showing it lets the wallet keep re-checking it
            // (the funds may arrive as the wallet syncs) instead of replying with an unfunded transaction.
            return if (pa?.completionException != null) TdppAction.ASK else TdppAction.ACCEPT
        }
        return TdppAction.ASK
    }

    /* Process a tx completion proposal, and decide what to do about it */
    fun handleTxAutopay(uri: Uri): TdppAction
    {
        proposalUrl = uri
        proposedTx = null
        proposedDestinations = null

        val txBin = run {
            val txHex = uri.getQueryParameter("tx")
            if (txHex == null)
            {
                val txB64u = uri.getQueryParameter("tx64")
                if (txB64u == null) throw TdppException(S.BadWebLink, "missing tx parameter")
                txB64u.fromBase64Url()
            }
            else txHex.fromHex()
        }

        parseCommonFields(uri)

        tflags = uri.getQueryParameter("flags")?.toInt() ?: 0

        // If we are funding then inputSatoshis must be provided
        val tmp = uri.getQueryParameter("inamt")?.toLongOrNull()  // ?: return displayError(R.string.BadLink)
        if ((tmp == null) && ((tflags and TDPP_FLAG_NOFUND) == 0))
        {
            throw TdppException(S.BadWebLink, "missing inamt parameter")
        }
        inputSatoshis = tmp ?: 0

        val tx = txFor(chainSelector!!, BCHserialized(txBin, SerializationType.NETWORK))
        //originalTx = tx.clone()
        originalTx = txFor(chainSelector!!, BCHserialized(txBin, SerializationType.NETWORK))  // TODO tx.clone()
        LogIt.info(sourceLoc() + ": Tx to autopay: " + tx.toHex())

        // Analyze and sign transaction
        proposalLock.lock {
            val fp = spendableFingerprint(getRelevantAccount().wallet)
            val analysis = attemptCompletion(tx)
            LogIt.info(sourceLoc() + ": Completed tx: " + tx.toHex())
            publish(tx, analysis, fp)  // save the final tx to be issued later if user agrees and no problems
        }
        var action = determineAction(domain)
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
            addrAmt.add(Pair(addrPa, amt))
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

        if (d.assetInfo == TdppAction.DENY)
        {
            // TODO
            return TdppAction.DENY
        }

        val scriptTemplateHexList = uri.getQueryParameters("af")
        if (scriptTemplateHexList.isEmpty())
        {
            throw TdppException(S.BadWebLink, "missing 'af' parameter")
        }

        val chalbyStr = uri.getQueryParameter("chalby")
        val chalby = chalbyStr?.fromHex()

        val acc = getRelevantAccount(d.accountName)
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
                    if (gi != null && !gi.isAuthority())
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

        /*  TODO -- right now always accepted because it comes from a QR code
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
                whenDone?.invoke(url, addr, true) ?: run {
                    wallyApp?.post(url, {
                        it.setBody(addr)
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
        try
        {
            parseCommonFields(iuri, false)
        }
        catch(e: TdppException)
        {
            return TdppAction.DENY
        }
        val result = handleSendToAutopay(iuri)
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
        val result = handleTxAutopay(iuri)
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
        val receivingTokenInfo = mutableMapOf<GroupId, Long>()
        val sendingTokenInfo = mutableMapOf<GroupId, Long>()
        val myInputTokenInfo = mutableMapOf<GroupId, Long>()

        val cflags = completionFlags(flags)

        // Look at the inputs and match with UTXOs that I have, so I have the additional info required to sign this input
        bindOwnInputs(tx, wal)

        // Complete and sign the transaction
        var completionException: Exception? = null
        try
        {
            wal.txCompleter(tx, 0, cflags, inputSatoshis, destinationAddress = payAddressOverride())
        }
        catch (e: Exception)  // Try to report on the tx even if we can't complete it.
        {
            completionException = e
        }

        var foreignUnsignedInputs = listOf<Int>()
        if (completionException != null)
            foreignUnsignedInputs = tx.inputs.withIndex().filter { it.value.script.size == 0 && wal.getTxo(it.value.spendable.outpoint!!) == null }.map { it.index }

        // LogIt.info("Completed tx: " + tx.toHex())

        for (inp in tx.inputs)
        {
            val address = inp.spendable.address
            if ((address != null) && (wal.isWalletAddress(address)))  // Is this coming from this wallet?
            {
                assert(inp.spendable.amount != -1L)  // Its -1 if I don't know the amount (in which case it ought to NOT be one of my inputs so should never happen)
                iFunded += inp.spendable.amount

                val iSuppliedTokens = inp.spendable.priorOutScript.groupInfo(inp.spendable.amount)
                if (iSuppliedTokens != null)
                {
                    imSpendingTokenTypes++
                    wallyApp?.assetManager?.track(iSuppliedTokens.groupId)
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
                    wallyApp?.assetManager?.track(groupInfo.groupId)
                    receivingTokenInfo[groupInfo.groupId] = (receivingTokenInfo[groupInfo.groupId] ?: 0) + groupInfo.tokenAmount
                }
            }
            else
            {
                sendingSats += out.amount
                if ((groupInfo != null) && (!groupInfo.isAuthority()))
                {
                    wallyApp?.assetManager?.track(groupInfo.groupId)
                    sendingTokenTypes++
                    sendingTokenInfo[groupInfo.groupId] = (sendingTokenInfo[groupInfo.groupId] ?: 0) + groupInfo.tokenAmount
                }
            }
        }

        // Net ignores anything others are doing
        val myNetTokenInfo = mutableMapOf<GroupId, Long>()

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

        val avm = AssetViewModel(false)
        val alst = mutableListOf<AssetInfo>()
        for ((gid, amt) in myNetTokenInfo)
        {
            val ai = wallyApp?.assetManager?.assets[gid]  // This will always resolve because we call track above
            if (ai != null) alst.add(ai)
        }
        avm.amounts.value = myNetTokenInfo
        avm.assets.value = alst
        LogIt.info("trickle pay assets ${alst.size} myNetTokenInfo: ${myNetTokenInfo.size}\n$alst, ")

        return TxAnalysisResults(act, receivingSats, sendingSats, receivingTokenTypes, sendingTokenTypes, imSpendingTokenTypes, inputSatoshis, iFunded,
          myInputTokenInfo, sendingTokenInfo, receivingTokenInfo, myNetTokenInfo, avm, completionException, foreignUnsignedInputs)
    }
}


fun HandleTdpp(iuri: Uri, then: ((String, String, Boolean?)->Unit)?= null): Boolean
{
    val bkg = wallyApp!!.amIbackground()  // if the app is backgrounded, we need to notify and not just change the GUI
    val scheme = iuri.scheme
    val path = iuri.path

    // For certain screens, just ignore duplicate requests
    var superseded = false
    if (nav.currentScreen.value == ScreenId.SpecialTxPerm)
    {
        val ctp = nav.curData.value as? TricklePaySession
        if (ctp != null)
        {
            if (ctp.proposalUrl == iuri) return false
            // A different transaction request from the same site while its previous one is still being shown: the site has moved on
            // (a new game, a re-issued offer), so drop the old proposal rather than stacking the two.
            if (path == "/tx" && ctp.originalTx != null && ctp.host == iuri.host && (ctp.topic ?: "") == (iuri.getQueryParameter("topic") ?: ""))
            {
                LogIt.info(sourceLoc() + ": TDPP request from ${ctp.host} supersedes the proposal being shown")
                ctp.supersede()
                nav.back()
                superseded = true
            }
        }
    }

    if (scheme?.lowercase() == TDPP_URI_SCHEME)
    {
        val tp = TricklePaySession(wallyApp!!.tpDomains, then)
        tp.parseCommonFields(iuri, true)
        val address = iuri.getQueryParameter("addr")
        if (path == "/reg")  // Handle registration
        {
            tp.newDomain = true
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

            clearAlerts()  // If the user explicitly moved to a different screen, they must be aware of the alert
            nav.go(ScreenId.TricklePayRegistrations, data = tp)
            nav.go(ScreenId.TricklePayRegistrations, screenSubState = "true".toByteArray(), data = tp)
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

                TdppAction.DENY ->
                {
                    tp.rejectAddressRequest()
                    return true // true because "autoHandle" returns whether the intent was "handled" automatically -- denial is handling it
                }
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

                TdppAction.DENY ->
                {
                    tp.rejectAssetRequest()
                    return true  // true because "autoHandle" returns whether the intent was "handled" automatically -- denial is handling it
                }
            }
        }
        else if (path == "/tx")
        {
            val result = try
            {
                tp.attemptSpecialTx(iuri)
            }
            catch (e: Exception)
            {
                if (superseded) displayNotice(S.TpProposalSuperseded)  // the proposal that was on screen is gone whatever became of the replacement
                throw e
            }
            if (superseded && result != TdppAction.ASK) displayNotice(S.TpProposalSuperseded)  // for ASK it is shown after nav.go, which clears alerts
            when(result)
            {
                TdppAction.ASK ->  // special tx
                {
                    nav.go(ScreenId.SpecialTxPerm, data = tp)
                    if (superseded) displayNotice(S.TpProposalSuperseded)
                    return false
                }
                TdppAction.ACCEPT ->  // special tx auto accepted
                {
                    tp.acceptSpecialTx()
                }
                // special tx auto-deny
                TdppAction.DENY ->
                {
                    tp.rejectSpecialTx()
                    return true // true because "autoHandle" returns whether the intent was "handled" automatically -- denial is handling it
                }
            }
        }

    }
    return false
}


// ---------- TDPP submit-and-confirm helper ----------

private const val TDPP_NETWORK_TIMEOUT_MS: ULong = 60_000UL

private val tdppPendingLock = Mutex()
private val tdppPendingIdems = mutableSetOf<Hash256>()

fun isTdppPending(idem: Hash256): Boolean = tdppPendingLock.lock { tdppPendingIdems.contains(idem) }
private fun registerTdppPending(idem: Hash256)
{
    tdppPendingLock.lock { tdppPendingIdems.add(idem) }
}

private fun deregisterTdppPending(idem: Hash256)
{
    tdppPendingLock.lock { tdppPendingIdems.remove(idem) }
}

/**
 * Submit a TDPP-completed tx and resolve to a user-facing outcome.
 * Owns all user-facing feedback for this tx (success animation, error/warning/notice displays).
 *
 *  - completed = true  → the tx is fully signed. Success is shown once, as soon as the wallet
 *                        observes the tx land in the mempool (confirmedHeight == -1). The wallet
 *                        observer then stays registered (keeping the idem in tdppPendingIdems so
 *                        account.kt stays quiet) until the tx is mined, at which point it releases
 *                        the guard without re-animating. Originator response is advisory on success
 *                        and authoritative on error. 60s timeout fallback if the tx is never seen.
 *  - completed = false → the tx is a partial (firm offer / multi-party proposal). Nobody can
 *                        broadcast it as-is, and the buyer's eventual completion will have a
 *                        different idem so the wallet observer can never match. Originator
 *                        response is the only signal — no observer, no timeout.
 */
fun submitTdppCompletion(
  pTx: iTransaction,
  wallet: Wallet,
  completed: Boolean,
  noPost: Boolean,
  replyProtocol: String,
  hostAndPort: String,
  cookieParam: String,
)
{
    val idem = pTx.idem
    val resolved = atomic(false)
    val handleRef = atomic(-1)

    registerTdppPending(idem)

    fun cleanup()
    {
        deregisterTdppPending(idem)
        val h = handleRef.value
        if (h >= 0) wallet.removeOnWalletChange(h)
    }

    LogIt.info(sourceLoc() + " TDPP submit: idem=${idem.toHex()} completed=$completed noPost=$noPost hostAndPort=$hostAndPort")

    //Setup callback handlers and timeout
    if (completed)
    {
        // walletChanged is invoked with a non-null list at only a couple of sites in libnexakotlin;
        // the majority of invocations pass null. Look the tx up by idem on every callback so we don't
        // miss a state change that's signaled by a null-payload notification.
        handleRef.value = wallet.setOnWalletChange { _, _ ->
            val txh = wallet.getTx(idem) ?: return@setOnWalletChange
            val height = txh.confirmedHeight   // -1 = mempool, >0 = mined, Long.MIN_VALUE = rejected


            //REJECTED
            if (height == Long.MIN_VALUE)
            {
                // Network refused or evicted the tx.
                if (resolved.compareAndSet(false, true))
                {
                    // Same UI-settle race as the eviction handler at account.kt:446-450.
                    laterJob {
                        millisleep(1000U)
                        displayWarning(i18n(S.staleTransaction), i18n(S.staleTransactionDetails))
                    }
                }
                cleanup()
                return@setOnWalletChange
            }

            // ACCEPTED (mempool)
            // The accepted tx is in our wallet (mempool). Show acceptance success exactly once;
            // `resolved` gates against the repeated walletChange callbacks fired by unrelated
            // transactions -- getTx returns our tx every time, but we act only on the first.
            if (resolved.compareAndSet(false, true))
            {
                LogIt.info(sourceLoc() + " TDPP accepted: idem=${idem.toHex()} height=$height")
                txh.relatedTo["TDPP"] = byteArrayOf(1)
                laterJob {
                    specialTxSuccessAnimationIsPlaying.update { it + 1 }
                    // nav.back() runs clearScreenAlerts() right after acceptSpecialTx returns
                    // (ActionPermissionScreens.kt:379-382). If the wallet observer fires before that
                    // happens, the alert gets cleared. Delay so clearScreenAlerts runs first.
                    millisleep(1000U)
                    displaySuccess(S.TpTxAccepted)
                }
            }

            // Keep account.kt's cb1 suppressed (idem stays in tdppPendingIdems) for the ENTIRE
            // window in which cb1 could fire its receive animation -- which is while
            // confirmedHeight >= curHeight-1 (the tx is at the tip or one block below; see
            // account.kt:446). Releasing only once the tx is buried deeper than that makes the
            // suppression independent of callback ordering, of relatedTo persistence, and of
            // interestingTx re-processing the tx on the next block (wallet.kt:6215): while the tx is
            // still in that window cb1 skips it on the idem regardless of who runs first.
            if (height > 1 && wallet.blockchain.curHeight - height > 1) cleanup()
        }

        laterJob("tdppTimeout") {
            millisleep(TDPP_NETWORK_TIMEOUT_MS)
            if (resolved.compareAndSet(false, true))
            {
                LogIt.warning(sourceLoc() + " TDPP network timeout: idem=${idem.toHex()} not observed on network within ${TDPP_NETWORK_TIMEOUT_MS}ms")
                cleanup()
                displayWarning(i18n(S.TpNetworkTimeout))
            }
        }
    }
    // For !completed (partial tx): no observer, no timeout — the originator response below is the
    // only signal. The eventual broadcast (after a buyer completes the offer) will have a different
    // idem than pTx.idem so an observer here would never match anyway.

    // Only broadcast a fully-signed tx. Matches the original code's `if (completed) wallet.send(...)`
    // structure — partial txs can't be broadcast as-is.
    if (completed && !noPost)
    {
        try
        {
            wallet.send(pTx)
        }
        catch (e: Exception)
        {
            logThreadException(e)
            if (resolved.compareAndSet(false, true))
            {
                cleanup()
                // This branch runs synchronously inside acceptSpecialTx, before nav.back().
                // Defer the alert past the impending clearScreenAlerts to avoid losing it.
                laterJob {
                    millisleep(1000U)
                    displayUnexpectedException(e)
                }
            }
            return
        }
    }

    if (hostAndPort.isNotBlank())
    {
        val txHex = pTx.toHex()
        laterJob("tdppOriginatorPost") {
            val req = Url(replyProtocol + "://" + hostAndPort + "/tx?tx=" + txHex + "&" + cookieParam)
            LogIt.info("Sending special tx response: $req")
            val data: String = try
            {
                req.readText(HTTP_REQ_TIMEOUT_MS)
            }
            catch (e: Exception)
            {
                LogIt.info("Error submitting transaction: " + e.message)
                if (resolved.compareAndSet(false, true))
                {
                    cleanup()
                    millisleep(1000U)
                    displayError(S.WebsiteUnavailable)
                }
                return@laterJob
            }
            LogIt.info(sourceLoc() + " TP originator response: $data")

            // Match the originator's response against known error/success patterns. Substring
            // matching mirrors the original logic — keeps the success-by-default behaviour for
            // bodies we don't recognise. "rejected" is added to cover descriptive error strings
            // like "Tx rejected by peer" that don't include the literal "invalid" / "error" tokens.
            val lower = data.lowercase()
            when
            {
                data == "unknown session" ->
                    if (resolved.compareAndSet(false, true))
                    {
                        LogIt.info(sourceLoc() + " TDPP originator response classified as unknown-session")
                        cleanup()
                        // The server never stored the transaction, so nothing can ever complete it: free the coins it was holding
                        if (!completed || noPost) TdppPartialLedger.releaseFor(wallet, pTx)
                        millisleep(1000U)
                        displayWarning(i18n(S.TpNoSession), i18n(S.TpNoSession))
                    }

                lower.contains("invalid") || lower.contains("error") || lower.contains("rejected") ->
                {
                    if (resolved.compareAndSet(false, true))
                    {
                        LogIt.info(sourceLoc() + " TDPP originator response classified as reject: $data")
                        cleanup()
                        millisleep(1000U)
                        displayWarning(i18n(S.badTransaction), data)
                    }
                }

                else ->
                {
                    if (!completed)
                    {
                        // Partial tx — the originator stored the offer, but no transaction has
                        // actually happened (and may never). Acknowledge the listing with a
                        // green banner but NO success animation (which would imply a completed
                        // on-chain tx).
                        if (resolved.compareAndSet(false, true))
                        {
                            LogIt.info(sourceLoc() + " TDPP partial-tx submission accepted by originator")
                            cleanup()
                            millisleep(1000U)
                            displaySuccess(S.TpPartialSubmitted)
                        }
                    }
                    else
                    {
                        // Completed tx — originator's positive response is advisory; wait for the
                        // wallet observer or timeout to resolve.
                        LogIt.info(sourceLoc() + " TDPP originator accepted submission; awaiting network confirmation for idem=${idem.toHex()}: $data")
                    }
                }
            }
        }
    }
}

