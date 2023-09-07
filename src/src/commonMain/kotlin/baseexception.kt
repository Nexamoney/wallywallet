package info.bitcoinunlimited.www.wally

import org.nexa.libnexakotlin.*


open class LibNexaExceptionI(err: Int, details:String?=null, severity: ErrorSeverity=ErrorSeverity.Expected) : LibNexaException(i18n(err), details, severity, err)

open class AssertException(why: String) : LibNexaException(why, "Assertion", ErrorSeverity.Abnormal)

// TODO xlat
open class PasteUnintelligibleException() : LibNexaExceptionI(S.pasteUnintelligible, null, ErrorSeverity.Expected)
open class NotUriException() : PasteUnintelligibleException()