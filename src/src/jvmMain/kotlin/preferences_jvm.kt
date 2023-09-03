package info.bitcoinunlimited.www.wally
import java.util.prefs.Preferences
import org.nexa.libnexakotlin.*

actual val PREF_MODE_PRIVATE:Int = 0

class JavaPrefsEdit(val jprefs:JavaPrefs): PreferencesEdit
{
    val edit = jprefs.db

    override fun putString(key: String, value: String): PreferencesEdit
    {
        edit.put(key, value)
        return this
    }
    override fun putBoolean(key: String, value: Boolean): PreferencesEdit
    {
        edit.putBoolean(key, value)
        return this
    }
    override fun putInt(key: String, value: Int): PreferencesEdit
    {
        edit.putInt(key, value)
        return this
    }
    override fun commit()
    {
        edit.sync()
    }

}

class JavaPrefs(prefDbName: String, mode: Int): info.bitcoinunlimited.www.wally.SharedPreferences
{
    val db = Preferences.userRoot().node(prefDbName)
    override fun edit(): PreferencesEdit = JavaPrefsEdit(this)

    override fun getBoolean(key: String, defaultValue: Boolean): Boolean = db.getBoolean(key, defaultValue)
    override fun getString(key: String, defaultValue: String?): String? = db.get(key, defaultValue)
    override fun getInt(key: String, defaultValue: Int): Int = db.getInt(key, defaultValue)
}

actual fun getSharedPreferences(prefDbName: String, mode: Int): info.bitcoinunlimited.www.wally.SharedPreferences
{
    return JavaPrefs(prefDbName, mode)
}