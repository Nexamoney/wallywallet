package info.bitcoinunlimited.www.wally.ui

import androidx.compose.animation.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.FocusInteraction
import androidx.compose.foundation.interaction.HoverInteraction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.eygraber.uri.Uri
import info.bitcoinunlimited.www.wally.*
import info.bitcoinunlimited.www.wally.ui.theme.BaseBkg
import info.bitcoinunlimited.www.wally.ui.theme.SelectedBkg
import info.bitcoinunlimited.www.wally.ui.theme.WallyDivider
import info.bitcoinunlimited.www.wally.ui.theme.WallyHalfDivider
import info.bitcoinunlimited.www.wally.ui.theme.WallyRowBbkg1
import info.bitcoinunlimited.www.wally.ui.theme.WallyRowBbkg2
import info.bitcoinunlimited.www.wally.ui.theme.wallyPurpleExtraLight
import info.bitcoinunlimited.www.wally.ui.theme.wallyPurpleLight
import info.bitcoinunlimited.www.wally.ui.views.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.nexa.libnexakotlin.*


private val LogIt = GetLog("wally.identity")

@Composable
private fun switch(mode: Char, curval: Boolean, desc: Int, onAction: (Boolean) -> Unit)
{
    var cv = curval
    var text = if (mode == 'm')
    {
        // Any mandatory item is forced on; user can only reject the registration
        if (cv == false)
        {
            onAction(true)
            cv = true // compose is not working; onAction call changes the mutableState which should cause recompose but it doesn't so force this to be true for now.
        }
        "(" + i18n(S.required) + ") " + i18n(desc)
    }
    else i18n(desc)
    // Don't show info that the domain doesn't care about.  Disable mandatory switches because they can't be changed
    if (mode != 'x') WallySwitch(cv, text, enabled = (mode != 'm'), Modifier, onAction)
}

@Composable
fun IdentityDomainView(from: IdentityDomain?, to: IdentityDomain, newDomain: Boolean, modifier: Modifier = Modifier)
{
    var uniqP by remember { mutableStateOf(to.useIdentity == IdentityDomain.IDENTITY_BY_HASH) }

    var hdlP by remember { mutableStateOf(to.hdlP) }
    var emailP by remember { mutableStateOf(to.emailP) }
    var smP by remember { mutableStateOf(to.smP) }
    var avaP by remember { mutableStateOf(to.avaP) }
    var realnameP by remember { mutableStateOf(to.realnameP) }
    var dobP by remember { mutableStateOf(to.dobP) }
    var phoneP by remember { mutableStateOf(to.phoneP) }
    var postalP by remember { mutableStateOf(to.postalP) }
    var billingP by remember { mutableStateOf(to.billingP) }
    var attestP by remember { mutableStateOf(to.attestP) }

    Column(modifier = modifier) {
        if (newDomain) CenteredSectionText(S.newDomainRequestingIdentity)
        else if (from == null) CenteredSectionText(S.IdentityAssociatedWith)
        else CenteredSectionText(S.domainRequestingAdditionalIdentityInfo)
        CenteredText(to.domain)
        Spacer(Modifier.height(10.dp))

        WallySwitch(uniqP, S.useUniqueIdentity) { uniqP = it; to.useIdentity = if (it) IdentityDomain.IDENTITY_BY_HASH else IdentityDomain.COMMON_IDENTITY }
        WallyHalfDivider()


        switch(to.hdlR, hdlP, S.provideAlias) { hdlP = it ; to.hdlP = it }
        switch(to.emailR, emailP, S.provideEmail) { emailP = it; to.emailP = it }
        switch(to.smR, smP, S.provideSocialMedia) { smP = it; to.smP = it }
        switch(to.avaR, avaP, S.provideAvatar) { avaP = it; to.avaP = it }
        WallyHalfDivider()
        switch(to.realnameR, realnameP, S.provideRealName) { realnameP = it; to.realnameP = it }
        switch(to.dobR, dobP, S.provideBirthday) { dobP = it; to.dobP = it }
        switch(to.phoneR, phoneP, S.providePhone) { phoneP = it; to.phoneP = it }
        switch(to.postalR, postalP, S.providePostalAddress) { postalP = it; to.postalP = it }
        switch(to.billingR, billingP, S.provideBillingAddress) { billingP = it; to.billingP = it }
        WallyHalfDivider()
        switch(to.attestR, attestP, S.provideAttestations) { attestP = it; to.attestP = it }
    }
}


// The identity screen can either be navigated to (in which case sess is null) or be part of an identity request.
@Composable
fun IdentityScreen(act: Account, mainPill: AccountPillViewModel, sess: IdentitySession?, nav: ScreenNav)
{
    var newDomain = false

    var uri by remember { mutableStateOf<Uri?>(sess?.uri) }
    var domain by remember { mutableStateOf<IdentityDomain?>(null) }
    var origDomain by remember { mutableStateOf<IdentityDomain?>(null) }
    var account by remember { mutableStateOf<Account>(sess?.pill?.account?.value ?: act) }
    var identities = remember { mutableStateListOf(*(account.wallet.allIdentityDomains().toTypedArray())) }
    LaunchedEffect(Unit) {  // Tie the account local to changes in the account pill
        (sess?.pill?.account ?: mainPill.account).collect {
            account = it ?: act
        }
    }
    LaunchedEffect(account) {  // Tie identities to the account
        identities.removeAll({true})
        identities.addAll(account.wallet.allIdentityDomains())
    }

    val u = uri
    val host = u?.host
    if (host != null)
    {
        origDomain = account.wallet.lookupIdentityDomain(host)
        origDomain?.let { sess?.idData = it }
        val operation = u.getQueryParameter("op")
        if (operation == null)
        {
            displayError(S.unknownOperation)
            nav.back()
            return
        }

        val commonIdDest = account.wallet.destinationFor(Bip44Wallet.COMMON_IDENTITY_SEED)
        val commonIdAddress = commonIdDest.address ?: throw PrimaryWalletInvalidException()
        val identityInfo: IdentityInfo = account.wallet.lookupIdentityInfo(commonIdAddress) ?: run {
            val ii = IdentityInfo()
            ii.identity = commonIdAddress
            account.wallet.upsertIdentityInfo(ii)
            ii
        }

        if (operation.lowercase() == "reg")
        {
            domain = origDomain?.clone()
            if (domain == null)
            {
                domain = IdentityDomain(host, IdentityDomain.COMMON_IDENTITY)
                domain!!.setPerms(u.queryMap().mapValues { if (it.value == "m" || it.value == "r") true else false })
                newDomain = true
            }
            domain!!.setReqs(u.queryMap().toMutableMap())
            sess?.idData = domain
        }
    }

    val wallet = account.wallet
    LogIt.info("identity domain count:" + identities.size.toString() + " " + wallet.allIdentityDomains().map { it.domain }.toString())

    val d = domain

    Column(Modifier.fillMaxSize()) {
        Spacer(Modifier.height(16.dp))
        (sess?.pill ?: mainPill).draw(false)
        Spacer(Modifier.height(5.dp))
        if (d==null)  // show my info
        {
            Row {
                Text(text = i18n(S.commonIdentityForAccount) % mapOf("act" to account.name),
                  modifier = Modifier.padding(0.dp).weight(1f),
                  style = WallySectionTextStyle(),
                  textAlign = TextAlign.Center
                )
            }
            // Show a share identity link on the front screen
            val dest = wallet.destinationFor(Bip44Wallet.COMMON_IDENTITY_SEED)
            val destStr = dest.address.toString()
            SelectionContainer(Modifier.fillMaxWidth(0.98f)) {
                CenteredFittedText(destStr)
            }
            val mydata = wallet.identityInfo[dest.address]
            Row {
                 IconButton(
                   onClick = { nav.go(ScreenId.IdentityEdit) }
                 ) {
                     Icon(Icons.Filled.EditNote, contentDescription = "Edit pen", modifier = Modifier.size(32.dp))
                 }
                 if (mydata != null)
                 {
                     // QR code
                     /*
                var uri = "nexid://p2p?op=share&addr=" + destStr
                if (hdl != null && hdl != "") uri = uri + "&hdl=" + URLEncoder.encode(hdl, "utf-8")
                if (email != null && email != "") uri = uri + "&em=" + email.encodeToByteArray().toHex()
                if (socialmedia != null && socialmedia != "") uri = uri + "&sm=" + URLEncoder.encode(socialmedia, "utf-8")
                LogIt.info("encoded URI: " + uri)

                val sz = min(ui.commonIdentityQRCode.getWidth().toLong(), ui.commonIdentityQRCode.getHeight().toLong())
                val qr = textToQREncode(uri, sz.toInt())
                 */

                     Column(Modifier.padding(8.dp)) {
                         Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                             Text(mydata.hdl)
                             Text(mydata.realname)
                         }
                         if (mydata.email.isNotBlank()) Text(mydata.email)
                         if (mydata.sm.isNotBlank()) Text(mydata.sm)
                     }

                 }
             }
        }

        if (sess == null)  // If this is not an action permission request, then show the current registrations by this account
        {
            CenteredSectionText(S.IdentityRegistrations)
            if (identities.size == 0)
            {
                Text(i18n(S.NoIdentitiesRegistered), Modifier.background(wallyPurpleLight).fillMaxWidth())
            }
            else
            {
                // Identity registration list
                LazyColumn(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier) {
                    identities.forEachIndexed { index, entry ->
                        item(key = entry.domain) {
                            Box(Modifier.padding(5.dp, 3.dp).fillMaxWidth().clip(RoundedCornerShape(12.dp))
                              .background(if (d == entry) wallyPurpleLight else wallyPurpleExtraLight)
                              .padding(8.dp, 8.dp).clickable {
                                val d1 = domain?.clone()
                                if (d1 != null)
                                {
                                    wallet.upsertIdentityDomain(d1)
                                    wallet.save(true)
                                }
                                if (domain != entry) domain = entry
                                else domain = null
                            }) {
                                Text(entry.domain)
                            }
                        }
                    }
                }
            }
        }

        WallyDivider()
        if (d != null)
        {
            IdentityDomainView(origDomain, d, newDomain, modifier = Modifier.padding(8.dp, 8.dp))
            if (sess != null)  // If this is not an action permission request, then show the current registrations by this account
            {
                Column(modifier = Modifier.weight(0.5f).padding(12.dp,8.dp)) {
                    val aa = sess.associatedAccounts
                    if (aa != null && aa.isNotEmpty())
                    {
                        CenteredFittedText(i18n(S.ServiceAssociatedWith))
                        CenteredText(aa.joinToString(", ") { it.name })
                    }
                    Spacer(Modifier.height(8.dp))
                    if (origDomain != null)
                    {
                        CenteredText(i18n(S.existingRegistration) % mapOf("act" to account.name))
                    }
                    else
                    {
                        CenteredText(i18n(S.nonexistentRegistration) % mapOf("act" to account.name))
                    }

                }
                WallyDivider()
            }
            // fillMaxHeight() pushes the buttons to the bottom because of the alignment
            Row(modifier = Modifier.fillMaxWidth().fillMaxHeight(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.Bottom) {
                if (uri == null)  // This is not a request to login; its just the user doing edits
                {
                    Button(
                      onClick = {
                          // Turn the menu on since user has accepted an operation of this type
                          enableNavMenuItem(ScreenId.Identity)

                          val saveDomain = d.clone()
                          wallet.upsertIdentityDomain(saveDomain)
                          wallet.save(true)
                          uri = null
                          domain = null
                      }
                    ) {
                        Text(i18n(S.Back))
                    }
                    OutlinedButton(
                      onClick = {
                          LogIt.info("Wallet ${account.wallet.name} removing domain ${d.domain}")
                          LogIt.info(account.wallet.identityDomain.keys.joinToString(", "))
                          account.wallet.removeIdentityDomain(d.domain)
                          laterJob { account.wallet.save(true) }
                          identities.removeAll { it.domain == d.domain }
                          LogIt.info(account.wallet.identityDomain.keys.joinToString(", "))
                          displayNotice(S.removed)
                          uri = null
                          domain = null
                      },
                      modifier = Modifier.testTag("RemoveIdentityButton")
                    ) {
                        Text(i18n(S.remove))
                    }
                }
                else  // this is a login or registration request
                {
                    // Bottom button row
                    ButtonRowAcceptDeny({
                        // Turn the menu on since user has accepted an operation of this type
                        enableNavMenuItem(ScreenId.Identity)
                        val saveDomain = d.clone()
                        sess?.idData = d
                        displaySuccess(S.Processing)
                        laterJob {
                            val success = if (sess?.uri != null) onProvideIdentity(sess, account) else true
                            if (success)
                            {
                                wallet.upsertIdentityDomain(saveDomain)
                                wallet.save(true)
                                displaySuccess(S.TpRegAccepted)
                                // Only allow autoconnect wallet on login or reg operations (not sign or info)
                                if ((sess?.op == "login") or (sess?.op == "reg"))
                                {
                                    val u = sess?.uri
                                    if (u != null)
                                    {
                                        if (u.getQueryParameter("connect") != null)
                                        {
                                            val host = u.host
                                            if (host != null)
                                            {
                                                wallyApp?.accessHandler?.let {
                                                    if (it.activeTo(host) == null)  // only start long polling if its not already started
                                                      it.startLongPolling(sess.walletConnectProtocol, host, sess.cookie)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            nav.back()
                        }
                    }, {
                        displayNotice(S.TpRegDenied)
                          nav.back()
                    },
                      Modifier.fillMaxWidth().background(Color.White))
                    /*
                    Button(
                      onClick = {
                          // Turn the menu on since user has accepted an operation of this type
                          enableNavMenuItem(ScreenId.TricklePay)
                          val saveDomain = d.clone()
                          sess?.idData = d
                          laterJob {
                              val success = if (sess?.uri != null) onProvideIdentity(sess, account) else true
                              if (success)
                              {
                                  wallet.upsertIdentityDomain(saveDomain)
                                  wallet.save(true)
                                  displaySuccess(S.TpRegAccepted)
                              }
                              nav.back()
                          }
                      }
                    ) {
                        Text(i18n(S.accept))
                    }
                    OutlinedButton(
                      onClick = {
                          displayNotice(S.TpRegDenied)
                          nav.back()
                      }
                    ) {
                        Text(i18n(S.reject))
                      }
                     */
                }
            }
        }
        else  // Otherwise just show the registrations buttons
        {
            if (devMode)
            {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.Bottom) {
                    Button(
                      onClick = {
                          wallet.allIdentityDomains().forEach {
                              wallet.removeIdentityDomain(it.domain)
                          }
                          identities.removeAll({true})
                          identities.addAll(account.wallet.allIdentityDomains())
                          laterJob { wallet.save(true) }
                      }
                    ) {
                        Text(i18n(S.removeAll))
                    }
                }
            }
        }
    }
}

/** Standard Wally data entry field. */
@Composable
fun ThinDataEntry(value: String, modifier: Modifier = Modifier, textStyle: TextStyle? = null, keyboardOptions: KeyboardOptions?=null, bkgCol: Color? = null, onValueChange: ((String) -> Unit)? = null)
{
    val ts = textStyle ?: MaterialTheme.typography.bodyMedium.copy(fontSize = 18.sp)
    val scope = rememberCoroutineScope()
    val bkgColor = remember { Animatable(BaseBkg) }
    val ia = remember { MutableInteractionSource() }
    // Track whenever we are inside a data entry field, because the soft keyboard will appear & we want to modify the screen based on soft
    // keyboard state
    LaunchedEffect(ia) {
        var entries=0
        try
        {
            ia.interactions.collect {
                //LogIt.info("WallyDataEntry interaction: $it")
                when (it)
                {
                    // Hover for mouse platforms, Focus for touch platforms
                    is HoverInteraction.Enter, is FocusInteraction.Focus ->
                    {
                        scope.launch(exceptionHandler) {
                            bkgColor.animateTo(bkgCol ?: SelectedBkg, animationSpec = tween(500))
                        }
                        if (entries==0) UxInTextEntry(true)
                        entries++
                    }

                    is HoverInteraction.Exit, is FocusInteraction.Unfocus ->
                    {
                        scope.launch(exceptionHandler) {
                            bkgColor.animateTo(bkgCol ?: BaseBkg, animationSpec = tween(500))
                        }
                        entries--
                        if (entries==0) UxInTextEntry(false)
                    }
                }
            }
        }
        catch(e: CancellationException)
        {
            // LogIt.info("WallyDataEntry cancelled $entries")
            if (entries>0) UxInTextEntry(false)
        }
    }

    BasicTextField(
      value,
      onValueChange ?: { },
      textStyle = ts,
      interactionSource = ia,
      modifier = modifier,
      keyboardOptions = keyboardOptions ?: KeyboardOptions(imeAction = ImeAction.Done),
      decorationBox = { tf ->
          Box(Modifier.hoverable(ia, true)
            .background(bkgCol ?: bkgColor.value)
            /*
            .drawBehind {
                val strokeWidthPx = 1.dp.toPx()
                val verticalOffset = size.height - 2.sp.toPx()
                drawLine(
                  color = Color.Black,
                  strokeWidth = strokeWidthPx,
                  start = Offset(0f, verticalOffset),
                  end = Offset(size.width, verticalOffset))
            } */
            )
          {
              tf()
          }
      }
    )
}

@Composable
fun TitledBox(
  title: Int,
  modifier: Modifier = Modifier,
  content: @Composable BoxScope.() -> Unit
) {
    Box(modifier = modifier.padding(top = 8.dp)) {
        Box(modifier = Modifier.fillMaxWidth().border(2.dp, Color.Gray).padding(16.dp,12.dp, 0.dp, 5.dp)) {
            content()
        }
        Box(
          modifier = Modifier
            .align(Alignment.TopStart)
            .offset(x = 12.dp, y = (-12).dp) // float above the border
            .background(Color.White) // mask the border behind the text
            .zIndex(1f)
        ) {
            Text(
                text = i18n(title),
                fontSize = 14.sp,
                color = Color.Gray
            )
        }
    }
}

@Composable
fun IdentityEditScreen(account: Account, nav: ScreenNav)
{
    val commonIdDest = account.wallet.destinationFor(Bip44Wallet.COMMON_IDENTITY_SEED)
    val commonIdAddress = commonIdDest.address ?: throw PrimaryWalletInvalidException()
    // if null
    val identityInfo: IdentityInfo = account.wallet.lookupIdentityInfo(commonIdAddress) ?: run {
         val ii = IdentityInfo()
        ii.identity = commonIdAddress
        account.wallet.upsertIdentityInfo(ii)
        ii
    }

    var hdl by remember { mutableStateOf(identityInfo.hdl) }
    var email by remember { mutableStateOf(identityInfo.email) }
    var fullname by remember { mutableStateOf(identityInfo.realname) }
    var postal by remember { mutableStateOf(identityInfo.postal) }
    var billing by remember { mutableStateOf(identityInfo.billing) }
    var sm by remember { mutableStateOf(identityInfo.sm) }

    nav.onDepart {
        account.wallet.upsertIdentityInfo(identityInfo)
    }

    val bkgCol = Color.White
    val boxMod = Modifier.padding(0.dp, 8.dp)
    val entryMod = Modifier.fillMaxWidth()
    Column(modifier = Modifier.padding(10.dp, 4.dp)) {
        CenteredSectionText(S.IdentityAssociatedWith)
        Spacer(Modifier.height(8.dp))

        //CenteredFittedText(S.UsernameOrAliasText, 1.3)
        //WallyTextEntry(hdl, Modifier.fillMaxWidth(WIDTH_FRAC).align(Alignment.CenterHorizontally), bkgCol=bkgCol) { identityInfo.hdl = it; hdl = it }

        TitledBox(S.UsernameOrAliasText, boxMod) {
            ThinDataEntry(hdl, entryMod, bkgCol = bkgCol) { identityInfo.hdl = it; hdl = it }
        }
        TitledBox(S.EmailText, boxMod) {
            ThinDataEntry(email, entryMod, bkgCol=bkgCol) { identityInfo.email = it; email = it }
        }

        TitledBox(S.NameText, boxMod) {
            ThinDataEntry(fullname, entryMod, bkgCol = bkgCol) { identityInfo.realname = it; fullname = it }
        }

        TitledBox(S.PostalAddressText, boxMod) {
            ThinDataEntry(postal, entryMod, bkgCol = bkgCol) { identityInfo.postal = it; postal = it }
        }

        TitledBox(S.BillingAddressText, boxMod) {
            ThinDataEntry(billing, entryMod, bkgCol = bkgCol) { identityInfo.billing = it; billing = it }
        }

        TitledBox(S.SocialMediaText, boxMod) {
            ThinDataEntry(sm, entryMod, bkgCol = bkgCol) { identityInfo.sm = it; sm = it }
        }

        Spacer(Modifier.height(1.dp).weight(1.0f))
        CenteredText(i18n(S.IdentityInfoNote), Modifier.padding(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            OutlinedButton(onClick = {
                account.wallet.identityInfo.clear()
                account.wallet.identityInfoChanged = true
            }) {
                Text(i18n(S.clear))
            }
            Button(
              onClick = { nav.back() }
            ) {
                Text(i18n(S.done))
            }
        }
    }
}