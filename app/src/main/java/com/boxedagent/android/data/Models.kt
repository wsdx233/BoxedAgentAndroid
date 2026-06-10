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
data class BoxPortMapping(
    val id: String = "",
    val name: String = "",
    val port: Int = 0,
    val protocol: String = "http",
    val slug: String = "",
    val openPath: String? = null,
    val createdAt: String = "",
    val updatedAt: String = ""
)

@Serializable
data class ContainerGpuConfig(
    val enabled: Boolean = false,
    val count: JsonElement? = null,
    val deviceIds: List<String> = emptyList()
)

@Serializable
data class ContainerDeviceMapping(
    val pathOnHost: String = "",
    val pathInContainer: String? = null,
    val cgroupPermissions: String? = null
)

@Serializable
data class ContainerBindMount(
    val source: String = "",
    val target: String = "",
    val readonly: Boolean? = null
)

@Serializable
data class ContainerStartupConfig(
    val workingDir: String? = null,
    val user: String? = null,
    val startupScript: String? = null,
    val env: Map<String, String> = emptyMap(),
    val extraHosts: List<String> = emptyList(),
    val shmSizeMb: Int? = null,
    val gpu: ContainerGpuConfig? = null,
    val devices: List<ContainerDeviceMapping> = emptyList(),
    val privileged: Boolean? = null,
    val capAdd: List<String> = emptyList(),
    val mounts: List<ContainerBindMount> = emptyList(),
    val exposedPorts: List<Int> = emptyList()
)

@Serializable
data class BoxRecord(
    val id: String = "",
    val name: String = "",
    val description: String? = null,
    val image: String = "",
    val imageProfileId: String? = null,
    val workspacePath: String = "",
    val env: Map<String, String> = emptyMap(),
    val labels: Map<String, String> = emptyMap(),
    val memoryMb: Int? = null,
    val cpus: Double? = null,
    val enableCodeServer: Boolean = false,
    val codeServerPassword: String? = null,
    val portMappings: List<BoxPortMapping> = emptyList(),
    val pi: PiBoxConfig = PiBoxConfig(),
    val startup: ContainerStartupConfig = ContainerStartupConfig(),
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
data class ImageBuildContextFile(
    val path: String = "",
    val content: String = "",
    val mode: Int? = null
)

@Serializable
data class ImageBuildConfig(
    val buildArgs: Map<String, String> = emptyMap(),
    val platform: String? = null,
    val target: String? = null,
    val noCache: Boolean? = null,
    val pull: Boolean? = null,
    val contextFiles: List<ImageBuildContextFile> = emptyList()
)

@Serializable
data class ImageProfileBoxDefaults(
    val env: Map<String, String> = emptyMap(),
    val labels: Map<String, String> = emptyMap(),
    val memoryMb: Int? = null,
    val cpus: Double? = null,
    val enableCodeServer: Boolean? = null,
    val codeServerPassword: String? = null,
    val pi: PiBoxConfig? = null,
    val startup: ContainerStartupConfig? = null
)

@Serializable
data class ImageProfileRecord(
    val id: String = "",
    val name: String = "",
    val description: String? = null,
    val image: String = "",
    val baseImage: String? = null,
    val dockerfile: String = "",
    val build: ImageBuildConfig = ImageBuildConfig(),
    val boxDefaults: ImageProfileBoxDefaults = ImageProfileBoxDefaults(),
    val status: String = "draft",
    val error: String? = null,
    val lastBuiltAt: String? = null,
    val createdAt: String = "",
    val updatedAt: String = ""
)

@Serializable
data class ImageProfilesResponse(
    val profiles: List<ImageProfileRecord> = emptyList(),
    val advancedOptionsAllowed: Boolean = true
)

@Serializable
data class ImageProfileBuildResponse(
    val profile: ImageProfileRecord = ImageProfileRecord(),
    val image: ImageStatusResponse = ImageStatusResponse()
)

@Serializable
data class CreateBoxRequest(
    val name: String,
    val description: String? = null,
    val image: String? = null,
    val imageProfileId: String? = null,
    val buildImage: Boolean = true,
    val env: Map<String, String>? = null,
    val labels: Map<String, String>? = null,
    val memoryMb: Int? = null,
    val cpus: Double? = null,
    val enableCodeServer: Boolean? = null,
    val codeServerPassword: String? = null,
    val pi: PiBoxConfig? = null,
    val startup: ContainerStartupConfig? = null,
    val autostart: Boolean = true
)

@Serializable
data class DuplicateBoxRequest(
    val name: String? = null,
    val description: String? = null,
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
    val imageProfileId: String? = null,
    val env: Map<String, String>? = null,
    val labels: Map<String, String>? = null,
    val memoryMb: Int? = null,
    val cpus: Double? = null,
    val enableCodeServer: Boolean? = null,
    val codeServerPassword: String? = null,
    val pi: PiBoxConfig? = null,
    val startup: ContainerStartupConfig? = null,
    val workspacePath: String? = null
)

@Serializable
data class AgentSessionNotice(
    val id: String = "",
    val kind: String = "extension_notify",
    val title: String = "",
    val message: String = "",
    val notifyType: String? = null,
    val timestamp: String = ""
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
    val kind: String = "chat",
    val cwd: String? = null,
    val model: String? = null,
    val provider: String? = null,
    val thinkingLevel: String? = null,
    val autoCompactionEnabled: Boolean? = null,
    val sessionFile: String? = null,
    val piSessionId: String? = null,
    val launchArgs: List<String> = emptyList(),
    val error: String? = null,
    val loadedResources: PiLoadedResources? = null,
    val notices: List<AgentSessionNotice> = emptyList()
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
    val kind: String = "chat",
    val launchArgs: List<String>? = null,
    val launchArgsText: String? = null,
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
data class CloneSessionRequest(val name: String? = null)

@Serializable
data class CloneSessionResponse(
    val session: AgentSessionRecord = AgentSessionRecord(),
    val cancelled: Boolean? = null
)

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
data class SessionTreeNode(
    val id: String = "",
    val parentId: String? = null,
    val depth: Int = 0,
    val type: String = "",
    val role: String? = null,
    val text: String = "",
    val timestamp: String? = null,
    val label: String? = null,
    val active: Boolean = false,
    val inActivePath: Boolean = false
)

@Serializable
data class SessionTree(
    val nodes: List<SessionTreeNode> = emptyList(),
    val activeId: String? = null,
    val activePathIds: List<String> = emptyList(),
    val entryCount: Int = 0
)

@Serializable
data class SessionTreeResponse(val tree: SessionTree = SessionTree())

@Serializable
data class TreeNavigateRequest(val targetId: String)

@Serializable
data class TreeNavigateResponse(
    val session: AgentSessionRecord = AgentSessionRecord(),
    val editorText: String? = null,
    val activeId: String? = null
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
data class PiSlashCommand(
    val name: String = "",
    val description: String? = null,
    val source: String = "extension",
    val sourceInfo: JsonObject? = null
)

@Serializable
data class PiSlashCommandsResponse(val commands: List<PiSlashCommand> = emptyList())

@Serializable
data class PiLoadedResourceItem(
    val name: String = "",
    val path: String = "",
    val scope: String = "workspace",
    val kind: String = "context",
    val type: String? = null,
    val source: String? = null,
    val description: String? = null,
    val entrypoint: String? = null,
    val size: Long? = null
)

@Serializable
data class PiLoadedResources(
    val cwd: String = "/workspace",
    val reason: String? = null,
    val generatedAt: String = "",
    val contextFiles: List<PiLoadedResourceItem> = emptyList(),
    val packages: List<PiLoadedResourceItem> = emptyList(),
    val extensions: List<PiLoadedResourceItem> = emptyList(),
    val skills: List<PiLoadedResourceItem> = emptyList(),
    val prompts: List<PiLoadedResourceItem> = emptyList(),
    val themes: List<PiLoadedResourceItem> = emptyList(),
    val diagnostics: List<String> = emptyList()
)

@Serializable
data class PiLoadedResourcesResponse(val resources: PiLoadedResources = PiLoadedResources())

@Serializable
data class ReloadSessionResponse(val session: AgentSessionRecord = AgentSessionRecord())

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
data class FileOperationRequest(val source: String, val target: String)

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
data class SessionMessageResponse(val message: JsonElement? = null)

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
    val contextUsage: ContextUsage? = null,
    val loadedResources: PiLoadedResources? = null
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
data class SelectedSessionRequest(val sessionId: String? = null)

@Serializable
data class SelectedSessionResponse(
    val ok: Boolean? = null,
    val sessionId: String? = null,
    val activeSessionId: String? = null,
    val boxId: String? = null,
    val session: AgentSessionRecord? = null
)

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

data class ChatMessageTruncationPath(
    val path: String,
    val totalChars: Long,
    val shownChars: Long,
    val omittedChars: Long
)

data class ChatMessageTransportMeta(
    val messageId: String,
    val truncated: Boolean,
    val totalChars: Long? = null,
    val shownChars: Long? = null,
    val omittedChars: Long? = null,
    val paths: List<ChatMessageTruncationPath> = emptyList()
)

data class ToolResultMeta(
    val truncated: Boolean? = null,
    val totalLines: Long? = null,
    val shownLines: Long? = null,
    val omittedLines: Long? = null,
    val totalBytes: Long? = null,
    val shownBytes: Long? = null,
    val label: String? = null
)

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
    val toolResultMeta: ToolResultMeta? = null,
    val toolStatus: String? = null,
    val transport: ChatMessageTransportMeta? = null,
    val noticeId: String? = null,
    val sourceIndex: Long? = null
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
