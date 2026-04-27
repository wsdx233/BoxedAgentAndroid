package com.boxedagent.android.ui.terminal

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.view.ActionMode
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
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
    private val longPressHandler = Handler(Looper.getMainLooper())
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private val selectionHandlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(92, 107, 192)
        style = Paint.Style.FILL
    }
    private val selectionHandleBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(220, 255, 255, 255)
        style = Paint.Style.STROKE
        strokeWidth = dp(1.5f)
    }
    private val handleRadiusPx = dp(6f)
    private val handleHitRadiusPx = dp(24f)

    private var textSizeSp = 14f
    private var renderer = TerminalRenderer(spToPx(textSizeSp), Typeface.MONOSPACE)
    private var emulator: TerminalEmulator? = null
    private var columns = 0
    private var rows = 0
    private var topRow = 0
    private var lastTouchY = 0f
    private var downX = 0f
    private var downY = 0f
    private var dragging = false
    private var longPressTriggered = false
    private var pendingLongPress: Runnable? = null
    private var activeSelectionHandle: SelectionHandle? = null
    private var selectionActionMode: ActionMode? = null
    private var selectionActive = false
    private var selX1 = -1
    private var selY1 = -1
    private var selX2 = -1
    private var selY2 = -1
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
        isLongClickable = true
        keepScreenOn = true
    }

    fun setTextSizeSp(value: Float) {
        if (abs(value - textSizeSp) < 0.1f) return
        stopTextSelectionMode()
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
        handleScreenUpdated(term)
        invalidate()
    }

    fun appendRemoteText(text: String) = appendRemoteBytes(text.toByteArray(StandardCharsets.UTF_8))

    fun sendText(text: String) {
        if (text.isEmpty()) return
        stopTextSelectionMode()
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
        if (sequence.isNotEmpty()) {
            stopTextSelectionMode()
            onInput?.invoke(sequence)
        }
    }

    fun pasteFromClipboard() = output.onPasteTextFromClipboard()

    fun sendCtrl(letter: Char) {
        stopTextSelectionMode()
        onInput?.invoke(controlChar(letter).toString())
    }

    fun clearScreen() {
        stopTextSelectionMode()
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
                downX = event.x
                downY = event.y
                lastTouchY = event.y
                dragging = false
                longPressTriggered = false
                activeSelectionHandle = hitTestSelectionHandle(event.x, event.y)
                if (activeSelectionHandle == null) scheduleLongPress(event.x, event.y)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                activeSelectionHandle?.let { handle ->
                    updateSelectionHandle(handle, event.x, event.y)
                    return true
                }

                if (longPressTriggered) {
                    if (distanceSquared(downX, downY, event.x, event.y) > touchSlop * touchSlop) {
                        updateSelectionHandle(SelectionHandle.END, event.x, event.y)
                    }
                    return true
                }

                if (distanceSquared(downX, downY, event.x, event.y) > touchSlop * touchSlop) {
                    cancelPendingLongPress()
                }

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
                cancelPendingLongPress()
                if (activeSelectionHandle != null) {
                    activeSelectionHandle = null
                    selectionActionMode?.invalidate()
                    return true
                }
                if (longPressTriggered) {
                    longPressTriggered = false
                    selectionActionMode?.invalidate()
                    return true
                }
                if (selectionActive) {
                    stopTextSelectionMode()
                    return true
                }
                if (!dragging && event.eventTime - event.downTime < 250) showKeyboard()
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                cancelPendingLongPress()
                activeSelectionHandle = null
                longPressTriggered = false
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
        val selection = normalizedSelection()
        canvas.drawColor(term.mColors.mCurrentColors[TextStyle.COLOR_INDEX_BACKGROUND])
        if (selection == null) {
            renderer.render(term, canvas, topRow, -1, -1, -1, -1)
        } else {
            renderer.render(term, canvas, topRow, selection.y1, selection.y2, selection.x1, selection.x2)
            drawSelectionHandles(canvas)
        }
    }

    override fun onDetachedFromWindow() {
        cancelPendingLongPress()
        stopTextSelectionMode()
        super.onDetachedFromWindow()
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
            stopTextSelectionMode()
            columns = newColumns
            rows = newRows
            current.resize(newColumns, newRows, renderer.getFontWidth().roundToInt(), renderer.getFontLineSpacing())
            current.clearScrollCounter()
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
        handleScreenUpdated(term)
        invalidate()
    }

    private fun handleScreenUpdated(term: TerminalEmulator) {
        val rowsInHistory = term.screen.activeTranscriptRows
        if (topRow < -rowsInHistory) topRow = -rowsInHistory

        val rowShift = term.scrollCounter
        if (selectionActive && rowShift > 0) {
            if (-topRow + rowShift > rowsInHistory) {
                stopTextSelectionMode()
                topRow = 0
            } else {
                topRow -= rowShift
                selY1 -= rowShift
                selY2 -= rowShift
            }
        } else if (!selectionActive && topRow != 0) {
            topRow = 0
        }
        term.clearScrollCounter()
    }

    private fun scrollByRows(delta: Int) {
        if (selectionActive) return
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

    private fun scheduleLongPress(x: Float, y: Float) {
        cancelPendingLongPress()
        val runnable = Runnable {
            pendingLongPress = null
            if (dragging) return@Runnable
            longPressTriggered = true
            startTextSelectionMode(x, y)
        }
        pendingLongPress = runnable
        longPressHandler.postDelayed(runnable, ViewConfiguration.getLongPressTimeout().toLong())
    }

    private fun cancelPendingLongPress() {
        pendingLongPress?.let { longPressHandler.removeCallbacks(it) }
        pendingLongPress = null
    }

    private fun startTextSelectionMode(x: Float, y: Float) {
        val term = ensureEmulator()
        val position = terminalPositionForPoint(x, y)
        selX1 = position.x
        selY1 = position.y
        selX2 = position.x
        selY2 = position.y
        expandInitialSelection(term)
        selectionActive = true
        performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        startSelectionActionMode()
        invalidate()
    }

    private fun stopTextSelectionMode(finishActionMode: Boolean = true) {
        cancelPendingLongPress()
        activeSelectionHandle = null
        longPressTriggered = false
        if (!selectionActive && selectionActionMode == null) return
        selectionActive = false
        selX1 = -1
        selY1 = -1
        selX2 = -1
        selY2 = -1
        if (finishActionMode) {
            val mode = selectionActionMode
            selectionActionMode = null
            mode?.finish()
        } else {
            selectionActionMode = null
        }
        invalidate()
    }

    private fun startSelectionActionMode() {
        if (selectionActionMode != null) {
            selectionActionMode?.invalidate()
            return
        }

        selectionActionMode = startActionMode(object : ActionMode.Callback2() {
            override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
                val show = MenuItem.SHOW_AS_ACTION_IF_ROOM or MenuItem.SHOW_AS_ACTION_WITH_TEXT
                menu.add(Menu.NONE, MENU_COPY, Menu.NONE, "复制").setShowAsAction(show)
                menu.add(Menu.NONE, MENU_PASTE, Menu.NONE, "粘贴").setShowAsAction(show)
                menu.add(Menu.NONE, MENU_SELECT_ALL, Menu.NONE, "全选")
                return true
            }

            override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean {
                menu.findItem(MENU_COPY)?.isEnabled = getSelectedText().isNotEmpty()
                menu.findItem(MENU_PASTE)?.isEnabled = clipboard.hasPrimaryClip()
                return true
            }

            override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
                when (item.itemId) {
                    MENU_COPY -> {
                        copySelectionToClipboard()
                        return true
                    }
                    MENU_PASTE -> {
                        stopTextSelectionMode()
                        pasteFromClipboard()
                        return true
                    }
                    MENU_SELECT_ALL -> {
                        selectAllText()
                        mode.invalidate()
                        return true
                    }
                }
                return false
            }

            override fun onDestroyActionMode(mode: ActionMode) {
                stopTextSelectionMode(finishActionMode = false)
            }

            override fun onGetContentRect(mode: ActionMode, view: View, outRect: Rect) {
                getSelectionContentRect(outRect)
            }
        }, ActionMode.TYPE_FLOATING)
    }

    private fun copySelectionToClipboard() {
        val text = getSelectedText()
        if (text.isNotEmpty()) {
            clipboard.setPrimaryClip(ClipData.newPlainText("Terminal", text))
        }
        stopTextSelectionMode()
    }

    private fun selectAllText() {
        val term = emulator ?: return
        selectionActive = true
        selX1 = 0
        selY1 = -term.screen.activeTranscriptRows
        selX2 = (term.mColumns - 1).coerceAtLeast(0)
        selY2 = (term.mRows - 1).coerceAtLeast(0)
        topRow = selY1.coerceAtMost(0)
        invalidate()
    }

    private fun getSelectedText(): String {
        val term = emulator ?: return ""
        val selection = normalizedSelection() ?: return ""
        return term.getSelectedText(selection.x1, selection.y1, selection.x2, selection.y2)
    }

    private fun expandInitialSelection(term: TerminalEmulator) {
        val row = selY1
        if (!isSelectableCell(term, selX1, row)) return
        while (selX1 > 0 && isSelectableCell(term, selX1 - 1, row)) selX1--
        while (selX2 < term.mColumns - 1 && isSelectableCell(term, selX2 + 1, row)) selX2++
    }

    private fun isSelectableCell(term: TerminalEmulator, x: Int, y: Int): Boolean {
        val text = runCatching { term.screen.getSelectedText(x, y, x, y, false) }.getOrDefault("")
        return text.any { it != '\u0000' && !it.isWhitespace() }
    }

    private fun updateSelectionHandle(handle: SelectionHandle, x: Float, y: Float) {
        if (!selectionActive) return
        val position = terminalPositionForPoint(x, y)
        when (handle) {
            SelectionHandle.START -> {
                if (comparePosition(position.x, position.y, selX2, selY2) <= 0) {
                    selX1 = position.x
                    selY1 = position.y
                } else {
                    selX1 = selX2
                    selY1 = selY2
                }
            }
            SelectionHandle.END -> {
                if (comparePosition(position.x, position.y, selX1, selY1) >= 0) {
                    selX2 = position.x
                    selY2 = position.y
                } else {
                    selX2 = selX1
                    selY2 = selY1
                }
            }
        }
        selectionActionMode?.invalidate()
        invalidate()
    }

    private fun terminalPositionForPoint(x: Float, y: Float): TerminalPosition {
        val term = ensureEmulator()
        val maxColumn = (term.mColumns - 1).coerceAtLeast(0)
        val column = (x / renderer.getFontWidth()).toInt().coerceIn(0, maxColumn)
        val minRow = -term.screen.activeTranscriptRows
        val maxRow = (term.mRows - 1).coerceAtLeast(0)
        val row = ((y / renderer.getFontLineSpacing()).toInt() + topRow).coerceIn(minRow, maxRow)
        return TerminalPosition(column, row)
    }

    private fun normalizedSelection(): SelectionRange? {
        if (!selectionActive || selX1 < 0 || selX2 < 0) return null
        return if (comparePosition(selX1, selY1, selX2, selY2) <= 0) {
            SelectionRange(selX1, selY1, selX2, selY2)
        } else {
            SelectionRange(selX2, selY2, selX1, selY1)
        }
    }

    private fun comparePosition(x1: Int, y1: Int, x2: Int, y2: Int): Int = when {
        y1 < y2 -> -1
        y1 > y2 -> 1
        x1 < x2 -> -1
        x1 > x2 -> 1
        else -> 0
    }

    private fun drawSelectionHandles(canvas: Canvas) {
        val start = selectionHandleCenter(SelectionHandle.START) ?: return
        val end = selectionHandleCenter(SelectionHandle.END) ?: return
        drawSelectionHandle(canvas, start)
        drawSelectionHandle(canvas, end)
    }

    private fun drawSelectionHandle(canvas: Canvas, center: HandleCenter) {
        val stemHalfWidth = dp(1.25f)
        val stemTop = (center.y - renderer.getFontLineSpacing() * 0.55f).coerceAtLeast(0f)
        canvas.drawRoundRect(center.x - stemHalfWidth, stemTop, center.x + stemHalfWidth, center.y, stemHalfWidth, stemHalfWidth, selectionHandlePaint)
        canvas.drawCircle(center.x, center.y, handleRadiusPx, selectionHandlePaint)
        canvas.drawCircle(center.x, center.y, handleRadiusPx, selectionHandleBorderPaint)
    }

    private fun hitTestSelectionHandle(x: Float, y: Float): SelectionHandle? {
        if (!selectionActive) return null
        val radiusSquared = handleHitRadiusPx * handleHitRadiusPx
        val end = selectionHandleCenter(SelectionHandle.END)
        if (end != null && distanceSquared(x, y, end.x, end.y) <= radiusSquared) return SelectionHandle.END
        val start = selectionHandleCenter(SelectionHandle.START)
        if (start != null && distanceSquared(x, y, start.x, start.y) <= radiusSquared) return SelectionHandle.START
        return null
    }

    private fun selectionHandleCenter(handle: SelectionHandle): HandleCenter? {
        val selection = normalizedSelection() ?: return null
        val column = if (handle == SelectionHandle.START) selection.x1 else selection.x2 + 1
        val row = if (handle == SelectionHandle.START) selection.y1 else selection.y2
        val rawX = column * renderer.getFontWidth()
        val rawY = (row - topRow + 1) * renderer.getFontLineSpacing().toFloat()
        val maxX = (width - handleRadiusPx).coerceAtLeast(handleRadiusPx)
        val maxY = (height - handleRadiusPx).coerceAtLeast(handleRadiusPx)
        return HandleCenter(
            rawX.coerceIn(handleRadiusPx, maxX),
            rawY.coerceIn(handleRadiusPx, maxY)
        )
    }

    private fun getSelectionContentRect(outRect: Rect) {
        val selection = normalizedSelection()
        if (selection == null) {
            outRect.set(0, 0, width.coerceAtLeast(1), renderer.getFontLineSpacing().coerceAtLeast(1))
            return
        }
        val viewWidth = width.coerceAtLeast(1)
        val viewHeight = height.coerceAtLeast(1)
        val lineHeight = renderer.getFontLineSpacing()
        val sameRow = selection.y1 == selection.y2
        val rawLeft = if (sameRow) (selection.x1 * renderer.getFontWidth()).roundToInt() else 0
        val rawRight = if (sameRow) ((selection.x2 + 1) * renderer.getFontWidth()).roundToInt() else viewWidth
        val left = rawLeft.coerceIn(0, (viewWidth - 1).coerceAtLeast(0))
        val right = rawRight.coerceIn(left + 1, viewWidth)
        val top = ((selection.y1 - topRow) * lineHeight).coerceIn(0, (viewHeight - 1).coerceAtLeast(0))
        val bottom = ((selection.y2 - topRow + 1) * lineHeight).coerceIn(top + 1, viewHeight)
        outRect.set(left, top, right, bottom)
    }

    private fun distanceSquared(x1: Float, y1: Float, x2: Float, y2: Float): Float {
        val dx = x1 - x2
        val dy = y1 - y2
        return dx * dx + dy * dy
    }

    private fun spToPx(sp: Float): Int = (sp * resources.displayMetrics.scaledDensity).roundToInt().coerceAtLeast(10)

    private fun dp(value: Float): Float = value * resources.displayMetrics.density

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

    private enum class SelectionHandle { START, END }
    private data class TerminalPosition(val x: Int, val y: Int)
    private data class SelectionRange(val x1: Int, val y1: Int, val x2: Int, val y2: Int)
    private data class HandleCenter(val x: Float, val y: Float)

    private companion object {
        const val MENU_COPY = 1
        const val MENU_PASTE = 2
        const val MENU_SELECT_ALL = 3
    }
}
