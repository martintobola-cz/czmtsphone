package cz.mts.phone.recorder

import android.content.Context

/**
 * Uložení Shizuku auth klíče (z bezdrátového ladění) do SharedPreferences.
 * Zavolej setAuthKey() odněkud ze svého Settings UI; getAuthKey() se pak
 * použije automaticky v CallRecordingManager při každém startu nahrávání.
 */
object ShizukuAuthKeyStore {
    private const val PREFS_NAME = "shizuku_recorder_prefs"
    private const val KEY_AUTH = "shizuku_auth_key"

    fun getAuthKey(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_AUTH, "") ?: ""
    }

    fun setAuthKey(context: Context, key: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_AUTH, key).apply()
    }
}
