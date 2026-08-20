package com.charactym.app.ui.mapping

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.charactym.app.CharactymApp
import com.charactym.app.data.GlyphRepository
import com.charactym.app.data.local.Glyph
import com.charactym.app.ui.common.FloatingExportPill
import com.charactym.app.ui.theme.Palette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.floor

@OptIn(ExperimentalCoroutinesApi::class)
class MappingViewModel(repository: GlyphRepository) : ViewModel() {

    private val _text = MutableStateFlow("")
    val text: StateFlow<String> = _text.asStateFlow()

    /** 输入串中出现的映射字对应的全部文字（编号升序） */
    val matchedGlyphs: StateFlow<List<Glyph>> = _text
        .flatMapLatest { t ->
            val chars = t.codePoints().toArray()
                .map { String(Character.toChars(it)) }
                .distinct()
            if (chars.isEmpty()) flowOf(emptyList()) else repository.observeByHanziIn(chars)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun updateText(t: String) {
        _text.value = t.take(200)
    }

    fun clearText() {
        _text.value = ""
    }
}

/** 渲染槽位：无对应字 = 淡红占位；有对应字 = 字图（多字时盖淡蓝蒙版） */
private sealed interface Slot {
    data object Placeholder : Slot
    data class GlyphSlot(val glyph: Glyph, val isVariant: Boolean) : Slot
}

/**
 * 映射页：输入一串字符，按顺序排出每个字符所映射的人造文字。
 * 支持横排（自动换行、上下滚动）与竖排（列满换列、左右滑动），
 * 以及从左到右 / 从右到左方向切换（竖排时控制列方向：左起=蒙古文式，右起=汉字书法式）。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun MappingScreen(
    modifier: Modifier = Modifier,
    bottomInset: Dp = 0.dp,
) {
    val context = LocalContext.current
    val app = remember { CharactymApp.from(context) }
    val vm: MappingViewModel = viewModel { MappingViewModel(app.glyphRepository) }
    val text by vm.text.collectAsStateWithLifecycle()
    val glyphs by vm.matchedGlyphs.collectAsStateWithLifecycle()

    var horizontal by rememberSaveable { mutableStateOf(true) }
    var ltr by rememberSaveable { mutableStateOf(true) }

    // 多选状态（仅实际存在的字可选，占位不可选）
    var selectionMode by rememberSaveable { mutableStateOf(false) }
    val selected = remember { mutableStateListOf<Long>() }
    val scope = rememberCoroutineScope()

    // 胶囊与输入法：键盘弹出时贴到键盘上方约 12dp（扣除已占掉的底栏高度）；
    // 下限取静止位 20dp——键盘收起时胶囊提前滑到静止位停住，无瞬移
    val density = LocalDensity.current
    val imePx = WindowInsets.ime.getBottom(density)
    val pillBottomPad = if (imePx > 0) {
        with(density) { (((imePx - bottomInset.toPx()) / density.density) + 12f).dp.coerceAtLeast(20.dp) }
    } else 20.dp

    fun exitSelection() {
        selectionMode = false
        selected.clear()
    }

    fun toggle(id: Long) {
        if (id in selected) selected.remove(id) else selected.add(id)
    }

    BackHandler(enabled = selectionMode) { exitSelection() }

    // 书写方向：交给布局系统的 RTL 机制，字符顺序始终按输入顺序
    val direction = if (ltr) LayoutDirection.Ltr else LayoutDirection.Rtl

    val byHanzi = remember(glyphs) { glyphs.groupBy { it.hanzi } }
    val slots = remember(text, byHanzi) {
        text.codePoints().toArray().flatMap { cp ->
            val c = String(Character.toChars(cp))
            val gs = byHanzi[c].orEmpty()
            if (gs.isEmpty()) listOf(Slot.Placeholder) else gs.map { Slot.GlyphSlot(it, gs.size > 1) }
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
        Text(
            "Charactym · 映射",
            style = MaterialTheme.typography.headlineSmall,
            color = Palette.MainText,
            modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 8.dp),
        )
        OutlinedTextField(
            value = text,
            onValueChange = vm::updateText,
            singleLine = true,
            placeholder = { Text("输入字符以映射") },
            trailingIcon = {
                if (text.isNotEmpty()) {
                    TextButton(onClick = vm::clearText) { Text("清空", color = Palette.BluePrimary) }
                }
            },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        )

        // 排列与方向切换
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilterChip(selected = horizontal, onClick = { horizontal = true }, label = { Text("横排") })
            FilterChip(selected = !horizontal, onClick = { horizontal = false }, label = { Text("竖排") })
            Spacer(modifier = Modifier.weight(1f))
            FilterChip(selected = ltr, onClick = { ltr = true }, label = { Text("从左到右") })
            FilterChip(selected = !ltr, onClick = { ltr = false }, label = { Text("从右到左") })
        }

        if (text.isBlank()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "输入一串字符，下方会按顺序排出\n每个字符所映射的人造文字",
                    color = Palette.GrayText,
                    textAlign = TextAlign.Center,
                    lineHeight = 22.sp,
                    modifier = Modifier.padding(horizontal = 32.dp),
                )
            }
        } else if (horizontal) {
            // 横排：自动换行，上下滚动。方向由布局方向控制：
            // 从右到左 = 从右上角开始向左排，换行后仍从右往左。
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 4.dp),
            ) {
                val square = squareSize()
                CompositionLocalProvider(LocalLayoutDirection provides direction) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        slots.forEach { slot ->
                            SlotBox(
                                slot = slot,
                                size = square,
                                selected = selectionMode && slot is Slot.GlyphSlot && slot.glyph.id in selected,
                                onClick = { if (selectionMode && slot is Slot.GlyphSlot) toggle(slot.glyph.id) },
                                onLongClick = {
                                    if (!selectionMode && slot is Slot.GlyphSlot) {
                                        selectionMode = true
                                        selected.add(slot.glyph.id)
                                    }
                                },
                            )
                        }
                    }
                }
            }
        } else {
            // 竖排：列满换列，整体左右滑动。方向由布局方向控制：
            // 从右到左 = 第一列在右上角、从上往下、向左换列（汉字书法式）。
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val square = squareSize()
                val perColumn = ((maxHeight - 24.dp) / (square + 2.dp)).toInt().coerceAtLeast(1)
                val columns = slots.chunked(perColumn)
                CompositionLocalProvider(LocalLayoutDirection provides direction) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                    ) {
                        columns.forEach { col ->
                            Column(
                                modifier = Modifier.padding(end = 2.dp),
                                verticalArrangement = Arrangement.spacedBy(2.dp),
                            ) {
                                col.forEach { slot ->
                                    SlotBox(
                                        slot = slot,
                                        size = square,
                                        selected = selectionMode && slot is Slot.GlyphSlot && slot.glyph.id in selected,
                                        onClick = { if (selectionMode && slot is Slot.GlyphSlot) toggle(slot.glyph.id) },
                                        onLongClick = {
                                            if (!selectionMode && slot is Slot.GlyphSlot) {
                                                selectionMode = true
                                                selected.add(slot.glyph.id)
                                            }
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        }

        if (selectionMode) {
            FloatingExportPill(
                count = selected.size,
                onCancel = ::exitSelection,
                onExport = {
                    scope.launch {
                        val n = withContext(Dispatchers.IO) {
                            app.dataTransferManager.exportGlyphsToDownloads(selected.toList())
                        }
                        Toast.makeText(
                            context,
                            if (n > 0) "已导出 $n 张 PNG 到「下载/Charactym」" else "导出失败",
                            Toast.LENGTH_SHORT,
                        ).show()
                        exitSelection()
                    }
                },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = pillBottomPad),
            )
        }
    }
}

/**
 * 一行 8 个方块：按像素向下取整计算尺寸，避免 dp 四舍五入累计导致第 8 个被挤到下一行。
 */
@Composable
private fun BoxWithConstraintsScope.squareSize(): Dp {
    val density = LocalDensity.current
    return with(density) {
        val gapPx = 2.dp.toPx()
        val wPx = maxWidth.toPx()
        val itemPx = floor((wPx - 7 * gapPx) / 8.0).toInt()
        (itemPx / density.density).dp
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SlotBox(
    slot: Slot,
    size: Dp,
    selected: Boolean = false,
    onClick: () -> Unit = {},
    onLongClick: () -> Unit = {},
) {
    when (slot) {
        is Slot.Placeholder -> {
            // 无对应字：淡红色占位方块（不可选）
            Box(modifier = Modifier.size(size).background(Palette.PlaceholderRed))
        }
        is Slot.GlyphSlot -> {
            val context = LocalContext.current
            val repository = remember { CharactymApp.from(context).glyphRepository }
            val bmp by produceState<ImageBitmap?>(null, slot.glyph.id, slot.glyph.pngPath) {
                value = withContext(Dispatchers.IO) { repository.loadBitmap(slot.glyph.id)?.asImageBitmap() }
            }
            Box(
                modifier = Modifier
                    .size(size)
                    .background(Color.White)
                    .then(if (selected) Modifier.border(2.dp, Palette.BluePrimary) else Modifier)
                    .combinedClickable(onClick = onClick, onLongClick = onLongClick),
            ) {
                bmp?.let { Image(bitmap = it, contentDescription = null, modifier = Modifier.fillMaxSize()) }
                // 一个字符对应多个字：盖淡蓝蒙版区分
                if (slot.isVariant) {
                    Box(modifier = Modifier.fillMaxSize().background(Palette.VariantMaskBlue))
                }
            }
        }
    }
}
