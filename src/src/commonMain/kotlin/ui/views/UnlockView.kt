package info.bitcoinunlimited.www.wally.ui

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
import androidx.compose.ui.text.input.ImeAction
import info.bitcoinunlimited.www.wally.*

/**
 * Displays a confirm/dismiss dialog to users with optional confirm/dismiss button text and description
 */
@Composable
fun UnlockDialog(onPinEntered: (String) -> Unit)
{
    val pin = remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }
    clearAlerts()

    AlertDialog(title = { Text(i18n(S.EnterPIN)) },
      text = {
              Column {
                  TextField(pin.value,
                    onValueChange = { pin.value = it },
                    //label = { Text("Enter PIN") },
                    modifier = Modifier.focusRequester(focusRequester),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            if (wallyApp!!.unlockAccounts(pin.value) == 0)  // nothing got unlocked
                                displayError(S.InvalidPIN)
                            else triggerAccountsChanged()  // We don't know what accounts got unlocked so just redraw them all in this non-performance change
                            triggerUnlockDialog(false)
                        }
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