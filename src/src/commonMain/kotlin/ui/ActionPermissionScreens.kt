@file:OptIn(ExperimentalUnsignedTypes::class)

package info.bitcoinunlimited.www.wally.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.eygraber.uri.Uri
import org.nexa.libnexakotlin.*
import info.bitcoinunlimited.www.wally.*
import info.bitcoinunlimited.www.wally.ui.theme.*
import io.ktor.http.*
import io.ktor.utils.io.errors.*
import okio.FileNotFoundException
import org.nexa.libnexakotlin.CannotLoadException
import org.nexa.libnexakotlin.simpleapi.NexaScript
import kotlin.math.log

private val LogIt = GetLog("wally.actperm")

class IdentitySession(var uri: Uri?, var idData: IdentityDomain?=null, val whenDone: ((String, String, Boolean?)->Unit)?= null)

fun HandleIdentity(uri: Uri, then: ((String, String,Boolean?)->Unit)?= null): Boolean
{
    val sess = IdentitySession(uri, null, then)
    LogIt.info(sourceLoc() +": Handle identity operation")

    val operation = uri.getQueryParameter("op")
    if (operation == null)
    {
        displayError(S.unknownOperation)
        return false
    }
    var useOp = true
    if (operation.lowercase() == "login")
    {
        // Identity always happens relative to your "primary" account
        val account = wallyApp?.nullablePrimaryAccount
        if (account == null) displayError(S.primaryAccountRequired)
        else
        {
            val domain = uri.host?.let { account.wallet.lookupIdentityDomain(it) }
            if (domain == null)
            {
                displayError(S.UnknownDomainRegisterFirst)
                return false
            }
        }
        // otherwise  head to the identityOp screen... (below)
    }
    else if (operation.lowercase() == "reg")
    {
        // A registration needs the identity page if its trying to change something, otherwise its just an accept/deny identity op
        val account = wallyApp?.nullablePrimaryAccount
        if (account == null) displayError(S.primaryAccountRequired)
        else
        {
            val domain = uri.host?.let { account.wallet.lookupIdentityDomain(it) }
            if (domain == null) useOp = false  // Don't even know about this domain, need to register it
            else
            {
                val d = domain.clone()
                d.setReqs(uri.queryMap())
                if (!d.permsWithinReqs()) useOp = false
            }

        }
    }
    LogIt.info(sourceLoc() +": Launch identity operation")
    if (useOp) nav.go(ScreenId.IdentityOp, data = sess)
    else nav.go(ScreenId.Identity, data = sess)
    /*
    if (useOp) wallyApp?.later {
        LogIt.info(sourceLoc() +": send screen change")
        externalDriver.send(GuiDriver(ScreenId.IdentityOp, uri = uri))
    }
    else wallyApp?.later { externalDriver.send(GuiDriver(ScreenId.Identity, uri = uri)) }
     */
    return true
}


fun getSecretAndAddress(wallet: Wallet, host: String?, path: String?): Pair<Secret, PayAddress>
{
    val seed = if (host == null) Bip44Wallet.COMMON_IDENTITY_SEED
    else
    {
        val idData = wallet.lookupIdentityDomain(host)
        if (idData != null)
        {
            if (idData.useIdentity == IdentityDomain.COMMON_IDENTITY)
                Bip44Wallet.COMMON_IDENTITY_SEED
            else if (idData.useIdentity == IdentityDomain.IDENTITY_BY_HASH)
                host + ( path ?: "")
            else
            {
                LogIt.severe("Invalid identity selector; corrupt?")
                Bip44Wallet.COMMON_IDENTITY_SEED
            }
        }
        else
            Bip44Wallet.COMMON_IDENTITY_SEED
    }

    val identityDest: PayDestination = wallet.destinationFor(seed)

    // This is a coding bug in the wallet
    val secret = identityDest.secret ?: throw IdentityException("Wallet failed to provide an identity with a secret", "bad wallet", ErrorSeverity.Severe)
    val address = identityDest.address ?: throw IdentityException("Wallet failed to provide an identity with an address", "bad wallet", ErrorSeverity.Severe)
    return Pair(secret, address)
}

@Composable
fun IdentityPermScreen(account: Account, sess: IdentitySession, nav: ScreenNav)
{
    val uri = sess.uri
    if (uri == null)
    {
        displayError(S.badQR)
        nav.back()
        return
    }

    val queries = uri.queryMap()
    val op = queries["op"]?.lowercase() ?: ""

    var msgToSign by remember { mutableStateOf<ByteArray?>(null) }
    var destToSignWith by remember { mutableStateOf<PayDestination?>(null) }

    var error = ""  // If there's some error but you want to insist on the user actively dismissing the screen, set this to the error to display

    var act = try
    {
        wallyApp!!.preferredVisibleAccount()
    }
    catch (e: PrimaryWalletInvalidException)
    {
        displayError(S.primaryAccountRequired)
        nav.back()
        return
    }
    var w = act.wallet

    // TODO If the primary account is locked

    val h = uri.host

    if (op != "sign")  // sign does not need a registered identity domain so skip all this checking for this op
    {
        var idData:IdentityDomain? = null
        if (h == null)
        {
            displayError(S.badLink, "Identity request did not provide a host")
            return
        }
        idData = w.lookupIdentityDomain(h) // + path)
        if (idData == null)  // We don't know about this domain, so register and give it info in one shot
        {
            nav.back()
            nav.go(ScreenId.Identity)  // This should never be called because we should not have gone into the IdentityPermScreen if the domain does not exist
            return
        }
        sess.idData = idData
    }

    val topText = when(op)
    {
        "info" -> S.provideInfoQuestion
        "reg"  -> S.provideLoginQuestion
        "login"  -> S.provideLoginQuestion
        "sign" -> S.signDataQuestion
        else -> {
            displayError(S.badQR, "invalid identity operation")
            nav.back()
            return
        }
    }

    Column(Modifier.fillMaxWidth()) {
        CenteredSectionText(topText)
        if (h != null)
        {
            val tm = Modifier.padding(0.dp).fillMaxWidth().align(Alignment.CenterHorizontally)
            val ts = TextStyle(fontStyle = FontStyle.Italic, fontSize = FontScale(1.5))
            Text(h, modifier = tm, style = ts, textAlign = TextAlign.Center)
        }

        if (op == "sign")
        {

            var signAddr = queries["addr"]?.let { PayAddress(it) } ?: null
            var signText = queries["sign"]?.urlDecode()
            val signHex = queries["signhex"]
            if (signAddr != null)
            {
                var dest = w.walletDestination(signAddr)
                if (dest == null)
                {
                    for (aname in wallyApp!!.visibleAccountNames())
                    {
                        val a = wallyApp!!.accounts[aname] ?: continue
                        dest = a.wallet.walletDestination(signAddr)
                        if (dest != null)
                        {
                            act = a
                            w = act.wallet
                            break
                        }
                    }
                    if (dest == null)
                    {
                        displayError(S.unknownAddress)
                        return
                    }
                }
                destToSignWith = dest
            }
            destToSignWith?.let {
                CenteredSectionText(i18n(S.SigningWithAccount) % mapOf("act" to act.nameAndChain))
                CenteredFittedText(it.address.toString())
            }
            if (signText != null)
            {
                Spacer(Modifier.height(10.dp))
                CenteredSectionText(S.textToSign)
                WallyBrightEmphasisBox(Modifier.weight(1f).fillMaxWidth()) {
                    Text(signText, modifier = Modifier.wrapContentHeight(align = Alignment.CenterVertically), colorPrimaryDark)
                }
                msgToSign = signText.toByteArray()
            }
            else if (signHex != null)
            {
                CenteredSectionText(S.binaryToSign)
                WallyBrightEmphasisBox(Modifier.weight(1f).fillMaxWidth()) {
                    Text(signHex, modifier = Modifier.wrapContentHeight(align = Alignment.CenterVertically), colorPrimaryDark)
                }
                msgToSign = signHex.fromHex()
            }
            else
            {
                displayError(S.nothingToSign)
            }
        }

        if (error != "")
        {
            SectionText(S.CannotCompleteTransaction)
            Text(error)
        }

        Spacer(Modifier.height(5.dp))

        Row(modifier = Modifier.fillMaxWidth().padding(0.dp), horizontalArrangement = Arrangement.SpaceAround, verticalAlignment = Alignment.CenterVertically) {
            Spacer(Modifier.width(1.dp))
            if (error == "") WallyBoringLargeTextButton(S.yes) {
                // If the user accepts this identity operation

                // Turn the identity menu since user has done an identity operation
                enableNavMenuItem(ScreenId.Identity)

                if (op == "sign")
                {
                    val path = uri.encodedPath

                    val (secret, address) = destToSignWith?.let { Pair(it.secret, it.address) } ?: getSecretAndAddress(act.wallet, h ?: "", path)

                    val msg = msgToSign
                    if (secret == null)
                    {
                        displayError(S.unknownAddress)
                        nav.back()
                    }
                    else if (msg == null)
                    {
                        displayError(S.nothingToSign)
                        nav.back()
                    }
                    else
                    {
                        val msgSig = libnexa.signMessage(msg, secret.getSecret())
                        if (msgSig == null || msgSig.size == 0)
                        {
                            displayError(S.badSignature)
                            nav.back()
                        }
                        else
                        {
                            val sigStr = Codec.encode64(msgSig)
                            val msgStr = msg.decodeUtf8()
                            val clipboard = """{ "message":"${msgStr}", "address":"${address.toString()}", "signature": "${sigStr}" }"""
                            later {
                                LogIt.info(clipboard)
                                setTextClipboard(clipboard)
                            }
                            displayNotice(S.sigInClipboard)

                            // If a continuation was provided, give the response to that
                            val then = sess.whenDone
                            if (then != null)
                            {
                                val responseProtocol = queries["proto"]
                                var protocol = responseProtocol ?: uri.scheme
                                val cookie = queries["cookie"]

                                var sigReq = protocol + "://" + h + path +
                                  "?op=sign&addr=" + address.toString().urlEncode() + "&sig=" + sigStr.urlEncode() + if (cookie == null) "" else "&cookie=" + cookie.urlEncode()
                                then(sigReq, clipboard, true)
                            }
                            else  // Otherwise send it via the default response (http)
                            {

                                val reply = queries["reply"]
                                if ((reply == null || reply == "true") && (h != "_"))
                                {
                                    val responseProtocol = queries["proto"]
                                    var protocol = responseProtocol ?: uri.scheme  // Prefer the protocol requested by the other side, otherwise use the same protocol we got the request from
                                    val portStr = if ((uri.port > 0) && (uri.port != 80) && (uri.port != 443)) ":" + uri.port.toString() else ""
                                    val cookie = queries["cookie"]

                                    // Server BUG workaround: nexid defines a scheme, not a protocol, so "proto" must have been defined to tell me how to
                                    // actually contact the server.
                                    if (protocol == "nexid") protocol = "http"

                                    var sigReq = protocol + "://" + h + portStr + path
                                    sigReq += "?op=sign&addr=" + address.toString().urlEncode() + "&sig=" + sigStr.urlEncode() + if (cookie == null) "" else "&cookie=" + cookie.urlEncode()

                                    LogIt.info("signature reply: " + sigReq)
                                    try
                                    {
                                        LogIt.info(sigReq)
                                        val (resp, status) = Uri.parse(sigReq).loadTextAndStatus(HTTP_REQ_TIMEOUT_MS)
                                        LogIt.info("signature response code:" + status.toString() + " response: " + resp)
                                        if ((status >= 200) and (status < 250))
                                        {
                                            displayNotice(resp)
                                            nav.back()
                                        }
                                        else
                                        {
                                            displayNotice(resp)
                                            nav.back()
                                        }

                                    }
                                    catch (e: FileNotFoundException)
                                    {
                                        displayError(S.badLink)
                                        nav.back()
                                    }
                                    catch (e: IOException)
                                    {
                                        logThreadException(e)
                                        displayError(S.connectionAborted)
                                        nav.back()
                                    }
                                    catch (e: Exception)
                                    {
                                        displayError(S.connectionException)
                                        nav.back()
                                    }
                                }
                                else
                                {
                                    nav.back()
                                }
                            }
                        }
                    }
                }
                else
                {
                    onProvideIdentity(sess, account)
                    nav.back()
                }
            }

            WallyBoringLargeTextButton(if (error == "") S.no else S.cancel)
            {
                nav.back()
                displayNotice(S.cancelled)
            }
            Spacer(Modifier.width(1.dp))
        }
        Spacer(Modifier.height(10.dp))
    }
}


@Composable
fun SpecialTxPermScreen(acc: Account, sess: TricklePaySession, nav: ScreenNav)
{
    var breakIt = false // TODO allow a debug mode that produces bad tx

    var accountUnlockTrigger = remember { mutableStateOf(0) }
    var oldAccountUnlockTrigger = remember { mutableStateOf(0) }

        // auto-complete if already accepted and sess.accepted = true (needed to unlock the account)

    val pTx = sess.proposedTx
    val panalysis = sess.proposalAnalysis
    if ((pTx == null) || (panalysis == null))  // context lost probable bug
    {
        displayUnexpectedException(TdppException(S.unavailable, "protocol context lost accepting special transaction"))
        nav.back()
        return
    }

    // unlock was attempted, if successful, accept, otherwise error
    if ((sess.accepted)&&(accountUnlockTrigger.value != oldAccountUnlockTrigger.value))
    {
        oldAccountUnlockTrigger.value = accountUnlockTrigger.value
        if (panalysis.account.locked)
        {
            displayError(S.InvalidPIN)
            sess.accepted = false
        }
        else
        {
            try
            {
                sess.acceptSpecialTx(breakIt)
            }
            catch (e: LibNexaExceptionI)
            {
                displayUnexpectedException(e)
            }
            catch(e: Exception)
            {
                handleThreadException(e)
                displayError(S.unknownError, e.toString())
            }
            nav.back()
        }
    }


    Column(Modifier.fillMaxWidth()) {

        // Change the title if the request is for a partial transaction
        CenteredSectionText(if (sess.tflags and TDPP_FLAG_PARTIAL != 0) S.IncompleteTpTransactionFrom else S.SpecialTpTransactionFrom)

        val tpc = sess.topic.let {
            if (it == null) ""
            else ":" + it
        }
        val fromEntity = sess.host + tpc
        val tm = Modifier.padding(0.dp).fillMaxWidth().align(Alignment.CenterHorizontally)
        val ts = TextStyle(fontStyle = FontStyle.Italic, fontSize = FontScale(1.5))
        Text(fromEntity, modifier = tm, style = ts, textAlign = TextAlign.Center)

        CenteredSectionText(S.TpInAccount)
        val fromAccount = acc.nameAndChain
        Text(fromAccount, modifier = tm, style = ts, textAlign = TextAlign.Center)
        CenteredSectionText(S.TpEntails)

        val a = sess.proposalAnalysis
        var GuiCustomTxCost = ""
        var GuiCustomTxFee = ""
        var GuiCustomTxTokenSummary = ""
        var GuiCustomTxError = ""
        var DeleteButtonText = S.deny

        if (a != null)
        {
            // what's being paid to me - what I'm contributing.  So if I pay out then its a negative number
            val netSats = a.receivingSats - a.myInputSatoshis

            if (netSats > 0)
            {
                GuiCustomTxCost = (i18n(S.receiving) + " " + acc.cryptoFormat.format(acc.fromFinestUnit(netSats)) + " " + acc.currencyCode)
            }
            else if (netSats < 0)
            {
                val txt = i18n(S.sending) + " " + acc.cryptoFormat.format(acc.fromFinestUnit(-netSats)) + " " + acc.currencyCode
                // LogIt.info(txt)
                GuiCustomTxCost = txt
            }
            else
            {
                GuiCustomTxCost = i18n(S.nothing)
            }

            if (a.otherInputSatoshis != null)
            {
                val fee = (a.myInputSatoshis + a.otherInputSatoshis) - (a.receivingSats + a.sendingSats)
                if (fee > 0)
                {
                    GuiCustomTxFee = (i18n(S.ForAFeeOf) % mapOf("fee" to acc.cryptoFormat.format(acc.fromFinestUnit(fee)), "units" to acc.currencyCode))
                }
                else  // Almost certainly the requester is going to fill out more of this tx so the fee is kind of irrelevant.  TODO: only show the fee if this wallet is paying it (tx is complete)
                {
                    GuiCustomTxFee = ""
                }
            }
            else
            {
                GuiCustomTxFee = ""
            }

            // Expand the text to handle proving ownership of (that is, sending token to yourself)

            var receivingTokenTypes = 0L
            var spendingTokenTypes = 0L
            var provingTokenTypes = 0L
            for ((_, v) in a.myNetTokenInfo)
            {
                if (v > 0) receivingTokenTypes++
                else if (v < 0) spendingTokenTypes++
                else provingTokenTypes++
            }

            var summary = if (receivingTokenTypes > 0)
            {
                if (spendingTokenTypes > 0)
                {
                    i18n(S.TpExchangingTokens) % mapOf("tokSnd" to a.sendingTokenTypes.toString(), "tokRcv" to a.receivingTokenTypes.toString())
                }
                else
                {
                    i18n(S.TpReceivingTokens) % mapOf("tokRcv" to a.receivingTokenTypes.toString())
                }
            }
            else
            {
                if (spendingTokenTypes > 0)
                {
                    i18n(S.TpSendingTokens) % mapOf("tokSnd" to spendingTokenTypes.toString())
                }
                else ""
            }

            if (provingTokenTypes > 0) summary = summary + "\n" + (i18n(S.TpShowingTokens) % mapOf("tokReveal" to provingTokenTypes.toString()))

            GuiCustomTxTokenSummary = summary

            var error: String? = null
            if ((receivingTokenTypes == 0L) && (spendingTokenTypes == 0L) && (provingTokenTypes == 0L) && (netSats == 0L))
            {
                error = i18n(S.TpHasNoPurpose)
            }

            a.completionException?.let { error = it.message }

            if (error != null)  // If there's an exception, the only possibility is to abort
            {
                //ui.GuiCustomTxErrorHeading.visibility = View.VISIBLE
                // ui.GuiTpSpecialTxAccept.visibility = View.GONE
                GuiCustomTxError = error ?: ""
                DeleteButtonText = S.cancel
            }
            else
            {
                //ui.GuiCustomTxErrorHeading.visibility = View.GONE
                //ui.GuiTpSpecialTxAccept.visibility = View.VISIBLE
                GuiCustomTxError = ""
            }
        }

        Text(GuiCustomTxCost, modifier = tm, style = ts, textAlign = TextAlign.Center)
        Text(GuiCustomTxFee, modifier = tm, style = ts, textAlign = TextAlign.Center)
        Text(GuiCustomTxTokenSummary, modifier = tm, style = ts, textAlign = TextAlign.Center)

        Spacer(Modifier.defaultMinSize(1.dp,10.dp).weight(1f))

        // TODO get all this on the bottom
        if (GuiCustomTxError != "")
        {
            CenteredSectionText(S.CannotCompleteTransaction)
            Text(GuiCustomTxError, maxLines = 10, softWrap = true)
        }

        WallyDivider()
        Spacer(Modifier.height(5.dp))

        Row(modifier = Modifier.fillMaxWidth().padding(0.dp), horizontalArrangement = Arrangement.SpaceAround, verticalAlignment = Alignment.CenterVertically) {
            if (GuiCustomTxError == "") WallyBoringLargeTextButton(S.accept)
            {
                // Turn the menu on since user has accepted an operation of this type
                enableNavMenuItem(ScreenId.TricklePay)

                try
                {
                    LogIt.info(sourceLoc() +": accept trickle pay special transaction")

                    // Step 1, unlock if needed, otherwise accept & done
                    if ((pTx != null) && (panalysis != null))
                    {
                        if (sess.accepted == false)
                        {
                            sess.accepted = true

                            if (panalysis.account.locked)
                            {
                                triggerUnlockDialog(true) { accountUnlockTrigger.value += 1 }
                            }
                            else
                            {
                                sess.acceptSpecialTx(breakIt)
                                nav.back()
                            }
                        }
                    }
                    else
                    {
                        displayUnexpectedException(TdppException(S.unavailable, "protocol context lost accepting special transaction"))
                        nav.back()
                    }

                }
                catch (e: LibNexaExceptionI)
                {
                    displayUnexpectedException(e)
                    nav.back()
                }
                catch (e: LibNexaException)
                {
                    handleThreadException(e)
                    displayError(S.unknownError, e.toString())
                    nav.back()
                }
                catch(e: Exception)
                {
                    handleThreadException(e)
                    displayError(S.unknownError, e.toString())
                    nav.back()
                }
            }

            WallyBoringLargeTextButton(DeleteButtonText)
            {
                //info.bitcoinunlimited.www.wally.LogIt.info("deny trickle pay special transaction")
                // give back any inputs we grabbed to fulfill this tx
                sess.proposedTx?.let { acc.wallet.abortTransaction(it) }
                sess.proposedTx = null
                nav.back()
                displayNotice(S.cancelled)
            }
        }
    }
}

@Composable
fun AssetInfoPermScreen(acc: Account, sess: TricklePaySession , nav: ScreenNav)
{
    val tpc = sess.topic.let {
        if (it == null) ""
        else ":" + it
    }
    val fromEntity = sess.host + tpc

    Column {
        val tm = Modifier.padding(0.dp).fillMaxWidth().align(Alignment.CenterHorizontally)
        val ts = TextStyle(fontStyle = FontStyle.Italic, fontSize = FontScale(1.5))

        CenteredSectionText(S.TpAssetRequestFrom)
        Text(fromEntity, modifier = tm, style = ts, textAlign = TextAlign.Center)

        CenteredSectionText(S.TpHandledByAccount)
        val fromAccount = acc.nameAndChain
        Text(fromAccount, modifier = tm, style = ts, textAlign = TextAlign.Center)

        Spacer(Modifier.height(10.dp))
        val alist = sess.assetInfoList?.assets
        // combine utxos of assets of the same type for display purposes
        val aset = mutableSetOf<GroupId>()
        if (alist != null) for (a in alist)
        {
            val prevout = txOutputFor(acc.wallet.chainSelector, BCHserialized(a.prevout.fromHex(), SerializationType.NETWORK))
            val tmpl = prevout.script.parseTemplate(prevout.amount)
            val gi = tmpl?.groupInfo
            if (gi != null)
            {
                aset.add(gi.groupId)
                val ai = wallyApp!!.assetManager.assets[gi.groupId]
                LogIt.info("info: ${a.amt} ${a.prevout} ${gi.groupId.toString()} ${ai?.ticker}")
            }
            else
            {
                LogIt.warning(sourceLoc() +": All non-asset prevouts should already be filtered out")
            }
        }

        val numAssetsToShare = aset.size
        Text(i18n(S.TpAssetMatches) % mapOf("num" to numAssetsToShare.toString() ), modifier = tm, textAlign = TextAlign.Center)

        Spacer(Modifier.height(20.dp))
        SectionText(S.TpAssetInfoNotXfer)

        Spacer(Modifier.defaultMinSize(1.dp,10.dp).weight(1f))
        WallyDivider()
        Spacer(Modifier.height(5.dp))

        Row(modifier = Modifier.fillMaxWidth().padding(0.dp), horizontalArrangement = Arrangement.SpaceAround, verticalAlignment = Alignment.CenterVertically) {
            WallyBoringLargeTextButton(S.accept)
            {
                // Turn the menu on since user has accepted an operation of this type
                enableNavMenuItem(ScreenId.TricklePay)

                try
                {
                    val details = sess.acceptAssetRequest()
                    displaySuccess(S.TpAssetRequestAccepted, details)
                }
                catch (e: LibNexaExceptionI)
                {
                    displayUnexpectedException(e)
                }
                catch(e: Exception)
                {
                    handleThreadException(e)
                    displayError(S.unknownError, e.toString())
                }
                nav.back()
            }
            WallyBoringLargeTextButton(S.deny)
            {
                nav.back()
                displayNotice(S.TpAssetRequestDenied)
            }
        }
    }
}

@Composable
fun SendToPermScreen(acc: Account, sess: TricklePaySession , nav: ScreenNav)
{
    var breakIt = false // TODO allow a debug mode that produces bad tx

    val u: Uri = sess.proposalUrl ?: return

    val sa: List<Pair<PayAddress, Long>> = sess.proposedDestinations ?: return

    val tpc = sess.topic.let {
        if (it == null) ""
        else ":" + it
    }

    var total = 0L
    for (it in sa)
        total += it.second

    val ars = sess.askReasons
    val askReasons = if (ars != null && ars.size > 0) ars.joinToString("\n") else ""

    val domainAndTopic = u.authority + tpc

    Column(Modifier.padding(8.dp, 2.dp).testTag("ActionPermissionScreensColumn")) {
        CenteredSectionText(S.TpSendToTitle)
        Text(domainAndTopic)
        CenteredSectionText(S.TpInAccount)
        Text(acc.nameAndChain)
        CenteredSectionText(S.TpSpends)
        Text(acc.cryptoFormat.format(acc.fromFinestUnit(total)) + " " + acc.currencyCode)
        Text(sess.reason ?: "")
        CenteredSectionText(S.TpWhyNotAuto)
        Text(askReasons)

        Spacer(Modifier.height(10.dp))
        WallyDivider()
        Spacer(Modifier.height(5.dp))

        Row(modifier = Modifier.fillMaxWidth().padding(0.dp), horizontalArrangement = Arrangement.SpaceAround, verticalAlignment = Alignment.CenterVertically) {
            WallyBoringLargeTextButton(S.accept)
            {
                try
                {
                    sess.acceptSendToRequest()
                    displaySuccess(S.TpSendRequestAccepted)
                }
                catch(e:WalletNotEnoughBalanceException)
                {
                    displayError(S.insufficentBalance)
                }
                catch(e:WalletNotEnoughTokenBalanceException)
                {
                    displayError(S.insufficentTokenBalance)
                }
                catch(e:WalletException)
                {
                    displayUnexpectedException(e)
                }
                nav.back()
            }
            WallyBoringLargeTextButton(S.deny)
            {
                displayNotice(S.TpSendRequestDenied)
                nav.back()
            }
        }
    }
}



fun onProvideIdentity(sess: IdentitySession, account: Account? = null)
{
    if (true) {
        val iuri = sess.uri
        try
        {
            if (iuri != null)
            {
                val tmpHost = iuri.host
                if (tmpHost == null)
                {
                    displayError(S.badLink)
                    return
                }
                val port = iuri.port
                val path = iuri.encodedPath
                val attribs = iuri.queryMap()
                val challenge:String = attribs["chal"] ?: run {
                    displayError(S.badLink)
                    return
                }
                val cookie = attribs["cookie"]
                val op = attribs["op"]
                val responseProtocol = attribs["proto"]
                var protocol = responseProtocol ?: iuri.scheme  // Prefer the protocol requested by the other side, otherwise use the same protocol we got the request from

                if (protocol == "nexid") protocol = "http"  // workaround a bug in some servers where they don't provide a protocol in their uri.

                val portStr = if ((port > 0) && (port != 80) && (port != 443)) ":" + port.toString() else ""

                val act = account ?: try
                {
                    wallyApp!!.primaryAccount
                }
                catch (e: PrimaryWalletInvalidException)
                {
                    displayError(S.primaryAccountRequired)
                    return
                }
                if (act.locked)
                {
                    displayError(S.NoAccounts)
                    return
                }

                val wallet = act.wallet
                val idData = sess.idData
                val seed = if (idData != null)
                {
                    if (idData.useIdentity == IdentityDomain.COMMON_IDENTITY)
                        Bip44Wallet.COMMON_IDENTITY_SEED
                    else if (idData.useIdentity == IdentityDomain.IDENTITY_BY_HASH)
                        tmpHost + path
                    else
                    {
                        LogIt.severe("Invalid identity selector; corrupt?")
                        Bip44Wallet.COMMON_IDENTITY_SEED
                    }
                }
                else
                    Bip44Wallet.COMMON_IDENTITY_SEED

                val identityDest: PayDestination = wallet.destinationFor(seed)

                // This is a coding bug in the wallet
                val secret = identityDest.secret ?: throw IdentityException("Wallet failed to provide an identity with a secret", "bad wallet", ErrorSeverity.Severe)
                val address = identityDest.address ?: throw IdentityException("Wallet failed to provide an identity with an address", "bad wallet", ErrorSeverity.Severe)

                if (op == "login")
                {
                    val chalToSign = tmpHost + portStr + "_nexid_" + op + "_" + challenge
                    LogIt.info("challenge: " + chalToSign + " cookie: " + cookie)

                    val sig = libnexa.signMessage(chalToSign.toByteArray(), secret.getSecret())
                    if (sig == null || sig.size == 0) throw IdentityException("Wallet failed to provide a signable identity", "bad wallet", ErrorSeverity.Severe)
                    val sigStr = Codec.encode64(sig)
                    LogIt.info("signature is: " + sigStr)

                    var loginReq = protocol + "://" + tmpHost + portStr + path
                    loginReq += "?op=login&addr=" + address.toString().urlEncode() + "&sig=" + sigStr.urlEncode() + (if (cookie != null) "&cookie=" + cookie.urlEncode() else "")

                    sess.whenDone?.invoke(loginReq, "", true) ?: wallyApp!!.handleLogin(loginReq)
                    return
                }
                else if ((op == "reg") || (op == "info"))
                {
                    val chalToSign = tmpHost + portStr + "_nexid_" + op + "_" + challenge
                    LogIt.info("Identity operation: reg or info: address: " + identityDest.address + "  challenge: " + chalToSign + "  cookie: " + cookie)

                    val sig = libnexa.signMessage(chalToSign.toByteArray(), secret.getSecret())
                    if (sig == null || sig.size == 0) throw IdentityException("Wallet failed to provide a signable identity", "bad wallet", ErrorSeverity.Severe)
                    val sigStr = Codec.encode64(sig)
                    LogIt.info("Signature is: " + sigStr + "  hex: " + sig.toHex())

                    val identityInfo = wallet.lookupIdentityInfo(address)

                    var loginReq = protocol + "://" + tmpHost + portStr + path + "?cookie=" + cookie.toString().urlEncode()

                    val params = mutableMapOf<String, String>()
                    params["op"] = op
                    params["addr"] = address.toString().jsonString()
                    params["sig"] = sigStr.jsonString()
                    params["cookie"] = cookie.toString().jsonString()

                    val jsonBody = StringBuilder("{")
                    var firstTime = true
                    for ((k, value) in params)
                    {
                        if (!firstTime) jsonBody.append(',')
                        else firstTime = false
                        jsonBody.append('"')
                        jsonBody.append(k)
                        jsonBody.append("""":"""")
                        jsonBody.append(value)
                        jsonBody.append('"')
                    }
                    jsonBody.append('}')

                    sess.whenDone?.invoke(loginReq, jsonBody.toString(), true) ?: wallyApp?.handlePostLogin(loginReq, jsonBody.toString())
                    return
                }
                else if (op == "sign")
                {

                    val msg = attribs["sign"]?.encodeToByteArray() // msgToSign
                    if (msg == null)
                    {
                        displayError(S.nothingToSign)
                        return
                    }
                    else
                    {
                        val msgSig = libnexa.signMessage(msg, secret.getSecret())
                        if (msgSig == null || msgSig.size == 0)
                        {
                            displayError(S.badSignature)
                            return
                        }
                        else
                        {
                            val sigStr = Codec.encode64(msgSig)

                            val msgStr = msg.decodeUtf8()
                            val s = """{ "message":"${msgStr}", "address":"${address.toString()}", "signature": "${sigStr}" }"""
                            LogIt.info(s)
                            setTextClipboard(s)
                            displayNotice(S.sigInClipboard)

                            var sigReq = protocol + "://" + tmpHost + portStr + path + "?op=sign&addr=" + address.toString() + "&sig=" + sigStr.urlEncode() + if (cookie == null) "" else "&cookie=" + cookie.urlEncode()
                            val cb = sess.whenDone
                            if (cb!=null) cb(sigReq, "", true)
                            else
                            {
                                val reply = attribs["reply"]
                                if (reply == null || reply == "true")
                                {
                                    try
                                    {
                                        val result = Url(sigReq).readBytes(HTTP_REQ_TIMEOUT_MS, 1000)
                                        if (result.size < 100) displayNotice(result.decodeUtf8())
                                    }
                                    catch (e: CannotLoadException)
                                    {
                                        displayError(S.connectionException)
                                    }
                                    catch (e: Exception)
                                    {
                                        displayUnexpectedException(e)
                                    }
                                }
                            }
                        }
                    }

                }
            }
            else  // uri was null
            {
                return
            }
        }
        catch (e: IdentityException)
        {
            logThreadException(e)
        }
    }
}
