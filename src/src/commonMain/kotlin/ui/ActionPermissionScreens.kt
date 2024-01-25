package info.bitcoinunlimited.www.wally.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.eygraber.uri.Uri
import org.nexa.libnexakotlin.*
import info.bitcoinunlimited.www.wally.*
import info.bitcoinunlimited.www.wally.ui.theme.*
import io.ktor.utils.io.errors.*
import okio.FileNotFoundException

private val LogIt = GetLog("wally.actperm")

/** TODO MOVE to TP file */
@Composable
fun TpSettingsScreen(acc: Account, tp: TricklePaySession, nav: ScreenNav)
{
    Column {
        TitleText(S.SpecialTpTransactionFrom)
        Text("from who")
        SectionText(S.TpInAccount)
        Text("account ")
        SectionText(S.TpEntails)
        Text("tx cost")
        Text( "tx fee")
        Text("token summary")

        WallyDivider()

        Row(modifier = Modifier.fillMaxWidth().padding(0.dp), horizontalArrangement = Arrangement.SpaceAround, verticalAlignment = Alignment.CenterVertically) {
            WallyBoringLargeTextButton(S.accept)
            {
                TODO()
            }
            WallyBoringLargeTextButton(S.deny)
            {
                TODO()
            }
        }
    }
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
fun IdentityPermScreen(uri: Uri?, nav: ScreenNav)
{
    if (uri == null)
    {
        displayError(S.badQR)
        nav.back()
        return
    }

    var triggeredNewDomain by remember { mutableStateOf(false) }
    val perms by remember { mutableStateMapOf<String, Boolean>() }

    val queries = uri.queryMap()
    val op = queries["op"]?.lowercase() ?: ""

    var msgToSign by remember { mutableStateOf<ByteArray?>(null) }

    var error = ""  // If there's some error but you want to insist on the user actively dismissing the screen, set this to the error to display


    /*
    fun updatePermsFromIntent(intent: Intent)
    {
        for (k in nexidParams)  // Update new perms
        {
            if (intent.hasExtra(k + "P"))
            {
                val r = intent.getBooleanExtra(k + "P", false)
                LogIt.info("updated " + k + " to " + r)
                perms[k] = r
            }
        }
    }

     */

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

    if (op != "sign")  // sign does not need a registered identity domain so skip all this checking for this op
    {
        if (h == null)
        {
            displayError(S.badLink, "Identity request did not provide a host")
            return
        }
        val idData = w.lookupIdentityDomain(h) // + path)
        if (idData == null)  // We don't know about this domain, so register and give it info in one shot
        {
            if (op != "reg")
            {
                displayError(S.UnknownDomainRegisterFirst)
                return
            }
            if (triggeredNewDomain == false)  // I only want to drop into the new domain settings once
            {
                triggeredNewDomain = true
                TODO()
                //nav.go(ScreenId.IdentitySettings)
            }
        }
        else // We know about this domain, but its asking for more info
        {

            /*
            idData.getPerms(perms)
            idData.getReqs(reqs)
            var settingsNeedChanging = false
            for ((k, v) in attribs)
            {
                if (k in nexidParams)
                {
                    if (reqs[k] != v)   // Change the information requirements coming from this domain: TODO, ignore looser requirements
                    {
                        reqs[k] = v
                        settingsNeedChanging = true
                    }
                }
            }
            if (settingsNeedChanging)
            {
                var intent = Intent(context, DomainIdentitySettings::class.java)
                intent.putExtra("domainName", h)
                intent.putExtra("title", getString(R.string.domainRequestingAdditionalIdentityInfo))
                intent.putExtra("mode", "reg")
                for ((k, v) in attribs)
                {
                    intent.putExtra(k, v)
                }

                startActivityForResult(intent, IDENTITY_SETTINGS_RESULT)
            }
         */
        }

    }


    val topText = when(op)
    {
        "info" -> S.provideInfoQuestion
        "reg"  -> TODO()
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
                WallyEmphasisBox(Modifier.weight(1f).fillMaxWidth()) {
                    Text(signText, modifier = Modifier.wrapContentHeight(align = Alignment.CenterVertically), colorPrimaryDark)
                }
                msgToSign = signText.toByteArray()
            }
            else if (signHex != null)
            {
                CenteredSectionText(S.binaryToSign)
                WallyEmphasisBox(Modifier.weight(1f).fillMaxWidth()) {
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

        Spacer(Modifier.height(10.dp))

        // TODO get all this on the bottom
        if (GuiCustomTxError != "")
        {
            SectionText(S.CannotCompleteTransaction)
            Text(GuiCustomTxError)
        }

        WallyDivider()
        Spacer(Modifier.height(5.dp))

        Row(modifier = Modifier.fillMaxWidth().padding(0.dp), horizontalArrangement = Arrangement.SpaceAround, verticalAlignment = Alignment.CenterVertically) {
            if (GuiCustomTxError == "") WallyBoringLargeTextButton(S.accept)
            {
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
                if (sess != null)
                {
                    sess.proposedTx?.let { acc.wallet.abortTransaction(it) }
                    sess.proposedTx = null
                }
                nav.back()
                displayNotice(S.cancelled)
            }
        }
    }
}

@Composable
fun AssetInfoPermScreen(acc: Account, tp: TricklePaySession , nav: ScreenNav)
{
    Column {
        CenteredSectionText(S.TpAssetInfoRequest)
        Text("tp entity")
        CenteredSectionText(S.TpHandledByAccount)
        Text("account ")
        CenteredSectionText(S.TpEntails)
        val numAssetsToShare = 10.toString()
        Text(i18n(S.TpAssetMatches) % mapOf("num" to numAssetsToShare ))

        SectionText(S.TpAssetInfoNotXfer)

        Spacer(Modifier.height(10.dp))
        WallyDivider()
        Spacer(Modifier.height(5.dp))

        Row(modifier = Modifier.fillMaxWidth().padding(0.dp), horizontalArrangement = Arrangement.SpaceAround, verticalAlignment = Alignment.CenterVertically) {
            WallyBoringLargeTextButton(S.accept)
            {
                TODO()
            }
            WallyBoringLargeTextButton(S.deny)
            {
                TODO()
            }
        }
    }
}

@Composable
fun SendToPermScreen(acc: Account, tp: TricklePaySession , nav: ScreenNav)
{
    Column {
        CenteredSectionText(S.TpSendToTitle)
        Text("to who")
        CenteredSectionText(S.TpInAccount)
        Text("account ")
        CenteredSectionText(S.TpSpends)
        Text("send amount")
        CenteredSectionText(S.TpWhyNotAuto)
        Text("why not auto send")

        Spacer(Modifier.height(10.dp))
        WallyDivider()
        Spacer(Modifier.height(5.dp))

        Row(modifier = Modifier.fillMaxWidth().padding(0.dp), horizontalArrangement = Arrangement.SpaceAround, verticalAlignment = Alignment.CenterVertically) {
            WallyBoringLargeTextButton(S.accept)
            {
                TODO()
            }
            WallyBoringLargeTextButton(S.deny)
            {
                TODO()
            }
        }
    }
}



