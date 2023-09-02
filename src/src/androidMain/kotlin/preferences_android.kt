package info.bitcoinunlimited.www.wally

import android.content.Context
import android.content.ContextWrapper.*
import android.content.SharedPreferences
import org.nexa.libnexakotlin.*

actual val PREF_MODE_PRIVATE:Int = Context.MODE_PRIVATE

class AndroidPrefsEdit(prefs:AndroidPrefs): PreferencesEdit
{
    val edit = prefs.db.edit()

    override fun putString(key: String, value: String):PreferencesEdit
    {
        edit.putString(key, value)
        return this
    }
    override fun putBoolean(key: String, value: Boolean):PreferencesEdit
    {
        edit.putBoolean(key, value)
        return this
    }
    override fun putInt(key: String, value: Int):PreferencesEdit
    {
        edit.putInt(key, value)
        return this
    }
    override fun commit()
    {
        edit.commit()
    }

}

class AndroidPrefs(prefDbName: String, mode: Int): info.bitcoinunlimited.www.wally.SharedPreferences
{
    val db = context!!.getSharedPreferences(prefDbName, mode)
    override fun edit(): PreferencesEdit = AndroidPrefsEdit(this)

    override fun getBoolean(key: String, defaultValue: Boolean): Boolean = db.getBoolean(key, defaultValue)
    override fun getString(key: String, defaultValue: String?): String? = db.getString(key, defaultValue)
    override fun getInt(key: String, defaultValue: Int): Int = db.getInt(key, defaultValue)
}

actual fun getSharedPreferences(prefDbName: String, mode: Int): info.bitcoinunlimited.www.wally.SharedPreferences
{
    return AndroidPrefs(prefDbName, mode)
}