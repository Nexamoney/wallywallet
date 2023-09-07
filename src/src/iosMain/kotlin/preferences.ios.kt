package info.bitcoinunlimited.www.wally

import platform.Foundation.NSUserDefaults
import platform.darwin.NSInteger

class IosPrefsEdit(val jprefs:IosPrefs): PreferencesEdit
{
    override fun putString(key: String, value: String): PreferencesEdit
    {
        jprefs.db.setObject(value, jprefs.prefPrefix + key)
        return this
    }
    override fun putBoolean(key: String, value: Boolean): PreferencesEdit
    {
        jprefs.db.setBool(value, jprefs.prefPrefix + key)
        return this
    }
    override fun putInt(key: String, value: Int): PreferencesEdit
    {
        jprefs.db.setInteger(value.toLong(), jprefs.prefPrefix + key)
        return this
    }
    override fun commit()
    {
        jprefs.db.synchronize()
    }

}

class IosPrefs(val prefPrefix: String, mode: Int): info.bitcoinunlimited.www.wally.SharedPreferences
{
    // Since IOS does not have preference namespaces, we prefix every key with the db name
    val db = NSUserDefaults.standardUserDefaults

    override fun edit(): PreferencesEdit = IosPrefsEdit(this)

    override fun getBoolean(key: String, defaultValue: Boolean): Boolean
    {
        val k = prefPrefix + key
        if (db.objectForKey(k) == null) return defaultValue
        return db.boolForKey(k)
    }
    override fun getString(key: String, defaultValue: String?): String?
    {
        val k = prefPrefix + key
        if (db.objectForKey(k) == null) return defaultValue
        return db.stringForKey(k)
    }
    override fun getInt(key: String, defaultValue: Int): Int
    {
        val k = prefPrefix + key
        if (db.objectForKey(k) == null) return defaultValue
        return db.integerForKey(k).toInt()
    }
}


actual val PREF_MODE_PRIVATE: Int = 0

actual fun getSharedPreferences(prefDbName: String, mode: Int): SharedPreferences
{
    return IosPrefs(prefDbName, mode)
}