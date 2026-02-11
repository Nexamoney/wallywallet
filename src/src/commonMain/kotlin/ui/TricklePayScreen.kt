package info.bitcoinunlimited.www.wally.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowRight
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.ionspin.kotlin.bignum.decimal.BigDecimal
import info.bitcoinunlimited.www.wally.*
import info.bitcoinunlimited.www.wally.ui.theme.colorWarning
import info.bitcoinunlimited.www.wally.ui.views.*
import org.nexa.libnexakotlin.*

fun groupedCurrencyDecimal(a: String): BigDecimal
{
    val s = a.replace(NumberGroupingSeparator, "")
    return CurrencyDecimal(s)
}

@Composable
fun TricklePayDomainView(toSession: TdppDomain, modifier: Modifier = Modifier, act: Account)
{
    val badFieldBkg = colorWarning

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

    var assetInfo by remember { mutableStateOf(toSession.assetInfo)}
    val maxper = remember { mutableStateOf(fmtMax(toSession.maxper)) }
    val maxday = remember { mutableStateOf(fmtMax(toSession.maxday)) }
    val maxweek = remember { mutableStateOf(fmtMax(toSession.maxweek)) }
    val maxmonth = remember { mutableStateOf(fmtMax(toSession.maxmonth)) }
    var automaticEnabled by remember { mutableStateOf(toSession.automaticEnabled) }

    Column(modifier = modifier.verticalScroll(rememberScrollState()).testTag("TricklePayDomainViewTag")) {
        WallyCardHeadlineContent(
          headline = i18n(S.Domain),
          content = toSession.domain,
          cardModifier = Modifier.testTag("TricklePayDomainViewDomainName")
        )
        if (toSession.topic.isNotEmpty())
        {
            WallyCardHeadlineContent(
              headline = i18n(S.Purpose),
              content = toSession.topic
            )
        }
        WallyOptionsCard(
          headline = i18n(S.Actions),
          options = listOf(TdppAction.ACCEPT, TdppAction.ASK, TdppAction.DENY),
          selectedOption = assetInfo,
          onOptionChanged = {
              toSession.assetInfo = it
              assetInfo = it
              wallyApp!!.tpDomains.insert(toSession)
          },
          optionToText = {
              when(it)
              {
                  TdppAction.ACCEPT -> i18n(S.accept)
                  TdppAction.ASK -> i18n(S.ask)
                  TdppAction.DENY -> i18n(S.deny)
              }
          }
        )

        AutopayCard(
          autoPayEnabled = automaticEnabled,
          maxPerRequest = maxper.value,
          maxPerDay = maxday.value,
          maxPerWeek = maxweek.value,
          maxPerMonth = maxmonth.value,
          onAutoPayToggled = {
              toSession.automaticEnabled = !toSession.automaticEnabled
              automaticEnabled = toSession.automaticEnabled
              wallyApp!!.tpDomains.insert(toSession)
          },
          onMaxPerRequestChanged = {
              val tmp = changeHandler(it, toSession.maxper)
              maxper.value = tmp.first; toSession.maxper = tmp.second
              wallyApp!!.tpDomains.insert(toSession)
          },
          onMaxPerDayChanged = {
              val tmp = changeHandler(it, toSession.maxday)
              maxday.value = tmp.first; toSession.maxday = tmp.second
              wallyApp!!.tpDomains.insert(toSession)
          },
          onMaxPerWeekChanged = {
              val tmp = changeHandler(it, toSession.maxweek)
              maxweek.value = tmp.first; toSession.maxweek = tmp.second
              wallyApp!!.tpDomains.insert(toSession)
          },
          onMaxPerMonthChanged = {
              val tmp = changeHandler(it, toSession.maxmonth)
              maxmonth.value = tmp.first; toSession.maxmonth = tmp.second
              wallyApp!!.tpDomains.insert(toSession)
          }
        )
    }
}

@Composable
fun TricklePayRegistrationsScreen()
{
    val domains = wallyApp!!.tpDomains.domains.collectAsState().value

    Column(Modifier.fillMaxSize()) {
        if (domains.size == 0)
        {
            WallyCardContent(i18n(S.NoServicesRegistered))
        }
        LazyColumn(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(0.1f)) {
            domains.entries.forEachIndexed { index, entry ->
                item(key = entry.key) {
                    val tdppDomain = entry.value
                    val domain = entry.key
                    val purpose = entry.value.topic
                    ListItem(
                      modifier = Modifier.background(Color.White).clickable {
                          val editDomain = TricklePaySession(wallyApp!!.tpDomains)
                          editDomain.domain = tdppDomain
                          editDomain.editDomain = true
                          nav.go(ScreenId.TricklePayRegistrations, screenSubState = "true".toByteArray(), data = editDomain)
                      },
                      headlineContent = { Text(domain) },
                      supportingContent = { Text(purpose) },
                      trailingContent = {
                          Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowRight,
                            contentDescription = "Arrow",
                          )
                      },
                      colors = ListItemDefaults.colors (
                          containerColor = Color.White
                      )
                    )
                }
            }
        }
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

@Composable
fun ConfigureTricklePayScreen(fromSession: TricklePaySession, toDomain: TdppDomain, account: Account)
{
    var fromSess by remember { mutableStateOf(fromSession) }

    nav.onDepart {
        if (fromSess.editDomain)  // save any changes to this domain whenever you leave, if user is just editing it
        {
            wallyApp!!.tpDomains.save()
        }
    }

    Column(Modifier.fillMaxSize()) {
        if (fromSess.domain == toDomain) CenteredSectionText(S.ConfigureService)
        else CenteredSectionText(S.AcceptOrRejectService)
        TricklePayDomainView(toDomain, modifier = Modifier.weight(1f).padding(8.dp, 0.dp), account)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.Bottom) {
            if (!fromSess.newDomain)
            {
                Button(
                  onClick = {
                      wallyApp!!.tpDomains.insert(toDomain)
                      wallyApp!!.tpDomains.save()
                      fromSess.whenDone?.invoke(fromSess.proposalUrl.toString(), "ok", true)
                      nav.back()
                  }
                ) {
                    Text(i18n(S.done))
                }
                Button(
                  onClick = {
                      fromSess.domain?.let { wallyApp!!.tpDomains.remove(it) }
                      fromSess.whenDone?.invoke(fromSess.proposalUrl.toString(), "", false)
                      nav.back()
                  },
                  colors = ButtonDefaults.buttonColors(
                    containerColor = Color.Red
                  )
                ) {
                    Text(i18n(S.remove))
                }
            }
            else
            {
                Button(
                  onClick = {
                      wallyApp!!.tpDomains.insert(toDomain)
                      wallyApp!!.tpDomains.save()
                      displaySuccess(S.TpRegAccepted)
                      fromSess.whenDone?.invoke(fromSess.proposalUrl.toString(), "ok", true)
                      nav.back()
                  }
                ) {
                    Text(i18n(S.accept))
                }
                Button(
                  onClick = {
                      displayNotice(S.TpRegDenied)
                      fromSess.whenDone?.invoke(fromSess.proposalUrl.toString(), "", false)
                      nav.back()
                  },
                  colors = ButtonDefaults.buttonColors(
                    containerColor = Color.Red
                  )
                ) {
                    Text(i18n(S.reject))
                }
            }
        }
    }
}

@Composable
fun AutopayCard(
  autoPayEnabled: Boolean,
  maxPerRequest: String,
  maxPerDay: String,
  maxPerWeek: String,
  maxPerMonth: String,
  onAutoPayToggled: (Boolean) -> Unit,
  onMaxPerRequestChanged: (String) -> Unit,
  onMaxPerDayChanged: (String) -> Unit,
  onMaxPerWeekChanged: (String) -> Unit,
  onMaxPerMonthChanged: (String) -> Unit,
) {
    Card(
      modifier = Modifier.fillMaxWidth()
        .padding(vertical = 6.dp),
      shape = MaterialTheme.shapes.medium,
      colors = CardDefaults.cardColors(
        containerColor = Color.White,
      ),
      elevation = CardDefaults.cardElevation(
        defaultElevation = 4.dp
      )
    ) {
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Text(
              text = i18n(S.AutoPay),
              style = MaterialTheme.typography.titleMedium,
              modifier = Modifier.padding(bottom = 4.dp)
            )

            Text(
              text = "If Autopay manual approval is disabled, all payments ask for user authorisation and the limits below are ignored",
              style = MaterialTheme.typography.bodyMedium
            )

            WallySwitchRow(autoPayEnabled, S.ManualApproval) {
                onAutoPayToggled(it)
            }

            WallyNumericInputFieldBalance(
              mod = Modifier.testTag("amountToSendInput"),
              amount = maxPerRequest,
              label = "NEX",
              placeholder = i18n(S.enterNEXAmount),
              isEnabled = autoPayEnabled,
              hasIosDoneButton = true,
              hasButtonRow = false,
            ) { onMaxPerRequestChanged(it) }
            Text(
              text = "Max per request",
              style = MaterialTheme.typography.labelMedium,
              color = if (!autoPayEnabled) Color.Gray else Color.Unspecified
            )

            Spacer(Modifier.height(8.dp))
            WallyNumericInputFieldBalance(
              mod = Modifier.testTag("amountToSendInput"),
              amount = maxPerDay,
              label = "NEX",
              placeholder = i18n(S.enterNEXAmount),
              isEnabled = autoPayEnabled,
              hasIosDoneButton = true,
              hasButtonRow = false,
            ) { onMaxPerDayChanged(it) }
            Text(
              text = "Max per day",
              style = MaterialTheme.typography.labelMedium,
              color = if (!autoPayEnabled) Color.Gray else Color.Unspecified
            )

            Spacer(Modifier.height(8.dp))
            WallyNumericInputFieldBalance(
              mod = Modifier.testTag("amountToSendInput"),
              amount = maxPerWeek,
              label = "NEX",
              placeholder = i18n(S.enterNEXAmount),
              isEnabled = autoPayEnabled,
              hasIosDoneButton = true,
              hasButtonRow = false,
            ) { onMaxPerWeekChanged(it) }
            Text(
              text = "Max per week",
              style = MaterialTheme.typography.labelMedium,
              color = if (!autoPayEnabled) Color.Gray else Color.Unspecified
            )

            Spacer(Modifier.height(8.dp))
            WallyNumericInputFieldBalance(
              mod = Modifier.testTag("amountToSendInput"),
              amount = maxPerMonth,
              label = "NEX",
              placeholder = i18n(S.enterNEXAmount),
              isEnabled = autoPayEnabled,
              hasIosDoneButton = true,
              hasButtonRow = false,
            ) { onMaxPerMonthChanged(it) }
            Text(
              text = "Max per month",
              style = MaterialTheme.typography.labelMedium,
              color = if (!autoPayEnabled) Color.Gray else Color.Unspecified
            )
        }
    }
}