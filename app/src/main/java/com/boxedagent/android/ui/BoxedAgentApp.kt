package com.boxedagent.android.ui

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.provider.OpenableColumns
import android.provider.Settings
import android.webkit.CookieManager
import android.webkit.MimeTypeMap
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.runtime.saveable.rememberSaveable
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.boxedagent.android.TerminalActivity
import com.boxedagent.android.data.*
import io.noties.prism4j.GrammarLocator
import io.noties.prism4j.Prism4j
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
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
import java.util.Locale
import kotlin.math.PI
import kotlin.math.max

private val UiJson = Json { prettyPrint = true; ignoreUnknownKeys = true; explicitNulls = false }
private val ThinkingLevels = listOf("off", "minimal", "low", "medium", "high", "xhigh")
private val MarkdownParagraphBreakRegex = Regex("\\n{2,}")
private val MarkdownHeadingRegex = Regex("^(#{1,6})\\s+(.+)$")
private val InlineMarkdownRegex = Regex("(\\*\\*[^*]+\\*\\*|`[^`]+`)")

private data class AppStrings(
    val boxesTitle: String,
    val boxesSubtitle: String,
    val toolsTitle: String,
    val toolsSubtitle: String,
    val settingsTitle: String,
    val settingsSubtitle: String,
    val close: String,
    val connectionTitle: String,
    val connectionDesc: String,
    val apiUrl: String,
    val token: String,
    val connect: String,
    val connecting: String,
    val serverSettings: String,
    val appSettings: String,
    val serverManagement: String,
    val addServer: String,
    val editServer: String,
    val serverName: String,
    val save: String,
    val saveAndConnect: String,
    val active: String,
    val switchServer: String,
    val delete: String,
    val theme: String,
    val lightMode: String,
    val darkMode: String,
    val language: String,
    val followSystem: String,
    val chinese: String,
    val english: String,
    val currentServer: String,
    val toolsTerminal: String,
    val toolsFiles: String,
    val toolsPi: String,
    val toolsCode: String
)

private val ZhStrings = AppStrings(
    boxesTitle = "Boxes / Sessions",
    boxesSubtitle = "Docker 沙箱与会话管理",
    toolsTitle = "Tools",
    toolsSubtitle = "Shell、Files、Pi、code-server",
    settingsTitle = "软件设置",
    settingsSubtitle = "服务器连接、外观与语言",
    close = "关闭",
    connectionTitle = "连接 BoxedAgent",
    connectionDesc = "选择已有服务器，或新增一个连接。",
    apiUrl = "API 地址",
    token = "Token（未启用认证可留空）",
    connect = "连接 / 登录",
    connecting = "连接中…",
    serverSettings = "软件设置",
    appSettings = "应用设置",
    serverManagement = "服务器连接",
    addServer = "新增服务器",
    editServer = "编辑服务器",
    serverName = "服务器名称",
    save = "保存",
    saveAndConnect = "保存并连接",
    active = "当前",
    switchServer = "切换连接",
    delete = "删除",
    theme = "外观模式",
    lightMode = "亮色模式",
    darkMode = "暗色模式",
    language = "软件语言",
    followSystem = "跟随系统",
    chinese = "中文",
    english = "English",
    currentServer = "当前服务器",
    toolsTerminal = "Shell",
    toolsFiles = "Files",
    toolsPi = "Pi",
    toolsCode = "Code"
)

private val EnStrings = AppStrings(
    boxesTitle = "Boxes / Sessions",
    boxesSubtitle = "Docker sandboxes and sessions",
    toolsTitle = "Tools",
    toolsSubtitle = "Shell, Files, Pi, code-server",
    settingsTitle = "Settings",
    settingsSubtitle = "Server connections, appearance and language",
    close = "Close",
    connectionTitle = "Connect to BoxedAgent",
    connectionDesc = "Choose an existing server or add a connection.",
    apiUrl = "API URL",
    token = "Token (optional when auth is disabled)",
    connect = "Connect / Sign in",
    connecting = "Connecting…",
    serverSettings = "Settings",
    appSettings = "App settings",
    serverManagement = "Server connections",
    addServer = "Add server",
    editServer = "Edit server",
    serverName = "Server name",
    save = "Save",
    saveAndConnect = "Save & connect",
    active = "Active",
    switchServer = "Switch connection",
    delete = "Delete",
    theme = "Appearance",
    lightMode = "Light mode",
    darkMode = "Dark mode",
    language = "Language",
    followSystem = "Follow system",
    chinese = "中文",
    english = "English",
    currentServer = "Current server",
    toolsTerminal = "Shell",
    toolsFiles = "Files",
    toolsPi = "Pi",
    toolsCode = "Code"
)

private fun ColorScheme.isDarkLike(): Boolean = background.luminance() < 0.5f || surface.luminance() < 0.5f

private fun stringsFor(mode: AppLanguageMode): AppStrings = when (mode) {
    AppLanguageMode.Zh -> ZhStrings
    AppLanguageMode.En -> EnStrings
    AppLanguageMode.System -> if (Locale.getDefault().language.startsWith("zh")) ZhStrings else EnStrings
}
private val LocalAppStrings = staticCompositionLocalOf { ZhStrings }
@Composable private fun localized(zh: String, en: String): String = if (LocalAppStrings.current === EnStrings) en else zh
private const val PREVIEW_LARGE_FILE_THRESHOLD_BYTES: Long = 10L * 1024L * 1024L
private const val APK_MIME_TYPE = "application/vnd.android.package-archive"
private const val TOOL_CODE_COLLAPSED_CHARS = 12_000

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BoxedAgentApp(viewModel: AppViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val strings = stringsFor(state.languageMode)
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    LaunchedEffect(state.tuiTerminalLaunch?.id) {
        val launch = state.tuiTerminalLaunch ?: return@LaunchedEffect
        context.startActivity(Intent(context, TerminalActivity::class.java).apply {
            putExtra(TerminalActivity.EXTRA_BASE_URL, viewModel.baseUrl())
            putExtra(TerminalActivity.EXTRA_TOKEN, viewModel.bearerToken())
            putExtra(TerminalActivity.EXTRA_MODE, TerminalActivity.MODE_TUI)
            putExtra(TerminalActivity.EXTRA_SESSION_ID, launch.sessionId)
            putExtra(TerminalActivity.EXTRA_SESSION_NAME, launch.sessionName)
        })
        viewModel.clearTuiTerminalLaunch(launch.id)
    }

    LaunchedEffect(state.event?.id) {
        val event = state.event ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(event.message)
        viewModel.clearEvent(event.id)
    }

    CompositionLocalProvider(LocalAppStrings provides strings) {
        BoxedAgentAppContent(state, viewModel, snackbarHostState, strings)
    }
}

@Composable
private fun BoxedAgentAppContent(state: AppUiState, viewModel: AppViewModel, snackbarHostState: SnackbarHostState, strings: AppStrings) {
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
                title = strings.boxesTitle,
                subtitle = strings.boxesSubtitle,
                onClose = { viewModel.setPanel(MainPanel.Chat) },
                actions = { IconButton(onClick = { viewModel.setPanel(MainPanel.Settings) }, modifier = Modifier.size(40.dp)) { Icon(Icons.Rounded.Settings, contentDescription = strings.settingsTitle) } }
            ) { BoxesScreen(state, viewModel) }
            SideOverlay(
                visible = state.selectedPanel == MainPanel.Tools,
                fromStart = false,
                title = strings.toolsTitle,
                subtitle = strings.toolsSubtitle,
                onClose = { viewModel.setPanel(MainPanel.Chat) }
            ) { ToolsScreen(state, viewModel) }
            SideOverlay(
                visible = state.selectedPanel == MainPanel.Settings,
                fromStart = true,
                title = strings.settingsTitle,
                subtitle = strings.settingsSubtitle,
                onClose = { viewModel.setPanel(MainPanel.Boxes) }
            ) { SettingsScreen(state, viewModel, strings) }
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
    actions: @Composable RowScope.() -> Unit = {},
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
                    IconButton(onClick = onClose, modifier = Modifier.size(42.dp)) { Icon(Icons.Rounded.Close, contentDescription = localized("关闭", "Close")) }
                    Column(Modifier.weight(1f)) {
                        Text(title, fontWeight = FontWeight.Black, fontSize = 22.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    actions()
                }
                Box(Modifier.weight(1f)) { content() }
            }
        }
    }
}

@Composable
private fun ConnectionScreen(state: AppUiState, viewModel: AppViewModel, modifier: Modifier = Modifier) {
    val strings = stringsFor(state.languageMode)
    var editingProfile by remember { mutableStateOf<ServerProfile?>(null) }
    var addingProfile by remember { mutableStateOf(false) }
    var deleteProfile by remember { mutableStateOf<ServerProfile?>(null) }

    LazyColumn(
        modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(strings.connectionTitle, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
                Text(strings.connectionDesc, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item {
            SettingsSectionCard(title = strings.serverManagement, icon = Icons.Rounded.Cloud) {
                if (state.serverProfiles.isEmpty()) {
                    Text(localized("还没有服务器连接", "No server connections yet"), fontWeight = FontWeight.Bold)
                    Text(localized("点击下方“新增服务器”添加 API 地址和 Token。", "Tap Add server below to enter the API URL and token."), color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    state.serverProfiles.forEach { profile ->
                        ServerProfileRow(
                            profile = profile,
                            active = profile.id == state.activeServerProfileId && state.authenticated,
                            strings = strings,
                            switchLabel = if (state.authLoading && profile.id == state.activeServerProfileId) strings.connecting else strings.connect,
                            switchEnabled = !state.authLoading,
                            editEnabled = !state.authLoading,
                            deleteEnabled = !state.authLoading,
                            onSwitch = { viewModel.switchServerProfile(profile.id) },
                            onEdit = { editingProfile = profile },
                            onDelete = { deleteProfile = profile }
                        )
                    }
                }
                OutlinedButton(onClick = { addingProfile = true }, enabled = !state.authLoading, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(strings.addServer)
                }
            }
        }
        if (state.authLoading) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        state.connectionError?.let { error ->
            item { AssistChip(onClick = {}, label = { Text(error) }, colors = AssistChipDefaults.assistChipColors(labelColor = MaterialTheme.colorScheme.error)) }
        }
    }

    if (addingProfile) ServerProfileDialog(strings = strings, initial = null, onDismiss = { addingProfile = false }, onSave = { name, url, token, connect -> addingProfile = false; viewModel.saveServerProfile(null, name, url, token, connect) })
    editingProfile?.let { profile ->
        ServerProfileDialog(strings = strings, initial = profile, onDismiss = { editingProfile = null }, onSave = { name, url, token, connect -> editingProfile = null; viewModel.saveServerProfile(profile.id, name, url, token, connect) })
    }
    deleteProfile?.let { profile ->
        ConfirmDialog(strings.delete, "${strings.delete} ${profile.name}?", onDismiss = { deleteProfile = null }, onConfirm = { viewModel.deleteServerProfile(profile.id); deleteProfile = null })
    }
}

@Composable
private fun SettingsScreen(state: AppUiState, viewModel: AppViewModel, strings: AppStrings) {
    var editingProfile by remember { mutableStateOf<ServerProfile?>(null) }
    var addingProfile by remember { mutableStateOf(false) }
    var deleteProfile by remember { mutableStateOf<ServerProfile?>(null) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            SettingsSectionCard(title = strings.serverManagement, icon = Icons.Rounded.Cloud) {
                Text(strings.currentServer, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                state.serverProfiles.forEach { profile ->
                    ServerProfileRow(
                        profile = profile,
                        active = profile.id == state.activeServerProfileId,
                        strings = strings,
                        onSwitch = { viewModel.switchServerProfile(profile.id) },
                        onEdit = { editingProfile = profile },
                        onDelete = { deleteProfile = profile }
                    )
                }
                OutlinedButton(onClick = { addingProfile = true }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(strings.addServer)
                }
            }
        }
        item {
            SettingsSectionCard(title = strings.appSettings, icon = Icons.Rounded.Tune) {
                Text(strings.theme, fontWeight = FontWeight.Bold)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SettingsChoiceChip(strings.lightMode, selected = state.themeMode == AppThemeMode.Light, modifier = Modifier.weight(1f)) { viewModel.setThemeMode(AppThemeMode.Light) }
                    SettingsChoiceChip(strings.darkMode, selected = state.themeMode == AppThemeMode.Dark, modifier = Modifier.weight(1f)) { viewModel.setThemeMode(AppThemeMode.Dark) }
                }
                Spacer(Modifier.height(4.dp))
                Text(strings.language, fontWeight = FontWeight.Bold)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SettingsChoiceChip(strings.followSystem, selected = state.languageMode == AppLanguageMode.System, modifier = Modifier.fillMaxWidth()) { viewModel.setLanguageMode(AppLanguageMode.System) }
                    SettingsChoiceChip(strings.chinese, selected = state.languageMode == AppLanguageMode.Zh, modifier = Modifier.fillMaxWidth()) { viewModel.setLanguageMode(AppLanguageMode.Zh) }
                    SettingsChoiceChip(strings.english, selected = state.languageMode == AppLanguageMode.En, modifier = Modifier.fillMaxWidth()) { viewModel.setLanguageMode(AppLanguageMode.En) }
                }
            }
        }
    }
    if (addingProfile) ServerProfileDialog(strings = strings, initial = null, onDismiss = { addingProfile = false }, onSave = { name, url, token, connect -> addingProfile = false; viewModel.saveServerProfile(null, name, url, token, connect) })
    editingProfile?.let { profile ->
        ServerProfileDialog(strings = strings, initial = profile, onDismiss = { editingProfile = null }, onSave = { name, url, token, connect -> editingProfile = null; viewModel.saveServerProfile(profile.id, name, url, token, connect) })
    }
    deleteProfile?.let { profile ->
        ConfirmDialog(strings.delete, "${strings.delete} ${profile.name}?", onDismiss = { deleteProfile = null }, onConfirm = { viewModel.deleteServerProfile(profile.id); deleteProfile = null })
    }
}

@Composable
private fun SettingsSectionCard(title: String, icon: ImageVector, content: @Composable ColumnScope.() -> Unit) {
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer) {
                    Icon(icon, contentDescription = null, modifier = Modifier.padding(8.dp).size(20.dp))
                }
                Text(title, fontWeight = FontWeight.Black, fontSize = 17.sp)
            }
            content()
        }
    }
}

@Composable
private fun SettingsChoiceChip(label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        leadingIcon = { if (selected) Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(16.dp)) },
        modifier = modifier
    )
}

@Composable
private fun ServerProfileRow(
    profile: ServerProfile,
    active: Boolean,
    strings: AppStrings,
    switchLabel: String = strings.switchServer,
    switchEnabled: Boolean = !active,
    editEnabled: Boolean = true,
    deleteEnabled: Boolean = !active,
    onSwitch: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    OutlinedCard(Modifier.fillMaxWidth(), border = androidx.compose.foundation.BorderStroke(1.dp, if (active) MaterialTheme.colorScheme.primary.copy(alpha = .55f) else MaterialTheme.colorScheme.outlineVariant)) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Rounded.Dns, contentDescription = null, tint = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                Column(Modifier.weight(1f)) {
                    Text(profile.name, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(profile.baseUrl, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (active) AssistChip(onClick = {}, label = { Text(strings.active, fontSize = 11.sp) }, leadingIcon = { Icon(Icons.Rounded.CheckCircle, contentDescription = null, modifier = Modifier.size(14.dp)) })
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onSwitch, enabled = switchEnabled, modifier = Modifier.weight(1f)) { Text(switchLabel, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                IconButton(onClick = onEdit, enabled = editEnabled) { Icon(Icons.Rounded.Edit, contentDescription = strings.editServer) }
                IconButton(onClick = onDelete, enabled = deleteEnabled) { Icon(Icons.Rounded.Delete, contentDescription = strings.delete, tint = if (deleteEnabled) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .45f)) }
            }
        }
    }
}

@Composable
private fun ServerProfileDialog(strings: AppStrings, initial: ServerProfile?, onDismiss: () -> Unit, onSave: (String, String, String, Boolean) -> Unit) {
    var name by remember(initial?.id) { mutableStateOf(initial?.name.orEmpty()) }
    var baseUrl by remember(initial?.id) { mutableStateOf(initial?.baseUrl.orEmpty()) }
    var token by remember(initial?.id) { mutableStateOf(initial?.token.orEmpty()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) strings.addServer else strings.editServer) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text(strings.serverName) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(baseUrl, { baseUrl = it }, label = { Text(strings.apiUrl) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(token, { token = it }, label = { Text(strings.token) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(strings.close) } },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { onSave(name, baseUrl, token, false) }, enabled = baseUrl.isNotBlank()) { Text(strings.save) }
                Button(onClick = { onSave(name, baseUrl, token, true) }, enabled = baseUrl.isNotBlank()) { Text(strings.saveAndConnect) }
            }
        }
    )
}

@Composable
private fun BoxesScreen(state: AppUiState, viewModel: AppViewModel) {
    val context = LocalContext.current
    fun openTuiSession(session: AgentSessionRecord) {
        context.startActivity(Intent(context, TerminalActivity::class.java).apply {
            putExtra(TerminalActivity.EXTRA_BASE_URL, viewModel.baseUrl())
            putExtra(TerminalActivity.EXTRA_TOKEN, viewModel.bearerToken())
            putExtra(TerminalActivity.EXTRA_MODE, TerminalActivity.MODE_TUI)
            putExtra(TerminalActivity.EXTRA_SESSION_ID, session.id)
            putExtra(TerminalActivity.EXTRA_SESSION_NAME, session.name)
        })
    }
    var createBox by remember { mutableStateOf(false) }
    var createSession by remember { mutableStateOf(false) }
    var renameBox by remember { mutableStateOf<BoxRecord?>(null) }
    var duplicateBox by remember { mutableStateOf<BoxRecord?>(null) }
    var cloneBox by remember { mutableStateOf<BoxRecord?>(null) }
    var deleteBox by remember { mutableStateOf<BoxRecord?>(null) }
    var renameSession by remember { mutableStateOf<AgentSessionRecord?>(null) }
    var deleteSession by remember { mutableStateOf<AgentSessionRecord?>(null) }
    var forkSession by remember { mutableStateOf<AgentSessionRecord?>(null) }
    var treeSession by remember { mutableStateOf<AgentSessionRecord?>(null) }
    val sessionsForBox = state.sessions.filter { it.boxId == state.activeBoxId }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column { Text("Boxes", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold); Text(localized("沙箱与会话", "Sandboxes and sessions"), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                FilledTonalButton(onClick = { createBox = true }, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)) { Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(16.dp)); Text("Box") }
            }
        }
        if (state.boxes.isEmpty()) item { EmptyCard(localized("还没有 Box", "No boxes yet"), localized("点击右上角创建一个开发沙箱。", "Create a sandbox from the top right.")) }
        items(state.boxes, key = { it.id }) { box ->
            BoxCard(
                box = box,
                active = box.id == state.activeBoxId,
                onSelect = { viewModel.selectBox(box.id) },
                onStartStop = { if (box.status == "running") viewModel.stopBox(box.id) else viewModel.startBox(box.id) },
                onRename = { renameBox = box },
                onDuplicate = { duplicateBox = box },
                onClone = { cloneBox = box },
                onDelete = { deleteBox = box }
            )
        }
        item { HorizontalDivider(Modifier.padding(vertical = 4.dp)) }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column { Text("Sessions", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold); Text(state.activeBox?.name ?: localized("请先选择 Box", "Select a box"), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                FilledTonalButton(onClick = { createSession = true }, enabled = state.activeBox != null, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)) { Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(16.dp)); Text("Session") }
            }
        }
        if (state.activeBox != null && sessionsForBox.isEmpty()) item { EmptyCard(localized("当前 Box 没有 Session", "No sessions in this box"), localized("创建 Session 后即可对话。", "Create a session to start chatting.")) }
        items(sessionsForBox, key = { it.id }) { session ->
            SessionCard(
                session = session,
                active = session.id == state.activeSessionId,
                onSelect = {
                    viewModel.selectSession(session.id)
                    if (session.kind == "tui") openTuiSession(session) else viewModel.setPanel(MainPanel.Chat)
                },
                onStartStop = { if (session.status == "running" || session.status == "working") viewModel.stopSession(session.id) else viewModel.startSession(session.id) },
                onRename = { renameSession = session },
                onTree = { treeSession = session },
                onClone = { if (session.kind != "tui") viewModel.cloneSession(session.id, nextReplicatedName(session.name, "session", "-clone")) },
                onDuplicate = { viewModel.duplicateSession(session.id, nextReplicatedName(session.name, "session", "-copy")) },
                onFork = { if (session.kind != "tui") forkSession = session },
                onDelete = { deleteSession = session }
            )
        }
    }

    if (createBox) CreateBoxDialog(profiles = state.imageProfiles, onDismiss = { createBox = false }, onCreate = { name, image, desc, password, provider, model, thinking, imageProfileId, buildImage ->
        createBox = false; viewModel.createBox(name, image, desc, password, provider, model, thinking, imageProfileId, buildImage)
    })
    state.activeBox?.let { activeBox -> if (createSession) CreateSessionDialog(activeBox, viewModel, onDismiss = { createSession = false }) }
    renameBox?.let { box -> InputDialog(localized("重命名 Box", "Rename box"), box.name, onDismiss = { renameBox = null }, onConfirm = { viewModel.renameBox(box.id, it); renameBox = null }) }
    duplicateBox?.let { box -> InputDialog(localized("复刻 Box 配置", "Duplicate box config"), nextReplicatedName(box.name, "box", "-copy"), onDismiss = { duplicateBox = null }, onConfirm = { viewModel.duplicateBox(box.id, it); duplicateBox = null }) }
    cloneBox?.let { box -> InputDialog(localized("克隆 Box", "Clone box"), nextReplicatedName(box.name, "box", "-clone"), onDismiss = { cloneBox = null }, onConfirm = { viewModel.cloneBox(box.id, it); cloneBox = null }) }
    deleteBox?.let { box -> ConfirmDialog(localized("删除 Box", "Delete box"), localized("删除", "Delete") + " ${box.name}?", onDismiss = { deleteBox = null }, onConfirm = { viewModel.deleteBox(box.id); deleteBox = null }) }
    renameSession?.let { s -> InputDialog(localized("重命名 Session", "Rename session"), s.name, onDismiss = { renameSession = null }, onConfirm = { viewModel.renameSession(s.id, it); renameSession = null }) }
    deleteSession?.let { s -> ConfirmDialog(localized("删除 Session", "Delete session"), localized("删除", "Delete") + " ${s.name}?", onDismiss = { deleteSession = null }, onConfirm = { viewModel.deleteSession(s.id); deleteSession = null }) }
    forkSession?.let { s -> ForkDialog(s, viewModel, onDismiss = { forkSession = null }) }
    treeSession?.let { s -> SessionTreeDialog(s, viewModel, onDismiss = { treeSession = null }) }
}

@Composable
private fun BoxCard(box: BoxRecord, active: Boolean, onSelect: () -> Unit, onStartStop: () -> Unit, onRename: () -> Unit, onDuplicate: () -> Unit, onClone: () -> Unit, onDelete: () -> Unit) {
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
            Box { IconButton(onClick = { menu = true }, modifier = Modifier.size(40.dp)) { Icon(Icons.Rounded.MoreVert, contentDescription = localized("操作", "Actions")) }; DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text(localized("重命名", "Rename")) }, onClick = { menu = false; onRename() })
                DropdownMenuItem(text = { Text(if (box.status == "running") localized("停止", "Stop") else localized("启动", "Start")) }, onClick = { menu = false; onStartStop() })
                DropdownMenuItem(text = { Text(localized("复刻配置", "Duplicate config")) }, onClick = { menu = false; onDuplicate() })
                DropdownMenuItem(text = { Text(localized("克隆", "Clone")) }, onClick = { menu = false; onClone() })
                DropdownMenuItem(text = { Text(localized("删除", "Delete")) }, onClick = { menu = false; onDelete() })
            } }
        }
    }
}

@Composable
private fun SessionCard(session: AgentSessionRecord, active: Boolean, onSelect: () -> Unit, onStartStop: () -> Unit, onRename: () -> Unit, onTree: () -> Unit, onClone: () -> Unit, onDuplicate: () -> Unit, onFork: () -> Unit, onDelete: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    ElevatedCard(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onSelect),
        colors = CardDefaults.elevatedCardColors(containerColor = if (active) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(session.name, fontWeight = FontWeight.Bold, fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    if (session.kind == "tui") AssistChip(onClick = {}, label = { Text("TUI", fontSize = 11.sp) }, leadingIcon = { Icon(Icons.Rounded.Terminal, contentDescription = null, modifier = Modifier.size(14.dp)) }, modifier = Modifier.height(28.dp))
                    StatusDot(session.status)
                }
                Text(if (session.kind == "tui") listOf("pi TUI", session.cwd ?: "/workspace").joinToString(" · ") else listOf(session.provider, session.model, session.thinkingLevel, session.cwd ?: "/workspace").filterNotNull().joinToString(" · "), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                session.error?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis) }
            }
            Box { IconButton(onClick = { menu = true }, modifier = Modifier.size(40.dp)) { Icon(Icons.Rounded.MoreVert, contentDescription = localized("操作", "Actions")) }; DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text(if (session.status == "running" || session.status == "working") localized("停止", "Stop") else localized("启动", "Start")) }, onClick = { menu = false; onStartStop() })
                DropdownMenuItem(text = { Text(localized("重命名", "Rename")) }, onClick = { menu = false; onRename() })
                if (session.kind != "tui") DropdownMenuItem(text = { Text("Tree") }, onClick = { menu = false; onTree() })
                if (session.kind != "tui") DropdownMenuItem(text = { Text("Fork") }, onClick = { menu = false; onFork() })
                if (session.kind != "tui") DropdownMenuItem(text = { Text("Clone") }, onClick = { menu = false; onClone() })
                DropdownMenuItem(text = { Text(localized("复刻空配置", "Duplicate config")) }, onClick = { menu = false; onDuplicate() })
                DropdownMenuItem(text = { Text(localized("删除", "Delete")) }, onClick = { menu = false; onDelete() })
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CreateBoxDialog(profiles: List<ImageProfileRecord>, onDismiss: () -> Unit, onCreate: (String, String, String, String, String, String, String, String?, Boolean) -> Unit) {
    var name by remember { mutableStateOf("box-${(100..999).random()}") }
    var image by remember { mutableStateOf("boxedagent/ubuntu-dev:24.04") }
    var selectedProfileId by remember(profiles) { mutableStateOf(profiles.firstOrNull()?.id) }
    var buildImage by remember { mutableStateOf(true) }
    var password by remember { mutableStateOf("boxedagent") }
    var provider by remember { mutableStateOf("") }
    var model by remember { mutableStateOf("") }
    var thinking by remember { mutableStateOf("medium") }
    var desc by remember { mutableStateOf("") }
    val selectedProfile = profiles.firstOrNull { it.id == selectedProfileId }
    val canCreate = name.isNotBlank() && (selectedProfile != null || image.isNotBlank())
    AlertDialog(onDismissRequest = onDismiss, confirmButton = { Button(enabled = canCreate, onClick = { onCreate(name, if (selectedProfile == null) image else "", desc, password, provider, model, thinking, selectedProfile?.id, buildImage) }) { Text(localized("创建", "Create")) } }, dismissButton = { TextButton(onClick = onDismiss) { Text(localized("取消", "Cancel")) } }, title = { Text(localized("创建 Box", "Create box")) }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(name, { name = it }, label = { Text(localized("名称", "Name")) }, singleLine = true)
            if (profiles.isNotEmpty()) {
                Text(localized("镜像模板", "Image profile"), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    FilterChip(selected = selectedProfileId == null, onClick = { selectedProfileId = null }, label = { Text(localized("手动镜像", "Manual image")) })
                    profiles.take(12).forEach { profile ->
                        FilterChip(selected = selectedProfileId == profile.id, onClick = { selectedProfileId = profile.id; image = profile.image }, label = { Text(profile.name.take(24), maxLines = 1, overflow = TextOverflow.Ellipsis) })
                    }
                }
                selectedProfile?.let { profile -> Text(listOf(profile.image, profile.status, profile.description.orEmpty()).filter { it.isNotBlank() }.joinToString(" · "), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis) }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Switch(checked = buildImage, onCheckedChange = { buildImage = it })
                    Text(localized("创建前确保/构建镜像", "Ensure/build image before create"), fontSize = 13.sp)
                }
            }
            if (selectedProfile == null) OutlinedTextField(image, { image = it }, label = { Text(localized("Docker 镜像", "Docker image")) }, singleLine = true)
            OutlinedTextField(desc, { desc = it }, label = { Text(localized("描述", "Description")) }, singleLine = true)
            OutlinedTextField(password, { password = it }, label = { Text(localized("code-server 密码", "code-server password")) }, singleLine = true)
            OutlinedTextField(provider, { provider = it }, label = { Text(localized("默认 Provider", "Default provider")) }, singleLine = true)
            OutlinedTextField(model, { model = it }, label = { Text(localized("默认 Model", "Default model")) }, singleLine = true)
            DropdownField("Thinking", thinking, ThinkingLevels, { thinking = it })
        }
    })
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CreateSessionDialog(box: BoxRecord, viewModel: AppViewModel, onDismiss: () -> Unit) {
    var kind by remember { mutableStateOf("chat") }
    var name by remember { mutableStateOf("Session") }
    var cwd by remember { mutableStateOf("/workspace") }
    var thinking by remember { mutableStateOf(box.pi.defaultThinkingLevel ?: "medium") }
    var provider by remember { mutableStateOf(box.pi.defaultProvider.orEmpty()) }
    var model by remember { mutableStateOf(box.pi.defaultModel.orEmpty()) }
    var models by remember { mutableStateOf<List<PiModel>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var search by remember { mutableStateOf("") }
    var launchArgsText by remember { mutableStateOf("") }
    var showFolderPicker by remember { mutableStateOf(false) }
    var showManualModel by remember { mutableStateOf(false) }
    LaunchedEffect(box.id) { loading = true; models = runCatching { viewModel.loadBoxModels(box.id) }.getOrDefault(emptyList()); loading = false }
    val visible = remember(models, search) { models.filter { "${it.providerNameOrNull().orEmpty()} ${it.id} ${it.name.orEmpty()}".contains(search, ignoreCase = true) }.take(120) }
    val selectedModel = remember(models, provider, model) { models.firstOrNull { it.id == model && it.providerNameOrNull() == provider } }
    val manualFieldsVisible = showManualModel || (!loading && models.isEmpty())
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { Button(onClick = { viewModel.createSession(name, cwd, provider, model, thinking, kind = kind, launchArgsText = launchArgsText); onDismiss() }, enabled = name.isNotBlank()) { Text(localized("创建", "Create")) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(localized("取消", "Cancel")) } },
        title = { Text(localized("新建 Session", "New session")) },
        text = {
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 560.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                item {
                    Surface(Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surfaceContainerLow, shape = RoundedCornerShape(16.dp)) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Icon(Icons.Rounded.Dns, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Column(Modifier.weight(1f)) {
                                Text(box.name, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(localized("为此 Box 创建新对话", "Create a new session in this box"), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                            }
                        }
                    }
                }
                item {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(
                            selected = kind == "chat",
                            onClick = { kind = "chat"; if (name.startsWith("TUI Session")) name = "Session" },
                            leadingIcon = { Icon(Icons.Rounded.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp)) },
                            label = { Text("Chat Session") }
                        )
                        FilterChip(
                            selected = kind == "tui",
                            onClick = { kind = "tui"; if (name == "Session" || name.startsWith("Session ")) name = "TUI Session" },
                            leadingIcon = { Icon(Icons.Rounded.Terminal, contentDescription = null, modifier = Modifier.size(16.dp)) },
                            label = { Text("TUI Session") }
                        )
                    }
                    Text(if (kind == "tui") localized("以真实 pi 终端界面运行；关闭终端只会 detach。", "Runs the real pi terminal UI; closing the terminal only detaches.") else localized("普通 Chat Session，使用 Android 原生聊天界面。", "Regular chat session using the native Android chat UI."), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                }
                item {
                    CompactSessionTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = localized("名称", "Name"),
                        icon = Icons.Rounded.Edit
                    )
                }
                item {
                    CompactSessionTextField(
                        value = cwd,
                        onValueChange = { cwd = it },
                        label = localized("工作目录", "Working directory"),
                        icon = Icons.Rounded.FolderOpen,
                        trailing = { IconButton(onClick = { showFolderPicker = true }) { Icon(Icons.Rounded.FolderOpen, contentDescription = localized("选择目录", "Choose folder")) } }
                    )
                }
                item {
                    OutlinedButton(onClick = { showFolderPicker = true }, modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)) {
                        Icon(Icons.Rounded.CreateNewFolder, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(localized("浏览或新建工作目录", "Browse or create working folder"), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                if (kind != "tui") item {
                    Text("Thinking", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        ThinkingLevels.forEach { level ->
                            FilterChip(
                                selected = thinking == level,
                                onClick = { thinking = level },
                                label = { Text(level, fontSize = 12.sp) },
                                leadingIcon = { if (thinking == level) Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(15.dp)) }
                            )
                        }
                    }
                }
                if (kind != "tui") item {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Column(Modifier.weight(1f)) {
                            Text(localized("模型", "Model"), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Text(modelSelectionSummary(provider, model, selectedModel), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        TextButton(onClick = { showManualModel = !showManualModel }, enabled = models.isNotEmpty()) { Text(if (showManualModel) localized("收起", "Hide") else localized("手动填写", "Manual")) }
                    }
                    OutlinedTextField(
                        value = search,
                        onValueChange = { search = it },
                        leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                        label = { Text(localized("搜索 provider / model", "Search provider / model")) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                if (kind == "tui") item {
                    Surface(Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surfaceContainerLow, shape = RoundedCornerShape(16.dp), border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
                        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(localized("自定义 pi 参数", "Custom pi args"), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Text(localized("例如 -ne -ns -nt、--extension ./ext.ts。BoxedAgent 会自动管理 --mode 与 session。", "Examples: -ne -ns -nt, --extension ./ext.ts. BoxedAgent manages --mode and session automatically."), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                            CodeTextField("launchArgs", launchArgsText, { launchArgsText = it })
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                listOf("-ne", "-ns", "-nt", "--no-context-files", "--extension ").forEach { preset -> AssistChip(onClick = { launchArgsText = appendArgText(launchArgsText, preset) }, label = { Text(preset) }) }
                            }
                        }
                    }
                }
                if (kind != "tui" && manualFieldsVisible) item {
                    Surface(Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surfaceContainerLow, shape = RoundedCornerShape(16.dp), border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
                        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            CompactSessionTextField(provider, { provider = it }, localized("Provider（可选）", "Provider (optional)"), Icons.Rounded.Cloud)
                            CompactSessionTextField(model, { model = it }, localized("Model（可选）", "Model (optional)"), Icons.Rounded.AutoAwesome)
                        }
                    }
                }
                if (kind != "tui" && loading) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
                if (kind != "tui") items(visible, key = { "${it.providerNameOrNull()}/${it.id}" }) { item ->
                    ModelPickerRow(
                        model = item,
                        selected = item.id == model && item.providerNameOrNull() == provider,
                        compact = true,
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { provider = item.providerNameOrNull().orEmpty(); model = item.id }
                    )
                }
                if (kind != "tui" && !loading && visible.isEmpty()) item { Text(localized("没有匹配模型，可手动填写 provider / model。", "No matching models. You can enter provider / model manually."), Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp) }
            }
        }
    )
    if (showFolderPicker) {
        SessionFolderPickerDialog(
            viewModel = viewModel,
            initialCwd = cwd,
            onDismiss = { showFolderPicker = false },
            onSelect = { selected -> cwd = selected; showFolderPicker = false }
        )
    }
}

@Composable
private fun CompactSessionTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    icon: ImageVector,
    trailing: @Composable (() -> Unit)? = null
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        leadingIcon = { Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp)) },
        trailingIcon = trailing,
        singleLine = true,
        shape = RoundedCornerShape(14.dp),
        textStyle = LocalTextStyle.current.copy(fontSize = 14.sp),
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun SessionFolderPickerDialog(viewModel: AppViewModel, initialCwd: String, onDismiss: () -> Unit, onSelect: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    var path by remember(initialCwd) { mutableStateOf(workspaceRelPath(initialCwd)) }
    var entries by remember { mutableStateOf<List<FileEntry>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var creating by remember { mutableStateOf(false) }
    var createName by remember { mutableStateOf("") }
    val invalidNameText = localized("名称不能包含 /、\\ 或 ..", "Name cannot contain /, \\ or ..")
    val directories = remember(entries) { entries.filter { it.type == "directory" || it.type == "dir" }.sortedBy { it.name.lowercase() } }
    fun createTargetPath(name: String): String = if (path == "." || path.isBlank()) name else "$path/$name"
    fun reload() { scope.launch { loadFiles(viewModel, path, { loading = it }, { entries = it }, { error = it }) } }
    LaunchedEffect(path) { loadFiles(viewModel, path, { loading = it }, { entries = it }, { error = it }) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(localized("选择工作目录", "Select working folder")) },
        text = {
            Column(Modifier.fillMaxWidth().heightIn(max = 520.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    FilledTonalIconButton(onClick = { path = parentPath(path) }, modifier = Modifier.size(36.dp)) { Icon(Icons.Rounded.ArrowUpward, contentDescription = localized("上级", "Up"), modifier = Modifier.size(19.dp)) }
                    FilledTonalIconButton(onClick = { reload() }, modifier = Modifier.size(36.dp)) { Icon(Icons.Rounded.Refresh, contentDescription = localized("刷新", "Refresh"), modifier = Modifier.size(19.dp)) }
                    OutlinedButton(onClick = { creating = true; createName = "" }, contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)) {
                        Icon(Icons.Rounded.CreateNewFolder, contentDescription = null, modifier = Modifier.size(17.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(localized("新建目录", "New folder"), fontSize = 13.sp)
                    }
                }
                Surface(Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surfaceContainerHighest, shape = RoundedCornerShape(10.dp)) {
                    Row(Modifier.padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Rounded.FolderOpen, contentDescription = null, modifier = Modifier.size(17.dp), tint = MaterialTheme.colorScheme.primary)
                        Text(workspaceAbsPath(path), fontFamily = FontFamily.Monospace, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    }
                }
                if (creating) {
                    Surface(Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surfaceContainerLow, shape = RoundedCornerShape(10.dp), border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
                        Row(Modifier.padding(8.dp), horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.CreateNewFolder, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            OutlinedTextField(createName, { createName = it }, label = { Text(localized("目录名", "Folder name")) }, singleLine = true, modifier = Modifier.weight(1f))
                            TextButton(onClick = { creating = false; createName = "" }) { Text(localized("取消", "Cancel")) }
                            Button(onClick = {
                                val name = createName.trim()
                                if (!isSafeFileName(name)) { error = invalidNameText; return@Button }
                                scope.launch {
                                    error = null
                                    runCatching {
                                        val target = normalizeFileBrowserPath(createTargetPath(name))
                                        viewModel.mkdir(target)
                                        creating = false
                                        createName = ""
                                        path = target
                                    }.onFailure { error = it.message }
                                }
                            }, enabled = createName.isNotBlank()) { Text(localized("创建", "Create")) }
                        }
                    }
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }
                if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
                Surface(Modifier.fillMaxWidth().heightIn(min = 220.dp, max = 320.dp), color = MaterialTheme.colorScheme.surfaceContainerLowest, shape = RoundedCornerShape(12.dp), border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
                    LazyColumn(Modifier.fillMaxSize()) {
                        item {
                            ListItem(
                                headlineContent = { Text(localized("使用当前目录", "Use current folder"), fontWeight = FontWeight.Bold) },
                                supportingContent = { Text(workspaceAbsPath(path), fontFamily = FontFamily.Monospace, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                leadingContent = { Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                                modifier = Modifier.clickable { onSelect(workspaceAbsPath(path)) }
                            )
                            HorizontalDivider()
                        }
                        items(directories, key = { it.path }) { entry ->
                            ListItem(
                                headlineContent = { Text(entry.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                supportingContent = { Text(workspaceAbsPath(entry.path), fontFamily = FontFamily.Monospace, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                leadingContent = { Icon(Icons.Rounded.Folder, contentDescription = null, tint = Color(0xFFD6A433)) },
                                trailingContent = { Icon(Icons.Rounded.ChevronRight, contentDescription = null) },
                                modifier = Modifier.clickable { path = workspaceRelPath(entry.path) }
                            )
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .65f))
                        }
                        if (!loading && directories.isEmpty()) item { Text(localized("没有子目录", "No subfolders"), Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                }
            }
        },
        confirmButton = { Button(onClick = { onSelect(workspaceAbsPath(path)) }) { Text(localized("选择此目录", "Select this folder")) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(localized("取消", "Cancel")) } }
    )
}

@Composable
private fun ForkDialog(session: AgentSessionRecord, viewModel: AppViewModel, onDismiss: () -> Unit) {
    var messages by remember { mutableStateOf<List<ForkMessage>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(session.id) { loading = true; error = null; runCatching { viewModel.loadForkMessages(session.id) }.onSuccess { messages = it }.onFailure { error = it.message }; loading = false }
    AlertDialog(onDismissRequest = onDismiss, confirmButton = { TextButton(onClick = onDismiss) { Text(localized("关闭", "Close")) } }, title = { Text("Fork Session") }, text = {
        Column(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(localized("选择分叉位置", "Choose a fork point"), color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            if (!loading && messages.isEmpty()) Text(localized("没有可 fork 的用户消息", "No user messages to fork"))
            messages.forEachIndexed { i, msg ->
                OutlinedCard(Modifier.fillMaxWidth().clickable { viewModel.forkSession(session.id, msg.entryId, nextReplicatedName(session.name, "session", "-fork")); onDismiss() }) {
                    Text("#${i + 1} ${msg.text.take(160)}", Modifier.padding(12.dp))
                }
            }
        }
    })
}

@Composable
private fun SessionTreeDialog(session: AgentSessionRecord, viewModel: AppViewModel, onDismiss: () -> Unit) {
    var tree by remember(session.id) { mutableStateOf(SessionTree()) }
    var loading by remember(session.id) { mutableStateOf(true) }
    var error by remember(session.id) { mutableStateOf<String?>(null) }
    var query by remember(session.id) { mutableStateOf("") }
    LaunchedEffect(session.id) {
        loading = true
        error = null
        runCatching { viewModel.loadSessionTree(session.id) }
            .onSuccess { tree = it }
            .onFailure { error = it.message }
        loading = false
    }
    val nodes = tree.nodes
    val filtered = remember(nodes, query) {
        val q = query.trim()
        if (q.isBlank()) nodes else nodes.filter { node ->
            listOf(node.text, node.label.orEmpty(), node.type, node.role.orEmpty()).joinToString(" ").contains(q, ignoreCase = true)
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text(localized("关闭", "Close")) } },
        title = { Text("Session Tree") },
        text = {
            Column(Modifier.heightIn(max = 560.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Text(localized("按 pi /tree 方式切换当前 Session 的活动分支。选择用户消息时，原消息会放回输入框。", "Navigate this session's active branch like pi /tree. Selecting a user message puts it back into the composer."), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text(localized("搜索节点", "Search nodes")) },
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth()
                )
                if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 13.sp) }
                if (!loading && nodes.isEmpty()) Text(localized("当前 Session 还没有可导航的历史。", "This session has no navigable history yet."), color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (!loading && nodes.isNotEmpty() && filtered.isEmpty()) Text(localized("没有匹配节点", "No matching nodes"), color = MaterialTheme.colorScheme.onSurfaceVariant)
                LazyColumn(Modifier.heightIn(max = 360.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(filtered, key = { it.id }) { node ->
                        SessionTreeNodeRow(node = node, onClick = { viewModel.navigateSessionTree(session.id, node.id); onDismiss() })
                    }
                }
                Text("${filtered.size} / ${nodes.size} nodes · ${session.name}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    )
}

@Composable
private fun SessionTreeNodeRow(node: SessionTreeNode, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val bg = when {
        node.active -> colors.primaryContainer
        node.inActivePath -> colors.surfaceContainerHigh
        else -> colors.surfaceContainerLow
    }
    val roleColor = when (node.role ?: node.type) {
        "user" -> colors.primary
        "assistant" -> colors.tertiary
        "toolResult" -> colors.secondary
        else -> colors.onSurfaceVariant
    }
    OutlinedCard(
        Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.outlinedCardColors(containerColor = bg),
        border = androidx.compose.foundation.BorderStroke(1.dp, if (node.active) colors.primary.copy(alpha = .55f) else colors.outlineVariant)
    ) {
        Row(Modifier.fillMaxWidth().padding(start = (node.depth * 14).coerceAtMost(92).dp, end = 10.dp, top = 9.dp, bottom = 9.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Surface(shape = RoundedCornerShape(7.dp), color = roleColor.copy(alpha = .13f), contentColor = roleColor) {
                Text(treeNodeRoleLabel(node), Modifier.padding(horizontal = 6.dp, vertical = 3.dp), fontSize = 10.sp, fontWeight = FontWeight.Black)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                val line = compactText(node.label?.let { "[$it]" }, previewText(node.text, 150), node.type.takeIf { node.text.isBlank() })
                Text(line, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, color = colors.onSurface)
                node.timestamp?.takeIf { it.isNotBlank() }?.let { Text(it, fontSize = 10.sp, color = colors.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            }
            if (node.active) Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = colors.primary, modifier = Modifier.size(17.dp))
        }
    }
}

private fun treeNodeRoleLabel(node: SessionTreeNode): String = when {
    node.role == "user" -> "USER"
    node.role == "assistant" -> "AI"
    node.role == "toolResult" -> "TOOL"
    node.type == "compaction" -> "CMP"
    node.type == "branch_summary" -> "SUM"
    node.type == "model_change" -> "MODEL"
    node.type == "thinking_level_change" -> "THINK"
    else -> node.type.take(6).uppercase().ifBlank { "NODE" }
}

@Composable
private fun InputDialog(title: String, initial: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var value by remember(initial) { mutableStateOf(initial) }
    AlertDialog(onDismissRequest = onDismiss, confirmButton = { Button(enabled = value.isNotBlank(), onClick = { onConfirm(value.trim()) }) { Text(localized("确定", "OK")) } }, dismissButton = { TextButton(onClick = onDismiss) { Text(localized("取消", "Cancel")) } }, title = { Text(title) }, text = { OutlinedTextField(value, { value = it }, singleLine = true, modifier = Modifier.fillMaxWidth()) })
}

@Composable
private fun ConfirmDialog(title: String, text: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, confirmButton = { Button(onClick = onConfirm, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { Text(localized("删除", "Delete")) } }, dismissButton = { TextButton(onClick = onDismiss) { Text(localized("取消", "Cancel")) } }, title = { Text(title) }, text = { Text(text) })
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
    val autoOpenProgressMessageId = if (state.activeTurn && latestProgressMessageId == lastMessage?.id) latestProgressMessageId else null
    val streamingAssistantMessageId = if (state.activeTurn) state.activeMessages.lastOrNull { it.role == "assistant" }?.id else null
    val showWaitingCard = state.activeTurn && !lastMessage.isAgentOutputInProgress()
    val latestProgressArgsKey = latestProgressMessage?.toolArgs?.toString()?.let { "${it.length}:${it.hashCode()}" }

    LaunchedEffect(state.composerInsert?.id) {
        val insert = state.composerInsert ?: return@LaunchedEffect
        if (insert.sessionId == null || insert.sessionId == state.activeSessionId) {
            text = if (insert.replace) insert.text else appendComposerText(text, insert.text)
            viewModel.clearComposerInsert(insert.id)
        }
    }

    val stickToBottom by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val total = info.totalItemsCount
            total == 0 || (info.visibleItemsInfo.lastOrNull()?.index ?: 0) >= total - 3
        }
    }
    // Do not key this effect on scroll position: crossing the near-bottom threshold
    // should not itself force a jump to the bottom.
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
        latestProgressArgsKey,
        latestProgressMessage?.toolStatus,
        state.activeTurn,
        showWaitingCard
    ) {
        if ((state.activeMessages.isNotEmpty() || state.activeTurn) && stickToBottom) {
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

    fun openTuiSession(session: AgentSessionRecord) {
        context.startActivity(Intent(context, TerminalActivity::class.java).apply {
            putExtra(TerminalActivity.EXTRA_BASE_URL, viewModel.baseUrl())
            putExtra(TerminalActivity.EXTRA_TOKEN, viewModel.bearerToken())
            putExtra(TerminalActivity.EXTRA_MODE, TerminalActivity.MODE_TUI)
            putExtra(TerminalActivity.EXTRA_SESSION_ID, session.id)
            putExtra(TerminalActivity.EXTRA_SESSION_NAME, session.name)
        })
    }

    val activeBox = state.activeBox
    if (activeBox == null) {
        Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { viewModel.setPanel(MainPanel.Boxes) }) { Icon(Icons.Rounded.Menu, contentDescription = "Boxes", tint = MaterialTheme.colorScheme.onSurface) }
                Text("BoxedAgent", fontSize = 23.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
                IconButton(onClick = { viewModel.setPanel(MainPanel.Tools) }) { Icon(Icons.Rounded.FormatListBulleted, contentDescription = "Tools", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            CenterWelcome(localized("选择或创建 Box", "Select or create a box"), localized("点击左上角打开 Boxes。", "Open Boxes from the top left."))
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
            CenterWelcome(localized("选择或创建 Session", "Select or create a session"), localized("点击左上角打开 Sessions。", "Open Sessions from the top left."))
        }
        return
    }
    if (session.kind == "tui") {
        TuiSessionPlaceholder(
            session = session,
            box = activeBox,
            resources = state.activeResources,
            isWorking = state.activeTurn,
            onBoxes = { viewModel.setPanel(MainPanel.Boxes) },
            onTools = { viewModel.setPanel(MainPanel.Tools) },
            onRefresh = { viewModel.refresh() },
            onOpen = { openTuiSession(session) },
            onReload = { viewModel.reloadSession(session.id) },
            onStop = { viewModel.stopSession(session.id) }
        )
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
            onRefresh = { viewModel.refresh(); viewModel.loadSessionMessages() }
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
                        autoOpenProgress = msg.id == autoOpenProgressMessageId,
                        isLatestMessage = msg.id == lastMessage?.id,
                        streaming = msg.id == streamingAssistantMessageId,
                        onFork = { forkDialogSession = session },
                        onShowDialog = { dialogMessage = msg.text.ifBlank { msg.toolResult.orEmpty() } },
                        onExpand = { viewModel.expandMessage(it, session.id) }
                    )
                }
                if (showWaitingCard) item { ProcessingCard() }
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
                model = session.model ?: localized("模型", "Model"),
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
        SheetHeader(Icons.Rounded.Search, localized("搜索消息", "Search messages"), localized("搜索当前 Session", "Search this session"))
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text(localized("输入关键词", "Keyword")) },
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
            if (results.isEmpty()) item { Text(localized("没有匹配消息", "No matches"), Modifier.padding(18.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

@Composable
private fun MessageDialog(text: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text(localized("关闭", "Close")) } },
        title = { Text(localized("消息内容", "Message")) },
        text = { Box(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState())) { MarkdownishText(text, selectable = true) } }
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
            QuickJumpButton(Icons.Rounded.KeyboardDoubleArrowUp, localized("顶部", "Top")) { jump(0) }
            QuickJumpButton(Icons.Rounded.KeyboardArrowUp, localized("上一条用户消息", "Previous user message")) { jump(userIndexes.lastOrNull { it < listState.firstVisibleItemIndex }) }
            QuickJumpButton(Icons.Rounded.KeyboardArrowDown, localized("下一条用户消息", "Next user message")) { jump(userIndexes.firstOrNull { it > listState.firstVisibleItemIndex }) }
            QuickJumpButton(Icons.Rounded.KeyboardDoubleArrowDown, localized("底部", "Bottom")) { jump(total - 1) }
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
private fun TuiSessionPlaceholder(
    session: AgentSessionRecord,
    box: BoxRecord?,
    resources: PiLoadedResources?,
    isWorking: Boolean,
    onBoxes: () -> Unit,
    onTools: () -> Unit,
    onRefresh: () -> Unit,
    onOpen: () -> Unit,
    onReload: () -> Unit,
    onStop: () -> Unit
) {
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        ChatTopBar(
            session = session,
            stats = null,
            autoCompact = false,
            isWorking = isWorking,
            onBoxes = onBoxes,
            onTools = onTools,
            onRefresh = onRefresh
        )
        Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically), horizontalAlignment = Alignment.CenterHorizontally) {
            Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer) {
                Icon(Icons.Rounded.Terminal, contentDescription = null, modifier = Modifier.padding(18.dp).size(42.dp))
            }
            Text(localized("TUI Session 在独立终端中运行", "TUI session runs in a separate terminal"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            Text(
                localized("关闭终端只会 detach，不会结束后端 pi TUI 进程。", "Closing the terminal only detaches; it does not stop the backend pi TUI process."),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(session.name, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(listOf("pi TUI", box?.name, session.cwd ?: "/workspace", session.status).filterNotNull().joinToString(" · "), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                    session.launchArgs.takeIf { it.isNotEmpty() }?.let { Text(it.joinToString(" "), fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis) }
                    resources?.let { Text(formatLoadedResourcesSummary(it), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp) }
                    session.error?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }
                }
            }
            Button(onClick = onOpen, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Rounded.OpenInNew, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(localized("打开 TUI 终端", "Open TUI terminal"))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onReload, modifier = Modifier.weight(1f)) { Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Reload") }
                OutlinedButton(onClick = onStop, modifier = Modifier.weight(1f), colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Icon(Icons.Rounded.Stop, contentDescription = null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(localized("停止", "Stop")) }
            }
        }
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
    onRefresh: () -> Unit
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
                Text(listOf(session.provider ?: localized("默认助手", "Default assistant"), session.model).filterNotNull().joinToString(" / "), fontSize = 12.sp, color = colors.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (isWorking) StatusChip("working")
            IconButton(onClick = onTools, modifier = Modifier.size(38.dp)) { Icon(Icons.Rounded.FormatListBulleted, contentDescription = "Tools", modifier = Modifier.size(28.dp), tint = colors.onSurfaceVariant) }
            IconButton(onClick = onRefresh, modifier = Modifier.size(38.dp)) { Icon(Icons.Rounded.Refresh, contentDescription = localized("刷新", "Refresh"), modifier = Modifier.size(28.dp), tint = colors.onSurfaceVariant) }
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
    var elapsedSeconds by remember { mutableStateOf(0.0) }
    LaunchedEffect(Unit) {
        val startTime = SystemClock.elapsedRealtime()
        while (true) {
            elapsedSeconds = (SystemClock.elapsedRealtime() - startTime) / 1000.0
            delay(50)
        }
    }

    val colors = MaterialTheme.colorScheme
    Surface(
        color = colors.surfaceContainerLow.copy(alpha = 0.86f),
        contentColor = colors.onSurface,
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, colors.outlineVariant.copy(alpha = 0.55f)),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            JumpingDotsIndicator(color = colors.onSurface)
            Row(
                Modifier.weight(1f),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    localized("等待回应", "Waiting for response"),
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Text(
                    String.format(Locale.US, "%.1fs", elapsedSeconds),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = colors.onSurfaceVariant.copy(alpha = 0.72f),
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun JumpingDotsIndicator(color: Color, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "processing-dots")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 1500, easing = LinearEasing)),
        label = "processing-dots-progress"
    )
    Row(
        modifier.height(28.dp).padding(bottom = 3.dp),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        repeat(3) { index ->
            val phase = ((progress * 1500f - index * 250f).mod(1500f)) / 1500f
            val wave = (1f - kotlin.math.abs(phase * 2f - 1f)).coerceIn(0f, 1f)
            val easedWave = ((1f - kotlin.math.cos(wave * PI.toFloat())) / 2f).coerceIn(0f, 1f)
            val offsetY = (-16).dp * easedWave
            Box(
                Modifier
                    .offset(y = offsetY)
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(color.copy(alpha = 0.35f + 0.65f * easedWave))
            )
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
            SlashCommandSuggestions(
                text = text,
                commands = state.activeCommands,
                onLoad = { state.activeSessionId?.let { viewModel.loadSessionCommands(it) } },
                onChoose = { command -> onTextChange(applySlashCommand(text, command.name)) }
            )
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
                    if (text.isBlank()) Text(localized("输入消息", "Message"), fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                ComposerIconButton(Icons.Rounded.AutoAwesome, localized("模型", "Model"), selected = model != localized("模型", "Model"), onClick = { onShowModelMenu(true) })
                ComposerIconButton(Icons.Rounded.Search, localized("搜索消息", "Search"), onClick = onSearchClick)
                ComposerIconButton(Icons.Rounded.Lightbulb, "Thinking $thinking", selected = thinking != "off", onClick = { onShowThinkingMenu(true) })
                ComposerIconButton(Icons.Rounded.Tune, "Compact", selected = autoCompact, onClick = { onShowCompactMenu(true) })
                ComposerIconButton(Icons.Rounded.AttachFile, localized("附件", "Attach"), selected = attachments.isNotEmpty(), onClick = onPickFiles)
                ComposerIconButton(Icons.Rounded.Add, localized("更多", "More"), onClick = { onShowCompactMenu(true) })
                if (isWorking && !canSend) {
                    FilledIconButton(onClick = onAbort, modifier = Modifier.size(38.dp), colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.error)) { Icon(Icons.Rounded.Stop, contentDescription = localized("中止", "Abort"), modifier = Modifier.size(22.dp)) }
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
                    ) { Icon(Icons.Rounded.ArrowUpward, contentDescription = localized("发送", "Send"), modifier = Modifier.size(22.dp)) }
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
private fun LoadedResourcesCompact(resources: PiLoadedResources) {
    val summary = formatLoadedResourcesSummary(resources)
    if (summary.isBlank()) return
    Surface(Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surfaceContainerHighest, shape = RoundedCornerShape(16.dp)) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            Icon(Icons.Rounded.Inventory2, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(17.dp))
            Text(summary, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun SlashCommandSuggestions(text: String, commands: List<PiSlashCommand>, onLoad: suspend () -> Unit, onChoose: (PiSlashCommand) -> Unit) {
    val completion = remember(text) { slashCompletionQuery(text) }
    var loading by remember { mutableStateOf(false) }
    LaunchedEffect(completion != null) {
        if (completion != null && commands.isEmpty() && !loading) {
            loading = true
            runCatching { onLoad() }
            loading = false
        }
    }
    if (completion == null) return
    val builtIns = listOf(PiSlashCommand(name = "reload", description = localized("重启当前 pi session 并重新加载资源", "Reload current pi session and resources"), source = "builtin"))
    val visible = remember(commands, completion) { (builtIns + commands).filter { it.name.contains(completion, ignoreCase = true) }.distinctBy { it.name }.take(8) }
    if (visible.isEmpty() && !loading) return
    Surface(Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = RoundedCornerShape(18.dp), border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Column(Modifier.padding(6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Rounded.Bolt, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(17.dp))
                Text(localized("Slash 命令", "Slash commands"), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                if (loading) CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
            }
            visible.forEach { command ->
                ListItem(
                    headlineContent = { Text("/${command.name}", fontFamily = FontFamily.Monospace, fontSize = 13.sp, maxLines = 1) },
                    supportingContent = { Text(command.description ?: command.source, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 12.sp) },
                    leadingContent = { Icon(if (command.source == "builtin") Icons.Rounded.Refresh else Icons.Rounded.Extension, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                    modifier = Modifier.clip(RoundedCornerShape(12.dp)).clickable { onChoose(command) }
                )
            }
            if (commands.isEmpty() && !loading) Text(localized("暂无 extension 命令", "No extension commands"), Modifier.padding(10.dp), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
        }
    }
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

@Composable
private fun modelSelectionSummary(provider: String, model: String, selected: PiModel?): String = when {
    selected != null -> "${selected.providerNameOrNull() ?: provider}/${selected.name ?: selected.id}"
    provider.isBlank() && model.isBlank() -> localized("使用 Box 默认模型", "Use box default model")
    provider.isBlank() -> model
    model.isBlank() -> provider
    else -> "$provider/$model"
}

private fun modelMetaParts(model: PiModel): List<String> = buildList {
    model.providerNameOrNull()?.let { add(it) }
    model.contextWindow?.let { add("${formatTokens(it)} ctx") }
    model.maxTokens?.let { add("${formatTokens(it)} max") }
    if (model.reasoning == true) add("reasoning")
    model.input?.takeIf { it.isNotEmpty() }?.let { add(it.joinToString("/")) }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ModelPickerRow(model: PiModel, selected: Boolean, compact: Boolean = false, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    OutlinedCard(
        modifier = modifier.clickable(onClick = onClick),
        colors = CardDefaults.outlinedCardColors(containerColor = if (selected) colors.primaryContainer.copy(alpha = .58f) else colors.surfaceContainerLow),
        border = androidx.compose.foundation.BorderStroke(1.dp, if (selected) colors.primary.copy(alpha = .62f) else colors.outlineVariant)
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = if (compact) 8.dp else 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Surface(shape = CircleShape, color = if (selected) colors.primary else colors.surfaceContainerHighest, contentColor = if (selected) colors.onPrimary else colors.primary) {
                Icon(if (selected) Icons.Rounded.Check else Icons.Rounded.SmartToy, contentDescription = null, modifier = Modifier.padding(7.dp).size(18.dp))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(model.name ?: model.id, fontWeight = FontWeight.SemiBold, fontSize = if (compact) 13.sp else 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(model.id, color = colors.onSurfaceVariant, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, fontFamily = FontFamily.Monospace)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    modelMetaParts(model).take(if (compact) 4 else 6).forEach { TinyMetaChip(it, selected = selected) }
                }
            }
            if (!compact && selected) Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = colors.primary, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun TinyMetaChip(text: String, selected: Boolean = false) {
    val colors = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = if (selected) colors.primary.copy(alpha = .12f) else colors.surfaceContainerHighest,
        contentColor = if (selected) colors.primary else colors.onSurfaceVariant,
        border = androidx.compose.foundation.BorderStroke(1.dp, if (selected) colors.primary.copy(alpha = .28f) else colors.outlineVariant)
    ) {
        Text(text, Modifier.padding(horizontal = 6.dp, vertical = 2.dp), fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
            SheetHeader(Icons.Rounded.Lightbulb, localized("思考强度", "Thinking"), localized("选择推理强度", "Choose reasoning level"))
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
            SheetHeader(Icons.Rounded.AutoAwesome, localized("模型", "Model"), localized("切换 provider / model", "Switch provider / model"))
            OutlinedTextField(search, { search = it }, leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) }, label = { Text(localized("搜索 provider / model", "Search provider / model")) }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp))
            if (state.modelLoading) LinearProgressIndicator(Modifier.fillMaxWidth().padding(18.dp))
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 420.dp), contentPadding = PaddingValues(vertical = 8.dp)) {
                items(models, key = { "${it.providerNameOrNull()}/${it.id}" }) { model ->
                    val selected = state.activeSession?.model == model.id && state.activeSession?.provider == model.providerNameOrNull()
                    ModelPickerRow(
                        model = model,
                        selected = selected,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                        onClick = { onShowModelMenu(false); viewModel.setSessionModel(model) }
                    )
                }
                if (!state.modelLoading && models.isEmpty()) item { Text(localized("没有可显示的模型", "No models"), Modifier.padding(18.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
    }
    if (showCompactMenu) {
        ModalBottomSheet(onDismissRequest = { onShowCompactMenu(false) }) {
            SheetHeader(Icons.Rounded.Tune, localized("Compact / 更多", "Compact / More"), localized("上下文与附加操作", "Context and actions"))
            ListItem(
                headlineContent = { Text(localized("自动 Compact", "Auto compact")) },
                supportingContent = { Text(if (autoCompact) localized("已开启", "Enabled") else localized("点击开启", "Tap to enable")) },
                leadingContent = { Icon(if (autoCompact) Icons.Rounded.ToggleOn else Icons.Rounded.ToggleOff, contentDescription = null, tint = if (autoCompact) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant) },
                modifier = Modifier.clickable { onShowCompactMenu(false); viewModel.setAutoCompaction(true) }
            )
            ListItem(
                headlineContent = { Text(localized("手动 Compact", "Manual compact")) },
                supportingContent = { Text(localized("仅手动触发", "Only when triggered")) },
                leadingContent = { Icon(if (!autoCompact) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked, contentDescription = null) },
                modifier = Modifier.clickable { onShowCompactMenu(false); viewModel.setAutoCompaction(false) }
            )
            ListItem(
                headlineContent = { Text(localized("立即执行 Compact", "Compact now")) },
                supportingContent = { Text(if (text.isBlank()) localized("压缩当前上下文", "Compact current context") else localized("使用输入内容作为要求", "Use input as instructions")) },
                leadingContent = { Icon(Icons.Rounded.Archive, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                modifier = Modifier.clickable { onShowCompactMenu(false); viewModel.compact(text.trim().ifBlank { null }); onTextChange("") }
            )
            Spacer(Modifier.height(18.dp))
        }
    }
    if (showSendModeMenu) {
        ModalBottomSheet(onDismissRequest = { onShowSendModeMenu(false) }) {
            SheetHeader(Icons.Rounded.Send, localized("发送方式", "Send mode"), localized("选择队列策略", "Choose queue behavior"))
            ListItem(
                headlineContent = { Text(localized("立即发送", "Send now")) },
                supportingContent = { Text(localized("中断当前 turn", "Abort current turn")) },
                leadingContent = { Icon(Icons.Rounded.FlashOn, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                modifier = Modifier.clickable { onShowSendModeMenu(false); onSend(null) }
            )
            ListItem(
                headlineContent = { Text(localized("Steer 队列", "Steer queue")) },
                supportingContent = { Text(localized("注入当前 turn", "Inject into current turn")) },
                leadingContent = { Icon(Icons.Rounded.AltRoute, contentDescription = null) },
                modifier = Modifier.clickable { onShowSendModeMenu(false); onSend("steer") }
            )
            ListItem(
                headlineContent = { Text(localized("Follow-up 队列", "Follow-up queue")) },
                supportingContent = { Text(localized("完成后发送", "Send after completion")) },
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

@Composable
private fun thinkingDescription(level: String): String = when (level) {
    "off" -> localized("关闭扩展思考", "Disable extended thinking")
    "minimal" -> localized("最少推理", "Minimal reasoning")
    "low" -> localized("低强度思考", "Low reasoning")
    "medium" -> localized("默认平衡", "Balanced")
    "high" -> localized("更强推理", "Stronger reasoning")
    "xhigh" -> localized("超高推理", "Extra high reasoning")
    else -> ""
}

@Composable
private fun ModelDropdown(expanded: Boolean, onDismiss: () -> Unit, state: AppUiState, viewModel: AppViewModel) {
    var search by remember { mutableStateOf("") }
    val models = remember(state.sessionModels, search) { state.sessionModels.filter { "${it.providerNameOrNull().orEmpty()} ${it.id} ${it.name.orEmpty()}".contains(search, ignoreCase = true) }.take(120) }
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss, modifier = Modifier.heightIn(max = 520.dp).widthIn(min = 320.dp)) {
        DropdownMenuItem(text = { OutlinedTextField(search, { search = it }, label = { Text(localized("搜索", "Search")) }, singleLine = true) }, onClick = {})
        if (state.modelLoading) DropdownMenuItem(text = { LinearProgressIndicator(Modifier.fillMaxWidth()) }, onClick = {})
        models.forEach { model -> DropdownMenuItem(text = { Column { Text(model.name ?: model.id); Text("${model.providerNameOrNull() ?: "unknown"} · ${model.id}${model.contextWindow?.let { " · ${formatTokens(it)} ctx" } ?: ""}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) } }, onClick = { onDismiss(); viewModel.setSessionModel(model) }) }
        if (!state.modelLoading && models.isEmpty()) DropdownMenuItem(text = { Text(localized("没有可显示的模型", "No models")) }, onClick = {})
    }
}

private fun ChatMessage?.isAgentOutputInProgress(): Boolean = this != null && when (role) {
    "assistant" -> text.isNotBlank() || thinking?.isNotBlank() == true
    "tool" -> toolStatus in setOf("pending", "running") || toolArgs != null || toolResult?.isNotBlank() == true
    else -> false
}

@Composable
private fun MessageBubble(message: ChatMessage, autoOpenProgress: Boolean, isLatestMessage: Boolean, streaming: Boolean, onFork: () -> Unit, onShowDialog: () -> Unit, onExpand: (String) -> Unit) {
    when (message.role) {
        "user" -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Surface(color = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary, shape = RoundedCornerShape(22.dp, 22.dp, 6.dp, 22.dp), modifier = Modifier.widthIn(max = 330.dp)) {
                Column(Modifier.padding(12.dp)) {
                    if (message.text.isNotBlank()) SelectionContainer { Text(message.text) }
                    AttachmentGallery(message.attachments)
                    TruncationNotice(message.transport, onExpand)
                }
            }
        }
        "assistant" -> Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            val hasThinking = message.thinking?.isNotBlank() == true
            message.thinking?.takeIf { it.isNotBlank() }?.let { ExpandableBlock(localized("思考过程", "Thinking"), it, autoOpen = autoOpenProgress, autoCollapse = !isLatestMessage, stateKey = message.id, lightweight = streaming) }
            if (message.text.isNotBlank()) MarkdownishText(message.text, selectable = true, lightweight = streaming) else if (!hasThinking) Spacer(Modifier.height(1.dp))
            AttachmentGallery(message.attachments)
            TruncationNotice(message.transport, onExpand)
            if (message.text.isNotBlank()) AssistantActions(message.text, onFork, onShowDialog)
        }
        "tool" -> ToolMessageCard(message, autoOpen = autoOpenProgress, autoCollapse = !isLatestMessage, onExpand = onExpand)
        else -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SelectionContainer { AssistChip(onClick = {}, leadingIcon = { Icon(Icons.Rounded.Warning, contentDescription = null) }, label = { Text(message.text) }) }
            TruncationNotice(message.transport, onExpand)
        }
    }
}

@Composable
private fun TruncationNotice(meta: ChatMessageTransportMeta?, onExpand: (String) -> Unit, modifier: Modifier = Modifier) {
    if (meta?.truncated != true) return
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = .34f),
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = .24f))
    ) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Rounded.ContentCut, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
            Text(
                localized("已截断长消息", "Long message truncated") + (meta.omittedChars?.takeIf { it > 0 }?.let { localized("，省略 ${formatCount(it)} 字符", ", ${formatCount(it)} chars omitted") } ?: ""),
                fontSize = 12.sp,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = { onExpand(meta.messageId) }, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)) {
                Icon(Icons.Rounded.OpenInFull, contentDescription = null, modifier = Modifier.size(14.dp))
                Text(localized("展开完整消息", "Expand"), fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun AssistantActions(text: String, onFork: () -> Unit, onShowDialog: () -> Unit) {
    val clipboard = LocalClipboardManager.current
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { clipboard.setText(AnnotatedString(text)) }, modifier = Modifier.size(34.dp)) { Icon(Icons.Rounded.ContentCopy, contentDescription = localized("复制", "Copy"), tint = color) }
        IconButton(onClick = onFork, modifier = Modifier.size(34.dp)) { Icon(Icons.Rounded.CallSplit, contentDescription = "Fork", tint = color) }
        IconButton(onClick = onShowDialog, modifier = Modifier.size(34.dp)) { Icon(Icons.Rounded.OpenInFull, contentDescription = localized("查看完整内容", "Open full text"), tint = color) }
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
private fun MarkdownishText(text: String, selectable: Boolean = false, lightweight: Boolean = false) {
    val blocks = remember(text, lightweight) { if (lightweight) listOf(MdBlock.Text(text)) else parseMarkdownBlocks(text) }
    val content: @Composable () -> Unit = {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            blocks.forEach { block ->
                when (block) {
                    is MdBlock.Code -> CodeBlock(block.language.ifBlank { "code" }, block.code)
                    is MdBlock.Text -> MarkdownTextBlock(block.text)
                }
            }
        }
    }
    if (selectable) SelectionContainer { content() } else content()
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
    val dark = colors.isDarkLike()
    val inlineCodeBackground = if (dark) Color(0xFF312D36) else Color(0xFFE8EAF6)
    val inlineCodeColor = if (dark) Color(0xFFEADDFF) else Color(0xFF5B3DB5)
    val paragraphs = remember(text) { text.split(MarkdownParagraphBreakRegex).filter { it.isNotBlank() } }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        paragraphs.forEach { para ->
            val lines = para.lines()
            val heading = lines.firstOrNull()?.let { MarkdownHeadingRegex.find(it) }
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
                                Text(
                                    inlineMarkdown(line.trimStart().drop(2).trim(), colors.onSurface, inlineCodeBackground, inlineCodeColor),
                                    style = TextStyle(fontSize = 16.sp, lineHeight = 24.sp),
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
                else -> Text(
                    inlineMarkdown(para, colors.onSurface, inlineCodeBackground, inlineCodeColor),
                    style = TextStyle(fontSize = 16.sp, lineHeight = 24.sp)
                )
            }
        }
    }
}

private fun inlineMarkdown(text: String, baseColor: Color, inlineCodeBackground: Color, inlineCodeColor: Color): AnnotatedString = buildAnnotatedString {
    var last = 0
    fun appendPlain(until: Int) {
        if (until > last) withStyle(SpanStyle(color = baseColor)) { append(text.substring(last, until)) }
    }
    for (m in InlineMarkdownRegex.findAll(text)) {
        appendPlain(m.range.first)
        val token = m.value
        when {
            token.startsWith("**") -> withStyle(SpanStyle(color = baseColor, fontWeight = FontWeight.Black)) { append(token.removePrefix("**").removeSuffix("**")) }
            token.startsWith("`") -> withStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = inlineCodeBackground, color = inlineCodeColor, fontWeight = FontWeight.SemiBold)) { append(token.removePrefix("`").removeSuffix("`")) }
        }
        last = m.range.last + 1
    }
    appendPlain(text.length)
}

private enum class ToolKind { Read, Edit, Write, Bash, Ls, Grep, Find, Unknown }

@Composable
private fun ToolMessageCard(message: ChatMessage, autoOpen: Boolean, autoCollapse: Boolean, onExpand: (String) -> Unit) {
    val status = message.toolStatus ?: "pending"
    val kind = toolKindForName(message.toolName)
    val accent = toolKindAccent(kind)
    val border = when (status) {
        "running" -> accent.copy(alpha = .50f)
        "error" -> MaterialTheme.colorScheme.error.copy(alpha = .55f)
        else -> MaterialTheme.colorScheme.outlineVariant
    }
    var open by rememberSaveable(message.id) { mutableStateOf(autoOpen) }
    var openedByAuto by rememberSaveable(message.id) { mutableStateOf(autoOpen) }
    LaunchedEffect(autoOpen, autoCollapse, message.id) {
        when {
            autoOpen -> { open = true; openedByAuto = true }
            autoCollapse && openedByAuto -> { open = false; openedByAuto = false }
        }
    }
    OutlinedCard(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        border = androidx.compose.foundation.BorderStroke(1.dp, border)
    ) {
        Column(Modifier.fillMaxWidth()) {
            Row(
                Modifier.fillMaxWidth().clickable { open = !open; openedByAuto = false }.padding(horizontal = 12.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(9.dp)
            ) {
                ToolKindIcon(kind, status)
                Text(
                    toolLabel(kind, message.toolName),
                    fontWeight = FontWeight.Black,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.widthIn(max = 92.dp)
                )
                ToolOverview(message, modifier = Modifier.weight(1f))
                ToolStatusBadge(status)
                Icon(if (open) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
            }
            if (open) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .72f))
                ToolPreview(message)
            }
            TruncationNotice(message.transport, onExpand, Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp))
        }
    }
}

@Composable
private fun ToolKindIcon(kind: ToolKind, status: String) {
    val accent = toolKindAccent(kind)
    Surface(shape = RoundedCornerShape(9.dp), color = accent.copy(alpha = .13f), contentColor = accent, modifier = Modifier.size(26.dp)) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Icon(toolIcon(kind), contentDescription = null, modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
private fun toolKindAccent(kind: ToolKind): Color = when (kind) {
    ToolKind.Read, ToolKind.Edit -> MaterialTheme.colorScheme.primary
    ToolKind.Write -> MaterialTheme.colorScheme.tertiary
    ToolKind.Bash -> MaterialTheme.colorScheme.secondary
    ToolKind.Ls, ToolKind.Grep, ToolKind.Find -> MaterialTheme.colorScheme.primary
    ToolKind.Unknown -> MaterialTheme.colorScheme.onSurfaceVariant
}

private fun toolIcon(kind: ToolKind): ImageVector = when (kind) {
    ToolKind.Read -> Icons.Rounded.Visibility
    ToolKind.Edit -> Icons.Rounded.Edit
    ToolKind.Write -> Icons.Rounded.NoteAdd
    ToolKind.Bash -> Icons.Rounded.Terminal
    ToolKind.Ls -> Icons.Rounded.Folder
    ToolKind.Grep -> Icons.Rounded.Search
    ToolKind.Find -> Icons.Rounded.FindInPage
    ToolKind.Unknown -> Icons.Rounded.Build
}

@Composable
private fun toolLabel(kind: ToolKind, name: String?): String = when (kind) {
    ToolKind.Read -> localized("读取文件", "Read file")
    ToolKind.Edit -> localized("编辑文件", "Edit file")
    ToolKind.Write -> localized("写入文件", "Write file")
    ToolKind.Bash -> localized("执行命令", "Run command")
    ToolKind.Ls -> localized("列出目录", "List directory")
    ToolKind.Grep -> localized("搜索文本", "Search text")
    ToolKind.Find -> localized("查找文件", "Find files")
    ToolKind.Unknown -> name?.takeIf { it.isNotBlank() } ?: "tool"
}

private fun toolKindForName(name: String?): ToolKind {
    val raw = name.orEmpty().trim().lowercase()
    val last = raw.split(Regex("[./:]")).filter { it.isNotBlank() }.lastOrNull() ?: raw
    val keys = listOf(raw, last).map { it.replace(Regex("[\\s_-]"), "") }
    fun has(vararg values: String): Boolean = keys.any { key -> values.any { it == key } }
    return when {
        has("read", "readfile", "fileread", "view") -> ToolKind.Read
        has("edit", "editfile", "fileedit", "replace", "strreplace") -> ToolKind.Edit
        has("write", "writefile", "filewrite", "create", "createfile") -> ToolKind.Write
        has("bash", "shell", "terminal", "runcommand", "exec", "execute") -> ToolKind.Bash
        has("ls", "list", "listdir", "listdirectory") -> ToolKind.Ls
        has("grep", "rg", "ripgrep", "search", "searchtext") -> ToolKind.Grep
        has("find", "findfile", "findfiles", "glob") -> ToolKind.Find
        else -> ToolKind.Unknown
    }
}

@Composable
private fun ToolStatusBadge(status: String) {
    val color = when (status) {
        "running" -> MaterialTheme.colorScheme.primary
        "error" -> MaterialTheme.colorScheme.error
        "done" -> Color(0xFF16A34A)
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Box(Modifier.size(28.dp), contentAlignment = Alignment.Center) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(color))
    }
}

@Composable
private fun ToolOverview(message: ChatMessage, modifier: Modifier = Modifier) {
    val kind = toolKindForName(message.toolName)
    val args = message.toolArgs.toolObjectOrNull()
    val result = message.toolResult.orEmpty()
    val path = args.toolPath()
    Row(modifier.horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        when (kind) {
            ToolKind.Edit -> {
                val edits = editDiffInputs(args, path)
                val stats = editChangeStats(edits)
                val paths = uniqueStrings(listOf(path) + edits.map { it.path })
                if (paths.isNotEmpty()) ToolMuted("${paths.size} files")
                if (edits.isNotEmpty()) ToolChangeStats(stats.added, stats.removed) else ToolMuted(previewText(result) ?: localized("等待 diff", "Waiting for diff"))
                paths.firstOrNull()?.let { ToolFileRef(it) }
                if (paths.size > 1) ToolMuted("+${paths.size - 1}")
                if (paths.isEmpty() && edits.isEmpty() && result.isBlank()) ToolMuted(localized("点击查看详情", "Details"))
            }
            ToolKind.Read -> {
                if (path != null) ToolFileRef(path) else ToolMuted("file")
                readLineSummary(args, result)?.takeIf { it.isNotBlank() }?.let { ToolMuted(it) }
            }
            ToolKind.Write -> {
                val content = args.firstString("content", "newText", "new_text", "replacement", "text", "value", "data")
                val added = content?.let { splitTextLines(it).size } ?: lineCount(result)
                if (added > 0) ToolChangeStats(added, 0)
                if (path != null) ToolFileRef(path) else ToolMuted(previewText(result) ?: "file")
            }
            ToolKind.Bash -> {
                val command = bashCommand(message.toolArgs)
                if (command != null) ToolInlineCode("$ ${previewText(command, 180)}") else ToolMuted(previewText(result) ?: "shell")
            }
            ToolKind.Ls -> if (path != null) ToolFileRef(path) else ToolMuted(localized("当前目录", "Current directory"))
            ToolKind.Grep, ToolKind.Find -> {
                args.firstString("pattern", "query", "regex", "name", "value")?.let { ToolInlineCode(previewText(it, 80) ?: it) }
                path?.let { ToolFileRef(it) }
                if (path == null && args.firstString("pattern", "query", "regex", "name", "value") == null) ToolMuted(previewText(message.toolArgs?.let { pretty(it) } ?: result) ?: localized("点击查看详情", "Details"))
            }
            ToolKind.Unknown -> ToolMuted(compactText(path?.let { fileNameFromPath(it) }, previewText(message.toolArgs?.let { pretty(it) }), previewText(result)))
        }
    }
}

@Composable
private fun ToolMuted(text: String) {
    Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
}

@Composable
private fun ToolInlineCode(text: String, language: String = "bash") {
    val dark = MaterialTheme.colorScheme.isDarkLike()
    val highlighted = remember(text, language, dark) { highlightCode(text, language, dark) }
    Surface(shape = RoundedCornerShape(7.dp), color = MaterialTheme.colorScheme.primary.copy(alpha = .10f), contentColor = MaterialTheme.colorScheme.onPrimaryContainer) {
        Text(highlighted, fontFamily = FontFamily.Monospace, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 260.dp).padding(horizontal = 6.dp, vertical = 3.dp))
    }
}

@Composable
private fun ToolChangeStats(added: Int, removed: Int) {
    val add = added.coerceAtLeast(0)
    val del = removed.coerceAtLeast(0)
    if (add == 0 && del == 0) { ToolMuted(localized("无行变更", "No changes")); return }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        if (add > 0) Text("+$add", color = Color(0xFF7EE787), fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Black, fontSize = 12.sp, maxLines = 1)
        if (del > 0) Text("-$del", color = Color(0xFFFF7B72), fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Black, fontSize = 12.sp, maxLines = 1)
    }
}

private data class ToolFileIconData(val icon: ImageVector, val description: String)

@Composable
private fun ToolFileRef(path: String) {
    val name = fileNameFromPath(path)
    Row(Modifier.widthIn(max = 190.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        ToolFileIcon(fileIconForPath(path))
        Text(name, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .88f), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun ToolPathPill(path: String?, fallback: String = "file") {
    val label = path?.let { fileNameFromPath(it) } ?: fallback
    Surface(shape = RoundedCornerShape(9.dp), color = MaterialTheme.colorScheme.primary.copy(alpha = .10f), contentColor = MaterialTheme.colorScheme.onSurface) {
        Row(Modifier.widthIn(max = 220.dp).padding(horizontal = 7.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            ToolFileIcon(fileIconForPath(path ?: label))
            Text(label, fontFamily = FontFamily.Monospace, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun ToolFileIcon(data: ToolFileIconData) {
    Surface(shape = RoundedCornerShape(7.dp), color = MaterialTheme.colorScheme.surfaceContainerHighest, contentColor = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(22.dp)) {
        Box(contentAlignment = Alignment.Center) {
            Icon(data.icon, contentDescription = data.description, modifier = Modifier.size(15.dp))
        }
    }
}

private fun fileIconForPath(path: String): ToolFileIconData {
    val name = fileNameFromPath(path).lowercase()
    specialFileIcon(name)?.let { return it }
    return when (fileExtension(name)) {
        "js", "mjs", "cjs", "jsx" -> fileIcon(Icons.Rounded.Javascript, "JavaScript")
        "html", "htm" -> fileIcon(Icons.Rounded.Html, "HTML")
        "css", "scss", "sass", "less" -> fileIcon(Icons.Rounded.Css, "CSS")
        "php" -> fileIcon(Icons.Rounded.Php, "PHP")
        "json", "jsonc", "yml", "yaml", "toml", "xml", "gql", "graphql", "proto" -> fileIcon(Icons.Rounded.DataObject, "Data file")
        "sh", "bash", "zsh", "fish", "ps1", "bat", "cmd" -> fileIcon(Icons.Rounded.Terminal, "Shell")
        "md", "mdx", "markdown", "txt", "log" -> fileIcon(Icons.Rounded.Article, "Document")
        "csv", "tsv", "xls", "xlsx" -> fileIcon(Icons.Rounded.TableChart, "Table")
        "sql", "sqlite", "sqlite3", "db" -> fileIcon(Icons.Rounded.Storage, "Database")
        "zip", "tar", "gz", "tgz", "rar", "7z" -> fileIcon(Icons.Rounded.FolderZip, "Archive")
        "apk" -> fileIcon(Icons.Rounded.Android, "Android package")
        "png", "jpg", "jpeg", "gif", "webp", "bmp", "svg" -> fileIcon(Icons.Rounded.Image, "Image")
        "pdf" -> fileIcon(Icons.Rounded.PictureAsPdf, "PDF")
        "mp4", "mov", "mkv", "webm" -> fileIcon(Icons.Rounded.Movie, "Video")
        "mp3", "wav", "m4a", "flac", "ogg" -> fileIcon(Icons.Rounded.AudioFile, "Audio")
        "ts", "mts", "cts", "tsx", "py", "pyw", "ipynb", "kt", "kts", "java", "go", "rs", "rb", "cs", "swift", "dart", "scala", "groovy", "c", "h", "cc", "cpp", "cxx", "hpp", "hh", "vue", "svelte", "astro", "lua", "r", "ex", "exs" -> fileIcon(Icons.Rounded.Code, "Source code")
        else -> fileIcon(Icons.Rounded.Description, "File")
    }
}

private fun specialFileIcon(name: String): ToolFileIconData? = when {
    name == "dockerfile" || name.endsWith(".dockerfile") || name in setOf("docker-compose.yml", "docker-compose.yaml", "compose.yml", "compose.yaml") -> fileIcon(Icons.Rounded.DataObject, "Docker")
    name == "makefile" || name == "cmakelists.txt" || name.endsWith(".gradle") || name.endsWith(".gradle.kts") || name == "gradlew" -> fileIcon(Icons.Rounded.Build, "Build file")
    name.startsWith(".env") -> fileIcon(Icons.Rounded.Key, "Environment")
    name.startsWith(".git") -> fileIcon(Icons.Rounded.Commit, "Git")
    name in setOf("package.json", "package-lock.json", "pnpm-lock.yaml", "pnpm-lock.yml", "yarn.lock") -> fileIcon(Icons.Rounded.DataObject, "Node package")
    name.startsWith("tsconfig") && name.endsWith(".json") -> fileIcon(Icons.Rounded.DataObject, "TypeScript config")
    name in setOf("go.mod", "go.sum", "cargo.toml", "cargo.lock", "rust-toolchain", "requirements.txt", "pyproject.toml", "poetry.lock", "pdm.lock", "pom.xml") -> fileIcon(Icons.Rounded.Code, "Project file")
    else -> null
}

private fun fileIcon(icon: ImageVector, description: String): ToolFileIconData = ToolFileIconData(icon, description)

@Composable
private fun ToolPreview(message: ChatMessage) {
    val kind = toolKindForName(message.toolName)
    val args = message.toolArgs.toolObjectOrNull()
    val result = message.toolResult.orEmpty()
    val path = args.toolPath()
    Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
        when (kind) {
            ToolKind.Write -> {
                val content = args.firstString("content", "newText", "new_text", "replacement", "text", "value", "data")
                ToolPreviewHeader(localized("写入 diff", "Write diff"), path)
                if (!content.isNullOrBlank()) DiffBlock("Unified diff", buildWriteDiff(content, path))
                else if (result.isNotBlank()) ToolCodeBlock(localized("输出预览", "Output"), result)
                else EmptyToolPreview(localized("没有可显示的写入内容", "No write content"))
                if (result.isNotBlank() && content != null) SmallToolDetails(localized("工具结果", "Tool result")) { ToolCodeBlock(localized("输出预览", "Output"), result, maxLines = 10) }
            }
            ToolKind.Edit -> {
                val edits = editDiffInputs(args, path)
                val diffs = buildEditDiffs(args, path)
                ToolPreviewHeader(localized("编辑 diff", "Edit diff"), path ?: edits.firstOrNull()?.path, if (edits.size > 1) "${edits.size} blocks" else null)
                if (diffs.isNotEmpty()) DiffBlock("Unified diff", diffs)
                else if (result.isNotBlank()) ToolCodeBlock(localized("输出预览", "Output"), result)
                else EmptyToolPreview(localized("没有可显示的编辑内容", "No edit content"))
                if (result.isNotBlank() && diffs.isNotEmpty()) SmallToolDetails(localized("工具结果", "Tool result")) { ToolCodeBlock(localized("输出预览", "Output"), result, maxLines = 10) }
                else if (message.toolArgs != null && diffs.isEmpty()) SmallToolDetails(localized("参数", "Args")) { ToolCodeBlock("json", pretty(message.toolArgs), maxLines = 10) }
            }
            ToolKind.Read -> {
                val display = result.ifBlank { message.toolArgs?.let { pretty(it) }.orEmpty() }
                ToolPreviewHeader(localized("读取", "Read"), path, readLineSummary(args, result))
                if (display.isNotBlank()) { ToolCodeBlock(languageForPath(path).ifBlank { "text" }, display); ToolMetaNotice(message.toolResultMeta, result.ifBlank { display }) }
                else EmptyToolPreview(localized("没有可显示的读取结果", "No read output"))
            }
            ToolKind.Bash -> {
                val command = bashCommand(message.toolArgs)
                if (!command.isNullOrBlank()) {
                    ToolPreviewHeader(localized("执行命令", "Command"), null)
                    ToolCodeBlock("$ command", command, maxLines = 24)
                }
                if (result.isNotBlank()) {
                    ToolPreviewHeader(localized("输出预览", "Output"), null)
                    ToolCodeBlock("terminal", result)
                    ToolMetaNotice(message.toolResultMeta, result)
                }
                if (command.isNullOrBlank() && result.isBlank()) EmptyToolPreview(localized("暂无输出", "No output yet"))
            }
            ToolKind.Ls, ToolKind.Grep, ToolKind.Find, ToolKind.Unknown -> {
                if (message.toolArgs != null) {
                    ToolPreviewHeader(localized("参数", "Args"), path)
                    ToolCodeBlock("json", pretty(message.toolArgs), maxLines = 10)
                }
                if (result.isNotBlank()) {
                    ToolPreviewHeader(localized("输出预览", "Output"), path)
                    ToolCodeBlock(localized("输出", "Output"), result)
                    ToolMetaNotice(message.toolResultMeta, result)
                }
                if (message.toolArgs == null && result.isBlank()) EmptyToolPreview(localized("没有可显示的工具内容", "No tool content"))
            }
        }
    }
}

@Composable
private fun ToolPreviewHeader(title: String, path: String?, extra: String? = null) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Black, fontSize = 12.sp, maxLines = 1)
        path?.let { ToolPathPill(it) }
        extra?.takeIf { it.isNotBlank() }?.let { ToolMuted(it) }
    }
}

@Composable
private fun ToolCodeBlock(title: String, text: String, maxLines: Int = 18) {
    CodeBlock(title, text, maxCollapsedLines = maxLines, highlight = false)
}

@Composable
private fun ToolMetaNotice(meta: ToolResultMeta?, text: String) {
    val enriched = remember(meta, text) { enrichToolMeta(meta, text) } ?: return
    val english = LocalAppStrings.current === EnStrings
    val parts = buildList {
        enriched.label?.takeIf { it.isNotBlank() }?.let { add(it) }
        val shown = enriched.shownLines
        val total = enriched.totalLines
        val omitted = enriched.omittedLines
        if (shown != null && total != null && total != shown) add(if (english) "shown $shown/$total lines" else "显示 $shown/$total 行")
        else if (total != null) add(if (english) "$total lines" else "共 $total 行")
        if (omitted != null) add(if (english) "$omitted omitted" else "省略 $omitted 行")
        val shownBytes = enriched.shownBytes
        val totalBytes = enriched.totalBytes
        if (shownBytes != null && totalBytes != null && totalBytes != shownBytes) add("${formatBytes(shownBytes)}/${formatBytes(totalBytes)}")
        else if (totalBytes != null) add(formatBytes(totalBytes))
    }
    if (parts.isEmpty() && enriched.truncated != true) return
    val title = if (english) "Tool output truncated" else "工具输出已截断"
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = .35f), contentColor = MaterialTheme.colorScheme.onPrimaryContainer) {
        Text(
            listOfNotNull(if (enriched.truncated == true) title else null, parts.joinToString(" · ").takeIf { it.isNotBlank() }).joinToString(" · "),
            fontSize = 12.sp,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)
        )
    }
}

private fun enrichToolMeta(meta: ToolResultMeta?, text: String): ToolResultMeta? {
    val lines = lineCount(text).toLong()
    var next = meta ?: return null
    if (lines > 0 && next.shownLines == null) next = next.copy(shownLines = lines)
    if (lines > 0 && next.totalLines == null && next.truncated != true) next = next.copy(totalLines = lines)
    if (next.omittedLines != null && next.shownLines != null && next.totalLines == null) next = next.copy(totalLines = next.shownLines + next.omittedLines)
    if (next.omittedLines == null && next.totalLines != null && next.shownLines != null && next.totalLines > next.shownLines) next = next.copy(omittedLines = next.totalLines - next.shownLines)
    return next
}

@Composable
private fun EmptyToolPreview(text: String) {
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = .55f)) {
        Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, modifier = Modifier.padding(12.dp))
    }
}

@Composable
private fun SmallToolDetails(title: String, content: @Composable () -> Unit) {
    var open by rememberSaveable { mutableStateOf(false) }
    OutlinedCard(Modifier.fillMaxWidth(), border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(Modifier.fillMaxWidth().clickable { open = !open }.padding(horizontal = 10.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(if (open) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(title, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
            }
            if (open) Box(Modifier.padding(start = 8.dp, end = 8.dp, bottom = 8.dp)) { content() }
        }
    }
}

private data class EditDiffInput(val oldText: String, val newText: String, val path: String?)
private data class ChangeStats(val added: Int, val removed: Int)
private data class DiffLine(val type: DiffLineType, val text: String)
private enum class DiffLineType { Add, Del, Meta, Ctx }

@Composable
private fun DiffBlock(title: String, lines: List<DiffLine>, maxLines: Int = 240) {
    val clipboard = LocalClipboardManager.current
    val bg = MaterialTheme.colorScheme.surfaceContainerHighest
    val headerBg = MaterialTheme.colorScheme.surfaceContainerHigh
    val clipped = lines.size > maxLines
    var expanded by rememberSaveable { mutableStateOf(false) }
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
                    Text(if (expanded) localized("收起", "Collapse") else localized("展开全部", "Expand"), fontSize = 12.sp)
                }
            }
            TextButton(onClick = { clipboard.setText(AnnotatedString(text)) }, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)) { Icon(Icons.Rounded.ContentCopy, contentDescription = null, modifier = Modifier.size(13.dp)); Text(localized("复制", "Copy"), fontSize = 12.sp) }
        }
        Column(Modifier.fillMaxWidth().background(bg)) {
            visible.forEach { line -> DiffLineRow(line) }
            if (clipped && !expanded) DiffLineRow(DiffLine(DiffLineType.Meta, localized("… 已隐藏 ${lines.size - maxLines} 行，共 ${lines.size} 行", "… ${lines.size - maxLines} hidden lines, ${lines.size} total")))
        }
    }
}

@Composable
private fun DiffLineRow(line: DiffLine) {
    val colors = MaterialTheme.colorScheme
    val add = Color(0xFF16A34A)
    val del = colors.error
    val bg = when (line.type) {
        DiffLineType.Add -> add.copy(alpha = .12f)
        DiffLineType.Del -> del.copy(alpha = .12f)
        DiffLineType.Meta -> colors.primaryContainer.copy(alpha = .36f)
        DiffLineType.Ctx -> Color.Transparent
    }
    val stripe = when (line.type) {
        DiffLineType.Add -> add
        DiffLineType.Del -> del
        else -> Color.Transparent
    }
    val signColor = when (line.type) {
        DiffLineType.Add -> add
        DiffLineType.Del -> del
        DiffLineType.Meta -> colors.primary
        DiffLineType.Ctx -> colors.onSurfaceVariant
    }
    val textColor = when (line.type) {
        DiffLineType.Meta -> colors.primary
        else -> colors.onSurface
    }
    Row(Modifier.fillMaxWidth().background(bg), verticalAlignment = Alignment.Top) {
        Box(Modifier.width(3.dp).height(20.dp).background(stripe))
        Text(
            when (line.type) { DiffLineType.Add -> "+"; DiffLineType.Del -> "-"; DiffLineType.Meta -> ""; DiffLineType.Ctx -> " " },
            color = signColor,
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier.width(28.dp).padding(top = 2.dp),
            maxLines = 1
        )
        Text(line.text.ifEmpty { " " }, color = textColor, fontFamily = FontFamily.Monospace, fontSize = 12.sp, lineHeight = 18.sp, modifier = Modifier.weight(1f).padding(vertical = 2.dp, horizontal = 4.dp))
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
    val edits = editDiffInputs(args, filePath)
    return edits.flatMapIndexed { index, edit ->
        val lines = if (edit.oldText.isNotBlank()) buildUnifiedDiff(edit.oldText, edit.newText, edit.path ?: filePath) else buildWriteDiff(edit.newText, edit.path ?: filePath)
        if (index == 0) lines else listOf(DiffLine(DiffLineType.Meta, "")) + lines
    }
}

private fun editDiffInputs(args: JsonObject?, fallbackPath: String?): List<EditDiffInput> {
    if (args == null) return emptyList()
    val out = mutableListOf<EditDiffInput>()
    fun push(obj: JsonObject?) {
        if (obj == null) return
        val oldText = obj.firstString("oldText", "old_text", "old", "original", "before").orEmpty()
        val newText = obj.firstString("newText", "new_text", "replacement", "replace", "new", "after", "content", "text", "value").orEmpty()
        val path = obj.toolPath() ?: fallbackPath
        if (oldText.isNotBlank() || newText.isNotBlank()) out += EditDiffInput(oldText, newText, path)
    }
    listOf("edits", "changes", "replacements").forEach { key ->
        (args[key] as? JsonArray)?.forEach { push(it.toolObjectOrNull()) }
    }
    push(args)
    return out.distinctBy { Triple(it.oldText, it.newText, it.path) }
}

private fun editChangeStats(edits: List<EditDiffInput>): ChangeStats = edits.fold(ChangeStats(0, 0)) { acc, edit ->
    val changed = changedLineCounts(edit.oldText, edit.newText)
    ChangeStats(acc.added + changed.added, acc.removed + changed.removed)
}

private fun changedLineCounts(oldText: String, newText: String): ChangeStats {
    if (oldText.isBlank() && newText.isBlank()) return ChangeStats(0, 0)
    val oldLines = splitTextLines(oldText)
    val newLines = splitTextLines(newText)
    if (oldLines.isEmpty()) return ChangeStats(newLines.size, 0)
    if (newLines.isEmpty()) return ChangeStats(0, oldLines.size)
    if (oldLines.size * newLines.size > 90_000) return ChangeStats(newLines.size, oldLines.size)
    val dp = Array(oldLines.size + 1) { IntArray(newLines.size + 1) }
    for (i in oldLines.size - 1 downTo 0) {
        for (j in newLines.size - 1 downTo 0) {
            dp[i][j] = if (oldLines[i] == newLines[j]) dp[i + 1][j + 1] + 1 else max(dp[i + 1][j], dp[i][j + 1])
        }
    }
    val common = dp[0][0]
    return ChangeStats(newLines.size - common, oldLines.size - common)
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
private fun lineCount(text: String): Int = if (text.isBlank()) 0 else splitTextLines(text).size
private fun editPreview(args: JsonObject?): String? = args.firstString("newText", "new_text", "replacement", "content", "text", "value") ?: ((args?.get("edits") as? JsonArray)?.firstOrNull()?.toolObjectOrNull()).firstString("newText", "new_text", "replacement", "content", "text", "value")
private fun JsonObject?.toolPath(): String? = this?.firstString("path", "file", "filename", "filePath", "file_path", "directory", "dir", "cwd")
private fun JsonObject?.firstString(vararg keys: String): String? = keys.firstNotNullOfOrNull { this?.stringValue(it)?.takeIf(String::isNotBlank) }
private fun uniqueStrings(values: List<String?>): List<String> = values.mapNotNull { it?.trim()?.takeIf(String::isNotBlank) }.distinct()
private fun fileNameFromPath(path: String): String = path.replace('\\', '/').trimEnd('/').split('/').filter { it.isNotBlank() }.lastOrNull() ?: path.ifBlank { "file" }
private fun fileExtension(name: String): String = fileNameFromPath(name).lowercase().substringAfterLast('.', missingDelimiterValue = "").takeIf { it != fileNameFromPath(name).lowercase() }.orEmpty()
private fun readLineSummary(args: JsonObject?, result: String): String? {
    val start = args.numericValue("offset", "start", "startLine", "start_line", "line", "from")
    val limit = args.numericValue("limit", "lines", "lineCount", "line_count", "count")
    return when {
        start != null && limit != null -> "L$start-${max(start, start + limit - 1)}"
        start != null -> "L$start+"
        limit != null -> "first $limit lines"
        result.isNotBlank() -> lineCount(result).takeIf { it > 0 }?.let { if (it == 1) "L1" else "L1-$it" }
        else -> null
    }
}
private fun JsonObject?.numericValue(vararg keys: String): Int? = keys.firstNotNullOfOrNull { key ->
    val raw = this?.stringValue(key)?.replace(",", "")?.trim()
    raw?.toDoubleOrNull()?.takeIf { it.isFinite() }?.toInt()?.coerceAtLeast(1)
}
private fun compactText(vararg parts: String?): String = parts.mapNotNull { it?.trim()?.takeIf(String::isNotBlank) }.joinToString(" · ").ifBlank { "Details" }
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
private fun CodeBlock(
    title: String,
    text: String,
    collapsedChars: Int = TOOL_CODE_COLLAPSED_CHARS,
    maxCollapsedLines: Int? = null,
    forceDark: Boolean = false,
    highlight: Boolean = true
) {
    val clipboard = LocalClipboardManager.current
    val dark = forceDark || MaterialTheme.colorScheme.isDarkLike()
    val bg = MaterialTheme.colorScheme.surfaceContainerHighest
    val headerBg = MaterialTheme.colorScheme.surfaceContainerHigh
    val label = MaterialTheme.colorScheme.onSurfaceVariant
    val lines = remember(text) { text.replace("\r\n", "\n").split("\n") }
    val lineClipped = maxCollapsedLines?.let { lines.size > it } == true
    val charClipped = text.length > collapsedChars
    val clipped = lineClipped || charClipped
    var expanded by rememberSaveable { mutableStateOf(false) }
    val collapsedText = remember(text, collapsedChars, maxCollapsedLines) {
        val lineLimited = maxCollapsedLines?.let { max -> if (lines.size > max) lines.take(max).joinToString("\n") else text } ?: text
        if (lineLimited.length > collapsedChars) lineLimited.take(collapsedChars) else lineLimited
    }
    val displayText = if (clipped && !expanded) collapsedText else text
    val highlighted = remember(displayText, title, dark, highlight) { if (highlight) highlightCode(displayText, title, dark) else AnnotatedString(displayText) }
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
                    Text(if (expanded) localized("收起", "Collapse") else localized("展开全部", "Expand"), fontSize = 12.sp)
                }
            }
            TextButton(onClick = { clipboard.setText(AnnotatedString(text)) }, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)) { Icon(Icons.Rounded.ContentCopy, contentDescription = null, modifier = Modifier.size(13.dp)); Text(localized("复制", "Copy"), fontSize = 12.sp) }
        }
        Text(highlighted, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall, modifier = Modifier.fillMaxWidth().background(bg).padding(10.dp), lineHeight = 18.sp)
        if (clipped && !expanded) {
            val hiddenLines = (lines.size - (maxCollapsedLines ?: lines.size)).coerceAtLeast(0)
            val hiddenChars = (text.length - displayText.length).coerceAtLeast(0)
            Text(
                if (LocalAppStrings.current === EnStrings) {
                    "… ${listOfNotNull(hiddenLines.takeIf { it > 0 }?.let { "$it folded lines, ${lines.size} total" }, hiddenChars.takeIf { it > 0 }?.let { "$it hidden chars" }).joinToString("; ")}."
                } else {
                    "… ${listOfNotNull(hiddenLines.takeIf { it > 0 }?.let { "折叠 $it 行，共 ${lines.size} 行" }, hiddenChars.takeIf { it > 0 }?.let { "隐藏 $it 个字符" }).joinToString("；")}" 
                },
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
private fun ExpandableBlock(title: String, text: String, autoOpen: Boolean = false, autoCollapse: Boolean = false, stateKey: String = title, lightweight: Boolean = false) {
    var open by rememberSaveable(stateKey) { mutableStateOf(autoOpen) }
    var openedByAuto by rememberSaveable(stateKey) { mutableStateOf(autoOpen) }
    LaunchedEffect(autoOpen, autoCollapse, stateKey) {
        when {
            autoOpen -> { open = true; openedByAuto = true }
            autoCollapse && openedByAuto -> { open = false; openedByAuto = false }
        }
    }
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(10.dp)) {
            Row(Modifier.fillMaxWidth().clickable { open = !open; openedByAuto = false }, horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Psychology, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                    Text(title, fontWeight = FontWeight.Bold)
                }
                Text(if (open) localized("收起", "Collapse") else localized("展开", "Expand"), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
            }
            if (open) Spacer(Modifier.height(8.dp))
            if (open) MarkdownishText(text, lightweight = lightweight)
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
        confirmButton = { TextButton(onClick = onDismiss) { Text(localized("关闭", "Close")) } },
        title = { Text(image.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                image.path?.let { Text(it, fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis) }
                Box(Modifier.fillMaxWidth().heightIn(min = 180.dp, max = 460.dp).background(MaterialTheme.colorScheme.surfaceContainerHighest, RoundedCornerShape(14.dp)).padding(8.dp), contentAlignment = Alignment.Center) {
                    if (bitmap != null) Image(bitmap = bitmap, contentDescription = image.name, modifier = Modifier.fillMaxWidth().heightIn(max = 430.dp), contentScale = ContentScale.Fit)
                    else Text(localized("无法预览图片", "Cannot preview image"), color = MaterialTheme.colorScheme.onSurfaceVariant)
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
    val prompts = if (LocalAppStrings.current === EnStrings) listOf(
        "Please inspect this workspace and summarize it briefly." to "Summarize",
        "Please run the necessary checks and identify the best improvements." to "Check project",
        "Please implement a small change and explain how to test it." to "Implement"
    ) else listOf(
        "请快速了解这个 workspace 的结构，并给我一个简短总结。" to "总结 workspace",
        "请运行必要检查，找出当前项目最值得改进的地方。" to "检查项目",
        "请帮我实现一个小改动，并说明测试方式。" to "实现改动"
    )
    ElevatedCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) { Text(localized("准备好开始了吗？", "Ready to start?"), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black); Text(localized("描述任务即可开始。", "Describe a task to begin."), color = MaterialTheme.colorScheme.onSurfaceVariant); Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) { prompts.forEach { (p, label) -> AssistChip(onClick = { onPrompt(p) }, label = { Text(label) }) } } } }
}

@Composable
private fun CenterWelcome(title: String, subtitle: String) { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { ElevatedCard(Modifier.padding(22.dp).fillMaxWidth()) { Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) { Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold); Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant) } } } }

@Composable
private fun ToolsScreen(state: AppUiState, viewModel: AppViewModel) {
    val strings = stringsFor(state.languageMode)
    Column(Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = state.selectedToolTab.ordinal) {
            ToolTab.entries.forEach { tab ->
                Tab(
                    selected = state.selectedToolTab == tab,
                    onClick = { viewModel.setToolTab(tab) },
                    icon = { Icon(when (tab) { ToolTab.Terminal -> Icons.Rounded.Terminal; ToolTab.Files -> Icons.Rounded.Folder; ToolTab.Pi -> Icons.Rounded.Settings; ToolTab.CodeServer -> Icons.Rounded.Code }, contentDescription = null) },
                    text = { Text(when (tab) { ToolTab.Terminal -> strings.toolsTerminal; ToolTab.Files -> strings.toolsFiles; ToolTab.Pi -> strings.toolsPi; ToolTab.CodeServer -> strings.toolsCode }) }
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
    if (activeBox == null) { CenterWelcome(localized("请选择 Box", "Select a box"), localized("Shell 连接到 /workspace。", "Shell opens at /workspace.")) ; return }
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
                        Text(localized("独立终端", "Terminal"), fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Text(activeBox.name, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    StatusChip(activeBox.status)
                }
                Text(localized("在独立页面打开 Shell。", "Open the shell in a separate screen."), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
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
                    Text("${localized("打开", "Open")} ${activeBox.name} Shell")
                }
            }
        }
        ElevatedCard(Modifier.fillMaxWidth(), colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(localized("快捷键", "Shortcuts"), fontWeight = FontWeight.Bold)
                Text("ESC · TAB · CTRL · ALT · arrows · paste", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
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
    if (state.activeBox == null) { CenterWelcome(localized("请选择 Box", "Select a box"), localized("浏览和管理 workspace 文件。", "Browse workspace files.")) ; return }
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    var path by remember(state.activeBoxId) { mutableStateOf(viewModel.rememberedFileBrowserPath(state.activeBoxId)) }
    var bookmarks by remember(state.activeBoxId) { mutableStateOf(viewModel.rememberedFileBookmarks(state.activeBoxId)) }
    var showBookmarksPanel by remember(state.activeBoxId) { mutableStateOf(false) }
    var entries by remember { mutableStateOf<List<FileEntry>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var createKind by remember { mutableStateOf<String?>(null) }
    var createName by remember { mutableStateOf("") }
    var pendingDownload by remember { mutableStateOf<DownloadedFile?>(null) }
    var actionEntry by remember { mutableStateOf<FileEntry?>(null) }
    var copyEntry by remember { mutableStateOf<FileEntry?>(null) }
    var moveEntry by remember { mutableStateOf<FileEntry?>(null) }
    var deleteEntry by remember { mutableStateOf<FileEntry?>(null) }
    var largePreviewEntry by remember { mutableStateOf<FileEntry?>(null) }
    var apkPermissionEntry by remember { mutableStateOf<FileEntry?>(null) }
    var pendingApkInstallAfterPermission by remember { mutableStateOf<FileEntry?>(null) }
    var previewDownload by remember { mutableStateOf<PreviewDownloadState?>(null) }
    var previewJob by remember { mutableStateOf<Job?>(null) }
    val createDoc = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri -> val file = pendingDownload; if (uri != null && file != null) context.contentResolver.openOutputStream(uri)?.use { it.write(file.bytes) }; pendingDownload = null }
    val pickUpload = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        scope.launch {
            uris.mapNotNull { context.readDraftAttachment(it) }.forEach { viewModel.uploadFile(path, it) }
            loadFiles(viewModel, path, { loading = it }, { entries = it }, { error = it })
        }
    }
    val previewTitle = localized("预览", "Preview")
    val previewNoApp = localized("没有可打开此文件的应用", "No app can open this file")
    val previewCannotOpen = localized("无法打开预览", "Cannot open preview")
    val previewCanceled = localized("已取消预览下载", "Preview download canceled")
    val previewFailed = localized("预览失败", "Preview failed")
    val apkPermissionDenied = localized("未授予安装未知应用权限，无法安装 APK", "APK install permission was not granted")
    val apkSettingsFailed = localized("无法打开安装权限设置", "Cannot open APK install permission settings")
    val apkNoInstaller = localized("没有可用的系统安装程序", "No system package installer available")
    val apkCannotInstall = localized("无法启动 APK 安装", "Cannot start APK installation")
    val selectSessionFirst = localized("请先选择 Session", "Select a session first")
    val attachedText = localized("已附加", "Attached")
    val invalidNameText = localized("名称不能包含 /、\\ 或 ..", "Name cannot contain /, \\ or ..")
    val pathCopiedText = localized("已复制路径", "Path copied")
    DisposableEffect(Unit) { onDispose { previewJob?.cancel() } }
    fun reload() { scope.launch { loadFiles(viewModel, path, { loading = it }, { entries = it }, { error = it }) } }
    fun beginPreviewDownload(entry: FileEntry, installApk: Boolean = false) {
        previewJob?.cancel()
        previewDownload = PreviewDownloadState(entry = entry, bytesRead = 0L, totalBytes = entry.size.takeIf { it > 0 } ?: -1L, installApk = installApk)
        previewJob = scope.launch {
            try {
                val cached = viewModel.downloadFileToCache(entry.path) { read, total ->
                    previewDownload = PreviewDownloadState(entry = entry, bytesRead = read, totalBytes = total.takeIf { it > 0 } ?: entry.size.takeIf { it > 0 } ?: -1L, installApk = installApk)
                }
                previewDownload = null
                if (installApk) context.installCachedApk(cached, apkNoInstaller, apkCannotInstall) { viewModel.emit(it) }
                else context.openCachedPreview(cached, previewTitle, previewNoApp, previewCannotOpen) { viewModel.emit(it) }
            } catch (e: CancellationException) {
                previewDownload = null
                viewModel.emit(previewCanceled)
            } catch (e: Exception) {
                previewDownload = null
                viewModel.emit("$previewFailed: ${e.message}")
            }
        }
    }
    val installPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        val entry = pendingApkInstallAfterPermission
        pendingApkInstallAfterPermission = null
        if (entry != null) {
            if (context.canInstallUnknownApks()) beginPreviewDownload(entry, installApk = true) else viewModel.emit(apkPermissionDenied)
        }
    }
    fun requestApkInstall(entry: FileEntry) {
        if (context.canInstallUnknownApks()) beginPreviewDownload(entry, installApk = true) else apkPermissionEntry = entry
    }
    fun requestPreview(entry: FileEntry) {
        if (isApkFile(entry)) requestApkInstall(entry)
        else if (entry.size >= PREVIEW_LARGE_FILE_THRESHOLD_BYTES) largePreviewEntry = entry
        else beginPreviewDownload(entry)
    }
    fun attach(entry: FileEntry) {
        if (state.activeSessionId == null) {
            viewModel.emit(selectSessionFirst)
            return
        }
        viewModel.insertIntoComposer(fileRef(workspaceAbsPath(entry.path)))
        viewModel.emit("$attachedText ${entry.name}")
    }
    fun createTargetPath(name: String): String = if (path == "." || path.isBlank()) name else "$path/$name"
    val normalizedPath = normalizeFileBrowserPath(path)
    val currentBookmarked = bookmarks.contains(normalizedPath)
    fun saveBookmarkList(next: List<String>) {
        val cleaned = next.map { normalizeFileBrowserPath(it) }.distinct().sorted()
        bookmarks = cleaned
        viewModel.rememberFileBookmarks(state.activeBoxId, cleaned)
    }
    fun addBookmark(targetPath: String = path) {
        val normalized = normalizeFileBrowserPath(targetPath)
        if (normalized !in bookmarks) saveBookmarkList(bookmarks + normalized)
    }
    fun removeBookmark(targetPath: String = path) {
        val normalized = normalizeFileBrowserPath(targetPath)
        saveBookmarkList(bookmarks.filterNot { it == normalized })
    }
    fun toggleBookmark(targetPath: String = path) {
        val normalized = normalizeFileBrowserPath(targetPath)
        if (normalized in bookmarks) removeBookmark(normalized) else addBookmark(normalized)
    }
    LaunchedEffect(state.activeBoxId, path) {
        viewModel.rememberFileBrowserPath(state.activeBoxId, path)
        loadFiles(viewModel, path, { loading = it }, { entries = it }, { error = it })
    }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface).padding(horizontal = 10.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            FilledTonalIconButton(onClick = { path = parentPath(path) }, modifier = Modifier.size(36.dp)) { Icon(Icons.Rounded.ArrowUpward, contentDescription = localized("上级", "Up"), modifier = Modifier.size(19.dp)) }
            FilledTonalIconButton(onClick = { reload() }, modifier = Modifier.size(36.dp)) { Icon(Icons.Rounded.Refresh, contentDescription = localized("刷新", "Refresh"), modifier = Modifier.size(19.dp)) }
            FilledTonalIconButton(onClick = { showBookmarksPanel = !showBookmarksPanel }, modifier = Modifier.size(36.dp)) { Icon(if (currentBookmarked) Icons.Rounded.BookmarkAdded else Icons.Rounded.Bookmarks, contentDescription = localized("书签", "Bookmarks"), modifier = Modifier.size(19.dp), tint = if (currentBookmarked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSecondaryContainer) }
            FilledTonalButton(onClick = { pickUpload.launch(arrayOf("*/*")) }, contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)) { Icon(Icons.Rounded.Upload, contentDescription = null, modifier = Modifier.size(17.dp)); Spacer(Modifier.width(4.dp)); Text(localized("上传", "Upload"), fontSize = 13.sp) }
            OutlinedButton(onClick = { createKind = "file"; createName = "" }, contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)) { Icon(Icons.Rounded.NoteAdd, contentDescription = null, modifier = Modifier.size(17.dp)); Spacer(Modifier.width(4.dp)); Text(localized("文件", "File"), fontSize = 13.sp) }
            OutlinedButton(onClick = { createKind = "dir"; createName = "" }, contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)) { Icon(Icons.Rounded.CreateNewFolder, contentDescription = null, modifier = Modifier.size(17.dp)); Spacer(Modifier.width(4.dp)); Text(localized("目录", "Folder"), fontSize = 13.sp) }
        }
        Surface(Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surfaceContainerHighest, shape = RoundedCornerShape(10.dp)) {
            Row(Modifier.padding(horizontal = 10.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Rounded.FolderOpen, contentDescription = null, modifier = Modifier.size(17.dp), tint = MaterialTheme.colorScheme.primary)
                BasicTextField(value = path, onValueChange = { path = it.ifBlank { "." } }, singleLine = true, textStyle = TextStyle(fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface, fontFamily = FontFamily.Monospace), modifier = Modifier.weight(1f))
            }
        }
        AnimatedVisibility(visible = showBookmarksPanel) {
            FileBookmarksPanel(
                currentPath = normalizedPath,
                bookmarks = bookmarks,
                onJump = { path = it; showBookmarksPanel = false },
                onToggleCurrent = { toggleBookmark(path) },
                onRemove = { removeBookmark(it) }
            )
        }
        createKind?.let { kind ->
            Surface(Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surfaceContainerLow, shape = RoundedCornerShape(10.dp), border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
                Row(Modifier.padding(8.dp), horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(if (kind == "dir") Icons.Rounded.CreateNewFolder else Icons.Rounded.NoteAdd, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    OutlinedTextField(createName, { createName = it }, label = { Text(if (kind == "dir") localized("目录名", "Folder name") else localized("文件名", "File name")) }, singleLine = true, modifier = Modifier.weight(1f))
                    TextButton(onClick = { createKind = null; createName = "" }) { Text(localized("取消", "Cancel")) }
                    Button(onClick = {
                        val name = createName.trim()
                        if (!isSafeFileName(name)) { error = invalidNameText; return@Button }
                        scope.launch {
                            error = null
                            runCatching {
                                val target = createTargetPath(name)
                                if (kind == "dir") viewModel.mkdir(target) else viewModel.uploadFile(path, DraftAttachment(name, "text/plain", ByteArray(0), false))
                                createKind = null; createName = ""
                                if (kind == "dir") path = target else loadFiles(viewModel, path, { loading = it }, { entries = it }, { error = it })
                            }.onFailure { error = it.message }
                        }
                    }, enabled = createName.isNotBlank()) { Text(localized("创建", "Create")) }
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
                if (!loading && entries.isEmpty()) item { Text(localized("空目录", "Empty folder"), Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
    }
    actionEntry?.let { entry ->
        ModalBottomSheet(onDismissRequest = { actionEntry = null }) {
            SheetHeader(if (entry.type == "directory") Icons.Rounded.Folder else Icons.Rounded.Description, entry.name, workspaceAbsPath(entry.path))
            if (entry.type == "directory") ListItem(headlineContent = { Text(localized("打开目录", "Open folder")) }, leadingContent = { Icon(Icons.Rounded.FolderOpen, contentDescription = null) }, modifier = Modifier.clickable { path = entry.path; actionEntry = null })
            if (entry.type == "directory") {
                val bookmarked = bookmarks.contains(normalizeFileBrowserPath(entry.path))
                ListItem(headlineContent = { Text(if (bookmarked) localized("移除书签", "Remove bookmark") else localized("添加到书签", "Add bookmark")) }, leadingContent = { Icon(if (bookmarked) Icons.Rounded.BookmarkAdded else Icons.Rounded.BookmarkAdd, contentDescription = null) }, modifier = Modifier.clickable { toggleBookmark(entry.path); actionEntry = null })
            }
            if (entry.type == "file") {
                val apk = isApkFile(entry)
                ListItem(
                    headlineContent = { Text(if (apk) localized("安装 APK", "Install APK") else localized("预览打开", "Preview")) },
                    supportingContent = { Text(if (apk) localized("下载后调用系统安装程序", "Download and launch system installer") else localized("下载到缓存后打开", "Download to cache and open")) },
                    leadingContent = { Icon(if (apk) Icons.Rounded.Android else Icons.Rounded.Visibility, contentDescription = null) },
                    modifier = Modifier.clickable { actionEntry = null; requestPreview(entry) }
                )
            }
            if (entry.type == "file") ListItem(headlineContent = { Text(localized("附加到消息", "Attach to message")) }, supportingContent = { Text(fileRef(workspaceAbsPath(entry.path))) }, leadingContent = { Icon(Icons.Rounded.AttachFile, contentDescription = null) }, modifier = Modifier.clickable { attach(entry); actionEntry = null })
            ListItem(headlineContent = { Text(localized("复制路径", "Copy path")) }, supportingContent = { Text(workspaceAbsPath(entry.path)) }, leadingContent = { Icon(Icons.Rounded.ContentCopy, contentDescription = null) }, modifier = Modifier.clickable { clipboard.setText(AnnotatedString(workspaceAbsPath(entry.path))); actionEntry = null; viewModel.emit(pathCopiedText) })
            ListItem(headlineContent = { Text(localized("复制到…", "Copy to…")) }, supportingContent = { Text(defaultCopyTarget(entry.path)) }, leadingContent = { Icon(Icons.Rounded.FileCopy, contentDescription = null) }, modifier = Modifier.clickable { copyEntry = entry; actionEntry = null })
            ListItem(headlineContent = { Text(localized("移动 / 重命名…", "Move / rename…")) }, supportingContent = { Text(entry.path) }, leadingContent = { Icon(Icons.Rounded.DriveFileMove, contentDescription = null) }, modifier = Modifier.clickable { moveEntry = entry; actionEntry = null })
            if (entry.type == "file") ListItem(headlineContent = { Text(localized("下载", "Download")) }, leadingContent = { Icon(Icons.Rounded.Download, contentDescription = null) }, modifier = Modifier.clickable { scope.launch { val file = viewModel.downloadFile(entry.path); pendingDownload = file; createDoc.launch(file.name) }; actionEntry = null })
            ListItem(headlineContent = { Text(localized("删除", "Delete"), color = MaterialTheme.colorScheme.error) }, leadingContent = { Icon(Icons.Rounded.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) }, modifier = Modifier.clickable { deleteEntry = entry; actionEntry = null })
            Spacer(Modifier.height(18.dp))
        }
    }
    copyEntry?.let { entry -> FileOperationDialog(title = localized("复制到", "Copy to"), entry = entry, initialTarget = defaultCopyTarget(entry.path), onDismiss = { copyEntry = null }, onConfirm = { target -> scope.launch { runCatching { viewModel.copyFile(entry.path, workspaceRelPath(target)); copyEntry = null; loadFiles(viewModel, path, { loading = it }, { entries = it }, { error = it }) }.onFailure { error = it.message } } }) }
    moveEntry?.let { entry -> FileOperationDialog(title = localized("移动 / 重命名", "Move / rename"), entry = entry, initialTarget = entry.path, onDismiss = { moveEntry = null }, onConfirm = { target -> scope.launch { runCatching { viewModel.moveFile(entry.path, workspaceRelPath(target)); moveEntry = null; path = parentPath(workspaceRelPath(target)); loadFiles(viewModel, path, { loading = it }, { entries = it }, { error = it }) }.onFailure { error = it.message } } }) }
    deleteEntry?.let { entry -> ConfirmDialog(localized("删除", "Delete") + " ${entry.name}", localized("确定删除", "Delete") + " ${workspaceAbsPath(entry.path)}?", onDismiss = { deleteEntry = null }, onConfirm = { scope.launch { viewModel.deleteFile(entry.path); deleteEntry = null; loadFiles(viewModel, path, { loading = it }, { entries = it }, { error = it }) } }) }
    apkPermissionEntry?.let { entry ->
        ApkInstallPermissionDialog(
            entry = entry,
            onDismiss = { apkPermissionEntry = null },
            onOpenSettings = {
                apkPermissionEntry = null
                pendingApkInstallAfterPermission = entry
                runCatching { installPermissionLauncher.launch(apkInstallPermissionIntent(context)) }
                    .onFailure { first ->
                        runCatching { installPermissionLauncher.launch(Intent(Settings.ACTION_SECURITY_SETTINGS)) }
                            .onFailure { second ->
                                pendingApkInstallAfterPermission = null
                                viewModel.emit("$apkSettingsFailed: ${second.message ?: first.message}")
                            }
                    }
            }
        )
    }
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

@Composable
private fun FileBookmarksPanel(
    currentPath: String,
    bookmarks: List<String>,
    onJump: (String) -> Unit,
    onToggleCurrent: () -> Unit,
    onRemove: (String) -> Unit
) {
    OutlinedCard(Modifier.fillMaxWidth(), border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Column(Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Rounded.Bookmarks, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                Column(Modifier.weight(1f)) {
                    Text(localized("书签", "Bookmarks"), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text(localized("快速跳转", "Quick folders"), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                }
                TextButton(onClick = onToggleCurrent, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)) {
                    Icon(if (bookmarks.contains(currentPath)) Icons.Rounded.BookmarkAdded else Icons.Rounded.BookmarkAdd, contentDescription = null, modifier = Modifier.size(15.dp))
                    Text(if (bookmarks.contains(currentPath)) localized("移除当前", "Remove current") else localized("添加当前", "Add current"), fontSize = 12.sp)
                }
            }
            if (bookmarks.isEmpty()) {
                Text(localized("还没有书签。", "No bookmarks yet."), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, modifier = Modifier.padding(vertical = 6.dp))
            } else {
                Column(Modifier.fillMaxWidth().heightIn(max = 220.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    bookmarks.forEach { bookmark ->
                        val active = normalizeFileBrowserPath(bookmark) == currentPath
                        Surface(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { onJump(bookmark) },
                            color = if (active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
                            contentColor = if (active) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                        ) {
                            Row(Modifier.padding(start = 10.dp, end = 4.dp, top = 7.dp, bottom = 7.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(Icons.Rounded.Bookmark, contentDescription = null, modifier = Modifier.size(17.dp), tint = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                                Column(Modifier.weight(1f)) {
                                    Text(bookmarkLabel(bookmark), fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(workspaceAbsPath(bookmark), fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = LocalContentColor.current.copy(alpha = .72f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                                IconButton(onClick = { onRemove(bookmark) }, modifier = Modifier.size(32.dp)) { Icon(Icons.Rounded.Close, contentDescription = localized("移除书签", "Remove bookmark"), modifier = Modifier.size(16.dp)) }
                            }
                        }
                    }
                }
            }
        }
    }
}

private suspend fun loadFiles(viewModel: AppViewModel, path: String, setLoading: (Boolean) -> Unit, setEntries: (List<FileEntry>) -> Unit, setError: (String?) -> Unit) {
    setLoading(true); setError(null)
    runCatching { viewModel.listFiles(path) }.onSuccess { setEntries(it) }.onFailure { setError(it.message) }
    setLoading(false)
}

@Composable
private fun FileOperationDialog(title: String, entry: FileEntry, initialTarget: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var target by remember(entry.path, initialTarget) { mutableStateOf(workspaceAbsPath(initialTarget)) }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { Button(onClick = { onConfirm(target) }, enabled = workspaceRelPath(target).isNotBlank()) { Text(localized("确定", "OK")) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(localized("取消", "Cancel")) } },
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(entry.name, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(workspaceAbsPath(entry.path), fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(target, { target = it }, label = { Text(localized("目标路径", "Target path")) }, singleLine = true, modifier = Modifier.fillMaxWidth(), textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace, fontSize = 13.sp))
            }
        }
    )
}

private data class PreviewDownloadState(val entry: FileEntry, val bytesRead: Long, val totalBytes: Long, val installApk: Boolean = false)

@Composable
private fun ApkInstallPermissionDialog(entry: FileEntry, onDismiss: () -> Unit, onOpenSettings: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { Button(onClick = onOpenSettings) { Text(localized("去授权", "Open settings")) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(localized("取消", "Cancel")) } },
        icon = { Icon(Icons.Rounded.Android, contentDescription = null) },
        title = { Text(localized("允许安装 APK", "Allow APK install")) },
        text = {
            Text(
                localized(
                    "安装 ${entry.name} 前，需要先允许本应用安装未知来源应用。授权后会继续下载并打开系统安装程序。",
                    "Before installing ${entry.name}, allow this app to install unknown apps. After permission is granted, the APK will be downloaded and opened with the system installer."
                )
            )
        }
    )
}

@Composable
private fun LargeFilePreviewDialog(entry: FileEntry, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { Button(onClick = onConfirm) { Text(localized("继续", "Continue")) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(localized("取消", "Cancel")) } },
        title = { Text(localized("预览大文件？", "Preview large file?")) },
        text = { Text("${entry.name} · ${formatBytes(entry.size)}") }
    )
}

@Composable
private fun PreviewDownloadDialog(progress: PreviewDownloadState, onCancel: () -> Unit) {
    val total = progress.totalBytes
    val fraction = if (total > 0) (progress.bytesRead.toFloat() / total.toFloat()).coerceIn(0f, 1f) else null
    AlertDialog(
        onDismissRequest = {},
        confirmButton = {},
        dismissButton = { TextButton(onClick = onCancel) { Text(localized("取消下载", "Cancel")) } },
        title = { Text(if (progress.installApk) localized("正在准备安装", "Preparing install") else localized("正在准备预览", "Preparing preview")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(progress.entry.name, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (fraction != null) LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth()) else LinearProgressIndicator(Modifier.fillMaxWidth())
                Text(
                    if (total > 0) "${formatBytes(progress.bytesRead)} / ${formatBytes(total)}" else localized("已下载", "Downloaded") + " ${formatBytes(progress.bytesRead)}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp
                )
            }
        }
    )
}

private fun Context.openCachedPreview(file: CachedPreviewFile, titlePrefix: String, noAppMessage: String, fallbackError: String, onError: (String) -> Unit) {
    val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file.file)
    val mimeType = previewMimeType(file.name, file.mimeType)
    val intent = Intent(Intent.ACTION_VIEW)
        .setDataAndType(uri, mimeType)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        .also { it.clipData = ClipData.newUri(contentResolver, file.name, uri) }
    runCatching { startActivity(Intent.createChooser(intent, "$titlePrefix ${file.name}")) }
        .onFailure { e ->
            val message = if (e is ActivityNotFoundException) noAppMessage else (e.message ?: fallbackError)
            onError(message)
        }
}

private fun Context.installCachedApk(file: CachedPreviewFile, noInstallerMessage: String, fallbackError: String, onError: (String) -> Unit) {
    val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file.file)
    val intent = Intent(Intent.ACTION_VIEW)
        .setDataAndType(uri, APK_MIME_TYPE)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        .also { it.clipData = ClipData.newUri(contentResolver, file.name, uri) }
    runCatching { startActivity(intent) }
        .onFailure { e ->
            val message = if (e is ActivityNotFoundException) noInstallerMessage else (e.message ?: fallbackError)
            onError(message)
        }
}

private fun Context.canInstallUnknownApks(): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.O || packageManager.canRequestPackageInstalls()

private fun apkInstallPermissionIntent(context: Context): Intent = Intent(
    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
    Uri.parse("package:${context.packageName}")
)

private fun isApkFile(entry: FileEntry): Boolean = entry.type == "file" && fileNameFromPath(entry.name.ifBlank { entry.path }).lowercase().endsWith(".apk")

private fun previewMimeType(name: String, serverMimeType: String): String {
    val clean = serverMimeType.substringBefore(';').trim().lowercase()
    if (clean.isNotBlank() && clean != "application/octet-stream" && clean != "binary/octet-stream") return clean
    val ext = name.substringAfterLast('.', "").lowercase()
    return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: when (ext) {
        "apk" -> APK_MIME_TYPE
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
            if (entry.type == "directory") Icon(Icons.Rounded.Folder, contentDescription = null, tint = Color(0xFFD6A433), modifier = Modifier.size(20.dp))
            else ToolFileIcon(fileIconForPath(entry.path))
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(entry.name, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(workspaceAbsPath(entry.path), fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis, fontFamily = FontFamily.Monospace)
            }
            Text(if (entry.type == "file") formatBytes(entry.size) else "dir", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(76.dp), maxLines = 1)
            if (entry.type == "file") IconButton(onClick = onAttach, modifier = Modifier.size(34.dp)) { Icon(Icons.Rounded.AttachFile, contentDescription = localized("附加", "Attach"), modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary) }
            else Spacer(Modifier.width(34.dp))
            IconButton(onClick = onMore, modifier = Modifier.size(34.dp)) { Icon(Icons.Rounded.MoreVert, contentDescription = localized("文件操作", "File actions"), modifier = Modifier.size(18.dp)) }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .65f))
    }
}

private fun isSafeFileName(name: String): Boolean = name.isNotBlank() && '/' !in name && '\\' !in name && name != "." && name != ".." && !name.contains("..")

@Composable
private fun PiSettingsTab(state: AppUiState, viewModel: AppViewModel) {
    val activeBox = state.activeBox
    if (activeBox == null) { CenterWelcome(localized("请选择 Box", "Select a box"), localized("配置当前 Box 的 Pi。", "Configure Pi for this box.")) ; return }
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
    var extraArgsText by remember(state.activeBoxId) { mutableStateOf("") }
    var materialized by remember(state.activeBoxId) { mutableStateOf("/workspace/.boxedagent/pi-agent") }
    var error by remember { mutableStateOf<String?>(null) }
    var ok by remember { mutableStateOf<String?>(null) }
    val savedText = localized("已保存", "Saved")
    LaunchedEffect(state.activeBoxId) { runCatching { viewModel.getPiConfig() }.onSuccess { cfg -> provider = cfg.pi.defaultProvider.orEmpty(); model = cfg.pi.defaultModel.orEmpty(); thinking = cfg.pi.defaultThinkingLevel ?: "medium"; enabledModels = cfg.pi.enabledModels.joinToString(", "); settingsText = cfg.pi.settingsJson?.let { UiJson.encodeToString(it) } ?: "{}"; modelsText = cfg.pi.modelsJson?.let { UiJson.encodeToString(it) } ?: "{}"; envText = UiJson.encodeToString(cfg.env); systemPrompt = cfg.pi.systemPrompt.orEmpty(); appendSystem = cfg.pi.appendSystemPrompt.orEmpty(); agentsMd = cfg.pi.agentsMd.orEmpty(); extraArgsText = cfg.pi.extraArgs.joinToString("\n"); materialized = cfg.materialized.piCodingAgentDir }.onFailure { error = it.message } }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { ElevatedCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(14.dp)) { Text("Pi config · ${activeBox.name}", fontWeight = FontWeight.Bold); Text("PI_CODING_AGENT_DIR: $materialized", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) } } }
        item { error?.let { Text(it, color = MaterialTheme.colorScheme.error) }; ok?.let { Text(it, color = Color(0xFF4ADE80)) } }
        item { SettingsCard(localized("模型与运行参数", "Model and runtime")) { OutlinedTextField(provider, { provider = it }, label = { Text(localized("默认 Provider", "Default provider")) }, singleLine = true, modifier = Modifier.fillMaxWidth()); OutlinedTextField(model, { model = it }, label = { Text(localized("默认 Model", "Default model")) }, singleLine = true, modifier = Modifier.fillMaxWidth()); DropdownField("Thinking", thinking, ThinkingLevels, { thinking = it }); OutlinedTextField(enabledModels, { enabledModels = it }, label = { Text("enabledModels") }, singleLine = true, modifier = Modifier.fillMaxWidth()) } }
        item { SettingsCard(localized("JSON 配置", "JSON config")) { CodeTextField(localized("环境变量 JSON", "Env JSON"), envText, { envText = it }); CodeTextField("models.json", modelsText, { modelsText = it }); CodeTextField(localized("settings.json 额外配置", "Extra settings.json"), settingsText, { settingsText = it }) } }
        item { SettingsCard(localized("插件与启动参数", "Plugins and startup args")) { Text(localized("每行一个传给 pi RPC 进程的额外参数，可用于启用插件或 MCP。保存后重启 Session 生效。", "One extra pi RPC argument per line. Use this for plugins or MCP; restart sessions after saving."), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp); CodeTextField("extraArgs", extraArgsText, { extraArgsText = it }) } }
        item { SettingsCard(localized("Prompt 与项目上下文", "Prompts and context")) { CodeTextField("SYSTEM.md", systemPrompt, { systemPrompt = it }); CodeTextField("APPEND_SYSTEM.md", appendSystem, { appendSystem = it }); CodeTextField("AGENTS.md", agentsMd, { agentsMd = it }) } }
        item { Button(onClick = { scope.launch { error = null; ok = null; runCatching { parseObject(settingsText); parseObject(modelsText); val env = parseEnv(envText); viewModel.updatePiConfig(PiConfigUpdateRequest(defaultProvider = provider.ifBlank { null }, defaultModel = model.ifBlank { null }, defaultThinkingLevel = thinking, enabledModels = enabledModels.split(',').map { it.trim() }.filter { it.isNotBlank() }, settingsJsonText = settingsText, modelsJsonText = modelsText, systemPrompt = systemPrompt, appendSystemPrompt = appendSystem, agentsMd = agentsMd, extraArgs = parseExtraArgs(extraArgsText), env = env)) }.onSuccess { ok = savedText }.onFailure { error = it.message } } }, modifier = Modifier.fillMaxWidth()) { Text(localized("保存 Pi 配置", "Save Pi config")) } }
    }
}

@Composable
private fun SettingsCard(title: String, content: @Composable ColumnScope.() -> Unit) { ElevatedCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) { Text(title, fontWeight = FontWeight.Bold); content() } } }
@Composable
private fun CodeTextField(label: String, value: String, onValue: (String) -> Unit) { OutlinedTextField(value, onValue, label = { Text(label) }, modifier = Modifier.fillMaxWidth().heightIn(min = 130.dp), textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace), maxLines = 12) }

@Composable
private fun CodeServerTab(state: AppUiState, viewModel: AppViewModel) {
    val activeBox = state.activeBox
    if (activeBox == null) { CenterWelcome(localized("请选择 Box", "Select a box"), localized("打开 code-server。", "Open code-server.")) ; return }
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val url = viewModel.codeServerUrl().orEmpty()
    var webKey by remember(url) { mutableStateOf(0) }
    val cookieHeader = viewModel.authCookieHeader()

    Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        ElevatedCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("code-server", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(localized("默认密码", "Default password") + ": ${activeBox.codeServerPassword ?: "boxedagent"}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { webKey++ }) { Text(localized("刷新", "Refresh")) }
                    OutlinedButton(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }) { Text(localized("外部浏览器", "Browser")) }
                    OutlinedButton(onClick = { clipboard.setText(AnnotatedString(url)) }) { Text(localized("复制 URL", "Copy URL")) }
                    OutlinedButton(onClick = { clipboard.setText(AnnotatedString(viewModel.bearerToken())) }) { Text(localized("复制 Token", "Copy token")) }
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
    return parsed as? JsonObject ?: error("Must be a JSON object")
}
private fun parseEnv(text: String): Map<String, String> = parseObject(text).mapValues { it.value.jsonPrimitive.contentOrNull ?: it.value.toString() }
private fun parseExtraArgs(text: String): List<String> = text.lines().map { it.trim() }.filter { it.isNotBlank() && !it.startsWith("#") }
private fun appendArgText(current: String, arg: String): String = current.trimEnd().let { if (it.isBlank()) arg else "$it $arg" }
private fun slashCompletionQuery(text: String): String? {
    val trimmed = text.trimStart()
    if (!trimmed.startsWith("/") || trimmed.contains('\n')) return null
    val token = trimmed.substringAfter('/').substringBefore(' ')
    return token.takeIf { it.length <= 48 }
}
private fun applySlashCommand(text: String, name: String): String {
    val leading = text.takeWhile { it.isWhitespace() }
    val rest = text.trimStart()
    val tail = rest.substringAfter(' ', "")
    return buildString {
        append(leading)
        append('/')
        append(name)
        if (tail.isNotBlank()) append(' ').append(tail) else append(' ')
    }
}
private fun formatLoadedResourcesSummary(resources: PiLoadedResources): String {
    val parts = listOf(
        "Context" to resources.contextFiles.size,
        "Packages" to resources.packages.size,
        "Extensions" to resources.extensions.size,
        "Skills" to resources.skills.size,
        "Prompts" to resources.prompts.size,
        "Themes" to resources.themes.size
    ).filter { it.second > 0 }.joinToString(" · ") { "${it.first} ${it.second}" }
    val warnings = resources.diagnostics.takeIf { it.isNotEmpty() }?.let { " · Warnings ${it.size}" }.orEmpty()
    return listOf(resources.cwd, parts.ifBlank { null }).filterNotNull().joinToString(" · ") + warnings
}
private fun normalizeFileBrowserPath(value: String): String {
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
private fun bookmarkLabel(path: String): String = normalizeFileBrowserPath(path).let { normalized -> if (normalized == ".") "workspace" else normalized.split('/').lastOrNull().orEmpty().ifBlank { normalized } }
private fun parentPath(path: String): String = if (path == "." || path.isBlank()) "." else path.split('/').dropLast(1).joinToString("/").ifBlank { "." }
private fun defaultCopyTarget(path: String): String {
    val rel = workspaceRelPath(path)
    val parent = parentPath(rel)
    val name = rel.substringAfterLast('/')
    val dot = name.lastIndexOf('.').takeIf { it > 0 }
    val copyName = if (dot != null) "${name.substring(0, dot)} copy${name.substring(dot)}" else "$name copy"
    return if (parent == ".") copyName else "$parent/$copyName"
}
private fun workspaceRelPath(path: String): String {
    val trimmed = path.trim()
    val rel = when {
        trimmed.isBlank() || trimmed == "/workspace" -> "."
        trimmed.startsWith("/workspace/") -> trimmed.removePrefix("/workspace/")
        trimmed.startsWith("/") -> "."
        else -> trimmed
    }
    return normalizeFileBrowserPath(rel)
}
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
