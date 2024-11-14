package info.bitcoinunlimited.www.wally.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
//import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import info.bitcoinunlimited.www.wally.*
import info.bitcoinunlimited.www.wally.ui.theme.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.datetime.*
import org.nexa.libnexakotlin.*
import org.nexa.threads.*
/**
 * Address information to display in view
 */
data class AddressInfo(val address: PayAddress, val givenOut: Boolean, val amountHeld: Long, val totalReceived: Long, val firstRecv: Long, val lastRecv: Long, val assetTypesReceived:Long)

val addressInfoComparator = object:  Comparator<AddressInfo>
{

    override fun compare(a: AddressInfo, b: AddressInfo): Int
    {
        // First sort by what's in the addresses
        if ((a.amountHeld > 0)||(b.amountHeld > 0))
        {
            if (a.amountHeld > b.amountHeld) return -1
            if (b.amountHeld > a.amountHeld) return 1
            return a.address.toString().compareTo(b.address.toString())
        }
        // Next sort by the what used to be in the addresses
        if ((a.totalReceived > 0) || (b.totalReceived > 0))
        {
            if (a.totalReceived > b.totalReceived) return -1
            if (b.totalReceived > a.totalReceived) return 1
            return a.address.toString().compareTo(b.address.toString())
        }
        if ((a.assetTypesReceived > 0) || (b.assetTypesReceived > 0))
        {
            if (a.assetTypesReceived > b.assetTypesReceived) return -1
            if (b.assetTypesReceived > a.assetTypesReceived) return 1
            return a.address.toString().compareTo(b.address.toString())
        }
        // Finally in lexographical order of address
        return a.address.toString().compareTo(b.address.toString())
    }
}

private val addressHistoryInfo = MutableStateFlow<List<AddressInfo>?>(null)
private val addressHistoryAccount = MutableStateFlow<Account?>(null)
private val addressHistoryMutex = Mutex("addressHistory")
fun calcAddressHistoryInfo(acc : Account)
{
    addressHistoryMutex.lock {
        val addresses: MutableList<AddressInfo> = mutableListOf()
        for (a in acc.wallet.allAddresses)
        {
            val used = acc.wallet.isAddressGivenOut(a)
            val holding = acc.wallet.getBalanceIn(a)
            val totalReceived = acc.wallet.getBalanceIn(a, false)
            val os = a.lockingScript()

            var first = Long.MAX_VALUE
            var last = Long.MIN_VALUE
            var assetTypes = 0L
            acc.wallet.forEachTx {
                var amt = 0L
                var asset = false

                for (out in it.tx.outputs)
                {
                    val ungrouped = out.script.ungrouped()
                    if (os contentEquals ungrouped)
                    {
                        amt += out.amount
                        val gi = out.script.groupInfo(out.amount)
                        if (gi != null)
                        {
                            assetTypes += 1
                            asset = true
                        }
                        break
                    }
                }
                if ((amt > 0) || asset)
                {
                    if (first > it.date) first = it.date
                    if (last < it.date) last = it.date
                }
                false
            }

            addresses.add(AddressInfo(a, used, holding, totalReceived, first, last, assetTypes))
        }

        addresses.sortWith(addressInfoComparator)
        addressHistoryInfo.value = addresses
        addressHistoryAccount.value = acc
    }
}

/**
 * Address history for an account
 */
@OptIn(DelicateCoroutinesApi::class)
@Composable
fun AddressHistoryScreen(acc: Account, nav: ScreenNav)
{
    val timeZone = TimeZone.currentSystemDefault()

    // You cannot use a function call to trigger some action in compose since stuff can be randomly recomposed or cached and NOT recomposed
    // We also can't regenerate the address history list inline with recomposition because its too slow.
    // We don't need this view to be "live" WRT new transaction coming in.
    // So we choose to asynchronously calculate it whenever its null or when the passed account changes.
    // (and we erase it to null whenever we leave this screen)
    if (addressHistoryAccount.value != acc)
    {
        addressHistoryInfo.value = null
    }
    if ((addressHistoryInfo.value == null) || (addressHistoryAccount.value != acc)) laterJob {
          calcAddressHistoryInfo(acc)
      }
    nav.onDepart {
        addressHistoryInfo.value = null
    }

    fun onAddressCopied(address: String)
    {
        setTextClipboard(address)
        displayNotice(S.copiedToClipboard)
    }

    var inUsedSection = false
    var inUnusedSection = false
    val addresses = addressHistoryInfo.collectAsState().value
    if (addresses == null)
    {
        CenteredSectionText(S.Processing)
    }
    else
    {
        LazyColumn {
            addresses.forEachIndexed { idx, it ->
                // This section display code assumes that the address list is sorted as above
                if ((idx == 0) && (it.amountHeld > 0))
                {
                    item(key = "aa") {
                        CenteredSectionText(S.ActiveAddresses)
                        WallyHalfDivider()
                    }
                }
                else if ((it.amountHeld == 0L) && (it.totalReceived > 0) && (!inUsedSection))
                {
                    item(key = "ua") {
                        WallyDivider()
                        CenteredSectionText(S.UsedAddresses)
                        WallyHalfDivider()
                    }
                    inUsedSection = true
                }
                else if ((it.amountHeld == 0L) && (it.totalReceived == 0L) && (it.assetTypesReceived == 0L) && (!inUnusedSection))
                {
                    item(key = "unua") {
                        WallyDivider()
                        CenteredSectionText(S.UnusedAddresses)
                        WallyHalfDivider()
                    }
                    inUnusedSection = true
                }
                item(key = it.address.toString()) {
                    val color = if (idx % 2 == 1)
                    {
                        WallyRowAbkg1
                    }
                    else WallyRowAbkg2
                    val address = it.address.toString()

                    if (idx != 1)
                        Spacer(modifier = Modifier.height(6.dp))

                    Column(modifier = Modifier.fillMaxWidth().background(color).padding(1.dp).clickable {
                        onAddressCopied(it.address.toString())
                    }) {
                        val dest = acc.wallet.walletDestination(it.address)
                        val addrText = if (devMode && (dest != null)) "${dest.index}:$address" else address
                        FittedText(text = addrText, fontWeight = FontWeight.Bold, modifier = Modifier)

                        Column(modifier = Modifier.fillMaxWidth().background(color).padding(8.dp)) {

                            if ((it.amountHeld > 0) || (it.totalReceived > 0) || (it.assetTypesReceived > 0))
                            {
                                val assetsHeld = if (it.assetTypesReceived > 0) (" (" + (i18n(S.AssetTypes) % mapOf("assetTypes" to it.assetTypesReceived.toString())) + ")") else ""
                                val balance = i18n(S.balance) + " " + acc.cryptoFormat.format(acc.fromFinestUnit(it.amountHeld)) + assetsHeld
                                val totalReceived = i18n(S.totalReceived) + " " + acc.cryptoFormat.format(acc.fromFinestUnit(it.totalReceived))

                                if (devMode)
                                {
                                    if (dest != null)
                                    {
                                        // These are dev mode only, so english
                                        CenteredFittedText("Pubkey:" + (dest.pubkey?.toHex() ?: ""))
                                        //Text("Index: " + dest.index )
                                    }
                                }

                                if (it.firstRecv != Long.MIN_VALUE)
                                {
                                    Row {
                                        Column(Modifier.weight(1f)) {
                                            if (it.firstRecv == it.lastRecv)  // only one receive
                                            {
                                                val guiTxDate = try
                                                {
                                                    formatLocalEpochMilliseconds(it.firstRecv)
                                                }
                                                catch (e: IllegalArgumentException)  // happens if date is invalid
                                                {
                                                    i18n(S.unavailable)
                                                }
                                                catch (e: DateTimeArithmeticException)  // happens if date is invalid
                                                {
                                                    i18n(S.unavailable)
                                                }
                                                Text(i18n(S.FirstUse) % mapOf("date" to guiTxDate))
                                            }
                                            else
                                            {
                                                val guiTxDate = try
                                                {
                                                    formatLocalEpochMilliseconds(it.firstRecv)
                                                }
                                                catch (e: IllegalArgumentException)  // happens if date is invalid
                                                {
                                                    i18n(S.unavailable)
                                                }
                                                catch (e: DateTimeArithmeticException)  // happens if date is invalid
                                                {
                                                    i18n(S.unavailable)
                                                }

                                                val guiTxDateLast = try
                                                {
                                                    formatLocalEpochMilliseconds(it.lastRecv)
                                                }
                                                catch (e: IllegalArgumentException)  // happens if date is invalid
                                                {
                                                    i18n(S.unavailable)
                                                }
                                                catch (e: DateTimeArithmeticException)  // happens if date is invalid
                                                {
                                                    i18n(S.unavailable)
                                                }

                                                Text(i18n(S.FirstUse) % mapOf("date" to guiTxDate))
                                                Text(i18n(S.LastUse) % mapOf("date" to guiTxDateLast))
                                            }
                                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                                Text(totalReceived)
                                                if ((it.assetTypesReceived > 0) && (it.amountHeld == 0L)) // if amountHeld > 0 we put this info somewhere else
                                                {
                                                    Text(assetsHeld)
                                                    Spacer(Modifier.width(1.dp))
                                                }
                                            }
                                        }
                                        Column {
                                            val uri = it.address.blockchain.explorer("/address/${it.address.toString()}")
                                            if (uri != null)
                                            {
                                                //Spacer(Modifier.height(1.dp).weight(1f))
                                                WallyBoringButton({ openUrl(uri) }, modifier = Modifier.padding(0.dp, 0.dp, 10.dp, 0.dp)
                                                ) {
                                                    Icon(Icons.Default.ExitToApp, tint = colorConfirm, contentDescription = "view address activity")
                                                }
                                            }
                                        }
                                    }
                                    if (it.amountHeld > 0) Text(balance, fontWeight = FontWeight.Bold)
                                }
                                else
                                {
                                    assert(false)  // should never happen if some amount is held
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}