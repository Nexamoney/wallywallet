// Copyright (c) 2019 Andrew Stone Consulting (qq9wwnuw4eukyh5g34ckg5vk4aaxnvr04vkspyv850)
// Distributed under the MIT software license, see the accompanying file COPYING or http://www.opensource.org/licenses/mit-license.php.
package info.bitcoinunlimited.www.wally

const val LOCAL_CURRENCY_PREF = "localCurrency"
const val PRIMARY_ACT_PREF = "primaryAccount"
const val DEV_MODE_PREF = "devinfo"
const val SHOW_IDENTITY_PREF = "showIdentityMenu"
const val SHOW_TRICKLE_PAY_PREF = "showTricklePayMenu"
const val SHOW_ASSETS_PREF = "showAssetsMenu"
const val CONFIRM_ABOVE_PREF = "confirmAbove"
const val SOUND_ENABLED_PREF = "soundEnabled"
const val LAST_REVIEW_TIMESTAMP = "lastReviewTimestamp"
const val ACCESS_PRICE_DATA_PREF = "accessPriceData"
const val EXCLUSIVE_NODE_SWITCH = "exclusiveNodeSwitch"
const val PREFER_NODE_SWITCH = "preferNodeSwitch"
const val BACKGROUND_SYNC_PREF = "backgroundSync"
const val DARK_MODE_PREF = "darkModeMenu"
const val EXPERIMENTAL_UX_MODE_PREF = "expUX"
const val CONFIGURED_NODE = "NodeAddress"
const val SELECTED_ACCOUNT_NAME_PREF = "selectedAccountName"

const val PREFERENCE_FILE_NAME = "bitcoinunlimited.wally.prefs"
// set to something to have a difference set of preference (for testing).  It is important to ALWAYS set something when testing
// or the list of active wallets may be overwritten.
var TEST_PREF = ""
