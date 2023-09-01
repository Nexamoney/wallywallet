package info.bitcoinunlimited.www.wally

import org.nexa.libnexakotlin.*

open class PrimaryWalletInvalidException() : LibNexaException("Primary account not defined or currently unavailable", "No primary account", ErrorSeverity.Abnormal)
open class WalletInvalidException() : LibNexaExceptionI(R.string.accountUnavailableDetails, i18n(R.string.accountUnavailable), ErrorSeverity.Expected)

open class PasteUnintelligibleException() : LibNexaExceptionI(R.string.pasteUnintelligible, null, ErrorSeverity.Expected)
open class NotUriException() : PasteUnintelligibleException()
open class PasteEmptyException() : LibNexaExceptionI(R.string.pasteIsEmpty, null, ErrorSeverity.Expected)
open class BadAmountException(msg: Int) : LibNexaExceptionI(R.string.badAmount, i18n(msg))
open class BadCryptoException(msg: Int = -1) : LibNexaExceptionI(R.string.badCryptoCode, i18n(msg))
open class BadUnitException(msg: Int = -1) : LibNexaExceptionI(R.string.badCurrencyUnit, i18n(msg))
open class UnavailableException(msg: Int = -1) : LibNexaExceptionI(R.string.unavailable, i18n(msg))
open class UiUnavailableException(msg: Int = -1) : LibNexaExceptionI(R.string.unavailable, i18n(msg))

open class TdppException(err: Int? = null, details: String?) : LibNexaExceptionI(if (err != null) err else R.string.unknownError, details, ErrorSeverity.Abnormal)

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