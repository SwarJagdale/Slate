package app.webcodex.codex.storage

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import org.json.JSONArray
import org.json.JSONObject

data class CachedSession(
    val workspaceName: String,
    val workspacePath: String?,
    val threadId: String?,
    val messages: List<CachedMessage>,
    val threadList: List<CachedThreadSummary>,
    val workspaces: List<CachedWorkspaceOption>
)

data class CachedMessage(
    val id: String,
    val type: String,
    val content: String,
    val isStreaming: Boolean,
    val metadata: Map<String, Any>
)

data class CachedThreadSummary(
    val id: String,
    val preview: String,
    val cwd: String?,
    val updatedAt: Long
)

data class CachedWorkspaceOption(
    val name: String,
    val path: String
)

data class CachedActiveSession(
    val threadId: String,
    val preview: String,
    val workspacePath: String?,
    val workspaceName: String?,
    val turnRunning: Boolean,
    val lastActivityAt: Long
)

class SessionCacheStore(private val context: Context) {
    companion object {
        private const val KEY_SNAPSHOT = "snapshot"
        private const val KEY_ACTIVE_SESSIONS = "active_sessions"
    }

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs = EncryptedSharedPreferences.create(
        context,
        "webcodex_session_cache",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    suspend fun read(): CachedSession? {
        val raw = prefs.getString(KEY_SNAPSHOT, null) ?: return null
        return runCatching { decode(raw) }.getOrNull()
    }

    suspend fun write(session: CachedSession) {
        prefs.edit().putString(KEY_SNAPSHOT, encode(session)).apply()
    }

    private fun encode(session: CachedSession): String {
        val json = JSONObject()
            .put("workspaceName", session.workspaceName)
            .put("workspacePath", session.workspacePath)
            .put("threadId", session.threadId)
            .put(
                "messages",
                JSONArray().apply {
                    session.messages.forEach { message ->
                        put(
                            JSONObject()
                                .put("id", message.id)
                                .put("type", message.type)
                                .put("content", message.content)
                                .put("isStreaming", message.isStreaming)
                                .put("metadata", JSONObject().apply {
                                    message.metadata.forEach { (key, value) ->
                                        put(key, value)
                                    }
                                })
                        )
                    }
                }
            )
            .put(
                "threadList",
                JSONArray().apply {
                    session.threadList.forEach { thread ->
                        put(
                            JSONObject()
                                .put("id", thread.id)
                                .put("preview", thread.preview)
                                .put("cwd", thread.cwd)
                                .put("updatedAt", thread.updatedAt)
                        )
                    }
                }
            )
            .put(
                "workspaces",
                JSONArray().apply {
                    session.workspaces.forEach { workspace ->
                        put(
                            JSONObject()
                                .put("name", workspace.name)
                                .put("path", workspace.path)
                        )
                    }
                }
            )
        return json.toString()
    }

    private fun decode(raw: String): CachedSession {
        val json = JSONObject(raw)
        return CachedSession(
            workspaceName = json.optString("workspaceName", ""),
            workspacePath = json.readNullableString("workspacePath"),
            threadId = json.readNullableString("threadId"),
            messages = json.optJSONArray("messages").toMessageList(),
            threadList = json.optJSONArray("threadList").toThreadList(),
            workspaces = json.optJSONArray("workspaces").toWorkspaceList()
        )
    }

    private fun JSONArray?.toMessageList(): List<CachedMessage> {
        if (this == null) return emptyList()
        return (0 until length()).mapNotNull { index ->
            val item = optJSONObject(index) ?: return@mapNotNull null
            val metadataJson = item.optJSONObject("metadata") ?: JSONObject()
            val metadata = buildMap {
                val keys = metadataJson.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    val value = metadataJson.opt(key) ?: continue
                    if (value != JSONObject.NULL) {
                        put(key, value)
                    }
                }
            }
            CachedMessage(
                id = item.optString("id", ""),
                type = item.optString("type", ""),
                content = item.optString("content", ""),
                isStreaming = item.optBoolean("isStreaming", false),
                metadata = metadata
            )
        }
    }

    private fun JSONArray?.toThreadList(): List<CachedThreadSummary> {
        if (this == null) return emptyList()
        return (0 until length()).mapNotNull { index ->
            val item = optJSONObject(index) ?: return@mapNotNull null
            CachedThreadSummary(
                id = item.optString("id", ""),
                preview = item.optString("preview", ""),
                cwd = item.readNullableString("cwd"),
                updatedAt = item.optLong("updatedAt", 0L)
            )
        }
    }

    private fun JSONArray?.toWorkspaceList(): List<CachedWorkspaceOption> {
        if (this == null) return emptyList()
        return (0 until length()).mapNotNull { index ->
            val item = optJSONObject(index) ?: return@mapNotNull null
            CachedWorkspaceOption(
                name = item.optString("name", ""),
                path = item.optString("path", "")
            )
        }
    }

    private fun JSONObject.readNullableString(key: String): String? {
        if (isNull(key)) return null
        return optString(key).ifBlank { null }
    }

    // ═══════════════════════════════════════════════════════════════
    // Active sessions persistence
    // ═══════════════════════════════════════════════════════════════

    suspend fun readActiveSessions(): List<CachedActiveSession> {
        val raw = prefs.getString(KEY_ACTIVE_SESSIONS, null) ?: return emptyList()
        return runCatching { decodeActiveSessions(raw) }.getOrDefault(emptyList())
    }

    suspend fun writeActiveSessions(sessions: List<CachedActiveSession>) {
        prefs.edit().putString(KEY_ACTIVE_SESSIONS, encodeActiveSessions(sessions)).apply()
    }

    private fun encodeActiveSessions(sessions: List<CachedActiveSession>): String {
        return JSONArray().apply {
            sessions.forEach { s ->
                put(JSONObject()
                    .put("threadId", s.threadId)
                    .put("preview", s.preview)
                    .put("workspacePath", s.workspacePath)
                    .put("workspaceName", s.workspaceName)
                    .put("turnRunning", s.turnRunning)
                    .put("lastActivityAt", s.lastActivityAt)
                )
            }
        }.toString()
    }

    private fun decodeActiveSessions(raw: String): List<CachedActiveSession> {
        val arr = JSONArray(raw)
        return (0 until arr.length()).mapNotNull { i ->
            val obj = arr.optJSONObject(i) ?: return@mapNotNull null
            CachedActiveSession(
                threadId = obj.optString("threadId", ""),
                preview = obj.optString("preview", ""),
                workspacePath = obj.readNullableString("workspacePath"),
                workspaceName = obj.readNullableString("workspaceName"),
                turnRunning = obj.optBoolean("turnRunning", false),
                lastActivityAt = obj.optLong("lastActivityAt", 0L)
            )
        }
    }
}
