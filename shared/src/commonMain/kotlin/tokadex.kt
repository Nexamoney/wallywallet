@file:OptIn(ExperimentalUnsignedTypes::class)
package org.wallywallet.tokadex

import org.nexa.libnexakotlin.*

private val LogIt = GetLog("cli")

val TOKADEX_FEE = 0L // 10.nexa
var TOKADEX_ADDR:PayAddress? = null

/** Create a Tokadex partial transaction that offers to trade an asset (that you own) for native coin (nexa).  It is expected that this wallet contains the asset, but this
 * command will find the UTXOs and populate the transaction.
 * @param gid  Asset to offer
 * @param grpQty Quantity of asset to offer
 * @param askingQty Quantity (in satoshis) the "taker" must supply to complete this transaction
 * @param minConfirms Use UTXOs with at least this many confirmations
 * @return iTransaction containing the partial transaction offer.  The "taker" can use [txCompleter] to complete it.  Use [iTransaction.createTdppUrl] to make TDPP offer request
 * */
@cli(Display.Dev, "Create a partial transaction compliant with the Tokadex requirements")
fun CommonWallet.createToxadexExchangeOffer(grpQty: Long, gid: GroupId, askingQty: Long,  payToAddress:PayAddress, platFee: Long, platAddress: PayAddress?,  minConfirms:Int = 0): iTransaction
{
    // Create a transaction for whatever chain this wallet is running on
    val tx = txFor(chainSelector)
    val myAddress = getNewAddress()
    // Add an output for this amount of tokens, just to get txCompleter to fund it.  I will delete this output so that the taker can supply it
    tx.add(txOutputFor(myAddress, grpQty, gid))
    tx.lockTime = 0
    // Here I'm saying "complete this transaction by funding (adding inputs for) any tokens that are being sent", and just above I added an output to send some tokens.
    // So this call is going to populate the transaction with inputs that pull in the tokens needed, AND may add an token change payment to myself if it had to pull in too many.
    txCompleter(tx, minConfirms, TxCompletionFlags.PARTIAL or TxCompletionFlags.FUND_GROUPS, changeAddress = myAddress)
    assert(tx.inputs.isNotEmpty())
    // Tokadex wants the change to be last, so I've got to grab it now
    assert(tx.outputs.size <= 2) // The fake output is a 0, and we may have some change
    val change = if (tx.outputs.size == 2) tx.outputs[1] else null  // grab the change

    // now get rid of all those outputs, and build the outputs back up in the correct order
    tx.outputs.clear()

    // Tokadex "Maker's Receive": Now add the output we really want, which is a payment in native coin
    tx.add(txOutputFor(askingQty, payToAddress))
    // Tokadex "Platform Fee": give $ to the platform
    if (platAddress != null)
        tx.add(txOutputFor(platFee, platAddress))
    change?.let {tx.add(change)}

    // Sign this transaction "partially"
    txCompleter(tx, minConfirms, TxCompletionFlags.PARTIAL or TxCompletionFlags.SIGN or TxCompletionFlags.BIND_OUTPUT_PARAMETERS, changeAddress = myAddress)

    // Not needed in Tokadex
    // Now add an output to tell the "taker" how to grab the what was offered.
    // This ought to be optional; a wallet can analyze the transaction's inputs to see what's being offered.  But doing that requires doing a UTXO look up to find out what
    // assets the inputs are offering.  This is how we just tell the "taker" what's being offered.  Note that if we are lying, the tx will be bad so the "taker" is not trusting
    // us.
    // tx.add(txOutputFor(chainSelector, dust(chainSelector), SatoshiScript.grouped(chainSelector, gid, grpQty).add(OP.TMPL_SCRIPT)))
    return tx
}

/** Make and post a Tokadex partial transaction that offers to trade an asset (that you own) for native coin (nexa).  It is expected that this wallet contains the asset, but this
 * command will find the UTXOs and populate the transaction.
 * @param tx The partial transaction containing the offer
 * @param offerGrp What token is being offered.  This is searchable in the CAPD message (indicative), so must be the same as what is actually in the tx (authoritative)
 * @param recvGrp What token should be used for payment.  This is searchable in the CAPD message (indicative).  If null, it is assumed to be the native coin.
 * */
@cli(Display.Dev, "Post an offer to sell some token for some Nexa")
fun CommonWallet.postTokadexOffer(tx: iTransaction, offerGrp: GroupId, recvGrp:GroupId? = null): CapdMsg
{
    val partialTxBin = tx.toByteArray()
    val TOKADEX_NEXA_ID = ByteArray(6, { 0.toByte()})

    val tokadexCapdContentsSer = BCHserialized(SerializationType.NETWORK)
        .addExactBytes(byteArrayOf('T'.code.toByte(), 'K'.code.toByte(), 'D'.code.toByte()))
        .addVariableSized(offerGrp.data.takeLast(6).toByteArray() + (if (recvGrp == null) TOKADEX_NEXA_ID else recvGrp.data.takeLast(6).toByteArray()))
        .addVariableSized(partialTxBin)
    val tokadexCapdContents = tokadexCapdContentsSer.toByteArray()

    LogIt.info("Tokadex CAPD message contents: ${tokadexCapdContents.toHex()}")
    val msg = CapdMsg(tokadexCapdContents)
    val cxn = blockchain.net.getNode()
    var prioritySet = false
    val haveCd = org.nexa.threads.Gate()
    cxn.reloadCapdInfo { haveCd.wake(); prioritySet = true }
    haveCd.delayuntil(60000, { prioritySet })
    LogIt.info("CAPD relay priority ${cxn.capdRelayPriority}")

    msg.setPowTargetHarderThanPriority(cxn.capdRelayPriority+0.1)
    msg.solve(5)
    check(msg.check())
    cxn.send(msg)
    return msg
}

/** Make and post a Tokadex partial transaction that offers to trade an asset (that you own) for native coin (nexa).  It is expected that this wallet contains the asset, but this
 * command will find the UTXOs and populate the transaction.
 * @param grpId  Asset to offer
 * @param grpQuantity Quantity of asset to offer
 * @param satoshiQuantity Quantity (in satoshis) the "taker" must supply to complete this transaction
 * @param platAddress Tokadex
 * @return iTransaction containing the partial transaction offer.  The "taker" can use [txCompleter] to complete it.  Use [iTransaction.createTdppUrl] to make TDPP offer request
 * */
@cli(Display.Simple, "Post an offer to sell some token for some Nexa")
fun CommonWallet.postTokadexOffer(grpQuantity: Long, grpId: GroupId, satoshiQuantity: Long, payTo: PayAddress?=null): CapdMsg
{
    // val tokaAddr = TOKADEX_ADDR ?: getnewaddress()  // If I haven't set up a fee address, pay the fee to myself (for debugging)
    val pTo = payTo ?: getnewaddress()
    val partialTx = createToxadexExchangeOffer(grpQuantity,grpId,  satoshiQuantity,pTo, TOKADEX_FEE, TOKADEX_ADDR)
    LogIt.info("Offer Partial TX is: ${partialTx.idem}\n  ${partialTx.toHex()}")
    val msg = postTokadexOffer(partialTx, grpId)
    LogIt.info("CAPD message ${msg.hash()?.toHex()}:\n${msg.toByteArray().toHex()}")
    return msg
}

/** Make and post a Tokadex partial transaction that offers to trade an asset (that you own) for native coin (nexa).  It is expected that this wallet contains the asset, but this
 * command will find the UTXOs and populate the transaction.
 * @param grpId  Asset to offer
 * @param grpQuantity Quantity of asset to offer
 * @param satoshiQuantity Quantity (in satoshis) the "taker" must supply to complete this transaction
 * @param platAddress Tokadex
 * @return iTransaction containing the partial transaction offer.  The "taker" can use [txCompleter] to complete it.  Use [iTransaction.createTdppUrl] to make TDPP offer request
 * */
@cli(Display.Simple, "Post an offer to sell some token for some Nexa")
fun CommonWallet.postTokadexOffer(grpQuantity: Long, grpId: String, satoshiQuantity: Long, payTo: String?=null): CapdMsg
{
    val gid = GroupId(grpId)
    val pTo = payTo?.let { PayAddress(payTo) }
    return postTokadexOffer(grpQuantity, gid, satoshiQuantity, pTo)
}

