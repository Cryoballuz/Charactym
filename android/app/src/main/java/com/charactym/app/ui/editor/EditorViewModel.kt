package com.charactym.app.ui.editor

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.charactym.app.data.GlyphBitmapStore
import com.charactym.app.data.GlyphRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.sqrt

/** 绘图工具 */
enum class Tool { BRUSH, ERASER }

/** 笔刷粗细三档：画笔与橡皮使用不同的像素宽度（以触点为中心、边长为 px 格的方形笔头） */
enum class BrushSize(val brushPx: Int, val eraserPx: Int, val label: String) {
    SMALL(4, 10, "小"),
    MEDIUM(8, 20, "中"),
    LARGE(12, 30, "大"),
}

/**
 * 录入页状态：持有 256×256 画布数据（ByteArray，0=白 / 0xFF=黑）、
 * 显示位图、撤销栈（最多 50 步）、输入框内容与保存逻辑。
 */
class EditorViewModel(
    private val repository: GlyphRepository,
    private val editId: Long? = null,
) : ViewModel() {

    // ---- 画布数据 ----
    private val pixels = ByteArray(GlyphBitmapStore.PIXEL_COUNT)

    /** 显示用位图（256×256），笔画直接画在其关联的 Canvas 上，读回像素时同步到 pixels */
    private val bitmap = Bitmap.createBitmap(
        GlyphBitmapStore.SIZE,
        GlyphBitmapStore.SIZE,
        Bitmap.Config.ARGB_8888,
    ).apply { eraseColor(Color.WHITE) }
    private val canvas = Canvas(bitmap)

    private val undoStack = ArrayDeque<ByteArray>()

    private val blackPaint = android.graphics.Paint().apply {
        color = Color.BLACK
        isAntiAlias = false
        style = android.graphics.Paint.Style.FILL
    }
    private val whitePaint = android.graphics.Paint().apply {
        color = Color.WHITE
        isAntiAlias = false
        style = android.graphics.Paint.Style.FILL
    }

    // ---- 界面状态 ----
    var tool by mutableStateOf(Tool.BRUSH)
        private set
    var brushSize by mutableStateOf(BrushSize.MEDIUM)
        private set
    var gridVisible by mutableStateOf(true)
        private set
    var hanzi by mutableStateOf("")
        private set
    var note by mutableStateOf("")
        private set
    var isBlankCanvas by mutableStateOf(true)
        private set
    var undoCount by mutableIntStateOf(0)
        private set
    var saving by mutableStateOf(false)
        private set

    val isEditMode: Boolean get() = editId != null

    /** 编辑模式下是否已加载完原数据（加载完成前禁止保存） */
    var loaded by mutableStateOf(editId == null)
        private set

    private val _savedEvents = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val savedEvents: SharedFlow<Unit> = _savedEvents

    /** 新建模式遇到重复汉字时的待确认信息：(已有数量, 汉字)；非空时 UI 弹确认框 */
    var pendingDuplicate by mutableStateOf<Pair<Int, String>?>(null)
        private set

    /** 画布内容版本号：每次笔画/撤销/清空 +1，用于触发画布重绘 */
    private val _revision = mutableIntStateOf(0)
    val revisionState: State<Int> = _revision

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    val displayBitmap: Bitmap get() = bitmap

    init {
        // 编辑模式：加载原记录与字图
        if (editId != null) {
            viewModelScope.launch {
                val glyph = repository.getById(editId)
                if (glyph == null) {
                    _message.value = "加载失败：该记录不存在"
                    loaded = true
                    return@launch
                }
                repository.loadPixels(editId)?.let { p ->
                    p.copyInto(pixels)
                    redrawBitmapFromPixels()
                }
                hanzi = glyph.hanzi
                note = glyph.note
                loaded = true
            }
        }
    }

    // ---- 操作 ----
    fun chooseTool(value: Tool) { tool = value }
    fun chooseBrushSize(value: BrushSize) { brushSize = value }
    fun toggleGrid() { gridVisible = !gridVisible }

    /**
     * 映射字过滤：可选，允许任意非空白、非控制字符的单个 Unicode 码点字符
     * （汉字/字母/假名/单码点 emoji 均可），多输只取第 1 个有效字符。
     */
    fun updateMapped(raw: String) {
        val first = raw.codePoints().toArray()
            .firstOrNull { !Character.isWhitespace(it) && !Character.isISOControl(it) }
        hanzi = if (first != null) String(Character.toChars(first)) else ""
    }

    fun updateNote(raw: String) {
        note = raw.take(200)
    }

    /** 一笔开始：把当前画布压入撤销栈（真正落笔时才调用，见 GlyphCanvas） */
    fun onStrokeStart() {
        undoStack.addLast(pixels.copyOf())
        if (undoStack.size > MAX_UNDO) undoStack.removeFirst()
        undoCount = undoStack.size
    }

    /**
     * 从格子 (x1,y1) 到 (x2,y2) 画一段。
     * 采用"印章式"绘制：沿轨迹每隔 0.25 格盖一个方形笔头印章，
     * 转弯处也能无缝覆盖，避免线段拼接产生的撕裂纹路；粗细恒定。
     */
    fun onStrokeSegment(x1: Int, y1: Int, x2: Int, y2: Int) {
        val paint = if (tool == Tool.ERASER) whitePaint else blackPaint
        val sizePx = if (tool == Tool.ERASER) brushSize.eraserPx else brushSize.brushPx
        val half = sizePx / 2f
        val dx = (x2 - x1).toFloat()
        val dy = (y2 - y1).toFloat()
        val dist = sqrt(dx * dx + dy * dy)
        val steps = max(1, ceil(dist / STAMP_STEP).toInt())
        for (i in 0..steps) {
            val t = i / steps.toFloat()
            val cx = x1 + dx * t + 0.5f
            val cy = y1 + dy * t + 0.5f
            canvas.drawRect(cx - half, cy - half, cx + half, cy + half, paint)
        }
        _revision.intValue++
    }

    /** 一笔结束：把位图内容同步回像素数组 */
    fun onStrokeEnd() {
        syncPixelsFromBitmap()
    }

    fun undo() {
        if (undoStack.isEmpty()) return
        val snapshot = undoStack.removeLast()
        undoCount = undoStack.size
        snapshot.copyInto(pixels)
        redrawBitmapFromPixels()
        _revision.intValue++
    }

    /** 清空画布（UI 已确认过；空白画布只提示） */
    fun clearCanvas() {
        if (isBlankCanvas) {
            _message.value = "画布已经是空白的"
            return
        }
        onStrokeStart()
        pixels.fill(0x00)
        bitmap.eraseColor(Color.WHITE)
        isBlankCanvas = true
        _revision.intValue++
    }

    /**
     * 请求保存：映射字与备注都为空时提示；新建模式若该映射字已有对应文字，
     * 先让 UI 弹确认框。
     */
    fun requestSave() {
        if (saving || !loaded) return
        if (hanzi.isEmpty() && note.isBlank()) {
            _message.value = "映射字和备注至少填写一个"
            return
        }
        viewModelScope.launch {
            if (editId == null && hanzi.isNotEmpty()) {
                val existing = repository.countByHanzi(hanzi)
                if (existing > 0) {
                    pendingDuplicate = existing to hanzi
                    return@launch
                }
            }
            doSave()
        }
    }

    /** 用户在重复汉字确认框中点了「确认保存」 */
    fun confirmDuplicateSave() {
        pendingDuplicate = null
        viewModelScope.launch { doSave() }
    }

    fun cancelDuplicateSave() {
        pendingDuplicate = null
    }

    private suspend fun doSave() {
        if (saving) return
        saving = true
        runCatching {
            if (editId != null) {
                repository.update(editId, hanzi, note.trim(), pixels.copyOf())
            } else {
                repository.insert(hanzi, note.trim(), pixels.copyOf())
            }
        }
            .onSuccess {
                pixels.fill(0x00)
                bitmap.eraseColor(Color.WHITE)
                isBlankCanvas = true
                undoStack.clear()
                undoCount = 0
                hanzi = ""
                note = ""
                _revision.intValue++
                _message.value = if (editId != null) "已保存修改" else "保存成功"
                _savedEvents.tryEmit(Unit)
            }
            .onFailure { _message.value = "保存失败：${it.message ?: "未知错误"}" }
        saving = false
    }

    fun clearMessage() { _message.value = null }

    // ---- 内部工具 ----
    private fun syncPixelsFromBitmap() {
        val colors = IntArray(GlyphBitmapStore.PIXEL_COUNT)
        bitmap.getPixels(colors, 0, GlyphBitmapStore.SIZE, 0, 0, GlyphBitmapStore.SIZE, GlyphBitmapStore.SIZE)
        var hasBlack = false
        for (i in colors.indices) {
            if (colors[i] == Color.BLACK) {
                pixels[i] = 0xFF.toByte()
                hasBlack = true
            } else {
                pixels[i] = 0x00
            }
        }
        isBlankCanvas = !hasBlack
    }

    private fun redrawBitmapFromPixels() {
        val colors = IntArray(GlyphBitmapStore.PIXEL_COUNT)
        var hasBlack = false
        for (i in colors.indices) {
            if ((pixels[i].toInt() and 0xFF) == 0xFF) {
                colors[i] = Color.BLACK
                hasBlack = true
            } else {
                colors[i] = Color.WHITE
            }
        }
        bitmap.setPixels(colors, 0, GlyphBitmapStore.SIZE, 0, 0, GlyphBitmapStore.SIZE, GlyphBitmapStore.SIZE)
        isBlankCanvas = !hasBlack
    }

    companion object {
        private const val MAX_UNDO = 50

        /** 印章步长（格）：越小笔画越连续 */
        private const val STAMP_STEP = 0.25f
    }
}

