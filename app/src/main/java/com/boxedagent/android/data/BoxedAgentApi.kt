package com.boxedagent.android.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.encodeToString
import kotlinx.serialization.serializer
import kotlinx.serialization.json.Json
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.IOException
import java.io.File
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class ApiException(message: String, val statusCode: Int? = null, val code: String? = null) : RuntimeException(message)

class InMemoryCookieJar : CookieJar {
    private val storage = ConcurrentHashMap<String, MutableList<Cookie>>()

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        val key = url.host
        val current = storage.getOrPut(key) { mutableListOf() }
        synchronized(current) {
            val names = cookies.map { it.name }.toSet()
            current.removeAll { it.name in names }
            current.addAll(cookies)
        }
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val current = storage[url.host] ?: return emptyList()
        val now = System.currentTimeMillis()
        synchronized(current) {
            current.removeAll { it.expiresAt < now }
            return current.filter { it.matches(url) }
        }
    }

    fun clear() = storage.clear()

    fun cookieHeader(url: String): String {
        val httpUrl = runCatching { url.toHttpUrl() }.getOrNull() ?: return ""
        return loadForRequest(httpUrl).joinToString("; ") { "${it.name}=${it.value}" }
    }
}

class BoxedAgentApi(
    baseUrl: String,
    private var bearerToken: String = ""
) {
    private val cookieJar = InMemoryCookieJar()
    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        encodeDefaults = false
        isLenient = true
        coerceInputValues = true
    }
    private val client = OkHttpClient.Builder()
        .cookieJar(cookieJar)
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .pingInterval(25, TimeUnit.SECONDS)
        .build()

    var baseUrl: String = normalizeBaseUrl(baseUrl)
        private set

    fun updateConnection(baseUrl: String, bearerToken: String) {
        this.baseUrl = normalizeBaseUrl(baseUrl)
        this.bearerToken = bearerToken.trim()
        cookieJar.clear()
    }

    fun token(): String = bearerToken

    suspend fun authStatus(): AuthStatus = get("/api/auth/status")
    suspend fun login(token: String): LoginResponse {
        bearerToken = token.trim()
        return post("/api/auth/login", LoginRequest(token.trim()))
    }
    suspend fun logout(): OkResponse = post("/api/auth/logout", UnitBody)
    suspend fun health(): HealthResponse = get("/api/health")

    suspend fun imageStatus(image: String): ImageStatusResponse = get("/api/images/status?image=${enc(image)}")
    suspend fun ensureImage(image: String): ImageStatusResponse = post("/api/images/ensure", ImageEnsureRequest(image))

    suspend fun listBoxes(): List<BoxRecord> = get<BoxesResponse>("/api/boxes").boxes
    suspend fun createBox(body: CreateBoxRequest): BoxRecord = post("/api/boxes", body)
    suspend fun updateBox(id: String, body: PatchBoxRequest): BoxRecord = patch("/api/boxes/${encPath(id)}", body)
    suspend fun duplicateBox(id: String, body: DuplicateBoxRequest = DuplicateBoxRequest()): BoxRecord = post("/api/boxes/${encPath(id)}/duplicate", body)
    suspend fun cloneBox(id: String, body: CloneBoxRequest): BoxRecord = post("/api/boxes/${encPath(id)}/clone", body)
    suspend fun startBox(id: String): BoxRecord = post("/api/boxes/${encPath(id)}/start", UnitBody)
    suspend fun stopBox(id: String): BoxRecord = post("/api/boxes/${encPath(id)}/stop", UnitBody)
    suspend fun deleteBox(id: String, deleteWorkspace: Boolean = false): OkResponse = delete("/api/boxes/${encPath(id)}?force=true&deleteWorkspace=$deleteWorkspace")
    suspend fun boxModels(boxId: String): List<PiModel> = get<ModelsResponse>("/api/boxes/${encPath(boxId)}/models").models

    suspend fun getPiConfig(boxId: String): PiConfigResponse = get("/api/boxes/${encPath(boxId)}/pi-config")
    suspend fun updatePiConfig(boxId: String, body: PiConfigUpdateRequest): PiConfigResponse = put("/api/boxes/${encPath(boxId)}/pi-config", body)

    suspend fun getCurrentSession(): SelectedSessionResponse = get("/api/current-session")
    suspend fun setCurrentSession(sessionId: String): SelectedSessionResponse = put("/api/current-session", SelectedSessionRequest(sessionId))
    suspend fun clearCurrentSession(): SelectedSessionResponse = delete("/api/current-session")

    suspend fun listSessions(boxId: String? = null): List<AgentSessionRecord> = get<SessionsResponse>("/api/sessions${boxId?.let { "?boxId=${enc(it)}" } ?: ""}").sessions
    suspend fun createSession(body: CreateSessionRequest): AgentSessionRecord = post("/api/sessions", body)
    suspend fun startSession(id: String): AgentSessionRecord = post("/api/sessions/${encPath(id)}/start", UnitBody)
    suspend fun stopSession(id: String): AgentSessionRecord = post("/api/sessions/${encPath(id)}/stop", UnitBody)
    suspend fun updateSession(id: String, body: PatchSessionRequest): AgentSessionRecord = patch("/api/sessions/${encPath(id)}", body)
    suspend fun deleteSession(id: String): OkResponse = delete("/api/sessions/${encPath(id)}")
    suspend fun abortSession(id: String): OkResponse = post("/api/sessions/${encPath(id)}/abort", UnitBody)
    suspend fun duplicateSession(id: String, body: DuplicateSessionRequest = DuplicateSessionRequest()): DuplicateSessionResponse = post("/api/sessions/${encPath(id)}/duplicate", body)
    suspend fun cloneSession(id: String, body: CloneSessionRequest = CloneSessionRequest()): CloneSessionResponse = post("/api/sessions/${encPath(id)}/clone", body)
    suspend fun sessionTree(id: String): SessionTree = get<SessionTreeResponse>("/api/sessions/${encPath(id)}/tree").tree
    suspend fun navigateSessionTree(id: String, body: TreeNavigateRequest): TreeNavigateResponse = post("/api/sessions/${encPath(id)}/tree/navigate", body)
    suspend fun forkMessages(id: String): List<ForkMessage> = get<ForkMessagesResponse>("/api/sessions/${encPath(id)}/fork-messages").messages
    suspend fun forkSession(id: String, body: ForkSessionRequest): ForkSessionResponse = post("/api/sessions/${encPath(id)}/fork", body)
    suspend fun prompt(id: String, body: PromptRequest): PromptResponse = post("/api/sessions/${encPath(id)}/prompt", body)
    suspend fun messages(id: String, expand: List<String> = emptyList()): List<kotlinx.serialization.json.JsonElement> {
        val query = expand.takeIf { it.isNotEmpty() }?.joinToString(",")?.let { "?expand=${enc(it)}" }.orEmpty()
        return get<SessionMessagesResponse>("/api/sessions/${encPath(id)}/messages$query").messages
    }
    suspend fun message(id: String, messageId: String): kotlinx.serialization.json.JsonElement? = get<SessionMessageResponse>("/api/sessions/${encPath(id)}/messages/${encPath(messageId)}").message
    suspend fun sessionState(id: String): SessionStateResponse = get("/api/sessions/${encPath(id)}/state")
    suspend fun sessionStats(id: String): SessionStats? = get<SessionStatsResponse>("/api/sessions/${encPath(id)}/stats").stats
    suspend fun sessionModels(id: String): List<PiModel> = get<ModelsResponse>("/api/sessions/${encPath(id)}/models").models
    suspend fun setSessionModel(id: String, body: SetModelRequest): SetModelResponse = patch("/api/sessions/${encPath(id)}/model", body)
    suspend fun setSessionThinking(id: String, level: String): RuntimePatchResponse = patch("/api/sessions/${encPath(id)}/thinking", SetThinkingRequest(level))
    suspend fun setAutoCompaction(id: String, enabled: Boolean): RuntimePatchResponse = patch("/api/sessions/${encPath(id)}/auto-compaction", SetAutoCompactionRequest(enabled))
    suspend fun compactSession(id: String, customInstructions: String? = null): OkResponse = post("/api/sessions/${encPath(id)}/compact", CompactRequest(customInstructions))

    suspend fun listFiles(boxId: String, path: String): List<FileEntry> = get<FilesResponse>("/api/boxes/${encPath(boxId)}/files?path=${enc(path)}").entries
    suspend fun mkdir(boxId: String, path: String): OkResponse = post("/api/boxes/${encPath(boxId)}/files/mkdir", MkdirRequest(path))
    suspend fun deleteFile(boxId: String, path: String): OkResponse = delete("/api/boxes/${encPath(boxId)}/files?path=${enc(path)}")
    suspend fun uploadFile(boxId: String, path: String, fileName: String, bytes: ByteArray, mimeType: String? = null): UploadResponse = withContext(Dispatchers.IO) {
        val mediaType = (mimeType ?: "application/octet-stream").toMediaType()
        val multipart = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("file", fileName, bytes.toRequestBody(mediaType))
            .build()
        request("POST", "/api/boxes/${encPath(boxId)}/files/upload?path=${enc(path)}", multipart, UploadResponse.serializer())
    }

    suspend fun downloadFile(boxId: String, path: String): DownloadedFile = withContext(Dispatchers.IO) {
        val response = rawRequest("GET", "/api/boxes/${encPath(boxId)}/files/download?path=${enc(path)}", null)
        response.use {
            if (!it.isSuccessful) throw errorFromResponse(it)
            val bytes = it.body?.bytes() ?: ByteArray(0)
            val contentType = it.header("content-type")?.substringBefore(';')?.trim()
            val disposition = it.header("content-disposition")
            val fallback = path.substringAfterLast('/').ifBlank { "download" }
            DownloadedFile(fileNameFromDisposition(disposition) ?: fallback, contentType ?: "application/octet-stream", bytes)
        }
    }

    suspend fun downloadFileTo(
        boxId: String,
        path: String,
        destination: File,
        onProgress: (bytesRead: Long, totalBytes: Long) -> Unit = { _, _ -> }
    ): DownloadedDiskFile = suspendCancellableCoroutine { cont ->
        val builder = Request.Builder().url(url("/api/boxes/${encPath(boxId)}/files/download?path=${enc(path)}")).get()
        addAuth(builder)
        val call = client.newCall(builder.build())
        cont.invokeOnCancellation {
            call.cancel()
            runCatching { destination.delete() }
        }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (!cont.isCancelled) cont.resumeWithException(e)
            }

            override fun onResponse(call: Call, response: Response) {
                response.use { res ->
                    try {
                        if (!res.isSuccessful) throw errorFromResponse(res)
                        val body = res.body ?: throw ApiException("下载响应为空")
                        val contentType = res.header("content-type")?.substringBefore(';')?.trim()
                        val disposition = res.header("content-disposition")
                        val fallback = path.substringAfterLast('/').ifBlank { "download" }
                        val total = body.contentLength()
                        var read = 0L
                        destination.parentFile?.mkdirs()
                        destination.outputStream().use { output ->
                            body.byteStream().use { input ->
                                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                                while (cont.isActive) {
                                    val n = input.read(buffer)
                                    if (n < 0) break
                                    output.write(buffer, 0, n)
                                    read += n
                                    onProgress(read, total)
                                }
                            }
                        }
                        if (!cont.isActive) {
                            call.cancel()
                            runCatching { destination.delete() }
                            return
                        }
                        onProgress(read, total)
                        cont.resume(DownloadedDiskFile(fileNameFromDisposition(disposition) ?: fallback, contentType ?: "application/octet-stream", destination, read))
                    } catch (t: Throwable) {
                        runCatching { destination.delete() }
                        if (!cont.isCancelled) cont.resumeWithException(t)
                    }
                }
            }
        })
    }

    fun downloadUrl(boxId: String, path: String): String = "$baseUrl/api/boxes/${encPath(boxId)}/files/download?path=${enc(path)}"
    fun codeServerUrl(boxId: String): String = "$baseUrl/codeserver/${encPath(boxId)}/"
    fun cookieHeader(): String = cookieJar.cookieHeader(baseUrl)

    fun webSocket(path: String, listener: WebSocketListener): WebSocket {
        val wsUrl = toWsUrl(path)
        val builder = Request.Builder().url(wsUrl)
        addAuth(builder)
        return client.newWebSocket(builder.build(), listener)
    }

    private inline fun <reified T> decode(text: String): T = json.decodeFromString(text)
    private inline fun <reified T> encode(value: T): String = json.encodeToString(value)

    private suspend inline fun <reified T> get(path: String): T = request("GET", path, null, serializer())
    private suspend inline fun <reified Req, reified T> post(path: String, body: Req): T = request("POST", path, jsonBody(body), serializer())
    private suspend inline fun <reified Req, reified T> put(path: String, body: Req): T = request("PUT", path, jsonBody(body), serializer())
    private suspend inline fun <reified Req, reified T> patch(path: String, body: Req): T = request("PATCH", path, jsonBody(body), serializer())
    private suspend inline fun <reified T> delete(path: String): T = request("DELETE", path, null, serializer())

    private inline fun <reified Req> jsonBody(body: Req): RequestBody? {
        if (body === UnitBody) return EMPTY_BODY
        return encode(body).toRequestBody(JSON)
    }

    @PublishedApi
    internal suspend fun <T> request(method: String, path: String, body: RequestBody?, serializer: KSerializer<T>): T = withContext(Dispatchers.IO) {
        val response = rawRequest(method, path, body)
        response.use {
            if (!it.isSuccessful) throw errorFromResponse(it)
            if (it.code == 204) return@withContext json.decodeFromString(serializer, "{}")
            val text = it.body?.string().orEmpty()
            if (text.isBlank()) json.decodeFromString(serializer, "{}") else json.decodeFromString(serializer, text)
        }
    }

    private suspend fun rawRequest(method: String, path: String, body: RequestBody?): Response = suspendCancellableCoroutine { cont ->
        val builder = Request.Builder().url(url(path)).method(method, bodyForMethod(method, body))
        addAuth(builder)
        val call = client.newCall(builder.build())
        cont.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (!cont.isCancelled) cont.resumeWithException(e)
            }

            override fun onResponse(call: Call, response: Response) {
                cont.resume(response)
            }
        })
    }

    private fun bodyForMethod(method: String, body: RequestBody?): RequestBody? = when (method) {
        "POST", "PUT", "PATCH" -> body ?: EMPTY_BODY
        else -> body
    }

    private fun addAuth(builder: Request.Builder) {
        val token = bearerToken.trim()
        if (token.isNotEmpty()) builder.header("Authorization", "Bearer $token")
    }

    private fun url(path: String): String = if (path.startsWith("http://") || path.startsWith("https://")) path else "$baseUrl$path"

    private fun toWsUrl(path: String): String {
        val httpUrl = url(path)
        return when {
            httpUrl.startsWith("https://") -> "wss://" + httpUrl.removePrefix("https://")
            httpUrl.startsWith("http://") -> "ws://" + httpUrl.removePrefix("http://")
            else -> httpUrl
        }
    }

    private fun errorFromResponse(response: Response): ApiException {
        val text = response.body?.string().orEmpty()
        val parsed = runCatching { json.decodeFromString<ErrorResponse>(text) }.getOrNull()
        val message = parsed?.error ?: text.takeIf { it.isNotBlank() } ?: response.message
        return ApiException(message, response.code, parsed?.code)
    }

    companion object {
        private val JSON = "application/json; charset=utf-8".toMediaType()
        private val EMPTY_BODY = ByteArray(0).toRequestBody(null)
        fun normalizeBaseUrl(value: String): String {
            val trimmed = value.trim().removeSuffix("/")
            if (trimmed.isBlank()) return "http://10.0.2.2:8080"
            return if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) trimmed else "http://$trimmed"
        }

        fun enc(value: String): String = URLEncoder.encode(value, "UTF-8").replace("+", "%20")
        fun encPath(value: String): String = enc(value)
    }
}

data class DownloadedFile(val name: String, val mimeType: String, val bytes: ByteArray)
data class DownloadedDiskFile(val name: String, val mimeType: String, val file: File, val bytesWritten: Long)

@kotlinx.serialization.Serializable
private object UnitBody

private fun fileNameFromDisposition(disposition: String?): String? {
    if (disposition.isNullOrBlank()) return null
    val star = Regex("filename\\*=UTF-8''([^;]+)").find(disposition)?.groupValues?.getOrNull(1)
    if (!star.isNullOrBlank()) return java.net.URLDecoder.decode(star, "UTF-8")
    return Regex("filename=\"?([^\";]+)\"?").find(disposition)?.groupValues?.getOrNull(1)
}
