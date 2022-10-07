// Copyright (c) 2019 Andrew Stone Consulting (qq9wwnuw4eukyh5g34ckg5vk4aaxnvr04vkspyv850)
// Distributed under the MIT software license, see the accompanying file COPYING or http://www.opensource.org/licenses/mit-license.php.
package info.bitcoinunlimited.www.wally

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri.decode
import android.os.Bundle
import android.view.View
import androidx.preference.PreferenceManager
import kotlinx.android.synthetic.main.activity_identity_op.*
import java.io.FileNotFoundException
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.logging.Logger
import bitcoinunlimited.libbitcoincash.*
import java.io.DataOutputStream
import java.net.URLDecoder

private val LogIt = Logger.getLogger("BU.wally.IdentityOp")

val IDENTITY_URI_SCHEME = "nexid"

open class IdentityOpException(msg: String, shortMsg: String? = null, severity: ErrorSeverity = ErrorSeverity.Abnormal) : BUException(msg, shortMsg, severity)

class IdentityOpActivity : CommonNavActivity()
{
    private val HTTP_REQ_TIMEOUT_MS: Int = 7000
    override var navActivityId = -1

    var displayedLoginRequest: URL? = null

    var query: Map<String, String>? = null
    val perms = mutableMapOf<String, Boolean>()
    val reqs = mutableMapOf<String, String>()

    var host: String? = null
    var triggeredNewDomain = false
    var account: Account? = null
    var pinTries = 0
    var msgToSign:ByteArray? = null

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
        else if (displayedLoginRequest == null) // Huh?  This activity MUST have an intent describing what to ask the user
        {
            finish()
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
                } catch (e: PrimaryWalletInvalidException)
                {
                    //displayError(R.string.pleaseWait)
                    val primName:String = chainToURI[PRIMARY_CRYPTO] ?: ""
                    clearIntentAndFinish(i18n(R.string.primaryAccountRequired) % mapOf("primCurrency" to primName), i18n(R.string.primaryAccountRequiredDetails))
                    return
                }

                // If the primary account is locked do not proceed
                if (act.locked)
                {
                    // TODO
                    //displayError(R.string.NoAccounts)
                    //finish()
                    return
                }

                val w = act.wallet

                if (true)
                {
                    val idData = w.lookupIdentityDomain(h) // + path)
                    if (idData == null)
                    {
                        val queries = iuri.queryMap()
                        if (queries["op"]?.lowercase() != "reg")
                        {
                            blankActivity()
                            displayError(R.string.UnknownDomainRegisterFirst, null, { finish() })
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
                                if (k in nexidParams)
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
                        var settingsNeedChanging = false
                        for ((k, v) in attribs)
                        {
                            if (k in nexidParams)
                            {
                                if (reqs[k] != v)   // Change the information requirements coming from this domain: TODO, ignore looser requirements
                                {
                                    reqs[k] = v
                                    settingsNeedChanging = true
                                }
                            }
                        }
                        if (settingsNeedChanging)
                        {
                            var intent = Intent(context, DomainIdentitySettings::class.java)
                            intent.putExtra("domainName", h)
                            intent.putExtra("title", getString(R.string.domainRequestingAdditionalIdentityInfo))
                            for ((k, v) in attribs)
                            {
                                intent.putExtra(k, v)
                            }

                            startActivityForResult(intent, IDENTITY_SETTINGS_RESULT)
                        }
                    }
                }
            }
        }

    }

    fun updatePermsFromIntent(intent: Intent)
    {
        for (k in nexidParams)  // Update new perms
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
            clearIntentAndFinish(i18n(R.string.primaryAccountRequired) % mapOf("primCurrency" to (chainToURI[PRIMARY_CRYPTO] ?: "")), i18n(R.string.primaryAccountRequiredDetails))
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
                displayError(R.string.NoAccounts, null, { finish() })
                return
            }
        }

        host = iuri.getHost()
        val attribs = iuri.queryMap()
        val op = attribs["op"]
        if (op == "info")
        {
            ProvideLoginInfoText.text = i18n(R.string.provideInfoQuestion)
        }
        else if (op == "reg")
        {
            identityInformation.visibility = View.INVISIBLE
            // TODO check if we know about this site
        }
        else if (op == "sign")
        {
            ProvideLoginInfoText.text = i18n(R.string.signDataQuestion)
            val signText = attribs["sign"]
            if (signText != null)
            {
                identityOpDetailsHeader.text = i18n(R.string.textToSign)
                val s = URLDecoder.decode(signText,"utf-8")
                identityInformation.text = s
                msgToSign = signText.toByteArray()
            }
            else
            {
                identityOpDetailsHeader.text = i18n(R.string.binaryToSign)
                val signHex = attribs["signhex"]
                if (signHex != null)
                {
                    identityInformation.text = signHex
                    msgToSign = signHex.fromHex()
                }
                else
                {
                    clearIntentAndFinish(i18n(R.string.nothingToSign))
                }
            }
        }

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
                val responseProtocol = attribs["proto"]
                val protocol = responseProtocol ?: iuri.protocol  // Prefer the protocol requested by the other side, otherwise use the same protocol we got the request from

                val portStr = if ((port > 0) && (port != 80) && (port != 443)) ":" + port.toString() else ""
                val chalToSign = h + portStr + "_nexid_" + op + "_" + challenge
                LogIt.info("challenge: " + chalToSign + " cookie: " + cookie)

                if (challenge == null) // intent was previously cleared by someone throw IdentityException("challenge string was not provided", "no challenge")
                {
                    finish()
                    return@launch
                }

                val act = account ?: try
                {
                    (application as WallyApp).primaryAccount
                } catch (e: PrimaryWalletInvalidException)
                {
                    clearIntentAndFinish(i18n(R.string.primaryAccountRequired) % mapOf("primCurrency" to (chainToURI[PRIMARY_CRYPTO] ?: "")), i18n(R.string.primaryAccountRequiredDetails))
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

                if (op == "login")
                {
                    val sig = Wallet.signMessage(chalToSign.toByteArray(), secret.getSecret())
                    if (sig.size == 0) throw IdentityException("Wallet failed to provide a signable identity", "bad wallet", ErrorSeverity.Severe)
                    val sigStr = Codec.encode64(sig)
                    LogIt.info("signature is: " + sigStr)

                    var loginReq = protocol + "://" + h + portStr + path
                    loginReq += "?op=login&addr=" + address.toString() + "&sig=" + URLEncoder.encode(sigStr, "UTF-8") + "&cookie=" + URLEncoder.encode(cookie, "UTF-8")

                    var forwarded = 0;
                    getloop@ while (forwarded < 3)
                    {
                        LogIt.info("login reply: " + loginReq)
                        try
                        {
                            val req: HttpURLConnection = URL(loginReq).openConnection() as HttpURLConnection
                            req.setConnectTimeout(HTTP_REQ_TIMEOUT_MS)
                            val resp = req.inputStream.bufferedReader().readText()
                            LogIt.info("login response code:" + req.responseCode.toString() + " response: " + resp)
                            if ((req.responseCode >= 200) and (req.responseCode < 250))
                            {
                                displayNotice(resp, null, 1000) { clearIntentAndFinish() }
                            }
                            else if ((req.responseCode == 301) or (req.responseCode == 302))  // Handle URL forwarding (often switching from http to https)
                            {
                                loginReq = req.getHeaderField("Location")
                                forwarded += 1
                                continue@getloop
                            }
                            else
                            {
                                displayError(resp, null, { clearIntentAndFinish() })
                            }
                        }
                        catch (e: FileNotFoundException)
                        {
                            displayError(R.string.badLink, loginReq, { clearIntentAndFinish() })
                        }
                        catch (e: IOException)
                        {
                            displayError(R.string.connectionAborted, loginReq, { clearIntentAndFinish() })
                        }
                        catch (e: java.net.ConnectException)
                        {
                            displayError(R.string.connectionException, null, { clearIntentAndFinish() })
                        }

                        break@getloop  // only way to actually loop is to hit a 301 or 302
                    }
                }
                else if ((op == "reg") || (op == "info"))
                {
                    val sig = Wallet.signMessage(chalToSign.toByteArray(), secret.getSecret())
                    if (sig.size == 0) throw IdentityException("Wallet failed to provide a signable identity", "bad wallet", ErrorSeverity.Severe)
                    val sigStr = Codec.encode64(sig)
                    LogIt.info("signature is: " + sigStr)

                    var forwarded = 0

                    val identityInfo = wallet.lookupIdentityInfo(address)
                    if (identityInfo == null)
                    {
                        throw IdentityException("Wallet did not provide identity information", "bad wallet", ErrorSeverity.Severe)
                    }

                    var loginReq = protocol + "://" + h + portStr + path

                    val params = mutableMapOf<String, String>()
                    params["op"] = op
                    params["addr"] = address.toString()
                    params["sig"] = sigStr
                    params["cookie"] = cookie.toString()

                    // Supply additional requested data
                    postloop@ while (forwarded < 3)
                    {
                        val prefs: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(applicationContext)

                        val idData = wallet.lookupIdentityDomain(h)
                        if (idData != null)
                        {
                            val perms = mutableMapOf<String, Boolean>()
                            idData.getPerms(perms)
                            for (i in nexidParams)
                            {
                                if (attribs.containsKey(i))
                                {
                                    val req = attribs[i]

                                    if (perms[i] == true)
                                    {
                                        val info = identityInfo.getString(i, null)
                                        if (info == null || info == "")  // missing some info that is needed
                                        {
                                            var intent = Intent(v.context, IdentitySettings::class.java)
                                            nexidUpdateIntentFromReqs(intent, reqs)
                                            intent.putExtra("domainName", idData.domain)
                                            (v.context as Activity).startActivity(intent)
                                        }
                                        else
                                        {
                                            params[i] = info
                                        }
                                    }
                                    else
                                    {
                                        if (req == "m")  // mandatory, so this login won't work
                                        {
                                            displayError(i18n(R.string.withholdingMandatoryInfo))
                                            // Start DomainIdentitySettings dialog, instead of
                                            clearIntentAndFinish()
                                        }
                                    }
                                }
                            }
                        }

                        val jsonBody = StringBuilder("{")
                        var firstTime = true
                        for ((k, value) in params)
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

                        LogIt.info("sending registration reply: " + loginReq)
                        try
                        {
                            //val body = """[1,2,3]"""  // URLEncoder.encode("""[1,2,3]""","UTF-8")
                            val req: HttpURLConnection = URL(loginReq).openConnection() as HttpURLConnection
                            req.requestMethod = "POST"
                            req.setRequestProperty("Content-Type", "application/json")
                            req.setRequestProperty("Accept", "*/*")
                            req.setRequestProperty("Content-Length", jsonBody.length.toString())
                            req.setConnectTimeout(HTTP_REQ_TIMEOUT_MS)
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
                            {
                                displayNotice(resp, null, 1000) { clearIntentAndFinish() }
                            }
                            else if ((req.responseCode == 301) or (req.responseCode == 302))  // Handle URL forwarding
                            {
                                loginReq = req.getHeaderField("Location")
                                forwarded += 1
                                continue@postloop
                            }
                            else
                            {
                                displayError(resp, null, { clearIntentAndFinish() })
                            }
                        }
                        catch (e: java.net.SocketTimeoutException)
                        {
                            LogIt.info("SOCKET TIMEOUT:  If development, check phone's network.  Ensure you can route from phone to target!  " + e.toString())
                            displayError(R.string.connectionException, null, { clearIntentAndFinish() })
                        }
                        catch (e: IOException)
                        {
                            LogIt.info("registration IOException: " + e.toString())
                            displayError(R.string.connectionAborted, null, { clearIntentAndFinish() })
                        }
                        catch (e: FileNotFoundException)
                        {
                            LogIt.info("registration FileNotFoundException: " + e.toString())
                            displayError(R.string.badLink, null, { clearIntentAndFinish() })
                        }
                        catch (e: java.net.ConnectException)
                        {
                            displayError(R.string.connectionException, null, { clearIntentAndFinish() })
                        }
                        catch (e: Throwable)
                        {
                            displayError(R.string.unknownError, null, { clearIntentAndFinish() })
                        }

                        break@postloop  // Only way to actually loop is to get a http 301 or 302
                    }
                }
                else if (op == "sign")
                {
                    val msg = msgToSign
                    if (msg == null)
                    {
                        displayError(R.string.nothingToSign, null, { clearIntentAndFinish() })
                    }
                    else
                    {
                        val msgSig = Wallet.signMessage(msg, secret.getSecret())
                        val sigStr = Codec.encode64(msgSig)
                        laterUI {
                            var clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
                            var clip = ClipData.newPlainText("text", address.toString() + " -> " + sigStr)
                            clipboard.setPrimaryClip(clip)
                        }
                        displayNotice(R.string.sigInClipboard, NOTICE_DISPLAY_TIME, { clearIntentAndFinish() })

                        val reply = attribs["reply"]
                        if (reply == null || reply == "true")
                        {
                        var sigReq = protocol + "://" + h + portStr + path
                        sigReq += "?op=sign&addr=" + address.toString() + "&sig=" + URLEncoder.encode(sigStr, "UTF-8") + "&cookie=" + URLEncoder.encode(cookie, "UTF-8")

                        var forwarded = 0
                        getloop@ while (forwarded < 3)
                        {
                            LogIt.info("signature reply: " + sigReq)
                            try
                            {
                                val req: HttpURLConnection = URL(sigReq).openConnection() as HttpURLConnection
                                req.setConnectTimeout(HTTP_REQ_TIMEOUT_MS)
                                val resp = req.inputStream.bufferedReader().readText()
                                LogIt.info("signature response code:" + req.responseCode.toString() + " response: " + resp)
                                if ((req.responseCode >= 200) and (req.responseCode < 250))
                                {
                                    displayNotice(resp, null, 1000) { clearIntentAndFinish() }
                                }
                                else if ((req.responseCode == 301) or (req.responseCode == 302))  // Handle URL forwarding (often switching from http to https)
                                {
                                    sigReq = req.getHeaderField("Location")
                                    forwarded += 1
                                    continue@getloop
                                }
                                else
                                {
                                    displayError(resp, null, { clearIntentAndFinish() })
                                }
                            }
                            catch (e: FileNotFoundException)
                            {
                                displayError(R.string.badLink, sigReq, { clearIntentAndFinish() })
                            }
                            catch (e: IOException)
                            {
                                displayError(R.string.connectionAborted, sigReq, { clearIntentAndFinish() })
                            }
                            catch (e: java.net.ConnectException)
                            {
                                displayError(R.string.connectionException, null, { clearIntentAndFinish() })
                            }
                            break@getloop  // only way to actually loop is to hit a 301 or 302
                        }
                        }
                    }

                }
            }
            else  // uri was null
            {
                clearIntentAndFinish()
            }
        }

        showUI(false)
    }

    fun showUI(show:Boolean)
    {
        val s = if (show) View.VISIBLE else View.INVISIBLE

        identityInformation.visibility = s
        identityOpDetailsHeader.visibility = s
        ProvideLoginInfoText.visibility = s
        displayLoginRecipient.visibility = s
        provideIdentityNoButton.visibility = s
        provideIdentityYesButton.visibility = s
    }

    @Suppress("UNUSED_PARAMETER")
    fun onDontProvideIdentity(v: View)
    {
        showUI(false)
        clearIntentAndFinish()
    }

    fun clearIntentAndFinish(error: String? = null, details: String? = null)
    {
        intent.putExtra("repeat", "true")
        if (error != null) intent.putExtra("error", error)
        if (details != null) intent.putExtra("details", details)
        setResult(Activity.RESULT_OK, intent)
        finish()
    }

};

