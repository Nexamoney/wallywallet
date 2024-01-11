package info.bitcoinunlimited.www.wally.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import info.bitcoinunlimited.www.wally.*
import info.bitcoinunlimited.www.wally.ui.theme.*
import info.bitcoinunlimited.www.wally.ui.views.ResImageView
import kotlinx.coroutines.*
import kotlinx.datetime.*
import org.nexa.libnexakotlin.*

/**
 * Address information to display in view
 */
data class AddressInfo(val address: PayAddress, val givenOut: Boolean, val amountHeld: Long, val totalReceived: Long, val firstRecv: Long, val lastRecv: Long)

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
        // Finally in lexographical order of address
        return a.address.toString().compareTo(b.address.toString())
    }
}

/**
 * Address history for an account
 */
@OptIn(DelicateCoroutinesApi::class)
@Composable
fun AddressHistoryScreen(acc: Account, nav: ScreenNav)
{
    val addresses: MutableState<MutableList<AddressInfo>> = remember { mutableStateOf(mutableListOf()) }
    val timeZone = TimeZone.currentSystemDefault()
    var displayCopiedNotice by remember { mutableStateOf(false) }

    /**
     * Populates all addresses for one account with used, holding and received balance.
     */
    fun fillAddressList()
    {
        addresses.value.clear()
        for (a in acc.wallet.allAddresses)
        {
            val used = acc.wallet.isAddressGivenOut(a)
            val holding = acc.wallet.getBalanceIn(a)
            val totalReceived = acc.wallet.getBalanceIn(a, false)

            val os = a.outputScript()
            var first = Long.MAX_VALUE
            var last = Long.MIN_VALUE
            acc.wallet.forEachTx {
                var amt = 0L
                for (out in it.tx.outputs)
                {
                    if (os contentEquals out.script)
                    {
                        amt += out.amount
                        break
                    }
                }
                if (amt > 0)
                {
                    if (first > it.date) first = it.date
                    if (last < it.date) last = it.date
                }
                false
            }

            if (used)
                addresses.value.add(AddressInfo(a, used, holding, totalReceived, first, last))
        }

        addresses.value.sortWith(addressInfoComparator)
    }

    fillAddressList()

    fun onAddressCopied(address: String)
    {
        setTextClipboard(address)
        displayCopiedNotice = true
        GlobalScope.launch(Dispatchers.IO + exceptionHandler) {
            delay(NORMAL_NOTICE_DISPLAY_TIME)  // Delay of 5 seconds
            withContext(Dispatchers.Default + exceptionHandler) {
                displayCopiedNotice = false
            }
        }
    }

    LazyColumn {
            addresses.value.forEachIndexed {idx, it ->
                item(key=it.address.toString()) {
                    val color = if (idx % 2 == 1) { WallyRowAbkg1 } else WallyRowAbkg2
                    val address = it.address.toString()

                    if(idx != 1)
                        Spacer(modifier = Modifier.height(6.dp))

                    Column (modifier = Modifier.fillMaxWidth().background(color).padding(4.dp).clickable {
                        onAddressCopied(it.address.toString())
                    }) {
                        Text(
                          text = address,
                          fontWeight = FontWeight.Bold,
                          fontSize = 13.sp,
                          modifier = Modifier.padding(4.dp)
                        )

                        if ((it.amountHeld > 0)||(it.totalReceived > 0))
                        {
                            val balance = i18n(S.balance) + " " + acc.cryptoFormat.format(acc.fromFinestUnit(it.amountHeld))
                            val totalReceived = i18n(S.totalReceived) + " " + acc.cryptoFormat.format(acc.fromFinestUnit(it.totalReceived))

                            if (it.firstRecv != Long.MIN_VALUE)
                            {
                                if (it.firstRecv == it.lastRecv)  // only one receive
                                {
                                    val firstRecv = Instant.fromEpochMilliseconds(it.firstRecv).toLocalDateTime(timeZone)
                                    val guiTxDate = formatLocalDateTime(firstRecv)
                                    Text(guiTxDate)
                                }
                                else
                                {
                                    val firstRecv = Instant.fromEpochMilliseconds(it.firstRecv).toLocalDateTime(timeZone)
                                    val guiTxDate = formatLocalDateTime(firstRecv)
                                    val lastRecv = Instant.fromEpochMilliseconds(it.lastRecv).toLocalDateTime(timeZone)
                                    val guiTxDateLast = formatLocalDateTime(lastRecv)
                                    Text(guiTxDate)
                                    Text(guiTxDateLast)
                                }
                            }
                            else
                            {
                                assert(false)  // should never happen if some amount is held
                            }

                            Row {
                                Column(Modifier.weight(1f)) { // This makes the Column take up all available space
                                    Text(balance)
                                    Text(totalReceived)
                                }
                                ResImageView("icons/receivearrow.xml", Modifier.width(40.dp))
                            }
                        }
                    }
                }
            }
        }
}