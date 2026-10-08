package ui

import com.eygraber.uri.Uri
import info.bitcoinunlimited.www.wally.*
import info.bitcoinunlimited.www.wally.ui.setSelectedAccount
import org.nexa.libnexakotlin.*
import org.nexa.threads.millinow
import org.nexa.threads.millisleep
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val EXTERNAL_ADDR = "nexa:nqtsq5g5fxz9qyup04g288qy2pxf9aemxjysnzqnn2nky4xw"

/** Re-analysis of a special transaction proposal that could not be completed, and the "use reserved coins" retry (work item #691) */
class TricklePayReanalysisTest
{
    init {
        initializeLibNexa()
    }

    val cs = ChainSelector.NEXA

    @BeforeTest
    fun beforeTest()
    {
        wallyApp = CommonApp(true)
        wallyApp!!.onCreate()
        val account = mockAccount()
        wallyApp!!.accounts[account.name] = account
        setSelectedAccount(account)
        TdppPartialLedger.clear()
    }

    @AfterTest
    fun afterTest()
    {
        TdppPartialLedger.clear()
        awaitDeletionsComplete()
        deleteMockAccountDbs()
        wallyApp = null
    }

    /** A real (empty) account that the request will be analysed against */
    private fun makeAccount(): Account
    {
        val account = wallyApp!!.newAccount("tpreanaly", 0U, "", cs) ?: error("could not create the test account (a previous deletion still pending?)")
        setSelectedAccount(account)
        return account
    }

    /** Give [wallet] a coin of [amount] satoshis, as if a transaction paying it had been found in an (ancient) block.  Confirmed on
     * purpose: the wallet rebroadcasts unconfirmed transactions, a real node would reject this made-up one, and the wallet would then
     * drop the coin again. */
    private fun fundWallet(wallet: Wallet, amount: Long): Spendable
    {
        val tx = txFor(cs)
        val from = Spendable(cs, NexaTxOutpoint(Hash256(ByteArray(32) { 7 }), 0), amount + 1000)
        tx.add(txInputFor(from), SPENDABLE_UNRESERVED)
        tx.add(NexaTxOutput(cs, amount, wallet.getNewDestination().address!!.outputScript()))
        (wallet as CommonWallet).interestingTx(listOf(tx), Hash256(ByteArray(32) { 9 }), 1L, 1_000_000_000_000L, "test funding")
        val sp = wallet.getTxo(NexaTxOutpoint(tx.idem, 0))
        assertNotNull(sp, "wallet did not record the funding output")
        assertTrue(sp.isUnspent)
        return sp
    }

    /** A session built from a /tx request that pays [amount] satoshis to an external address, i.e. one that the wallet must fund */
    private fun requestSession(flags: Int = 0, amount: Long = 1000L, host: String = "example.com", topic: String? = null): TricklePaySession
    {
        val tx = txFor(cs)
        tx.add(NexaTxOutput(cs, amount, PayAddress(EXTERNAL_ADDR).outputScript()))
        val domains = TricklePayDomains()
        domains.domainsLoaded = true
        val session = TricklePaySession(domains)
        session.chainSelector = cs
        val topicParam = if (topic != null) "&topic=$topic" else ""
        val uri = Uri.parse("https://$host/tx?host=$host&port=8080&tx=${tx.toHex()}&flags=$flags&inamt=0&blockchain=nexa$topicParam")
        session.handleTxAutopay(uri)
        assertNotNull(session.originalTx)
        assertNotNull(session.proposalAnalysis.value)
        return session
    }

    private fun countReserved(wallet: Wallet): Int
    {
        var n = 0
        wallet.forEachUtxo { if (it.isUnspent && it.reserved != 0L) n++; false }
        return n
    }

    private fun waitUntil(timeoutMs: Long, desc: String, cond: () -> Boolean)
    {
        val start = millinow()
        while (millinow() - start < timeoutMs)
        {
            if (cond()) return
            millisleep(50U)
        }
        error("timed out after ${timeoutMs}ms waiting for: $desc")
    }

    @Test
    fun reanalyzeIsNoOpForHandBuiltSession()
    {
        val session = tricklePaySessionFaker(mockAccount())
        assertNull(session.originalTx)
        assertFalse(session.reanalyze(force = true))
        assertEquals(0, session.proposalRevision.value)
        session.requestReanalysis(true)
        session.startWatching()
        assertFalse(session.analyzing.value)
        session.stopWatching()
        assertNotNull(session.proposalAnalysis.value)
    }

    @Test
    fun insufficientFundsIsClassifiedAndHoldsNothing()
    {
        val account = makeAccount()
        val session = requestSession()
        val pa = session.proposalAnalysis.value!!
        assertEquals(account, pa.account)
        assertTrue(pa.isInsufficientFunds, "expected insufficient funds, got ${pa.completionException}")
        assertFalse(pa.reservedCoinsWouldHelp)
        assertEquals(1, session.proposalRevision.value)
        // A failed attempt keeps nothing reserved
        assertEquals(0, countReserved(account.wallet))
        wallyApp!!.deleteAccount(account)
    }

    @Test
    fun forcedReanalysisNeverPublishesNull()
    {
        val account = makeAccount()
        val session = requestSession()
        val first = session.proposedTx
        repeat(3) {
            assertFalse(session.reanalyze(force = true))
            assertNotNull(session.proposalAnalysis.value)
            assertTrue(session.proposalAnalysis.value!!.isInsufficientFunds)
        }
        assertEquals(4, session.proposalRevision.value)
        assertNotSame(first, session.proposedTx)
        assertEquals(0, countReserved(account.wallet))
        assertFalse(session.analyzing.value)
        wallyApp!!.deleteAccount(account)
    }

    @Test
    fun unforcedReanalysisSkipsWhenNothingSpendableChanged()
    {
        val account = makeAccount()
        val session = requestSession()
        assertFalse(session.reanalyze(force = false))
        assertFalse(session.reanalyze(force = false))
        assertEquals(1, session.proposalRevision.value, "no completion should run while the spendable coins are unchanged")
        wallyApp!!.deleteAccount(account)
    }

    @Test
    fun unforcedReanalysisSkipsACompletableProposal()
    {
        val account = makeAccount()
        val session = requestSession(flags = TDPP_FLAG_NOFUND or TDPP_FLAG_PARTIAL)
        assertNull(session.proposalAnalysis.value!!.completionException)
        assertFalse(session.reanalyze(force = false))
        assertEquals(1, session.proposalRevision.value)
        assertTrue(session.reanalyze(force = true))
        assertEquals(2, session.proposalRevision.value)
        wallyApp!!.deleteAccount(account)
    }

    @Test
    fun insufficientFlipsToCompletableWhenFundsArrive()
    {
        val account = makeAccount()
        val session = requestSession(amount = 1000L)
        assertTrue(session.proposalAnalysis.value!!.isInsufficientFunds)

        val coin = fundWallet(account.wallet, 100_000L)
        val addressBefore = account.wallet.getCurrentDestination().address
        // Not forced: the fingerprint changed, so a completion runs and now succeeds
        assertTrue(session.reanalyze(force = false))
        val pa = session.proposalAnalysis.value!!
        assertNull(pa.completionException)
        assertEquals(2, session.proposalRevision.value)
        assertTrue(pa.myInputSatoshis >= 1000L)
        // The completable proposal holds its inputs (and the change address it consumed) until it is decided...
        assertEquals(1, countReserved(account.wallet))
        assertTrue(coin.reserved != 0L)
        // ...and leaving the screen undecided gives them back, keeping the analysis but refusing to accept it until re-analysed
        session.releaseIfUndecided()
        assertEquals(0, countReserved(account.wallet))
        assertEquals(addressBefore, account.wallet.getCurrentDestination().address, "the change address must be handed back")
        assertNotNull(session.proposalAnalysis.value)
        assertFalse(session.tryAccept(), "a proposal whose holdings were given back must be re-analysed before it can be accepted")
        assertFalse(session.accepted)

        // Releasing is idempotent and only ever touches what the session itself holds: a reservation somebody else takes on the same
        // coin afterwards (an asset offer, another request) is left alone by a second release, a re-analysis, or a supersede
        coin.reserved = 4242L
        session.releaseIfUndecided()
        assertEquals(4242L, coin.reserved)
        session.startWatching()  // coming back to the screen re-analyses
        assertFalse(session.reanalyze(force = false))  // insufficient again: the only coin is somebody else's now
        assertTrue(session.proposalAnalysis.value!!.isInsufficientFunds)
        assertEquals(4242L, coin.reserved)
        session.supersede()
        assertEquals(4242L, coin.reserved)
        wallyApp!!.deleteAccount(account)
    }

    @Test
    fun acceptIsRefusedAfterLeavingOrSupersede()
    {
        val account = makeAccount()
        fundWallet(account.wallet, 100_000L)
        val session = requestSession(flags = TDPP_FLAG_NOFUND or TDPP_FLAG_PARTIAL)
        assertNull(session.proposalAnalysis.value!!.completionException)
        // Leaving the screen undecided: the retained PIN continuation (or a late tap) must not submit the walked-away-from proposal
        session.screenHidden()
        session.releaseIfUndecided()
        assertFalse(session.tryAccept())
        assertFalse(session.accepted)
        // Nothing runs while the screen is away (a late tick, say)...
        assertFalse(session.reanalyze(force = true))
        // ...coming back (startWatching) re-analyses on a worker, after which accepting works again
        val revision = session.proposalRevision.value
        session.startWatching()
        waitUntil(10_000, "re-analysis on return") { session.proposalRevision.value > revision && !session.analyzing.value }
        assertFalse(session.needsReanalysis)
        assertTrue(session.tryAccept())
        assertTrue(session.accepted)
        session.stopWatching()

        val other = requestSession(flags = TDPP_FLAG_NOFUND or TDPP_FLAG_PARTIAL)
        other.supersede()
        assertFalse(other.tryAccept())
        other.acceptSpecialTx()  // the unlocked path is guarded too
        assertFalse(other.accepted)
        wallyApp!!.deleteAccount(account)
    }

    @Test
    fun rejectClosesTheProposal()
    {
        val account = makeAccount()
        val session = requestSession()
        session.host = "127.0.0.1"
        session.port = 1  // the reject notification is best effort; make it fail fast
        session.rejectSpecialTx()
        assertTrue(session.proposalClosed)
        assertNull(session.proposalAnalysis.value)
        assertFalse(session.reanalyze(force = true))
        assertEquals(1, session.proposalRevision.value)
        wallyApp!!.deleteAccount(account)
    }

    @Test
    fun supersedeClosesTheProposalButKeepsTheAnalysis()
    {
        val account = makeAccount()
        val session = requestSession(flags = TDPP_FLAG_NOFUND or TDPP_FLAG_PARTIAL)
        session.supersede()
        assertTrue(session.proposalClosed)
        assertNotNull(session.proposalAnalysis.value)
        assertFalse(session.reanalyze(force = true))
        assertEquals(1, session.proposalRevision.value)
        wallyApp!!.deleteAccount(account)
    }

    @Test
    fun tryAcceptRefusesAFailedProposal()
    {
        val account = makeAccount()
        val session = requestSession()
        assertFalse(session.tryAccept())
        assertFalse(session.accepted)
        assertFalse(session.proposalClosed)
        wallyApp!!.deleteAccount(account)
    }

    @Test
    fun determineActionAsksWhenCompletionFailed()
    {
        val session = tricklePaySessionFaker(mockAccount())
        val domain = TdppDomain("example.com", "", "addr", "NEX", 100, 500, 2000, 5000, "", "", "", "", true, "", "", "")
        session.totalNexaSpent = 0
        // Nothing to ask about: no tokens spent, within the automatic limit, automatic payments enabled
        session.proposalAnalysis.value = session.proposalAnalysis.value!!.copy(imSpendingTokenTypes = 0, completionException = null)
        assertEquals(TdppAction.ACCEPT, session.determineAction(domain))
        session.proposalAnalysis.value = session.proposalAnalysis.value!!.copy(completionException = WalletNotEnoughBalanceException("not enough"))
        assertEquals(TdppAction.ASK, session.determineAction(domain))
        // A policy that denies still denies, whether or not the proposal could be completed
        domain.maxperExceeded = TdppAction.DENY
        session.totalNexaSpent = 1000
        assertEquals(TdppAction.DENY, session.determineAction(domain))
    }

    @Test
    fun acceptedPartialIsRecordedAndOffersItsCoinsToTheNextRequest()
    {
        val account = makeAccount()
        val coin = fundWallet(account.wallet, 100_000L)

        // First request: a partial transaction that the wallet funds and hands to the server (the reply is best effort; fail it fast)
        val first = requestSession(flags = TDPP_FLAG_PARTIAL or TDPP_FLAG_NOPOST, amount = 1000L)
        assertNull(first.proposalAnalysis.value!!.completionException)
        first.port = 1
        first.acceptSpecialTx()
        assertTrue(first.accepted)
        assertTrue(first.proposalClosed)
        assertEquals(1, TdppPartialLedger.size())
        assertEquals(1, countReserved(account.wallet))
        assertTrue(coin.reserved != 0L)
        val candidates = TdppPartialLedger.candidates(account.wallet, "example.com", null)
        assertEquals(1, candidates.size)
        assertEquals(listOf(coin.outpoint), TdppPartialLedger.stillReserved(account.wallet, candidates).map { it.outpoint })
        // Scoped to the site: another host sees nothing
        assertTrue(TdppPartialLedger.candidates(account.wallet, "other.example.com", null).isEmpty())
        assertTrue(TdppPartialLedger.candidates(account.wallet, "example.com", "othertopic").isEmpty())

        // Second request from the same site: the coin is reserved, so it cannot be completed...
        val second = requestSession(amount = 1000L)
        val pa = second.proposalAnalysis.value!!
        assertTrue(pa.isInsufficientFunds, "expected insufficient funds, got ${pa.completionException}")
        // ...but it would be with the reserved coin, and finding that out did not touch the reservation
        assertTrue(pa.reservedCoinsWouldHelp)
        assertTrue(coin.reserved != 0L)
        assertEquals(1, countReserved(account.wallet))
        assertEquals(1, TdppPartialLedger.size())

        // Without a node that can confirm the coin is unspent (none connected, or one that cannot answer) the retry refuses and changes nothing
        assertFalse(second.retryIgnoringReserved())
        assertTrue(coin.reserved != 0L)
        assertEquals(1, TdppPartialLedger.size())
        assertFalse(second.usingReservedCoins.value)
        assertFalse(second.analyzing.value)

        // Releasing the ledger's coins directly (what a confirmed retry does) makes the request completable
        TdppPartialLedger.release(account.wallet, candidates, setOf(coin.outpoint!!))
        assertEquals(0, TdppPartialLedger.size())
        assertEquals(0L, coin.reserved)
        assertTrue(second.reanalyze(force = false))
        assertNull(second.proposalAnalysis.value!!.completionException)
        second.releaseIfUndecided()
        assertEquals(0, countReserved(account.wallet))
        wallyApp!!.deleteAccount(account)
    }

    @Test
    fun ledgerForgetsEntriesWhoseCoinsWereReleased()
    {
        val account = makeAccount()
        val coin = fundWallet(account.wallet, 100_000L)
        val first = requestSession(flags = TDPP_FLAG_PARTIAL or TDPP_FLAG_NOPOST, amount = 1000L)
        first.port = 1  // the best-effort reply must fail fast
        first.acceptSpecialTx()
        assertEquals(1, TdppPartialLedger.size())
        // Something else (AccountDetail "Reassess", a restart) frees the coin: the entry is no longer holding anything
        coin.reserved = 0L
        assertTrue(TdppPartialLedger.candidates(account.wallet, "example.com", null).isEmpty())
        assertEquals(0, TdppPartialLedger.size())
        wallyApp!!.deleteAccount(account)
    }

    @Test
    fun deletingTheAccountDropsItsLedgerEntries()
    {
        val account = makeAccount()
        fundWallet(account.wallet, 100_000L)
        val first = requestSession(flags = TDPP_FLAG_PARTIAL or TDPP_FLAG_NOPOST, amount = 1000L)
        first.port = 1  // the best-effort reply must fail fast
        first.acceptSpecialTx()
        assertEquals(1, TdppPartialLedger.size())
        wallyApp!!.deleteAccount(account)
        assertEquals(0, TdppPartialLedger.size())
    }
}
