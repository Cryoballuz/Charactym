package com.charactym.app.ui.detail

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.charactym.app.CharactymApp
import com.charactym.app.ui.formatDate
import com.charactym.app.ui.theme.Palette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 详情页：大字图 + 汉字 + 完整备注 + 编辑/删除。
 * 数据实时观察，从编辑页返回后自动刷新。
 */
@Composable
fun DetailScreen(
    glyphId: Long,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val app = remember { CharactymApp.from(context) }
    val repository = app.glyphRepository
    val glyph by repository.observeGlyph(glyphId).collectAsStateWithLifecycle(initialValue = null)
    val scope = rememberCoroutineScope()

    var showDeleteConfirm by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    var exporting by remember { mutableStateOf(false) }

    // 字图随 updatedAt 变化重新加载（编辑重画后自动刷新）
    val image by produceState<ImageBitmap?>(null, glyphId, glyph?.updatedAt) {
        value = withContext(Dispatchers.IO) { repository.loadBitmap(glyphId)?.asImageBitmap() }
    }

    Column(modifier = modifier.fillMaxSize()) {
        // 顶部标题栏
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onBack) { Text("← 返回", color = Palette.BluePrimary) }
            Spacer(modifier = Modifier.weight(1f))
            Text(
                if (!glyph?.hanzi.isNullOrEmpty()) "「${glyph?.hanzi}」详情" else "详情",
                color = Palette.MainText,
                fontWeight = FontWeight.Medium,
            )
            Spacer(modifier = Modifier.weight(1f))
            Spacer(modifier = Modifier.width(64.dp))
        }

        if (glyph == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("加载中…", color = Palette.GrayText)
            }
        } else {
            val g = glyph!!
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .background(Color.White)
                        .border(1.dp, Palette.BorderGray),
                ) {
                    image?.let {
                        Image(bitmap = it, contentDescription = "大字图", modifier = Modifier.fillMaxSize())
                    }
                }
                // 无映射字的记录：映射字位置留空
                if (g.hanzi.isNotEmpty()) {
                    Text(
                        g.hanzi,
                        fontSize = 56.sp,
                        color = Palette.MainText,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }
                Text("录入于 ${formatDate(g.createdAt)}", fontSize = 12.sp, color = Palette.GrayText)

                Text(
                    "备注",
                    fontSize = 13.sp,
                    color = Palette.GrayText,
                    modifier = Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 6.dp),
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.White)
                        .border(1.dp, Palette.BorderGray, RoundedCornerShape(8.dp))
                        .padding(12.dp),
                ) {
                    Text(
                        if (g.note.isBlank()) "（无备注）" else g.note,
                        fontSize = 15.sp,
                        color = if (g.note.isBlank()) Palette.GrayText else Palette.MainText,
                        lineHeight = 22.sp,
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = onEdit,
                    modifier = Modifier.weight(1f).height(44.dp),
                    border = BorderStroke(1.dp, Palette.BluePrimary),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Palette.BluePrimary),
                    shape = RoundedCornerShape(8.dp),
                ) { Text("编辑") }
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            exporting = true
                            val ok = withContext(Dispatchers.IO) {
                                app.dataTransferManager.exportSingleToDownloads(glyphId)
                            }
                            exporting = false
                            Toast.makeText(
                                context,
                                if (ok) "已导出 PNG 到「下载/Charactym」" else "导出失败",
                                Toast.LENGTH_SHORT,
                            ).show()
                        }
                    },
                    enabled = !exporting,
                    modifier = Modifier.weight(1.4f).height(44.dp),
                    border = BorderStroke(1.dp, Palette.BluePrimary),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Palette.BluePrimary),
                    shape = RoundedCornerShape(8.dp),
                ) { Text(if (exporting) "导出中…" else "导出PNG", fontSize = 13.sp) }
                OutlinedButton(
                    onClick = { showDeleteConfirm = true },
                    enabled = !deleting,
                    modifier = Modifier.weight(1f).height(44.dp),
                    border = BorderStroke(1.dp, Palette.DangerRed),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Palette.DangerRed),
                    shape = RoundedCornerShape(8.dp),
                ) { Text(if (deleting) "删除中…" else "删除") }
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("删除该文字？") },
            text = {
                Text(
                    if (glyph?.hanzi.isNullOrEmpty()) "将删除该文字及其字图，删除后不可恢复。"
                    else "将删除「${glyph?.hanzi}」及其字图，删除后不可恢复。",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    deleting = true
                    scope.launch {
                        repository.delete(glyphId)
                        onBack()
                    }
                }) { Text("删除", color = Palette.DangerRed) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("取消") }
            },
        )
    }
}
