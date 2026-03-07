package app.webcodex.codex.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.viewModelScope
import app.webcodex.codex.data.CodexRepository
import app.webcodex.codex.data.createOkHttp
import app.webcodex.codex.data.createRetrofit
import app.webcodex.codex.network.ApiService
import app.webcodex.codex.network.WsEvent
import app.webcodex.codex.service.ConnectionManager
import app.webcodex.codex.service.CodexConnectionService
import app.webcodex.codex.commands.SlashCommandHandler
import app.webcodex.codex.storage.AppSettings
import app.webcodex.codex.storage.CachedMessage
import app.webcodex.codex.storage.CachedSession
import app.webcodex.codex.storage.CachedThreadSummary
import app.webcodex.codex.storage.CachedWorkspaceOption
import app.webcodex.codex.storage.SessionCacheStore
import app.webcodex.codex.storage.SettingsStore
import app.webcodex.codex.storage.TokenStore
import app.webcodex.codex.storage.CachedActiveSession
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONObject
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.snapshotFlow

data class ChatMessage(
    val id: String,
    val type: MessageType,
    val content: String,
    val isStreaming: Boolean = false,
    val metadata: Map<String, Any> = emptyMap()
)

enum class MessageType {
    USER, AGENT, SYSTEM, ERROR, STDERR, PERMISSION, EXEC, TOOL, FILE, PLAN, THINKING
}

data class CodexUiState(
    val isConnected: Boolean = false,
    val connectionStatus: String = "disconnected",
    val serverHost: String = "10.0.2.2",
    val serverPort: String = "3000",
    val token: String = "",
    val workspacePath: String? = null,
    val workspaceName: String = "",
    val threadId: String? = null,
    val activeTurnId: String? = null,
    val messages: List<ChatMessage> = emptyList(),
    val pendingQueue: List<String> = emptyList(),
    val threadList: List<ThreadSummary> = emptyList(),
    val workspaces: List<WorkspaceOption> = emptyList(),
    val models: List<ModelOption> = emptyList(),
    val settings: AppSettings = AppSettings("", "", "on-request", "workspaceWrite", "10.0.2.2", "3000"),
    val tokenUsage: TokenUsage? = null,
    val rateLimits: Map<String, RateLimitInfo> = emptyMap(),
    val error: String? = null,
    val showSettings: Boolean = false,
    val showHistory: Boolean = false,
    val showNewChatModal: Boolean = false,
    val historyIncludeArchived: Boolean = false,
    val historyWorkspaceFilter: String? = null,
    val historySearchQuery: String = "",
    val itemTexts: Map<String, String> = emptyMap(),
    val hasOfflineCache: Boolean = false,
    val preferOfflineHome: Boolean = false,
    val showActiveSessionsOverlay: Boolean = false
)

data class ThreadSummary(val id: String, val preview: String, val cwd: String?, val updatedAt: Long)
data class WorkspaceOption(val name: String, val path: String)
data class ModelOption(val value: String, val label: String)
data class TokenUsage(val inputTokens: Int, val cachedInputTokens: Int, val outputTokens: Int, val reasoningOutputTokens: Int)
data class RateLimitInfo(val usedPercent: Double, val resetsAt: Double?)

data class ActiveSession(
    val threadId: String,
    val preview: String,
    val workspacePath: String?,
    val workspaceName: String?,
    val turnRunning: Boolean = false,
    val lastActivityAt: Long = System.currentTimeMillis()
)

class CodexViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application
    private val tokenStore = TokenStore(application)
    private val settingsStore = SettingsStore(application)
    private val sessionCacheStore = SessionCacheStore(application)
    private val okHttp = createOkHttp()
    private val api: ApiService = createRetrofit(okHttp).create(ApiService::class.java)
    private val repository = CodexRepository(api, okHttp)
    private var autoConnectAttempted = false

    private val _uiState = MutableStateFlow(CodexUiState())
    val uiState: StateFlow<CodexUiState> = _uiState.asStateFlow()

    val activeSessions = mutableStateMapOf<String, ActiveSession>()
    val sessionMessageCache = mutableMapOf<String, List<ChatMessage>>()

    init {
        viewModelScope.launch {
            sessionCacheStore.read()?.let(::restoreCachedSession)
            uiState
                .map { it.toCachedSession() }
                .debounce(500)
                .distinctUntilChanged()
                .collect { snapshot ->
                    if (snapshot != null) {
                        sessionCacheStore.write(snapshot)
                    }
                }
        }
        // Restore and auto-save active sessions
        viewModelScope.launch {
            sessionCacheStore.readActiveSessions().forEach { cached ->
                activeSessions[cached.threadId] = ActiveSession(
                    threadId = cached.threadId,
                    preview = cached.preview,
                    workspacePath = cached.workspacePath,
                    workspaceName = cached.workspaceName,
                    turnRunning = false, // never restore as running
                    lastActivityAt = cached.lastActivityAt
                )
            }
            snapshotFlow { activeSessions.toMap() }
                .debounce(1000)
                .distinctUntilChanged()
                .collect { map ->
                    sessionCacheStore.writeActiveSessions(map.values.map { s ->
                        CachedActiveSession(
                            threadId = s.threadId,
                            preview = s.preview,
                            workspacePath = s.workspacePath,
                            workspaceName = s.workspaceName,
                            turnRunning = s.turnRunning,
                            lastActivityAt = s.lastActivityAt
                        )
                    })
                }
        }
        viewModelScope.launch {
            settingsStore.settings.collect { s ->
                _uiState.update { it.copy(settings = s, serverHost = s.serverHost, serverPort = s.serverPort) }
                maybeAutoConnect(s)
            }
        }
        viewModelScope.launch {
            ConnectionManager.connectionState.collect { state ->
                syncConnectionState(state)
            }
        }
        viewModelScope.launch {
            ConnectionManager.events.collect { event ->
                when (event) {
                    is WsEvent.SessionReady -> onSessionReady(event)
                    is WsEvent.AppEvent -> onAppEvent(event.json)
                    is WsEvent.Stderr -> addStderr(event.text)
                    is WsEvent.Error -> addError(event.message)
                    is WsEvent.SessionClosed -> addSystem("Session closed: ${event.reason}")
                }
            }
        }
    }

    private fun syncConnectionState(state: ConnectionManager.ConnectionState) {
        when (state) {
            is ConnectionManager.ConnectionState.Error, is ConnectionManager.ConnectionState.Disconnected -> {
                if (state is ConnectionManager.ConnectionState.Error) {
                    transitionToOffline(state.message)
                } else {
                    transitionToOffline()
                }
                return
            }
            else -> { /* continue to update */ }
        }
        _uiState.update { current ->
            val (isConnected, connectionStatus, error) = when (state) {
                is ConnectionManager.ConnectionState.Connecting, is ConnectionManager.ConnectionState.Reconnecting ->
                    Triple(current.isConnected, "connecting", null)
                is ConnectionManager.ConnectionState.Initializing ->
                    Triple(true, "initializing", null)
                is ConnectionManager.ConnectionState.Ready ->
                    Triple(true, if (current.connectionStatus == "resuming") "resuming" else "ready", null)
                is ConnectionManager.ConnectionState.Working ->
                    Triple(true, "working", null)
                else -> return@update current
            }
            current.copy(
                isConnected = isConnected,
                connectionStatus = connectionStatus,
                error = error,
                hasOfflineCache = current.messages.isNotEmpty() || current.hasOfflineCache,
                preferOfflineHome = if (isConnected || current.hasOfflineCache) true else current.preferOfflineHome
            )
        }
    }

    private fun maybeAutoConnect(settings: AppSettings) {
        if (autoConnectAttempted || ConnectionManager.isOpen) return
        autoConnectAttempted = true

        val savedToken = tokenStore.token?.trim().orEmpty()
        val shouldAutoConnect = tokenStore.rememberToken &&
            savedToken.isNotBlank() &&
            settings.serverHost.isNotBlank() &&
            settings.serverPort.isNotBlank()

        if (!shouldAutoConnect) return

        _uiState.update { it.copy(token = savedToken) }
        connect(
            host = settings.serverHost,
            port = settings.serverPort,
            token = savedToken,
            workspace = _uiState.value.workspacePath,
            rememberToken = true
        )
    }

    private fun onSessionReady(e: WsEvent.SessionReady) {
        val state = _uiState.value
        ConnectionManager.setReconnectParams(
            baseUrl = "http://${state.serverHost}:${state.serverPort}",
            token = state.token.ifBlank { tokenStore.token ?: "" },
            workspace = state.workspacePath,
            sessionId = e.sessionId
        )
        ConnectionManager.onSessionReady(e.sessionId)
        _uiState.update {
            it.copy(
                isConnected = true,
                connectionStatus = "initializing",
                workspaceName = e.workspaceName,
                error = null,
                preferOfflineHome = true
            )
        }
        CodexConnectionService.start(app)
        if (state.threadId != null) {
            reattachSession(e.workspaceName)
        } else {
            initSession(e.workspaceName)
        }
    }

    private fun reattachSession(workspaceName: String) {
        val threadId = _uiState.value.threadId ?: run {
            initSession(workspaceName)
            return
        }
        viewModelScope.launch {
            rpc("initialize", mapOf(
                "clientInfo" to mapOf("name" to "cortex", "title" to "Cortex", "version" to "0.2.0"),
                "capabilities" to mapOf("experimentalApi" to false)
            )) { _ ->
                notify("initialized", emptyMap())
                val s = _uiState.value.settings
                val params = mutableMapOf<String, Any>("threadId" to threadId)
                if (s.model.isNotEmpty()) params["model"] = s.model
                params["approvalPolicy"] = s.approvalPolicy
                params["sandbox"] = when (s.sandbox) {
                    "workspaceWrite" -> "workspace-write"
                    "readOnly" -> "read-only"
                    "dangerFullAccess" -> "danger-full-access"
                    else -> "workspace-write"
                }
                rpc("thread/resume", params) { res ->
                    val thread = (res as? Map<*, *>)?.get("thread") as? Map<*, *>
                    val tid = thread?.get("id") as? String ?: threadId
                    ConnectionManager.updateStatus(ConnectionManager.ConnectionState.Ready)
                    CodexConnectionService.updateReady(app)
                    _uiState.update {
                        it.copy(
                            threadId = tid,
                            connectionStatus = "ready",
                            workspaceName = workspaceName
                        )
                    }
                    upsertActiveSession(tid, "Reconnected", _uiState.value.workspacePath, workspaceName)
                    rpc("thread/read", mapOf("threadId" to tid, "includeTurns" to true)) { readRes ->
                        val t = (readRes as? Map<*, *>)?.get("thread") as? Map<*, *>
                        val turns = t?.get("turns") as? List<*>
                        val msgs = mutableListOf<ChatMessage>()
                        turns?.forEach { turn ->
                            val items = (turn as? Map<*, *>)?.get("items") as? List<*>
                            items?.forEach { item ->
                                val m = item as? Map<*, *> ?: return@forEach
                                val type = m["type"] as? String ?: return@forEach
                                when (type) {
                                    "userMessage" -> {
                                        val content = (m["content"] as? List<*>)?.mapNotNull { c ->
                                            (c as? Map<*, *>)?.takeIf { it["type"] == "text" }?.get("text") as? String
                                        }?.joinToString("\n") ?: ""
                                        msgs.add(ChatMessage("u-${m["id"]}", MessageType.USER, content))
                                    }
                                    "agentMessage" -> {
                                        val text = m["text"] as? String ?: ""
                                        msgs.add(ChatMessage("a-${m["id"]}", MessageType.AGENT, text))
                                    }
                                    "reasoning" -> {
                                        val body = (m["content"] as? List<*>)?.joinToString("\n") { it.toString() } ?: ""
                                        msgs.add(ChatMessage("t-${m["id"]}", MessageType.THINKING, body))
                                    }
                                    "commandExecution" -> {
                                        val cmd = m["command"] as? String ?: ""
                                        msgs.add(ChatMessage("ex-${m["id"]}", MessageType.EXEC, "", false, mapOf("command" to cmd)))
                                    }
                                    "fileChange" -> msgs.add(ChatMessage("f-${m["id"]}", MessageType.FILE, "", false, mapOf("item" to (m.toString()))))
                                    "mcpToolCall" -> msgs.add(ChatMessage("m-${m["id"]}", MessageType.TOOL, m["tool"] as? String ?: ""))
                                }
                            }
                        }
                        _uiState.update { it.copy(messages = msgs) }
                        addSystem("Reconnected")
                    }
                    loadModels()
                    loadThreadList()
                }
            }
        }
    }

    private fun initSession(workspaceName: String) {
        viewModelScope.launch {
            rpc("initialize", mapOf(
                "clientInfo" to mapOf("name" to "cortex", "title" to "Cortex", "version" to "0.2.0"),
                "capabilities" to mapOf("experimentalApi" to false)
            )) { result ->
                notify("initialized", emptyMap())
                val s = _uiState.value.settings
                val threadParams = mutableMapOf<String, Any>()
                if (s.model.isNotEmpty()) threadParams["model"] = s.model
                threadParams["approvalPolicy"] = s.approvalPolicy
                threadParams["sandbox"] = when (s.sandbox) {
                    "workspaceWrite" -> "workspace-write"
                    "readOnly" -> "read-only"
                    "dangerFullAccess" -> "danger-full-access"
                    else -> "workspace-write"
                }
                rpc("thread/start", threadParams) { tr ->
                    val thread = (tr as? Map<*, *>)?.get("thread") as? Map<*, *>
                    val tid = thread?.get("id") as? String
                    ConnectionManager.updateStatus(ConnectionManager.ConnectionState.Ready)
                    CodexConnectionService.updateReady(app)
                    _uiState.update {
                        it.copy(
                            threadId = tid,
                            connectionStatus = "ready",
                            workspaceName = workspaceName,
                            messages = emptyList(),
                            itemTexts = emptyMap(),
                            pendingQueue = emptyList(),
                            tokenUsage = null,
                            rateLimits = emptyMap()
                        )
                    }
                    if (tid != null) upsertActiveSession(tid, "", _uiState.value.workspacePath, workspaceName)
                    addSystem("Cortex · $workspaceName")
                    loadModels()
                    loadThreadList()
                }
            }
        }
    }

    private fun onAppEvent(json: JSONObject) {
        if (json.has("method") && json.has("id")) {
            // Permission request — only show for current thread
            addPermissionCard(json.getString("id"), json.getString("method"), json.optJSONObject("params") ?: JSONObject())
            return
        }
        val method = json.optString("method", "")
        val params = json.optJSONObject("params") ?: JSONObject()
        val eventThreadId = params.optString("threadId", "").takeIf { it.isNotEmpty() }
        val currentThreadId = _uiState.value.threadId
        val isCurrentThread = eventThreadId == null || eventThreadId == currentThreadId

        when (method) {
            "turn/started" -> {
                val turn = params.optJSONObject("turn")
                val turnId = turn?.optString("id")
                // Update active session metadata for any thread
                if (eventThreadId != null) markSessionRunning(eventThreadId)
                // Only update global UI state for current thread
                if (isCurrentThread) {
                    ConnectionManager.updateStatus(ConnectionManager.ConnectionState.Working)
                    CodexConnectionService.updateWorking(app)
                    _uiState.update { it.copy(activeTurnId = turnId, connectionStatus = "working") }
                }
            }
            "turn/completed" -> {
                if (ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED).not()) {
                    CodexConnectionService.showTurnComplete(app)
                }
                // Update active session metadata for any thread
                if (eventThreadId != null) markSessionIdle(eventThreadId)
                // Only update global UI state for current thread
                if (isCurrentThread) {
                    ConnectionManager.updateStatus(ConnectionManager.ConnectionState.Ready)
                    CodexConnectionService.updateReady(app)
                    _uiState.update { it.copy(activeTurnId = null, connectionStatus = "ready") }
                    processQueue()
                }
            }
            "error" -> {
                if (eventThreadId != null) markSessionIdle(eventThreadId)
                if (isCurrentThread) {
                    addError(params.optJSONObject("error")?.optString("message") ?: "Unknown error")
                    _uiState.update { it.copy(activeTurnId = null, connectionStatus = "error") }
                }
            }
            "item/started" -> {
                if (eventThreadId != null) updateSessionActivity(eventThreadId)
                if (!isCurrentThread) return // Transcript guard
                val item = params.optJSONObject("item") ?: return
                val type = item.optString("type")
                val id = item.optString("id")
                when (type) {
                    "reasoning" -> addThinkingItem(id)
                    "agentMessage" -> addAgentTextItem(id)
                    "commandExecution" -> addExecItem(id, item.optString("command", ""))
                    "fileChange" -> addFileChangeItem(id, item)
                    "mcpToolCall" -> addMcpToolItem(id, item)
                }
            }
            "item/completed" -> {
                if (eventThreadId != null) updateSessionActivity(eventThreadId)
                if (!isCurrentThread) return // Transcript guard
                val item = params.optJSONObject("item") ?: return
                val type = item.optString("type")
                val id = item.optString("id")
                when (type) {
                    "reasoning" -> collapseThinking(id)
                    "agentMessage" -> finalizeAgent(id)
                    "commandExecution" -> finalizeExec(id, item)
                    "mcpToolCall" -> finalizeMcpTool(id, item)
                }
            }
            "item/agentMessage/delta" -> {
                if (eventThreadId != null) updateSessionActivity(eventThreadId)
                if (!isCurrentThread) return
                addAgentDelta(params.optString("itemId"), params.optString("delta", ""))
            }
            "item/reasoning/textDelta", "item/reasoning/summaryTextDelta" -> {
                if (eventThreadId != null) updateSessionActivity(eventThreadId)
                if (!isCurrentThread) return
                addThinkingDelta(params.optString("itemId"), params.optString("delta", ""))
            }
            "item/commandExecution/outputDelta" -> {
                if (eventThreadId != null) updateSessionActivity(eventThreadId)
                if (!isCurrentThread) return
                appendExecOutput(params.optString("itemId"), params.optString("delta", ""))
            }
            "thread/tokenUsage/updated" -> {
                if (!isCurrentThread) return
                val usage = params.optJSONObject("tokenUsage")?.optJSONObject("last")
                if (usage != null) {
                    _uiState.update {
                        it.copy(tokenUsage = TokenUsage(
                            usage.optInt("inputTokens", 0),
                            usage.optInt("cachedInputTokens", 0),
                            usage.optInt("outputTokens", 0),
                            usage.optInt("reasoningOutputTokens", 0)
                        ))
                    }
                }
            }
            "account/rateLimits/updated" -> {
                val rl = params.optJSONObject("rateLimits")
                if (rl != null && rl.has("limitId")) {
                    val id = rl.optString("limitId")
                    val primary = rl.optJSONObject("primary")
                    val usedPct = primary?.optDouble("usedPercent", 0.0) ?: 0.0
                    val resetsAt = primary?.optDouble("resetsAt")
                    _uiState.update {
                        it.copy(rateLimits = it.rateLimits + (id to RateLimitInfo(usedPct, resetsAt)))
                    }
                }
            }
        }
    }

    fun connect(host: String, port: String, token: String, workspace: String?, rememberToken: Boolean) {
        val baseUrl = "http://$host:$port"
        if (rememberToken) {
            tokenStore.token = token
            tokenStore.rememberToken = true
        }
        viewModelScope.launch {
            settingsStore.updateSettings { it.copy(serverHost = host, serverPort = port) }
        }
        _uiState.update { it.copy(connectionStatus = "connecting", error = null, preferOfflineHome = true) }
        ConnectionManager.connect(baseUrl = baseUrl, token = token, workspace = workspace, sessionId = null)
    }

    fun disconnect() {
        CodexConnectionService.stop(app)
        ConnectionManager.disconnect()
        transitionToOffline()
    }

    private fun loadModels() {
        rpc("model/list", mapOf("limit" to 50, "includeHidden" to false)) { res ->
            val data = (res as? Map<*, *>)?.get("data") as? List<*>
            val models = data.orEmpty().mapNotNull { entry ->
                val model = entry as? Map<*, *> ?: return@mapNotNull null
                val value = (model["model"] as? String) ?: (model["id"] as? String) ?: return@mapNotNull null
                val baseLabel = (model["displayName"] as? String) ?: value
                val label = if (model["isDefault"] == true) "$baseLabel ★" else baseLabel
                ModelOption(value, label)
            }
            if (models.isNotEmpty()) {
                _uiState.update { it.copy(models = models) }
            }
        }
    }

    fun loadWorkspaces(token: String, host: String? = null, port: String? = null) {
        viewModelScope.launch {
            val h = host ?: _uiState.value.serverHost
            val p = port ?: _uiState.value.serverPort
            val baseUrl = "http://$h:$p"
            repository.getWorkspaces(baseUrl, token).onSuccess { res ->
                val list = listOf(WorkspaceOption("${res.base.name}/ (root)", res.base.path)) +
                    res.dirs.map { WorkspaceOption("${it.name}/", it.path) }
                _uiState.update { it.copy(workspaces = list) }
            }
        }
    }

    fun sendMessage(text: String): Boolean {
        val state = _uiState.value
        if (text.isBlank()) return false
        val parsed = SlashCommandHandler.parse(text)
        if (parsed != null) return executeSlashCommand(parsed)
        if (!state.isConnected || state.threadId == null) return false
        if (state.activeTurnId != null) {
            _uiState.update { it.copy(pendingQueue = it.pendingQueue + text) }
            return true
        }
        addUserMessage(text)
        // Update active session preview with first message if preview is empty
        val tid = state.threadId
        if (tid != null) {
            val session = activeSessions[tid]
            if (session != null && session.preview.isEmpty()) {
                activeSessions[tid] = session.copy(preview = text.take(80), lastActivityAt = System.currentTimeMillis())
            }
        }
        dispatchTurn(text)
        return true
    }

    private fun executeSlashCommand(parsed: app.webcodex.codex.commands.ParsedCommand): Boolean {
        val (cmd, args) = parsed
        val state = _uiState.value
        when (cmd) {
            "new" -> { startNewThread(); return true }
            "threads" -> { toggleHistory(); return true }
            "resume" -> {
                val id = args.getOrNull(0)
                if (id != null) resumeThread(id) else toggleHistory()
                return true
            }
            "interrupt" -> { interrupt(); return true }
            "archive" -> {
                val tid = state.threadId ?: run { addError("No active thread to archive"); return true }
                rpc("thread/archive", mapOf("threadId" to tid)) { addSystem("Thread archived"); loadThreadList() }
                return true
            }
            "unarchive" -> {
                val id = args.getOrNull(0) ?: run { addError("Usage: /unarchive <threadId>"); return true }
                rpc("thread/unarchive", mapOf("threadId" to id)) { addSystem("Thread unarchived"); loadThreadList() }
                return true
            }
            "fork" -> {
                val tid = state.threadId ?: run { addError("No active thread to fork"); return true }
                rpc("thread/fork", mapOf("threadId" to tid)) { res ->
                    val thread = (res as? Map<*, *>)?.get("thread") as? Map<*, *>
                    val newId = thread?.get("id") as? String
                    if (newId != null) resumeThread(newId) else addSystem("Fork created")
                }
                return true
            }
            "rollback" -> {
                val tid = state.threadId ?: run { addError("No active thread"); return true }
                val n = maxOf(1, args.getOrNull(0)?.toIntOrNull() ?: 1)
                rpc("thread/rollback", mapOf("threadId" to tid, "turnCount" to n)) {
                    addSystem("Rolled back $n turn(s)")
                    loadThreadList()
                    rpc("thread/read", mapOf("threadId" to tid, "includeTurns" to true)) { readRes ->
                        val thread = (readRes as? Map<*, *>)?.get("thread") as? Map<*, *>
                        val turns = thread?.get("turns") as? List<*>
                        if (!turns.isNullOrEmpty()) {
                            _uiState.update { it.copy(messages = emptyList()) }
                            turns.forEach { _ -> } // Rehydrate from turns - simplified
                        }
                    }
                }
                return true
            }
            "compact" -> {
                val tid = state.threadId ?: run { addError("No active thread"); return true }
                rpc("thread/compact/start", mapOf("threadId" to tid)) { addSystem("Compacting…") }
                return true
            }
            "diff" -> {
                val cwd = state.workspacePath ?: state.threadList.find { it.id == state.threadId }?.cwd
                if (cwd == null) { addError("Select a workspace when connecting to use /diff"); return true }
                rpc("gitDiffToRemote", mapOf("cwd" to cwd)) { res ->
                    val diff = (res as? Map<*, *>)?.get("diff") as? String
                    if (diff != null) {
                        _uiState.update { it.copy(messages = it.messages + ChatMessage("d-${System.currentTimeMillis()}", MessageType.FILE, diff, false, mapOf("path" to "git diff"))) }
                    } else addSystem("No diff")
                }
                return true
            }
            "plan" -> {
                val tid = state.threadId ?: run { addError("No active thread"); return true }
                val prompt = args.joinToString(" ").ifBlank { "Propose a plan." }
                addUserMessage(prompt)
                val s = state.settings
                val params = buildTurnParams(prompt) + ("collaborationMode" to mapOf("mode" to "plan", "settings" to emptyMap<String, Any>()))
                _uiState.update { it.copy(activeTurnId = "_pending", connectionStatus = "working") }
                rpc("turn/start", params) { }
                return true
            }
            "skills" -> rpc("skills/list", emptyMap()) { res ->
                val data = (res as? Map<*, *>)?.get("data") as? List<*>
                val lines = data.orEmpty().mapNotNull { e ->
                    val m = e as? Map<*, *> ?: return@mapNotNull null
                    val skills = (m["skills"] as? List<*>)?.map { (it as? Map<*, *>)?.get("name") ?: it }?.joinToString(", ") ?: "?"
                    "${m["cwd"] ?: "?"}: $skills"
                }
                addSystem(if (lines.isNotEmpty()) "Skills:\n${lines.joinToString("\n")}" else "No skills found")
            }
            "mcp" -> rpc("mcpServerStatus/list", emptyMap()) { res ->
                val data = (res as? Map<*, *>)?.get("data") as? List<*>
                val txt = data.orEmpty().joinToString("\n") { entry ->
                    (entry as? Map<*, *>)?.let { m ->
                        "- ${m["name"] ?: "?"}: ${(m["tools"] as? Map<*, *>)?.size ?: 0} tools"
                    } ?: ""
                }
                addSystem(if (txt.isNotEmpty()) "MCP tools:\n$txt" else "No MCP servers configured")
            }
            "status" -> {
                val s = state.settings
                addSystem("Model: ${s.model.ifEmpty { "(default)" }} · Approval: ${s.approvalPolicy} · Sandbox: ${s.sandbox}")
                return true
            }
            "clearterminals" -> {
                val tid = state.threadId ?: run { addError("No active thread"); return true }
                rpc("thread/backgroundTerminals/clean", mapOf("threadId" to tid)) { addSystem("Background terminals cleaned") }
                return true
            }
            "run" -> {
                val cmd = args.joinToString(" ")
                if (cmd.isBlank()) { addError("Usage: /run <command>"); return true }
                rpc("command/exec", mapOf("command" to cmd.split(" "), "sandboxPolicy" to mapOf("type" to state.settings.sandbox))) { addSystem("Command sent") }
                return true
            }
            "rename" -> {
                val name = args.joinToString(" ")
                val tid = state.threadId ?: run { addError("No active thread"); return true }
                if (name.isBlank()) { addError("Usage: /rename <name>"); return true }
                rpc("thread/name/set", mapOf("threadId" to tid, "name" to name)) { addSystem("Renamed: $name") }
                return true
            }
            "settings" -> { toggleSettings(); return true }
            "approval", "permissions" -> {
                val policy = args.getOrNull(0)?.lowercase()
                if (policy !in listOf("on-request", "untrusted", "never")) { addError("Usage: /approval on-request|untrusted|never"); return true }
                val approvalPolicy = policy ?: return true
                updateSettings { it.copy(approvalPolicy = approvalPolicy) }
                addSystem("Approval: $approvalPolicy")
                return true
            }
            "sandbox" -> {
                val mode = args.getOrNull(0)?.lowercase()
                val map = mapOf("workspace-write" to "workspaceWrite", "read-only" to "readOnly", "danger-full-access" to "dangerFullAccess")
                val camel = map[mode] ?: mode?.replace(Regex("-([a-z])")) { it.groupValues[1].uppercase() }
                if (camel !in listOf("workspaceWrite", "readOnly", "dangerFullAccess")) { addError("Usage: /sandbox workspace-write|read-only|danger-full-access"); return true }
                val sandboxMode = camel ?: return true
                updateSettings { it.copy(sandbox = sandboxMode) }
                addSystem("Sandbox: $sandboxMode")
                return true
            }
            "logout" -> rpc("account/logout", emptyMap()) { addSystem("Logged out") }
            "quit", "exit" -> disconnect()
            "help" -> addSystem("Commands: /new /threads /resume /model /interrupt /review /archive /unarchive /fork /rollback /compact /diff /plan /skills /mcp /apps /experimental /status /run /rename /clearterminals /settings /approval /permissions /sandbox /logout /quit /help")
            else -> return false
        }
        return true
    }

    private fun buildTurnParams(text: String): Map<String, Any> {
        val state = _uiState.value
        val tid = state.threadId ?: return emptyMap()
        val s = state.settings
        return mapOf(
            "threadId" to tid,
            "input" to listOf(mapOf("type" to "text", "text" to text)),
            "approvalPolicy" to s.approvalPolicy,
            "sandboxPolicy" to mapOf("type" to s.sandbox)
        ) + (if (s.model.isNotEmpty()) mapOf("model" to s.model) else emptyMap()) +
            (if (s.effort.isNotEmpty()) mapOf("effort" to s.effort) else emptyMap())
    }

    fun startNewThread(workspacePath: String? = null) {
        val state = _uiState.value
        if (!state.isConnected) { addError("Connect first to start a new chat"); return }
        val s = state.settings

        // If a different workspace is requested, reconnect with it
        val targetWs = workspacePath?.takeIf { it.isNotBlank() }
        val currentWs = state.workspacePath
        if (targetWs != null && targetWs != currentWs) {
            val token = state.token.ifBlank { tokenStore.token ?: "" }
            disconnect()
            viewModelScope.launch {
                kotlinx.coroutines.delay(300)
                connect(s.serverHost, s.serverPort, token, targetWs, false)
            }
            return
        }

        val threadParams = mutableMapOf<String, Any>()
        if (s.model.isNotEmpty()) threadParams["model"] = s.model
        threadParams["approvalPolicy"] = s.approvalPolicy
        threadParams["sandbox"] = when (s.sandbox) {
            "workspaceWrite" -> "workspace-write"
            "readOnly" -> "read-only"
            "dangerFullAccess" -> "danger-full-access"
            else -> "workspace-write"
        }
        rpc("thread/start", threadParams) { res ->
            val thread = (res as? Map<*, *>)?.get("thread") as? Map<*, *>
            val tid = thread?.get("id") as? String
            _uiState.update {
                it.copy(
                    threadId = tid,
                    messages = emptyList(),
                    pendingQueue = emptyList(),
                    itemTexts = emptyMap(),
                    showHistory = false
                )
            }
            if (tid != null) upsertActiveSession(tid, "", state.workspacePath, state.workspaceName)
            addSystem("New chat started")
            loadThreadList()
        }
    }

    fun resumeThread(targetId: String) {
        val state = _uiState.value
        if (!state.isConnected || targetId == state.threadId) return
        // Save current thread's messages to cache before switching
        val oldThreadId = state.threadId
        if (oldThreadId != null && state.messages.isNotEmpty()) {
            sessionMessageCache[oldThreadId] = state.messages
        }

        // Instantly load cached messages for target (if available)
        val cachedMsgs = sessionMessageCache[targetId]
        _uiState.update {
            it.copy(
                threadId = targetId,
                messages = cachedMsgs ?: emptyList(),
                pendingQueue = emptyList(),
                itemTexts = emptyMap(),
                connectionStatus = if (cachedMsgs != null) "ready" else "resuming"
            )
        }
        val s = state.settings
        val params = mutableMapOf<String, Any>("threadId" to targetId)
        if (s.model.isNotEmpty()) params["model"] = s.model
        params["approvalPolicy"] = s.approvalPolicy
        params["sandbox"] = when (s.sandbox) {
            "workspaceWrite" -> "workspace-write"
            "readOnly" -> "read-only"
            "dangerFullAccess" -> "danger-full-access"
            else -> "workspace-write"
        }
        rpc("thread/resume", params) { res ->
            val thread = (res as? Map<*, *>)?.get("thread") as? Map<*, *>
            val tid = thread?.get("id") as? String ?: targetId
            _uiState.update { it.copy(threadId = tid) }
            upsertActiveSession(tid, "", _uiState.value.workspacePath, _uiState.value.workspaceName)
            rpc("thread/read", mapOf("threadId" to tid, "includeTurns" to true)) { readRes ->
                val t = (readRes as? Map<*, *>)?.get("thread") as? Map<*, *>
                val turns = t?.get("turns") as? List<*>
                val msgs = mutableListOf<ChatMessage>()
                turns?.forEach { turn ->
                    val items = (turn as? Map<*, *>)?.get("items") as? List<*>
                    items?.forEach { item ->
                        val m = item as? Map<*, *> ?: return@forEach
                        val type = m["type"] as? String ?: return@forEach
                        when (type) {
                            "userMessage" -> {
                                val content = (m["content"] as? List<*>)?.mapNotNull { c ->
                                    (c as? Map<*, *>)?.takeIf { it["type"] == "text" }?.get("text") as? String
                                }?.joinToString("\n") ?: ""
                                msgs.add(ChatMessage("u-${m["id"]}", MessageType.USER, content))
                            }
                            "agentMessage" -> {
                                val text = m["text"] as? String ?: ""
                                msgs.add(ChatMessage("a-${m["id"]}", MessageType.AGENT, text))
                            }
                            "reasoning" -> {
                                val body = (m["content"] as? List<*>)?.joinToString("\n") { it.toString() } ?: ""
                                msgs.add(ChatMessage("t-${m["id"]}", MessageType.THINKING, body))
                            }
                            "commandExecution" -> {
                                val cmd = m["command"] as? String ?: ""
                                msgs.add(ChatMessage("ex-${m["id"]}", MessageType.EXEC, "", false, mapOf("command" to cmd)))
                            }
                            "fileChange" -> msgs.add(ChatMessage("f-${m["id"]}", MessageType.FILE, "", false, mapOf("item" to (m.toString()))))
                            "mcpToolCall" -> msgs.add(ChatMessage("m-${m["id"]}", MessageType.TOOL, m["tool"] as? String ?: ""))
                        }
                    }
                }
                _uiState.update { it.copy(messages = msgs, connectionStatus = "ready") }
                // Update cache with fresh data
                sessionMessageCache[tid] = msgs
                addSystem("Resumed thread")
            }
            loadThreadList()
        }
    }

    private fun dispatchTurn(text: String) {
        val state = _uiState.value
        val threadId = state.threadId ?: return
        val s = state.settings
        val params = mutableMapOf<String, Any>(
            "threadId" to threadId,
            "input" to listOf(mapOf("type" to "text", "text" to text))
        )
        if (s.model.isNotEmpty()) params["model"] = s.model
        if (s.effort.isNotEmpty()) params["effort"] = s.effort
        params["approvalPolicy"] = s.approvalPolicy
        params["sandboxPolicy"] = mapOf("type" to s.sandbox)

        _uiState.update { it.copy(activeTurnId = "_pending", connectionStatus = "working") }
        rpc("turn/start", params) { _ ->
            // turn/started will set activeTurnId
        }
    }

    private fun processQueue() {
        val q = _uiState.value.pendingQueue
        if (q.isEmpty()) return
        val next = q.first()
        _uiState.update { it.copy(pendingQueue = it.pendingQueue.drop(1)) }
        addUserMessage(next)
        dispatchTurn(next)
    }

    fun interrupt() {
        val state = _uiState.value
        val tid = state.activeTurnId ?: return
        val threadId = state.threadId ?: return
        if (tid == "_pending") return
        rpc("turn/interrupt", mapOf("threadId" to threadId, "turnId" to tid)) { }
    }

    private fun rpc(method: String, params: Map<String, Any>, callback: (Any?) -> Unit) {
        ConnectionManager.sendRpc(method, params, { result ->
            viewModelScope.launch {
                callback(result)
            }
        }) { t ->
            addError("$method failed: ${t.message}")
        }
    }

    private fun notify(method: String, params: Map<String, Any>) {
        ConnectionManager.sendNotify(method, params)
    }

    fun sendPermission(requestId: String, decision: String) {
        if (!_uiState.value.isConnected) return
        ConnectionManager.sendPermission(requestId, decision)
    }

    private fun addUserMessage(text: String) {
        _uiState.update {
            it.copy(messages = it.messages + ChatMessage("u-${System.currentTimeMillis()}", MessageType.USER, text))
        }
    }

    private fun addSystem(text: String) {
        _uiState.update {
            it.copy(messages = it.messages + ChatMessage("s-${System.currentTimeMillis()}", MessageType.SYSTEM, text))
        }
    }

    private fun addError(text: String) {
        _uiState.update {
            it.copy(
                messages = it.messages + ChatMessage("e-${System.currentTimeMillis()}", MessageType.ERROR, text),
                error = text
            )
        }
    }

    private fun addStderr(text: String) {
        _uiState.update {
            val last = it.messages.lastOrNull()
            if (last?.type == MessageType.STDERR) {
                it.copy(messages = it.messages.dropLast(1) + last.copy(content = text))
            } else {
                it.copy(messages = it.messages + ChatMessage("st-${System.currentTimeMillis()}", MessageType.STDERR, text))
            }
        }
    }

    private fun addAgentTextItem(itemId: String) {
        _uiState.update {
            it.copy(
                messages = it.messages + ChatMessage("a-$itemId", MessageType.AGENT, "", true),
                itemTexts = it.itemTexts + (itemId to "")
            )
        }
    }

    private fun addAgentDelta(itemId: String, delta: String) {
        _uiState.update {
            val prev = it.itemTexts[itemId] ?: ""
            it.copy(itemTexts = it.itemTexts + (itemId to (prev + delta)))
        }
    }

    private fun finalizeAgent(itemId: String) {
        val text = _uiState.value.itemTexts[itemId] ?: ""
        _uiState.update {
            it.copy(
                messages = it.messages.map { m ->
                    if (m.id == "a-$itemId") m.copy(content = text, isStreaming = false) else m
                },
                itemTexts = it.itemTexts - itemId
            )
        }
    }

    private fun addThinkingItem(itemId: String) {
        _uiState.update {
            it.copy(
                messages = it.messages + ChatMessage("t-$itemId", MessageType.THINKING, "", true),
                itemTexts = it.itemTexts + (itemId to "")
            )
        }
    }

    private fun addThinkingDelta(itemId: String, delta: String) {
        _uiState.update {
            val prev = it.itemTexts[itemId] ?: ""
            it.copy(itemTexts = it.itemTexts + (itemId to (prev + delta)))
        }
    }

    private fun collapseThinking(itemId: String) {
        val text = _uiState.value.itemTexts[itemId] ?: ""
        _uiState.update {
            it.copy(
                messages = it.messages.map { m ->
                    if (m.id == "t-$itemId") m.copy(content = text, isStreaming = false) else m
                },
                itemTexts = it.itemTexts - itemId
            )
        }
    }

    private fun addExecItem(itemId: String, command: String) {
        _uiState.update {
            it.copy(messages = it.messages + ChatMessage(
                "ex-$itemId", MessageType.EXEC, "",
                true, mapOf("itemId" to itemId, "command" to command)
            ))
        }
    }

    private fun appendExecOutput(itemId: String, delta: String) {
        _uiState.update {
            it.copy(messages = it.messages.map { m ->
                if (m.id == "ex-$itemId") m.copy(content = m.content + delta) else m
            })
        }
    }

    private fun finalizeExec(itemId: String, item: JSONObject) {
        val output = item.optString("aggregatedOutput", "")
        val ok = item.optInt("exitCode", 0) == 0
        _uiState.update {
            it.copy(messages = it.messages.map { m ->
                if (m.id == "ex-$itemId") m.copy(content = output, isStreaming = false, metadata = m.metadata + ("ok" to ok)) else m
            })
        }
    }

    private fun addFileChangeItem(itemId: String, item: JSONObject) {
        _uiState.update {
            it.copy(messages = it.messages + ChatMessage("f-$itemId", MessageType.FILE, item.toString(), false, mapOf("itemId" to itemId)))
        }
    }

    private fun addMcpToolItem(itemId: String, item: JSONObject) {
        _uiState.update {
            it.copy(messages = it.messages + ChatMessage("m-$itemId", MessageType.TOOL, item.optString("tool", ""), true, mapOf("itemId" to itemId)))
        }
    }

    private fun finalizeMcpTool(itemId: String, item: JSONObject) {
        _uiState.update {
            it.copy(messages = it.messages.map { m ->
                if (m.id == "m-$itemId") m.copy(isStreaming = false, metadata = m.metadata + ("item" to item.toString())) else m
            })
        }
    }

    private fun addPermissionCard(reqId: String, method: String, params: JSONObject) {
        val detail = when {
            method.contains("commandExecution") -> params.optString("command", "?")
            method.contains("fileChange") -> params.optString("grantRoot", "")
            else -> params.toString()
        }
        _uiState.update {
            it.copy(messages = it.messages + ChatMessage("p-$reqId", MessageType.PERMISSION, detail, false, mapOf("reqId" to reqId, "method" to method)))
        }
    }

    fun loadThreadList() {
        val state = _uiState.value
        if (!state.isConnected) return
        val params = mutableMapOf<String, Any>(
            "limit" to 50,
            "sortKey" to "updated_at",
            "archived" to state.historyIncludeArchived
        )
        state.historyWorkspaceFilter?.takeIf { it.isNotBlank() }?.let { params["cwd"] = it }
        rpc("thread/list", params) { result ->
            val data = (result as? Map<*, *>)?.get("data") as? List<*>
            val list = data.orEmpty().mapNotNull { t ->
                val m = t as? Map<*, *> ?: return@mapNotNull null
                ThreadSummary(
                    id = m["id"] as? String ?: "",
                    preview = m["preview"] as? String ?: "",
                    cwd = m["cwd"] as? String,
                    updatedAt = (m["updatedAt"] as? Number)?.toLong() ?: 0L
                )
            }
            _uiState.update { it.copy(threadList = list) }
            // Refresh active session metadata from thread list
            for ((sessionId, session) in activeSessions) {
                val thread = list.find { it.id == sessionId }
                if (thread != null) {
                    activeSessions[sessionId] = session.copy(
                        preview = thread.preview.ifEmpty { session.preview },
                        workspacePath = thread.cwd ?: session.workspacePath
                    )
                }
            }
        }
    }

    fun updateSettings(block: (AppSettings) -> AppSettings) {
        viewModelScope.launch {
            settingsStore.updateSettings(block)
        }
    }

    fun setToken(token: String) = _uiState.update { it.copy(token = token) }
    fun setServerHost(host: String) = _uiState.update { it.copy(serverHost = host) }
    fun setServerPort(port: String) = _uiState.update { it.copy(serverPort = port) }
    fun setWorkspacePath(path: String?) = _uiState.update { it.copy(workspacePath = path) }
    fun openConnectScreen() = _uiState.update { it.copy(preferOfflineHome = false, connectionStatus = "disconnected") }
    fun resumeOfflineCache() = _uiState.update {
        if (it.hasOfflineCache) it.copy(preferOfflineHome = true, connectionStatus = "offline", error = null) else it
    }
    fun clearError() = _uiState.update { it.copy(error = null) }
    fun toggleSettings() = _uiState.update { it.copy(showSettings = !it.showSettings) }
    fun toggleHistory() = _uiState.update { it.copy(showHistory = !it.showHistory) }
    fun setShowHistory(show: Boolean) = _uiState.update { it.copy(showHistory = show) }
    fun openNewChatModal() = _uiState.update { it.copy(showNewChatModal = true) }
    fun dismissNewChatModal() = _uiState.update { it.copy(showNewChatModal = false) }
    fun setShowActiveSessionsOverlay(show: Boolean) = _uiState.update { it.copy(showActiveSessionsOverlay = show) }
    fun setHistoryIncludeArchived(include: Boolean) {
        _uiState.update { it.copy(historyIncludeArchived = include) }
        loadThreadList()
    }
    fun setHistoryWorkspaceFilter(cwd: String?) {
        _uiState.update { it.copy(historyWorkspaceFilter = cwd?.takeIf { s -> s.isNotBlank() }) }
        loadThreadList()
    }
    fun setHistorySearchQuery(query: String) = _uiState.update { it.copy(historySearchQuery = query) }
    fun clearQueue() = _uiState.update { it.copy(pendingQueue = emptyList()) }

    fun removeFromQueue(idx: Int) {
        _uiState.update { state ->
            if (idx in state.pendingQueue.indices) {
                state.copy(pendingQueue = state.pendingQueue.filterIndexed { i, _ -> i != idx })
            } else state
        }
    }

    private fun restoreCachedSession(cached: CachedSession) {
        if (cached.messages.isEmpty()) return
        _uiState.update {
            it.copy(
                connectionStatus = "offline",
                workspaceName = cached.workspaceName,
                workspacePath = cached.workspacePath,
                threadId = cached.threadId,
                messages = cached.messages.map { message ->
                    ChatMessage(
                        id = message.id,
                        type = message.type.toMessageType(),
                        content = message.content,
                        isStreaming = false,
                        metadata = message.metadata
                    )
                },
                threadList = cached.threadList.map { thread ->
                    ThreadSummary(thread.id, thread.preview, thread.cwd, thread.updatedAt)
                },
                workspaces = cached.workspaces.map { workspace ->
                    WorkspaceOption(workspace.name, workspace.path)
                },
                itemTexts = emptyMap(),
                hasOfflineCache = true,
                preferOfflineHome = true
            )
        }
    }

    private fun transitionToOffline(error: String? = null) {
        _uiState.update { state ->
            val hasCache = state.messages.isNotEmpty() || state.hasOfflineCache
            state.copy(
                isConnected = false,
                activeTurnId = null,
                connectionStatus = if (hasCache) "offline" else "disconnected",
                models = emptyList(),
                pendingQueue = emptyList(),
                itemTexts = emptyMap(),
                tokenUsage = null,
                rateLimits = emptyMap(),
                hasOfflineCache = hasCache,
                preferOfflineHome = hasCache,
                error = error
            )
        }
    }

    private fun CodexUiState.toCachedSession(): CachedSession? {
        if (messages.isEmpty()) return null
        val capped = messages.takeLast(200)
        return CachedSession(
            workspaceName = workspaceName,
            workspacePath = workspacePath,
            threadId = threadId,
            messages = capped.map { message ->
                CachedMessage(
                    id = message.id,
                    type = message.type.name,
                    content = message.content.take(4000),
                    isStreaming = false,
                    metadata = message.metadata.filterValues { value ->
                        value is String || value is Number || value is Boolean
                    }
                )
            },
            threadList = threadList.map { thread ->
                CachedThreadSummary(thread.id, thread.preview, thread.cwd, thread.updatedAt)
            },
            workspaces = workspaces.map { workspace ->
                CachedWorkspaceOption(workspace.name, workspace.path)
            }
        )
    }

    private fun String.toMessageType(): MessageType =
        runCatching { MessageType.valueOf(this) }.getOrDefault(MessageType.SYSTEM)

    // ═══════════════════════════════════════════════════════════════
    // Active session helpers
    // ═══════════════════════════════════════════════════════════════

    fun upsertActiveSession(threadId: String, preview: String, workspacePath: String?, workspaceName: String?) {
        val existing = activeSessions[threadId]
        activeSessions[threadId] = (existing ?: ActiveSession(threadId = threadId, preview = "", workspacePath = null, workspaceName = null)).copy(
            preview = preview.ifEmpty { existing?.preview ?: "" },
            workspacePath = workspacePath ?: existing?.workspacePath,
            workspaceName = workspaceName ?: existing?.workspaceName,
            lastActivityAt = System.currentTimeMillis()
        )
    }

    private fun markSessionRunning(threadId: String) {
        val session = activeSessions[threadId] ?: return
        activeSessions[threadId] = session.copy(turnRunning = true, lastActivityAt = System.currentTimeMillis())
    }

    private fun markSessionIdle(threadId: String) {
        val session = activeSessions[threadId] ?: return
        activeSessions[threadId] = session.copy(turnRunning = false, lastActivityAt = System.currentTimeMillis())
    }

    private fun updateSessionActivity(threadId: String) {
        val session = activeSessions[threadId] ?: return
        activeSessions[threadId] = session.copy(lastActivityAt = System.currentTimeMillis())
    }

    fun removeActiveSession(threadId: String) {
        activeSessions.remove(threadId)
    }

    fun sortedActiveSessions(): List<ActiveSession> {
        return activeSessions.values.sortedByDescending { it.lastActivityAt }
    }

    fun cycleActiveSession(forward: Boolean): String? {
        val sorted = sortedActiveSessions()
        if (sorted.size < 2) return null
        val currentId = _uiState.value.threadId ?: return null
        val idx = sorted.indexOfFirst { it.threadId == currentId }
        if (idx < 0) return sorted.first().threadId
        val nextIdx = if (forward) (idx + 1) % sorted.size else (idx - 1 + sorted.size) % sorted.size
        return sorted[nextIdx].threadId
    }
}
