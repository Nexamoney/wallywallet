package info.bitcoinunlimited.www.wally.uiv2

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.outlined.Cancel
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.RequestQuote
import androidx.compose.material.icons.outlined.Send
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.nexa.libnexakotlin.*
import info.bitcoinunlimited.www.wally.*
import info.bitcoinunlimited.www.wally.ui.*
import info.bitcoinunlimited.www.wally.ui.theme.*
import info.bitcoinunlimited.www.wally.ui2.themeUi2.wallyPurple
import info.bitcoinunlimited.www.wally.ui2.themeUi2.wallyPurple2

private val LogIt = GetLog("wally.actperm.ui2")

@Composable
fun IconLabelValueRow(icon: ImageVector, labelRes: Int, value: String){
    IconLabelValueRow(icon, i18n(labelRes), value)
}

@Composable
fun IconLabelValueRow(icon: ImageVector, label: String, value: String)
{
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
          verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, label, tint = wallyPurple2)
            Spacer(modifier = Modifier.width(8.dp))
            Text(
              text = label,
              style = MaterialTheme.typography.labelLarge,
              color = wallyPurple2
            )
        }
        Spacer(Modifier.width(24.dp))
        Text(
          text = value,
          style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
fun SpecialTxPermScreenUi2(acc: Account, sess: TricklePaySession)
{
    // Change the title if the request is for a partial transaction
    val titleRes = if (sess.tflags and TDPP_FLAG_PARTIAL != 0) S.IncompleteTpTransactionFrom else S.SpecialTpTransactionFrom
    val tpc = sess.topic.let {
        if (it == null) ""
        else ":" + it
    }
    val fromAccount = acc.name
    val currencyCode = acc.currencyCode
    val fromEntity = sess.host + tpc
    var breakIt = false // TODO allow a debug mode that produces bad tx

    // TODO: Handle locked accounts
    var accountUnlockTrigger = remember { mutableStateOf(0) }
    var oldAccountUnlockTrigger = remember { mutableStateOf(0) }

    var isSendingSomething by remember { mutableStateOf(false) }
    var isReceiving by remember { mutableStateOf(false) }
    var isDemonstratingOwnership by remember { mutableStateOf(false) }
    var error: String by remember { mutableStateOf("") }

    // auto-complete if already accepted and sess.accepted = true (needed to unlock the account)

    val pTx = sess.proposedTx
    val panalysis = sess.proposalAnalysis
    if ((pTx == null) || (panalysis == null))  // context lost probable bug
    {
        displayUnexpectedException(TdppException(S.unavailable, "protocol context lost accepting special transaction"))
        nav.back()
        return
    }


    val proposal = sess.proposalAnalysis
    var GuiCustomTxFee = ""
    var GuiCustomTxTokenSummary = ""
    var GuiCustomTxError = ""
    var DeleteButtonText = S.deny
    var netSats = 0L

    var receivingTokenTypes = 0L
    var spendingTokenTypes = 0L
    var provingTokenTypes = 0L


    proposal?.myNetTokenInfo?.let {
        for ((_, v) in proposal.myNetTokenInfo)
        {
            if (v > 0) receivingTokenTypes++
            else if (v < 0) spendingTokenTypes++
            else provingTokenTypes++
        }
    }

    proposal?.let {
        // what's being paid to me - what I'm contributing.  So if I pay out then its a negative number
        netSats = proposal.receivingSats - proposal.myInputSatoshis

        if (proposal.otherInputSatoshis != null)
        {
            val fee = (proposal.myInputSatoshis + proposal.otherInputSatoshis) - (proposal.receivingSats + proposal.sendingSats)
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
    }

    // if I'm paying out something (negative net sats) or spending some token types I'm sending something
    isSendingSomething = netSats < 0L || spendingTokenTypes < 0
    // if I'm receiving some sats (positive net sats) or receiving some token types I'm receiving something
    isReceiving = netSats > 0L || receivingTokenTypes > 0

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

    var fee = 0L
    var tokenSummary = ""
    if (proposal != null)
    {

        if (proposal.otherInputSatoshis != null)
        {
            fee = (proposal.myInputSatoshis + proposal.otherInputSatoshis) - (proposal.receivingSats + proposal.sendingSats)
            // TODO: only show the fee if this wallet is paying it (tx is complete)
        }

        if (provingTokenTypes > 0) tokenSummary = tokenSummary + "\n" + (i18n(S.TpShowingTokens) % mapOf("tokReveal" to provingTokenTypes.toString()))

        GuiCustomTxTokenSummary = tokenSummary

        if ((receivingTokenTypes == 0L) && (spendingTokenTypes == 0L) && (provingTokenTypes == 0L) && (netSats == 0L))
        {
            error = i18n(S.TpHasNoPurpose)
        }

        proposal.completionException?.let { error = it.message ?: "" }

        if (error.isNotEmpty())  // If there's an exception, the only possibility is to abort
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

    fun acceptProposal()
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

    fun rejectProposal()
    {
        //info.bitcoinunlimited.www.wally.LogIt.info("deny trickle pay special transaction")
        // give back any inputs we grabbed to fulfill this tx
        sess.proposedTx?.let { acc.wallet.abortTransaction(it) }
        sess.proposedTx = null
        nav.back()
        displayNotice(S.cancelled)
    }

    Box(
      modifier = Modifier.fillMaxSize()
    ) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
            Column(
              modifier = Modifier.fillMaxWidth(),
              horizontalAlignment = Alignment.CenterHorizontally
            ){
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                  modifier = Modifier.padding(start = 48.dp, end = 48.dp),
                  text = i18n(titleRes),
                  style = MaterialTheme.typography.headlineLarge,
                  fontWeight = FontWeight.Bold,
                  textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                  text = fromEntity,
                  style = MaterialTheme.typography.headlineSmall,
                  fontWeight = FontWeight.Bold,
                  color = Color.Black
                )
            }

            if (error.isEmpty())
            {
                /*
                    What you are sending
                 */
                if (isSendingSomething)
                    Column (
                      modifier = Modifier.fillMaxWidth()
                        .wrapContentHeight()
                        .padding(32.dp)
                    ) {
                        Text(
                          text = i18n(S.sending),
                          modifier = Modifier.fillMaxWidth(),
                          style = MaterialTheme.typography.headlineSmall,
                          textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(16.dp))
                        Box(
                          modifier = Modifier.fillMaxWidth()
                            .wrapContentHeight()
                            .border(
                              width = 1.dp,
                              color = Color.LightGray,
                              shape = RoundedCornerShape(16.dp)
                            )
                        ) {
                            Column(
                              modifier = Modifier.fillMaxWidth()
                                .wrapContentHeight()
                                .padding(16.dp),
                              horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                IconLabelValueRow(
                                  icon = Icons.Default.AccountBalance,
                                  labelRes = S.account,
                                  value = fromAccount
                                )
                                // netSats: what's being paid to me - what I'm contributing.  So if I pay out then its a negative number
                                if (netSats < 0L)
                                    IconLabelValueRow(
                                      icon = Icons.Default.AttachMoney,
                                      label = currencyCode,
                                      value = acc.cryptoFormat.format(acc.fromFinestUnit(netSats))
                                    )
                                Spacer(modifier = Modifier.height(16.dp))
                                if (spendingTokenTypes > 0L)
                                    IconLabelValueRow(
                                      icon = Icons.Outlined.Image,
                                      labelRes = S.spendingTokens,
                                      value = spendingTokenTypes.toString()
                                    )
                                if (fee > 0L)
                                {
                                    IconLabelValueRow(
                                      icon = Icons.Outlined.RequestQuote,
                                      label = "Fee",
                                      value = (i18n(S.ForAFeeOf) % mapOf("fee" to acc.cryptoFormat.format(acc.fromFinestUnit(fee)), "units" to acc.currencyCode))
                                    )
                                }
                            }
                        }
                    }

                /*
                    What you are receiving
                 */
                if (isReceiving)
                    Column(
                      modifier = Modifier.fillMaxWidth()
                        .wrapContentHeight()
                        .padding(32.dp)
                    ) {
                        Text(
                          text = i18n(S.receiving),
                          modifier = Modifier.fillMaxWidth(),
                          style = MaterialTheme.typography.headlineSmall,
                          textAlign = TextAlign.Center,
                        )
                        Spacer(Modifier.height(16.dp))
                        Box(
                          modifier = Modifier.fillMaxWidth()
                            .wrapContentHeight()
                            .border(
                              width = 1.dp,
                              color = Color.LightGray,
                              shape = RoundedCornerShape(16.dp)
                            )
                        ) {
                            Column(
                              modifier = Modifier.fillMaxWidth()
                                .wrapContentHeight()
                                .padding(16.dp),
                              horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                // netSats: what's being paid to me - what I'm contributing.  So if I pay out then its a negative number
                                if (netSats > 0L)
                                    IconLabelValueRow(
                                      icon = Icons.Default.AttachMoney,
                                      label = currencyCode,
                                      value = acc.cryptoFormat.format(acc.fromFinestUnit(netSats))
                                    )
                                Spacer(modifier = Modifier.height(16.dp))
                                if (receivingTokenTypes > 0L)
                                    IconLabelValueRow(
                                      icon = Icons.Outlined.Image,
                                      labelRes = S.assets,
                                      value = "$receivingTokenTypes"
                                    )
                            }
                        }
                    }
            }
            else if (error.isNotEmpty())
            /*
                Error message
            */
                Box(
                  modifier = Modifier.weight(1f)
                    .fillMaxWidth()
                    .wrapContentHeight()
                    .padding(32.dp)
                ) {
                    SectionText(S.CannotCompleteTransaction)
                    Text(error)
                }

            val tm = Modifier.padding(0.dp).fillMaxWidth().align(Alignment.CenterHorizontally)
            val ts = TextStyle(fontStyle = FontStyle.Italic, fontSize = FontScale(1.5))
            Text(GuiCustomTxFee, modifier = tm, style = ts, textAlign = TextAlign.Center)
            Text(GuiCustomTxTokenSummary, modifier = tm, style = ts, textAlign = TextAlign.Center)

            Spacer(Modifier.defaultMinSize(1.dp,10.dp).weight(1f))

            // TODO get all this on the bottom (?)
            if (GuiCustomTxError != "")
            {
                CenteredSectionText(S.CannotCompleteTransaction)
                Text(GuiCustomTxError, maxLines = 10, softWrap = true)
            }
            Spacer(Modifier.height(100.dp))
        }

        // Bottom button row
        Row(
          modifier = Modifier.align(Alignment.BottomCenter)
            .fillMaxWidth()
            .background(Color.White)
            .padding(2.dp),
          horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            if (GuiCustomTxError == "")
                IconTextButtonUi2(
                  icon = Icons.Outlined.Send,
                  modifier = Modifier.weight(1f),
                  description = i18n(S.accept),
                  color = wallyPurple,
                ) {
                    acceptProposal()
                }
            IconTextButtonUi2(
              icon = Icons.Outlined.Cancel,
              modifier = Modifier.weight(1f),
              description = i18n(DeleteButtonText),
              color = wallyPurple,
            ) {
                rejectProposal()
            }
        }
    }
}