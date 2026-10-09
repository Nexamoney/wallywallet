package info.bitcoinunlimited.www.wally.ui.views

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.material3.AlertDialog
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.Cancel
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import info.bitcoinunlimited.www.wally.*
import info.bitcoinunlimited.www.wally.ui.DoneButtonOptional
import info.bitcoinunlimited.www.wally.ui.ScreenId
import info.bitcoinunlimited.www.wally.ui.assignAccountsGuiSlots
import info.bitcoinunlimited.www.wally.ui.nav
import info.bitcoinunlimited.www.wally.ui.theme.wallyAttention
import info.bitcoinunlimited.www.wally.ui.theme.wallyTile
import info.bitcoinunlimited.www.wally.ui.theme.wallyTileHeader
import info.bitcoinunlimited.www.wally.ui.triggerAccountsChanged
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import org.nexa.libnexakotlin.GetLog

private val LogIt = GetLog("BU.wally.unlockview")

class UnlockViewModel(val account:MutableStateFlow<Account?>): ViewModel()
{
    val unlockTileSize = MutableStateFlow<Int>(0)
    var unlockThen: (() -> Unit)? = null

    // The screen that asked for the PIN, so that leaving it can abandon the request
    var requestedOnScreen: ScreenId? = null

    val pin = MutableStateFlow<String>("")

    fun triggerUnlockDialog(show: Boolean = true, then: (() -> Unit)? = {})
    {
        if (show)
        {
            clearAlerts()
            unlockThen = then
            requestedOnScreen = nav.currentScreen.value
            unlockTileSize.interpolate(300, null, 300)
        }
        else
        {
            requestedOnScreen = null
            unlockTileSize.interpolate(300, null, 0)
        }
    }

    /** The user navigated off the screen that asked for the PIN, so drop the request and whatever it was going to do. */
    fun abandonUnlockDialog()
    {
        pin.value = ""
        unlockThen = null
        triggerUnlockDialog(false)
    }

    internal fun attemptUnlock(pin: String, dismissOnFailure: Boolean = true)
    {
        wallyApp!!.unlockAccountsThen(pin) { actsUnlocked ->
            if (actsUnlocked == 0)  // nothing got unlocked
                displayError(S.InvalidPIN, persistAcrossScreens = 0)
            else
            {
                LogIt.info("Unlocked ${actsUnlocked} accounts")
                clearAlerts()
                triggerAccountsChanged()
                assignAccountsGuiSlots()  // In case accounts should be showed
            }  // We don't know what accounts got unlocked so just redraw them all in this non-performance change
            if (dismissOnFailure) triggerUnlockDialog(false)
            unlockThen?.invoke()
            unlockThen = null
        }
    }
}

@Composable
fun UnlockTile(vm: UnlockViewModel, enterPin: String = i18n(S.EnterPIN))
{
    //val pin = remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }
    val curSz = vm.unlockTileSize.collectAsState().value
    val focusManager = LocalFocusManager.current
    val ime = LocalSoftwareKeyboardController.current
    val currentScreen = nav.currentScreen.collectAsState().value
    val showing = curSz != 0

    // Navigating away (typically the back button) abandons the PIN request rather than carrying it to the next screen
    LaunchedEffect(currentScreen) {
        if (showing && (vm.requestedOnScreen != currentScreen)) vm.abandonUnlockDialog()
    }

    // Whatever dismissed the tile, the soft keyboard it opened has to go with it
    var wasShowing by remember { mutableStateOf(false) }
    LaunchedEffect(showing) {
        if (showing) wasShowing = true
        else if (wasShowing)
        {
            wasShowing = false
            focusManager.clearFocus(true)
            ime?.hide()
        }
    }

    if (showing)
    {
        LaunchedEffect(Unit) { withFrameNanos { }; focusRequester.requestFocus() }
        Box(modifier = Modifier.fillMaxWidth().padding(8.dp, 8.dp, 8.dp, 8.dp).wallyTile(wallyAttention).heightIn(0.dp, curSz.dp),
          contentAlignment = Alignment.Center) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Spacer(modifier = Modifier.widthIn(2.dp, 8.dp).weight(0.05f))
                OutlinedButton(
                  onClick = {
                      if (vm.pin.value.length > 0)
                      {
                          vm.attemptUnlock(vm.pin.value)
                          vm.pin.value = ""
                      }
                      else vm.triggerUnlockDialog(false)
                  },
                  modifier = Modifier.testTag("UnlockTileAccept"),
                  colors = ButtonDefaults.outlinedButtonColors().copy(containerColor = Color(0x40FFFFFF), contentColor = Color.White),
                  border = BorderStroke(1.dp, Color.White),
                  content = {
                      Icon(Icons.AutoMirrored.Outlined.Send, contentDescription = i18n(S.accept), tint = Color.White)
                  })
                Spacer(modifier = Modifier.widthIn(2.dp, 8.dp).weight(0.05f))
                Column(
                  modifier = Modifier.weight(1f),
                  horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(style = wallyTileHeader(), text = enterPin)
                    TextField(
                      vm.pin.collectAsState().value,
                      colors = TextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        cursorColor = Color.White,
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.White,
                        unfocusedIndicatorColor = Color.White
                      ),
                      onValueChange = { vm.pin.value = it },
                      textStyle = LocalTextStyle.current.copy(textAlign = TextAlign.Center),
                      modifier = Modifier.testTag("EnterPIN")
                        .focusRequester(focusRequester)
                        .onKeyEvent {
                            if ((it.key == Key.Enter) || (it.key == Key.NumPadEnter))
                            {
                                if (vm.pin.value.length > 0)
                                {
                                    vm.attemptUnlock(vm.pin.value)
                                    vm.pin.value = ""
                                }
                                else vm.triggerUnlockDialog(false)
                                false
                            }
                            else false// Do not accept this key
                        },
                      singleLine = true,
                      keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done, keyboardType = KeyboardType.Number),
                      keyboardActions = KeyboardActions(
                        onDone = {
                            if (vm.pin.value.length > 0)
                            {
                                vm.attemptUnlock(vm.pin.value)
                                vm.pin.value = ""
                            }
                            else vm.triggerUnlockDialog(false)
                        }
                      ),
                    )
                }
                Spacer(modifier = Modifier.widthIn(2.dp, 8.dp).weight(0.02f))
                OutlinedButton(
                  onClick = {
                      vm.pin.value = ""
                      vm.triggerUnlockDialog(false)
                  },
                  colors = ButtonDefaults.outlinedButtonColors().copy(containerColor = Color(0x40FFFFFF), contentColor = Color.White),
                  modifier = Modifier.testTag("UnlockTileCancel"),
                  border = BorderStroke(1.dp, Color.White),
                  content = {
                      Icon(Icons.Outlined.Cancel, contentDescription = i18n(S.cancel), tint = Color.White)
                  })
                Spacer(modifier = Modifier.widthIn(2.dp, 8.dp).weight(0.02f))
            }

        }
    }
}


/**
 * Displays a confirm/dismiss dialog to users with optional confirm/dismiss button text and description
 */
/*
@Composable
fun UnlockView(enterPin: String = i18n(S.EnterPIN))
{
    val pin = remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val ime = LocalSoftwareKeyboardController.current
    val cos = rememberCoroutineScope()

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
                            attemptUnlock(pin.value)
                            false
                        }
                        else false// Do not accept this key
                    }.onGloballyPositioned { focusRequester.requestFocus() }.onFocusChanged {
                              if (it.isFocused||it.isCaptured||it.hasFocus)
                              {
                                  cos.launch { delay(500); ime?.show() }
                              }
                               },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done, keyboardType = KeyboardType.Number),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            focusManager.clearFocus(true)
                            ime?.hide()
                            attemptUnlock(pin.value)
                        }
                    ),
                  )
             },
      confirmButton = {
          DoneButtonOptional( onClick = { attemptUnlock(pin.value) })
      },
      dismissButton = {
          focusManager.clearFocus(true)
          },
      onDismissRequest = {
          focusManager.clearFocus(true)
          triggerUnlockDialog(false)
      },
      )
}

 */