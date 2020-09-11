// Copyright (c) 2019 Andrew Stone Consulting (qq9wwnuw4eukyh5g34ckg5vk4aaxnvr04vkspyv850)
// Distributed under the MIT software license, see the accompanying file COPYING or http://www.opensource.org/licenses/mit-license.php.
package info.bitcoinunlimited.www.wally

import android.app.Activity
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.view.View
import android.widget.ActionMenuView
import com.google.android.material.bottomnavigation.BottomNavigationView
import androidx.appcompat.app.AppCompatActivity
import android.widget.TextView
import androidx.preference.PreferenceManager
import kotlinx.android.synthetic.main.activity_identity_op.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import java.io.FileNotFoundException
import java.io.IOException
import java.lang.Exception
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.logging.Logger
import bitcoinunlimited.libbitcoincash.*
import kotlinx.android.synthetic.main.activity_identity.*
import java.io.DataOutputStream

private val LogIt = Logger.getLogger("bitcoinunlimited.IdentityOp")

val IDENTITY_URI_SCHEME = "bchidentity"

open class IdentityOpException(msg: String, shortMsg: String? = null, severity: ErrorSeverity = ErrorSeverity.Abnormal) : BUException(msg, shortMsg, severity)

class IdentityOpActivity : CommonActivity()
{
    override var navActivityId = -1

    var displayedLoginRequest: URL? = null

    var query: Map<String, String>? = null
    val perms = mutableMapOf<String, Boolean>()
    val reqs = mutableMapOf<String, String>()

    var host: String? = null
    var triggeredNewDomain = false
    var account: Account? = null
    var pinTries = 0

    override fun onCreate(savedInstanceState: Bundle?)
    {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_identity_op)

        if (intent.scheme != null)  // its null if normal app startup
        {
            updatePermsFromIntent(intent)
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

    fun blankActivity()
    {
        displayLoginRecipient.visibility = View.INVISIBLE
        provideIdentityNoButton.visibility = View.INVISIBLE
        provideIdentityYesButton.visibility = View.INVISIBLE
        ProvideLoginInfoText.visibility = View.INVISIBLE
    }

    /** Check whether this domain has ever been seen before, and if it hasn't pop up the new domain configuration activity */
    fun checkIntentNewDomain(receivedIntent: Intent)
    {
        val iuri = receivedIntent.toUri(0).toUrl()  // URI_ANDROID_APP_SCHEME | URI_INTENT_SCHEME

        if (receivedIntent.scheme == IDENTITY_URI_SCHEME)
        {
            val h = iuri.getHost()
            host = h

            // val path = iuri.getPath()
            val attribs = iuri.queryMap()
            query = iuri.queryMap()

            val context = this

            if (true)
            {
                // Run blocking so the IdentityOp activity does not momentarily appear
                val act = account ?: try
                    {
                        (application as WallyApp).primaryAccount
                    }
                    catch (e: PrimaryWalletInvalidException)
                    {
                        displayError(R.string.pleaseWait)
                        null
                    }

                // If the primary account is locked do not proceed
                if (act?.locked ?: false)
                {
                    // TODO
                    //displayError(R.string.NoAccounts)
                    //finish()
                    return
                }

                val w = act?.wallet

                if (w != null)
                {
                    val idData = w.lookupIdentityDomain(h) // + path)
                    if (idData == null)
                    {
                        val queries = iuri.queryMap()
                        if (queries["op"]?.toLowerCase() != "reg")
                        {
                            blankActivity()
                            displayError(i18n(R.string.UnknownDomainRegisterFirst), { finish() })
                            return
                        }
                        if (triggeredNewDomain == false)  // I only want to drop into the new domain settings once
                        {
                            triggeredNewDomain = true
                            var intent = Intent(context, DomainIdentitySettings::class.java)
                            intent.putExtra("domainName", h) // + path)
                            intent.putExtra("title", getString(R.string.newDomainRequestingIdentity))
                            for ((k, v) in attribs)
                            {
                                LogIt.info(k + " => " + v)
                                intent.putExtra(k, v)
                                if (k in BCHidentityParams)
                                    reqs[k] = v  // Set the information requirements coming from this domain
                            }
                            // Since the wallet has no info about this domain, assume all of the info permissions are false (or null) so no xxxP keys need to be put into the intent.
                            startActivityForResult(intent, IDENTITY_SETTINGS_RESULT)
                        }
                    }
                    else
                    {
                        idData.getPerms(perms)
                        idData.getReqs(reqs)
                    }
                }
            }
        }

    }

    fun updatePermsFromIntent(intent: Intent)
    {
        for(k in BCHidentityParams)  // Update new perms
        {
            if (intent.hasExtra(k + "P"))
            {
                val r = intent.getBooleanExtra(k + "P", false)
                LogIt.info("updated " + k + " to " + r)
                perms[k] = r
            }
        }
    }

    /** this handles the result of the new domain request, since no other child activities are possible */
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?)
    {
         LogIt.info(sourceLoc() + " activity completed $requestCode $resultCode")

         // Handle my sub-activity results
         if (requestCode == IDENTITY_SETTINGS_RESULT)
         {
             // Load any changes the sub-activity may have made
             val h = host
             if (h != null)
             {
                 val idData = account?.wallet?.lookupIdentityDomain(h)
                 idData?.getPerms(perms)
                 idData?.getReqs(reqs)
             }

             if (resultCode == Activity.RESULT_OK)
             {
                 if (data != null)
                 {
                     val err = data.getStringExtra("error")
                     if (err != null) displayError(err)
                 }
             }
             else if (requestCode == Activity.RESULT_CANCELED)  // If the DomainIdentitySettings activity got cancelled, the user wants to cancel the whole thing
             {
                 clearIntentAndFinish(i18n(R.string.cancelled))
             }
         }

        super.onActivityResult(requestCode, resultCode, data)
    }

    //? A new intent to pay someone could come from either startup (onResume) or just on it own (onNewIntent) so create a single function to deal with both
    fun handleNewIntent(receivedIntent: Intent)
    {
        val iuri = receivedIntent.toUri(0).toUrl()  // URI_ANDROID_APP_SCHEME | URI_INTENT_SCHEME
        LogIt.info(sourceLoc() + " Identity OP new Intent: " + iuri.toString())

        // Received bogus intent, or a repeat one that was already cleared
        if (receivedIntent.getStringExtra("repeat") == "true")
        {
            clearIntentAndFinish()
            return
        }

        if (account == null)
            try
            {
                account = (application as WallyApp).primaryAccount
            }
            catch (e: PrimaryWalletInvalidException)
            {
            }
        val acc = account
        if (acc == null)
        {
            displayError(R.string.pleaseWait)
            return
        }

        if (acc.locked)
        {
            if (pinTries == 0)
            {
                val intent = Intent(this, UnlockActivity::class.java)
                pinTries += 1
                startActivity(intent)
                return
            }
            else
            {
                blankActivity()
                displayError(i18n(R.string.NoAccounts), { finish() })
                return
            }
        }

        host = iuri.getHost()
        // val path = iuri.getPath()
        displayLoginRecipient.visibility = View.VISIBLE
        provideIdentityNoButton.visibility = View.VISIBLE
        provideIdentityYesButton.visibility = View.VISIBLE
        displayedLoginRequest = iuri
        displayLoginRecipient.text = host // + " (" + path + ")"
        checkIntentNewDomain(receivedIntent)
    }

    // If "yes" button pressed, contact the remote server with your registration or identity information
    @Suppress("UNUSED_PARAMETER")
    fun onProvideIdentity(v: View)
    {
        val iuri = displayedLoginRequest
        launch {
            if (iuri != null)
            {
                host = iuri.getHost()
                val h = host!!
                val port = iuri.getPort()
                val path = iuri.getPath()
                val attribs = iuri.queryMap()
                val challenge = attribs["chal"]
                val cookie = attribs["cookie"]
                val op = attribs["op"]

                val portStr = if ((port > 0) && (port != 80) && (port != 443)) ":" + port.toString() else ""
                val chalToSign = h + portStr + "_bchidentity_" + op + "_" + challenge
                LogIt.info("challenge: " + chalToSign + " cookie: " + cookie)

                if (challenge == null) // intent was previously cleared by someone throw IdentityException("challenge string was not provided", "no challenge")
                {
                    finish()
                    return@launch
                }

                val act = account ?: try
                {
                    (application as WallyApp).primaryAccount
                }
                catch (e: PrimaryWalletInvalidException)
                {
                    displayError(R.string.pleaseWait)
                    return@launch
                }
                if (act.locked)
                {
                    displayError(R.string.NoAccounts)
                    return@launch
                }

                val wallet = act.wallet
                val identityDest: PayDestination = wallet.destinationFor(h + path)

                // This is a coding bug in the wallet
                val secret = identityDest.secret ?: throw IdentityException("Wallet failed to provide an identity with a secret", "bad wallet", ErrorSeverity.Severe)
                val address = identityDest.address ?: throw IdentityException("Wallet failed to provide an identity with an address", "bad wallet", ErrorSeverity.Severe)

                val sig = Wallet.signMessage(chalToSign.toByteArray(), secret)

                if (sig.size == 0) throw IdentityException("Wallet failed to provide a signable identity", "bad wallet", ErrorSeverity.Severe)

                val sigStr = Codec.encode64(sig)
                LogIt.info("signature is: " + sigStr)

                if (op == "login")
                {
                    // TODO use https for addtl security
                    val loginReq = "http://" + h + portStr + path + "?op=login&addr=" + address.toString() + "&sig=" + URLEncoder.encode(sigStr, "UTF-8") + "&cookie=" + URLEncoder.encode(
                        cookie, "UTF-8")

                    LogIt.info("login reply: " + loginReq)
                    try
                    {
                        val req: HttpURLConnection = URL(loginReq).openConnection() as HttpURLConnection
                        val resp = req.inputStream.bufferedReader().readText()
                        LogIt.info("login response code:" + req.responseCode.toString() + " response: " + resp)
                        if ((req.responseCode >= 200) and (req.responseCode < 250))
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
                else if (op == "reg")
                {
                    // TODO use https for addtl security
                    val loginReq = "http://" + h + portStr + path

                    val params = mutableMapOf<String,String>()
                    params["op"] = "reg"
                    params["addr"] = address.toString()
                    params["sig"] = sigStr
                    params["cookie"] = cookie.toString()

                    // Supply additional requested data
                    if (true)
                    {
                        val prefs: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(applicationContext)

                        val idData = wallet.lookupIdentityDomain(h)
                        if (idData != null)
                        {
                            val perms = mutableMapOf<String, Boolean>()
                            idData.getPerms(perms)
                            for (i in BCHidentityParams)
                            {
                                if (attribs.containsKey(i))
                                {
                                    val req = attribs[i]

                                    if (perms[i] == true) prefs.getString(i, null)?.let { params[i] = it }
                                    else
                                    {
                                        if (req=="m")  // mandatory, so this login won't work
                                        {
                                            displayError(i18n(R.string.connectionException))
                                            // Start DomainIdentitySettings dialog, instead of
                                            clearIntentAndFinish()
                                        }
                                    }
                                }
                            }
                        }
                    }

                    val jsonBody = StringBuilder("{")
                    var firstTime = true
                    for ((k,value) in params)
                    {
                        if (!firstTime) jsonBody.append(',')
                        else firstTime = false
                        jsonBody.append('"')
                        jsonBody.append(k)
                        jsonBody.append("""":"""")
                        jsonBody.append(value)
                        jsonBody.append('"')
                    }
                    jsonBody.append('}')

                    LogIt.info("registration reply: " + loginReq)
                    try
                    {
                        //val body = """[1,2,3]"""  // URLEncoder.encode("""[1,2,3]""","UTF-8")
                        val req: HttpURLConnection = URL(loginReq).openConnection() as HttpURLConnection
                        req.requestMethod = "POST"
                        req.setRequestProperty("Content-Type", "application/json")
                        //req.setRequestProperty("charset", "utf-8")
                        req.setRequestProperty("Accept", "*/*")
                        req.setRequestProperty("Content-Length", jsonBody.length.toString())
                        req.doOutput = true
                        req.useCaches = false
                        val os = DataOutputStream(req.outputStream)
                        //os.write(jsonBody.toByteArray())
                        os.writeBytes(jsonBody.toString())
                        os.flush()
                        os.close()
                        val resp = req.inputStream.bufferedReader().readText()
                        LogIt.info("reg response code:" + req.responseCode.toString() + " response: " + resp)
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

    fun clearIntentAndFinish(error: String? = null)
    {
        intent.putExtra("repeat", "true")
        if (error != null) intent.putExtra("error", error)
        setResult(Activity.RESULT_OK, intent)
        finish()
    }

};

