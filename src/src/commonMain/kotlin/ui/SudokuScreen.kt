package ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import info.bitcoinunlimited.www.wally.CAMOUFLAGE_MEDITATION
import info.bitcoinunlimited.www.wally.CAMOUFLAGE_SUDOKU
import info.bitcoinunlimited.www.wally.SharedPreferences
import info.bitcoinunlimited.www.wally.ui.Camouflage
import info.bitcoinunlimited.www.wally.ui.camouflage
import info.bitcoinunlimited.www.wally.wallyApp

// Placeholder screen for implementing Sudoku camouflage
@Composable fun SudokuScreen()
{
    // TODO: Implement Sudoku camouflage here
    Column(
      modifier = Modifier.fillMaxSize()
    ) {
        Spacer(modifier = Modifier.height(100.dp))
        Text("SudokuScreen")

        Button(
          onClick = {
              val preferenceDB: SharedPreferences = wallyApp!!.preferenceDB
              camouflage.value = Camouflage.Disabled
              preferenceDB.edit().putBoolean(CAMOUFLAGE_SUDOKU, false)
              preferenceDB.edit().putBoolean(CAMOUFLAGE_MEDITATION, false)
          }
        ) {
            Text("Disable Camouflage")
        }
    }
}