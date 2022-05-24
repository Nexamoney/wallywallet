package info.bitcoinunlimited.www.wally

import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.view.Menu
import android.view.MenuInflater
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.ShareActionProvider
import androidx.core.view.MenuItemCompat
import androidx.preference.EditTextPreference
import androidx.preference.PreferenceFragmentCompat
import java.util.logging.Logger

private val LogIt = Logger.getLogger("BU.wally.IdentityActivity")

class IdentitySettings : AppCompatActivity()
{
    override fun onCreate(savedInstanceState: Bundle?)
    {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.settings_blank_activity)
        supportFragmentManager
          .beginTransaction()
          .replace(R.id.settings, SettingsFragment())
          .commit()
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

    }

    class SettingsFragment : PreferenceFragmentCompat()
    {
        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?)
        {
            setPreferencesFromResource(R.xml.identity_preferences, rootKey)

            val p: EditTextPreference? = findPreference("sm")

            p?.setOnBindEditTextListener { editText ->
                //editText.inputType = InputType.TYPE_CLASS_NUMBER

                editText.setHint(R.string.SocialMediaSummary)
            }

        }
    }
}