package info.bitcoinunlimited.www.wally

expect val PREF_MODE_PRIVATE:Int

interface PreferencesEdit
{
    fun putString(key:String, value: String): PreferencesEdit
    fun putBoolean(key:String, value: Boolean): PreferencesEdit
    fun putInt(key:String, value: Int): PreferencesEdit

    fun commit()
}
interface SharedPreferences
{
    fun edit(): PreferencesEdit

    fun getBoolean(key:String, defaultValue: Boolean) : Boolean
    fun getString(key: String, defaultValue: String?) : String?
    fun getInt(key: String, defaultValue: Int) : Int
}

expect fun getSharedPreferences(prefDbName: String, mode: Int): SharedPreferences