package info.bitcoinunlimited.www.wally.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.Composable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.text.input.ImeAction
import info.bitcoinunlimited.www.wally.*
import info.bitcoinunlimited.www.wally.ui.theme.BrightBkg
import kotlinx.coroutines.delay
import org.nexa.libnexakotlin.GetLog

private val LogIt = GetLog("BU.wally.unlockview")

/**
 * Displays a confirm/dismiss dialog to users with optional confirm/dismiss button text and description
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UnlockDialog(onPinEntered: (String) -> Unit)
{
    val pin = remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }
    clearAlerts()

    fun attemptUnlock()
    {
        if (wallyApp!!.unlockAccounts(pin.value) == 0)  // nothing got unlocked
            displayError(S.InvalidPIN, persistAcrossScreens = 0)
        else
        {
            clearAlerts()
            triggerAccountsChanged()
            triggerAssignAccountsGuiSlots()  // In case accounts should be showed

        }  // We don't know what accounts got unlocked so just redraw them all in this non-performance change
        LogIt.info("close unlock dialog")
        triggerUnlockDialog(false)
        onPinEntered(pin.value)
    }

    AlertDialog(title = { Text(i18n(S.EnterPIN)) },
      containerColor = BrightBkg,
      text = {
              Column {
                  TextField(pin.value,
                    colors = TextFieldDefaults.textFieldColors(
                      cursorColor = Color.Black,
                      containerColor = Color.Transparent,
                      focusedIndicatorColor = Color.Black,
                      unfocusedIndicatorColor = Color.Black
                    ),
                    onValueChange = { pin.value = it },
                    //label = { Text("Enter PIN") },
                    modifier = Modifier.focusRequester(focusRequester).onKeyEvent {
                        if ((it.key == Key.Enter)||(it.key == Key.NumPadEnter))
                        {
                            attemptUnlock()
                            false
                        }
                        false// Do not accept this key
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(
                        onDone = { attemptUnlock() }
                    ),
                  )
              }
             },
      confirmButton = {
          },
      dismissButton = {
          },
      onDismissRequest = {
            triggerUnlockDialog(false)
      },
      )
}