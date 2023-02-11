package info.bitcoinunlimited.www.wally

import android.content.Context
import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import bitcoinunlimited.libbitcoincash.ChainSelector
import bitcoinunlimited.libbitcoincash.dbgAssertGuiThread
import bitcoinunlimited.libbitcoincash.handleThreadException
import info.bitcoinunlimited.www.wally.databinding.AccountListItemBinding
import java.util.logging.Logger

private val LogIt = Logger.getLogger("BU.wally.accountlist")

class AccountListBinder(val ui: AccountListItemBinding, val guiList: GuiAccountList): GuiListItemBinder<Account>(ui.root)
{
    override fun populate()
    {
        if (devMode) ui.Info.visibility = View.VISIBLE
        else ui.Info.visibility = View.GONE
        val d = data
        if (d != null)
        {
            if (ui.balanceUnconfirmedValue.text == "") ui.balanceUnconfirmedValue.visibility = View.GONE
            d.setUI(ui, guiList, ui.accountIcon, ui.balanceTickerText, ui.balanceValue, ui.balanceUnconfirmedValue, ui.Info)

            ui.balanceUnits.text = d.currencyCode
            if (d.wallet.chainSelector == ChainSelector.NEXA) ui.accountIcon.setImageResource(R.drawable.nexa_icon)
            if (d.wallet.chainSelector == ChainSelector.NEXATESTNET) ui.accountIcon.setImageResource(R.drawable.nexatest_icon)
            if (d.wallet.chainSelector == ChainSelector.NEXAREGTEST) ui.accountIcon.setImageResource(R.drawable.nexareg_icon)
            if (d.wallet.chainSelector == ChainSelector.BCH) ui.accountIcon.setImageResource(R.drawable.bitcoin_cash_token)
            ui.accountIcon.visibility = View.VISIBLE
            d.onWalletChange(true)
        }
        else  // Clear this out just in case it is recycled as one of the blanks at the bottom
        {
            ui.balanceUnconfirmedValue.text == ""
            ui.Info.text = ""
            ui.accountIcon.visibility = View.INVISIBLE
            ui.lockIcon.visibility = View.INVISIBLE
            ui.balanceTickerText.text = ""
            ui.balanceValue.text = ""
            ui.balanceUnits.text = ""
        }

        ui.lockIcon.setOnClickListener({toggleLock() })

        ui.accountIcon.setOnClickListener({ launchAccountDetails() })
        ui.balanceTicker.setOnClickListener({ launchAccountDetails() })
        ui.balanceTickerText.setOnClickListener({ launchAccountDetails() })

        ui.balanceValue.setOnClickListener({ focusAccount() })

    }

    fun toggleLock()
    {
        val d = data
        if (d != null)
        {
            if (d.lockable)
            {
                if (d.locked)
                {
                    val intent = Intent(guiList.activity, UnlockActivity::class.java)
                    guiList.activity.startActivity(intent)
                }
                else
                {
                    d.pinEntered = false
                    d.onWalletChange(true)
                }
            }
        }
    }
    override fun onClick(v: View)
    {
        guiList.onItemClicked(this)
    }

    fun focusAccount()
    {
        try
        {
            dbgAssertGuiThread()
            val account = data
            if (account == null) return

            // ui.sendAccount.setSelection(account.name)
            guiList.activity.setFocusedAccount(account)

        } catch (e: Exception)
        {
            LogIt.warning("Exception clicking on balance: " + e.toString())
            handleThreadException(e)
        }
    }
    fun launchAccountDetails()
    {
        // dbgAssertGuiThread()
        val d = data
        if (d != null)
        {
            val intent = Intent(guiList.activity, AccountDetailsActivity::class.java)
            wallyApp?.let { a ->
                a.accounts[d.name]?.let { a.focusedAccount = it }
            }
            intent.putExtra("WalletName", d.name)
            guiList.activity.startActivity(intent)
        }
    }

}


/* This gives a Map a list interface, sorted by the passed comparator */
class ListifyMap<K, E>(val origmap: Map<K, E>, var filterPredicate: (Map.Entry<K, E>) -> Boolean, var comparator: Comparator<K>): List<E>
{
    protected var map = origmap.filter(filterPredicate)
    protected var order: List<K> = map.keys.sortedWith(comparator)
    override val size: Int
        get() = map.size

    override fun get(index: Int): E
    {
        return map[order[index]]!!
    }

    override fun isEmpty(): Boolean = map.isEmpty()

    fun changed()
    {
        order = map.keys.sortedWith(comparator)
    }

    class LmIterator<K, E>(val lm: ListifyMap<K, E>):Iterator<E>
    {
        var pos = 0
        override fun hasNext(): Boolean
        {
            return (pos < lm.size)
        }

        override fun next(): E
        {
            val ret = lm.get(pos)
            pos += 1
            return ret
        }
    }

    override fun iterator(): Iterator<E>
    {
        return LmIterator(this)
    }

    override fun listIterator(): ListIterator<E>
    {
        TODO("Not yet implemented")
    }

    override fun listIterator(index: Int): ListIterator<E>
    {
        TODO("Not yet implemented")
    }

    override fun subList(fromIndex: Int, toIndex: Int): List<E>
    {
        TODO("Not yet implemented")
    }

    override fun lastIndexOf(element: E): Int
    {
        TODO("Not yet implemented")
    }

    override fun indexOf(element: E): Int
    {
        TODO("Not yet implemented")
    }

    override fun containsAll(elements: Collection<E>): Boolean
    {
        TODO("Not yet implemented")
    }

    override fun contains(element: E): Boolean
    {
        for (i in map)
        {
            if (i.value == element) return true
        }
        return false
    }

}


class GuiAccountList(val activity: MainActivity)
{
    private lateinit var linearLayoutManager: LinearLayoutManager
    public lateinit var adapter: GuiList<Account, AccountListBinder>
    var list:List<Account> = listOf()

    fun changed()
    {
        // if (list is ListifyMap<String, Account>) (list as ListifyMap<String, Account>).changed()
    }

    open fun onItemClicked(holder: AccountListBinder)
    {
        activity.setFocusedAccount(holder.data)
    }



    fun inflate(context: Context, uiElem: RecyclerView, accountList: List<Account> )
    {
        list = accountList
        linearLayoutManager = LinearLayoutManager(context)
        adapter = GuiList(uiElem, accountList, context, { vg ->
            val ui = AccountListItemBinding.inflate(LayoutInflater.from(vg.context), vg, false)
            AccountListBinder(ui, this)
        })
        adapter.emptyBottomLines = 2
        adapter.rowBackgroundColors = WallyRowColors
        uiElem.layoutManager = linearLayoutManager
    }
}