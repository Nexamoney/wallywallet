package info.bitcoinunlimited.www.wally

import bitcoinunlimited.libbitcoincash.BUException
import bitcoinunlimited.libbitcoincash.ErrorSeverity
import bitcoinunlimited.libbitcoincash.logThreadException

open class BUExceptionI(err: Int, details:String?=null, severity: ErrorSeverity=ErrorSeverity.Expected) : BUException(i18n(err), details, severity, err)

open class PrimaryWalletInvalidException() : BUException("Primary account not defined or currently unavailable", "No primary account", ErrorSeverity.Abnormal)
open class WalletInvalidException() : BUExceptionI(R.string.accountUnavailableDetails, i18n(R.string.accountUnavailable), ErrorSeverity.Expected)

open class PasteUnintelligibleException() : BUExceptionI(R.string.pasteUnintelligible, null, ErrorSeverity.Expected)
open class NotUriException() : PasteUnintelligibleException()
open class PasteEmptyException() : BUExceptionI(R.string.pasteIsEmpty, null, ErrorSeverity.Expected)
open class BadAmountException(msg: Int) : BUExceptionI(R.string.badAmount, i18n(msg))
open class BadCryptoException(msg: Int = -1) : BUExceptionI(R.string.badCryptoCode, i18n(msg))
open class BadUnitException(msg: Int = -1) : BUExceptionI(R.string.badCurrencyUnit, i18n(msg))
open class UnavailableException(msg: Int = -1) : BUExceptionI(R.string.unavailable, i18n(msg))
open class UiUnavailableException(msg: Int = -1) : BUExceptionI(R.string.unavailable, i18n(msg))

open class TdppException(err: Int? = null, details: String?) : BUExceptionI(if (err != null) err else R.string.unknownError, details, ErrorSeverity.Abnormal)

fun<T> exceptNull(logTest:((e:Exception)->Boolean)? = null,doit: ()->T?):T?
{
    try
    {
        return doit()
    }
    catch(e:Exception)
    {
        if (logTest != null && logTest(e))
        {
            logThreadException(e)
        }
    }
    return null
}