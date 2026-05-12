package com.boxedagent.android.data

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.UUID

private val normalizerJson = Json { prettyPrint = true; ignoreUnknownKeys = true; explicitNulls = false }

fun normalizePiMessages(messages: List<JsonElement>): List<ChatMessage> {
    val toolCalls = mutableMapOf<String, Int>()
    val out = mutableListOf<ChatMessage>()
    messages.forEachIndexed { idx, element ->
        val obj = element.asObject() ?: return@forEachIndexed
        val transport = transportMeta(obj)
        val timestamp = obj.long("timestamp") ?: System.currentTimeMillis()
        when (obj.string("role")) {
            "user" -> {
                val content = obj["content"]
                val images = attachmentsFromContent(content)
                val expanded = extractInlineFileBlocks(contentToText(content).ifBlank { obj.string("message") ?: "" })
                val attachments = images + expanded.attachments
                out += ChatMessage(
                    id = "$idx-$timestamp",
                    role = "user",
                    text = expanded.text.ifBlank { attachmentSummary(attachments) },
                    attachments = attachments,
                    timestamp = timestamp,
                    transport = transport
                )
            }
            "assistant" -> {
                val parts = contentParts(obj["content"])
                val summary = obj.string("summary")?.let { "[summary]\n$it" }.orEmpty()
                if (parts.text.isNotBlank() || parts.thinking.isNotBlank() || summary.isNotBlank()) {
                    out += ChatMessage(
                        id = "$idx-$timestamp-assistant",
                        role = "assistant",
                        text = parts.text.ifBlank { summary },
                        thinking = parts.thinking.ifBlank { null },
                        timestamp = timestamp,
                        transport = transport
                    )
                }
                parts.tools.forEachIndexed { toolIdx, tool ->
                    val msg = ChatMessage(
                        id = "$idx-$timestamp-tool-$toolIdx",
                        role = "tool",
                        text = "",
                        timestamp = timestamp,
                        toolCallId = tool.toolCallId,
                        toolName = tool.toolName,
                        toolArgs = tool.toolArgs,
                        toolStatus = "pending",
                        transport = transport
                    )
                    out += msg
                    tool.toolCallId?.let { toolCalls[it] = out.lastIndex }
                }
            }
            else -> {
                val callId = obj.string("tool_call_id") ?: obj.string("toolCallId") ?: obj.string("id") ?: "$idx-$timestamp"
                val priorIndex = toolCalls[callId]
                val prior = priorIndex?.let { out[it] }
                val msg = ChatMessage(
                    id = prior?.id ?: "$idx-$timestamp",
                    role = "tool",
                    text = "",
                    toolCallId = callId,
                    toolName = obj.string("name") ?: obj.string("toolName") ?: prior?.toolName ?: "tool",
                    toolArgs = obj["args"] ?: obj["arguments"] ?: prior?.toolArgs,
                    toolStatus = if (obj.boolean("isError") == true) "error" else "done",
                    toolResult = contentToText(obj["content"]).ifBlank { resultToText(obj["result"]).ifBlank { pretty(element) } },
                    toolResultMeta = toolResultMeta(obj["result"] ?: obj["content"]),
                    timestamp = timestamp,
                    transport = transport
                )
                if (priorIndex != null) out[priorIndex] = if (transport == null && prior?.transport != null) msg.copy(transport = prior.transport) else msg else out += msg
            }
        }
    }
    return out
}

fun transportMeta(obj: JsonObject): ChatMessageTransportMeta? {
    val meta = obj.obj("__boxedagent") ?: return null
    val messageId = meta.string("messageId") ?: return null
    return ChatMessageTransportMeta(
        messageId = messageId,
        truncated = meta.boolean("truncated") == true,
        totalChars = meta.long("totalChars"),
        shownChars = meta.long("shownChars"),
        omittedChars = meta.long("omittedChars"),
        paths = (meta["paths"] as? JsonArray).orEmpty().mapNotNull { item ->
            val path = item.asObject() ?: return@mapNotNull null
            ChatMessageTruncationPath(
                path = path.string("path") ?: return@mapNotNull null,
                totalChars = path.long("totalChars") ?: 0,
                shownChars = path.long("shownChars") ?: 0,
                omittedChars = path.long("omittedChars") ?: 0
            )
        }
    )
}

data class ContentParts(val text: String, val thinking: String, val tools: List<ToolPart>)
data class ToolPart(val toolCallId: String?, val toolName: String?, val toolArgs: JsonElement?)

fun contentParts(content: JsonElement?): ContentParts {
    if (content == null) return ContentParts("", "", emptyList())
    if (content is JsonPrimitive) return ContentParts(content.contentOrNull.orEmpty(), "", emptyList())
    val arr = content as? JsonArray ?: return ContentParts(pretty(content), "", emptyList())
    val text = mutableListOf<String>()
    val thinking = mutableListOf<String>()
    val tools = mutableListOf<ToolPart>()
    arr.forEach { part ->
        if (part is JsonPrimitive) {
            part.contentOrNull?.let { text += it }
            return@forEach
        }
        val obj = part.asObject() ?: return@forEach
        when {
            isImageContentPart(obj) -> Unit
            obj.string("type") == "thinking" || obj.containsKey("thinking") -> thinking += (obj.string("thinking") ?: obj.string("text") ?: "")
            isToolContentPart(obj) -> tools += ToolPart(
                toolCallId = obj.string("id") ?: obj.string("toolCallId") ?: obj.string("tool_call_id"),
                toolName = obj.string("name") ?: obj.string("toolName") ?: "tool",
                toolArgs = obj["args"] ?: obj["arguments"] ?: obj["input"]
            )
            obj.string("type") == "text" || obj.containsKey("text") -> text += (obj.string("text") ?: "")
            else -> Unit
        }
    }
    return ContentParts(text.filter { it.isNotBlank() }.joinToString("\n\n"), thinking.filter { it.isNotBlank() }.joinToString("\n\n"), tools)
}

fun contentToText(content: JsonElement?): String {
    val parts = contentParts(content)
    return listOf(
        parts.thinking.takeIf { it.isNotBlank() }?.let { "思考：\n$it" },
        parts.text.takeIf { it.isNotBlank() }
    ).filterNotNull().joinToString("\n\n")
}

fun attachmentsFromContent(content: JsonElement?): List<ChatAttachment.Image> {
    val arr = content as? JsonArray ?: return emptyList()
    return arr.mapIndexedNotNull { idx, part ->
        val obj = part.asObject() ?: return@mapIndexedNotNull null
        if (!isImageContentPart(obj)) return@mapIndexedNotNull null
        val data = obj.string("data") ?: obj.string("imageData") ?: obj.obj("source")?.string("data") ?: return@mapIndexedNotNull null
        val mime = obj.string("mimeType") ?: obj.string("mediaType") ?: obj.obj("source")?.string("media_type") ?: "image/png"
        ChatAttachment.Image(obj.string("name") ?: "image-${idx + 1}", mime, data.removePrefixDataUrl())
    }
}

data class ExtractedFiles(val text: String, val attachments: List<ChatAttachment.File>)

fun extractInlineFileBlocks(text: String): ExtractedFiles {
    val attachments = mutableListOf<ChatAttachment.File>()
    val regex = Regex("<file\\s+name=[\"']([^\"']+)[\"']>[\\s\\S]*?</file>\\n?")
    val stripped = regex.replace(text) { match ->
        val path = match.groupValues[1]
        attachments += ChatAttachment.File(name = path.split('/').filter { it.isNotBlank() }.lastOrNull() ?: path, path = path)
        ""
    }.trimStart()
    return ExtractedFiles(stripped, attachments)
}

fun isImageContentPart(obj: JsonObject): Boolean {
    val type = obj.string("type")?.lowercase().orEmpty()
    val mime = obj.string("mimeType")
    return type in setOf("image", "image_url", "input_image") || obj.containsKey("imageData") || mime?.startsWith("image/") == true || obj.obj("source")?.string("type") == "base64"
}

fun isToolContentPart(obj: JsonObject): Boolean {
    val type = obj.string("type")?.lowercase().orEmpty()
    if (type == "thinking") return false
    return type in setOf("toolcall", "tool_call", "tool-call", "tool_use", "tooluse") || ((obj.containsKey("name") || obj.containsKey("toolName")) && (obj.containsKey("args") || obj.containsKey("arguments") || obj.containsKey("input")))
}

fun toolCallFromDelta(delta: JsonObject): ToolPart {
    findToolCall(delta["toolCall"])?.let { return it }
    findToolCall(delta["tool_call"])?.let { return it }
    findToolCall(delta["content"], delta.int("contentIndex"))?.let { return it }
    findToolCall(delta["partial"])?.let { return it }
    findToolCall(delta["message"], delta.int("contentIndex"))?.let { return it }
    return ToolPart(delta.string("toolCallId"), delta.string("toolName"), null)
}

fun findToolCall(value: JsonElement?, contentIndex: Int? = null): ToolPart? {
    if (value == null) return null
    if (value is JsonArray) {
        if (contentIndex != null) findToolCall(value.getOrNull(contentIndex))?.let { return it }
        value.forEach { findToolCall(it)?.let { found -> return found } }
        return null
    }
    val obj = value.asObject() ?: return null
    obj["content"]?.let { findToolCall(it, contentIndex)?.let { found -> return found } }
    if (!isToolContentPart(obj)) return null
    return ToolPart(
        toolCallId = obj.string("id") ?: obj.string("toolCallId") ?: obj.string("tool_call_id"),
        toolName = obj.string("name") ?: obj.string("toolName") ?: "tool",
        toolArgs = obj["args"] ?: obj["arguments"] ?: obj["input"]
    )
}

fun resultToText(result: JsonElement?): String {
    if (result == null) return ""
    if (result is JsonPrimitive) return result.contentOrNull.orEmpty()
    val obj = result as? JsonObject
    val content = obj?.get("content")
    if (content is JsonArray) return content.joinToString("\n") { item ->
        val io = item.asObject()
        io?.string("text") ?: io?.string("content") ?: pretty(item)
    }
    obj?.string("text")?.let { return it }
    return pretty(result)
}

fun toolResultMeta(result: JsonElement?): ToolResultMeta? {
    if (result == null) return null
    val records = collectRecords(result)
    val text = if (result is JsonPrimitive) result.contentOrNull.orEmpty() else resultToText(result)
    var totalLines = numericMeta(records, "totalLines", "total_lines", "lineCount", "line_count", "totalLineCount", "total_line_count", "linesTotal")
    var shownLines = numericMeta(records, "shownLines", "shown_lines", "displayedLines", "displayed_lines", "returnedLines", "returned_lines", "visibleLines", "visible_lines", "outputLines", "output_lines")
    var omittedLines = numericMeta(records, "omittedLines", "omitted_lines", "truncatedLines", "truncated_lines", "remainingLines", "remaining_lines")
    val totalBytes = numericMeta(records, "totalBytes", "total_bytes", "byteLength", "byte_length", "size", "totalSize", "total_size")
    val shownBytes = numericMeta(records, "shownBytes", "shown_bytes", "displayedBytes", "displayed_bytes", "returnedBytes", "returned_bytes", "outputBytes", "output_bytes")
    var truncated = booleanMeta(records, "truncated", "isTruncated", "is_truncated", "wasTruncated", "was_truncated")
    val label = stringMeta(records, "label", "title", "summary")
    Regex("(?:omitted|省略)\\s*([0-9,]+)\\s*(?:more\\s*)?(?:lines?|行)", RegexOption.IGNORE_CASE).find(text)?.let { match ->
        if (omittedLines == null) omittedLines = match.groupValues[1].replace(",", "").toLongOrNull()
    }
    Regex("(?:total|共)\\s*([0-9,]+)\\s*(?:lines?|行)", RegexOption.IGNORE_CASE).find(text)?.let { match ->
        if (totalLines == null) totalLines = match.groupValues[1].replace(",", "").toLongOrNull()
    }
    if (truncated == null && Regex("\\b(truncated|omitted)\\b|截断|省略", RegexOption.IGNORE_CASE).containsMatchIn(text)) truncated = true
    if (omittedLines != null && truncated != true) truncated = true
    if (truncated == null && totalLines == null && shownLines == null && omittedLines == null && totalBytes == null && shownBytes == null && label == null) return null
    return ToolResultMeta(truncated, totalLines, shownLines, omittedLines, totalBytes, shownBytes, label)
}

private fun collectRecords(value: JsonElement, out: MutableList<JsonObject> = mutableListOf(), depth: Int = 0): List<JsonObject> {
    if (depth > 3) return out
    when (value) {
        is JsonObject -> {
            out += value
            listOf("metadata", "meta", "stats", "summary", "details", "truncation", "content").forEach { key ->
                value[key]?.let { collectRecords(it, out, depth + 1) }
            }
        }
        is JsonArray -> value.take(20).forEach { collectRecords(it, out, depth + 1) }
        else -> Unit
    }
    return out
}

private fun numericMeta(records: List<JsonObject>, vararg keys: String): Long? {
    for (record in records) for (key in keys) {
        val value = (record[key] as? JsonPrimitive)?.contentOrNull?.replace(",", "")?.trim() ?: continue
        val number = value.toDoubleOrNull()?.takeIf { it.isFinite() && it > 0 } ?: continue
        return number.toLong()
    }
    return null
}

private fun booleanMeta(records: List<JsonObject>, vararg keys: String): Boolean? {
    for (record in records) for (key in keys) {
        when (val value = (record[key] as? JsonPrimitive)?.contentOrNull?.trim()) {
            "true", "TRUE", "True" -> return true
            "false", "FALSE", "False" -> return false
        }
    }
    return null
}

private fun stringMeta(records: List<JsonObject>, vararg keys: String): String? {
    for (record in records) for (key in keys) {
        val value = (record[key] as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf { it.isNotBlank() } ?: continue
        return value.take(120)
    }
    return null
}

fun ChatMessage.preview(): String = text.ifBlank { toolResult ?: toolName ?: attachmentSummary(attachments) }

fun attachmentSummary(attachments: List<ChatAttachment>): String {
    val imageCount = attachments.count { it is ChatAttachment.Image }
    val fileCount = attachments.count { it is ChatAttachment.File }
    return listOfNotNull(
        imageCount.takeIf { it > 0 }?.let { "$it 张图片" },
        fileCount.takeIf { it > 0 }?.let { "$it 个文件" }
    ).joinToString("，").ifBlank { "[附件]" }
}

fun pretty(value: JsonElement?): String = value?.let { runCatching { normalizerJson.encodeToString(it) }.getOrDefault(it.toString()) }.orEmpty()

fun JsonElement?.asObject(): JsonObject? = this as? JsonObject
fun JsonObject.string(key: String): String? = this[key]?.jsonPrimitive?.contentOrNull
fun JsonObject.boolean(key: String): Boolean? = this[key]?.jsonPrimitive?.booleanOrNull
fun JsonObject.long(key: String): Long? = this[key]?.jsonPrimitive?.contentOrNull?.toLongOrNull()
fun JsonObject.int(key: String): Int? = this[key]?.jsonPrimitive?.contentOrNull?.toIntOrNull()
fun JsonObject.obj(key: String): JsonObject? = this[key] as? JsonObject

fun newMessageId(): String = UUID.randomUUID().toString()

fun String.removePrefixDataUrl(): String = replace(Regex("^data:[^,]+,"), "")

fun formatTokens(value: Long): String = when {
    value >= 1_000_000 -> "%.1fM".format(value / 1_000_000.0)
    value >= 1_000 -> "${value / 1_000}k"
    else -> value.toString()
}

fun formatBytes(value: Long): String = when {
    value >= 1024 * 1024 -> "%.1fMB".format(value / 1024.0 / 1024.0)
    value >= 1024 -> "%.1fKB".format(value / 1024.0)
    else -> "${value}B"
}

fun formatCount(value: Long): String = "%,d".format(value.coerceAtLeast(0))

fun formatStats(stats: SessionStats?, autoCompact: Boolean): String {
    if (stats?.tokens == null && stats?.contextUsage == null) return "context — (${if (autoCompact) "auto" else "manual"})"
    val tokens = stats?.tokens
    val context = stats?.contextUsage
    val input = formatTokens(tokens?.input ?: 0)
    val output = formatTokens(tokens?.output ?: 0)
    val read = formatTokens(tokens?.cacheRead ?: 0)
    val cost = stats?.cost?.let { "$" + "%.3f".format(it) } ?: "$0.000"
    val percent = context?.percent?.let { "%.1f%%".format(it) } ?: "—%"
    val window = context?.contextWindow?.let { formatTokens(it) } ?: "ctx"
    return "↑$input ↓$output R$read $cost $percent/$window (${if (autoCompact) "auto" else "manual"})"
}
