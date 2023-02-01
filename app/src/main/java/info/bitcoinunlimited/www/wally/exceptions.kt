package info.bitcoinunlimited.www.wally

import bitcoinunlimited.libbitcoincash.BUException
import bitcoinunlimited.libbitcoincash.ErrorSeverity

open class PrimaryWalletInvalidException() : BUException("Primary account not defined or currently unavailable", "No primary account", ErrorSeverity.Abnormal)
open class WalletInvalidException() : BUException(i18n(R.string.accountUnavailableDetails), i18n(R.string.accountUnavailable), ErrorSeverity.Expected)

open class PasteUnintelligibleException() : BUException("", i18n(R.string.pasteUnintelligible), ErrorSeverity.Expected)
open class NotUriException() : PasteUnintelligibleException()
open class PasteEmptyException() : BUException("", i18n(R.string.pasteIsEmpty), ErrorSeverity.Abnormal)
open class BadAmountException(msg: Int) : BUException(i18n(msg), i18n(R.string.badAmount))
open class BadCryptoException(msg: Int = -1) : BUException(i18n(msg), i18n(R.string.badCryptoCode))
open class BadUnitException(msg: Int = -1) : BUException(i18n(msg), i18n(R.string.badCurrencyUnit))
open class UnavailableException(msg: Int = -1) : BUException(i18n(msg), i18n(R.string.unavailable))

open class UiUnavailableException(msg: Int = -1) : BUException(i18n(msg), i18n(R.string.unavailable))