package info.bitcoinunlimited.www.wally

import androidx.compose.runtime.MutableState
import kotlinx.coroutines.flow.MutableStateFlow

var allowAccessPriceData: Boolean = true
var devMode: MutableStateFlow<Boolean> = MutableStateFlow(false)

/** Make some type (probably a primitive type) into an object that holds one of them */
class Objectify<T>(var obj: T)
{
}