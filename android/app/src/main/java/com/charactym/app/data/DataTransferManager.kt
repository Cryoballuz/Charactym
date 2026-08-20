package com.charactym.app.data

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.charactym.app.data.local.Glyph
import com.charactym.app.data.local.GlyphDao
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * 导出 PNG 与整体备份/恢复。
 *
 * - 导出位置：系统「下载/Charactym」文件夹（Android 10+ 走 MediaStore，无需任何权限；
 *   更旧系统直接写公共下载目录）。
 * - 备份文件：zip 包，内含 manifest.json（版本信息）、glyphs.json（文字数据）、
 *   images/<id>.png（字图），恢复时校验版本后覆盖导入。
 */
class DataTransferManager(
    private val context: Context,
    private val dao: GlyphDao,
) {

    private val json = Json { ignoreUnknownKeys = true }
    private val glyphDir = File(context.filesDir, "glyphs").apply { mkdirs() }

    /** 导出全部字图为 PNG 到下载文件夹，返回导出数量 */
    suspend fun exportAllToDownloads(): Int = withContext(Dispatchers.IO) {
        val glyphs = dao.getAllOnce()
        glyphs.forEach { g ->
            val src = File(g.pngPath)
            if (src.exists()) {
                // 无映射字的记录：文件名只用编号
                val name = if (g.hanzi.isNotBlank()) "${g.hanzi}_${g.id}.png" else "${g.id}.png"
                writeToDownloads(name, "image/png") { out ->
                    src.inputStream().use { it.copyTo(out) }
                }
            }
        }
        glyphs.size
    }

    /** 导出单张字图为 PNG，返回是否成功 */
    suspend fun exportSingleToDownloads(glyphId: Long): Boolean = withContext(Dispatchers.IO) {
        val g = dao.getById(glyphId) ?: return@withContext false
        val src = File(g.pngPath)
        if (!src.exists()) return@withContext false
        val name = if (g.hanzi.isNotBlank()) "${g.hanzi}_${g.id}.png" else "${g.id}.png"
        writeToDownloads(name, "image/png") { out ->
            src.inputStream().use { it.copyTo(out) }
        }
        true
    }

    /** 多选导出：把指定 id 的字图全部导出到下载文件夹，返回实际导出数量 */
    suspend fun exportGlyphsToDownloads(ids: List<Long>): Int = withContext(Dispatchers.IO) {
        if (ids.isEmpty()) return@withContext 0
        var count = 0
        dao.getByIds(ids).forEach { g ->
            val src = File(g.pngPath)
            if (src.exists()) {
                val name = if (g.hanzi.isNotBlank()) "${g.hanzi}_${g.id}.png" else "${g.id}.png"
                writeToDownloads(name, "image/png") { out ->
                    src.inputStream().use { it.copyTo(out) }
                }
                count++
            }
        }
        count
    }

    /** 创建整体备份 zip 到下载文件夹，返回备份文件名（失败返回 null） */
    suspend fun createBackupToDownloads(): String? = withContext(Dispatchers.IO) {
        runCatching {
            val glyphs = dao.getAllOnce()
            val stamp = SimpleDateFormat("yyyyMMdd-HHmm", Locale.getDefault()).format(Date())
            val name = "Charactym备份-$stamp.zip"
            val manifest = BackupManifest(glyphCount = glyphs.size, createdAt = System.currentTimeMillis())
            val backupGlyphs = glyphs.map {
                BackupGlyph(it.id, it.hanzi, it.note, it.createdAt, it.updatedAt)
            }

            writeToDownloads(name, "application/zip") { out ->
                ZipOutputStream(out).use { zip ->
                    zip.putNextEntry(ZipEntry("manifest.json"))
                    zip.write(json.encodeToString(BackupManifest.serializer(), manifest).toByteArray(Charsets.UTF_8))
                    zip.closeEntry()

                    zip.putNextEntry(ZipEntry("glyphs.json"))
                    zip.write(
                        json.encodeToString(ListSerializer(BackupGlyph.serializer()), backupGlyphs)
                            .toByteArray(Charsets.UTF_8),
                    )
                    zip.closeEntry()

                    glyphs.forEach { g ->
                        val f = File(g.pngPath)
                        if (f.exists()) {
                            zip.putNextEntry(ZipEntry("images/${g.id}.png"))
                            f.inputStream().use { it.copyTo(zip) }
                            zip.closeEntry()
                        }
                    }
                }
            }
            name
        }.getOrNull()
    }

    /** 从备份 zip 恢复（覆盖当前全部数据），返回恢复条数 */
    suspend fun restoreFromUri(uri: Uri): Int = withContext(Dispatchers.IO) {
        var manifest: BackupManifest? = null
        var glyphs: List<BackupGlyph>? = null
        val images = mutableMapOf<Long, ByteArray>()

        context.contentResolver.openInputStream(uri)?.use { input ->
            ZipInputStream(input).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    val bytes = zip.readBytes()
                    when {
                        entry.name == "manifest.json" ->
                            manifest = json.decodeFromString(BackupManifest.serializer(), bytes.toString(Charsets.UTF_8))
                        entry.name == "glyphs.json" ->
                            glyphs = json.decodeFromString(
                                ListSerializer(BackupGlyph.serializer()),
                                bytes.toString(Charsets.UTF_8),
                            )
                        entry.name.startsWith("images/") ->
                            entry.name.removePrefix("images/").removeSuffix(".png").toLongOrNull()
                                ?.let { images[it] = bytes }
                    }
                    entry = zip.nextEntry
                }
            }
        } ?: error("无法读取备份文件")

        val m = checkNotNull(manifest) { "备份文件缺少 manifest.json" }
        check(m.formatVersion == BackupManifest.FORMAT_VERSION) { "不支持的备份格式版本：${m.formatVersion}" }
        val list = checkNotNull(glyphs) { "备份文件缺少 glyphs.json" }
        check(list.isNotEmpty()) { "备份中没有文字数据" }

        // 覆盖式恢复：先清空现有数据与图片
        dao.getAllOnce().forEach { File(it.pngPath).delete() }
        dao.deleteAll()

        list.forEach { bg ->
            val png = images[bg.id] ?: error("备份缺少图片：${bg.id}.png")
            val path = File(glyphDir, "${bg.id}.png").apply { writeBytes(png) }
            dao.insert(
                Glyph(
                    id = bg.id,
                    hanzi = bg.hanzi,
                    note = bg.note,
                    pngPath = path.absolutePath,
                    createdAt = bg.createdAt,
                    updatedAt = bg.updatedAt,
                ),
            )
        }
        list.size
    }

    private fun writeToDownloads(name: String, mime: String, writer: (java.io.OutputStream) -> Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, name)
                put(MediaStore.Downloads.MIME_TYPE, mime)
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/Charactym")
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: error("无法创建下载文件")
            context.contentResolver.openOutputStream(uri)?.use { writer(it) }
                ?: error("无法打开下载文件")
            val done = ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }
            context.contentResolver.update(uri, done, null, null)
        } else {
            // 旧系统：直接写入公共下载目录
            val dir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                "Charactym",
            ).apply { mkdirs() }
            FileOutputStream(File(dir, name)).use { writer(it) }
        }
    }
}
