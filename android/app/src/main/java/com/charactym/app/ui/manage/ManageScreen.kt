package com.charactym.app.ui.manage

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.charactym.app.CharactymApp
import com.charactym.app.data.BatchImportManager
import com.charactym.app.data.DataTransferManager
import com.charactym.app.data.GlyphRepository
import com.charactym.app.ui.theme.Palette
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ManageViewModel(
    private val repository: GlyphRepository,
    private val transfer: DataTransferManager,
    private val importer: BatchImportManager,
) : ViewModel() {

    val count: StateFlow<Int> = repository.observeAll()
        .map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    var busy by androidx.compose.runtime.mutableStateOf(false)
        private set

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private val _importResult = MutableStateFlow<BatchImportManager.ImportResult?>(null)
    val importResult: StateFlow<BatchImportManager.ImportResult?> = _importResult.asStateFlow()

    fun clearMessage() {
        _message.value = null
    }

    fun clearImportResult() {
        _importResult.value = null
    }

    fun exportAll() {
        if (busy) return
        viewModelScope.launch {
            busy = true
            runCatching { transfer.exportAllToDownloads() }
                .onSuccess { n ->
                    _message.value =
                        if (n == 0) "没有可导出的文字" else "已导出 $n 张 PNG 到「下载/Charactym」文件夹"
                }
                .onFailure { _message.value = "导出失败：${it.message ?: "未知错误"}" }
            busy = false
        }
    }

    fun createBackup() {
        if (busy) return
        viewModelScope.launch {
            busy = true
            runCatching { transfer.createBackupToDownloads() }
                .onSuccess { name ->
                    _message.value = name?.let { "备份已保存到「下载/Charactym」：$it" } ?: "备份失败"
                }
                .onFailure { _message.value = "备份失败：${it.message ?: "未知错误"}" }
            busy = false
        }
    }

    fun restore(uri: Uri) {
        if (busy) return
        viewModelScope.launch {
            busy = true
            runCatching { transfer.restoreFromUri(uri) }
                .onSuccess { n -> _message.value = "恢复成功：共导入 $n 条文字" }
                .onFailure { _message.value = "恢复失败：${it.message ?: "未知错误"}" }
            busy = false
        }
    }

    /** 批量导入图片：filenameAsHanzi=true 文件名作映射字，false 文件名作备注 */
    fun importImages(uris: List<Uri>, filenameAsHanzi: Boolean) {
        if (busy) return
        viewModelScope.launch {
            busy = true
            runCatching { importer.importImages(uris, filenameAsHanzi) }
                .onSuccess { _importResult.value = it }
                .onFailure { _message.value = "导入失败：${it.message ?: "未知错误"}" }
            busy = false
        }
    }
}

/**
 * 管理页：统计、导出全部 PNG、批量导入图片、整体备份、从备份恢复。
 * 入口在浏览页右上角「⚙️ 管理」。
 */
@Composable
fun ManageScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val app = remember { CharactymApp.from(context) }
    val vm: ManageViewModel = viewModel {
        ManageViewModel(app.glyphRepository, app.dataTransferManager, app.batchImportManager)
    }
    val count by vm.count.collectAsStateWithLifecycle()
    var showRestoreConfirm by remember { mutableStateOf(false) }
    var pickerKey by remember { mutableIntStateOf(0) }
    var showImportModeDialog by remember { mutableStateOf(false) }
    var pendingFilenameAsHanzi by remember { mutableStateOf(true) }
    var importReport by remember { mutableStateOf<BatchImportManager.ImportResult?>(null) }

    // 消息提示
    val message by vm.message.collectAsStateWithLifecycle()
    LaunchedEffect(message) {
        message?.let { m ->
            Toast.makeText(context, m, Toast.LENGTH_SHORT).show()
            vm.clearMessage()
        }
    }

    // 批量导入结果：全部成功弹 Toast；有跳过则弹报告框
    val importResult by vm.importResult.collectAsStateWithLifecycle()
    LaunchedEffect(importResult) {
        importResult?.let { r ->
            if (r.skipped.isEmpty()) {
                Toast.makeText(context, "批量导入成功：共 ${r.imported} 张", Toast.LENGTH_SHORT).show()
            } else {
                importReport = r
            }
            vm.clearImportResult()
        }
    }

    // 批量导入的多选图片选择器
    val importPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris ->
        if (uris.isNotEmpty()) vm.importImages(uris, pendingFilenameAsHanzi)
    }

    Column(
        modifier = modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("← 返回", color = Palette.BluePrimary) }
            Text("Charactym · 管理", style = MaterialTheme.typography.headlineSmall, color = Palette.MainText)
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White)
                .border(1.dp, Palette.BorderGray, RoundedCornerShape(8.dp))
                .padding(16.dp),
        ) {
            Text(
                "当前文字库共 $count 个字",
                fontSize = 16.sp,
                color = Palette.MainText,
            )
        }

        Button(
            onClick = vm::exportAll,
            enabled = !vm.busy && count > 0,
            modifier = Modifier.fillMaxWidth().height(48.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Palette.BluePrimary),
            shape = RoundedCornerShape(8.dp),
        ) {
            Text(if (vm.busy) "处理中…" else "导出全部 PNG 图片", fontSize = 15.sp, fontWeight = FontWeight.Medium)
        }

        OutlinedButton(
            onClick = { showImportModeDialog = true },
            enabled = !vm.busy,
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = RoundedCornerShape(8.dp),
        ) {
            Text(if (vm.busy) "处理中…" else "批量导入图片（256×256）", fontSize = 15.sp, color = Palette.BluePrimary)
        }

        Button(
            onClick = vm::createBackup,
            enabled = !vm.busy,
            modifier = Modifier.fillMaxWidth().height(48.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Palette.BluePrimary),
            shape = RoundedCornerShape(8.dp),
        ) {
            Text(if (vm.busy) "处理中…" else "创建整体备份文件", fontSize = 15.sp, fontWeight = FontWeight.Medium)
        }

        OutlinedButton(
            onClick = { showRestoreConfirm = true },
            enabled = !vm.busy,
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = RoundedCornerShape(8.dp),
        ) {
            Text("从备份文件恢复", fontSize = 15.sp, color = Palette.BluePrimary)
        }

        Text(
            "说明：导出的 PNG 图片和备份文件都保存在手机「下载/Charactym」文件夹中。" +
                "备份文件包含全部文字与字图，可用于换手机迁移，或误删数据后恢复。" +
                "恢复会用备份内容覆盖当前全部数据，请谨慎操作。" +
                "批量导入仅接受 256×256 的正方形图片，非黑白图片按 50% 亮度转黑白。",
            fontSize = 12.sp,
            color = Palette.GrayText,
            lineHeight = 18.sp,
        )
    }

    if (showRestoreConfirm) {
        AlertDialog(
            onDismissRequest = { showRestoreConfirm = false },
            title = { Text("从备份恢复？") },
            text = { Text("恢复将用备份文件的内容覆盖当前全部数据，且无法撤销。确定继续吗？") },
            confirmButton = {
                TextButton(onClick = {
                    showRestoreConfirm = false
                    pickerKey++
                }) { Text("继续", color = Palette.BluePrimary) }
            },
            dismissButton = {
                TextButton(onClick = { showRestoreConfirm = false }) { Text("取消") }
            },
        )
    }

    // 选择备份文件（SAF 文件选择器，无需任何权限）
    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) vm.restore(uri)
    }
    LaunchedEffect(pickerKey) {
        if (pickerKey > 0) {
            picker.launch(arrayOf("application/zip", "application/octet-stream", "*/*"))
        }
    }

    // 批量导入模式选择
    if (showImportModeDialog) {
        AlertDialog(
            onDismissRequest = { showImportModeDialog = false },
            title = { Text("选择导入方式") },
            text = { Text("图片的文件名（去掉扩展名）将如何处理？\n· 作为映射字：文件名须是单个字符\n· 作为备注：文件名任意长度") },
            confirmButton = {
                TextButton(onClick = {
                    showImportModeDialog = false
                    pendingFilenameAsHanzi = true
                    importPicker.launch(arrayOf("image/*"))
                }) { Text("文件名 → 映射字", color = Palette.BluePrimary) }
            },
            dismissButton = {
                TextButton(onClick = {
                    showImportModeDialog = false
                    pendingFilenameAsHanzi = false
                    importPicker.launch(arrayOf("image/*"))
                }) { Text("文件名 → 备注") }
            },
        )
    }

    // 批量导入结果报告（有跳过时，可上下滚动查看全部）
    val report = importReport
    if (report != null) {
        AlertDialog(
            onDismissRequest = { importReport = null },
            title = { Text("批量导入完成") },
            text = {
                // 注意顺序：heightIn 在外约束视口高度，verticalScroll 在内让内容溢出时可滚动
                Column(
                    modifier = Modifier
                        .heightIn(max = 320.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    val detail = report.skipped.joinToString("\n") { "· ${it.first}：${it.second}" }
                    Text("成功导入 ${report.imported} 张\n跳过 ${report.skipped.size} 张：\n$detail")
                }
            },
            confirmButton = {
                TextButton(onClick = { importReport = null }) { Text("知道了", color = Palette.BluePrimary) }
            },
        )
    }
}
