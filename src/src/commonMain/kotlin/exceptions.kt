package info.bitcoinunlimited.www.wally

import org.nexa.libnexakotlin.*

open class PrimaryWalletInvalidException() : LibNexaException("Primary account not defined or currently unavailable", "No primary account", ErrorSeverity.Abnormal)
open class WalletInvalidException() : LibNexaExceptionI(S.accountUnavailableDetails, i18n(S.accountUnavailable), ErrorSeverity.Expected)

open class PasteEmptyException() : LibNexaExceptionI(S.pasteIsEmpty, null, ErrorSeverity.Expected)
open class BadAmountException(msg: Int) : LibNexaExceptionI(S.badAmount, i18n(msg))
open class BadCryptoException(msg: Int = -1) : LibNexaExceptionI(S.badCryptoCode, i18n(msg))
open class BadUnitException(msg: Int = -1) : LibNexaExceptionI(S.badCurrencyUnit, i18n(msg))
open class UnavailableException(msg: Int = -1) : LibNexaExceptionI(S.unavailable, i18n(msg))
open class UiUnavailableException(msg: Int = -1) : LibNexaExceptionI(S.unavailable, i18n(msg))

open class TdppException(err: Int? = null, details: String?) : LibNexaExceptionI(if (err != null) err else S.unknownError, details, ErrorSeverity.Abnormal)

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