package info.bitcoinunlimited.www.wally

import kotlinx.coroutines.flow.MutableStateFlow

const val ERROR_DISPLAY_TIME = 10000L
const val NOTICE_DISPLAY_TIME = 4000L
const val NORMAL_NOTICE_DISPLAY_TIME = 2000L

var allowAccessPriceData: Boolean = true
var devMode = false

/** Make some type (probably a primitive type) into an object that holds one of them */
class Objectify<T>(var obj: T)
{
}