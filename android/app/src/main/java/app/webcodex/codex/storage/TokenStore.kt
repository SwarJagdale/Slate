package app.webcodex.codex.storage

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class TokenStore(context: Context) {
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs = EncryptedSharedPreferences.create(
        context,
        "codex_secure",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    var token: String?
        get() = prefs.getString(KEY_TOKEN, null)
        set(value) {
            prefs.edit().putString(KEY_TOKEN, value).apply()
        }

    var rememberToken: Boolean
        get() = prefs.getBoolean(KEY_REMEMBER, false)
        set(value) {
            prefs.edit().putBoolean(KEY_REMEMBER, value).apply()
        }

    companion object {
        private const val KEY_TOKEN = "token"
        private const val KEY_REMEMBER = "remember_token"
    }
}
