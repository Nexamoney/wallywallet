package info.bitcoinunlimited.www.wally.ui2.views

import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.Composable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import info.bitcoinunlimited.www.wally.*
import info.bitcoinunlimited.www.wally.ui2.triggerAccountsChanged
import info.bitcoinunlimited.www.wally.ui2.triggerAssignAccountsGuiSlots
import info.bitcoinunlimited.www.wally.ui2.triggerUnlockDialog
import info.bitcoinunlimited.www.wally.ui2.DoneButtonOptional
import org.nexa.libnexakotlin.GetLog

private val LogIt = GetLog("BU.wally.unlockview")

/**
 * Displays a confirm/dismiss dialog to users with optional confirm/dismiss button text and description
 */
@Composable
fun UnlockView(enterPin: String = i18n(S.EnterPIN), onPinEntered: (String) -> Unit)
{
    val pin = remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }

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

    AlertDialog(title = { Text(enterPin) },
      text = {
                  TextField(pin.value,
                    colors = TextFieldDefaults.colors(
                      cursorColor = Color.Black,
                      focusedContainerColor = Color.Transparent,
                      unfocusedContainerColor = Color.Transparent,
                      focusedIndicatorColor = Color.Black,
                      unfocusedIndicatorColor = Color.Black
                    ),
                    onValueChange = { pin.value = it },
                    modifier = Modifier.testTag("EnterPIN")
                      .focusRequester(focusRequester)
                      .onKeyEvent {
                        if ((it.key == Key.Enter)||(it.key == Key.NumPadEnter))
                        {
                            attemptUnlock()
                            false
                        }
                        else false// Do not accept this key
                    }.onGloballyPositioned {
                        focusRequester.requestFocus()
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done, keyboardType = KeyboardType.Number),
                    keyboardActions = KeyboardActions(
                        onDone = { attemptUnlock() }
                    ),
                  )
             },
      confirmButton = {
          DoneButtonOptional( onClick = { attemptUnlock() })
      },
      dismissButton = {
          },
      onDismissRequest = {
            triggerUnlockDialog(false)
      },
      )
}