package app.webcodex.codex.storage

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "webcodex_settings")

class SettingsStore(private val context: Context) {
    companion object {
        private val MODEL = stringPreferencesKey("model")
        private val EFFORT = stringPreferencesKey("effort")
        private val APPROVAL_POLICY = stringPreferencesKey("approvalPolicy")
        private val SANDBOX = stringPreferencesKey("sandbox")
        private val SERVER_HOST = stringPreferencesKey("serverHost")
        private val SERVER_PORT = stringPreferencesKey("serverPort")
        private val THEME = stringPreferencesKey("themeMode")        // "default","amoled","tokyo-night", etc.
        private val FONT = stringPreferencesKey("fontStyle")          // "current","cursor","anthropic","fira","ibm","dm"
        private val FONT_SIZE = stringPreferencesKey("fontSize")      // 11..30
        private val LINE_HEIGHT = stringPreferencesKey("lineHeight")  // 1.0..2.5
        private val LINE_WIDTH = stringPreferencesKey("lineWidth")    // 400..1200 (max content width dp)
        private val BORDER_RADIUS = stringPreferencesKey("borderRadius")
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { prefs ->
        AppSettings(
            model = prefs[MODEL] ?: "",
            effort = prefs[EFFORT] ?: "",
            approvalPolicy = prefs[APPROVAL_POLICY] ?: "on-request",
            sandbox = prefs[SANDBOX] ?: "workspaceWrite",
            serverHost = prefs[SERVER_HOST] ?: "10.0.2.2",
            serverPort = prefs[SERVER_PORT] ?: "3000",
            theme = prefs[THEME]?.let { v ->
                if (v in listOf("default","amoled","tokyo-night","catppuccin-mocha","catppuccin-latte","dracula","nord","gruvbox","light")) v
                else "default"
            } ?: "default",
            font = prefs[FONT]?.let { v ->
                if (v in listOf("current","cursor","anthropic","fira","ibm","dm")) v else "current"
            } ?: "current",
            fontSize = prefs[FONT_SIZE]?.toFloatOrNull() ?: 14f,
            lineHeight = prefs[LINE_HEIGHT]?.toFloatOrNull() ?: 1.6f,
            lineWidth = prefs[LINE_WIDTH]?.toIntOrNull()?.coerceIn(400, 1200) ?: 720,
            borderRadius = prefs[BORDER_RADIUS]?.toIntOrNull() ?: 8
        )
    }

    suspend fun updateSettings(block: (AppSettings) -> AppSettings) {
        context.dataStore.edit { prefs ->
            val current = AppSettings(
                model = prefs[MODEL] ?: "",
                effort = prefs[EFFORT] ?: "",
                approvalPolicy = prefs[APPROVAL_POLICY] ?: "on-request",
                sandbox = prefs[SANDBOX] ?: "workspaceWrite",
                serverHost = prefs[SERVER_HOST] ?: "10.0.2.2",
                serverPort = prefs[SERVER_PORT] ?: "3000",
                theme = prefs[THEME] ?: "default",
                font = prefs[FONT] ?: "current",
                fontSize = prefs[FONT_SIZE]?.toFloatOrNull() ?: 14f,
                lineHeight = prefs[LINE_HEIGHT]?.toFloatOrNull() ?: 1.6f,
                lineWidth = prefs[LINE_WIDTH]?.toIntOrNull()?.coerceIn(400, 1200) ?: 720,
                borderRadius = prefs[BORDER_RADIUS]?.toIntOrNull() ?: 8
            )
            val updated = block(current)
            prefs[MODEL] = updated.model
            prefs[EFFORT] = updated.effort
            prefs[APPROVAL_POLICY] = updated.approvalPolicy
            prefs[SANDBOX] = updated.sandbox
            prefs[SERVER_HOST] = updated.serverHost
            prefs[SERVER_PORT] = updated.serverPort
            prefs[THEME] = updated.theme
            prefs[FONT] = updated.font
            prefs[FONT_SIZE] = updated.fontSize.toString()
            prefs[LINE_HEIGHT] = updated.lineHeight.toString()
            prefs[LINE_WIDTH] = updated.lineWidth.toString()
            prefs[BORDER_RADIUS] = updated.borderRadius.toString()
        }
    }
}

data class AppSettings(
    val model: String,
    val effort: String,
    val approvalPolicy: String,
    val sandbox: String,
    val serverHost: String,
    val serverPort: String,
    val theme: String = "default",
    val font: String = "current",
    val fontSize: Float = 14f,
    val lineHeight: Float = 1.6f,
    val lineWidth: Int = 720,  // max content width (dp on Android, px on web)
    val borderRadius: Int = 8
)
