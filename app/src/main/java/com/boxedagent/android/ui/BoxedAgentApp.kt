package com.boxedagent.android.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.CookieManager
import android.webkit.MimeTypeMap
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.FileProvider
import com.boxedagent.android.TerminalActivity
import com.boxedagent.android.data.*
import io.noties.prism4j.GrammarLocator
import io.noties.prism4j.Prism4j
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.Base64
import kotlin.math.max

private val UiJson = Json { prettyPrint = true; ignoreUnknownKeys = true; explicitNulls = false }
private val ThinkingLevels = listOf("off", "minimal", "low", "medium", "high", "xhigh")
private const val PREVIEW_LARGE_FILE_THRESHOLD_BYTES: Long = 10L * 1024L * 1024L
private const val TOOL_CODE_COLLAPSED_CHARS = 12_000

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BoxedAgentApp(viewModel: AppViewModel) {
    val state by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.event?.id) {
        val event = state.event ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(event.message)
        viewModel.clearEvent(event.id)
    }

    if (state.authLoading || !state.authenticated) {
        Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) { padding ->
            ConnectionScreen(state, viewModel, Modifier.padding(padding))
        }
        return
    }

    BackHandler(enabled = state.selectedPanel != MainPanel.Chat) {
        viewModel.setPanel(MainPanel.Chat)
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            ChatScreen(state, viewModel)
            SideOverlay(
                visible = state.selectedPanel == MainPanel.Boxes,
                fromStart = true,
                title = "Boxes / Sessions",
                subtitle = "Docker 沙箱与会话管理",
                onClose = { viewModel.setPanel(MainPanel.Chat) }
            ) { BoxesScreen(state, viewModel) }
            SideOverlay(
                visible = state.selectedPanel == MainPanel.Tools,
                fromStart = false,
                title = "Tools",
                subtitle = "Shell、Files、Pi、code-server",
                onClose = { viewModel.setPanel(MainPanel.Chat) }
            ) { ToolsScreen(state, viewModel) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SideOverlay(
    visible: Boolean,
    fromStart: Boolean,
    title: String,
    subtitle: String,
    onClose: () -> Unit,
    content: @Composable () -> Unit
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(120)) + slideInHorizontally(tween(260)) { full -> if (fromStart) -full else full },
        exit = fadeOut(tween(120)) + slideOutHorizontally(tween(230)) { full -> if (fromStart) -full else full }
    ) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface, tonalElevation = 0.dp) {
            Column(Modifier.fillMaxSize()) {
                Row(
                    Modifier.fillMaxWidth().padding(start = 8.dp, end = 14.dp, top = 10.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    IconButton(onClick = onClose, modifier = Modifier.size(42.dp)) { Icon(Icons.Rounded.Close, contentDescription = "关闭") }
                    Column(Modifier.weight(1f)) {
                        Text(title, fontWeight = FontWeight.Black, fontSize = 22.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                Box(Modifier.weight(1f)) { content() }
            }
        }
    }
}

@Composable
private fun ConnectionScreen(state: AppUiState, viewModel: AppViewModel, modifier: Modifier = Modifier) {
    var baseUrl by remember(state.baseUrl) { mutableStateOf(state.baseUrl) }
    var token by remember(state.token) { mutableStateOf(state.token) }
    Box(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background), contentAlignment = Alignment.Center) {
        ElevatedCard(Modifier.padding(20.dp).fillMaxWidth()) {
            Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("连接 BoxedAgent", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
                Text("输入 BoxedAgent 后端地址与 BOXEDAGENT_TOKEN。模拟器访问宿主机默认使用 http://10.0.2.2:8080。", color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(value = baseUrl, onValueChange = { baseUrl = it; viewModel.updateConnectionFields(baseUrl = it) }, label = { Text("API 地址") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = token, onValueChange = { token = it; viewModel.updateConnectionFields(token = it) }, label = { Text("Token（未启用认证可留空）") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                state.connectionError?.let { AssistChip(onClick = {}, label = { Text(it) }, colors = AssistChipDefaults.assistChipColors(labelColor = MaterialTheme.colorScheme.error)) }
                Button(onClick = { viewModel.updateConnectionFields(baseUrl, token); viewModel.connectFromState() }, enabled = !state.authLoading, modifier = Modifier.fillMaxWidth()) {
                    Text(if (state.authLoading) "连接中…" else "连接 / 登录")
                }
            }
        }
    }
}

@Composable
private fun BoxesScreen(state: AppUiState, viewModel: AppViewModel) {
    var createBox by remember { mutableStateOf(false) }
    var createSession by remember { mutableStateOf(false) }
    var renameBox by remember { mutableStateOf<BoxRecord?>(null) }
    var cloneBox by remember { mutableStateOf<BoxRecord?>(null) }
    var deleteBox by remember { mutableStateOf<BoxRecord?>(null) }
    var renameSession by remember { mutableStateOf<AgentSessionRecord?>(null) }
    var deleteSession by remember { mutableStateOf<AgentSessionRecord?>(null) }
    var forkSession by remember { mutableStateOf<AgentSessionRecord?>(null) }
    val sessionsForBox = state.sessions.filter { it.boxId == state.activeBoxId }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column { Text("Boxes", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold); Text("Docker 沙箱与 Session 管理", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                FilledTonalButton(onClick = { createBox = true }, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)) { Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(16.dp)); Text("Box") }
            }
        }
        if (state.boxes.isEmpty()) item { EmptyCard("还没有 Box", "点击右上角创建一个 Ubuntu 开发沙箱。") }
        items(state.boxes, key = { it.id }) { box ->
            BoxCard(
                box = box,
                active = box.id == state.activeBoxId,
                onSelect = { viewModel.selectBox(box.id) },
                onStartStop = { if (box.status == "running") viewModel.stopBox(box.id) else viewModel.startBox(box.id) },
                onRename = { renameBox = box },
                onClone = { cloneBox = box },
                onDelete = { deleteBox = box }
            )
        }
        item { HorizontalDivider(Modifier.padding(vertical = 4.dp)) }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column { Text("Sessions", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold); Text(state.activeBox?.name ?: "请先选择 Box", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                FilledTonalButton(onClick = { createSession = true }, enabled = state.activeBox != null, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)) { Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(16.dp)); Text("Session") }
            }
        }
        if (state.activeBox != null && sessionsForBox.isEmpty()) item { EmptyCard("当前 Box 没有 Session", "创建 Session 后即可对话。") }
        items(sessionsForBox, key = { it.id }) { session ->
            SessionCard(
                session = session,
                active = session.id == state.activeSessionId,
                onSelect = { viewModel.selectSession(session.id); viewModel.setPanel(MainPanel.Chat) },
                onStartStop = { if (session.status == "running" || session.status == "working") viewModel.stopSession(session.id) else viewModel.startSession(session.id) },
                onRename = { renameSession = session },
                onDuplicate = { viewModel.duplicateSession(session.id) },
                onFork = { forkSession = session },
                onDelete = { deleteSession = session }
            )
        }
    }

    if (createBox) CreateBoxDialog(onDismiss = { createBox = false }, onCreate = { name, image, desc, password, provider, model, thinking ->
        createBox = false; viewModel.createBox(name, image, desc, password, provider, model, thinking)
    })
    state.activeBox?.let { activeBox -> if (createSession) CreateSessionDialog(activeBox, viewModel, onDismiss = { createSession = false }) }
    renameBox?.let { box -> InputDialog("重命名 Box", box.name, onDismiss = { renameBox = null }, onConfirm = { viewModel.renameBox(box.id, it); renameBox = null }) }
    cloneBox?.let { box -> InputDialog("克隆 Box", "${box.name}-clone", onDismiss = { cloneBox = null }, onConfirm = { viewModel.cloneBox(box.id, it); cloneBox = null }) }
    deleteBox?.let { box -> ConfirmDialog("删除 Box", "删除 ${box.name}? Session 会一起删除。", onDismiss = { deleteBox = null }, onConfirm = { viewModel.deleteBox(box.id); deleteBox = null }) }
    renameSession?.let { s -> InputDialog("重命名 Session", s.name, onDismiss = { renameSession = null }, onConfirm = { viewModel.renameSession(s.id, it); renameSession = null }) }
    deleteSession?.let { s -> ConfirmDialog("删除 Session", "删除 ${s.name}?", onDismiss = { deleteSession = null }, onConfirm = { viewModel.deleteSession(s.id); deleteSession = null }) }
    forkSession?.let { s -> ForkDialog(s, viewModel, onDismiss = { forkSession = null }) }
}

@Composable
private fun BoxCard(box: BoxRecord, active: Boolean, onSelect: () -> Unit, onStartStop: () -> Unit, onRename: () -> Unit, onClone: () -> Unit, onDelete: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    ElevatedCard(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onSelect),
        colors = CardDefaults.elevatedCardColors(containerColor = if (active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(box.name, fontWeight = FontWeight.Bold, fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    StatusDot(box.status)
                }
                Text(box.image, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                box.description?.takeIf { it.isNotBlank() }?.let { Text(it, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                box.error?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis) }
            }
            Box { IconButton(onClick = { menu = true }, modifier = Modifier.size(40.dp)) { Icon(Icons.Rounded.MoreVert, contentDescription = "操作") }; DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text(if (box.status == "running") "停止" else "启动") }, onClick = { menu = false; onStartStop() })
                DropdownMenuItem(text = { Text("重命名") }, onClick = { menu = false; onRename() })
                DropdownMenuItem(text = { Text("克隆") }, onClick = { menu = false; onClone() })
                DropdownMenuItem(text = { Text("删除") }, onClick = { menu = false; onDelete() })
            } }
        }
    }
}

@Composable
private fun SessionCard(session: AgentSessionRecord, active: Boolean, onSelect: () -> Unit, onStartStop: () -> Unit, onRename: () -> Unit, onDuplicate: () -> Unit, onFork: () -> Unit, onDelete: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    ElevatedCard(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onSelect),
        colors = CardDefaults.elevatedCardColors(containerColor = if (active) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(session.name, fontWeight = FontWeight.Bold, fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    StatusDot(session.status)
                }
                Text(listOf(session.provider, session.model, session.thinkingLevel, session.cwd ?: "/workspace").filterNotNull().joinToString(" · "), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                session.error?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis) }
            }
            Box { IconButton(onClick = { menu = true }, modifier = Modifier.size(40.dp)) { Icon(Icons.Rounded.MoreVert, contentDescription = "操作") }; DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text(if (session.status == "running" || session.status == "working") "停止" else "启动") }, onClick = { menu = false; onStartStop() })
                DropdownMenuItem(text = { Text("重命名") }, onClick = { menu = false; onRename() })
                DropdownMenuItem(text = { Text("Fork") }, onClick = { menu = false; onFork() })
                DropdownMenuItem(text = { Text("复刻") }, onClick = { menu = false; onDuplicate() })
                DropdownMenuItem(text = { Text("删除") }, onClick = { menu = false; onDelete() })
            } }
        }
    }
}

@Composable
private fun statusColor(status: String): Color = when (status) {
    "running" -> Color(0xFF22C55E)
    "working" -> Color(0xFF3B82F6)
    "error" -> MaterialTheme.colorScheme.error
    "starting", "creating" -> Color(0xFFF59E0B)
    else -> MaterialTheme.colorScheme.outline
}

@Composable
private fun StatusDot(status: String) {
    Box(Modifier.size(10.dp).clip(CircleShape).background(statusColor(status)))
}

@Composable
private fun StatusChip(status: String) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            StatusDot(status)
            Text(status, fontSize = 12.sp, maxLines = 1)
        }
    }
}

@Composable
private fun EmptyCard(title: String, subtitle: String) {
    OutlinedCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) { Text(title, fontWeight = FontWeight.Bold); Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant) } }
}

@Composable
private fun CreateBoxDialog(onDismiss: () -> Unit, onCreate: (String, String, String, String, String, String, String) -> Unit) {
    var name by remember { mutableStateOf("box-${(100..999).random()}") }
    var image by remember { mutableStateOf("boxedagent/ubuntu-dev:24.04") }
    var password by remember { mutableStateOf("boxedagent") }
    var provider by remember { mutableStateOf("") }
    var model by remember { mutableStateOf("") }
    var thinking by remember { mutableStateOf("medium") }
    var desc by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = onDismiss, confirmButton = { Button(enabled = name.isNotBlank() && image.isNotBlank(), onClick = { onCreate(name, image, desc, password, provider, model, thinking) }) { Text("创建") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }, title = { Text("创建 Box") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(name, { name = it }, label = { Text("名称") }, singleLine = true)
            OutlinedTextField(image, { image = it }, label = { Text("Docker 镜像") }, singleLine = true)
            OutlinedTextField(desc, { desc = it }, label = { Text("描述") }, singleLine = true)
            OutlinedTextField(password, { password = it }, label = { Text("code-server 密码") }, singleLine = true)
            OutlinedTextField(provider, { provider = it }, label = { Text("默认 Provider") }, singleLine = true)
            OutlinedTextField(model, { model = it }, label = { Text("默认 Model") }, singleLine = true)
            DropdownField("Thinking", thinking, ThinkingLevels, { thinking = it })
            Text("默认镜像不存在时后端会自动构建；创建后会生成默认会话。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    })
}

@Composable
private fun CreateSessionDialog(box: BoxRecord, viewModel: AppViewModel, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("Session") }
    var cwd by remember { mutableStateOf("/workspace") }
    var thinking by remember { mutableStateOf(box.pi.defaultThinkingLevel ?: "medium") }
    var provider by remember { mutableStateOf(box.pi.defaultProvider.orEmpty()) }
    var model by remember { mutableStateOf(box.pi.defaultModel.orEmpty()) }
    var models by remember { mutableStateOf<List<PiModel>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var search by remember { mutableStateOf("") }
    LaunchedEffect(box.id) { loading = true; models = runCatching { viewModel.loadBoxModels(box.id) }.getOrDefault(emptyList()); loading = false }
    val visible = remember(models, search) { models.filter { "${it.providerNameOrNull().orEmpty()} ${it.id} ${it.name.orEmpty()}".contains(search, ignoreCase = true) }.take(80) }
    AlertDialog(onDismissRequest = onDismiss, confirmButton = { Button(onClick = { viewModel.createSession(name, cwd, provider, model, thinking); onDismiss() }) { Text("创建") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }, title = { Text("新建 Session") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Box：${box.name}", color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedTextField(name, { name = it }, label = { Text("名称") }, singleLine = true)
            OutlinedTextField(cwd, { cwd = it }, label = { Text("工作目录") }, singleLine = true)
            DropdownField("Thinking", thinking, ThinkingLevels, { thinking = it })
            OutlinedTextField(provider, { provider = it }, label = { Text("Provider（可留空使用 Box 默认）") }, singleLine = true)
            OutlinedTextField(model, { model = it }, label = { Text("Model（可留空使用 Box 默认）") }, singleLine = true)
            OutlinedTextField(search, { search = it }, label = { Text("搜索可用模型") }, singleLine = true)
            if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            visible.forEach { m -> AssistChip(onClick = { provider = m.providerNameOrNull().orEmpty(); model = m.id }, label = { Text("${m.providerNameOrNull() ?: "?"}/${m.name ?: m.id}", maxLines = 1, overflow = TextOverflow.Ellipsis) }) }
        }
    })
}

@Composable
private fun ForkDialog(session: AgentSessionRecord, viewModel: AppViewModel, onDismiss: () -> Unit) {
    var messages by remember { mutableStateOf<List<ForkMessage>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(session.id) { loading = true; error = null; runCatching { viewModel.loadForkMessages(session.id) }.onSuccess { messages = it }.onFailure { error = it.message }; loading = false }
    AlertDialog(onDismissRequest = onDismiss, confirmButton = { TextButton(onClick = onDismiss) { Text("关闭") } }, title = { Text("Fork Session") }, text = {
        Column(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("选择一个用户消息，从该消息之前分叉。", color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            if (!loading && messages.isEmpty()) Text("没有可 fork 的用户消息")
            messages.forEachIndexed { i, msg ->
                OutlinedCard(Modifier.fillMaxWidth().clickable { viewModel.forkSession(session.id, msg.entryId); onDismiss() }) {
                    Text("#${i + 1} ${msg.text.take(160)}", Modifier.padding(12.dp))
                }
            }
        }
    })
}

@Composable
private fun InputDialog(title: String, initial: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var value by remember(initial) { mutableStateOf(initial) }
    AlertDialog(onDismissRequest = onDismiss, confirmButton = { Button(enabled = value.isNotBlank(), onClick = { onConfirm(value.trim()) }) { Text("确定") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }, title = { Text(title) }, text = { OutlinedTextField(value, { value = it }, singleLine = true, modifier = Modifier.fillMaxWidth()) })
}

@Composable
private fun ConfirmDialog(title: String, text: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, confirmButton = { Button(onClick = onConfirm, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { Text("删除") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }, title = { Text(title) }, text = { Text(text) })
}

@Composable
private fun DropdownField(label: String, value: String, options: List<String>, onChange: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box { OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) { Text("$label：$value") }; DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) { options.forEach { DropdownMenuItem(text = { Text(it) }, onClick = { expanded = false; onChange(it) }) } } }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ChatScreen(state: AppUiState, viewModel: AppViewModel) {
    val context = LocalContext.current
    var text by remember(state.activeSessionId) { mutableStateOf("") }
    var attachments by remember(state.activeSessionId) { mutableStateOf<List<DraftAttachment>>(emptyList()) }
    var showThinkingMenu by remember { mutableStateOf(false) }
    var showModelMenu by remember { mutableStateOf(false) }
    var showCompactMenu by remember { mutableStateOf(false) }
    var sendModeMenu by remember { mutableStateOf(false) }
    var showSearchSheet by remember { mutableStateOf(false) }
    var createSession by remember { mutableStateOf(false) }
    var forkDialogSession by remember { mutableStateOf<AgentSessionRecord?>(null) }
    var dialogMessage by remember { mutableStateOf<String?>(null) }
    var quickActionsVisible by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val pickFiles = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        val picked = uris.mapNotNull { context.readDraftAttachment(it) }
        attachments = attachments + picked
        text = appendComposerRefs(text, picked.map { fileRef(uploadedAttachmentPath(it.name)) })
    }
    val lastMessage = state.activeMessages.lastOrNull()
    val latestProgressMessage = state.activeMessages.lastOrNull { it.role == "tool" || (it.role == "assistant" && it.thinking?.isNotBlank() == true) }
    val latestProgressMessageId = latestProgressMessage?.id

    LaunchedEffect(state.composerInsert?.id) {
        val insert = state.composerInsert ?: return@LaunchedEffect
        if (insert.sessionId == null || insert.sessionId == state.activeSessionId) {
            text = appendComposerText(text, insert.text)
            viewModel.clearComposerInsert(insert.id)
        }
    }

    LaunchedEffect(
        state.activeMessages.size,
        lastMessage?.id,
        lastMessage?.text?.length,
        lastMessage?.thinking?.length,
        lastMessage?.toolResult?.length,
        lastMessage?.toolStatus,
        latestProgressMessageId,
        latestProgressMessage?.thinking?.length,
        latestProgressMessage?.toolResult?.length,
        latestProgressMessage?.toolStatus,
        state.activeTurn
    ) {
        if (state.activeMessages.isNotEmpty() || state.activeTurn) {
            withFrameNanos { }
            val total = listState.layoutInfo.totalItemsCount
            if (total > 0) listState.scrollToItem(total - 1)
        }
    }
    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }
            .distinctUntilChanged()
            .collect { scrolling ->
                if (scrolling) quickActionsVisible = false
                else {
                    quickActionsVisible = true
                    delay(2400)
                    quickActionsVisible = false
                }
            }
    }

    val activeBox = state.activeBox
    if (activeBox == null) {
        Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { viewModel.setPanel(MainPanel.Boxes) }) { Icon(Icons.Rounded.Menu, contentDescription = "Boxes", tint = MaterialTheme.colorScheme.onSurface) }
                Text("BoxedAgent", fontSize = 23.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
                IconButton(onClick = { viewModel.setPanel(MainPanel.Tools) }) { Icon(Icons.Rounded.FormatListBulleted, contentDescription = "Tools", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            CenterWelcome("选择或创建 Box", "每个 Box 都是独立 Docker 沙箱。点击左上角打开 Boxes。")
        }
        return
    }
    val session = state.activeSession
    if (session == null) {
        Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { viewModel.setPanel(MainPanel.Boxes) }) { Icon(Icons.Rounded.Menu, contentDescription = "Boxes", tint = MaterialTheme.colorScheme.onSurface) }
                Text(activeBox.name, fontSize = 23.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
                IconButton(onClick = { viewModel.setPanel(MainPanel.Tools) }) { Icon(Icons.Rounded.FormatListBulleted, contentDescription = "Tools", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            CenterWelcome("选择或创建 Session", "Session 负责和 Box 内的 pi RPC agent 对话。点击左上角打开 Sessions。")
        }
        return
    }
    val canSend = text.isNotBlank() || attachments.isNotEmpty()
    val chatBg = MaterialTheme.colorScheme.background

    Column(Modifier.fillMaxSize().background(chatBg)) {
        ChatTopBar(
            session = session,
            stats = state.activeStats,
            autoCompact = session.autoCompactionEnabled != false,
            isWorking = state.activeTurn,
            onBoxes = { viewModel.setPanel(MainPanel.Boxes) },
            onTools = { viewModel.setPanel(MainPanel.Tools) },
            onNewSession = { createSession = true }
        )
        Box(Modifier.weight(1f).fillMaxWidth()) {
            LazyColumn(
                Modifier.fillMaxSize(),
                state = listState,
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                if (state.messagesLoading) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
                if (!state.messagesLoading && state.activeMessages.isEmpty()) item { WelcomePrompts(onPrompt = { text = it }) }
                items(state.activeMessages, key = { it.id }) { msg ->
                    MessageBubble(
                        message = msg,
                        autoOpenProgress = msg.id == latestProgressMessageId,
                        onFork = { forkDialogSession = session },
                        onShowDialog = { dialogMessage = msg.text.ifBlank { msg.toolResult.orEmpty() } }
                    )
                }
                if (state.activeTurn) item { ProcessingCard() }
                item(key = "__chat_bottom") { Spacer(Modifier.height(1.dp)) }
            }
            ScrollQuickActions(
                visible = quickActionsVisible && state.activeMessages.any { it.role == "user" },
                listState = listState,
                messages = state.activeMessages,
                modifier = Modifier.align(Alignment.CenterEnd).padding(end = 8.dp)
            )
        }
        Box(Modifier.fillMaxWidth().imePadding()) {
            ChatComposer(
                text = text,
                onTextChange = { text = it },
                attachments = attachments,
                onRemoveAttachment = { attachments = attachments - it; text = removeComposerRef(text, uploadedAttachmentPath(it.name)) },
                onPickFiles = { pickFiles.launch(arrayOf("*/*")) },
                onSearchClick = { showSearchSheet = true },
                canSend = canSend,
                isWorking = state.activeTurn,
                thinking = session.thinkingLevel ?: "medium",
                model = session.model ?: "模型",
                autoCompact = session.autoCompactionEnabled != false,
                showThinkingMenu = showThinkingMenu,
                onShowThinkingMenu = { showThinkingMenu = it },
                showModelMenu = showModelMenu,
                onShowModelMenu = { showModelMenu = it; if (it) viewModel.loadSessionModels() },
                showCompactMenu = showCompactMenu,
                onShowCompactMenu = { showCompactMenu = it },
                showSendModeMenu = sendModeMenu,
                onShowSendModeMenu = { sendModeMenu = it },
                state = state,
                viewModel = viewModel,
                onSend = { mode -> viewModel.sendPrompt(text, attachments, mode); text = ""; attachments = emptyList() },
                onAbort = { viewModel.abortActive() }
            )
        }
    }
    if (createSession) CreateSessionDialog(activeBox, viewModel, onDismiss = { createSession = false })
    forkDialogSession?.let { ForkDialog(it, viewModel, onDismiss = { forkDialogSession = null }) }
    dialogMessage?.let { MessageDialog(it, onDismiss = { dialogMessage = null }) }
    if (showSearchSheet) {
        SearchMessagesSheet(
            messages = state.activeMessages,
            onDismiss = { showSearchSheet = false },
            onJump = { msg ->
                showSearchSheet = false
                val index = state.activeMessages.indexOfFirst { it.id == msg.id }
                if (index >= 0) scope.launch { listState.animateScrollToItem(index) }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchMessagesSheet(messages: List<ChatMessage>, onDismiss: () -> Unit, onJump: (ChatMessage) -> Unit) {
    var query by remember { mutableStateOf("") }
    val results = remember(messages, query) {
        val q = query.trim()
        if (q.isBlank()) messages.takeLast(30).asReversed()
        else messages.filter { it.preview().contains(q, ignoreCase = true) }
    }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        SheetHeader(Icons.Rounded.Search, "搜索消息", "搜索当前 Session 的用户、助手与工具消息")
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text("输入关键词") },
            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp)
        )
        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 460.dp), contentPadding = PaddingValues(vertical = 8.dp)) {
            items(results, key = { it.id }) { msg ->
                ListItem(
                    headlineContent = { Text(msg.preview().ifBlank { msg.role }, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                    supportingContent = { Text(msg.role, color = MaterialTheme.colorScheme.onSurfaceVariant) },
                    leadingContent = { Icon(when (msg.role) { "user" -> Icons.Rounded.Person; "assistant" -> Icons.Rounded.AutoAwesome; "tool" -> Icons.Rounded.Build; else -> Icons.Rounded.Info }, contentDescription = null) },
                    modifier = Modifier.clickable { onJump(msg) }
                )
            }
            if (results.isEmpty()) item { Text("没有匹配消息", Modifier.padding(18.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

@Composable
private fun MessageDialog(text: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("关闭") } },
        title = { Text("消息内容") },
        text = { Box(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState())) { MarkdownishText(text) } }
    )
}

@Composable
private fun ScrollQuickActions(visible: Boolean, listState: LazyListState, messages: List<ChatMessage>, modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    val total = listState.layoutInfo.totalItemsCount
    val userIndexes = remember(messages) { messages.mapIndexedNotNull { index, msg -> index.takeIf { msg.role == "user" } } }
    fun jump(target: Int?) {
        if (target == null) return
        scope.launch { listState.animateScrollToItem(target.coerceIn(0, maxOf(0, total - 1))) }
    }
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = fadeIn(tween(180)) + slideInHorizontally(tween(220)) { it },
        exit = fadeOut(tween(150)) + slideOutHorizontally(tween(190)) { it }
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            QuickJumpButton(Icons.Rounded.KeyboardDoubleArrowUp, "顶部") { jump(0) }
            QuickJumpButton(Icons.Rounded.KeyboardArrowUp, "上一条用户消息") { jump(userIndexes.lastOrNull { it < listState.firstVisibleItemIndex }) }
            QuickJumpButton(Icons.Rounded.KeyboardArrowDown, "下一条用户消息") { jump(userIndexes.firstOrNull { it > listState.firstVisibleItemIndex }) }
            QuickJumpButton(Icons.Rounded.KeyboardDoubleArrowDown, "底部") { jump(total - 1) }
        }
    }
}

@Composable
private fun QuickJumpButton(icon: androidx.compose.ui.graphics.vector.ImageVector, contentDescription: String, onClick: () -> Unit) {
    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = .92f), shadowElevation = 8.dp) {
        IconButton(onClick = onClick, modifier = Modifier.size(48.dp)) { Icon(icon, contentDescription = contentDescription, modifier = Modifier.size(30.dp)) }
    }
}

@Composable
private fun ChatTopBar(
    session: AgentSessionRecord,
    stats: SessionStats?,
    autoCompact: Boolean,
    isWorking: Boolean,
    onBoxes: () -> Unit,
    onTools: () -> Unit,
    onNewSession: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth().padding(start = 12.dp, end = 8.dp, top = 10.dp, bottom = 4.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            IconButton(onClick = onBoxes, modifier = Modifier.size(40.dp)) { Icon(Icons.Rounded.Menu, contentDescription = "Boxes", modifier = Modifier.size(30.dp), tint = colors.onSurface) }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(session.name, fontSize = 19.sp, fontWeight = FontWeight.SemiBold, color = colors.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(listOf(session.provider ?: "默认助手", session.model).filterNotNull().joinToString(" / "), fontSize = 12.sp, color = colors.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (isWorking) StatusChip("working")
            IconButton(onClick = onTools, modifier = Modifier.size(38.dp)) { Icon(Icons.Rounded.FormatListBulleted, contentDescription = "Tools", modifier = Modifier.size(28.dp), tint = colors.onSurfaceVariant) }
            IconButton(onClick = onNewSession, modifier = Modifier.size(38.dp)) { Icon(Icons.Rounded.AddComment, contentDescription = "新建 Session", modifier = Modifier.size(28.dp), tint = colors.onSurfaceVariant) }
        }
        TopStatsLine(stats, autoCompact, Modifier.fillMaxWidth().padding(start = 48.dp, end = 2.dp))
    }
}

@Composable
private fun TopStatsLine(stats: SessionStats?, autoCompact: Boolean, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val tokens = stats?.tokens
    val context = stats?.contextUsage
    Row(modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        StatItem(Icons.Rounded.ArrowUpward, formatTokens(tokens?.input ?: 0), colors.onSurfaceVariant)
        StatItem(Icons.Rounded.ArrowDownward, formatTokens(tokens?.output ?: 0), colors.onSurfaceVariant)
        StatItem(Icons.Rounded.Memory, "R${formatTokens(tokens?.cacheRead ?: 0)}", colors.onSurfaceVariant)
        StatItem(Icons.Rounded.Paid, stats?.cost?.let { "$${"%.3f".format(it)}" } ?: "$0.000", colors.onSurfaceVariant)
        StatItem(Icons.Rounded.DataUsage, "${context?.percent?.let { "%.1f%%".format(it) } ?: "—%"}/${context?.contextWindow?.let { formatTokens(it) } ?: "ctx"}", colors.onSurfaceVariant)
        Text(if (autoCompact) "(auto)" else "(manual)", color = colors.onSurfaceVariant, fontSize = 12.sp, maxLines = 1)
    }
}

@Composable
private fun ProcessingCard() {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.72f),
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.primary)
            Text("pi 正在处理…", fontWeight = FontWeight.SemiBold)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun ChatComposer(
    text: String,
    onTextChange: (String) -> Unit,
    attachments: List<DraftAttachment>,
    onRemoveAttachment: (DraftAttachment) -> Unit,
    onPickFiles: () -> Unit,
    onSearchClick: () -> Unit,
    canSend: Boolean,
    isWorking: Boolean,
    thinking: String,
    model: String,
    autoCompact: Boolean,
    showThinkingMenu: Boolean,
    onShowThinkingMenu: (Boolean) -> Unit,
    showModelMenu: Boolean,
    onShowModelMenu: (Boolean) -> Unit,
    showCompactMenu: Boolean,
    onShowCompactMenu: (Boolean) -> Unit,
    showSendModeMenu: Boolean,
    onShowSendModeMenu: (Boolean) -> Unit,
    state: AppUiState,
    viewModel: AppViewModel,
    onSend: (String?) -> Unit,
    onAbort: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = RoundedCornerShape(28.dp),
        tonalElevation = 0.dp
    ) {
        Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (state.activeQueue.steering.isNotEmpty() || state.activeQueue.followUp.isNotEmpty()) {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    state.activeQueue.steering.forEach { AssistChip(onClick = {}, label = { Text("Steer · $it") }) }
                    state.activeQueue.followUp.forEach { AssistChip(onClick = {}, label = { Text("Follow-up · $it") }) }
                }
            }
            if (attachments.isNotEmpty()) FlowRow(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.Start),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                attachments.forEach { a ->
                    InputChip(
                        selected = false,
                        onClick = { onRemoveAttachment(a) },
                        leadingIcon = { Icon(attachmentIcon(a.name, a.mimeType, a.isImage), contentDescription = null, modifier = Modifier.size(17.dp)) },
                        label = { Text("${a.name} ${formatBytes(a.size)}", maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 12.sp) },
                        colors = InputChipDefaults.inputChipColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                            labelColor = MaterialTheme.colorScheme.onSurface,
                            leadingIconColor = MaterialTheme.colorScheme.primary
                        ),
                        border = InputChipDefaults.inputChipBorder(
                            enabled = true,
                            selected = false,
                            borderColor = MaterialTheme.colorScheme.outlineVariant
                        )
                    )
                }
            }
            Surface(color = MaterialTheme.colorScheme.surfaceContainerHighest, shape = RoundedCornerShape(22.dp), modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp, max = 132.dp)) {
                Box(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
                    if (text.isBlank()) Text("输入消息与 AI 聊天", fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    BasicTextField(
                        value = text,
                        onValueChange = onTextChange,
                        textStyle = TextStyle(fontSize = 15.sp, lineHeight = 21.sp, color = MaterialTheme.colorScheme.onSurface),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                val active = MaterialTheme.colorScheme.primary
                val inactive = MaterialTheme.colorScheme.onSurfaceVariant
                ComposerIconButton(Icons.Rounded.AutoAwesome, "模型", selected = model != "模型", onClick = { onShowModelMenu(true) })
                ComposerIconButton(Icons.Rounded.Search, "搜索消息", onClick = onSearchClick)
                ComposerIconButton(Icons.Rounded.Lightbulb, "Thinking $thinking", selected = thinking != "off", onClick = { onShowThinkingMenu(true) })
                ComposerIconButton(Icons.Rounded.Tune, "Compact", selected = autoCompact, onClick = { onShowCompactMenu(true) })
                ComposerIconButton(Icons.Rounded.AttachFile, "附件", selected = attachments.isNotEmpty(), onClick = onPickFiles)
                ComposerIconButton(Icons.Rounded.Add, "更多", onClick = { onShowCompactMenu(true) })
                if (isWorking && !canSend) {
                    FilledIconButton(onClick = onAbort, modifier = Modifier.size(38.dp), colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.error)) { Icon(Icons.Rounded.Stop, contentDescription = "中止", modifier = Modifier.size(22.dp)) }
                } else {
                    FilledIconButton(
                        onClick = { if (isWorking) onShowSendModeMenu(true) else onSend(null) },
                        enabled = canSend,
                        modifier = Modifier.size(38.dp),
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = if (canSend) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = if (canSend) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                            disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .55f)
                        )
                    ) { Icon(Icons.Rounded.ArrowUpward, contentDescription = "发送", modifier = Modifier.size(22.dp)) }
                }
            }
        }
    }
    ChatOptionSheets(
        showThinkingMenu = showThinkingMenu,
        onShowThinkingMenu = onShowThinkingMenu,
        showModelMenu = showModelMenu,
        onShowModelMenu = onShowModelMenu,
        showCompactMenu = showCompactMenu,
        onShowCompactMenu = onShowCompactMenu,
        showSendModeMenu = showSendModeMenu,
        onShowSendModeMenu = onShowSendModeMenu,
        text = text,
        onTextChange = onTextChange,
        autoCompact = autoCompact,
        state = state,
        viewModel = viewModel,
        onSend = onSend
    )
}

@Composable
private fun ComposerIconButton(icon: androidx.compose.ui.graphics.vector.ImageVector, contentDescription: String, selected: Boolean = false, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size(38.dp)) {
        Icon(
            icon,
            contentDescription = contentDescription,
            tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(22.dp)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatOptionSheets(
    showThinkingMenu: Boolean,
    onShowThinkingMenu: (Boolean) -> Unit,
    showModelMenu: Boolean,
    onShowModelMenu: (Boolean) -> Unit,
    showCompactMenu: Boolean,
    onShowCompactMenu: (Boolean) -> Unit,
    showSendModeMenu: Boolean,
    onShowSendModeMenu: (Boolean) -> Unit,
    text: String,
    onTextChange: (String) -> Unit,
    autoCompact: Boolean,
    state: AppUiState,
    viewModel: AppViewModel,
    onSend: (String?) -> Unit
) {
    if (showThinkingMenu) {
        ModalBottomSheet(onDismissRequest = { onShowThinkingMenu(false) }) {
            SheetHeader(Icons.Rounded.Lightbulb, "思考强度", "选择当前 Session 的 thinking level")
            ThinkingLevels.forEach { level ->
                ListItem(
                    headlineContent = { Text(level) },
                    supportingContent = { Text(thinkingDescription(level)) },
                    leadingContent = { if (state.activeSession?.thinkingLevel == level) Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary) else Icon(Icons.Rounded.Circle, contentDescription = null) },
                    modifier = Modifier.clickable { onShowThinkingMenu(false); viewModel.chooseThinking(level) }
                )
            }
            Spacer(Modifier.height(18.dp))
        }
    }
    if (showModelMenu) {
        var search by remember { mutableStateOf("") }
        val models = remember(state.sessionModels, search) { state.sessionModels.filter { "${it.providerNameOrNull().orEmpty()} ${it.id} ${it.name.orEmpty()}".contains(search, ignoreCase = true) }.take(160) }
        ModalBottomSheet(onDismissRequest = { onShowModelMenu(false) }) {
            SheetHeader(Icons.Rounded.AutoAwesome, "模型", "切换当前 agent runtime 使用的 provider / model")
            OutlinedTextField(search, { search = it }, leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) }, label = { Text("搜索 provider / model") }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp))
            if (state.modelLoading) LinearProgressIndicator(Modifier.fillMaxWidth().padding(18.dp))
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 420.dp), contentPadding = PaddingValues(vertical = 8.dp)) {
                items(models, key = { "${it.providerNameOrNull()}/${it.id}" }) { model ->
                    val selected = state.activeSession?.model == model.id && state.activeSession?.provider == model.providerNameOrNull()
                    ListItem(
                        headlineContent = { Text(model.name ?: model.id, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        supportingContent = { Text("${model.providerNameOrNull() ?: "unknown"} · ${model.id}${model.contextWindow?.let { " · ${formatTokens(it)} ctx" } ?: ""}", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        leadingContent = { Icon(if (selected) Icons.Rounded.CheckCircle else Icons.Rounded.SmartToy, contentDescription = null, tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant) },
                        modifier = Modifier.clickable { onShowModelMenu(false); viewModel.setSessionModel(model) }
                    )
                }
                if (!state.modelLoading && models.isEmpty()) item { Text("没有可显示的模型", Modifier.padding(18.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
    }
    if (showCompactMenu) {
        ModalBottomSheet(onDismissRequest = { onShowCompactMenu(false) }) {
            SheetHeader(Icons.Rounded.Tune, "Compact / 更多", "上下文压缩与附加操作")
            ListItem(
                headlineContent = { Text("自动 Compact") },
                supportingContent = { Text(if (autoCompact) "已开启：上下文接近上限时自动压缩" else "点击开启自动压缩") },
                leadingContent = { Icon(if (autoCompact) Icons.Rounded.ToggleOn else Icons.Rounded.ToggleOff, contentDescription = null, tint = if (autoCompact) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant) },
                modifier = Modifier.clickable { onShowCompactMenu(false); viewModel.setAutoCompaction(true) }
            )
            ListItem(
                headlineContent = { Text("手动 Compact") },
                supportingContent = { Text("关闭自动压缩，只在你手动触发时压缩") },
                leadingContent = { Icon(if (!autoCompact) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked, contentDescription = null) },
                modifier = Modifier.clickable { onShowCompactMenu(false); viewModel.setAutoCompaction(false) }
            )
            ListItem(
                headlineContent = { Text("立即执行 Compact") },
                supportingContent = { Text(if (text.isBlank()) "直接压缩当前上下文" else "输入框内容会作为本次压缩要求") },
                leadingContent = { Icon(Icons.Rounded.Archive, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                modifier = Modifier.clickable { onShowCompactMenu(false); viewModel.compact(text.trim().ifBlank { null }); onTextChange("") }
            )
            Spacer(Modifier.height(18.dp))
        }
    }
    if (showSendModeMenu) {
        ModalBottomSheet(onDismissRequest = { onShowSendModeMenu(false) }) {
            SheetHeader(Icons.Rounded.Send, "发送方式", "当前 agent 正在处理时选择队列策略")
            ListItem(
                headlineContent = { Text("立即发送") },
                supportingContent = { Text("中断当前 turn，然后发送这条消息") },
                leadingContent = { Icon(Icons.Rounded.FlashOn, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                modifier = Modifier.clickable { onShowSendModeMenu(false); onSend(null) }
            )
            ListItem(
                headlineContent = { Text("Steer 队列") },
                supportingContent = { Text("当前 turn 期间注入 steering message") },
                leadingContent = { Icon(Icons.Rounded.AltRoute, contentDescription = null) },
                modifier = Modifier.clickable { onShowSendModeMenu(false); onSend("steer") }
            )
            ListItem(
                headlineContent = { Text("Follow-up 队列") },
                supportingContent = { Text("agent 完成后继续追问") },
                leadingContent = { Icon(Icons.Rounded.Queue, contentDescription = null) },
                modifier = Modifier.clickable { onShowSendModeMenu(false); onSend("followUp") }
            )
            Spacer(Modifier.height(18.dp))
        }
    }
}

@Composable
private fun SheetHeader(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer) {
            Icon(icon, contentDescription = null, modifier = Modifier.padding(10.dp).size(24.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun thinkingDescription(level: String): String = when (level) {
    "off" -> "关闭扩展思考"
    "minimal" -> "最少推理，响应更快"
    "low" -> "低强度思考"
    "medium" -> "默认平衡"
    "high" -> "更强推理"
    "xhigh" -> "超高推理，需模型支持"
    else -> ""
}

@Composable
private fun ModelDropdown(expanded: Boolean, onDismiss: () -> Unit, state: AppUiState, viewModel: AppViewModel) {
    var search by remember { mutableStateOf("") }
    val models = remember(state.sessionModels, search) { state.sessionModels.filter { "${it.providerNameOrNull().orEmpty()} ${it.id} ${it.name.orEmpty()}".contains(search, ignoreCase = true) }.take(120) }
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss, modifier = Modifier.heightIn(max = 520.dp).widthIn(min = 320.dp)) {
        DropdownMenuItem(text = { OutlinedTextField(search, { search = it }, label = { Text("搜索") }, singleLine = true) }, onClick = {})
        if (state.modelLoading) DropdownMenuItem(text = { LinearProgressIndicator(Modifier.fillMaxWidth()) }, onClick = {})
        models.forEach { model -> DropdownMenuItem(text = { Column { Text(model.name ?: model.id); Text("${model.providerNameOrNull() ?: "unknown"} · ${model.id}${model.contextWindow?.let { " · ${formatTokens(it)} ctx" } ?: ""}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) } }, onClick = { onDismiss(); viewModel.setSessionModel(model) }) }
        if (!state.modelLoading && models.isEmpty()) DropdownMenuItem(text = { Text("没有可显示的模型") }, onClick = {})
    }
}

@Composable
private fun MessageBubble(message: ChatMessage, autoOpenProgress: Boolean, onFork: () -> Unit, onShowDialog: () -> Unit) {
    when (message.role) {
        "user" -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Surface(color = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary, shape = RoundedCornerShape(22.dp, 22.dp, 6.dp, 22.dp), modifier = Modifier.widthIn(max = 330.dp)) {
                Column(Modifier.padding(12.dp)) { Text(message.text); AttachmentGallery(message.attachments) }
            }
        }
        "assistant" -> Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            val hasThinking = message.thinking?.isNotBlank() == true
            message.thinking?.takeIf { it.isNotBlank() }?.let { ExpandableBlock("思考过程", it, autoOpen = autoOpenProgress) }
            if (message.text.isNotBlank()) MarkdownishText(message.text) else if (!hasThinking) Spacer(Modifier.height(1.dp))
            AttachmentGallery(message.attachments)
            if (message.text.isNotBlank()) AssistantActions(message.text, onFork, onShowDialog)
        }
        "tool" -> ToolMessageCard(message, autoOpen = autoOpenProgress)
        else -> AssistChip(onClick = {}, leadingIcon = { Icon(Icons.Rounded.Warning, contentDescription = null) }, label = { Text(message.text) })
    }
}

@Composable
private fun AssistantActions(text: String, onFork: () -> Unit, onShowDialog: () -> Unit) {
    val clipboard = LocalClipboardManager.current
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { clipboard.setText(AnnotatedString(text)) }, modifier = Modifier.size(34.dp)) { Icon(Icons.Rounded.ContentCopy, contentDescription = "复制", tint = color) }
        IconButton(onClick = onFork, modifier = Modifier.size(34.dp)) { Icon(Icons.Rounded.CallSplit, contentDescription = "Fork", tint = color) }
        IconButton(onClick = onShowDialog, modifier = Modifier.size(34.dp)) { Icon(Icons.Rounded.OpenInFull, contentDescription = "Dialog 显示", tint = color) }
    }
}

@Composable
private fun StatItem(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(17.dp))
        Text(text, color = color, fontSize = 11.sp, maxLines = 1)
    }
}

@Composable
private fun MarkdownishText(text: String) {
    val blocks = remember(text) { parseMarkdownBlocks(text) }
    SelectionContainer {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            blocks.forEach { block ->
                when (block) {
                    is MdBlock.Code -> CodeBlock(block.language.ifBlank { "code" }, block.code)
                    is MdBlock.Text -> MarkdownTextBlock(block.text)
                }
            }
        }
    }
}

private sealed interface MdBlock {
    data class Text(val text: String) : MdBlock
    data class Code(val language: String, val code: String) : MdBlock
}

private fun parseMarkdownBlocks(text: String): List<MdBlock> {
    val out = mutableListOf<MdBlock>()
    val re = Regex("```([^\\n`]*)\\n([\\s\\S]*?)```")
    var last = 0
    for (match in re.findAll(text)) {
        if (match.range.first > last) out += MdBlock.Text(text.substring(last, match.range.first).trim('\n'))
        out += MdBlock.Code(match.groupValues[1].trim(), match.groupValues[2].trimEnd('\n'))
        last = match.range.last + 1
    }
    if (last < text.length) out += MdBlock.Text(text.substring(last).trim('\n'))
    return out.filterNot { it is MdBlock.Text && it.text.isBlank() }
}

@Composable
private fun MarkdownTextBlock(text: String) {
    val colors = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        text.split(Regex("\\n{2,}")).filter { it.isNotBlank() }.forEach { para ->
            val lines = para.lines()
            val heading = lines.firstOrNull()?.let { Regex("^(#{1,6})\\s+(.+)$").find(it) }
            when {
                heading != null && lines.size == 1 -> {
                    val level = heading.groupValues[1].length
                    Text(heading.groupValues[2], color = colors.onSurface, fontSize = if (level <= 2) 22.sp else 18.sp, fontWeight = FontWeight.Black, lineHeight = if (level <= 2) 28.sp else 24.sp)
                }
                lines.all { it.trimStart().startsWith("- ") || it.trimStart().startsWith("* ") || it.trimStart().startsWith("• ") } -> {
                    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        lines.forEach { line ->
                            Row(horizontalArrangement = Arrangement.spacedBy(9.dp), verticalAlignment = Alignment.Top) {
                                Text("•", color = colors.primary, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                                Text(inlineMarkdown(line.trimStart().drop(2).trim()), color = colors.onSurface, fontSize = 16.sp, lineHeight = 24.sp, modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
                else -> Text(inlineMarkdown(para), color = colors.onSurface, fontSize = 16.sp, lineHeight = 24.sp)
            }
        }
    }
}

private fun inlineMarkdown(text: String): AnnotatedString = buildAnnotatedString {
    val re = Regex("(\\*\\*[^*]+\\*\\*|`[^`]+`)")
    var last = 0
    for (m in re.findAll(text)) {
        append(text.substring(last, m.range.first))
        val token = m.value
        when {
            token.startsWith("**") -> withStyle(SpanStyle(fontWeight = FontWeight.Black)) { append(token.removePrefix("**").removeSuffix("**")) }
            token.startsWith("`") -> withStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = Color(0xFFE8EAF6), color = Color(0xFF5B3DB5))) { append(token.removePrefix("`").removeSuffix("`")) }
        }
        last = m.range.last + 1
    }
    append(text.substring(last))
}

@Composable
private fun ToolMessageCard(message: ChatMessage, autoOpen: Boolean) {
    val status = message.toolStatus ?: "pending"
    var open by remember(message.id) { mutableStateOf(autoOpen || status == "running") }
    LaunchedEffect(autoOpen, status, message.id) { open = autoOpen || status == "running" }
    ElevatedCard(Modifier.fillMaxWidth(), colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                Modifier.fillMaxWidth().clickable { open = !open },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                ToolStatusIcon(status)
                Text(message.toolName ?: "tool", fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    toolOverview(message),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                StatusChip(status)
                Icon(if (open) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
            }
            if (open) ToolPreview(message)
        }
    }
}

@Composable
private fun ToolStatusIcon(status: String) {
    when (status) {
        "running" -> CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.primary)
        "error" -> Icon(Icons.Rounded.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
        "done" -> Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
        else -> Icon(Icons.Rounded.Build, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun ToolPreview(message: ChatMessage) {
    val name = message.toolName.orEmpty().lowercase()
    val args = message.toolArgs.toolObjectOrNull()
    val result = message.toolResult.orEmpty()
    val path = args.toolPath()
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        when (name) {
            "write" -> {
                val content = args.firstString("content", "text", "value", "data")
                if (!content.isNullOrBlank()) DiffBlock("写入 diff", buildWriteDiff(content, path))
                else result.takeIf { it.isNotBlank() }?.let { CodeBlock("输出", it) }
                if (result.isNotBlank() && content != null) SmallToolDetails("工具结果") { CodeBlock("输出", result) }
            }
            "edit" -> {
                val diffs = buildEditDiffs(args, path)
                if (diffs.isNotEmpty()) DiffBlock("编辑 diff", diffs)
                else result.takeIf { it.isNotBlank() }?.let { CodeBlock("输出", it) }
                if (message.toolArgs != null) SmallToolDetails("参数") { CodeBlock("json", pretty(message.toolArgs)) }
            }
            "read" -> {
                val limit = args?.stringValue("limit")?.let { " · limit $it" }.orEmpty()
                Text(compactText("读取", path, limit), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                val display = result.ifBlank { message.toolArgs?.let { pretty(it) }.orEmpty() }
                if (display.isNotBlank()) CodeBlock(languageForPath(path).ifBlank { "text" }, display)
            }
            "bash", "shell" -> {
                bashCommand(message.toolArgs)?.let { CodeBlock("$ command", it) }
                result.takeIf { it.isNotBlank() }?.let { CodeBlock("terminal", it) }
            }
            else -> {
                message.toolArgs?.let { CodeBlock("参数", pretty(it)) }
                result.takeIf { it.isNotBlank() }?.let { CodeBlock("输出", it) }
            }
        }
    }
}

@Composable
private fun SmallToolDetails(title: String, content: @Composable () -> Unit) {
    var open by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        TextButton(onClick = { open = !open }, contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)) {
            Icon(if (open) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, contentDescription = null, modifier = Modifier.size(16.dp))
            Text(title, fontSize = 12.sp)
        }
        if (open) content()
    }
}

private fun toolOverview(message: ChatMessage): String {
    val name = message.toolName.orEmpty().lowercase()
    val args = message.toolArgs.toolObjectOrNull()
    val result = message.toolResult.orEmpty()
    val path = args.toolPath()
    return when (name) {
        "write" -> compactText("写入", path, previewText(args.firstString("content", "text", "value") ?: result))
        "edit" -> compactText("编辑", path, previewText(editPreview(args) ?: result))
        "read" -> compactText("读取", path, args?.stringValue("limit")?.let { "limit $it" } ?: previewText(result))
        "bash", "shell" -> bashCommand(message.toolArgs)?.let { "$ ${previewText(it, 180)}" } ?: compactText("bash", previewText(result))
        else -> compactText(path, previewText(message.toolArgs?.let { pretty(it) }), previewText(result))
    }
}

private data class DiffLine(val type: DiffLineType, val text: String)
private enum class DiffLineType { Add, Del, Meta, Ctx }

@Composable
private fun DiffBlock(title: String, lines: List<DiffLine>, maxLines: Int = 240) {
    val clipboard = LocalClipboardManager.current
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val bg = if (dark) Color(0xFF0F0D13) else Color(0xFFF8FAFC)
    val headerBg = if (dark) Color(0xFF191820) else Color(0xFFE7E9EE)
    val clipped = lines.size > maxLines
    var expanded by remember { mutableStateOf(false) }
    val visible = if (clipped && !expanded) lines.take(maxLines) else lines
    val text = lines.joinToString("\n") { line ->
        when (line.type) {
            DiffLineType.Add -> "+${line.text}"
            DiffLineType.Del -> "-${line.text}"
            DiffLineType.Meta -> line.text
            DiffLineType.Ctx -> " ${line.text}"
        }
    }
    OutlinedCard(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.outlinedCardColors(containerColor = bg),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(Modifier.fillMaxWidth().background(headerBg).padding(horizontal = 10.dp, vertical = 5.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            if (clipped) {
                TextButton(onClick = { expanded = !expanded }, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)) {
                    Icon(if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, contentDescription = null, modifier = Modifier.size(13.dp))
                    Text(if (expanded) "收起" else "展开全部", fontSize = 12.sp)
                }
            }
            TextButton(onClick = { clipboard.setText(AnnotatedString(text)) }, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)) { Icon(Icons.Rounded.ContentCopy, contentDescription = null, modifier = Modifier.size(13.dp)); Text("复制", fontSize = 12.sp) }
        }
        SelectionContainer {
            Column(Modifier.fillMaxWidth().background(bg)) {
                visible.forEach { line -> DiffLineRow(line) }
                if (clipped && !expanded) DiffLineRow(DiffLine(DiffLineType.Meta, "… 已隐藏 ${lines.size - maxLines} 行 diff，共 ${lines.size} 行；点击“展开全部”查看完整结果"))
            }
        }
    }
}

@Composable
private fun DiffLineRow(line: DiffLine) {
    val colors = MaterialTheme.colorScheme
    val bg = when (line.type) {
        DiffLineType.Add -> Color(0xFFDCFCE7).copy(alpha = .75f)
        DiffLineType.Del -> Color(0xFFFEE2E2).copy(alpha = .82f)
        DiffLineType.Meta -> colors.primaryContainer.copy(alpha = .45f)
        DiffLineType.Ctx -> Color.Transparent
    }
    val fg = when (line.type) {
        DiffLineType.Add -> Color(0xFF166534)
        DiffLineType.Del -> Color(0xFF991B1B)
        DiffLineType.Meta -> colors.primary
        DiffLineType.Ctx -> colors.onSurface
    }
    Row(Modifier.fillMaxWidth().background(bg), verticalAlignment = Alignment.Top) {
        Text(
            when (line.type) { DiffLineType.Add -> "+"; DiffLineType.Del -> "-"; DiffLineType.Meta -> ""; DiffLineType.Ctx -> " " },
            color = fg,
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
            modifier = Modifier.width(28.dp).padding(top = 2.dp),
            maxLines = 1
        )
        Text(line.text.ifEmpty { " " }, color = fg, fontFamily = FontFamily.Monospace, fontSize = 12.sp, lineHeight = 18.sp, modifier = Modifier.weight(1f).padding(vertical = 2.dp, horizontal = 4.dp))
    }
}

private fun buildWriteDiff(content: String, filePath: String?): List<DiffLine> {
    val lines = splitTextLines(content)
    return listOf(
        DiffLine(DiffLineType.Meta, "+++ ${filePath ?: "file"}"),
        DiffLine(DiffLineType.Meta, "@@ -0,0 +1,${lines.size} @@")
    ) + lines.map { DiffLine(DiffLineType.Add, it) }
}

private fun buildEditDiffs(args: JsonObject?, filePath: String?): List<DiffLine> {
    if (args == null) return emptyList()
    val oldText = args.firstString("oldText", "old_text", "old", "original")
    val newText = args.firstString("newText", "new_text", "replacement", "content", "text", "value")
    if (oldText != null && newText != null) return buildUnifiedDiff(oldText, newText, filePath)
    val edits = args["edits"] as? JsonArray ?: return newText?.let { buildWriteDiff(it, filePath) }.orEmpty()
    return edits.flatMapIndexed { index, element ->
        val edit = element.toolObjectOrNull()
        val old = edit.firstString("oldText", "old_text", "old", "original")
        val new = edit.firstString("newText", "new_text", "replacement", "content", "text", "value")
        when {
            old != null && new != null -> listOf(DiffLine(DiffLineType.Meta, "# edit ${index + 1}")) + buildUnifiedDiff(old, new, filePath).drop(if (index == 0) 0 else 3)
            new != null -> listOf(DiffLine(DiffLineType.Meta, "# edit ${index + 1}")) + buildWriteDiff(new, filePath).drop(if (index == 0) 0 else 2)
            else -> emptyList()
        }
    }
}

private fun buildUnifiedDiff(oldText: String, newText: String, filePath: String?): List<DiffLine> {
    val oldLines = splitTextLines(oldText)
    val newLines = splitTextLines(newText)
    val header = listOf(
        DiffLine(DiffLineType.Meta, "--- ${filePath ?: "file"}"),
        DiffLine(DiffLineType.Meta, "+++ ${filePath ?: "file"}"),
        DiffLine(DiffLineType.Meta, "@@ -1,${oldLines.size} +1,${newLines.size} @@")
    )
    if (oldLines.size * newLines.size > 90_000) {
        return header + oldLines.map { DiffLine(DiffLineType.Del, it) } + newLines.map { DiffLine(DiffLineType.Add, it) }
    }
    val dp = Array(oldLines.size + 1) { IntArray(newLines.size + 1) }
    for (i in oldLines.size - 1 downTo 0) {
        for (j in newLines.size - 1 downTo 0) {
            dp[i][j] = if (oldLines[i] == newLines[j]) dp[i + 1][j + 1] + 1 else max(dp[i + 1][j], dp[i][j + 1])
        }
    }
    val body = mutableListOf<DiffLine>()
    var i = 0
    var j = 0
    while (i < oldLines.size || j < newLines.size) {
        when {
            i < oldLines.size && j < newLines.size && oldLines[i] == newLines[j] -> { body += DiffLine(DiffLineType.Ctx, oldLines[i]); i++; j++ }
            j >= newLines.size || (i < oldLines.size && dp[i + 1][j] >= dp[i][j + 1]) -> { body += DiffLine(DiffLineType.Del, oldLines[i]); i++ }
            else -> { body += DiffLine(DiffLineType.Add, newLines[j]); j++ }
        }
    }
    return header + body
}

private fun splitTextLines(text: String): List<String> = if (text.isEmpty()) emptyList() else text.replace("\r\n", "\n").split("\n")
private fun editPreview(args: JsonObject?): String? = args.firstString("newText", "new_text", "replacement", "content", "text", "value") ?: ((args?.get("edits") as? JsonArray)?.firstOrNull()?.toolObjectOrNull()).firstString("newText", "new_text", "replacement", "content", "text", "value")
private fun JsonObject?.toolPath(): String? = this?.stringValue("path") ?: this?.stringValue("file") ?: this?.stringValue("filename")
private fun JsonObject?.firstString(vararg keys: String): String? = keys.firstNotNullOfOrNull { this?.stringValue(it)?.takeIf(String::isNotBlank) }
private fun compactText(vararg parts: String?): String = parts.mapNotNull { it?.trim()?.takeIf(String::isNotBlank) }.joinToString(" · ").ifBlank { "点击查看详情" }
private fun previewText(value: String?, max: Int = 90): String? {
    val text = value?.replace(Regex("\\s+"), " ")?.trim().orEmpty()
    if (text.isBlank()) return null
    return if (text.length > max) text.take(max - 1) + "…" else text
}
private fun bashCommand(value: JsonElement?): String? {
    val obj = value.toolObjectOrNull()
    return obj?.stringValue("command") ?: obj?.stringValue("cmd") ?: obj?.stringValue("script") ?: obj?.stringValue("value") ?: value.primitiveString()
}
private fun JsonElement?.toolObjectOrNull(): JsonObject? = when (this) {
    is JsonObject -> this
    is JsonPrimitive -> contentOrNull?.let { runCatching { UiJson.parseToJsonElement(it).jsonObjectOrNull() }.getOrNull() }
    else -> null
}
private fun JsonElement?.jsonObjectOrNull(): JsonObject? = this as? JsonObject
private fun JsonElement?.primitiveString(): String? = (this as? JsonPrimitive)?.contentOrNull
private fun JsonObject.stringValue(key: String): String? = when (val value = this[key]) {
    is JsonPrimitive -> value.contentOrNull
    null -> null
    else -> pretty(value)
}

@Composable
private fun CodeBlock(title: String, text: String, collapsedChars: Int = TOOL_CODE_COLLAPSED_CHARS) {
    val clipboard = LocalClipboardManager.current
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val bg = if (dark) Color(0xFF101014) else Color(0xFFF1F3F5)
    val headerBg = if (dark) Color(0xFF191820) else Color(0xFFE7E9EE)
    val label = if (dark) Color(0xFFC9C3D4) else Color(0xFF4B5563)
    val clipped = text.length > collapsedChars
    var expanded by remember { mutableStateOf(false) }
    val displayText = if (clipped && !expanded) text.take(collapsedChars) else text
    OutlinedCard(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.outlinedCardColors(containerColor = bg),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(Modifier.fillMaxWidth().background(headerBg).padding(horizontal = 10.dp, vertical = 5.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.labelSmall, color = label, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            if (clipped) {
                TextButton(onClick = { expanded = !expanded }, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)) {
                    Icon(if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, contentDescription = null, modifier = Modifier.size(13.dp))
                    Text(if (expanded) "收起" else "展开全部", fontSize = 12.sp)
                }
            }
            TextButton(onClick = { clipboard.setText(AnnotatedString(text)) }, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)) { Icon(Icons.Rounded.ContentCopy, contentDescription = null, modifier = Modifier.size(13.dp)); Text("复制", fontSize = 12.sp) }
        }
        SelectionContainer { Text(highlightCode(displayText, title, dark), fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall, modifier = Modifier.fillMaxWidth().background(bg).padding(10.dp), lineHeight = 18.sp) }
        if (clipped && !expanded) {
            Text(
                "已隐藏 ${text.length - displayText.length} 个字符，点击“展开全部”查看完整结果。",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
                modifier = Modifier.fillMaxWidth().background(bg).padding(start = 10.dp, end = 10.dp, bottom = 8.dp)
            )
        }
    }
}

private fun highlightCode(code: String, language: String, dark: Boolean): AnnotatedString =
    PrismCodeHighlighter.highlight(code, language, dark) ?: fallbackHighlightCode(code, language, dark)

private object PrismCodeHighlighter {
    private val prism: Prism4j? by lazy {
        runCatching {
            val locator = Class.forName("com.boxedagent.android.syntax.GrammarLocatorDef").getDeclaredConstructor().newInstance() as GrammarLocator
            Prism4j(locator)
        }.getOrNull()
    }

    fun highlight(code: String, language: String, dark: Boolean): AnnotatedString? = runCatching {
        val prism = prism ?: return null
        val lang = normalizeCodeLanguage(language, code)
        val grammar = prism.grammar(lang) ?: return null
        val nodes = prism.tokenize(code, grammar)
        buildAnnotatedString { appendPrismNodes(nodes, dark, null) }
    }.getOrNull()

    private fun AnnotatedString.Builder.appendPrismNodes(nodes: List<Prism4j.Node>, dark: Boolean, inherited: SpanStyle?) {
        nodes.forEach { node ->
            when (node) {
                is Prism4j.Text -> appendStyled(node.literal(), inherited)
                is Prism4j.Syntax -> {
                    val style = prismStyle(node.type(), node.alias(), dark) ?: inherited
                    val children = node.children()
                    if (children.isNullOrEmpty()) appendStyled(node.matchedString(), style)
                    else appendPrismNodes(children, dark, style)
                }
                else -> append(node.toString())
            }
        }
    }

    private fun AnnotatedString.Builder.appendStyled(text: String, style: SpanStyle?) {
        if (style == null) append(text) else withStyle(style) { append(text) }
    }
}

private fun normalizeCodeLanguage(language: String, code: String = ""): String {
    val raw = language.trim().removePrefix("language-").lowercase()
    val fromExt = languageForPath(raw).ifBlank { raw }
    val lang = when (fromExt) {
        "kt", "kts" -> "kotlin"
        "js", "jsx", "mjs", "cjs", "ts", "tsx" -> "javascript"
        "py" -> "python"
        "html", "htm", "xml", "svg", "vue", "markup" -> "markup"
        "yml" -> "yaml"
        "md" -> "markdown"
        "cc", "cxx", "hpp", "hh" -> "cpp"
        "cs" -> "csharp"
        "sh", "bash", "zsh", "fish", "shell", "terminal", "$ command" -> "bash"
        "txt", "text", "输出", "参数", "code", "file" -> ""
        else -> fromExt
    }
    if (lang.isNotBlank() && lang != "bash") return lang
    val trimmed = code.trimStart()
    return when {
        trimmed.startsWith("{") || trimmed.startsWith("[") -> "json"
        trimmed.startsWith("<") -> "markup"
        else -> lang
    }
}

private fun languageForPath(path: String?): String {
    val value = path?.substringBefore('?')?.substringBefore('#')?.trim().orEmpty()
    val ext = value.substringAfterLast('/', value).substringAfterLast('.', missingDelimiterValue = "").lowercase()
    return when (ext) {
        "kt", "kts" -> "kotlin"
        "java" -> "java"
        "js", "jsx", "mjs", "cjs", "ts", "tsx" -> "javascript"
        "json" -> "json"
        "py" -> "python"
        "html", "htm", "xml", "svg", "vue" -> "markup"
        "css", "scss", "sass" -> "css"
        "md", "markdown" -> "markdown"
        "yaml", "yml" -> "yaml"
        "c", "h" -> "c"
        "cpp", "cc", "cxx", "hpp", "hh" -> "cpp"
        "cs" -> "csharp"
        "go" -> "go"
        "swift" -> "swift"
        "dart" -> "dart"
        "sql" -> "sql"
        "sh", "bash", "zsh", "fish" -> "bash"
        else -> if (value.indexOf('.') < 0 && value.indexOf('/') < 0) value.lowercase() else ""
    }
}

private fun prismStyle(type: String, alias: String?, dark: Boolean): SpanStyle? {
    val key = alias?.takeIf { it.isNotBlank() } ?: type
    val color = when (key) {
        "comment", "prolog", "doctype", "cdata" -> if (dark) Color(0xFF8B949E) else Color(0xFF6B7280)
        "punctuation", "operator" -> if (dark) Color(0xFFC9D1D9) else Color(0xFF4B5563)
        "property", "tag", "symbol", "deleted" -> if (dark) Color(0xFFFF7B72) else Color(0xFFB91C1C)
        "boolean", "number", "constant" -> if (dark) Color(0xFFFFD580) else Color(0xFFB45309)
        "selector", "attr-name", "string", "char", "builtin", "inserted" -> if (dark) Color(0xFFA5D6FF) else Color(0xFF047857)
        "function" -> if (dark) Color(0xFFD2A8FF) else Color(0xFF7C3AED)
        "class-name" -> if (dark) Color(0xFFFFD580) else Color(0xFFB45309)
        "keyword", "atrule", "important" -> if (dark) Color(0xFFFF7B72) else Color(0xFF6D28D9)
        "regex", "variable" -> if (dark) Color(0xFF79C0FF) else Color(0xFF0369A1)
        "attr-value", "url" -> if (dark) Color(0xFFA5D6FF) else Color(0xFF0F766E)
        else -> return null
    }
    val bold = key == "keyword" || key == "important" || key == "function" || key == "class-name"
    return SpanStyle(color = color, fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal)
}

private fun fallbackHighlightCode(code: String, language: String, dark: Boolean): AnnotatedString = buildAnnotatedString {
    val keywords = setOf("fun", "val", "var", "class", "object", "interface", "if", "else", "when", "for", "while", "return", "import", "package", "const", "let", "function", "export", "from", "def", "async", "await", "try", "catch", "finally", "true", "false", "null", "None", "public", "private", "suspend", "data", "echo", "cd", "ls", "grep", "find", "cat", "sed", "awk")
    val token = Regex("(//.*|#.*|\\\"(?:\\\\.|[^\\\"])*\\\"|'(?:\\\\.|[^'])*'|\\b\\d+(?:\\.\\d+)?\\b|\\b[A-Za-z_][A-Za-z0-9_]*\\b)")
    var last = 0
    for (m in token.findAll(code)) {
        append(code.substring(last, m.range.first))
        val s = m.value
        val color = when {
            s.startsWith("//") || (s.startsWith("#") && !language.contains("json", true)) -> if (dark) Color(0xFF7E8A97) else Color(0xFF6B7280)
            s.startsWith("\"") || s.startsWith("'") -> if (dark) Color(0xFFA5D6FF) else Color(0xFF0F766E)
            s.firstOrNull()?.isDigit() == true -> if (dark) Color(0xFFFFD580) else Color(0xFFB45309)
            s in keywords -> if (dark) Color(0xFFD0BCFF) else Color(0xFF6D28D9)
            else -> if (dark) Color(0xFFE6E1E5) else Color(0xFF111827)
        }
        withStyle(SpanStyle(color = color, fontWeight = if (s in keywords) FontWeight.Bold else FontWeight.Normal)) { append(s) }
        last = m.range.last + 1
    }
    append(code.substring(last))
}

@Composable
private fun ExpandableBlock(title: String, text: String, autoOpen: Boolean = false) {
    var open by remember { mutableStateOf(autoOpen) }
    LaunchedEffect(autoOpen, text) { open = autoOpen }
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(10.dp)) {
            Row(Modifier.fillMaxWidth().clickable { open = !open }, horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Psychology, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                    Text(title, fontWeight = FontWeight.Bold)
                }
                Text(if (open) "收起" else "展开", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
            }
            if (open) Spacer(Modifier.height(8.dp))
            if (open) MarkdownishText(text)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AttachmentGallery(attachments: List<ChatAttachment>) {
    if (attachments.isEmpty()) return
    var previewImage by remember { mutableStateOf<ChatAttachment.Image?>(null) }
    FlowRow(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.Start),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        attachments.forEach { a ->
            when (a) {
                is ChatAttachment.Image -> AttachmentToken(
                    icon = attachmentIcon(a.name, a.mimeType, isImage = true),
                    label = a.name,
                    secondary = a.path,
                    onClick = { previewImage = a }
                )
                is ChatAttachment.File -> AttachmentToken(
                    icon = attachmentIcon(a.name, a.mimeType, isImage = false),
                    label = a.name,
                    secondary = a.size?.let { formatBytes(it) } ?: a.path,
                    onClick = null
                )
            }
        }
    }
    previewImage?.let { ImagePreviewDialog(it, onDismiss = { previewImage = null }) }
}

@Composable
private fun AttachmentToken(icon: ImageVector, label: String, secondary: String?, onClick: (() -> Unit)?) {
    val content = LocalContentColor.current
    val shape = RoundedCornerShape(10.dp)
    Surface(
        modifier = Modifier.widthIn(min = 86.dp, max = 236.dp).clip(shape).clickable(enabled = onClick != null) { onClick?.invoke() },
        shape = shape,
        color = content.copy(alpha = .08f),
        contentColor = content,
        border = androidx.compose.foundation.BorderStroke(1.dp, content.copy(alpha = .30f))
    ) {
        Row(Modifier.padding(horizontal = 9.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(17.dp), tint = content.copy(alpha = .88f))
            Column(Modifier.weight(1f, fill = false)) {
                Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                secondary?.takeIf { it.isNotBlank() }?.let { Text(it, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 10.sp, color = content.copy(alpha = .68f)) }
            }
        }
    }
}

@Composable
private fun ImagePreviewDialog(image: ChatAttachment.Image, onDismiss: () -> Unit) {
    val bitmap = remember(image.data) {
        runCatching {
            val raw = image.data.replace(Regex("^data:[^,]+,"), "")
            val bytes = Base64.getDecoder().decode(raw)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
        }.getOrNull()
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("关闭") } },
        title = { Text(image.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                image.path?.let { Text(it, fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis) }
                Box(Modifier.fillMaxWidth().heightIn(min = 180.dp, max = 460.dp).background(MaterialTheme.colorScheme.surfaceContainerHighest, RoundedCornerShape(14.dp)).padding(8.dp), contentAlignment = Alignment.Center) {
                    if (bitmap != null) Image(bitmap = bitmap, contentDescription = image.name, modifier = Modifier.fillMaxWidth().heightIn(max = 430.dp), contentScale = ContentScale.Fit)
                    else Text("无法预览图片", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    )
}

private fun attachmentIcon(name: String, mimeType: String? = null, isImage: Boolean = false): ImageVector {
    val mime = mimeType.orEmpty().lowercase()
    val lower = name.lowercase()
    return when {
        isImage || mime.startsWith("image/") || lower.endsWithAny(".png", ".jpg", ".jpeg", ".gif", ".webp", ".bmp", ".svg") -> Icons.Rounded.Image
        mime == "application/pdf" || lower.endsWith(".pdf") -> Icons.Rounded.PictureAsPdf
        mime.startsWith("video/") || lower.endsWithAny(".mp4", ".mov", ".mkv", ".webm") -> Icons.Rounded.Movie
        mime.startsWith("audio/") || lower.endsWithAny(".mp3", ".wav", ".m4a", ".flac", ".ogg") -> Icons.Rounded.AudioFile
        lower.endsWithAny(".zip", ".tar", ".gz", ".tgz", ".rar", ".7z") -> Icons.Rounded.FolderZip
        lower.endsWithAny(".kt", ".java", ".js", ".ts", ".tsx", ".jsx", ".py", ".go", ".rs", ".cpp", ".c", ".h", ".html", ".css", ".json", ".xml", ".yaml", ".yml", ".sh") -> Icons.Rounded.Code
        lower.endsWithAny(".md", ".txt", ".log", ".csv") || mime.startsWith("text/") -> Icons.Rounded.Article
        lower.endsWithAny(".xls", ".xlsx") -> Icons.Rounded.TableChart
        else -> Icons.Rounded.AttachFile
    }
}

private fun String.endsWithAny(vararg suffixes: String): Boolean = suffixes.any { endsWith(it) }

@Composable
private fun WelcomePrompts(onPrompt: (String) -> Unit) {
    ElevatedCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) { Text("准备好开始了吗？", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black); Text("像 Claude 一样自然地描述任务。BoxedAgent 会整合上下文、文件和工具调用。", color = MaterialTheme.colorScheme.onSurfaceVariant); Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf("请快速了解这个 workspace 的结构，并给我一个简短总结。" to "总结 workspace", "请运行必要检查，找出当前项目最值得改进的地方。" to "检查项目", "请帮我实现一个小改动，并说明测试方式。" to "实现改动").forEach { (p, label) -> AssistChip(onClick = { onPrompt(p) }, label = { Text(label) }) } } } }
}

@Composable
private fun CenterWelcome(title: String, subtitle: String) { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { ElevatedCard(Modifier.padding(22.dp).fillMaxWidth()) { Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) { Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold); Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant) } } } }

@Composable
private fun ToolsScreen(state: AppUiState, viewModel: AppViewModel) {
    Column(Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = state.selectedToolTab.ordinal) {
            ToolTab.entries.forEach { tab ->
                Tab(
                    selected = state.selectedToolTab == tab,
                    onClick = { viewModel.setToolTab(tab) },
                    icon = { Icon(when (tab) { ToolTab.Terminal -> Icons.Rounded.Terminal; ToolTab.Files -> Icons.Rounded.Folder; ToolTab.Pi -> Icons.Rounded.Settings; ToolTab.CodeServer -> Icons.Rounded.Code }, contentDescription = null) },
                    text = { Text(when (tab) { ToolTab.Terminal -> "Shell"; ToolTab.Files -> "Files"; ToolTab.Pi -> "Pi"; ToolTab.CodeServer -> "Code" }) }
                )
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (state.selectedToolTab) {
                ToolTab.Terminal -> TerminalTab(state, viewModel)
                ToolTab.Files -> FileBrowserTab(state, viewModel)
                ToolTab.Pi -> PiSettingsTab(state, viewModel)
                ToolTab.CodeServer -> CodeServerTab(state, viewModel)
            }
        }
    }
}

@Composable
private fun TerminalTab(state: AppUiState, viewModel: AppViewModel) {
    val activeBox = state.activeBox
    if (activeBox == null) { CenterWelcome("请选择 Box", "Shell 会连接到 Box 内 /workspace。") ; return }
    val context = LocalContext.current
    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        ElevatedCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer) {
                        Icon(Icons.Rounded.Terminal, contentDescription = null, modifier = Modifier.padding(10.dp).size(24.dp))
                    }
                    Column(Modifier.weight(1f)) {
                        Text("独立终端", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Text(activeBox.name, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    StatusChip(activeBox.status)
                }
                Text("为避免 Termux 终端 View 与 Compose / 输入法布局冲突，Shell 现在在独立页面中打开。终端页面会自动连接当前 Box 的 /workspace，并提供手机快捷键栏。", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                Button(
                    onClick = {
                        context.startActivity(Intent(context, TerminalActivity::class.java).apply {
                            putExtra(TerminalActivity.EXTRA_BASE_URL, viewModel.baseUrl())
                            putExtra(TerminalActivity.EXTRA_TOKEN, viewModel.bearerToken())
                            putExtra(TerminalActivity.EXTRA_BOX_ID, activeBox.id)
                            putExtra(TerminalActivity.EXTRA_BOX_NAME, activeBox.name)
                        })
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Rounded.OpenInNew, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("打开 ${activeBox.name} Shell")
                }
            }
        }
        ElevatedCard(Modifier.fillMaxWidth(), colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("快捷键", fontWeight = FontWeight.Bold)
                Text("独立终端底部保留 ESC、TAB、CTRL、ALT、方向键、PGUP/PGDN、C-C、C-D、C-Z、粘贴与清屏。输入法弹出时由独立 Activity 的原生布局处理，不影响 Chat 输入框。", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun TerminalExtraKeysBar(
    ctrlActive: Boolean,
    altActive: Boolean,
    onCtrlToggle: () -> Unit,
    onAltToggle: () -> Unit,
    onSend: (String) -> Unit,
    onCtrl: (Char) -> Unit,
    onPaste: () -> Unit,
    onClear: () -> Unit
) {
    Column(
        Modifier.fillMaxWidth().background(Color(0xFF24252B)).padding(horizontal = 4.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        TerminalKeyRow(
            keys = listOf(
                TerminalKey("ESC", "\u001b"), TerminalKey("TAB", "\t"), TerminalKey("CTRL", modifier = TerminalKeyModifier.Ctrl), TerminalKey("ALT", modifier = TerminalKeyModifier.Alt),
                TerminalKey("/", "/"), TerminalKey("-", "-"), TerminalKey("|", "|"), TerminalKey("~", "~")
            ),
            ctrlActive = ctrlActive,
            altActive = altActive,
            onCtrlToggle = onCtrlToggle,
            onAltToggle = onAltToggle,
            onSend = onSend,
            onCtrl = onCtrl,
            onPaste = onPaste,
            onClear = onClear
        )
        TerminalKeyRow(
            keys = listOf(
                TerminalKey("HOME", "\u001b[H"), TerminalKey("↑", "\u001b[A"), TerminalKey("END", "\u001b[F"), TerminalKey("PGUP", "\u001b[5~"),
                TerminalKey("←", "\u001b[D"), TerminalKey("↓", "\u001b[B"), TerminalKey("→", "\u001b[C"), TerminalKey("PGDN", "\u001b[6~")
            ),
            ctrlActive = ctrlActive,
            altActive = altActive,
            onCtrlToggle = onCtrlToggle,
            onAltToggle = onAltToggle,
            onSend = onSend,
            onCtrl = onCtrl,
            onPaste = onPaste,
            onClear = onClear
        )
        TerminalKeyRow(
            keys = listOf(
                TerminalKey("C-C", ctrl = 'c'), TerminalKey("C-D", ctrl = 'd'), TerminalKey("C-Z", ctrl = 'z'), TerminalKey("BKSP", "\u007f"),
                TerminalKey("ENTER", "\r"), TerminalKey("PASTE", action = TerminalKeyAction.Paste), TerminalKey("CLEAR", action = TerminalKeyAction.Clear)
            ),
            ctrlActive = ctrlActive,
            altActive = altActive,
            onCtrlToggle = onCtrlToggle,
            onAltToggle = onAltToggle,
            onSend = onSend,
            onCtrl = onCtrl,
            onPaste = onPaste,
            onClear = onClear
        )
    }
}

@Composable
private fun TerminalKeyRow(
    keys: List<TerminalKey>,
    ctrlActive: Boolean,
    altActive: Boolean,
    onCtrlToggle: () -> Unit,
    onAltToggle: () -> Unit,
    onSend: (String) -> Unit,
    onCtrl: (Char) -> Unit,
    onPaste: () -> Unit,
    onClear: () -> Unit
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        keys.forEach { key ->
            val active = (key.modifier == TerminalKeyModifier.Ctrl && ctrlActive) || (key.modifier == TerminalKeyModifier.Alt && altActive)
            TerminalKeyButton(key.label, active, Modifier.weight(1f)) {
                when {
                    key.modifier == TerminalKeyModifier.Ctrl -> onCtrlToggle()
                    key.modifier == TerminalKeyModifier.Alt -> onAltToggle()
                    key.ctrl != null -> onCtrl(key.ctrl)
                    key.action == TerminalKeyAction.Paste -> onPaste()
                    key.action == TerminalKeyAction.Clear -> onClear()
                    else -> onSend(key.sequence)
                }
            }
        }
    }
}

@Composable
private fun TerminalKeyButton(label: String, active: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val bg = if (active) Color(0xFF6750A4) else Color(0xFF3A3B42)
    Surface(modifier = modifier.height(34.dp), shape = RoundedCornerShape(7.dp), color = bg, contentColor = Color(0xFFECE6F0)) {
        Box(Modifier.fillMaxSize().clickable(onClick = onClick), contentAlignment = Alignment.Center) {
            Text(label, fontSize = 11.sp, fontFamily = FontFamily.Monospace, maxLines = 1, overflow = TextOverflow.Clip)
        }
    }
}

private enum class TerminalKeyModifier { Ctrl, Alt }
private enum class TerminalKeyAction { Paste, Clear }
private data class TerminalKey(
    val label: String,
    val sequence: String = "",
    val modifier: TerminalKeyModifier? = null,
    val ctrl: Char? = null,
    val action: TerminalKeyAction? = null
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FileBrowserTab(state: AppUiState, viewModel: AppViewModel) {
    if (state.activeBox == null) { CenterWelcome("请选择 Box", "文件浏览器支持上传、下载、新建目录与删除。") ; return }
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    var path by remember(state.activeBoxId) { mutableStateOf(viewModel.rememberedFileBrowserPath(state.activeBoxId)) }
    var entries by remember { mutableStateOf<List<FileEntry>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var createKind by remember { mutableStateOf<String?>(null) }
    var createName by remember { mutableStateOf("") }
    var pendingDownload by remember { mutableStateOf<DownloadedFile?>(null) }
    var actionEntry by remember { mutableStateOf<FileEntry?>(null) }
    var deleteEntry by remember { mutableStateOf<FileEntry?>(null) }
    var largePreviewEntry by remember { mutableStateOf<FileEntry?>(null) }
    var previewDownload by remember { mutableStateOf<PreviewDownloadState?>(null) }
    var previewJob by remember { mutableStateOf<Job?>(null) }
    val createDoc = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri -> val file = pendingDownload; if (uri != null && file != null) context.contentResolver.openOutputStream(uri)?.use { it.write(file.bytes) }; pendingDownload = null }
    val pickUpload = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        scope.launch {
            uris.mapNotNull { context.readDraftAttachment(it) }.forEach { viewModel.uploadFile(path, it) }
            loadFiles(viewModel, path, { loading = it }, { entries = it }, { error = it })
        }
    }
    DisposableEffect(Unit) { onDispose { previewJob?.cancel() } }
    fun reload() { scope.launch { loadFiles(viewModel, path, { loading = it }, { entries = it }, { error = it }) } }
    fun beginPreviewDownload(entry: FileEntry) {
        previewJob?.cancel()
        previewDownload = PreviewDownloadState(entry = entry, bytesRead = 0L, totalBytes = entry.size.takeIf { it > 0 } ?: -1L)
        previewJob = scope.launch {
            try {
                val cached = viewModel.downloadFileToCache(entry.path) { read, total ->
                    previewDownload = PreviewDownloadState(entry = entry, bytesRead = read, totalBytes = total.takeIf { it > 0 } ?: entry.size.takeIf { it > 0 } ?: -1L)
                }
                previewDownload = null
                context.openCachedPreview(cached) { viewModel.emit(it) }
            } catch (e: CancellationException) {
                previewDownload = null
                viewModel.emit("已取消预览下载")
            } catch (e: Exception) {
                previewDownload = null
                viewModel.emit("预览失败：${e.message}")
            }
        }
    }
    fun requestPreview(entry: FileEntry) {
        if (entry.size >= PREVIEW_LARGE_FILE_THRESHOLD_BYTES) largePreviewEntry = entry else beginPreviewDownload(entry)
    }
    fun attach(entry: FileEntry) {
        if (state.activeSessionId == null) {
            viewModel.emit("请先选择 Session")
            return
        }
        viewModel.insertIntoComposer(fileRef(workspaceAbsPath(entry.path)))
        viewModel.emit("已附加 ${entry.name}")
    }
    fun createTargetPath(name: String): String = if (path == "." || path.isBlank()) name else "$path/$name"
    LaunchedEffect(state.activeBoxId, path) {
        viewModel.rememberFileBrowserPath(state.activeBoxId, path)
        loadFiles(viewModel, path, { loading = it }, { entries = it }, { error = it })
    }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface).padding(horizontal = 10.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            FilledTonalIconButton(onClick = { path = parentPath(path) }, modifier = Modifier.size(36.dp)) { Icon(Icons.Rounded.ArrowUpward, contentDescription = "上级", modifier = Modifier.size(19.dp)) }
            FilledTonalIconButton(onClick = { reload() }, modifier = Modifier.size(36.dp)) { Icon(Icons.Rounded.Refresh, contentDescription = "刷新", modifier = Modifier.size(19.dp)) }
            FilledTonalButton(onClick = { pickUpload.launch(arrayOf("*/*")) }, contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)) { Icon(Icons.Rounded.Upload, contentDescription = null, modifier = Modifier.size(17.dp)); Spacer(Modifier.width(4.dp)); Text("上传", fontSize = 13.sp) }
            OutlinedButton(onClick = { createKind = "file"; createName = "" }, contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)) { Icon(Icons.Rounded.NoteAdd, contentDescription = null, modifier = Modifier.size(17.dp)); Spacer(Modifier.width(4.dp)); Text("文件", fontSize = 13.sp) }
            OutlinedButton(onClick = { createKind = "dir"; createName = "" }, contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)) { Icon(Icons.Rounded.CreateNewFolder, contentDescription = null, modifier = Modifier.size(17.dp)); Spacer(Modifier.width(4.dp)); Text("目录", fontSize = 13.sp) }
        }
        Surface(Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surfaceContainerHighest, shape = RoundedCornerShape(10.dp)) {
            Row(Modifier.padding(horizontal = 10.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Rounded.FolderOpen, contentDescription = null, modifier = Modifier.size(17.dp), tint = MaterialTheme.colorScheme.primary)
                BasicTextField(value = path, onValueChange = { path = it.ifBlank { "." } }, singleLine = true, textStyle = TextStyle(fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface, fontFamily = FontFamily.Monospace), modifier = Modifier.weight(1f))
            }
        }
        createKind?.let { kind ->
            Surface(Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surfaceContainerLow, shape = RoundedCornerShape(10.dp), border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
                Row(Modifier.padding(8.dp), horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(if (kind == "dir") Icons.Rounded.CreateNewFolder else Icons.Rounded.NoteAdd, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    OutlinedTextField(createName, { createName = it }, label = { Text(if (kind == "dir") "目录名" else "文件名") }, singleLine = true, modifier = Modifier.weight(1f))
                    TextButton(onClick = { createKind = null; createName = "" }) { Text("取消") }
                    Button(onClick = {
                        val name = createName.trim()
                        if (!isSafeFileName(name)) { error = "名称不能包含 /、\\ 或 .."; return@Button }
                        scope.launch {
                            error = null
                            runCatching {
                                val target = createTargetPath(name)
                                if (kind == "dir") viewModel.mkdir(target) else viewModel.uploadFile(path, DraftAttachment(name, "text/plain", ByteArray(0), false))
                                createKind = null; createName = ""
                                if (kind == "dir") path = target else loadFiles(viewModel, path, { loading = it }, { entries = it }, { error = it })
                            }.onFailure { error = it.message }
                        }
                    }, enabled = createName.isNotBlank()) { Text("创建") }
                }
            }
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }
        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surfaceContainerLowest, shape = RoundedCornerShape(12.dp), border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
            LazyColumn(Modifier.fillMaxSize()) {
                item { FileHeaderRow() }
                items(entries.sortedWith(compareBy<FileEntry> { it.type != "directory" }.thenBy { it.name.lowercase() }), key = { it.path }) { entry ->
                    FileRow(
                        entry = entry,
                        onOpen = { if (entry.type == "directory") path = entry.path else requestPreview(entry) },
                        onAttach = { attach(entry) },
                        onMore = { actionEntry = entry }
                    )
                }
                if (!loading && entries.isEmpty()) item { Text("空目录", Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
    }
    actionEntry?.let { entry ->
        ModalBottomSheet(onDismissRequest = { actionEntry = null }) {
            SheetHeader(if (entry.type == "directory") Icons.Rounded.Folder else Icons.Rounded.Description, entry.name, workspaceAbsPath(entry.path))
            if (entry.type == "directory") ListItem(headlineContent = { Text("打开目录") }, leadingContent = { Icon(Icons.Rounded.FolderOpen, contentDescription = null) }, modifier = Modifier.clickable { path = entry.path; actionEntry = null })
            if (entry.type == "file") ListItem(headlineContent = { Text("预览打开") }, supportingContent = { Text("下载到应用缓存后使用本机应用打开") }, leadingContent = { Icon(Icons.Rounded.Visibility, contentDescription = null) }, modifier = Modifier.clickable { actionEntry = null; requestPreview(entry) })
            if (entry.type == "file") ListItem(headlineContent = { Text("快速附加到消息") }, supportingContent = { Text(fileRef(workspaceAbsPath(entry.path))) }, leadingContent = { Icon(Icons.Rounded.AttachFile, contentDescription = null) }, modifier = Modifier.clickable { attach(entry); actionEntry = null })
            ListItem(headlineContent = { Text("复制路径") }, supportingContent = { Text(workspaceAbsPath(entry.path)) }, leadingContent = { Icon(Icons.Rounded.ContentCopy, contentDescription = null) }, modifier = Modifier.clickable { clipboard.setText(AnnotatedString(workspaceAbsPath(entry.path))); actionEntry = null; viewModel.emit("已复制路径") })
            if (entry.type == "file") ListItem(headlineContent = { Text("下载") }, leadingContent = { Icon(Icons.Rounded.Download, contentDescription = null) }, modifier = Modifier.clickable { scope.launch { val file = viewModel.downloadFile(entry.path); pendingDownload = file; createDoc.launch(file.name) }; actionEntry = null })
            ListItem(headlineContent = { Text("删除", color = MaterialTheme.colorScheme.error) }, leadingContent = { Icon(Icons.Rounded.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) }, modifier = Modifier.clickable { deleteEntry = entry; actionEntry = null })
            Spacer(Modifier.height(18.dp))
        }
    }
    deleteEntry?.let { entry -> ConfirmDialog("删除 ${entry.name}", "确定删除 ${workspaceAbsPath(entry.path)}？", onDismiss = { deleteEntry = null }, onConfirm = { scope.launch { viewModel.deleteFile(entry.path); deleteEntry = null; loadFiles(viewModel, path, { loading = it }, { entries = it }, { error = it }) } }) }
    largePreviewEntry?.let { entry ->
        LargeFilePreviewDialog(
            entry = entry,
            onDismiss = { largePreviewEntry = null },
            onConfirm = { largePreviewEntry = null; beginPreviewDownload(entry) }
        )
    }
    previewDownload?.let { progress ->
        PreviewDownloadDialog(progress = progress, onCancel = { previewJob?.cancel(); previewJob = null; previewDownload = null })
    }
}

private suspend fun loadFiles(viewModel: AppViewModel, path: String, setLoading: (Boolean) -> Unit, setEntries: (List<FileEntry>) -> Unit, setError: (String?) -> Unit) {
    setLoading(true); setError(null)
    runCatching { viewModel.listFiles(path) }.onSuccess { setEntries(it) }.onFailure { setError(it.message) }
    setLoading(false)
}

private data class PreviewDownloadState(val entry: FileEntry, val bytesRead: Long, val totalBytes: Long)

@Composable
private fun LargeFilePreviewDialog(entry: FileEntry, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { Button(onClick = onConfirm) { Text("继续预览") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
        title = { Text("预览大文件？") },
        text = { Text("${entry.name} 大小为 ${formatBytes(entry.size)}。预览需要先下载到应用缓存，可能消耗流量与时间。") }
    )
}

@Composable
private fun PreviewDownloadDialog(progress: PreviewDownloadState, onCancel: () -> Unit) {
    val total = progress.totalBytes
    val fraction = if (total > 0) (progress.bytesRead.toFloat() / total.toFloat()).coerceIn(0f, 1f) else null
    AlertDialog(
        onDismissRequest = {},
        confirmButton = {},
        dismissButton = { TextButton(onClick = onCancel) { Text("取消下载") } },
        title = { Text("正在准备预览") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(progress.entry.name, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (fraction != null) LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth()) else LinearProgressIndicator(Modifier.fillMaxWidth())
                Text(
                    if (total > 0) "${formatBytes(progress.bytesRead)} / ${formatBytes(total)}" else "已下载 ${formatBytes(progress.bytesRead)}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp
                )
            }
        }
    )
}

private fun Context.openCachedPreview(file: CachedPreviewFile, onError: (String) -> Unit) {
    val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file.file)
    val mimeType = previewMimeType(file.name, file.mimeType)
    val intent = Intent(Intent.ACTION_VIEW)
        .setDataAndType(uri, mimeType)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    runCatching { startActivity(Intent.createChooser(intent, "预览 ${file.name}")) }
        .onFailure { e ->
            val message = if (e is ActivityNotFoundException) "没有可打开 ${file.name} 的应用" else (e.message ?: "无法打开预览")
            onError(message)
        }
}

private fun previewMimeType(name: String, serverMimeType: String): String {
    val clean = serverMimeType.substringBefore(';').trim().lowercase()
    if (clean.isNotBlank() && clean != "application/octet-stream" && clean != "binary/octet-stream") return clean
    val ext = name.substringAfterLast('.', "").lowercase()
    return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: when (ext) {
        "md", "markdown" -> "text/markdown"
        "log", "txt", "csv", "json", "xml", "yaml", "yml", "kt", "java", "js", "ts", "py", "sh" -> "text/plain"
        else -> "application/octet-stream"
    }
}

@Composable
private fun FileHeaderRow() {
    Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("NAME", Modifier.weight(1f), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
        Text("SIZE", Modifier.width(76.dp), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
        Spacer(Modifier.width(84.dp))
    }
    HorizontalDivider()
}

@Composable
private fun FileRow(entry: FileEntry, onOpen: () -> Unit, onAttach: () -> Unit, onMore: () -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().heightIn(min = 42.dp).clickable { onOpen() }.padding(start = 10.dp, end = 4.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(if (entry.type == "directory") Icons.Rounded.Folder else Icons.Rounded.Description, contentDescription = null, tint = if (entry.type == "directory") Color(0xFFD6A433) else MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(entry.name, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(workspaceAbsPath(entry.path), fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis, fontFamily = FontFamily.Monospace)
            }
            Text(if (entry.type == "file") formatBytes(entry.size) else "dir", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(76.dp), maxLines = 1)
            if (entry.type == "file") IconButton(onClick = onAttach, modifier = Modifier.size(34.dp)) { Icon(Icons.Rounded.AttachFile, contentDescription = "快速附加", modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary) }
            else Spacer(Modifier.width(34.dp))
            IconButton(onClick = onMore, modifier = Modifier.size(34.dp)) { Icon(Icons.Rounded.MoreVert, contentDescription = "文件操作", modifier = Modifier.size(18.dp)) }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .65f))
    }
}

private fun isSafeFileName(name: String): Boolean = name.isNotBlank() && '/' !in name && '\\' !in name && name != "." && name != ".." && !name.contains("..")

@Composable
private fun PiSettingsTab(state: AppUiState, viewModel: AppViewModel) {
    val activeBox = state.activeBox
    if (activeBox == null) { CenterWelcome("请选择 Box", "Pi 配置为每个 Box 独立保存。") ; return }
    val scope = rememberCoroutineScope()
    var provider by remember(state.activeBoxId) { mutableStateOf("") }
    var model by remember(state.activeBoxId) { mutableStateOf("") }
    var thinking by remember(state.activeBoxId) { mutableStateOf("medium") }
    var enabledModels by remember(state.activeBoxId) { mutableStateOf("") }
    var settingsText by remember(state.activeBoxId) { mutableStateOf("{}") }
    var modelsText by remember(state.activeBoxId) { mutableStateOf("{}") }
    var envText by remember(state.activeBoxId) { mutableStateOf("{}") }
    var systemPrompt by remember(state.activeBoxId) { mutableStateOf("") }
    var appendSystem by remember(state.activeBoxId) { mutableStateOf("") }
    var agentsMd by remember(state.activeBoxId) { mutableStateOf("") }
    var materialized by remember(state.activeBoxId) { mutableStateOf("/workspace/.boxedagent/pi-agent") }
    var error by remember { mutableStateOf<String?>(null) }
    var ok by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(state.activeBoxId) { runCatching { viewModel.getPiConfig() }.onSuccess { cfg -> provider = cfg.pi.defaultProvider.orEmpty(); model = cfg.pi.defaultModel.orEmpty(); thinking = cfg.pi.defaultThinkingLevel ?: "medium"; enabledModels = cfg.pi.enabledModels.joinToString(", "); settingsText = cfg.pi.settingsJson?.let { UiJson.encodeToString(it) } ?: "{}"; modelsText = cfg.pi.modelsJson?.let { UiJson.encodeToString(it) } ?: "{}"; envText = UiJson.encodeToString(cfg.env); systemPrompt = cfg.pi.systemPrompt.orEmpty(); appendSystem = cfg.pi.appendSystemPrompt.orEmpty(); agentsMd = cfg.pi.agentsMd.orEmpty(); materialized = cfg.materialized.piCodingAgentDir }.onFailure { error = it.message } }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { ElevatedCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(14.dp)) { Text("Pi 配置 · ${activeBox.name}", fontWeight = FontWeight.Bold); Text("PI_CODING_AGENT_DIR：$materialized", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) } } }
        item { error?.let { Text(it, color = MaterialTheme.colorScheme.error) }; ok?.let { Text(it, color = Color(0xFF4ADE80)) } }
        item { SettingsCard("模型与运行参数") { OutlinedTextField(provider, { provider = it }, label = { Text("默认 Provider") }, singleLine = true, modifier = Modifier.fillMaxWidth()); OutlinedTextField(model, { model = it }, label = { Text("默认 Model") }, singleLine = true, modifier = Modifier.fillMaxWidth()); DropdownField("Thinking", thinking, ThinkingLevels, { thinking = it }); OutlinedTextField(enabledModels, { enabledModels = it }, label = { Text("enabledModels（逗号分隔）") }, singleLine = true, modifier = Modifier.fillMaxWidth()) } }
        item { SettingsCard("JSON 配置") { CodeTextField("环境变量 JSON", envText, { envText = it }); CodeTextField("models.json", modelsText, { modelsText = it }); CodeTextField("settings.json 额外配置", settingsText, { settingsText = it }) } }
        item { SettingsCard("Prompt 与项目上下文") { CodeTextField("SYSTEM.md", systemPrompt, { systemPrompt = it }); CodeTextField("APPEND_SYSTEM.md", appendSystem, { appendSystem = it }); CodeTextField("AGENTS.md", agentsMd, { agentsMd = it }) } }
        item { Button(onClick = { scope.launch { error = null; ok = null; runCatching { parseObject(settingsText); parseObject(modelsText); val env = parseEnv(envText); viewModel.updatePiConfig(PiConfigUpdateRequest(defaultProvider = provider.ifBlank { null }, defaultModel = model.ifBlank { null }, defaultThinkingLevel = thinking, enabledModels = enabledModels.split(',').map { it.trim() }.filter { it.isNotBlank() }, settingsJsonText = settingsText, modelsJsonText = modelsText, systemPrompt = systemPrompt, appendSystemPrompt = appendSystem, agentsMd = agentsMd, env = env)) }.onSuccess { ok = "已保存并写入 Box workspace；运行中的 Session 建议重启。" }.onFailure { error = it.message } } }, modifier = Modifier.fillMaxWidth()) { Text("保存 Pi 配置") } }
    }
}

@Composable
private fun SettingsCard(title: String, content: @Composable ColumnScope.() -> Unit) { ElevatedCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) { Text(title, fontWeight = FontWeight.Bold); content() } } }
@Composable
private fun CodeTextField(label: String, value: String, onValue: (String) -> Unit) { OutlinedTextField(value, onValue, label = { Text(label) }, modifier = Modifier.fillMaxWidth().heightIn(min = 130.dp), textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace), maxLines = 12) }

@Composable
private fun CodeServerTab(state: AppUiState, viewModel: AppViewModel) {
    val activeBox = state.activeBox
    if (activeBox == null) { CenterWelcome("请选择 Box", "code-server 通过 BoxedAgent 反向代理访问。") ; return }
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val url = viewModel.codeServerUrl().orEmpty()
    var webKey by remember(url) { mutableStateOf(0) }
    val cookieHeader = viewModel.authCookieHeader()

    Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        ElevatedCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("code-server", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("内嵌 WebView 通过 BoxedAgent 反向代理访问。默认密码：${activeBox.codeServerPassword ?: "boxedagent"}。首次启动可能需要数秒。", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { webKey++ }) { Text("刷新") }
                    OutlinedButton(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }) { Text("外部浏览器") }
                    OutlinedButton(onClick = { clipboard.setText(AnnotatedString(url)) }) { Text("复制 URL") }
                    OutlinedButton(onClick = { clipboard.setText(AnnotatedString(viewModel.bearerToken())) }) { Text("复制 Token") }
                }
            }
        }
        key(webKey) {
            AndroidView(
                modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(18.dp)),
                factory = { ctx ->
                    if (cookieHeader.isNotBlank()) CookieManager.getInstance().setCookie(viewModel.baseUrl(), cookieHeader)
                    CookieManager.getInstance().setAcceptCookie(true)
                    WebView(ctx).apply {
                        webViewClient = WebViewClient()
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.cacheMode = WebSettings.LOAD_DEFAULT
                        settings.useWideViewPort = true
                        settings.loadWithOverviewMode = true
                        loadUrl(url, mapOf("Authorization" to "Bearer ${viewModel.bearerToken()}"))
                    }
                },
                update = { webView ->
                    if (cookieHeader.isNotBlank()) CookieManager.getInstance().setCookie(viewModel.baseUrl(), cookieHeader)
                    if (webView.url != url) webView.loadUrl(url, mapOf("Authorization" to "Bearer ${viewModel.bearerToken()}"))
                }
            )
        }
    }
}

private fun parseObject(text: String): JsonObject {
    val parsed = UiJson.parseToJsonElement(text.ifBlank { "{}" })
    return parsed as? JsonObject ?: error("必须是 JSON 对象")
}
private fun parseEnv(text: String): Map<String, String> = parseObject(text).mapValues { it.value.jsonPrimitive.contentOrNull ?: it.value.toString() }
private fun parentPath(path: String): String = if (path == "." || path.isBlank()) "." else path.split('/').dropLast(1).joinToString("/").ifBlank { "." }
private fun workspaceAbsPath(path: String): String = when {
    path.startsWith("/workspace") -> path
    path == "." || path.isBlank() -> "/workspace"
    else -> "/workspace/${path.trimStart('/')}"
}

private fun appendComposerRefs(current: String, refs: List<String>): String = appendComposerText(current, refs.filter { it.isNotBlank() }.joinToString(" "))
private fun appendComposerText(current: String, insert: String): String {
    val value = insert.trim()
    if (value.isBlank()) return current
    val prefix = if (current.isBlank() || current.last().isWhitespace()) "" else " "
    return "$current$prefix$value "
}
private fun removeComposerRef(current: String, path: String): String {
    val unquoted = fileRef(path)
    val quoted = "@\"${path.replace("\\", "\\\\").replace("\"", "\\\"")}\""
    return current.replace(unquoted, "").replace(quoted, "").replace(Regex(" {2,}"), " ").replace(Regex(" *\n *"), "\n").trimStart()
}

private fun Context.readDraftAttachment(uri: Uri): DraftAttachment? = runCatching {
    val mime = contentResolver.getType(uri) ?: "application/octet-stream"
    val name = contentResolver.query(uri, null, null, null, null)?.useName() ?: uri.lastPathSegment ?: "attachment"
    val bytes = contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return null
    DraftAttachment(name, mime, bytes, mime.startsWith("image/"))
}.getOrNull()

private fun Cursor.useName(): String? = use { c -> val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME); if (idx >= 0 && c.moveToFirst()) c.getString(idx) else null }

private fun JsonObject.string(key: String): String? = this[key]?.jsonPrimitive?.contentOrNull
