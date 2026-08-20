package com.charactym.app.ui.browse

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
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
import com.charactym.app.ui.common.GlyphListItem
import com.charactym.app.ui.theme.Palette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class BrowseViewModel(repository: GlyphRepository) : ViewModel() {
    val glyphs: StateFlow<List<Glyph>> = repository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
}

/**
 * 浏览页：全部记录列表，按录入时间新→旧；右上角是「管理」入口。
 * 长按进入多选：选中蓝色边框，底部浮出导出胶囊。
 */
@Composable
fun BrowseScreen(
    onOpenDetail: (Long) -> Unit,
    onOpenManage: () -> Unit,
    modifier: Modifier = Modifier,
    bottomInset: Dp = 0.dp,
) {
    val context = LocalContext.current
    val app = remember { CharactymApp.from(context) }
    val vm: BrowseViewModel = viewModel { BrowseViewModel(app.glyphRepository) }
    val glyphs by vm.glyphs.collectAsStateWithLifecycle()

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

    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Charactym · 浏览",
                    style = MaterialTheme.typography.headlineSmall,
                    color = Palette.MainText,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onOpenManage) { Text("⚙️ 管理", color = Palette.BluePrimary) }
            }
            if (glyphs.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        "还没有录入任何文字\n去「录入」页写一个吧！",
                        color = Palette.GrayText,
                        textAlign = TextAlign.Center,
                        lineHeight = 22.sp,
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 80.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(glyphs, key = { it.id }) { glyph ->
                        GlyphListItem(
                            glyph = glyph,
                            selected = selectionMode && glyph.id in selected,
                            onClick = { if (selectionMode) toggle(glyph.id) else onOpenDetail(glyph.id) },
                            onLongClick = {
                                if (!selectionMode) {
                                    selectionMode = true
                                    selected.add(glyph.id)
                                }
                            },
                        )
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
