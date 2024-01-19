package info.bitcoinunlimited.www.wally

import kotlinx.coroutines.flow.MutableStateFlow

val IDENTITY_URI_SCHEME = "nexid"
val TDPP_URI_SCHEME = "tdpp"

const val ERROR_DISPLAY_TIME = 10000L
const val NOTICE_DISPLAY_TIME = 5000L
const val NORMAL_NOTICE_DISPLAY_TIME = 2000L

const val HTTP_REQ_TIMEOUT_MS: Int = 7000

val LAST_RESORT_BCH_ELECTRS = "bch2.bitcoinunlimited.net"
val LAST_RESORT_NEXA_ELECTRS = "electrum.nexa.org"


var allowAccessPriceData: Boolean = true
var devMode = false

/** Make some type (probably a primitive type) into an object that holds one of them */
class Objectify<T>(var obj: T)
{
}