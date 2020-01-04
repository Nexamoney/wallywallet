// Copyright (c) 2019 Andrew Stone Consulting (qq9wwnuw4eukyh5g34ckg5vk4aaxnvr04vkspyv850)
// Distributed under the MIT software license, see the accompanying file COPYING or http://www.opensource.org/licenses/mit-license.php.
package bitcoinunlimited.libbitcoincash

import android.content.Context
import android.content.SharedPreferences
import android.content.res.Resources
import android.text.Editable
import android.text.TextWatcher
import android.widget.CompoundButton
import android.widget.EditText
import android.widget.TextView
import info.bitcoinunlimited.www.wally.BCH_PREFER_NODE_SWITCH
import info.bitcoinunlimited.www.wally.R
import kotlinx.android.synthetic.main.activity_settings.*
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import java.io.File
import java.util.logging.Logger


private val LogIt = Logger.getLogger("bitcoinunlimited.useful")

public var appResources: Resources? = null

// It is necessary to abstract the R.string. resource identifier because they don't exist on other platforms
val RinsufficentBalance = R.string.insufficentBalance
val RbadWalletImplementation = R.string.badWalletImplementation
val RwalletAndAddressIncompatible = R.string.chainIncompatibleWithAddress
val RnotSupported = R.string.notSupported
val Rexpired = R.string.expired
val RsendMoreThanBalance = R.string.sendMoreThanBalance
val RbadAddress = R.string.badAddress
val RblankAddress = R.string.blankAddress
val RblockNotForthcoming = R.string.blockNotForthcoming
val RheadersNotForthcoming = R.string.headersNotForthcoming
val RbadTransaction = R.string.badTransaction
val RfeeExceedsFlatMax = R.string.feeExceedsFlatMax
val RexcessiveFee = R.string.excessiveFee
val Rbip70NoAmount = R.string.badAmount
val RdeductedFeeLargerThanSendAmount = R.string.deductedFeeLargerThanSendAmount

var RwalletDisconnectedFromBlockchain = R.string.walletDisconnectedFromBlockchain

open class AssertException(why: String): BUException(why, "Assertion", ErrorSeverity.Abnormal)

/** Do whatever you pass within the user interface context, asynchronously */
fun laterUI(fn: suspend () -> Unit): Unit
{
    MainScope().launch {
        fn()
    }
}

/** execute the passed code block directly if not in the UI thread, otherwise defer it */
fun notInUI(fn: () -> Unit)
{
    val tname = Thread.currentThread().name
    if (tname == "main")
    {
        GlobalScope.launch { fn() }
    }
    else fn()
}

/** Connects a gui switch to a preference DB item.  To be called in onCreate */
fun SetupBooleanPreferenceGui(key: String, db: SharedPreferences, button: CompoundButton)
{
    button.setChecked(db.getBoolean(key, false))

    button.setOnCheckedChangeListener(object : CompoundButton.OnCheckedChangeListener
    {
        override fun onCheckedChanged(buttonView: CompoundButton?, isChecked: Boolean)
        {
            with (db.edit())
            {
                putBoolean(key, isChecked)
                commit()
            }
        }
    })
}

/** Connects a gui text entry field to a preference DB item.  To be called in onCreate */
fun SetupTextPreferenceGui(key: String, db: SharedPreferences, view: EditText)
{
    view.text.clear()
    view.text.append(db.getString(key, ""))

    view.addTextChangedListener(object : TextWatcher {
        override fun afterTextChanged(p: Editable?) {
            dbgAssertGuiThread()
            if (p == null) return
            val text = p.toString()
            with (db.edit())
            {
                putString(key, text)
                commit()
            }
        }

        override fun beforeTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {
        }

        override fun onTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {
        }
    })
}

class TextViewReactor<T>(public val gui: TextView):Reactor<T>()
{
    override fun change(obj: Reactive<T>)
    {
        laterUI {
            obj.access()?.let {
                gui.text = it.first.toString()
            }
        }
    }
}

fun RunningTheTests(): Boolean
{
    try
    {
        Class.forName("bitcoinunlimited.wally.AndroidTest")
        return true
    }
    catch (e: ClassNotFoundException)
    {
        return false
    }
}



fun dbgAssertGuiThread()
{
    val tname = Thread.currentThread().name
    if (tname != "main")
    {
        LogIt.warning("ASSERT GUI operations in thread " + tname)
        throw AssertException("Executing GUI operations in thread " + tname)
    }
}

fun dbgAssertNotGuiThread()
{
    val tname = Thread.currentThread().name
    if (tname == "main")
    {
        LogIt.warning("ASSERT blocking operations in GUI thread " + tname)
        throw AssertException("Executing blocking operations in GUI thread " + tname)
    }
}
// String translation and display

class PlatformContext(val context: Context)
{
    val filesDir: File?
    get() = context.filesDir
}

// Lookup strings in strings.xml
fun i18n(id: Int):String
{
    if (id == -1) return ""
    val s = appResources?.getString(id)
    if (s != null) return s

    LogIt.severe("Missing strings.xml translation for " + id.toString())
    return "STR" + id.toString()
}


fun EditText.afterTextChanged(afterTextChanged: (String) -> Unit) {
    this.addTextChangedListener(object: TextWatcher {
        override fun afterTextChanged(s: Editable?) {
            afterTextChanged.invoke(s.toString())
        }

        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) { }

        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { }
    })
}

fun EditText.validate(validator: (String) -> Boolean, message: String) {
    this.afterTextChanged {
        this.error = if (validator(it)) null else message
    }
    this.error = if (validator(this.text.toString())) null else message
}


fun GetLog(module:String): Logger
{
    return Logger.getLogger(module)
}
