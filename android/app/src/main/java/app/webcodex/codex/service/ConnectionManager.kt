package app.webcodex.codex.service

import app.webcodex.codex.data.createWebSocketOkHttp
import app.webcodex.codex.network.WsEvent
import app.webcodex.codex.network.WebSocketClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Singleton that owns the WebSocket connection and exposes connection state.
 * Used by CodexConnectionService (keeps process alive) and CodexViewModel (UI).
 */
object ConnectionManager {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val okHttp = createWebSocketOkHttp()
    private val wsClient = WebSocketClient(okHttp)

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    val events = wsClient.events
    val isOpen: Boolean get() = wsClient.isOpen()

    private var currentSessionId: String? = null
    private var reconnectParams: ReconnectParams? = null
    private var isExplicitClose = false

    data class ReconnectParams(
        val baseUrl: String,
        val token: String,
        val workspace: String?,
        val sessionId: String
    )

    sealed class ConnectionState {
        data object Disconnected : ConnectionState()
        data object Connecting : ConnectionState()
        data object Initializing : ConnectionState()
        data object Ready : ConnectionState()
        data object Working : ConnectionState()
        data object Reconnecting : ConnectionState()
        data class Error(val message: String) : ConnectionState()
    }

    fun connect(
        baseUrl: String,
        token: String,
        workspace: String? = null,
        sessionId: String? = null
    ) {
        isExplicitClose = false
        currentSessionId = null

        _connectionState.value = ConnectionState.Connecting

        wsClient.connect(
            baseUrl = baseUrl,
            token = token,
            workspace = workspace,
            sessionId = sessionId,
            onOpen = { _connectionState.value = ConnectionState.Initializing },
            onClose = { code, reason ->
                wsClient.clearSocketReference()
                if (isExplicitClose) {
                    _connectionState.value = ConnectionState.Disconnected
                    return@connect
                }
                val params = reconnectParams
                if (params != null) {
                    _connectionState.value = ConnectionState.Reconnecting
                    scope.launch {
                        delay(reconnectDelayMs)
                        attemptReconnect(params)
                    }
                } else {
                    _connectionState.value = ConnectionState.Error(
                        if (code != 1000) "Disconnected: $reason" else "Connection closed"
                    )
                }
            },
            onFailure = { t ->
                wsClient.clearSocketReference()
                if (isExplicitClose) {
                    _connectionState.value = ConnectionState.Disconnected
                    return@connect
                }
                val params = reconnectParams
                if (params != null) {
                    _connectionState.value = ConnectionState.Reconnecting
                    scope.launch {
                        delay(reconnectDelayMs)
                        attemptReconnect(params)
                    }
                } else {
                    _connectionState.value = ConnectionState.Error(t.message ?: "Connection failed")
                }
            }
        )
    }

    private var reconnectAttempts = 0
    private val maxReconnectAttempts = 20
    private val reconnectDelayMs = 2000L

    private suspend fun attemptReconnect(params: ReconnectParams) {
        if (isExplicitClose) return
        reconnectAttempts++
        if (reconnectAttempts > maxReconnectAttempts) {
            _connectionState.value = ConnectionState.Error("Reconnect failed after $maxReconnectAttempts attempts")
            reconnectParams = null
            return
        }
        withContext(Dispatchers.Main) {
            connect(
                baseUrl = params.baseUrl,
                token = params.token,
                workspace = params.workspace,
                sessionId = params.sessionId
            )
        }
    }

    fun setReconnectParams(baseUrl: String, token: String, workspace: String?, sessionId: String) {
        reconnectParams = ReconnectParams(baseUrl, token, workspace, sessionId)
        currentSessionId = sessionId
        reconnectAttempts = 0
    }

    fun onSessionReady(sessionId: String) {
        currentSessionId = sessionId
        reconnectAttempts = 0
    }

    fun updateStatus(status: ConnectionState) {
        if (_connectionState.value !is ConnectionState.Error) {
            _connectionState.value = status
        }
    }

    fun disconnect() {
        isExplicitClose = true
        reconnectParams = null
        currentSessionId = null
        reconnectAttempts = 0
        wsClient.close()
        _connectionState.value = ConnectionState.Disconnected
    }

    fun sendRpc(method: String, params: Map<String, Any>, callback: (Any?) -> Unit, onError: (Throwable) -> Unit) {
        wsClient.sendRpc(method, params, callback, onError)
    }

    fun sendNotify(method: String, params: Map<String, Any> = emptyMap()) {
        wsClient.sendNotify(method, params)
    }

    fun sendPermission(requestId: String, decision: String) {
        wsClient.sendPermission(requestId, decision)
    }
}
