package ui

import com.eygraber.uri.Uri
import info.bitcoinunlimited.www.wally.*
import info.bitcoinunlimited.www.wally.ui.setSelectedAccount
import org.nexa.libnexakotlin.*
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

private const val SELLER_PAYOUT = "nexa:nqtsq5g5fxz9qyup04g288qy2pxf9aemxjysnzqnn2nky4xw"

/** A /tx request spending our coins is signed by the account holding them, not the selected one */
class TricklePayOwningAccountTest
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

    private fun newAccount(name: String): Account =
        wallyApp!!.newAccount(name, 0U, "", cs) ?: error("could not create the test account $name (a previous deletion still pending?)")

    private fun fundWallet(wallet: Wallet, amount: Long): Spendable
    {
        val tx = txFor(cs)
        val from = Spendable(cs, NexaTxOutpoint(Hash256(ByteArray(32) { 5 }), 0), amount + 1000)
        tx.add(txInputFor(from), SPENDABLE_UNRESERVED)
        tx.add(NexaTxOutput(cs, amount, wallet.getNewDestination().address!!.outputScript()))
        (wallet as CommonWallet).interestingTx(listOf(tx), Hash256(ByteArray(32) { 6 }), 1L, 1_000_000_000_000L, "test funding")
        val sp = wallet.getTxo(NexaTxOutpoint(tx.idem, 0))
        assertNotNull(sp, "wallet did not record the funding output")
        return sp
    }

    private fun offerSession(coin: Spendable): TricklePaySession
    {
        val tx = txFor(cs)
        tx.add(txInputFor(Spendable(cs, coin.outpoint!!, coin.amount)))
        tx.add(NexaTxOutput(cs, coin.amount, PayAddress(SELLER_PAYOUT).outputScript()))
        val domains = TricklePayDomains()
        domains.domainsLoaded = true
        val session = TricklePaySession(domains)
        session.chainSelector = cs
        val flags = TDPP_FLAG_PARTIAL or TDPP_FLAG_NOFUND
        session.handleTxAutopay(Uri.parse("https://market.example/tx?tx=${tx.toHex()}&flags=$flags&chain=nexa"))
        assertNotNull(session.proposalAnalysis.value)
        return session
    }

    @Test
    fun anOfferIsSignedByTheAccountHoldingItsCoinNotTheSelectedOne()
    {
        val holder = newAccount("tpholder")
        val other = newAccount("tpother")
        val coin = fundWallet(holder.wallet, 10_000)
        setSelectedAccount(other)

        val session = offerSession(coin)

        val pa = session.proposalAnalysis.value!!
        assertEquals(holder, pa.account, "analysed with ${pa.account.name}")
        assertEquals(holder, session.getRelevantAccount())
        assertEquals(listOf(holder), session.pill.choices)
        assertEquals(null, pa.completionException)
        assertTrue(session.proposedTx!!.inputs[0].script.size > 0, "the offer's input was left unsigned")

        wallyApp!!.deleteAccount(other)
        wallyApp!!.deleteAccount(holder)
    }

    @Test
    fun aRequestSpendingNoneOfOurCoinsKeepsTheSelectedAccount()
    {
        val holder = newAccount("tpholder2")
        val other = newAccount("tpother2")
        fundWallet(holder.wallet, 10_000)
        setSelectedAccount(other)

        val foreign = Spendable(cs, NexaTxOutpoint(Hash256(ByteArray(32) { 3 }), 0), 10_000)
        val session = offerSession(foreign)

        assertEquals(other, session.proposalAnalysis.value!!.account)
        assertTrue(session.pill.choices!!.containsAll(listOf(holder, other)), "the usual choice of accounts")

        wallyApp!!.deleteAccount(other)
        wallyApp!!.deleteAccount(holder)
    }

    @Test
    fun anOfferStaysWithTheHolderWhenItIsSelected()
    {
        val holder = newAccount("tpholder3")
        val other = newAccount("tpother3")
        val coin = fundWallet(holder.wallet, 10_000)
        setSelectedAccount(holder)

        val session = offerSession(coin)

        assertEquals(holder, session.proposalAnalysis.value!!.account)
        assertEquals(listOf(holder), session.pill.choices)

        wallyApp!!.deleteAccount(other)
        wallyApp!!.deleteAccount(holder)
    }

    /** A purchase of [coin] as a marketplace would send it to the buyer: the seller's input is already signed, the buyer pays [price] on top */
    private fun preSignedPurchaseTx(seller: Account, coin: Spendable, price: Long): iTransaction
    {
        val tx = txFor(cs)
        tx.add(txInputFor(coin))
        tx.add(NexaTxOutput(cs, coin.amount + price, PayAddress(SELLER_PAYOUT).outputScript()))
        seller.wallet.txCompleter(tx, 0, TxCompletionFlags.SIGN or TxCompletionFlags.PARTIAL or TxCompletionFlags.BIND_OUTPUT_PARAMETERS, null)
        assertTrue(tx.inputs[0].script.size > 0, "the seller's input was not signed")
        return tx
    }

    private fun purchaseSession(tx: iTransaction, flags: Int, inamt: Long): TricklePaySession
    {
        val domains = TricklePayDomains()
        domains.domainsLoaded = true
        val session = TricklePaySession(domains)
        session.chainSelector = cs
        session.handleTxAutopay(Uri.parse("https://market.example/tx?tx=${tx.toHex()}&flags=$flags&inamt=$inamt&chain=nexa"))
        assertNotNull(session.proposalAnalysis.value)
        return session
    }

    @Test
    fun aPreSignedInputOfAnotherAccountDoesNotTakeOverTheSelectedAccount()
    {
        val seller = newAccount("tpseller")
        val buyer = newAccount("tpbuyer")
        val nft = fundWallet(seller.wallet, 10_000)
        setSelectedAccount(buyer)

        val tx = preSignedPurchaseTx(seller, nft, 0)
        val sellerSig = tx.inputs[0].script.toHex()
        val session = purchaseSession(tx, TDPP_FLAG_PARTIAL or TDPP_FLAG_NOFUND, nft.amount)

        val pa = session.proposalAnalysis.value!!
        assertEquals(buyer, pa.account, "analysed with ${pa.account.name}")
        assertEquals(buyer, session.getRelevantAccount())
        assertTrue(session.pill.choices!!.containsAll(listOf(seller, buyer)), "the pill must still offer both accounts")
        assertEquals(sellerSig, session.proposedTx!!.inputs[0].script.toHex(), "the seller's signature was touched")

        wallyApp!!.deleteAccount(buyer)
        wallyApp!!.deleteAccount(seller)
    }

    @Test
    fun theSelectedAccountFundsAPurchaseOfAnotherAccountsPreSignedCoin()
    {
        val seller = newAccount("tpseller2")
        val buyer = newAccount("tpbuyer2")
        val nft = fundWallet(seller.wallet, 10_000)
        fundWallet(buyer.wallet, 50_000)
        setSelectedAccount(buyer)

        val tx = preSignedPurchaseTx(seller, nft, 5_000)
        val sellerSig = tx.inputs[0].script.toHex()
        val session = purchaseSession(tx, TDPP_FLAG_PARTIAL, nft.amount)

        val pa = session.proposalAnalysis.value!!
        assertEquals(buyer, pa.account, "analysed with ${pa.account.name}")
        assertEquals(null, pa.completionException)
        val completed = session.proposedTx!!
        assertEquals(sellerSig, completed.inputs[0].script.toHex(), "the seller's signature was touched")
        assertTrue(completed.inputs.size > 1, "the buyer added no funding input")
        assertTrue(completed.inputs.drop(1).all { buyer.wallet.getTxo(it.spendable.outpoint!!) != null }, "funding did not come from the buyer")
        assertTrue(session.pill.choices!!.containsAll(listOf(seller, buyer)), "the pill must still offer both accounts")

        wallyApp!!.deleteAccount(buyer)
        wallyApp!!.deleteAccount(seller)
    }

    @Test
    fun aMarketplaceRefusingAnUnsignedOfferIsARejection()
    {
        assertEquals(OriginatorReply.REJECTED, classifyOriginatorReply("Offer input #0 is not signed"))
        assertEquals(OriginatorReply.REJECTED, classifyOriginatorReply("Tx rejected by peer"))
        assertEquals(OriginatorReply.REJECTED, classifyOriginatorReply("Invalid transaction"))
        assertEquals(OriginatorReply.UNKNOWN_SESSION, classifyOriginatorReply("unknown session"))
        assertEquals(OriginatorReply.ACCEPTED, classifyOriginatorReply("ok"))
        assertEquals(OriginatorReply.ACCEPTED, classifyOriginatorReply(""))
    }
}
