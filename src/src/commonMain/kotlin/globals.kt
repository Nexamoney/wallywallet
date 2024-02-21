package info.bitcoinunlimited.www.wally

val IDENTITY_URI_SCHEME = "nexid"
val TDPP_URI_SCHEME = "tdpp"

const val ERROR_DISPLAY_TIME = 10000L
const val NOTICE_DISPLAY_TIME = 5000L
const val NORMAL_NOTICE_DISPLAY_TIME = 2000L

const val HTTP_REQ_TIMEOUT_MS: Int = 7000

val LAST_RESORT_BCH_ELECTRS = "bch2.bitcoinunlimited.net"
val LAST_RESORT_NEXA_ELECTRS = "electrum.nexa.org"

const val NEXA_EXPLORER_URL = "https://explorer.nexa.org"
const val NEXA_TESTNET_EXPLORER_URL = "https://testnet-explorer.nexa.org"

var allowAccessPriceData: Boolean = true
var devMode = false

/** Make some type (probably a primitive type) into an object that holds one of them */
class Objectify<T>(var obj: T)
{
}

// Search the first N addresses in a particular derivation path during wallet recovery.
// Since wallets give out addresses in order, this will tend to find the activity.  The only reason we search a bunch of addresses
// is because maybe some addresses were given out but payments weren't made.
val WALLET_RECOVERY_DERIVATION_PATH_SEARCH_DEPTH = 15
val WALLET_RECOVERY_IDENTITY_DERIVATION_PATH_SEARCH_DEPTH = 5
