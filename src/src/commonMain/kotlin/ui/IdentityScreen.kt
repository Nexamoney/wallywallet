package info.bitcoinunlimited.www.wally.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.eygraber.uri.Uri
import info.bitcoinunlimited.www.wally.*
import info.bitcoinunlimited.www.wally.ui.theme.WallyDivider
import info.bitcoinunlimited.www.wally.ui.theme.WallyHalfDivider
import info.bitcoinunlimited.www.wally.ui.theme.WallyRowBbkg1
import info.bitcoinunlimited.www.wally.ui.theme.WallyRowBbkg2
import info.bitcoinunlimited.www.wally.ui.views.*
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
fun IdentityScreen(account: Account, sess: IdentitySession?, nav: ScreenNav)
{
    var newDomain = false

    var uri by remember { mutableStateOf<Uri?>(sess?.uri) }
    var domain by remember { mutableStateOf<IdentityDomain?>(null) }

    var origDomain by remember { mutableStateOf<IdentityDomain?>(null) }

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
    var identities: MutableState<ArrayList<IdentityDomain>> = mutableStateOf(ArrayList(wallet.allIdentityDomains()))
    LogIt.info("identity domain count:" + identities.value.size.toString())
    LogIt.info(wallet.allIdentityDomains().map { it.domain }.toString())

    val d = domain

    Column(Modifier.fillMaxSize()) {
        if (d==null)  // show my info
        {
            Row {
                Text(text = i18n(S.commonIdentityForAccount) % mapOf("act" to account.name),
                  modifier = Modifier.padding(0.dp).weight(1f),
                  style = WallySectionTextStyle(),
                  textAlign = TextAlign.Center
                )
                IconButton(
                  onClick = { nav.go(ScreenId.IdentityEdit) }
                ) {
                    Icon(Icons.Filled.EditNote, contentDescription = "Edit pen", modifier = Modifier.size(32.dp))
                }
            }
            // Show a share identity link on the front screen
            val dest = wallet.destinationFor(Bip44Wallet.COMMON_IDENTITY_SEED)
            val destStr = dest.address.toString()
            CenteredFittedText(destStr)
            val mydata = wallet.identityInfo[dest.address]
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
                        Text(mydata.email)
                        Text(mydata.sm)
                    }

            }
        }

        WallyDivider()
        CenteredSectionText(S.IdentityRegistrations)
        if (identities.value.size == 0)
        {
            Text(i18n(S.NoIdentitiesRegistered), Modifier.background(WallyRowBbkg1).fillMaxWidth())
        }
        else
        {
            LazyColumn(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(0.1f)) {
                identities.value.forEachIndexed { index, entry ->
                    item(key = entry.domain) {
                        Box(Modifier.padding(4.dp, 2.dp).fillMaxWidth().background(if (index % 1 == 0) WallyRowBbkg1 else WallyRowBbkg2).clickable {
                            val d1 = domain?.clone()
                            if (d1 != null)
                            {
                                wallet.upsertIdentityDomain(d1)
                                wallet.save(true)
                            }
                            if (domain != entry) domain = entry
                            else domain = null
                        }) {
                            SectionText(entry.domain)
                        }
                    }
                }
            }
        }

        WallyDivider()
        if (d != null)
        {
            IdentityDomainView(origDomain, d, newDomain, modifier = Modifier.weight(1f).padding(8.dp, 0.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.Bottom) {
                if (uri == null)  // This is not a request to login; its just the user doing edits
                {
                    Button(
                      onClick = {
                          // Turn the menu on since user has accepted an operation of this type
                          enableNavMenuItem(ScreenId.TricklePay)

                          val saveDomain = d.clone()
                          wallet.upsertIdentityDomain(saveDomain)
                          wallet.save(true)
                          uri = null
                          domain = null
                      }
                    ) {
                        Text(i18n(S.done))
                    }
                    OutlinedButton(
                      onClick = {
                          wallet.removeIdentityDomain(d.domain)
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
                    Button(
                      onClick = {
                          // Turn the menu on since user has accepted an operation of this type
                          enableNavMenuItem(ScreenId.TricklePay)

                          val saveDomain = d.clone()
                          wallet.upsertIdentityDomain(saveDomain)
                          wallet.save(true)
                          sess?.idData = d
                          if (sess?.uri != null) onProvideIdentity(sess, account)
                          displaySuccess(S.TpRegAccepted)
                          nav.back()
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
                          identities.value = ArrayList(wallet.allIdentityDomains())
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

    Column {

        CenteredSectionText(S.IdentityAssociatedWith)
        Spacer(Modifier.height(8.dp))

        CenteredFittedText(S.UsernameOrAliasText, 1.3)
        WallyTextEntry(hdl, Modifier.fillMaxWidth(0.95f).align(Alignment.CenterHorizontally)) { identityInfo.hdl = it; hdl = it }

        CenteredFittedText(S.EmailText, 1.3)
        WallyTextEntry(email, Modifier.fillMaxWidth(0.95f).align(Alignment.CenterHorizontally)) { identityInfo.email = it; email = it }

        CenteredFittedText(S.NameText, 1.3)
        WallyTextEntry(fullname, Modifier.fillMaxWidth(0.95f).align(Alignment.CenterHorizontally)) { identityInfo.realname = it; fullname = it }

        CenteredFittedText(S.PostalAddressText, 1.3)
        WallyTextEntry(postal, Modifier.fillMaxWidth(0.95f).align(Alignment.CenterHorizontally)) { identityInfo.postal = it; postal = it }

        CenteredFittedText(S.BillingAddressText, 1.3)
        WallyTextEntry(billing, Modifier.fillMaxWidth(0.95f).align(Alignment.CenterHorizontally)) { identityInfo.billing = it; billing = it }

        CenteredFittedText(S.SocialMediaText, 1.3)
        WallyTextEntry(sm, Modifier.fillMaxWidth(0.95f).align(Alignment.CenterHorizontally)) { identityInfo.sm = it; sm = it }

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