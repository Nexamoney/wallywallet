package info.bitcoinunlimited.www.wally

/** Display an alert in the native manner (if it exists, see @platform()).  If there is no native manner, just return */
actual fun displayAlert(alert: Alert) {
    return
}

/** Actually share this text using the platform's share functionality */
actual fun platformShare(textToShare: String)
{
    return
}

/** Get a image from the file system (probably a QR code) and get a wally command string from it */
actual fun ImageQrCode(imageParsed: (String?) -> Unit): Boolean
{
    return false
}

actual fun stackTraceWithout(skipFirst: MutableSet<String>, ignoreFiles: MutableSet<String>?): String
{
    return ""
}