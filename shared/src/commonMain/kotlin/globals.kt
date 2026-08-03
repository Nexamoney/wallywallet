package info.bitcoinunlimited.www.wally

val IDENTITY_URI_SCHEME = "nexid"
val TDPP_URI_SCHEME = "tdpp"

const val ERROR_DISPLAY_TIME = 10000L
const val NOTICE_DISPLAY_TIME = 5000L
const val NORMAL_NOTICE_DISPLAY_TIME = 2000L

const val HTTP_REQ_TIMEOUT_MS: Int = 7000

// If the blockchain is behind by this many seconds, offer to fast forward.
const val OFFER_FAST_FORWARD_GAP = 86400*1  // 1 day in seconds

var allowAccessPriceData: Boolean = true
var devMode = false
var darkMode = false
var localCurrency = "USD"

// Search the first N addresses in a particular derivation path during wallet recovery.
// Since wallets give out addresses in order, this will tend to find the activity.  The only reason we search a bunch of addresses
// is because maybe some addresses were given out but payments weren't made.
val WALLY_WALLET_RECOVERY_DERIVATION_PATH_SEARCH_DEPTH = 200
val WALLY_WALLET_RECOVERY_IDENTITY_DERIVATION_PATH_SEARCH_DEPTH = 5

val WALLY_WALLET_RECOVERY_NON_INCREMENTAL_ADDRESS_HEIGHT = 200000  // This works around a bug in early wallets that accidentally would take addresses out of order
val WALLY_WALLET_FULL_RECOVERY_DERIVATION_PATH_MAX_GAP = 200
val WALLY_WALLET_FULL_RECOVERY_CHANGE_DERIVATION_PATH_MAX_GAP = 50
val WALLY_WALLET_FULL_RECOVERY_NONSTD_DERIVATION_PATH_MAX_GAP = 10