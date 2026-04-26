package com.boxedagent.android.ui.terminal

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Typeface
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.BaseInputConnection
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputMethodManager
import com.termux.terminal.KeyHandler
import com.termux.terminal.TerminalEmulator
import com.termux.terminal.TerminalOutput
import com.termux.terminal.TerminalSession
import com.termux.terminal.TerminalSessionClient
import com.termux.terminal.TextStyle
import com.termux.view.TerminalRenderer
import java.nio.charset.StandardCharsets
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * A lightweight remote terminal view backed by Termux's terminal-emulator and renderer modules.
 *
 * Termux's stock TerminalView is tightly coupled to a local PTY TerminalSession. BoxedAgent's shell
 * lives in the Docker container and is exposed over WebSocket, so this view feeds remote bytes into
 * Termux's TerminalEmulator and forwards keyboard/mouse-generated input bytes back to the socket.
 */
class RemoteTerminalView(context: Context) : View(context) {
    var onInput: ((String) -> Unit)? = null
    var onResize: ((columns: Int, rows: Int) -> Unit)? = null
    var isCtrlActive: () -> Boolean = { false }
    var isAltActive: () -> Boolean = { false }
    var onCtrlConsumed: () -> Unit = {}
    var onAltConsumed: () -> Unit = {}

    private val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    private var textSizeSp = 14f
    private var renderer = TerminalRenderer(spToPx(textSizeSp), Typeface.MONOSPACE)
    private var emulator: TerminalEmulator? = null
    private var columns = 0
    private var rows = 0
    private var topRow = 0
    private var lastTouchY = 0f
    private var dragging = false
    private val pendingOutput = mutableListOf<ByteArray>()

    private val output = object : TerminalOutput() {
        override fun write(data: ByteArray, offset: Int, count: Int) {
            onInput?.invoke(String(data, offset, count, StandardCharsets.UTF_8))
        }

        override fun titleChanged(oldTitle: String?, newTitle: String?) = Unit
        override fun onCopyTextToClipboard(text: String?) {
            clipboard.setPrimaryClip(ClipData.newPlainText("Terminal", text.orEmpty()))
        }
        override fun onPasteTextFromClipboard() {
            clipboard.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(context)?.toString()?.let { sendText(it) }
        }
        override fun onBell() = Unit
        override fun onColorsChanged() { invalidate() }
    }

    private val terminalClient = object : TerminalSessionClient {
        override fun onTextChanged(changedSession: TerminalSession) = Unit
        override fun onTitleChanged(changedSession: TerminalSession) = Unit
        override fun onSessionFinished(finishedSession: TerminalSession) = Unit
        override fun onCopyTextToClipboard(session: TerminalSession, text: String?) {
            clipboard.setPrimaryClip(ClipData.newPlainText("Terminal", text.orEmpty()))
        }
        override fun onPasteTextFromClipboard(session: TerminalSession?) = output.onPasteTextFromClipboard()
        override fun onBell(session: TerminalSession) = Unit
        override fun onColorsChanged(session: TerminalSession) { invalidate() }
        override fun onTerminalCursorStateChange(state: Boolean) { invalidate() }
        override fun setTerminalShellPid(session: TerminalSession, pid: Int) = Unit
        override fun getTerminalCursorStyle(): Int = 0
        override fun logError(tag: String?, message: String?) = Unit
        override fun logWarn(tag: String?, message: String?) = Unit
        override fun logInfo(tag: String?, message: String?) = Unit
        override fun logDebug(tag: String?, message: String?) = Unit
        override fun logVerbose(tag: String?, message: String?) = Unit
        override fun logStackTraceWithMessage(tag: String?, message: String?, e: Exception?) = Unit
        override fun logStackTrace(tag: String?, e: Exception?) = Unit
    }

    init {
        setBackgroundColor(Color.rgb(16, 16, 20))
        isFocusable = true
        isFocusableInTouchMode = true
        keepScreenOn = true
    }

    fun setTextSizeSp(value: Float) {
        if (abs(value - textSizeSp) < 0.1f) return
        textSizeSp = value
        renderer = TerminalRenderer(spToPx(value), Typeface.MONOSPACE)
        resizeTerminal(force = true)
        invalidate()
    }

    fun appendRemoteBytes(bytes: ByteArray) {
        if (emulator == null && (width <= 0 || height <= 0)) {
            pendingOutput += bytes.copyOf()
            return
        }
        val term = ensureEmulator()
        term.append(bytes, bytes.size)
        if (topRow != 0) topRow = 0
        invalidate()
    }

    fun appendRemoteText(text: String) = appendRemoteBytes(text.toByteArray(StandardCharsets.UTF_8))

    fun sendText(text: String) {
        if (text.isEmpty()) return
        val ctrl = isCtrlActive()
        val alt = isAltActive()
        val encoded = buildString {
            text.forEach { ch ->
                if (alt) append('\u001b')
                append(if (ctrl) controlChar(ch) else ch)
            }
        }
        if (ctrl) onCtrlConsumed()
        if (alt) onAltConsumed()
        onInput?.invoke(encoded)
    }

    fun sendSequence(sequence: String) {
        if (sequence.isNotEmpty()) onInput?.invoke(sequence)
    }

    fun pasteFromClipboard() = output.onPasteTextFromClipboard()

    fun sendCtrl(letter: Char) {
        onInput?.invoke(controlChar(letter).toString())
    }

    fun clearScreen() {
        pendingOutput.clear()
        emulator?.reset()
        appendRemoteText("\u001b[2J\u001b[H")
        invalidate()
    }

    fun terminalColumns(): Int = columns.takeIf { it > 0 } ?: 80
    fun terminalRows(): Int = rows.takeIf { it > 0 } ?: 24

    override fun onCheckIsTextEditor(): Boolean = true

    override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection {
        outAttrs.inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS or android.text.InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
        outAttrs.imeOptions = EditorInfo.IME_ACTION_NONE or EditorInfo.IME_FLAG_NO_EXTRACT_UI
        return object : BaseInputConnection(this, true) {
            override fun commitText(text: CharSequence?, newCursorPosition: Int): Boolean {
                sendText(text?.toString().orEmpty())
                return true
            }

            override fun deleteSurroundingText(beforeLength: Int, afterLength: Int): Boolean {
                sendSequence("\u007f")
                return true
            }

            override fun sendKeyEvent(event: KeyEvent): Boolean {
                if (event.action == KeyEvent.ACTION_DOWN) handleKeyDown(event.keyCode, event)
                return true
            }

            override fun performEditorAction(actionCode: Int): Boolean {
                sendSequence("\r")
                return true
            }
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (handleKeyDown(keyCode, event)) return true
        return super.onKeyDown(keyCode, event)
    }

    private fun handleKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        val term = emulator ?: return false
        if (event.action == KeyEvent.ACTION_MULTIPLE && keyCode == KeyEvent.KEYCODE_UNKNOWN) {
            sendText(event.characters.orEmpty())
            return true
        }
        var keyMod = 0
        val ctrl = event.isCtrlPressed || isCtrlActive()
        val alt = event.isAltPressed || isAltActive()
        if (ctrl) keyMod = keyMod or KeyHandler.KEYMOD_CTRL
        if (alt) keyMod = keyMod or KeyHandler.KEYMOD_ALT
        if (event.isShiftPressed) keyMod = keyMod or KeyHandler.KEYMOD_SHIFT
        if (event.isNumLockOn) keyMod = keyMod or KeyHandler.KEYMOD_NUM_LOCK
        val code = KeyHandler.getCode(keyCode, keyMod, term.isCursorKeysApplicationMode, term.isKeypadApplicationMode)
        if (code != null) {
            if (ctrl && isCtrlActive()) onCtrlConsumed()
            if (alt && isAltActive()) onAltConsumed()
            sendSequence(code)
            return true
        }
        val unicode = event.getUnicodeChar(event.metaState and KeyEvent.META_CTRL_MASK.inv())
        if (unicode > 0) {
            sendText(String(Character.toChars(unicode)))
            return true
        }
        return false
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                requestFocus()
                lastTouchY = event.y
                dragging = false
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val dy = event.y - lastTouchY
                if (abs(dy) >= renderer.getFontLineSpacing()) {
                    dragging = true
                    val deltaRows = (dy / renderer.getFontLineSpacing()).toInt()
                    scrollByRows(-deltaRows)
                    lastTouchY = event.y
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                if (!dragging && event.eventTime - event.downTime < 250) showKeyboard()
                return true
            }
        }
        return true
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        resizeTerminal(force = false)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val term = ensureEmulator()
        canvas.drawColor(term.mColors.mCurrentColors[TextStyle.COLOR_INDEX_BACKGROUND])
        renderer.render(term, canvas, topRow, -1, -1, -1, -1)
    }

    private fun ensureEmulator(): TerminalEmulator {
        emulator?.let { return it }
        val measuredCols = if (width > 0) (width / renderer.getFontWidth()).toInt() else 80
        val measuredRows = if (height > 0) (height / renderer.getFontLineSpacing()).toInt() else 24
        val cols = max(4, columns.takeIf { it > 0 } ?: measuredCols)
        val terminalRows = max(4, rows.takeIf { it > 0 } ?: measuredRows)
        return TerminalEmulator(output, cols, terminalRows, renderer.getFontWidth().roundToInt(), renderer.getFontLineSpacing(), 4000, terminalClient).also {
            emulator = it
            columns = cols
            rows = terminalRows
        }
    }

    private fun resizeTerminal(force: Boolean) {
        if (width <= 0 || height <= 0) return
        val newColumns = max(4, (width / renderer.getFontWidth()).toInt())
        val newRows = max(4, (height / renderer.getFontLineSpacing()).toInt())
        val current = emulator
        if (current == null) {
            columns = newColumns
            rows = newRows
            ensureEmulator()
            flushPendingOutput()
            topRow = 0
            onResize?.invoke(newColumns, newRows)
            return
        }
        if (force || newColumns != columns || newRows != rows) {
            columns = newColumns
            rows = newRows
            current.resize(newColumns, newRows, renderer.getFontWidth().roundToInt(), renderer.getFontLineSpacing())
            flushPendingOutput()
            topRow = 0
            onResize?.invoke(newColumns, newRows)
        }
    }

    private fun flushPendingOutput() {
        if (pendingOutput.isEmpty()) return
        val term = ensureEmulator()
        pendingOutput.forEach { term.append(it, it.size) }
        pendingOutput.clear()
        invalidate()
    }

    private fun scrollByRows(delta: Int) {
        val term = emulator ?: return
        val activeRows = term.screen.activeTranscriptRows
        topRow = (topRow + delta).coerceIn(-activeRows, 0)
        invalidate()
    }

    private fun showKeyboard() {
        post {
            val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(this, InputMethodManager.SHOW_IMPLICIT)
        }
    }

    private fun spToPx(sp: Float): Int = (sp * resources.displayMetrics.scaledDensity).roundToInt().coerceAtLeast(10)

    private fun controlChar(ch: Char): Char = when (ch) {
        in 'a'..'z' -> (ch.code - 'a'.code + 1).toChar()
        in 'A'..'Z' -> (ch.code - 'A'.code + 1).toChar()
        ' ', '2' -> 0.toChar()
        '[', '3' -> 27.toChar()
        '\\', '4' -> 28.toChar()
        ']', '5' -> 29.toChar()
        '^', '6' -> 30.toChar()
        '_', '7', '/' -> 31.toChar()
        '8' -> 127.toChar()
        else -> ch
    }
}
