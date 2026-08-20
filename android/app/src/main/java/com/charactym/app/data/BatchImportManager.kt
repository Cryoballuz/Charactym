package com.charactym.app.data

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 批量导入 256×256 图片：
 * - 模式一：文件名（去扩展名）作为映射字，要求是单个字符，否则跳过；
 * - 模式二：文件名（去扩展名）作为备注；
 * - 尺寸不是 256×256 的跳过并记录原因（不擅自缩放，保证像素忠实）；
 * - 颜色按亮度阈值 50% 转黑白（亮度 < 128 视为黑，否则白）；
 * - 直接入库（不弹重复确认），编号沿用最小空缺规则。
 */
class BatchImportManager(
    private val context: Context,
    private val repository: GlyphRepository,
) {

    data class ImportResult(
        val imported: Int,
        val skipped: List<Pair<String, String>>, // (文件名, 原因)
    )

    suspend fun importImages(uris: List<Uri>, filenameAsHanzi: Boolean): ImportResult =
        withContext(Dispatchers.IO) {
            var imported = 0
            val skipped = mutableListOf<Pair<String, String>>()

            uris.forEach { uri ->
                val displayName = queryDisplayName(uri) ?: "未知文件"
                val baseName = displayName.substringBeforeLast('.')

                // 文件名校验（映射字模式要求单个字符）
                if (filenameAsHanzi) {
                    val cps = baseName.codePoints().toArray()
                    if (cps.size != 1 || Character.isWhitespace(cps[0]) || Character.isISOControl(cps[0])) {
                        skipped += baseName to "文件名须为单个字符"
                        return@forEach
                    }
                }

                // 尺寸校验（先只读边界，避免大图占内存）
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
                if (bounds.outWidth == -1) {
                    skipped += baseName to "无法读取图片"
                    return@forEach
                }
                if (bounds.outWidth != GlyphBitmapStore.SIZE || bounds.outHeight != GlyphBitmapStore.SIZE) {
                    skipped += baseName to "尺寸须为 256×256（实际 ${bounds.outWidth}×${bounds.outHeight}）"
                    return@forEach
                }

                // 全量解码并转黑白（亮度阈值 50%）
                val bitmap = context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
                if (bitmap == null) {
                    skipped += baseName to "解码失败"
                    return@forEach
                }
                val buf = IntArray(GlyphBitmapStore.PIXEL_COUNT)
                bitmap.getPixels(buf, 0, GlyphBitmapStore.SIZE, 0, 0, GlyphBitmapStore.SIZE, GlyphBitmapStore.SIZE)
                bitmap.recycle()

                val pixels = ByteArray(GlyphBitmapStore.PIXEL_COUNT)
                for (i in buf.indices) {
                    val c = buf[i]
                    val r = (c shr 16) and 0xFF
                    val g = (c shr 8) and 0xFF
                    val b = c and 0xFF
                    val lum = 0.299 * r + 0.587 * g + 0.114 * b
                    pixels[i] = if (lum < 128) 0xFF.toByte() else 0x00
                }

                // 入库（映射字模式：文件名作映射字；备注模式：文件名作备注）
                if (filenameAsHanzi) {
                    val hanzi = String(Character.toChars(baseName.codePoints().toArray()[0]))
                    repository.insert(hanzi, "", pixels)
                } else {
                    repository.insert("", baseName, pixels)
                }
                imported++
            }

            ImportResult(imported, skipped)
        }

    private fun queryDisplayName(uri: Uri): String? {
        var name: String? = null
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0) name = c.getString(idx)
            }
        }
        return name ?: uri.lastPathSegment
    }
}
