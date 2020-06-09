// Copyright (c) 2019 Andrew Stone Consulting (qq9wwnuw4eukyh5g34ckg5vk4aaxnvr04vkspyv850)
// Distributed under the MIT software license, see the accompanying file COPYING or http://www.opensource.org/licenses/mit-license.php.
package info.bitcoinunlimited.www.wally

import android.content.Intent
import android.os.Bundle
import android.view.View
import com.google.android.material.bottomnavigation.BottomNavigationView
import androidx.appcompat.app.AppCompatActivity
import android.widget.TextView
import bitcoinunlimited.libbitcoincash.*
import kotlinx.android.synthetic.main.activity_identity_op.*
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.io.FileNotFoundException
import java.io.IOException
import java.lang.Exception
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.logging.Logger

private val LogIt = Logger.getLogger("bitcoinunlimited.IdentityOp")

val IDENTITY_URI_SCHEME = "bchidentity"

open class IdentityOpException(msg: String, shortMsg: String? = null, severity: ErrorSeverity = ErrorSeverity.Abnormal) : BUException(msg, shortMsg, severity)

class IdentityOpActivity : CommonActivity()
{
    override var navActivityId = -1

    var displayedLoginRequest: URL? = null

    override fun onCreate(savedInstanceState: Bundle?)
    {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_identity_op)

        if (intent.scheme != null)  // its null if normal app startup
        {
            checkIntentNewDomain(intent)
        }
    }

    override fun onStart()
    {
        super.onStart()
    }

    override fun onResume()
    {
        super.onResume()
        LogIt.info("Identity Operation")
        // Process the intent that caused this activity to resume
        if (intent.scheme != null)  // its null if normal app startup
        {
            handleNewIntent(intent)
        }
    }

    /** Check whether this domain has ever been seen before, and if it hasn't pop up the new domain configuration activity */
    fun checkIntentNewDomain(receivedIntent: Intent)
    {
        val iuri = receivedIntent.toUri(0).toUrl()  // URI_ANDROID_APP_SCHEME | URI_INTENT_SCHEME

        if (receivedIntent.scheme == IDENTITY_URI_SCHEME)
        {
            val host = iuri.getHost()
            // val path = iuri.getPath()

            val context = this

            runBlocking {
                // Run blocking so the IdentityOp activity does not momentarily appear
                val wallet = (application as WallyApp).primaryWallet
                val idData = wallet.lookupIdentityDomain(host) // + path)
                if (idData == null)
                {
                    var intent = Intent(context, DomainIdentitySettings::class.java)
                    intent.putExtra("domainName", host) // + path)
                    intent.putExtra("title", getString(R.string.newDomainRequestingIdentity))
                    startActivity(intent)
                }
            }
        }

    }

    //? A new intent to pay someone could come from either startup (onResume) or just on it own (onNewIntent) so create a single function to deal with both
    fun handleNewIntent(receivedIntent: Intent)
    {
        val iuri = receivedIntent.toUri(0).toUrl()  // URI_ANDROID_APP_SCHEME | URI_INTENT_SCHEME
        LogIt.info("Identity OP new Intent: " + iuri)

        // Received bogus intent, or a repeat one that was already cleared
        if (receivedIntent.getStringExtra("repeat") == "true")
        {
            clearIntentAndFinish()
            return
        }

        try
        {
            if (receivedIntent.scheme == IDENTITY_URI_SCHEME)
            {
                val host = iuri.getHost()
                val path = iuri.getPath()
                val attribs = iuri.queryMap()
                // Received bogus intent, or a repeat one that was already cleared
                if (attribs["op"] != "login")
                {
                    clearIntentAndFinish()
                    return
                }

                LogIt.info("host: " + host + " path: " + path)

                ProvideLoginInfoText.visibility = View.VISIBLE
                displayLoginRecipient.visibility = View.VISIBLE
                provideIdentityNoButton.visibility = View.VISIBLE
                provideIdentityYesButton.visibility = View.VISIBLE
                displayedLoginRequest = iuri
                displayLoginRecipient.text = host // + " (" + path + ")"

                val context = this

                GlobalScope.launch {
                    val wallet = (application as WallyApp).primaryWallet
                    val idData = wallet.lookupIdentityDomain(host + path)
                    if (idData == null)
                    {
                        var intent = Intent(context, DomainIdentitySettings::class.java)
                        intent.putExtra("domainName", host + path)
                        intent.putExtra("title", getString(R.string.newDomainRequestingIdentity))
                        //startActivity(intent)
                    }
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

    @Suppress("UNUSED_PARAMETER")
    fun onProvideIdentity(v: View)
    {
        val iuri = displayedLoginRequest
        GlobalScope.launch {
            if (iuri != null)
            {
                val host = iuri.getHost()
                val port = iuri.getPort()
                val path = iuri.getPath()
                val attribs = iuri.queryMap()
                val challenge = attribs["chal"]
                val cookie = attribs["cookie"]
                val op = attribs["op"]

                val portStr = if ((port > 0)&&(port != 80)&&(port !=443)) ":" + port.toString() else ""
                val chalToSign = host + portStr + "_bchidentity_" + op + "_" + challenge
                LogIt.info("challenge: " + chalToSign + " cookie: " + cookie)

                if (challenge == null) // intent was previously cleared by someone throw IdentityException("challenge string was not provided", "no challenge")
                {
                    finish()
                    return@launch
                }

                val wallet = (application as WallyApp).primaryWallet

                val identityDest: PayDestination = wallet.destinationFor(host + path)

                // This is a coding bug in the wallet
                val secret = identityDest.secret ?: throw IdentityException("Wallet failed to provide an identity with a secret", "bad wallet", ErrorSeverity.Severe)
                val address = identityDest.address ?: throw IdentityException("Wallet failed to provide an identity with an address", "bad wallet", ErrorSeverity.Severe)

                val sig = Wallet.signMessage(chalToSign.toByteArray(), secret)

                if (sig.size == 0) throw IdentityException("Wallet failed to provide a signable identity", "bad wallet", ErrorSeverity.Severe)

                val sigStr = Codec.encode64(sig)
                LogIt.info("signature is: " + sigStr)

                // TODO use https for addtl security
                val loginReq = "http://" + host + portStr + path + "?op=login&addr=" + address.toString() + "&sig=" + URLEncoder.encode(sigStr, "UTF-8") + "&cookie=" + URLEncoder.encode(
                    cookie,
                    "UTF-8")

                LogIt.info("login reply: " + loginReq)
                try
                {
                    val req: HttpURLConnection = URL(loginReq).openConnection() as HttpURLConnection
                    val resp = req.inputStream.bufferedReader().readText()
                    LogIt.info("response code:" + req.responseCode.toString() + " response: " + resp)
                    if ((req.responseCode >= 200) and (req.responseCode < 300))
                        displayNotice(resp, { clearIntentAndFinish() }, 1000)
                    else
                        displayError(resp, { clearIntentAndFinish() })
                }
                catch (e: IOException)
                {
                    displayError(i18n(R.string.connectionAborted), { clearIntentAndFinish() })
                }
                catch (e: FileNotFoundException)
                {
                    displayError(i18n(R.string.badLink), { clearIntentAndFinish() })
                }
                catch (e: java.net.ConnectException)
                {
                    displayError(i18n(R.string.connectionException), { clearIntentAndFinish() })
                }
            }
            else
            {
                // TODO shouldn't be visible if variable isn't set.
            }
        }


        ProvideLoginInfoText.visibility = View.INVISIBLE;
        displayLoginRecipient.visibility = View.INVISIBLE;
        provideIdentityNoButton.visibility = View.INVISIBLE;
        provideIdentityYesButton.visibility = View.INVISIBLE;
    }

    @Suppress("UNUSED_PARAMETER")
    fun onDontProvideIdentity(v: View)
    {
        ProvideLoginInfoText.visibility = View.INVISIBLE;
        displayLoginRecipient.visibility = View.INVISIBLE;
        provideIdentityNoButton.visibility = View.INVISIBLE;
        provideIdentityYesButton.visibility = View.INVISIBLE;
        clearIntentAndFinish()
    }

    fun clearIntentAndFinish()
    {
        intent.putExtra("repeat", "true")
        finish()
    }

};

