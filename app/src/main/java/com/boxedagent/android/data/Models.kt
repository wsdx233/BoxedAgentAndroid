package com.boxedagent.android.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

@Serializable
data class AuthStatus(
    val enabled: Boolean = false,
    val authenticated: Boolean = false
)

@Serializable
data class LoginRequest(val token: String)

@Serializable
data class LoginResponse(
    val ok: Boolean = false,
    val enabled: Boolean? = null,
    val error: String? = null
)

@Serializable
data class HealthResponse(
    val ok: Boolean = false,
    val docker: String = "unknown",
    val image: JsonElement? = null,
    val version: String? = null
)

@Serializable
data class ImageStatusResponse(
    val image: String = "",
    val available: Boolean = false,
    val source: String = "",
    val error: String? = null
)

@Serializable
data class ImageEnsureRequest(val image: String)

@Serializable
data class PiBoxConfig(
    val defaultProvider: String? = null,
    val defaultModel: String? = null,
    val defaultThinkingLevel: String? = null,
    val enabledModels: List<String> = emptyList(),
    val settingsJson: JsonObject? = null,
    val modelsJson: JsonObject? = null,
    val systemPrompt: String? = null,
    val appendSystemPrompt: String? = null,
    val agentsMd: String? = null,
    val extraArgs: List<String> = emptyList()
)

@Serializable
data class BoxRecord(
    val id: String = "",
    val name: String = "",
    val description: String? = null,
    val image: String = "",
    val workspacePath: String = "",
    val env: Map<String, String> = emptyMap(),
    val labels: Map<String, String> = emptyMap(),
    val memoryMb: Int? = null,
    val cpus: Double? = null,
    val enableCodeServer: Boolean = false,
    val codeServerPassword: String? = null,
    val pi: PiBoxConfig = PiBoxConfig(),
    val containerId: String? = null,
    val status: String = "stopped",
    val createdAt: String = "",
    val updatedAt: String = "",
    val lastActiveAt: String? = null,
    val error: String? = null
)

@Serializable
data class BoxesResponse(val boxes: List<BoxRecord> = emptyList())

@Serializable
data class CreateBoxRequest(
    val name: String,
    val description: String? = null,
    val image: String? = null,
    val env: Map<String, String>? = null,
    val labels: Map<String, String>? = null,
    val memoryMb: Int? = null,
    val cpus: Double? = null,
    val enableCodeServer: Boolean? = null,
    val codeServerPassword: String? = null,
    val pi: PiBoxConfig? = null,
    val autostart: Boolean = true
)

@Serializable
data class CloneBoxRequest(
    val name: String,
    val description: String? = null,
    val autostart: Boolean = true
)

@Serializable
data class PatchBoxRequest(
    val name: String? = null,
    val description: String? = null,
    val image: String? = null,
    val env: Map<String, String>? = null,
    val labels: Map<String, String>? = null,
    val memoryMb: Int? = null,
    val cpus: Double? = null,
    val enableCodeServer: Boolean? = null,
    val codeServerPassword: String? = null,
    val pi: PiBoxConfig? = null,
    val workspacePath: String? = null
)

@Serializable
data class AgentSessionRecord(
    val id: String = "",
    val boxId: String = "",
    val name: String = "",
    val status: String = "idle",
    val createdAt: String = "",
    val updatedAt: String = "",
    val lastActiveAt: String? = null,
    val cwd: String? = null,
    val model: String? = null,
    val provider: String? = null,
    val thinkingLevel: String? = null,
    val autoCompactionEnabled: Boolean? = null,
    val sessionFile: String? = null,
    val error: String? = null
)

@Serializable
data class SessionsResponse(val sessions: List<AgentSessionRecord> = emptyList())

@Serializable
data class CreateSessionRequest(
    val boxId: String,
    val name: String? = null,
    val cwd: String? = null,
    val provider: String? = null,
    val model: String? = null,
    val thinkingLevel: String? = null,
    val autostart: Boolean = false
)

@Serializable
data class PatchSessionRequest(
    val name: String? = null,
    val cwd: String? = null,
    val model: String? = null,
    val provider: String? = null,
    val thinkingLevel: String? = null
)

@Serializable
data class DuplicateSessionRequest(
    val name: String? = null,
    val autostart: Boolean? = null
)

@Serializable
data class DuplicateSessionResponse(val session: AgentSessionRecord = AgentSessionRecord())

@Serializable
data class ForkMessage(val entryId: String = "", val text: String = "")

@Serializable
data class ForkMessagesResponse(val messages: List<ForkMessage> = emptyList())

@Serializable
data class ForkSessionRequest(val entryId: String, val name: String? = null)

@Serializable
data class ForkSessionResponse(
    val session: AgentSessionRecord = AgentSessionRecord(),
    val text: String? = null,
    val cancelled: Boolean? = null
)

@Serializable
data class OkResponse(val ok: Boolean = false)

@Serializable
data class PiModel(
    val id: String = "",
    val provider: String? = null,
    val name: String? = null,
    val reasoning: Boolean? = null,
    val input: List<String>? = null,
    val contextWindow: Long? = null,
    val maxTokens: Long? = null,
    val providerId: String? = null,
    val providerName: String? = null
)

fun PiModel.providerNameOrNull(): String? = (provider ?: providerId ?: providerName)?.trim()?.takeIf { it.isNotEmpty() }

@Serializable
data class ModelsResponse(val models: List<PiModel> = emptyList())

@Serializable
data class FileEntry(
    val name: String = "",
    val path: String = "",
    val type: String = "file",
    val size: Long = 0,
    val modifiedAt: String = ""
)

@Serializable
data class FilesResponse(val entries: List<FileEntry> = emptyList())

@Serializable
data class MkdirRequest(val path: String)

@Serializable
data class UploadResponse(val ok: Boolean = false, val filename: String? = null)

@Serializable
data class ImagePayload(
    val type: String = "image",
    val data: String,
    val mimeType: String
)

@Serializable
data class PromptRequest(
    val message: String = "",
    val streamingBehavior: String? = null,
    val images: List<ImagePayload>? = null
)

@Serializable
data class PromptResponse(val ok: Boolean = false, val result: JsonElement? = null)

@Serializable
data class SessionMessagesResponse(val messages: List<JsonElement> = emptyList())

@Serializable
data class SessionStateResponse(val state: JsonElement? = null)

@Serializable
data class SetModelRequest(val provider: String, val modelId: String)

@Serializable
data class SetModelResponse(val session: AgentSessionRecord = AgentSessionRecord(), val model: PiModel? = null)

@Serializable
data class SetThinkingRequest(val level: String)

@Serializable
data class SetAutoCompactionRequest(val enabled: Boolean)

@Serializable
data class RuntimePatchResponse(val session: AgentSessionRecord = AgentSessionRecord(), val state: JsonElement? = null)

@Serializable
data class CompactRequest(val customInstructions: String? = null)

@Serializable
data class SessionStats(
    val sessionFile: String? = null,
    val sessionId: String? = null,
    val userMessages: Int? = null,
    val assistantMessages: Int? = null,
    val toolCalls: Int? = null,
    val toolResults: Int? = null,
    val totalMessages: Int? = null,
    val tokens: TokenStats? = null,
    val cost: Double? = null,
    val contextUsage: ContextUsage? = null
)

@Serializable
data class TokenStats(
    val input: Long? = null,
    val output: Long? = null,
    val cacheRead: Long? = null,
    val cacheWrite: Long? = null,
    val total: Long? = null
)

@Serializable
data class ContextUsage(
    val tokens: Long? = null,
    val contextWindow: Long? = null,
    val percent: Double? = null
)

@Serializable
data class SessionStatsResponse(val stats: SessionStats? = null)

@Serializable
data class PiConfigResponse(
    val pi: PiBoxConfig = PiBoxConfig(),
    val env: Map<String, String> = emptyMap(),
    val materialized: MaterializedPiConfig = MaterializedPiConfig()
)

@Serializable
data class MaterializedPiConfig(
    val piCodingAgentDir: String = "/workspace/.boxedagent/pi-agent",
    val settings: JsonObject? = null
)

@Serializable
data class PiConfigUpdateRequest(
    val defaultProvider: String? = null,
    val defaultModel: String? = null,
    val defaultThinkingLevel: String? = null,
    val enabledModels: List<String>? = null,
    val settingsJsonText: String? = null,
    val modelsJsonText: String? = null,
    val systemPrompt: String? = null,
    val appendSystemPrompt: String? = null,
    val agentsMd: String? = null,
    val extraArgs: List<String>? = null,
    val env: Map<String, String>? = null
)

@Serializable
data class ErrorResponse(val error: String? = null, val code: String? = null, val details: JsonElement? = null)

sealed interface ChatAttachment {
    val name: String
    data class Image(
        override val name: String,
        val mimeType: String,
        val data: String,
        val path: String? = null,
        val size: Long? = null
    ) : ChatAttachment

    data class File(
        override val name: String,
        val path: String,
        val size: Long? = null,
        val mimeType: String? = null
    ) : ChatAttachment
}

data class ChatMessage(
    val id: String,
    val role: String,
    val text: String,
    val timestamp: Long,
    val attachments: List<ChatAttachment> = emptyList(),
    val thinking: String? = null,
    val toolCallId: String? = null,
    val toolName: String? = null,
    val toolArgs: JsonElement? = null,
    val toolResult: String? = null,
    val toolStatus: String? = null
)

data class DraftAttachment(
    val name: String,
    val mimeType: String,
    val bytes: ByteArray,
    val isImage: Boolean
) {
    val size: Long get() = bytes.size.toLong()
    override fun equals(other: Any?): Boolean = other is DraftAttachment && name == other.name && mimeType == other.mimeType && bytes.contentEquals(other.bytes)
    override fun hashCode(): Int = 31 * (31 * name.hashCode() + mimeType.hashCode()) + bytes.contentHashCode()
}
