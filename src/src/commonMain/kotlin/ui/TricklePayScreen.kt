package info.bitcoinunlimited.www.wally.ui

import androidx.compose.foundation.background
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
import info.bitcoinunlimited.www.wally.*
import info.bitcoinunlimited.www.wally.ui.theme.*
import info.bitcoinunlimited.www.wally.ui.views.ResImageView
import kotlinx.coroutines.*
import org.nexa.libnexakotlin.*

@Composable
fun TricklePayDomainView(from: TdppDomain?, to: TdppDomain, modifier: Modifier = Modifier)
{
    Column(modifier = modifier) {
        CenteredSectionText(S.AcceptTpRegistration)
        Text(to.domain)
        CenteredSectionText(S.AcceptTpRegistrationTopic)
        Text(to.topic)
        Row {
            WallyRoundedButton({}) {
                Text(i18n(when (to.assetInfo) {
                    TdppAction.ACCEPT -> S.accept
                    TdppAction.ASK -> S.ask
                    TdppAction.DENY -> S.deny
                }))
            }
            Text(i18n(S.TpAssetInfoRequest))
        }
        CenteredSectionText(S.TpMaxHeading)
        // todo this needs to be a mutableState
        WallySwitch(to.automaticEnabled, S.TpEnableAutopay) {
            to.automaticEnabled = !to.automaticEnabled
        }
        Row {
            Text(i18n(S.TpMaxPer))
            WallyTextEntry("todo")
        }
        Row {
            Text(i18n(S.TpMaxPerDay))
            WallyTextEntry("todo")
            Text(i18n(S.NEX))
        }
        Row {
            Text(i18n(S.TpMaxPerWeek))
            WallyTextEntry("todo")
            Text(i18n(S.NEX))
        }
        Row {
            Text(i18n(S.TpMaxPerMonth))
            WallyTextEntry("todo")
            Text(i18n(S.NEX))
        }
    }

}

@Composable
fun TricklePayScreen(act: Account, startSess: TricklePaySession?, nav: ScreenNav)
{
    var sess by remember { mutableStateOf(startSess) }
    val domains = wallyApp!!.tpDomains.domains

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
                    Box(Modifier.background(if (index % 1 == 0) WallyRowBbkg1 else WallyRowBbkg2)) {
                        SectionText(entry.key)
                    }
                }
            }
        }

        WallyDivider()
        val s = sess
        val pdc = s?.proposedDomainChanges
        // Show proposed registration changes to this TDPP domain
        if (pdc != null)
        {
            TricklePayDomainView(s.domain, pdc, modifier = Modifier.weight(1f))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.Bottom) {
                WallyBoringLargeTextButton(S.accept, onClick = {
                    TODO()
                })
                WallyBoringLargeTextButton(S.done, onClick = {
                    TODO()
                })
                WallyBoringLargeTextButton(S.deny, onClick = {
                    TODO()
                })
            }
        }
        else  // Otherwise just show the registrations buttons
        {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.Bottom) {
                WallyBoringLargeTextButton(S.removeAll, onClick = {
                    TODO()
                })
            }
        }
    }
}