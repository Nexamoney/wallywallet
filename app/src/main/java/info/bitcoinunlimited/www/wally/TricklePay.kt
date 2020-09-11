// Copyright (c) 2019 Andrew Stone Consulting (qq9wwnuw4eukyh5g34ckg5vk4aaxnvr04vkspyv850)
// Distributed under the MIT software license, see the accompanying file COPYING or http://www.opensource.org/licenses/mit-license.php.
package info.bitcoinunlimited.www.wally

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.net.Uri.fromParts
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.View.GONE
import android.view.View.VISIBLE
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import androidx.core.net.toUri
import androidx.fragment.app.Fragment
import bitcoinunlimited.libbitcoincash.*
import com.google.android.material.bottomnavigation.BottomNavigationView
import kotlinx.android.synthetic.main.activity_trickle_pay.*
import kotlinx.android.synthetic.main.trickle_pay_reg.*
import java.lang.Exception
import java.net.URI
import java.net.URL
import java.util.logging.Logger

val TDPP_URI_SCHEME = "tdpp"

private val LogIt = Logger.getLogger("bu.TricklePay")

class TricklePayEmptyFragment : Fragment()
{

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View?
    {
        val ret = View(appContext?.context)
            // inflater.inflate(R.layout.welcomef1, container, false)
        return ret
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?)
    {
        super.onViewCreated(view, savedInstanceState)
    }
}



class TricklePayRegFragment : Fragment()
{
    var uri: Uri? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View?
    {
        val ret = inflater.inflate(R.layout.trickle_pay_reg, container, false)
        return ret
    }

    override fun onResume()
    {
        updateUI()
        super.onResume()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?)
    {
        super.onViewCreated(view, savedInstanceState)
        //view.findViewById<Button>(R.id.button_first).setOnClickListener {
        //    findNavController()?.navigate(R.id.action_Welcome1_to_Welcome2)
        //}
    }

    data class Data2Widgets(val amtParam:String, val descParam:String, val entry: EditText, val desc: TextView)

    fun populate(puri: Uri)
    {
        uri = puri
        updateUI()
    }

    fun updateUI()
    {
        val u:Uri = uri ?: return

        val topic = u.getQueryParameter("topic").let {
            if (it==null) ""
            else ":" + it
        }
        GuiTricklePayEntity.text = u.authority + topic

        val d2w = listOf(Data2Widgets("maxper","descper", GuiAutospendLimitEntry0, GuiAutospendLimitDescription0),
            Data2Widgets("maxday","descday", GuiAutospendLimitEntry1, GuiAutospendLimitDescription1),
            Data2Widgets("maxweek","descweek", GuiAutospendLimitEntry2, GuiAutospendLimitDescription2),
            Data2Widgets("maxmonth","descmonth", GuiAutospendLimitEntry3, GuiAutospendLimitDescription3)
        )

        for (d in d2w)
        {
            val amount: ULong? = u.getQueryParameter(d.amtParam).let {
                if (it == null) null
                else it.toULong()
            }
            val desc: String = u.getQueryParameter(d.descParam) ?: ""
            d.entry.text.clear()
            if (amount != null)
            {
                d.entry.text.append(amount.toString())
            }
            d.desc.text = desc
        }


    }
}

fun ConstructTricklePayRequest(entity: String, topic: String?, operation: String, signWith: PayDestination, uoa: String?, maxPer : ULong?, maxDay : ULong?, maxWeek : ULong?, maxMonth: ULong?): Uri
{
    val uri = Uri.Builder()

    uri.scheme("tdpp")
    uri.path("//" + entity + "/" + operation)

    // These are coding bugs in the app; you should not have provided a payment destination that does not have a signature
    val secret = signWith.secret ?: throw IdentityException("Wallet failed to provide an identity with a secret", "bad wallet", ErrorSeverity.Severe)
    val address = signWith.address ?: throw IdentityException("Wallet failed to provide an identity with an address", "bad wallet", ErrorSeverity.Severe)

    // NOTE, append query parameters in sorted order so that the signature string is correct!
    uri.appendQueryParameter("addr", address.toString() )
    if (maxDay != null) uri.appendQueryParameter("maxday", maxDay.toString())
    if (maxMonth != null) uri.appendQueryParameter("maxmonth", maxMonth.toString())
    if (maxPer != null) uri.appendQueryParameter("maxper", maxPer.toString())
    if (maxWeek != null) uri.appendQueryParameter("maxweek", maxWeek.toString())

    if (topic != null) uri.appendQueryParameter("topic", topic)
    if (uoa != null) uri.appendQueryParameter("uoa", uoa)

    val signThis = uri.build().toString()
    LogIt.info(signThis)
    val sig = Wallet.signMessage(signThis.toByteArray(), secret)
    if (sig.size == 0) throw IdentityException("Wallet failed to provide a signable identity", "bad wallet", ErrorSeverity.Severe)
    val sigStr = Codec.encode64(sig)
    uri.appendQueryParameter("sig",sigStr)
    return uri.build()
}

fun VerifyTdppSignature(uri: Uri):Boolean?
{
    val addressStr = uri.getQueryParameter("addr")
    if (addressStr == null) return null
    val sig = uri.getQueryParameter("sig")
    if (sig == null) return null

    // recast the URI into one with the parameters in the proper order, and no sig
    val suri = Uri.Builder()
    suri.scheme(uri.scheme)
    suri.authority(uri.authority)
    suri.path(uri.path)
    val orderedParams = uri.queryParameterNames.toList().sorted()
    for (p in orderedParams)
    {
        if (p=="sig") continue
        suri.appendQueryParameter(p, uri.getQueryParameter(p))
    }

    val verifyThis = suri.build().toString()

    val pa = PayAddress(addressStr)

    LogIt.info("verification for: " + verifyThis + " Address: " + addressStr)
    LogIt.info("Message hex: " + verifyThis.toByteArray().toHex())
    LogIt.info("Raw Address: " + pa.data.toHex())
    val sigBytes = Codec.decode64(sig)
    LogIt.info("Sig: " + sigBytes.toHex())
    val result = Wallet.verifyMessage(verifyThis.toByteArray(), pa.data, sigBytes)
    if (result == null || result.size == 0)
    {
        LogIt.info("verification failed for: " + verifyThis + " Address: " + addressStr)
        Wallet.verifyMessage(verifyThis.toByteArray(), pa.data, sigBytes)
        return false
    }
    LogIt.info("verification good for: " + verifyThis + " Address: " + addressStr)
    return true
}

fun generateAndLogSomeTricklePayRequests(application: WallyApp)
{
    val act = application.primaryAccount
    val wallet = act.wallet
    val identityDest: PayDestination = wallet.destinationFor(Bip44Wallet.COMMON_IDENTITY_SEED)

    var uri = ConstructTricklePayRequest("testapp", "testtopic", "reg", identityDest, "BCH", 1000000UL, null, null, 100000000UL)
    LogIt.info(uri.toString())
    if (VerifyTdppSignature(uri) == true)
    {
        LogIt.info("Sig Verified")
    }
    else
    {
        VerifyTdppSignature(uri)
    }
    var uri2 = Uri.parse(uri.toString())
    if (VerifyTdppSignature(uri2) == true)
    {
        LogIt.info("Sig Verified")
    }
    else
    {
        VerifyTdppSignature(uri)
    }


}


class TricklePayActivity : CommonActivity()
{
    override var navActivityId = R.id.navigation_trickle_pay

    override fun onCreate(savedInstanceState: Bundle?)
    {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_trickle_pay)
    }

    override fun onResume()
    {
        super.onResume()
        generateAndLogSomeTricklePayRequests(getApplication() as WallyApp)
        // Process the intent that caused this activity to resume
        if (intent.scheme != null)  // its null if normal app startup
        {
            handleNewIntent(intent)
        }
        else displayFragment(GuiTricklePayMain)
    }

    fun displayFragment(frag: Fragment)
    {
        val fragments = listOf(GuiTricklePayMain, GuiTricklePayReg)

        for (f in fragments)
        {
            if (f == frag) f.view?.visibility = VISIBLE
            else f.view?.visibility = GONE
        }
    }

    fun handleRegistration(uri: Uri)
    {
        val host = uri.getHost()
        val address = uri.getQueryParameter("addr")
        if (address == null)
        {
            return displayError(R.string.BadLink)
        }

        if (VerifyTdppSignature(uri) == true)
        {
            //var intent = Intent(this, TricklePayRegistrationActivity::class.java)
            //startActivityForResult(intent, TRICKLE_PAY_REG_OP_RESULT)
            (GuiTricklePayReg as TricklePayRegFragment).populate(uri)
        }
        else
        {
            displayFragment(GuiTricklePayMain)
            displayError(R.string.badSignature)
            return
        }

        displayFragment(GuiTricklePayReg)
    }

    //? Handle tdpp intents
    fun handleNewIntent(receivedIntent: Intent)
    {
        val iuri: Uri = receivedIntent.toUri(0).toUri()  // URI_ANDROID_APP_SCHEME | URI_INTENT_SCHEME
        LogIt.info("Identity new Intent: " + iuri)
        try
        {
            if (receivedIntent.scheme == TDPP_URI_SCHEME)
            {
                val host = iuri.getHost()
                val path = iuri.getPath()
                LogIt.info("Trickle Pay Intent host=${host} path=${path}")
                if (path == "/reg")  // Handle registration
                {
                    handleRegistration(iuri)
                }
                else if (path == "/sendto")
                {
                    LogIt.info("address autopay")
                }
                else if (path == "/tx")
                {
                    LogIt.info("tx autopay")
                }
                else if (path == "/jsonpay")
                {
                    LogIt.info("json autopay")
                }
                else
                {
                    displayError(i18n(R.string.unknownOperation) % mapOf("op" to path));
                }
            }
            else  // This should never happen because the AndroidManifest.xml Intent filter should match the URIs that we handle
            {
                displayError("bad link " + receivedIntent.scheme)
            }
        }
        catch (e: Exception)
        {
            displayException(e)
        }
    }

}
