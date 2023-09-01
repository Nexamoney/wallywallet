// Copyright (c) 2019 Andrew Stone Consulting (qq9wwnuw4eukyh5g34ckg5vk4aaxnvr04vkspyv850)
// Distributed under the MIT software license, see the accompanying file COPYING or http://www.opensource.org/licenses/mit-license.php.
package info.bitcoinunlimited.www.wally

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import java.io.FileNotFoundException
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

import info.bitcoinunlimited.www.wally.databinding.ActivityIdentityOpBinding
import org.nexa.libnexakotlin.libnexa
import java.io.DataOutputStream
import java.net.URLDecoder
import org.nexa.libnexakotlin.*

private val LogIt = GetLog("BU.wally.IdentityOp")

val IDENTITY_URI_SCHEME = "nexid"

open class IdentityOpException(msg: String, shortMsg: String? = null, severity: ErrorSeverity = ErrorSeverity.Abnormal) : LibNexaException(msg, shortMsg, severity)

class IdentityOpActivity : CommonNavActivity()
{
    private lateinit var ui:ActivityIdentityOpBinding
    override var navActivityId = -1

    var displayedLoginRequest: URL? = null

    var queries: Map<String, String> = mapOf()
    val perms = mutableMapOf<String, Boolean>()
    val reqs = mutableMapOf<String, String>()

    var host: String? = null
    var triggeredNewDomain = false
    var account: Account? = null
    var pinTries = 0
    var msgToSign:ByteArray? = null

    var skipActivityResume = false

    override fun onCreate(savedInstanceState: Bundle?)
    {
        super.onCreate(savedInstanceState)
        ui = ActivityIdentityOpBinding.inflate(layoutInflater)
        setContentView(ui.root)

        if (intent.scheme != null)  // its null if normal app startup
        {
            updatePermsFromIntent(intent)
            checkIntentNewDomain(intent)
        }
    }

    override fun onStart()
    {
        super.onStart()

        // If we ever need to launch an identity op then start showing the menu item
        enableMenu(this, SHOW_IDENTITY_PREF)
    }

    override fun onResume()
    {
        super.onResume()

        if (skipActivityResume) return  // because onActivityResult is handling it

        ui.identityInformation.visibility = View.INVISIBLE
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
        ui.displayLoginRecipient.visibility = View.INVISIBLE
        ui.provideIdentityNoButton.visibility = View.INVISIBLE
        ui.provideIdentityYesButton.visibility = View.INVISIBLE
        ui.ProvideLoginInfoText.visibility = View.INVISIBLE
        ui.identityInformation.visibility = View.INVISIBLE
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
            queries = iuri.queryMap()

            val context = this

            if (true)
            {
                // Run blocking so the IdentityOp activity does not momentarily appear
                val act = account ?: try
                {
                    val tmp = (application as WallyApp).primaryAccount
                    if (tmp == null) throw PrimaryWalletInvalidException()
                    tmp
                }
                catch (e: PrimaryWalletInvalidException)
                {
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
                val op = queries["op"]?.lowercase() ?: ""

                if (op != "sign")  // sign does not need a registered identity domain so skip all this checking for this op
                {
                    val idData = w.lookupIdentityDomain(h) // + path)
                    if (idData == null)  // We don't know about this domain, so register and give it info in one shot
                    {
                        if (op != "reg")
                        {
                            blankActivity()
                            wallyApp?.displayError(R.string.UnknownDomainRegisterFirst)
                            finish()
                            return
                        }
                        if (triggeredNewDomain == false)  // I only want to drop into the new domain settings once
                        {
                            triggeredNewDomain = true
                            var intent = Intent(context, DomainIdentitySettings::class.java)
                            intent.putExtra("domainName", h) // + path)
                            intent.putExtra("title", getString(R.string.newDomainRequestingIdentity))
                            intent.putExtra("mode", "reg")
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
                    else // We know about this domain, but its asking for more info
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
                            intent.putExtra("mode", "reg")
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
                    skipActivityResume = true
                    val err = data.getStringExtra("error")
                    if (err != null) displayError(err)
                    val result = data.getStringExtra("result")
                    if (result == "accept")
                        onProvideIdentity(null)
                    else
                        onDontProvideIdentity(null)
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
                wallyApp?.displayError(R.string.NoAccounts)
                finish()
                return
            }
        }

        host = iuri.getHost()
        val attribs = iuri.queryMap()
        val op = attribs["op"]
        if (op == "info")
        {
            ui.ProvideLoginInfoText.text = i18n(R.string.provideInfoQuestion)
        }
        else if (op == "reg")
        {
            // TODO check if we know about this site
        }
        else if (op == "sign")
        {
            ui.ProvideLoginInfoText.text = i18n(R.string.signDataQuestion)
            val signText = attribs["sign"]
            if (signText != null)
            {
                ui.identityOpDetailsHeader.text = i18n(R.string.textToSign)
                val s = URLDecoder.decode(signText,"utf-8")
                ui.identityInformation.text = s
                msgToSign = signText.toByteArray()
            }
            else
            {
                ui.identityOpDetailsHeader.text = i18n(R.string.binaryToSign)
                val signHex = attribs["signhex"]
                if (signHex != null)
                {
                    ui.identityInformation.text = signHex
                    msgToSign = signHex.fromHex()
                }
                else
                {
                    clearIntentAndFinish(i18n(R.string.nothingToSign))
                }
            }
            ui.identityInformation.visibility = View.VISIBLE
        }


        ui.displayLoginRecipient.visibility = View.VISIBLE
        ui.provideIdentityNoButton.visibility = View.VISIBLE
        ui.provideIdentityYesButton.visibility = View.VISIBLE
        displayedLoginRequest = iuri
        ui.displayLoginRecipient.text = host // + " (" + path + ")"
        checkIntentNewDomain(receivedIntent)
    }

    // If "yes" button pressed, contact the remote server with your registration or identity information
    @Suppress("UNUSED_PARAMETER")
    fun onProvideIdentity(v: View?)
    {
        val iuri = displayedLoginRequest
        launch {
            if (iuri != null)
            {
                val tmpHost = iuri.getHost()
                host = tmpHost
                val port = iuri.getPort()
                val path = iuri.getPath()
                val attribs = iuri.queryMap()
                val challenge = attribs["chal"]
                val cookie = attribs["cookie"]
                val op = attribs["op"]
                val responseProtocol = attribs["proto"]
                val protocol = responseProtocol ?: iuri.protocol  // Prefer the protocol requested by the other side, otherwise use the same protocol we got the request from

                val portStr = if ((port > 0) && (port != 80) && (port != 443)) ":" + port.toString() else ""

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
                val idData = wallet.lookupIdentityDomain(tmpHost)

                val seed = if (idData != null)
                {
                    if (idData.useIdentity == IdentityDomain.COMMON_IDENTITY)
                        Bip44Wallet.COMMON_IDENTITY_SEED
                    else if (idData.useIdentity == IdentityDomain.IDENTITY_BY_HASH)
                        tmpHost + path
                    else
                    {
                        LogIt.severe("Invalid identity selector; corrupt?")
                        Bip44Wallet.COMMON_IDENTITY_SEED
                    }
                }
                else
                    Bip44Wallet.COMMON_IDENTITY_SEED

                val identityDest: PayDestination = wallet.destinationFor(seed)

                // This is a coding bug in the wallet
                val secret = identityDest.secret ?: throw IdentityException("Wallet failed to provide an identity with a secret", "bad wallet", ErrorSeverity.Severe)
                val address = identityDest.address ?: throw IdentityException("Wallet failed to provide an identity with an address", "bad wallet", ErrorSeverity.Severe)

                if (op == "login")
                {
                    val chalToSign = tmpHost + portStr + "_nexid_" + op + "_" + challenge
                    LogIt.info("challenge: " + chalToSign + " cookie: " + cookie)

                    if (challenge == null) // intent was previously cleared by someone throw IdentityException("challenge string was not provided", "no challenge")
                    {
                        finish()
                        return@launch
                    }

                    val sig = libnexa.signMessage(chalToSign.toByteArray(), secret.getSecret())
                    if (sig == null || sig.size == 0) throw IdentityException("Wallet failed to provide a signable identity", "bad wallet", ErrorSeverity.Severe)
                    val sigStr = Codec.encode64(sig)
                    LogIt.info("signature is: " + sigStr)

                    var loginReq = protocol + "://" + tmpHost + portStr + path
                    loginReq += "?op=login&addr=" + address.urlEncode() + "&sig=" + sigStr.urlEncode() + "&cookie=" + URLEncoder.encode(cookie, "UTF-8")

                    wallyApp?.handleLogin(loginReq)
                    finish()

                }
                else if ((op == "reg") || (op == "info"))
                {
                    val chalToSign = tmpHost + portStr + "_nexid_" + op + "_" + challenge
                    LogIt.info("challenge: " + chalToSign + " cookie: " + cookie)

                    if (challenge == null) // intent was previously cleared by someone throw IdentityException("challenge string was not provided", "no challenge")
                    {
                        finish()
                        return@launch
                    }

                    val sig = libnexa.signMessage(chalToSign.toByteArray(), secret.getSecret())
                    if (sig == null || sig.size == 0) throw IdentityException("Wallet failed to provide a signable identity", "bad wallet", ErrorSeverity.Severe)
                    val sigStr = Codec.encode64(sig)
                    LogIt.info("signature is: " + sigStr)

                    var forwarded = 0

                    val identityInfo = wallet.lookupIdentityInfo(address)
                    if (identityInfo == null)
                    {
                        throw IdentityException("Wallet did not provide identity information", "bad wallet", ErrorSeverity.Severe)
                    }

                    var loginReq = protocol + "://" + tmpHost + portStr + path

                    val params = mutableMapOf<String, String>()
                    params["op"] = op
                    params["addr"] = address.toString()
                    params["sig"] = sigStr
                    params["cookie"] = cookie.toString()

                    // Supply additional requested data
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
                                            var intent = Intent(this, IdentitySettings::class.java)
                                            nexidUpdateIntentFromReqs(intent, reqs)
                                            intent.putExtra("domainName", idData.domain)
                                            intent.putExtra("title", i18n(R.string.IdentityDataMissing))
                                            startActivity(intent)
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

                    wallyApp?.handlePostLogin(loginReq, jsonBody.toString())
                    clearIntentAndFinish()
                }
                else if (op == "sign")
                {
                    val msg = msgToSign
                    if (msg == null)
                    {
                        wallyApp?.displayError(R.string.nothingToSign)
                        clearIntentAndFinish()
                    }
                    else
                    {
                        val msgSig = libnexa.signMessage(msg, secret.getSecret())
                        if (msgSig == null || msgSig.size == 0)
                        {
                            wallyApp?.displayError(R.string.badSignature)
                            clearIntentAndFinish()
                        }
                        else
                        {
                            val sigStr = Codec.encode64(msgSig)
                            laterUI {
                                val msgStr = String(msg)
                                var clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
                                val s = """{ "message":"${msgStr}", "address":"${address.toString()}", "signature": "${sigStr}" }"""
                                var clip = ClipData.newPlainText("text", s)
                                LogIt.info(s)
                                clipboard.setPrimaryClip(clip)
                            }
                            wallyApp?.displayNotice(R.string.sigInClipboard)

                            val reply = attribs["reply"]
                            if (reply == null || reply == "true")
                            {
                                var sigReq = protocol + "://" + tmpHost + portStr + path
                                sigReq += "?op=sign&addr=" + address.toString() + "&sig=" + URLEncoder.encode(sigStr, "UTF-8") + "&cookie=" + URLEncoder.encode(cookie, "UTF-8")

                                var forwarded = 0  // Handle URL forwarding
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
                                            wallyApp?.displayNotice(resp)
                                            clearIntentAndFinish()
                                        }
                                        else if ((req.responseCode == 301) or (req.responseCode == 302))  // Handle URL forwarding (often switching from http to https)
                                        {
                                            sigReq = req.getHeaderField("Location")
                                            forwarded += 1
                                            continue@getloop
                                        }
                                        else
                                        {
                                            wallyApp?.displayNotice(resp)
                                            clearIntentAndFinish()
                                        }
                                    } catch (e: FileNotFoundException)
                                    {
                                        wallyApp?.displayError(R.string.badLink)
                                        clearIntentAndFinish()
                                    } catch (e: IOException)
                                    {
                                        wallyApp?.displayError(R.string.connectionAborted)
                                        clearIntentAndFinish()
                                    } catch (e: java.net.ConnectException)
                                    {
                                        wallyApp?.displayError(R.string.connectionException)
                                        clearIntentAndFinish()
                                    }
                                    break@getloop  // only way to actually loop is to hit a 301 or 302
                                }
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

        ui.identityInformation.visibility = s
        ui.identityOpDetailsHeader.visibility = s
        ui.ProvideLoginInfoText.visibility = s
        ui.displayLoginRecipient.visibility = s
        ui.provideIdentityNoButton.visibility = s
        ui.provideIdentityYesButton.visibility = s
    }

    @Suppress("UNUSED_PARAMETER")
    fun onDontProvideIdentity(v: View?)
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

