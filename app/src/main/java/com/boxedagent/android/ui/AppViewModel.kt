package com.boxedagent.android.ui

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.boxedagent.android.data.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import java.io.File
import java.util.Base64

private const val DEFAULT_BASE_URL = "http://10.0.2.2:8080"

private val vmJson = Json { ignoreUnknownKeys = true; explicitNulls = false; isLenient = true }

enum class MainPanel { Boxes, Chat, Tools }
enum class ToolTab { Terminal, Files, Pi, CodeServer }

data class UiEvent(val id: Long = System.nanoTime(), val message: String)
data class ComposerInsert(val id: Long = System.nanoTime(), val sessionId: String?, val text: String)

data class AppUiState(
    val baseUrl: String = DEFAULT_BASE_URL,
    val token: String = "",
    val authLoading: Boolean = false,
    val authEnabled: Boolean = false,
    val authenticated: Boolean = false,
    val connectionError: String? = null,
    val health: String = "—",
    val activity: String = "",
    val boxes: List<BoxRecord> = emptyList(),
    val sessions: List<AgentSessionRecord> = emptyList(),
    val activeBoxId: String? = null,
    val activeSessionId: String? = null,
    val selectedPanel: MainPanel = MainPanel.Chat,
    val selectedToolTab: ToolTab = ToolTab.Terminal,
    val messagesBySession: Map<String, List<ChatMessage>> = emptyMap(),
    val messagesLoading: Boolean = false,
    val turnActiveBySession: Map<String, Boolean> = emptyMap(),
    val queueBySession: Map<String, QueueState> = emptyMap(),
    val statsBySession: Map<String, SessionStats?> = emptyMap(),
    val sessionModels: List<PiModel> = emptyList(),
    val modelLoading: Boolean = false,
    val event: UiEvent? = null,
    val composerInsert: ComposerInsert? = null
) {
    val activeBox: BoxRecord? get() = boxes.firstOrNull { it.id == activeBoxId }
    val activeSession: AgentSessionRecord? get() = sessions.firstOrNull { it.id == activeSessionId }
    val activeMessages: List<ChatMessage> get() = activeSessionId?.let { messagesBySession[it] }.orEmpty()
    val activeTurn: Boolean get() = activeSessionId?.let { turnActiveBySession[it] } == true
    val activeQueue: QueueState get() = activeSessionId?.let { queueBySession[it] } ?: QueueState()
    val activeStats: SessionStats? get() = activeSessionId?.let { statsBySession[it] }
}

data class QueueState(val steering: List<String> = emptyList(), val followUp: List<String> = emptyList())
data class CachedPreviewFile(val name: String, val mimeType: String, val file: File, val size: Long)

class AppViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = application.getSharedPreferences("boxedagent", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(AppUiState(
        baseUrl = prefs.getString("baseUrl", DEFAULT_BASE_URL) ?: DEFAULT_BASE_URL,
        token = prefs.getString("token", "") ?: ""
    ))
    val state: StateFlow<AppUiState> = _state

    private var api = BoxedAgentApi(_state.value.baseUrl, _state.value.token)
    private var globalWs: WebSocket? = null
    private var boxWs: WebSocket? = null
    private var sessionWs: WebSocket? = null
    private var pollingJob: Job? = null

    init {
        viewModelScope.launch { connect(_state.value.baseUrl, _state.value.token, silent = true) }
    }

    override fun onCleared() {
        globalWs?.close(1000, null)
        boxWs?.close(1000, null)
        sessionWs?.close(1000, null)
        super.onCleared()
    }

    fun setPanel(panel: MainPanel) = _state.update { it.copy(selectedPanel = panel) }
    fun setToolTab(tab: ToolTab) = _state.update { it.copy(selectedToolTab = tab) }
    fun updateConnectionFields(baseUrl: String? = null, token: String? = null) = _state.update { it.copy(baseUrl = baseUrl ?: it.baseUrl, token = token ?: it.token) }

    fun emit(message: String) = _state.update { it.copy(event = UiEvent(message = message)) }
    fun clearEvent(id: Long) = _state.update { if (it.event?.id == id) it.copy(event = null) else it }
    fun rememberedFileBrowserPath(boxId: String?): String = boxId?.takeIf { it.isNotBlank() }?.let { prefs.getString("fileBrowserPath:$it", ".") }?.takeIf { it.isNotBlank() } ?: "."
    fun rememberFileBrowserPath(boxId: String?, path: String) {
        val id = boxId?.takeIf { it.isNotBlank() } ?: return
        prefs.edit().putString("fileBrowserPath:$id", path.ifBlank { "." }).apply()
    }
    fun rememberedFileBookmarks(boxId: String?): List<String> {
        val id = boxId?.takeIf { it.isNotBlank() } ?: return emptyList()
        return prefs.getStringSet("fileBookmarks:$id", emptySet()).orEmpty().map { normalizeRelPath(it) }.distinct().sorted()
    }
    fun rememberFileBookmarks(boxId: String?, bookmarks: List<String>) {
        val id = boxId?.takeIf { it.isNotBlank() } ?: return
        prefs.edit().putStringSet("fileBookmarks:$id", bookmarks.map { normalizeRelPath(it) }.distinct().sorted().toSet()).apply()
    }
    fun insertIntoComposer(text: String, returnToChat: Boolean = true) = _state.update { it.copy(composerInsert = ComposerInsert(sessionId = it.activeSessionId, text = text), selectedPanel = if (returnToChat) MainPanel.Chat else it.selectedPanel) }
    fun clearComposerInsert(id: Long) = _state.update { if (it.composerInsert?.id == id) it.copy(composerInsert = null) else it }

    fun connectFromState() {
        val s = _state.value
        viewModelScope.launch { connect(s.baseUrl, s.token, silent = false) }
    }

    suspend fun connect(baseUrl: String, token: String, silent: Boolean = false) {
        _state.update { it.copy(authLoading = true, connectionError = null) }
        api.updateConnection(baseUrl, token)
        prefs.edit().putString("baseUrl", api.baseUrl).putString("token", token.trim()).apply()
        try {
            val status = api.authStatus()
            var authenticated = status.authenticated
            if (status.enabled && !authenticated && token.trim().isNotEmpty()) {
                authenticated = api.login(token).ok
            }
            _state.update {
                it.copy(
                    baseUrl = api.baseUrl,
                    token = token.trim(),
                    authLoading = false,
                    authEnabled = status.enabled,
                    authenticated = !status.enabled || authenticated,
                    connectionError = if (status.enabled && !authenticated) "Token 无效或未登录" else null
                )
            }
            if (!status.enabled || authenticated) {
                refreshAll()
                startGlobalWs()
                startPolling()
                if (!silent) emit("已连接 BoxedAgent")
            }
        } catch (e: Exception) {
            _state.update { it.copy(authLoading = false, authenticated = false, connectionError = e.message ?: String()) }
            if (!silent) emit("连接失败：${e.message}")
        }
    }

    fun logout() {
        viewModelScope.launch {
            runCatching { api.logout() }
            prefs.edit().remove("token").apply()
            globalWs?.close(1000, null); boxWs?.close(1000, null); sessionWs?.close(1000, null)
            pollingJob?.cancel()
            _state.update { AppUiState(baseUrl = it.baseUrl, token = "", authEnabled = it.authEnabled, authenticated = !it.authEnabled) }
        }
    }

    fun refresh() = viewModelScope.launch { refreshAll() }

    private suspend fun refreshAll() {
        try {
            val health = runCatching { api.health() }.getOrNull()
            val boxes = api.listBoxes()
            val sessions = api.listSessions()
            _state.update { old ->
                val activeBoxId = old.activeBoxId?.takeIf { id -> boxes.any { it.id == id } } ?: boxes.firstOrNull()?.id
                val activeSessionId = old.activeSessionId?.takeIf { id -> sessions.any { it.id == id && (activeBoxId == null || it.boxId == activeBoxId) } }
                    ?: sessions.firstOrNull { activeBoxId == null || it.boxId == activeBoxId }?.id
                old.copy(
                    health = health?.docker ?: old.health,
                    boxes = boxes,
                    sessions = sessions,
                    activeBoxId = activeBoxId,
                    activeSessionId = activeSessionId
                )
            }
            watchActiveBox()
            watchActiveSession(loadMessages = false)
        } catch (e: Exception) {
            emit("刷新失败：${e.message}")
        }
    }

    private fun startPolling() {
        pollingJob?.cancel()
        pollingJob = viewModelScope.launch {
            while (isActive) {
                delay(10_000)
                if (_state.value.authenticated) runCatching { refreshAll() }
            }
        }
    }

    private fun startGlobalWs() {
        globalWs?.close(1000, null)
        globalWs = api.webSocket("/ws/events", object : WebSocketListener() {
            override fun onMessage(webSocket: WebSocket, text: String) {
                val obj = runCatching { vmJson.parseToJsonElement(text).jsonObject }.getOrNull() ?: return
                when (obj.string("type")) {
                    "boxes_changed" -> viewModelScope.launch { refreshAll() }
                    "image_ensure_start" -> _state.update { it.copy(activity = "${if (obj.string("action") == "build") "构建" else "拉取"}镜像：${obj.string("image") ?: ""}") }
                    "image_progress" -> obj.string("message")?.let { msg -> _state.update { it.copy(activity = msg.take(160)) } }
                    "image_ensure_end" -> {
                        _state.update { it.copy(activity = "镜像就绪：${obj.string("image") ?: ""}") }
                        viewModelScope.launch { refreshAll() }
                    }
                    "image_ensure_error" -> _state.update { it.copy(activity = "镜像失败：${obj.string("error") ?: "unknown"}") }
                }
            }
        })
    }

    fun selectBox(id: String?) {
        _state.update { old ->
            val nextSession = old.sessions.firstOrNull { it.boxId == id }?.id
            old.copy(activeBoxId = id, activeSessionId = nextSession)
        }
        watchActiveBox()
        watchActiveSession(loadMessages = true)
    }

    fun selectSession(id: String?) {
        _state.update { it.copy(activeSessionId = id) }
        watchActiveSession(loadMessages = true)
    }

    private fun watchActiveBox() {
        val boxId = _state.value.activeBoxId ?: run { boxWs?.close(1000, null); boxWs = null; return }
        boxWs?.close(1000, null)
        boxWs = api.webSocket("/ws/boxes/$boxId/events", object : WebSocketListener() {
            override fun onMessage(webSocket: WebSocket, text: String) {
                val obj = runCatching { vmJson.parseToJsonElement(text).jsonObject }.getOrNull() ?: return
                if (obj.string("type") in setOf("sessions_changed", "box_updated")) viewModelScope.launch { refreshAll() }
            }
        })
    }

    private fun watchActiveSession(loadMessages: Boolean) {
        val sessionId = _state.value.activeSessionId ?: run { sessionWs?.close(1000, null); sessionWs = null; return }
        sessionWs?.close(1000, null)
        sessionWs = api.webSocket("/ws/sessions/$sessionId/events", object : WebSocketListener() {
            override fun onMessage(webSocket: WebSocket, text: String) = handleSessionWs(sessionId, text)
        })
        if (loadMessages || !_state.value.messagesBySession.containsKey(sessionId)) loadSessionMessages(sessionId)
        refreshSessionRuntime(sessionId)
    }

    fun loadSessionMessages(sessionId: String? = null) {
        val id = sessionId ?: _state.value.activeSessionId ?: return
        viewModelScope.launch {
            _state.update { it.copy(messagesLoading = true) }
            try {
                val messages = normalizePiMessages(api.messages(id))
                _state.update { old -> old.copy(messagesLoading = false, messagesBySession = old.messagesBySession + (id to messages)) }
            } catch (e: Exception) {
                _state.update { it.copy(messagesLoading = false) }
                emit("加载消息失败：${e.message}")
            }
        }
    }

    fun refreshSessionRuntime(sessionId: String? = null) {
        val id = sessionId ?: _state.value.activeSessionId ?: return
        viewModelScope.launch {
            runCatching { api.sessionStats(id) }.onSuccess { stats -> _state.update { it.copy(statsBySession = it.statsBySession + (id to stats)) } }
            runCatching { api.sessionState(id) }.onSuccess { applyRuntimeState(id, it.state) }
        }
    }

    private fun handleSessionWs(sessionId: String, text: String) {
        val msg = runCatching { vmJson.parseToJsonElement(text).jsonObject }.getOrNull() ?: return
        when (msg.string("type")) {
            "session_status" -> {
                val status = msg.string("status") ?: return
                setTurnActive(sessionId, status == "working")
                patchSessionLocal(sessionId, status = if (status == "starting" && _state.value.turnActiveBySession[sessionId] == true) "working" else status, error = msg.string("error"))
            }
            "agent_event" -> handleAgentEvent(sessionId, msg.obj("event") ?: return)
        }
    }

    private fun handleAgentEvent(sessionId: String, event: JsonObject) {
        when (event.string("type")) {
            "agent_start", "turn_start", "message_start" -> setTurnActive(sessionId, true)
            "agent_end", "turn_end", "message_end" -> { setTurnActive(sessionId, false); refreshSessionRuntime(sessionId) }
            "message_update" -> handleMessageUpdate(sessionId, event.obj("assistantMessageEvent") ?: return)
            "tool_execution_start" -> {
                setTurnActive(sessionId, true)
                upsertTool(sessionId, event.string("toolCallId") ?: "${event.string("toolName")}-${System.currentTimeMillis()}", event.string("toolName") ?: "tool", event["args"], null, "running")
            }
            "tool_execution_update" -> upsertTool(sessionId, event.string("toolCallId") ?: "${event.string("toolName")}-${System.currentTimeMillis()}", event.string("toolName") ?: "tool", event["args"], resultToText(event["partialResult"]), "running")
            "tool_execution_end" -> upsertTool(sessionId, event.string("toolCallId") ?: "${event.string("toolName")}-${System.currentTimeMillis()}", event.string("toolName") ?: "tool", event["args"], resultToText(event["result"]), if (event.boolean("isError") == true) "error" else "done")
            "queue_update" -> _state.update { old -> old.copy(queueBySession = old.queueBySession + (sessionId to QueueState(event.arrayStrings("steering"), event.arrayStrings("followUp")))) }
            "compaction_start" -> { setTurnActive(sessionId, true); appendSystem(sessionId, "正在压缩上下文：${event.string("reason") ?: "manual"}") }
            "compaction_end" -> {
                setTurnActive(sessionId, false)
                appendSystem(sessionId, if (event.boolean("aborted") == true) "上下文压缩已取消" else "上下文压缩完成${if (event.boolean("willRetry") == true) "，将自动重试" else ""}${event.string("errorMessage")?.let { "：$it" } ?: ""}")
                refreshSessionRuntime(sessionId)
            }
        }
    }

    private fun handleMessageUpdate(sessionId: String, delta: JsonObject) {
        when (delta.string("type")) {
            "start", "text_start", "thinking_start", "toolcall_start" -> setTurnActive(sessionId, true)
            "text_delta" -> updateLastAssistant(sessionId, delta.string("delta") ?: "")
            "thinking_delta" -> updateLastAssistantThinking(sessionId, delta.string("delta") ?: "")
            "toolcall_start", "toolcall_delta", "toolcall_end" -> {
                val tool = toolCallFromDelta(delta)
                val id = tool.toolCallId ?: delta.string("id") ?: (delta.string("contentIndex") ?: "tool")
                upsertTool(sessionId, id, tool.toolName ?: "tool", tool.toolArgs, null, "pending")
            }
            "done", "error" -> { setTurnActive(sessionId, false); refreshSessionRuntime(sessionId) }
        }
    }

    private fun setTurnActive(sessionId: String, active: Boolean) {
        _state.update { it.copy(turnActiveBySession = it.turnActiveBySession + (sessionId to active)) }
        patchSessionLocal(sessionId, status = if (active) "working" else null, error = null)
    }

    private fun patchSessionLocal(sessionId: String, status: String? = null, error: String? = null, patch: AgentSessionRecord? = null) {
        _state.update { old ->
            old.copy(sessions = old.sessions.map { s ->
                if (s.id != sessionId) s else patch ?: s.copy(status = status ?: s.status, error = error ?: s.error)
            })
        }
    }

    private fun appendMessage(sessionId: String, msg: ChatMessage) {
        _state.update { old -> old.copy(messagesBySession = old.messagesBySession + (sessionId to (old.messagesBySession[sessionId].orEmpty() + msg))) }
    }

    private fun appendSystem(sessionId: String, text: String) = appendMessage(sessionId, ChatMessage(newMessageId(), "system", text, System.currentTimeMillis()))

    private fun updateLastAssistant(sessionId: String, delta: String) {
        _state.update { old ->
            val list = old.messagesBySession[sessionId].orEmpty().toMutableList()
            val last = list.lastOrNull()
            if (last?.role == "assistant") list[list.lastIndex] = last.copy(text = last.text + delta)
            else list += ChatMessage(newMessageId(), "assistant", delta, System.currentTimeMillis())
            old.copy(messagesBySession = old.messagesBySession + (sessionId to list))
        }
    }

    private fun updateLastAssistantThinking(sessionId: String, delta: String) {
        _state.update { old ->
            val list = old.messagesBySession[sessionId].orEmpty().toMutableList()
            val last = list.lastOrNull()
            if (last?.role == "assistant") list[list.lastIndex] = last.copy(thinking = (last.thinking ?: "") + delta)
            else list += ChatMessage(newMessageId(), "assistant", "", System.currentTimeMillis(), thinking = delta)
            old.copy(messagesBySession = old.messagesBySession + (sessionId to list))
        }
    }

    private fun upsertTool(sessionId: String, toolCallId: String, name: String, args: JsonElement?, result: String?, status: String) {
        _state.update { old ->
            val list = old.messagesBySession[sessionId].orEmpty().toMutableList()
            val idx = list.indexOfFirst { it.role == "tool" && it.toolCallId == toolCallId }
            if (idx >= 0) {
                val current = list[idx]
                list[idx] = current.copy(toolName = name, toolArgs = args ?: current.toolArgs, toolResult = result ?: current.toolResult, toolStatus = status, timestamp = System.currentTimeMillis())
            } else {
                list += ChatMessage(newMessageId(), "tool", "", System.currentTimeMillis(), toolCallId = toolCallId, toolName = name, toolArgs = args, toolResult = result, toolStatus = status)
            }
            old.copy(messagesBySession = old.messagesBySession + (sessionId to list))
        }
    }

    private fun applyRuntimeState(sessionId: String, state: JsonElement?) {
        val obj = state as? JsonObject ?: return
        val modelObj = obj.obj("model")
        val provider = modelObj?.string("provider") ?: modelObj?.string("providerId") ?: modelObj?.string("providerName")
        val model = modelObj?.string("id")
        val thinking = obj.string("thinkingLevel")
        val auto = obj.boolean("autoCompactionEnabled")
        _state.update { old ->
            old.copy(sessions = old.sessions.map { s ->
                if (s.id == sessionId) s.copy(provider = provider ?: s.provider, model = model ?: s.model, thinkingLevel = thinking ?: s.thinkingLevel, autoCompactionEnabled = auto ?: s.autoCompactionEnabled) else s
            })
        }
    }

    fun createBox(name: String, image: String, description: String, password: String, provider: String, model: String, thinking: String) = viewModelScope.launch {
        try {
            _state.update { it.copy(activity = "创建 Box：检查/构建 Docker 镜像…") }
            val box = api.createBox(CreateBoxRequest(name = name, description = description.ifBlank { null }, image = image, enableCodeServer = true, codeServerPassword = password, autostart = true, pi = PiBoxConfig(defaultProvider = provider.ifBlank { null }, defaultModel = model.ifBlank { null }, defaultThinkingLevel = thinking)))
            api.createSession(CreateSessionRequest(boxId = box.id, name = "默认会话", provider = provider.ifBlank { null }, model = model.ifBlank { null }, thinkingLevel = thinking, autostart = false))
            refreshAll()
            selectBox(box.id)
            emit("Box 已创建")
        } catch (e: Exception) { emit("创建 Box 失败：${e.message}") }
        finally { _state.update { it.copy(activity = "") } }
    }

    fun renameBox(id: String, name: String) = viewModelScope.launch { runApi("重命名 Box") { api.updateBox(id, PatchBoxRequest(name = name)); refreshAll() } }
    fun startBox(id: String) = viewModelScope.launch { runApi("启动 Box") { api.startBox(id); refreshAll() } }
    fun stopBox(id: String) = viewModelScope.launch { runApi("停止 Box") { api.stopBox(id); refreshAll() } }
    fun deleteBox(id: String, deleteWorkspace: Boolean = false) = viewModelScope.launch { runApi("删除 Box") { api.deleteBox(id, deleteWorkspace); refreshAll() } }
    fun cloneBox(id: String, name: String) = viewModelScope.launch { runApi("克隆 Box") { val box = api.cloneBox(id, CloneBoxRequest(name = name)); refreshAll(); selectBox(box.id) } }

    fun createSession(name: String, cwd: String, provider: String?, model: String?, thinking: String?, autostart: Boolean = true) = viewModelScope.launch {
        val boxId = _state.value.activeBoxId ?: return@launch emit("请先选择 Box")
        runApi("创建 Session") {
            val s = api.createSession(CreateSessionRequest(boxId, name.ifBlank { null }, normalizeCwd(cwd), provider?.ifBlank { null }, model?.ifBlank { null }, thinking?.ifBlank { null }, autostart))
            refreshAll(); selectSession(s.id)
        }
    }

    fun renameSession(id: String, name: String) = viewModelScope.launch { runApi("重命名 Session") { api.updateSession(id, PatchSessionRequest(name = name)); refreshAll() } }
    fun startSession(id: String) = viewModelScope.launch { runApi("启动 Session") { api.startSession(id); refreshAll(); selectSession(id) } }
    fun stopSession(id: String) = viewModelScope.launch { runApi("停止 Session") { api.stopSession(id); refreshAll() } }
    fun deleteSession(id: String) = viewModelScope.launch { runApi("删除 Session") { api.deleteSession(id); refreshAll() } }
    fun duplicateSession(id: String) = viewModelScope.launch { runApi("复刻 Session") { val res = api.duplicateSession(id); refreshAll(); selectSession(res.session.id) } }
    fun forkSession(id: String, entryId: String) = viewModelScope.launch { runApi("Fork Session") { val res = api.forkSession(id, ForkSessionRequest(entryId)); refreshAll(); if (res.cancelled != true) { selectSession(res.session.id); res.text?.let { appendSystem(res.session.id, "已 Fork，原消息已放入草稿：\n$it") } } } }

    suspend fun loadForkMessages(sessionId: String): List<ForkMessage> = api.forkMessages(sessionId)
    suspend fun loadBoxModels(boxId: String): List<PiModel> = api.boxModels(boxId)

    fun sendPrompt(text: String, attachments: List<DraftAttachment>, sendMode: String?) = viewModelScope.launch {
        val s = _state.value
        val sessionId = s.activeSessionId ?: return@launch emit("请先选择 Session")
        val boxId = s.activeBoxId ?: return@launch emit("请先选择 Box")
        val cwd = s.activeSession?.cwd ?: "/workspace"
        val trimmed = text.trimEnd()
        if (trimmed.isBlank() && attachments.isEmpty()) return@launch
        try {
            val displayAttachments = mutableListOf<ChatAttachment>()
            val uploadedPaths = mutableListOf<String>()
            attachments.forEach { file ->
                api.uploadFile(boxId, ".upload", file.name, file.bytes, file.mimeType)
                val path = uploadedAttachmentPath(file.name)
                uploadedPaths += path
                if (file.isImage) {
                    val b64 = Base64.getEncoder().encodeToString(file.bytes)
                    displayAttachments += ChatAttachment.Image(file.name, file.mimeType.ifBlank { "image/png" }, b64, path = path, size = file.size)
                } else {
                    displayAttachments += ChatAttachment.File(file.name, path, file.size, file.mimeType)
                }
            }
            val mentionedPaths = runCatching { parseFileRefs(trimmed).map { resolveWorkspaceReference(it, cwd).absPath }.toSet() }.getOrDefault(emptySet())
            val refsToAppend = uploadedPaths.filterNot { it in mentionedPaths }
            val displayMessage = buildString {
                append(trimmed)
                if (refsToAppend.isNotEmpty()) {
                    if (isNotEmpty() && !last().isWhitespace()) append(' ')
                    append(refsToAppend.joinToString(" ") { fileRef(it) })
                }
            }.ifBlank { attachmentSummary(displayAttachments) }
            val expanded = expandFileReferencesForPrompt(boxId, cwd, displayMessage)
            val working = s.activeTurn || s.activeSession?.status == "working"
            val modeToUse = if (working) sendMode else null
            if (working && modeToUse == null) {
                api.abortSession(sessionId)
                setTurnActive(sessionId, false)
            }
            appendMessage(sessionId, ChatMessage(newMessageId(), "user", displayMessage, System.currentTimeMillis(), displayAttachments))
            setTurnActive(sessionId, true)
            api.prompt(sessionId, PromptRequest(message = expanded.message, streamingBehavior = modeToUse, images = expanded.images.takeIf { it.isNotEmpty() }))
        } catch (e: Exception) {
            setTurnActive(sessionId, false)
            appendSystem(sessionId, "发送失败：${e.message}")
        }
    }

    fun abortActive() = viewModelScope.launch {
        val id = _state.value.activeSessionId ?: return@launch
        runApi("中止") { api.abortSession(id); setTurnActive(id, false); refreshSessionRuntime(id) }
    }

    fun chooseThinking(level: String) = viewModelScope.launch {
        val id = _state.value.activeSessionId ?: return@launch
        runApi("切换 Thinking") { val res = api.setSessionThinking(id, level); patchSessionLocal(id, patch = res.session); applyRuntimeState(id, res.state) }
    }

    fun setAutoCompaction(enabled: Boolean) = viewModelScope.launch {
        val id = _state.value.activeSessionId ?: return@launch
        runApi("切换 Compact") { val res = api.setAutoCompaction(id, enabled); patchSessionLocal(id, patch = res.session); applyRuntimeState(id, res.state) }
    }

    fun compact(customInstructions: String?) = viewModelScope.launch {
        val id = _state.value.activeSessionId ?: return@launch
        runApi("Compact") { appendSystem(id, if (customInstructions.isNullOrBlank()) "已触发手动上下文压缩。" else "已触发手动上下文压缩。自定义要求：$customInstructions"); api.compactSession(id, customInstructions?.takeIf { it.isNotBlank() }) }
    }

    fun loadSessionModels(force: Boolean = false) = viewModelScope.launch {
        val id = _state.value.activeSessionId ?: return@launch
        if (_state.value.sessionModels.isNotEmpty() && !force) return@launch
        _state.update { it.copy(modelLoading = true) }
        try { _state.update { it.copy(sessionModels = api.sessionModels(id), modelLoading = false) } }
        catch (e: Exception) { _state.update { it.copy(modelLoading = false) }; emit("加载模型失败：${e.message}") }
    }

    fun setSessionModel(model: PiModel) = viewModelScope.launch {
        val id = _state.value.activeSessionId ?: return@launch
        val provider = model.providerNameOrNull() ?: return@launch emit("模型缺少 provider")
        runApi("切换模型") { val res = api.setSessionModel(id, SetModelRequest(provider, model.id)); patchSessionLocal(id, patch = res.session) }
    }

    suspend fun listFiles(path: String): List<FileEntry> {
        val boxId = _state.value.activeBoxId ?: throw ApiException("请先选择 Box")
        return api.listFiles(boxId, path)
    }

    suspend fun mkdir(path: String) {
        val boxId = _state.value.activeBoxId ?: throw ApiException("请先选择 Box")
        api.mkdir(boxId, path)
    }

    suspend fun deleteFile(path: String) {
        val boxId = _state.value.activeBoxId ?: throw ApiException("请先选择 Box")
        api.deleteFile(boxId, path)
    }

    suspend fun uploadFile(path: String, file: DraftAttachment) {
        val boxId = _state.value.activeBoxId ?: throw ApiException("请先选择 Box")
        api.uploadFile(boxId, path, file.name, file.bytes, file.mimeType)
    }

    suspend fun downloadFile(path: String): DownloadedFile {
        val boxId = _state.value.activeBoxId ?: throw ApiException("请先选择 Box")
        return api.downloadFile(boxId, path)
    }

    suspend fun downloadFileToCache(path: String, onProgress: (bytesRead: Long, totalBytes: Long) -> Unit): CachedPreviewFile {
        val boxId = _state.value.activeBoxId ?: throw ApiException("请先选择 Box")
        val dir = File(getApplication<Application>().cacheDir, "file-previews/$boxId").apply { mkdirs() }
        val fallbackName = path.substringAfterLast('/').ifBlank { "preview" }
        val tmp = File(dir, ".${System.nanoTime()}-${safeCacheFileName(fallbackName)}.part")
        return try {
            val downloaded = api.downloadFileTo(boxId, path, tmp, onProgress)
            val target = uniqueCacheFile(dir, downloaded.name)
            if (!tmp.renameTo(target)) {
                tmp.copyTo(target, overwrite = true)
                tmp.delete()
            }
            CachedPreviewFile(downloaded.name, downloaded.mimeType, target, downloaded.bytesWritten)
        } catch (e: Exception) {
            tmp.delete()
            throw e
        }
    }

    suspend fun getPiConfig(): PiConfigResponse {
        val boxId = _state.value.activeBoxId ?: throw ApiException("请先选择 Box")
        return api.getPiConfig(boxId)
    }

    suspend fun updatePiConfig(body: PiConfigUpdateRequest): PiConfigResponse {
        val boxId = _state.value.activeBoxId ?: throw ApiException("请先选择 Box")
        val res = api.updatePiConfig(boxId, body)
        refreshAll()
        return res
    }

    fun codeServerUrl(): String? = _state.value.activeBoxId?.let { api.codeServerUrl(it) }
    fun bearerToken(): String = api.token()
    fun authCookieHeader(): String = api.cookieHeader()
    fun baseUrl(): String = api.baseUrl

    fun openTerminal(cols: Int = 80, rows: Int = 24, listener: WebSocketListener): WebSocket? {
        val boxId = _state.value.activeBoxId ?: return null
        return api.webSocket("/ws/boxes/$boxId/terminal?cols=${cols.coerceIn(20, 400)}&rows=${rows.coerceIn(8, 120)}", listener)
    }

    private suspend fun expandFileReferencesForPrompt(boxId: String, cwd: String, message: String): ExpandedFileRefs {
        val refs = parseFileRefs(message)
        if (refs.isEmpty()) return ExpandedFileRefs(message, emptyList(), emptySet())
        val seen = linkedSetOf<String>()
        val images = mutableListOf<ImagePayload>()
        val fileText = StringBuilder()
        refs.forEach { rawRef ->
            val resolved = resolveWorkspaceReference(rawRef, cwd)
            if (!seen.add(resolved.absPath)) return@forEach
            val file = api.downloadFile(boxId, resolved.relPath)
            val mime = file.mimeType.substringBefore(';').trim().ifBlank { guessMimeType(resolved.absPath) ?: "text/plain" }
            if (mime.startsWith("image/") || isImagePath(resolved.absPath)) {
                images += ImagePayload(data = Base64.getEncoder().encodeToString(file.bytes), mimeType = if (mime.startsWith("image/")) mime else guessMimeType(resolved.absPath) ?: "image/png")
                fileText.append("<file name=\"").append(resolved.absPath).append("\"></file>\n")
            } else {
                fileText.append("<file name=\"").append(resolved.absPath).append("\">\n")
                fileText.append(file.bytes.toString(Charsets.UTF_8)).append("\n</file>\n")
            }
        }
        return ExpandedFileRefs(fileText.toString() + message, images, seen)
    }

    private suspend fun runApi(label: String, block: suspend () -> Unit) {
        try { block(); emit("$label 成功") }
        catch (e: Exception) { emit("$label 失败：${e.message}") }
    }
}

private fun normalizeRelPath(value: String): String {
    val parts = mutableListOf<String>()
    value.replace('\\', '/').split('/').forEach { part ->
        when {
            part.isBlank() || part == "." -> Unit
            part == ".." -> if (parts.isNotEmpty()) parts.removeAt(parts.lastIndex)
            else -> parts += part
        }
    }
    return parts.joinToString("/").ifBlank { "." }
}

private fun safeCacheFileName(name: String): String = name
    .replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]+"), "_")
    .trim()
    .take(120)
    .ifBlank { "preview" }

private fun uniqueCacheFile(dir: File, name: String): File {
    val safe = safeCacheFileName(name)
    val dot = safe.lastIndexOf('.').takeIf { it > 0 && it < safe.lastIndex }
    val base = dot?.let { safe.substring(0, it) } ?: safe
    val ext = dot?.let { safe.substring(it) }.orEmpty()
    var candidate = File(dir, safe)
    var i = 1
    while (candidate.exists()) {
        candidate = File(dir, "$base-$i$ext")
        i++
    }
    return candidate
}

fun normalizeCwd(value: String): String {
    val trimmed = value.trim().ifBlank { "/workspace" }
    if (trimmed == "/workspace" || trimmed.startsWith("/workspace/")) return trimmed.trimEnd('/').ifBlank { "/workspace" }
    val rel = trimmed.trimStart('/')
    if (rel.isBlank() || rel == "." || rel.contains("..")) return "/workspace"
    return "/workspace/$rel".trimEnd('/')
}

fun uploadedAttachmentPath(name: String): String = "/workspace/.upload/$name"

fun fileRef(path: String): String = if (path.any { it.isWhitespace() }) "@\"${path.replace("\\", "\\\\").replace("\"", "\\\"")}\"" else "@$path"

private data class WorkspaceRef(val absPath: String, val relPath: String)
private data class ExpandedFileRefs(val message: String, val images: List<ImagePayload>, val referencedPaths: Set<String>)

private fun parseFileRefs(text: String): List<String> {
    val refs = mutableListOf<String>()
    val re = Regex("(^|\\s)@(?:\\\"((?:\\\\.|[^\\\"\\\\])*)\\\"|'([^']*)'|([^\\s]+))")
    re.findAll(text).forEach { match ->
        val quoted = match.groupValues.getOrNull(2)?.takeIf { it.isNotBlank() }?.replace(Regex("\\\\([\\\"\\\\])"), "$1")
            ?: match.groupValues.getOrNull(3)?.takeIf { it.isNotBlank() }
        val raw = quoted ?: match.groupValues.getOrNull(4).orEmpty().replace(Regex("[),.;:!?，。；：！？]+$"), "")
        raw.trim().takeIf { it.isNotBlank() && !it.startsWith("@") }?.let { refs += it }
    }
    return refs
}

private fun resolveWorkspaceReference(input: String, cwd: String): WorkspaceRef {
    val base = normalizeWorkspacePath(cwd.ifBlank { "/workspace" })
    val raw = input.trim()
    val abs = if (raw.startsWith("/")) normalizeWorkspacePath(raw) else normalizeWorkspacePath("$base/$raw")
    if (abs != "/workspace" && !abs.startsWith("/workspace/")) throw ApiException("文件路径必须位于 /workspace 内：$input")
    return WorkspaceRef(abs, if (abs == "/workspace") "." else abs.removePrefix("/workspace/"))
}

private fun normalizeWorkspacePath(value: String): String {
    val parts = mutableListOf<String>()
    value.replace('\\', '/').split('/').forEach { part ->
        when {
            part.isBlank() || part == "." -> Unit
            part == ".." -> if (parts.isNotEmpty()) parts.removeAt(parts.lastIndex)
            else -> parts += part
        }
    }
    return "/${parts.joinToString("/")}".trimEnd('/').ifBlank { "/workspace" }
}

private fun isImagePath(path: String): Boolean = Regex("\\.(png|jpe?g|gif|webp)$", RegexOption.IGNORE_CASE).containsMatchIn(path)
private fun guessMimeType(path: String): String? = when {
    path.endsWith(".png", true) -> "image/png"
    path.endsWith(".jpg", true) || path.endsWith(".jpeg", true) -> "image/jpeg"
    path.endsWith(".gif", true) -> "image/gif"
    path.endsWith(".webp", true) -> "image/webp"
    path.endsWith(".json", true) -> "application/json"
    path.endsWith(".md", true) -> "text/markdown"
    else -> null
}

private fun JsonObject.arrayStrings(key: String): List<String> = (this[key] as? kotlinx.serialization.json.JsonArray)?.mapNotNull { it.jsonPrimitive.contentOrNull } ?: emptyList()
