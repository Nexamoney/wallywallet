package info.bitcoinunlimited.www.wally.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ionspin.kotlin.bignum.decimal.BigDecimal
import info.bitcoinunlimited.www.wally.*
import info.bitcoinunlimited.www.wally.ui.theme.*
import info.bitcoinunlimited.www.wally.ui.views.ResImageView
import kotlinx.coroutines.*
import org.nexa.libnexakotlin.*

fun groupedCurrencyDecimal(a: String): BigDecimal
{
    val s = a.replace(NumberGroupingSeparator, "")
    return CurrencyDecimal(s)
}

@Composable
fun TricklePayDomainView(from: TdppDomain?, to: TdppDomain, modifier: Modifier = Modifier)
{
    val badFieldBkg = colorWarning

    val act = wallyApp!!.accounts[to.accountName] ?: wallyApp!!.accounts[from?.accountName] ?: run {
        if (to.uoa == "") wallyApp!!.primaryAccount
        else
        {
            val acts = wallyApp!!.accountsFor(to.uoa)
            if (acts.size > 0) acts[0]
            else
            {
                displayError(S.NoAccounts); return@TricklePayDomainView
            }
        }
    }

    fun fmtMax(i:Long): String
    {
        // The cursor gets put in the wrong spot if a character (a comma) is injected, so for now don't allow any number grouping characters
        return if (i == -1L) "" else act.cryptoInputFormat.format(act.fromFinestUnit(i))
    }

    fun changeHandler(change: String, curAmt: Long):Triple<String,Long,Color?>
    {
        try
        {
            if (change == "")
            {
                return Triple("", -1, null)
            }
            else
            {
                // By parsing it and then reformatting it, grouping characters (i.e. the commas in numbers) are automatically placed in the right spots
                // But stuff like decimal points that haven't been used yet are removed so this needs work
                // maxper = fmtMax(to.maxper)
                val tmp = act.toFinestUnit(groupedCurrencyDecimal(change))
                if (tmp < 0)
                    return Triple(change, -1, badFieldBkg)
                return Triple(change, tmp, null)
            }
        }
        catch(e: NumberFormatException)
        {
            // If its not good, show it anyway, user might be working on it
            return Triple(change, -1, badFieldBkg)
        }
        catch(e:Exception)  // java.lang.StringIndexOutOfBoundsException
        {
            return Triple(change, -1, badFieldBkg)
        }
    }

    var assetInfo by remember { mutableStateOf(to.assetInfo)}
    var maxper = remember { mutableStateOf(fmtMax(to.maxper)) }
    var maxday = remember { mutableStateOf(fmtMax(to.maxday)) }
    var maxweek = remember { mutableStateOf(fmtMax(to.maxweek)) }
    var maxmonth = remember { mutableStateOf(fmtMax(to.maxmonth)) }


    var maxperBkg by remember { mutableStateOf<Color?>(null) }
    var maxdayBkg by remember { mutableStateOf<Color?>(null) }
    var maxweekBkg by remember { mutableStateOf<Color?>(null) }
    var maxmonthBkg by remember { mutableStateOf<Color?>(null) }

    var automaticEnabled by remember { mutableStateOf(to.automaticEnabled) }

    Column(modifier = modifier) {
        if (from == to) CenteredSectionText(S.EditTpRegistration)
        else CenteredSectionText(S.AcceptTpRegistration)
        Text(to.domain)
        CenteredSectionText(S.AcceptTpRegistrationTopic)
        Text(to.topic)
        CenteredSectionText(S.Actions)
        Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            WallyRoundedButton({
                to.assetInfo = to.assetInfo.inc()
                assetInfo = to.assetInfo
            }) {
                Text(i18n(when (assetInfo)
                {
                    TdppAction.ACCEPT -> S.accept
                    TdppAction.ASK -> S.ask
                    TdppAction.DENY -> S.deny
                }))
            }
            Spacer(Modifier.width(8.dp))
            Text(i18n(S.TpAssetInfoRequest))
        }
        CenteredSectionText(S.TpMaxHeading)
        // todo this needs to be a mutableState
        WallySwitch(automaticEnabled, S.TpEnableAutopay) {
            to.automaticEnabled = !to.automaticEnabled
            automaticEnabled = to.automaticEnabled
        }
        Spacer(Modifier.height(8.dp))
        Row {
            Text(i18n(S.TpMaxPer), Modifier.defaultMinSize(150.dp))
            WallyDecimalEntry(maxper, Modifier.weight(1f), bkgCol = maxperBkg) {
                val tmp = changeHandler(it, to.maxper)
                maxper.value = tmp.first; to.maxper = tmp.second; maxperBkg = tmp.third
                it
            }
            Text(i18n(S.NEX))
        }
        Text(to.descper, maxLines = 3)
        Spacer(Modifier.height(4.dp))
        Row {
            Text(i18n(S.TpMaxPerDay), Modifier.defaultMinSize(150.dp))
            WallyDecimalEntry(maxday, Modifier.weight(1f), bkgCol = maxdayBkg) {
                val tmp = changeHandler(it, to.maxday)
                maxday.value = tmp.first; to.maxday = tmp.second; maxdayBkg = tmp.third
                //to.maxday = act.toFinestUnit(groupedCurrencyDecimal(it))
                //maxday = it
                 it
            }
            Text(i18n(S.NEX))
        }
        Text(to.descday, maxLines = 3)
        Spacer(Modifier.height(4.dp))
        Row {
            Text(i18n(S.TpMaxPerWeek), Modifier.defaultMinSize(150.dp))
            WallyDecimalEntry(maxweek, Modifier.weight(1f), bkgCol = maxweekBkg) {
                val tmp = changeHandler(it, to.maxweek)
                maxweek.value = tmp.first; to.maxweek = tmp.second; maxweekBkg = tmp.third
                it
            }
            Text(i18n(S.NEX))
        }
        Text(to.descweek, maxLines = 3)
        Spacer(Modifier.height(4.dp))
        Row {
            Text(i18n(S.TpMaxPerMonth), Modifier.defaultMinSize(150.dp))
            WallyDecimalEntry(maxmonth, Modifier.weight(1f), bkgCol = maxmonthBkg) {
                val tmp = changeHandler(it, to.maxmonth)
                maxmonth.value = tmp.first; to.maxmonth = tmp.second; maxmonthBkg = tmp.third
                it
            }
            Text(i18n(S.NEX))
        }
        Text(to.descmonth, maxLines = 3)
    }
}

@Composable
fun TricklePayScreen(act: Account, startSess: TricklePaySession?, nav: ScreenNav)
{
    var sess by remember { mutableStateOf(startSess) }
    val domains = wallyApp!!.tpDomains.domains

    nav.onDepart {
        val s = sess
        if (s != null)
        {
            if (s.editDomain)  // save any changes to this domain whenever you leave, if user is just editing it
            {
                wallyApp!!.tpDomains.save()
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        CenteredSectionText(S.TpRegistrations)
        if (domains.size == 0)
        {
            Text(i18n(S.TpNoRegistrations), Modifier.background(WallyRowBbkg1).fillMaxWidth())
        }
        LazyColumn(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(0.1f)) {
            domains.entries.forEachIndexed { index, entry ->
                item(key = entry.key) {
                    val domain = entry.value
                    Box(Modifier.padding(4.dp, 2.dp).fillMaxWidth().background(if (index % 1 == 0) WallyRowBbkg1 else WallyRowBbkg2).clickable {
                        val editDomain = TricklePaySession(wallyApp!!.tpDomains)
                        editDomain.domain = domain
                        editDomain.editDomain = true
                        sess = editDomain
                    }) {
                        Text(entry.key)
                    }
                }
            }
        }

        WallyDivider()
        val s = sess
        if (s != null)
        {
            val pdc = s.proposedDomainChanges ?: if (s.editDomain == true) s.domain else null
            // Show proposed registration changes to this TDPP domain
            if (pdc != null)
            {
                TricklePayDomainView(s.domain, pdc, modifier = Modifier.weight(1f).padding(8.dp, 0.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.Bottom) {
                    if (!s.newDomain)
                    {
                        WallyBoringLargeTextButton(S.done, onClick = {
                            wallyApp!!.tpDomains.insert(pdc)
                            wallyApp!!.tpDomains.save()
                            sess = null
                        })
                        WallyBoringLargeTextButton(S.remove, onClick = {
                            s.domain?.let { wallyApp!!.tpDomains.remove(it) }
                            sess = null
                        })
                    }
                    else
                    {
                        WallyBoringLargeTextButton(S.accept, onClick = {
                            wallyApp!!.tpDomains.insert(pdc)
                            wallyApp!!.tpDomains.save()
                            displaySuccess(S.TpRegAccepted)
                            nav.back()
                        })
                        WallyBoringLargeTextButton(S.reject, onClick = {
                            displayNotice(S.TpRegDenied)
                            nav.back()
                        })
                    }
                }
            }
            else  // Otherwise just show the registrations buttons
            {
                if (devMode)
                {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.Bottom) {
                        WallyBoringLargeTextButton(S.removeAll, onClick = {
                            wallyApp!!.tpDomains.clear()
                        })
                    }
                }
            }
        }
    }
}