@file:OptIn(ExperimentalUnsignedTypes::class)

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
import info.bitcoinunlimited.www.wally.ui2.*
import info.bitcoinunlimited.www.wally.ui2.views.FontScale
import info.bitcoinunlimited.www.wally.ui2.theme.WallyDivider
import info.bitcoinunlimited.www.wally.ui2.views.CenteredSectionText
import info.bitcoinunlimited.www.wally.ui2.views.WallyBoringLargeTextButton

private val LogIt = GetLog("wally.actperm")

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