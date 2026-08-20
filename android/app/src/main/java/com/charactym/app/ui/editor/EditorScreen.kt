package com.charactym.app.ui.editor

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.charactym.app.CharactymApp
import com.charactym.app.ui.theme.Palette

// 设计规范色板（阶段 6 会整合进主题）
private val BluePrimary = Palette.BluePrimary
private val BlueLight = Palette.BlueLight
private val DangerRed = Palette.DangerRed
private val GrayText = Palette.GrayText
private val BorderGray = Palette.BorderGray
private val MainText = Palette.MainText

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    glyphId: Long? = null,
    onSaved: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val vm: EditorViewModel = viewModel { EditorViewModel(CharactymApp.from(context).glyphRepository, glyphId) }

    var showClearConfirm by remember { mutableStateOf(false) }
    var showBlankSaveConfirm by remember { mutableStateOf(false) }
    var resetKey by remember { mutableIntStateOf(0) }

    // 用 collectAsState 观察消息流：消息一变立即弹提示（直接读 .value 不会被 Compose 感知）
    val message by vm.message.collectAsStateWithLifecycle()
    LaunchedEffect(message) {
        message?.let { m ->
            Toast.makeText(context, m, Toast.LENGTH_SHORT).show()
            vm.clearMessage()
        }
    }

    // 编辑模式：保存成功后通知外部（返回上一页）
    LaunchedEffect(Unit) {
        vm.savedEvents.collect {
            if (glyphId != null) onSaved?.invoke()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(if (glyphId != null) "Charactym · 编辑" else "Charactym · 录入", style = MaterialTheme.typography.headlineSmall, color = MainText)
        Text("在画布上书写您的文字（256×256 黑白）", style = MaterialTheme.typography.bodySmall, color = GrayText)

        GlyphCanvas(
            image = vm.displayBitmap.asImageBitmap(),
            revision = vm.revisionState,
            guidesVisible = vm.gridVisible,
            resetKey = resetKey,
            onStrokeStart = vm::onStrokeStart,
            onStrokeSegment = vm::onStrokeSegment,
            onStrokeEnd = vm::onStrokeEnd,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .background(Color.White)
                .border(1.dp, BorderGray),
        )

        // 工具行
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ToolButton("画笔", selected = vm.tool == Tool.BRUSH, accent = BluePrimary, modifier = Modifier.weight(1f)) { vm.chooseTool(Tool.BRUSH) }
            ToolButton("橡皮", selected = vm.tool == Tool.ERASER, accent = BluePrimary, modifier = Modifier.weight(1f)) { vm.chooseTool(Tool.ERASER) }
            ToolButton("撤销", selected = false, accent = BluePrimary, enabled = vm.undoCount > 0, modifier = Modifier.weight(1f)) { vm.undo() }
            ToolButton("清空", selected = false, accent = DangerRed, modifier = Modifier.weight(1f)) {
                if (vm.isBlankCanvas) {
                    vm.clearCanvas() // 空白时仅提示
                } else {
                    showClearConfirm = true
                }
            }
        }

        // 笔刷三档
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("笔刷", color = MainText, fontSize = 14.sp)
            BrushSize.entries.forEach { b ->
                FilterChip(
                    selected = vm.brushSize == b,
                    onClick = { vm.chooseBrushSize(b) },
                    label = {
                        Text(
                            if (vm.tool == Tool.ERASER) "${b.label}(${b.eraserPx}px)" else "${b.label}(${b.brushPx}px)",
                        )
                    },
                    colors = androidx.compose.material3.FilterChipDefaults.filterChipColors(
                        selectedContainerColor = BlueLight.copy(alpha = 0.3f),
                    ),
                )
            }
        }

        // 辅助线开关 + 视图复位（独立一行，互不遮挡）
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("辅助线", color = MainText, fontSize = 14.sp)
            Switch(checked = vm.gridVisible, onCheckedChange = { vm.toggleGrid() })
            Spacer(modifier = Modifier.weight(1f))
            TextButton(onClick = { resetKey++ }) { Text("视图复位（铺满居中）", color = BluePrimary) }
        }

        // 输入区
        OutlinedTextField(
            value = vm.hanzi,
            onValueChange = vm::updateMapped,
            label = { Text("映射字（可选）") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = vm.note,
            onValueChange = vm::updateNote,
            label = { Text("备注") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 2,
            maxLines = 4,
        )

        Button(
            onClick = {
                when {
                    vm.hanzi.isEmpty() && vm.note.isBlank() -> vm.requestSave() // 弹出"至少填写一个"
                    vm.isBlankCanvas -> showBlankSaveConfirm = true
                    else -> vm.requestSave()
                }
            },
            enabled = !vm.saving && vm.loaded,
            modifier = Modifier.fillMaxWidth().height(48.dp),
            colors = ButtonDefaults.buttonColors(containerColor = BluePrimary),
            shape = RoundedCornerShape(8.dp),
        ) {
            Text(
                if (vm.saving) "保存中…"
                else if (glyphId != null) "保存修改"
                else "保 存",
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
            )
        }
    }

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text("清空画布") },
            text = { Text("确定清空画布吗？清空后可以用「撤销」找回。") },
            confirmButton = {
                TextButton(onClick = { showClearConfirm = false; vm.clearCanvas() }) {
                    Text("清空", color = DangerRed)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) { Text("取消") }
            },
        )
    }

    if (showBlankSaveConfirm) {
        AlertDialog(
            onDismissRequest = { showBlankSaveConfirm = false },
            title = { Text("保存空白文字？") },
            text = { Text("画布还是空白的，确定要保存吗？") },
            confirmButton = {
                TextButton(onClick = { showBlankSaveConfirm = false; vm.requestSave() }) {
                    Text("保存", color = BluePrimary)
                }
            },
            dismissButton = {
                TextButton(onClick = { showBlankSaveConfirm = false }) { Text("取消") }
            },
        )
    }

    // 重复映射字符确认：该映射字符已对应多个人造字时，需用户确认才保存新写法
    val duplicate = vm.pendingDuplicate
    if (duplicate != null) {
        AlertDialog(
            onDismissRequest = vm::cancelDuplicateSave,
            title = { Text("该映射字符已有对应人造文字") },
            text = {
                Text("该映射字符已有对应人造文字，字符「${duplicate.second}」已对应 ${duplicate.first} 个人造字。\n您仍要保存这个新的写法吗？")
            },
            confirmButton = {
                TextButton(onClick = vm::confirmDuplicateSave) { Text("确认保存", color = BluePrimary) }
            },
            dismissButton = {
                TextButton(onClick = vm::cancelDuplicateSave) { Text("取消") }
            },
        )
    }
}

@Composable
private fun ToolButton(
    label: String,
    selected: Boolean,
    accent: Color,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(40.dp),
        border = BorderStroke(1.dp, if (selected) accent else BorderGray),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = if (selected) BlueLight.copy(alpha = 0.25f) else Color.White,
            contentColor = if (selected) accent else MainText,
        ),
        shape = RoundedCornerShape(8.dp),
        contentPadding = PaddingValues(horizontal = 4.dp),
    ) {
        Text(label, fontSize = 13.sp)
    }
}
