package info.bitcoinunlimited.www.wally

import platform.UIKit.UIApplication

/** Returns true if this function is called within the UI thread
 * Many platforms have specific restrictions on what can be run within the UI (often the "main") thread.
 */
actual fun isUiThread(): Boolean
{
    return UIApplication.sharedApplication.isMainThread
}