package info.bitcoinunlimited.www.wally.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.RequestQuote
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.eygraber.uri.Uri
import org.nexa.libnexakotlin.*
import info.bitcoinunlimited.www.wally.*
import info.bitcoinunlimited.www.wally.ui.theme.WallyDivider
import info.bitcoinunlimited.www.wally.ui.theme.colorPrimaryDark
import info.bitcoinunlimited.www.wally.ui.theme.wallyPurple2
import info.bitcoinunlimited.www.wally.ui.views.*
import io.ktor.http.*
import kotlinx.coroutines.flow.collectLatest
import kotlinx.io.IOException
import okio.FileNotFoundException
import kotlin.plus

private val LogIt = GetLog("wally.actperm")

class IdentitySession(val uri: Uri?, var idData: IdentityDomain?=null, val whenDone: ((String, String, Boolean?)->Boolean)?= null)
{
    var autoHandle = true  // Automatically do the network response
    val cookie:String?
    val op:String?

    init {
        cookie = uri?.getQueryParameter("cookie")
        op = uri?.getQueryParameter("op")
    }

    val pill = AccountPill(null)

    /** All the possible accounts that can be used for this identity session */
    var candidateAccounts: ListifyMap<String, Account>? = null
        set(v)
        {
            field = v
            pill.choices = v?.toList()
        }

    /** These accounts are somehow already connected with this session (already registered, for example) */
    var associatedAccounts: ListifyMap<String, Account>? = null
        set(v)
        {
            field = v
        }


    protected var cproto: String? = null
    /** Get the wallet connect protocol (http or https) */
    val walletConnectProtocol:String
        get()
        {
            cproto?.let {return it }
            val calcedProto: String = uri?.let {
                val p = it.getQueryParameter("proto")  // If caller told us, use it
                if (p != null) p
                else  // otherwise use their own scheme (if its not uri)
                {
                    val scheme = it.scheme
                    if ((scheme != null) && (scheme != IDENTITY_URI_SCHEME)) scheme
                    else TDPP_DEFAULT_PROTOCOL
                }
            } ?: TDPP_DEFAULT_PROTOCOL
            cproto = calcedProto
            return calcedProto
        }
}

// Proportional vertical spacer that allows you to specify a max and min spacing
@Composable fun ColumnScope.VSpacer(spaceWeight: Float, max: Dp = Dp.Unspecified, min: Dp = Dp.Unspecified )
{
    Spacer(modifier = Modifier.weight(spaceWeight, false).heightIn(min,max))
}
// Proportional horizontal spacer that allows you to specify a max and min spacing
@Composable fun RowScope.VSpacer(spaceWeight: Float, max: Dp = Dp.Unspecified, min: Dp = Dp.Unspecified )
{
    Spacer(modifier = Modifier.weight(spaceWeight, false).heightIn(min, max))
}

// Proportional vertical spacer that allows you to specify a max and min spacing
@Composable fun ColumnScope.HSpacer(spaceWeight: Float, max: Dp = Dp.Unspecified, min: Dp = Dp.Unspecified )
{
    Spacer(modifier = Modifier.weight(spaceWeight, false).widthIn(min,max))
}
// Proportional horizontal spacer that allows you to specify a max and min spacing
@Composable fun RowScope.HSpacer(spaceWeight: Float, max: Dp = Dp.Unspecified, min: Dp = Dp.Unspecified )
{
    Spacer(modifier = Modifier.weight(spaceWeight, false).widthIn(min, max))
}

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
fun SpecialTxPermScreen(sess: TricklePaySession)
{
    // Change the title if the request is for a partial transaction
    val titleRes = if (sess.tflags and TDPP_FLAG_PARTIAL != 0) S.IncompleteTpTransactionFrom else S.SpecialTpTransactionFrom
    val topic = sess.topic.let {
        if (it == null) ""
        else it
    }
    val acc = sess.pill.account.collectAsState().value ?: sess.candidateAccounts?.first()
    if (acc==null)
    {
        displayError(i18n(S.UnknownDomainRegisterFirst))
        nav.back()
        return
    }

    // Every time the account change, we need to recalculate what it will take to solve this TX
    LaunchedEffect(Unit) {
        sess.pill.account.collectLatest {
            sess.originalTx?.let {
                // If the account changes to something different, redo the analysis
                val pa = sess.proposalAnalysis.value
                if (sess.getRelevantAccount() != pa?.account)
                {
                    // TODO copy the tx in a nice API
                    val tx = txFor(it.chainSelector, BCHserialized(SerializationType.NETWORK, it.toByteArray()))
                    sess.abortProposal()
                    sess.proposedTx = tx
                    sess.proposalAnalysis.value = sess.analyzeCompleteAndSignTx(tx, sess.inputSatoshis, sess.tflags)
                }
            }
        }
    }

    val fromAccount = acc.nameAndChain
    val currencyCode = acc.currencyCode
    val fromEntity = sess.host
    var breakIt = false // TODO allow a debug mode that produces bad tx

    // TODO: Handle locked accounts
    val accountUnlockTrigger = remember { mutableStateOf(0) }
    val oldAccountUnlockTrigger = remember { mutableStateOf(0) }

    var isSendingSomething by remember { mutableStateOf(false) }
    var isReceiving by remember { mutableStateOf(false) }
    var isDemonstratingOwnership by remember { mutableStateOf(false) }
    var error: String by remember { mutableStateOf("") }

    // auto-complete if already accepted and sess.accepted = true (needed to unlock the account)

    val pTx = sess.proposedTx
    val panalysis = sess.proposalAnalysis.collectAsState().value
    if ((pTx == null) || (panalysis == null))  // context lost probable bug
    {
        displayUnexpectedException(TdppException(S.unavailable, "protocol context lost accepting special transaction"))
        nav.back()
        return
    }

    LogIt.info("Proposal URI: ${sess.proposalUrl}")
    LogIt.info("Proposed Special Tx: ${pTx.toHex()}")
    LogIt.info("Proposed analysis: ${panalysis}")
    // pTx.debugDump()

    var GuiCustomTxFee = ""
    var GuiCustomTxTokenSummary = ""
    var GuiCustomTxError = ""
    var DeleteButtonText = S.deny
    var netSats = 0L

    var receivingTokenTypes = 0L
    var spendingTokenTypes = 0L   // Being pulled into this transaction
    var sendingTokenTypes = 0L    // in this transaction's outputs
    var provingTokenTypes = 0L


    panalysis.myNetTokenInfo.let {
        for ((_, v) in panalysis.myNetTokenInfo)
        {
            if (v > 0) receivingTokenTypes++
            else if (v < 0) spendingTokenTypes++
            else provingTokenTypes++
        }
    }

    panalysis.let {
        // what's being paid to me - what I'm contributing.  So if I pay out then its a negative number
        netSats = panalysis.receivingSats - panalysis.myInputSatoshis

        if (panalysis.otherInputSatoshis != null)
        {
            val fee = (panalysis.myInputSatoshis + panalysis.otherInputSatoshis) - (panalysis.receivingSats + panalysis.sendingSats)
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
        sendingTokenTypes = panalysis.sendingTokenTypes
    }

    // if I'm paying out something (negative net sats) or spending some token types I'm sending something
    isSendingSomething = netSats < 0L || spendingTokenTypes > 0 || sendingTokenTypes > 0
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
    if (panalysis != null)
    {

        if (panalysis.otherInputSatoshis != null)
        {
            fee = (panalysis.myInputSatoshis + panalysis.otherInputSatoshis) - (panalysis.receivingSats + panalysis.sendingSats)
            // TODO: only show the fee if this wallet is paying it (tx is complete)
        }

        if (provingTokenTypes > 0) tokenSummary = tokenSummary + "\n" + (i18n(S.TpShowingTokens) % mapOf("tokReveal" to provingTokenTypes.toString()))

        GuiCustomTxTokenSummary = tokenSummary

        if ((receivingTokenTypes == 0L) && (spendingTokenTypes == 0L) && (provingTokenTypes == 0L) && (netSats == 0L))
        {
            error = i18n(S.TpHasNoPurpose)
        }

        panalysis.completionException?.let { error = it.message ?: "" }

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

    // helper function to accept a proposal
    fun acceptProposal()
    {
        // Turn the menu on since user has accepted an operation of this type
        enableNavMenuItem(ScreenId.TricklePayRegistrations)
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

    // helper function to reject
    fun rejectProposal()
    {
        //info.bitcoinunlimited.www.wally.LogIt.info("deny trickle pay special transaction")
        // give back any inputs we grabbed to fulfill this tx
        sess.abortProposal()
        //sess.proposedTx?.let { acc.wallet.abortTransaction(it) }
        //sess.proposedTx = null
        nav.back()
        displayNotice(S.cancelled)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Spacer(Modifier.height(16.dp))
        sess.pill.draw(false)
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
            Column(
              modifier = Modifier.fillMaxWidth(),
              horizontalAlignment = Alignment.CenterHorizontally
            ){
                VSpacer(0.02f, 16.dp)
                Text(
                  modifier = Modifier.padding(start = 48.dp, end = 48.dp),
                  text = i18n(titleRes),
                  style = MaterialTheme.typography.headlineLarge,
                  fontWeight = FontWeight.Bold,
                  textAlign = TextAlign.Center
                )
                if (fromEntity != null)
                {
                    VSpacer(0.01f, 8.dp)
                    CenteredFittedWithinSpaceText(text = fromEntity, startingFontScale = 2.0, FontWeight.Bold, fontColor = Color.Black)
                }
                if (topic != "")
                {
                    VSpacer(0.01f, 8.dp)
                    CenteredFittedWithinSpaceText(text = topic, startingFontScale = 1.5, FontWeight.Medium, fontColor = Color.Black)
                }

                val reason = sess.reason
                if (reason != null)
                {
                    VSpacer(0.01f, 4.dp)
                    Text(
                      text = reason,
                      style = MaterialTheme.typography.headlineSmall,
                      fontWeight = FontWeight.Normal,
                      color = Color.Black
                    )
                }
                VSpacer(0.04f, 16.dp)
            }

            if (error.isEmpty())
            {
                /*
                    What you are sending
                 */
                if (isSendingSomething)
                    Column (
                      modifier = Modifier.fillMaxWidth().wrapContentHeight().padding(32.dp,16.dp,32.dp,16.dp)
                    ) {
                        Text(
                          text = i18n(S.sending),
                          modifier = Modifier.fillMaxWidth(),
                          style = MaterialTheme.typography.headlineSmall,
                          textAlign = TextAlign.Center
                        )
                        VSpacer(0.01f, 16.dp)
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
                                VSpacer(0.01f, 16.dp)
                                // netSats: what's being paid to me - what I'm contributing.  So if I pay out then its a negative number
                                if (netSats < 0L)
                                    IconLabelValueRow(
                                      icon = Icons.Default.AttachMoney,
                                      label = currencyCode,
                                      value = acc.cryptoFormat.format(acc.fromFinestUnit(netSats))
                                    )
                                VSpacer(0.01f, 16.dp)
                                if (spendingTokenTypes > 0L)
                                    IconLabelValueRow(
                                      icon = Icons.Outlined.Image,
                                      labelRes = S.spendingTokens,
                                      value = spendingTokenTypes.toString()
                                    )
                                if (fee > 0L)
                                {
                                    VSpacer(0.01f, 16.dp)
                                    IconLabelValueRow(
                                      icon = Icons.Outlined.RequestQuote,
                                      label = i18n(S.fee),
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
                        .padding(32.dp,4.dp,32.dp,4.dp)
                    ) {
                        Text(
                          text = i18n(S.receiving),
                          modifier = Modifier.fillMaxWidth(),
                          style = MaterialTheme.typography.headlineSmall,
                          textAlign = TextAlign.Center,
                        )
                        VSpacer(0.01f, 8.dp)
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
                                // If the account isn't shown as part of what is being sent, we need to show it here
                                if (!isSendingSomething)
                                {
                                    IconLabelValueRow(
                                      icon = Icons.Default.AccountBalance,
                                      labelRes = S.account,
                                      value = fromAccount
                                    )
                                    VSpacer(0.01f, 16.dp)
                                }
                                // netSats: what's being paid to me - what I'm contributing.  So if I pay out then its a negative number
                                if (netSats > 0L)
                                {
                                    IconLabelValueRow(
                                      icon = Icons.Default.AttachMoney,
                                      label = currencyCode,
                                      value = acc.cryptoFormat.format(acc.fromFinestUnit(netSats))
                                    )
                                }
                                VSpacer(0.01f, 16.dp)
                                if (receivingTokenTypes > 0L)
                                {
                                    IconLabelValueRow(
                                      icon = Icons.Outlined.Image,
                                      labelRes = S.assets,
                                      value = "$receivingTokenTypes"
                                    )
                                }
                            }
                        }
                    }
            }
            else // error.isNotEmpty()
            {
                Column(
                  modifier = Modifier.weight(1f)
                    .fillMaxWidth()
                    .wrapContentHeight()
                    .padding(32.dp)
                ) {
                    CenteredSectionText(S.CannotCompleteTransaction)
                    VSpacer(0.01f, 8.dp)
                    Text(error, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center, maxLines = 10)
                }
            }

            val tm = Modifier.padding(0.dp).fillMaxWidth().align(Alignment.CenterHorizontally)
            val ts = TextStyle(fontStyle = FontStyle.Italic, fontSize = FontScale(1.5))
            // This is redundant. It appears in the new UI send card
            //Text(GuiCustomTxFee, modifier = tm, style = ts, textAlign = TextAlign.Center)
            Text(GuiCustomTxTokenSummary, modifier = tm, style = ts, textAlign = TextAlign.Center)

            Spacer(Modifier.defaultMinSize(1.dp,10.dp).weight(0.05f))

            if (GuiCustomTxError != "")
            {
                CenteredSectionText(S.CannotCompleteTransaction)
                Spacer(Modifier.height(8.dp))
                Text(GuiCustomTxError, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center, maxLines = 100, softWrap = true)
            }
        }

        Spacer(Modifier.weight(1f))
        // Bottom button row
        ButtonRowAcceptDeny({acceptProposal()}, { rejectProposal() },
          Modifier.align(Alignment.CenterHorizontally).fillMaxWidth().background(Color.White), acceptEnabled = GuiCustomTxError == "")
        /*
        Row(
          modifier = Modifier.align(Alignment.BottomCenter)
            .fillMaxWidth()
            .background(Color.White)
            .padding(2.dp),
          horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            if (GuiCustomTxError == "")
                IconTextButton(
                  icon = Icons.Outlined.Send,
                  modifier = Modifier.weight(1f),
                  description = i18n(S.accept),
                  color = wallyPurple,
                ) {
                    acceptProposal()
                }
            IconTextButton(
              icon = Icons.Outlined.Cancel,
              modifier = Modifier.weight(1f),
              description = i18n(DeleteButtonText),
              color = wallyPurple,
            ) {
                rejectProposal()
            }
        } */
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

        ButtonRowAcceptDeny(
          accept = {
              // Turn the menu on since user has accepted an operation of this type
              enableNavMenuItem(ScreenId.TricklePayRegistrations)

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
          },
          deny = {
              nav.back()
              displayNotice(S.TpAssetRequestDenied)
          }
        )
    }
}


fun AcceptIdentityPermHandler(op:String, act: Account, sess: IdentitySession, msg: ByteArray?, destToSignWith: PayDestination?, nav: ScreenNav)
{
    // Shortcut definitions
    val uri = sess.uri!!  // Only called if already checked
    val queries = uri.queryMap()
    val h = uri.host

    // If the user accepts this identity operation
    // Turn the identity menu since user has done an identity operation
    enableNavMenuItem(ScreenId.Identity)

    if (op == "sign")
    {
        val path = uri.encodedPath

        val (secret, address) = destToSignWith?.let { Pair(it.secret, it.address) } ?: getSecretAndAddress(act.wallet, h ?: "", path)

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
                if ((sess.autoHandle)||(then == null))  // Otherwise send it via the default response (http)
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
                if (then != null)
                {
                    val responseProtocol = queries["proto"]
                    var protocol = responseProtocol ?: uri.scheme
                    val cookie = queries["cookie"]

                    val sigReq = protocol + "://" + h + path +
                      "?op=sign&addr=" + address.toString().urlEncode() + "&sig=" + sigStr.urlEncode() + if (cookie == null) "" else "&cookie=" + cookie.urlEncode()
                    then(sigReq, clipboard, true)
                }
            }
        }
    }
    else
    {
        onProvideIdentity(sess, act)
        nav.back()
    }
}

@Composable
fun IdentityPermScreen(sess: IdentitySession, nav: ScreenNav)
{
    val candActs = sess.candidateAccounts
    if (candActs?.isEmpty() == true)  // If the list exists but is empty, then there are no possible accounts.  If its null, all accounts apply
    {
        displayError(S.NoAccounts)
        nav.back()
        return
    }

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

    var act = sess.pill.account.collectAsState().value
    if (act==null)
    {
        act = sess.candidateAccounts?.first()
    }
    if (act==null)
    {
        displayError(i18n(S.UnknownDomainRegisterFirst))
        nav.back()
        return
    }

    val h = uri.host

    if (op != "sign")  // sign does not need a registered identity domain so skip all this checking for this op
    {
        var idData: IdentityDomain? = null
        if (h == null)
        {
            displayError(S.badLink, "Identity request did not provide a host")
            return
        }
        idData = act.wallet.lookupIdentityDomain(h) // + path)

        if (idData == null)  // We don't know about this domain, so register and give it info in one shot
        {
            nav.back()
            nav.go(ScreenId.Identity)  // This should never be called because the account won't be one of the choices if the domain does not exist
            return
        }
        sess.idData = idData
    }

    val topText = when (op)
    {
        "info" -> S.provideInfoQuestion
        "reg" -> S.provideLoginQuestion
        "login" -> S.provideLoginQuestion
        "sign" -> S.signDataQuestion
        else ->
        {
            displayError(S.badQR, "invalid identity operation")
            nav.back()
            return
        }
    }

    Column(Modifier.fillMaxSize()) {
        Spacer(Modifier.height(16.dp))
        sess.pill.draw(false)
        Spacer(Modifier.heightIn(5.dp, 100.dp).weight(0.2f))
        CenteredFittedTitleText(topText)
        Spacer(Modifier.heightIn(1.dp, 10.dp).weight(0.05f))
        if (h != null)
        {
            val tm = Modifier.padding(0.dp).fillMaxWidth().align(Alignment.CenterHorizontally)
            val ts = TextStyle(fontStyle = FontStyle.Italic, fontSize = FontScale(1.5))
            Text(h, modifier = tm, style = ts, textAlign = TextAlign.Center)
        }
        Spacer(Modifier.heightIn(1.dp, 100.dp).weight(0.10f))
        act.name.let {
            CenteredSectionText(i18n(S.TpInAccount))
            Spacer(Modifier.heightIn(1.dp, 10.dp).weight(0.05f))
            CenteredText(it)
        }

        if (op == "sign")
        {

            var signAddr = queries["addr"]?.let { PayAddress(it) } ?: null
            var signText = queries["sign"]?.urlDecode()
            val signHex = queries["signhex"]
            if (signAddr != null)
            {
                var dest = act?.wallet?.walletDestination(signAddr)
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

        Spacer(Modifier.weight(1f))  // Push the accept/deny buttons to the bottom

        // show the current registrations by this account
        Column(modifier = Modifier.weight(0.5f).padding(12.dp,8.dp)) {
            val aa = sess.associatedAccounts
            if (aa != null && aa.isNotEmpty())
            {
                CenteredFittedText(i18n(S.ServiceAssociatedWith))
                CenteredText(aa.joinToString(", ") { it.name })
            }
        }

        Spacer(Modifier.weight(1f))  // Push the accept/deny buttons to the bottom

        ButtonRowAcceptDeny({
            AcceptIdentityPermHandler(op, act, sess, msgToSign, destToSignWith, nav)
        }, {
            nav.back()
            displayNotice(S.cancelled)
        },
          acceptText = S.yes,
          denyText = if (error == "") S.no else S.cancel,
          modifier = Modifier.align(Alignment.CenterHorizontally).fillMaxWidth().background(Color.White)
        )
        Spacer(Modifier.height(10.dp))
    }
}


fun onProvideIdentity(sess: IdentitySession, account: Account? = null): Boolean
{
    val app = wallyApp!!
    if (true)
    {
        val iuri = sess.uri
        try
        {
            if (iuri != null)
            {
                val tmpHost = iuri.host
                if (tmpHost == null)
                {
                    displayError(S.badLink)
                    return false
                }
                val port = iuri.port
                val path = iuri.encodedPath
                val attribs = iuri.queryMap()
                val challenge:String = attribs["chal"] ?: run {
                    displayError(S.badLink)
                    return false
                }
                val cookie = attribs["cookie"]
                val op:String? = attribs["op"]
                val responseProtocol = attribs["proto"]
                var protocol = responseProtocol ?: iuri.scheme  // Prefer the protocol requested by the other side, otherwise use the same protocol we got the request from

                if (protocol == "nexid") protocol = "http"  // workaround a bug in some servers where they don't provide a protocol in their uri.

                val portStr = if ((port > 0) && (port != 80) && (port != 443)) ":" + port.toString() else ""

                val act = account ?: try
                {
                    app.primaryAccount
                }
                catch (e: PrimaryWalletInvalidException)
                {
                    displayError(S.primaryAccountRequired)
                    return false
                }
                if (act.locked)
                {
                    displayError(S.NoAccounts)
                    return false
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

                // Only allow autoconnect wallet on login or reg operations (not sign or info)
                // This is before the specific op handling because those statement  return when finished
                if ((op == "login") or (op == "reg"))
                {
                    if (iuri.getQueryParameter("connect") != null)
                    {
                        val hostAndPort = tmpHost + portStr
                        wallyApp?.accessHandler?.let {
                            if (it.activeTo(hostAndPort) == null)  // only start long polling if its not already started
                                it.startLongPolling(sess.walletConnectProtocol, hostAndPort, sess.cookie)
                        }
                    }
                }

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

                    if ((sess.autoHandle)||(sess.whenDone == null)) app.handleLogin(loginReq)
                    sess.whenDone?.invoke(loginReq, "", true)
                    return true
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

                    val loginReq = protocol + "://" + tmpHost + portStr + path + "?cookie=" + cookie.toString().urlEncode()

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

                    var ret = false
                    if ((sess.autoHandle)||(sess.whenDone == null)) app.handlePostLogin(loginReq, jsonBody.toString())
                    sess.whenDone?.let { ret = it.invoke(loginReq, jsonBody.toString(), true) }
                    return ret
                }
                else if (op == "sign")
                {

                    val msg = attribs["sign"]?.encodeToByteArray() // msgToSign
                    if (msg == null)
                    {
                        displayError(S.nothingToSign)
                        return  false
                    }
                    else
                    {
                        val msgSig = libnexa.signMessage(msg, secret.getSecret())
                        if (msgSig == null || msgSig.size == 0)
                        {
                            displayError(S.badSignature)
                            return false
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
                            if ((sess.autoHandle)||(cb == null))
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
                            if (cb != null) cb(sigReq, "", true)
                        }
                    }
                }
            }
            else  // uri was null
            {
                return false
            }
        }
        catch (e: IdentityException)
        {
            logThreadException(e)
            return false
        }
    }
    return true
}


@Composable
fun SendToPermScreen(sess: TricklePaySession , nav: ScreenNav)
{
    var breakIt = false // TODO allow a debug mode that produces bad tx

    val u: Uri = sess.proposalUrl ?: return

    val sa: List<Pair<PayAddress, Long>> = sess.proposedDestinations ?: return

    val acc = sess.pill.account.collectAsState().value ?: sess.candidateAccounts?.first()
    if (acc==null)
    {
        displayError(i18n(S.UnknownDomainRegisterFirst))
        nav.back()
        return
    }
    // Every time the account change, we need to recalculate what is needed to send
    /* nothing to recalculate right now
    LaunchedEffect(Unit) {
        sess.pill.account.collectLatest {

        }
    }
    */

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
        Spacer(Modifier.height(16.dp))
        sess.pill.draw(false)
        Spacer(Modifier.height(8.dp))
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

        Spacer(Modifier.width(1.dp).weight(1f))  // push buttons to the bottom

        ButtonRowAcceptDeny({
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
        }, {
            nav.back()
            displayNotice(S.TpSendRequestDenied)
        },
          acceptText = S.accept, denyText = S.deny,
          modifier = Modifier.align(Alignment.CenterHorizontally).fillMaxWidth().background(Color.White)
        )
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

fun HandleIdentity(uri: Uri, autoHandle: Boolean, then: ((String, String,Boolean?)->Boolean)?= null): Boolean
{
    val sess = IdentitySession(uri, null, then)
    sess.autoHandle = autoHandle
    LogIt.info(sourceLoc() +": Handle identity operation")
    val h = uri.host
    if (h == null)
    {
        displayError(S.unknownOperation)
        return false
    }

    val operation = uri.getQueryParameter("op")
    if (operation == null)
    {
        displayError(S.unknownOperation)
        return false
    }
    var useOp = true
    if (operation.lowercase() == "login")
    {
        val wa = wallyApp!!
        // Get all accounts that we could use to log in, preferring the focused one
        val tryActs = wa.orderedAccounts(true)
        tryActs.reprocess(object : Comparator<String>
        {
            override fun compare(p0: String, p1: String): Int
            {
                // prefer the focused account
                if (wa.focusedAccount.value?.name == p0) return Int.MIN_VALUE
                if (wa.focusedAccount.value?.name == p1) return Int.MAX_VALUE
                return p0.compareTo(p1)
            }
        }) { e ->  // filter in accounts that have logged in
            val idd = e.value.wallet.lookupIdentityDomain(h)
            (idd != null)
        }

        if (tryActs.isEmpty())
        {
            displayError(S.UnknownDomainRegisterFirst)
            return false
        }
        // candidates and associated are the same for login
        sess.candidateAccounts = tryActs
        sess.associatedAccounts = tryActs
        // otherwise  head to the identityOp screen... (below)
    }
    else if (operation.lowercase() == "reg")
    {
        val wa = wallyApp!!
        // Get all accounts that we could use to log in, preferring the focused one
        val tryActs = wa.orderedAccounts(true)
        tryActs.reprocess(object : Comparator<String>
        {
            override fun compare(p0: String, p1: String): Int
            {
                val domainP0 = wa.accounts[p0]?.wallet?.lookupIdentityDomain(h)
                val domainP1 = wa.accounts[p1]?.wallet?.lookupIdentityDomain(h)
                // first, prefer the accounts that have an existing registration with this service
                if ((domainP0 != null)&&(domainP1 == null)) return Int.MIN_VALUE
                if ((domainP1 != null)&&(domainP0 == null)) return Int.MAX_VALUE
                // next prefer the focused account
                if (wa.focusedAccount.value?.name == p0) return Int.MIN_VALUE
                if (wa.focusedAccount.value?.name == p1) return Int.MAX_VALUE
                // otherwise, order by name
                return p0.compareTo(p1)
            }
        }) { e ->  true }  // Any account could be used for registration

        if (tryActs.isEmpty())
        {
            displayError(S.NoAccounts)
            return false
        }
        sess.candidateAccounts = tryActs

        val ascActs = wa.orderedAccounts(true)
        ascActs.reprocess(object : Comparator<String>
        {
            override fun compare(p0: String, p1: String): Int
            {
                // order by name
                return p0.compareTo(p1)
            }
        }) { e ->
            val idd = e.value.wallet.lookupIdentityDomain(h)
            (idd != null)
        }
        sess.associatedAccounts = ascActs
        if (ascActs.isEmpty()) useOp = false  // Asking to register
        else
        {
            // Look for a domain that does not require any additional info
            // TODO: this should be done within the login/perm request screen on a per account basis.  Right now I'm just making an assumption based on one match
            for (a in ascActs)
            {
                val domain = a.wallet.lookupIdentityDomain(h)
                if (domain != null)
                {
                    // make a copy of the current info, apply the new reqs to the copy
                    val d = domain.clone()
                    d.setReqs(uri.queryMap())
                    // If the new requests are NOT the same or a narrowing of the perms given, then I need to ask for perms.
                    if (!d.permsWithinReqs())
                    {
                        useOp = false
                    }
                }
            }
        }
    }
    else if (operation.lowercase() == "sign")
    {
        val wa = wallyApp!!
        // Get all accounts that we could use to log in, preferring the focused one
        val tryActs = wa.orderedAccounts(true)
        tryActs.reprocess(object : Comparator<String>
        {
            override fun compare(p0: String, p1: String): Int
            {
                val domainP0 = wa.accounts[p0]?.wallet?.lookupIdentityDomain(h)
                val domainP1 = wa.accounts[p1]?.wallet?.lookupIdentityDomain(h)
                // first, prefer the accounts that have an existing registration with this service
                if ((domainP0 != null)&&(domainP1 == null)) return Int.MIN_VALUE
                if ((domainP1 != null)&&(domainP0 == null)) return Int.MAX_VALUE
                // next prefer the focused account
                if (wa.focusedAccount.value?.name == p0) return Int.MIN_VALUE
                if (wa.focusedAccount.value?.name == p1) return Int.MAX_VALUE
                // otherwise, order by name
                return p0.compareTo(p1)
            }
        }) { e ->  true }  // Any account could be used for registration

        if (tryActs.isEmpty())
        {
            displayError(S.NoAccounts)
            return false
        }
        sess.candidateAccounts = tryActs
        sess.associatedAccounts = null
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
