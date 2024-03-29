package info.bitcoinunlimited.www.wally.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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

private val LogIt = GetLog("wally.actperm")


fun HandleIdentity(uri: Uri): Boolean
{
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
    if (useOp) wallyApp?.later {
        LogIt.info(sourceLoc() +": send screen change")
        externalDriver.send(GuiDriver(ScreenId.IdentityOp, uri = uri))
    }
    else wallyApp?.later { externalDriver.send(GuiDriver(ScreenId.Identity, uri = uri)) }
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
fun IdentityPermScreen(account: Account, uri: Uri?, nav: ScreenNav)
{
    if (uri == null)
    {
        displayError(S.badQR)
        nav.back()
        return
    }

    val queries = uri.queryMap()
    val op = queries["op"]?.lowercase() ?: ""

    var msgToSign by remember { mutableStateOf<ByteArray?>(null) }

    var error = ""  // If there's some error but you want to insist on the user actively dismissing the screen, set this to the error to display


    val act = try
    {
        val tmp = wallyApp!!.primaryAccount
        if (tmp == null) throw PrimaryWalletInvalidException()
        tmp
    }
    catch (e: PrimaryWalletInvalidException)
    {
        displayError(S.primaryAccountRequired)
        nav.back()
        return
    }

    val w = act.wallet

    // TODO If the primary account is locked

    val h = uri.host

    var idData:IdentityDomain? = null
    if (op != "sign")  // sign does not need a registered identity domain so skip all this checking for this op
    {
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
            var signText = queries["sign"]?.urlDecode()
            val signHex = queries["signhex"]
            if (signText != null)
            {
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
                    val (secret, address) = getSecretAndAddress(act.wallet, h ?: "", path)

                    val msg = msgToSign
                    if (msg == null)
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
                            later {
                                val msgStr = msg.decodeUtf8()
                                val s = """{ "message":"${msgStr}", "address":"${address.toString()}", "signature": "${sigStr}" }"""
                                LogIt.info(s)
                                setTextClipboard(s)
                            }
                            displayNotice(S.sigInClipboard)

                            val reply = queries["reply"]
                            if (reply == null || reply == "true")
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
                        }
                    }
                }
                else
                {
                    // idData cant be null here based on the logic above
                    idData?.let { onProvideIdentity(uri,idData, account) }
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
            sess.acceptSpecialTx(breakIt)
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
                    LogIt.info("accept trickle pay special transaction")

                    val pTx = sess.proposedTx
                    val panalysis = sess.proposalAnalysis
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
        val numAssetsToShare = sess.assetInfoList?.assets?.size ?: 0
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

                val details = sess.acceptAssetRequest()
                displaySuccess(S.TpAssetRequestAccepted, details)
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

    var accountUnlockTrigger = remember { mutableStateOf(0) }
    var oldAccountUnlockTrigger = remember { mutableStateOf(0) }

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

    Column(Modifier.padding(8.dp, 2.dp)) {
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
                val s = sess.proposedDestinations
                sess.proposedDestinations = null // This stops accidental double send
                try
                {
                    if (s != null) acc.wallet.send(s, false, i18n(S.title_activity_trickle_pay) + " " + domainAndTopic + ". " + (sess.reason ?: ""))
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



fun onProvideIdentity(iuri: Uri, idData: IdentityDomain, account: Account? = null)
{
    launch {
        try
        {
            if (iuri != null)
            {
                val tmpHost = iuri.host
                if (tmpHost == null)
                {
                    displayError(S.badLink)
                    return@launch
                }
                val port = iuri.port
                val path = iuri.encodedPath
                val attribs = iuri.queryMap()
                val challenge = attribs["chal"]
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
                    return@launch
                }
                if (act.locked)
                {
                    displayError(S.NoAccounts)
                    return@launch
                }

                val wallet = act.wallet

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

                    wallyApp!!.handleLogin(loginReq)
                    return@launch
                }
                else if ((op == "reg") || (op == "info"))
                {
                    val chalToSign = tmpHost + portStr + "_nexid_" + op + "_" + challenge
                    LogIt.info("Identity operation: reg or info: challenge: " + chalToSign + " cookie: " + cookie)

                    val sig = libnexa.signMessage(chalToSign.toByteArray(), secret.getSecret())
                    if (sig == null || sig.size == 0) throw IdentityException("Wallet failed to provide a signable identity", "bad wallet", ErrorSeverity.Severe)
                    val sigStr = Codec.encode64(sig)
                    LogIt.info("signature is: " + sigStr)

                    val identityInfo = wallet.lookupIdentityInfo(address)

                    var loginReq = protocol + "://" + tmpHost + portStr + path

                    val params = mutableMapOf<String, String>()
                    params["op"] = op
                    params["addr"] = address.toString()
                    params["sig"] = sigStr
                    params["cookie"] = cookie.toString()

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

                    wallyApp?.handlePostLogin(loginReq, jsonBody.toString())
                    return@launch
                }
                else if (op == "sign")
                {

                    val msg = attribs["sign"]?.encodeToByteArray() // msgToSign
                    if (msg == null)
                    {
                        displayError(S.nothingToSign)
                        return@launch
                    }
                    else
                    {
                        val msgSig = libnexa.signMessage(msg, secret.getSecret())
                        if (msgSig == null || msgSig.size == 0)
                        {
                            displayError(S.badSignature)
                            return@launch
                        }
                        else
                        {
                            val sigStr = Codec.encode64(msgSig)

                            val msgStr = msg.decodeUtf8()
                            val s = """{ "message":"${msgStr}", "address":"${address.toString()}", "signature": "${sigStr}" }"""
                            LogIt.info(s)
                            setTextClipboard(s)
                            displayNotice(S.sigInClipboard)

                            val reply = attribs["reply"]
                            if (reply == null || reply == "true")
                            {
                                var sigReq = protocol + "://" + tmpHost + portStr + path
                                sigReq += "?op=sign&addr=" + address.toString() + "&sig=" + sigStr.urlEncode() + if (cookie == null) "" else "&cookie=" + cookie.urlEncode()

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


                                /*
                                var forwarded = 0  // Handle URL forwarding
                                getloop@ while (forwarded < 3)
                                {
                                    LogIt.info("signature reply: " + sigReq)
                                    try
                                    {
                                        LogIt.info(sigReq)
                                        val (resp, status) = Uri.parse(sigReq).loadTextAndStatus(HTTP_REQ_TIMEOUT_MS)
                                        LogIt.info("signature response code:" + status.toString() + " response: " + resp)
                                        if ((status >= 200) and (status < 250))
                                        {
                                            displayNotice(resp)
                                            return@launch
                                        }
                                        else if ((status == 301) or (status == 302))  // Handle URL forwarding (often switching from http to https)
                                        {
                                            sigReq = resp
                                            forwarded += 1
                                            continue@getloop
                                        }
                                        else
                                        {
                                            displayNotice(resp)
                                            return@launch
                                        }

                                    }
                                    catch (e: FileNotFoundException)
                                    {
                                        displayError(S.badLink)
                                        return@launch
                                    }
                                    catch (e: java.io.IOException)
                                    {
                                        logThreadException(e)
                                        displayError(S.connectionAborted)
                                        return@launch
                                    }
                                    catch (e: java.net.ConnectException)
                                    {
                                        displayError(S.connectionException)
                                        return@launch
                                    }
                                    break@getloop  // only way to actually loop is to hit a 301 or 302
                                }

                                 */
                            }
                        }
                    }

                }
            }
            else  // uri was null
            {
                return@launch
            }
        }
        catch (e: IdentityException)
        {
            logThreadException(e)
        }
    }
}
