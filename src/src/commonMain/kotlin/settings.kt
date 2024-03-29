// Copyright (c) 2019 Andrew Stone Consulting (qq9wwnuw4eukyh5g34ckg5vk4aaxnvr04vkspyv850)
// Distributed under the MIT software license, see the accompanying file COPYING or http://www.opensource.org/licenses/mit-license.php.
package info.bitcoinunlimited.www.wally

import org.nexa.libnexakotlin.ChainSelector
import org.nexa.libnexakotlin.chainToURI

val LOCAL_CURRENCY_PREF = "localCurrency"
val PRIMARY_ACT_PREF = "primaryAccount"
val DEV_MODE_PREF = "devinfo"
val SHOW_IDENTITY_PREF = "showIdentityMenu"
val SHOW_TRICKLEPAY_PREF = "showTricklePayMenu"
val SHOW_ASSETS_PREF = "showAssetsMenu"
val CONFIRM_ABOVE_PREF = "confirmAbove"

val ACCESS_PRICE_DATA_PREF = "accessPriceData"

const val EXCLUSIVE_NODE_SWITCH = "exclusiveNodeSwitch"
val CONFIGURED_NODE = "NodeAddress"
const val PREFER_NODE_SWITCH = "preferNodeSwitch"

var currentlySelectedAccount = chainToURI[ChainSelector.NEXA]  // The default crypto I'm using
