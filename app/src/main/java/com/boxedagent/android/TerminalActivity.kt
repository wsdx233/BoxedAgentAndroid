package com.boxedagent.android

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.boxedagent.android.data.BoxedAgentApi
import com.boxedagent.android.ui.terminal.RemoteTerminalView
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import org.json.JSONObject
import kotlin.math.max

class TerminalActivity : Activity() {
    private lateinit var api: BoxedAgentApi
    private lateinit var terminalView: RemoteTerminalView
    private lateinit var statusText: TextView
    private lateinit var ctrlKey: TextView
    private lateinit var altKey: TextView

    private var webSocket: WebSocket? = null
    private var ctrlActive = false
    private var altActive = false
    private var textSizeSp = 14f
    private var firstResizeConnected = false
    private var boxId: String = ""
    private var boxName: String = "Box"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color.BLACK
        window.navigationBarColor = Color.BLACK
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = false
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightNavigationBars = false
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING)

        boxId = intent.getStringExtra(EXTRA_BOX_ID).orEmpty()
        boxName = intent.getStringExtra(EXTRA_BOX_NAME) ?: "Box"
        val baseUrl = intent.getStringExtra(EXTRA_BASE_URL).orEmpty()
        val token = intent.getStringExtra(EXTRA_TOKEN).orEmpty()
        api = BoxedAgentApi(baseUrl, token)

        setContentView(buildContent())
        if (boxId.isBlank()) {
            setStatus("缺少 Box ID")
            terminalView.appendRemoteText("Missing Box ID.\r\n")
        }
    }

    override fun onDestroy() {
        webSocket?.close(1000, null)
        webSocket = null
        super.onDestroy()
    }

    private fun buildContent(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.BLACK)
        }
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val status = insets.getInsets(WindowInsetsCompat.Type.statusBars())
            val nav = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            view.setPadding(0, status.top, 0, max(nav.bottom, ime.bottom))
            insets
        }
        ViewCompat.requestApplyInsets(root)

        statusText = TextView(this).apply {
            text = "准备连接…"
            setTextColor(Color.rgb(236, 230, 240))
            textSize = 13f
            maxLines = 1
            typeface = Typeface.DEFAULT_BOLD
        }

        val toolbar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(6), dp(5), dp(6), dp(5))
            setBackgroundColor(Color.rgb(25, 26, 31))
        }
        toolbar.addView(toolbarButton("关闭") { finish() }, LinearLayout.LayoutParams(dp(54), dp(34)))
        toolbar.addView(statusText, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { leftMargin = dp(8); rightMargin = dp(6) })
        toolbar.addView(toolbarButton("A-") { changeTextSize(-1f) }, LinearLayout.LayoutParams(dp(42), dp(34)))
        toolbar.addView(toolbarButton("A+") { changeTextSize(1f) }, LinearLayout.LayoutParams(dp(42), dp(34)).apply { leftMargin = dp(4) })
        toolbar.addView(toolbarButton("重连") { reconnect() }, LinearLayout.LayoutParams(dp(54), dp(34)).apply { leftMargin = dp(4) })
        root.addView(toolbar, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))

        terminalView = RemoteTerminalView(this).apply {
            setTextSizeSp(textSizeSp)
            onInput = { sendInput(it) }
            onResize = { cols, rows ->
                if (!firstResizeConnected) {
                    firstResizeConnected = true
                    if (boxId.isNotBlank()) connect(cols, rows)
                } else {
                    sendResize(cols, rows)
                }
            }
            isCtrlActive = { ctrlActive }
            isAltActive = { altActive }
            onCtrlConsumed = { ctrlActive = false; updateModifierKeys() }
            onAltConsumed = { altActive = false; updateModifierKeys() }
        }
        root.addView(terminalView, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))

        root.addView(buildExtraKeys(), LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        return root
    }

    private fun buildExtraKeys(): LinearLayout {
        val keys = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(4), dp(4), dp(4), dp(4))
            setBackgroundColor(Color.rgb(36, 37, 43))
        }
        keys.addView(keyRow(listOf(
            Key("ESC", "\u001b"), Key("TAB", "\t"), Key("CTRL", special = SpecialKey.CTRL), Key("ALT", special = SpecialKey.ALT),
            Key("/", "/"), Key("-", "-"), Key("|", "|"), Key("~", "~")
        )))
        keys.addView(keyRow(listOf(
            Key("HOME", "\u001b[H"), Key("↑", "\u001b[A"), Key("END", "\u001b[F"), Key("PGUP", "\u001b[5~"),
            Key("←", "\u001b[D"), Key("↓", "\u001b[B"), Key("→", "\u001b[C"), Key("PGDN", "\u001b[6~")
        )))
        keys.addView(keyRow(listOf(
            Key("C-C", ctrl = 'c'), Key("C-D", ctrl = 'd'), Key("C-Z", ctrl = 'z'), Key("BKSP", "\u007f"),
            Key("ENTER", "\r"), Key("PASTE", special = SpecialKey.PASTE), Key("CLEAR", special = SpecialKey.CLEAR)
        )))
        return keys
    }

    private fun keyRow(keys: List<Key>): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER
        keys.forEachIndexed { index, key ->
            val view = keyButton(key.label) { handleKey(key) }
            if (key.special == SpecialKey.CTRL) ctrlKey = view
            if (key.special == SpecialKey.ALT) altKey = view
            addView(view, LinearLayout.LayoutParams(0, dp(34), 1f).apply {
                if (index > 0) leftMargin = dp(4)
                topMargin = dp(3)
            })
        }
    }

    private fun handleKey(key: Key) {
        when {
            key.special == SpecialKey.CTRL -> { ctrlActive = !ctrlActive; updateModifierKeys() }
            key.special == SpecialKey.ALT -> { altActive = !altActive; updateModifierKeys() }
            key.special == SpecialKey.PASTE -> terminalView.pasteFromClipboard()
            key.special == SpecialKey.CLEAR -> terminalView.clearScreen()
            key.ctrl != null -> terminalView.sendCtrl(key.ctrl)
            else -> terminalView.sendSequence(key.sequence)
        }
    }

    private fun updateModifierKeys() {
        if (::ctrlKey.isInitialized) ctrlKey.background = keyBackground(ctrlActive)
        if (::altKey.isInitialized) altKey.background = keyBackground(altActive)
    }

    private fun reconnect() {
        firstResizeConnected = true
        connect(terminalView.terminalColumns(), terminalView.terminalRows())
    }

    private fun connect(cols: Int, rows: Int) {
        webSocket?.close(1000, null)
        terminalView.clearScreen()
        setStatus("连接 ${boxName}…")
        webSocket = api.webSocket("/ws/boxes/$boxId/terminal?cols=${cols.coerceIn(20, 400)}&rows=${rows.coerceIn(8, 120)}", object : WebSocketListener() {
            override fun onMessage(webSocket: WebSocket, text: String) {
                runOnUiThread {
                    val obj = runCatching { JSONObject(text) }.getOrNull()
                    when (obj?.optString("type")) {
                        "ready" -> setStatus("${boxName} · ${obj.optInt("cols", cols)}×${obj.optInt("rows", rows)}")
                        "error" -> {
                            setStatus("终端错误")
                            terminalView.appendRemoteText("\r\n[terminal error] ${obj.optString("error")}\r\n")
                        }
                        else -> terminalView.appendRemoteText(text)
                    }
                }
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                runOnUiThread { terminalView.appendRemoteBytes(bytes.toByteArray()) }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                runOnUiThread {
                    setStatus("连接失败")
                    terminalView.appendRemoteText("\r\n[terminal error] ${t.message}\r\n")
                }
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                runOnUiThread { setStatus("已关闭") }
            }
        })
    }

    private fun sendInput(data: String) {
        webSocket?.send("{\"type\":\"input\",\"data\":${JSONObject.quote(data)}}")
    }

    private fun sendResize(cols: Int, rows: Int) {
        webSocket?.send("{\"type\":\"resize\",\"cols\":${cols.coerceIn(20, 400)},\"rows\":${rows.coerceIn(8, 120)}}")
    }

    private fun changeTextSize(delta: Float) {
        textSizeSp = (textSizeSp + delta).coerceIn(10f, 22f)
        terminalView.setTextSizeSp(textSizeSp)
    }

    private fun setStatus(text: String) { statusText.text = text }

    private fun toolbarButton(text: String, onClick: () -> Unit): TextView = TextView(this).apply {
        this.text = text
        textSize = 12f
        setTextColor(Color.rgb(236, 230, 240))
        gravity = Gravity.CENTER
        typeface = Typeface.DEFAULT_BOLD
        background = keyBackground(false, radius = 8f)
        setOnClickListener { onClick() }
    }

    private fun keyButton(text: String, onClick: () -> Unit): TextView = TextView(this).apply {
        this.text = text
        textSize = 12f
        setTextColor(Color.rgb(236, 230, 240))
        gravity = Gravity.CENTER
        typeface = Typeface.MONOSPACE
        background = keyBackground(false)
        setOnClickListener { onClick() }
    }

    private fun keyBackground(active: Boolean, radius: Float = 7f): GradientDrawable = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = dp(radius.toInt()).toFloat()
        setColor(if (active) Color.rgb(103, 80, 164) else Color.rgb(58, 59, 66))
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private data class Key(val label: String, val sequence: String = "", val special: SpecialKey? = null, val ctrl: Char? = null)
    private enum class SpecialKey { CTRL, ALT, PASTE, CLEAR }

    companion object {
        const val EXTRA_BASE_URL = "baseUrl"
        const val EXTRA_TOKEN = "token"
        const val EXTRA_BOX_ID = "boxId"
        const val EXTRA_BOX_NAME = "boxName"
    }
}
