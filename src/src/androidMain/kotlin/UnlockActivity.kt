package info.bitcoinunlimited.www.wally

import android.content.Context
import android.os.Bundle
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import info.bitcoinunlimited.www.wally.databinding.ActivityUnlockBinding
import kotlinx.coroutines.delay

class UnlockActivity : CommonActivity()
{
    private lateinit var ui:ActivityUnlockBinding
    override fun onCreate(savedInstanceState: Bundle?)
    {
        super.onCreate(savedInstanceState)
        ui = ActivityUnlockBinding.inflate(layoutInflater)
        setContentView(ui.root)

        ui.GuiEnterPIN.setOnEditorActionListener({ v: TextView, actionId: Int, event: KeyEvent? ->
            if (actionId == EditorInfo.IME_ACTION_DONE ||
              (event != null &&
                event.action == KeyEvent.ACTION_DOWN &&
                event.keyCode == KeyEvent.KEYCODE_ENTER))
            {
                val imm: InputMethodManager = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.hideSoftInputFromWindow(v.windowToken, 0)
                submitPIN(v.text.toString())

                true
            }
            else false

        })
    }

    fun submitPIN(pin: String)
    {
        val ret = wallyApp!!.unlockAccounts(pin)
        if (ret == 0) wallyApp!!.displayError(R.string.InvalidPIN)
        finish()
    }

    override fun onStart()
    {
        super.onStart()
    }

    override fun onResume()
    {
        super.onResume()
        ui.GuiEnterPIN.requestFocus()
        later {
            delay(200)  // give it time to get focus
            laterUI {
                val imm: InputMethodManager = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.showSoftInput(ui.GuiEnterPIN, InputMethodManager.SHOW_IMPLICIT)
            }
        }
    }
}