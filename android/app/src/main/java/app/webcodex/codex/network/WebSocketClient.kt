package app.webcodex.codex.network

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicInteger

sealed class WsEvent {
    data class SessionReady(val sessionId: String, val workspace: String, val workspaceName: String) : WsEvent()
    data class AppEvent(val json: JSONObject) : WsEvent()
    data class Stderr(val text: String) : WsEvent()
    data class Error(val message: String) : WsEvent()
    data class SessionClosed(val reason: String) : WsEvent()
}

class WebSocketClient(
    private val okHttp: OkHttpClient
) {
    private var webSocket: WebSocket? = null
    private val rpcSeq = AtomicInteger(0)
    private val pending = mutableMapOf<String, Pair<(Any?) -> Unit, (Throwable) -> Unit>>()
    private val eventChannel = Channel<WsEvent>(Channel.UNLIMITED)
    val events: Flow<WsEvent> = eventChannel.receiveAsFlow()

    fun connect(
        baseUrl: String,
        token: String,
        workspace: String? = null,
        onOpen: () -> Unit,
        onClose: (Int, String?) -> Unit,
        onFailure: (Throwable) -> Unit
    ) {
        val scheme = if (baseUrl.startsWith("https")) "wss" else "ws"
        val url = baseUrl.replace(Regex("^https?"), scheme)
        val wsUrl = buildString {
            append(url)
            if (!url.endsWith("/")) append("/")
            append("ws")
            if (!workspace.isNullOrBlank()) {
                append("?workspace=").append(java.net.URLEncoder.encode(workspace, "UTF-8"))
            }
        }

        val request = Request.Builder()
            .url(wsUrl)
            .header("Authorization", "Bearer $token")
            .build()
        webSocket = okHttp.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                onOpen()
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                try {
                    val obj = JSONObject(text)
                    val type = obj.optString("type", "")
                    when (type) {
                        "session_ready" -> {
                            eventChannel.trySend(WsEvent.SessionReady(
                                obj.optString("sessionId", ""),
                                obj.optString("workspace", ""),
                                obj.optString("workspaceName", "")
                            ))
                        }
                        "event" -> {
                            val data = obj.opt("data")
                            if (data is JSONObject) {
                                if (data.has("id") && (data.has("result") || data.has("error"))) {
                                    val id = data.optString("id", "")
                                    pending.remove(id)?.let { (resolve, reject) ->
                                        if (data.has("result")) resolve(jsonToMap(data.opt("result")))
                                        else reject(Throwable(data.optJSONObject("error")?.optString("message") ?: "RPC error"))
                                    }
                                } else {
                                    eventChannel.trySend(WsEvent.AppEvent(data))
                                }
                            }
                        }
                        "stderr" -> {
                            eventChannel.trySend(WsEvent.Stderr(obj.optString("data", "")))
                        }
                        "error" -> {
                            eventChannel.trySend(WsEvent.Error(obj.optString("error", "Unknown error")))
                        }
                        "session_closed" -> {
                            eventChannel.trySend(WsEvent.SessionClosed(obj.optString("reason", "")))
                        }
                    }
                } catch (_: Exception) {}
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                onMessage(webSocket, bytes.utf8())
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                onClose(code, reason)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                onFailure(t)
            }
        })
    }

    fun sendRpc(method: String, params: Any?, callback: (Any?) -> Unit, onError: (Throwable) -> Unit) {
        val id = (rpcSeq.incrementAndGet()).toString()
        pending[id] = Pair(callback, onError)
        val rpc = JSONObject().apply {
            put("id", id)
            put("method", method)
            put("params", toJsonValue(params) ?: JSONObject())
        }
        val envelope = JSONObject().apply {
            put("type", "rpc")
            put("rpc", rpc)
        }
        webSocket?.send(envelope.toString())
    }

    fun sendNotify(method: String, params: Map<String, Any> = emptyMap()) {
        val rpc = JSONObject().apply {
            put("method", method)
            put("params", toJsonValue(params))
        }
        val envelope = JSONObject().apply {
            put("type", "rpc")
            put("rpc", rpc)
        }
        webSocket?.send(envelope.toString())
    }

    fun sendPermission(requestId: String, decision: String) {
        val response = JSONObject().apply {
            put("id", requestId)
            put("result", JSONObject().apply { put("decision", decision) })
        }
        val envelope = JSONObject().apply {
            put("type", "permission")
            put("response", response)
        }
        webSocket?.send(envelope.toString())
    }

    fun close() {
        webSocket?.send(JSONObject().apply { put("type", "close") }.toString())
        webSocket?.close(1000, "Client close")
        webSocket = null
    }

    fun isOpen(): Boolean = webSocket != null
}

private fun toJsonValue(value: Any?): Any? = when (value) {
    null -> JSONObject.NULL
    is JSONObject, is JSONArray, is String, is Number, is Boolean -> value
    is Map<*, *> -> JSONObject().apply {
        value.forEach { (key, entryValue) ->
            if (key != null) put(key.toString(), toJsonValue(entryValue))
        }
    }
    is Iterable<*> -> JSONArray().apply {
        value.forEach { put(toJsonValue(it)) }
    }
    is Array<*> -> JSONArray().apply {
        value.forEach { put(toJsonValue(it)) }
    }
    else -> value.toString()
}

private fun jsonToMap(obj: Any?): Any? = when (obj) {
    is JSONObject -> obj.keys().asSequence().associateWith { jsonToMap(obj.opt(it)) }
    is JSONArray -> (0 until obj.length()).map { jsonToMap(obj.opt(it)) }
    else -> obj
}
