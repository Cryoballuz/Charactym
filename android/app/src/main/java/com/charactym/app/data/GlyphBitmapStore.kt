package com.charactym.app.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import java.io.File
import java.io.FileOutputStream

/**
 * 画布数据（256×256 字节数组）与 PNG 文件之间的转换与存取。
 *
 * 画布约定：ByteArray 长度 = 256*256，索引 = y*256+x；
 * 字节 0 = 白，0xFF = 黑（判断时用 `toInt() and 0xFF` 比较）。
 */
class GlyphBitmapStore(context: Context) {

    private val glyphDir = File(context.filesDir, "glyphs").apply { mkdirs() }

    /** 把画布数据保存为 PNG（白底黑字），返回文件路径 */
    fun savePixels(id: Long, pixels: ByteArray): String {
        require(pixels.size == PIXEL_COUNT) { "画布数据必须是 256×256（当前 ${pixels.size}）" }
        val colors = IntArray(PIXEL_COUNT)
        for (i in 0 until PIXEL_COUNT) {
            colors[i] = if ((pixels[i].toInt() and 0xFF) == 0xFF) Color.BLACK else Color.WHITE
        }
        val bitmap = Bitmap.createBitmap(colors, SIZE, SIZE, Bitmap.Config.ARGB_8888)
        val file = File(glyphDir, "$id.png")
        FileOutputStream(file).use { out ->
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)) { "PNG 压缩失败" }
        }
        bitmap.recycle()
        return file.absolutePath
    }

    /** 从 PNG 读回画布数据 */
    fun loadPixels(path: String): ByteArray {
        val bitmap = BitmapFactory.decodeFile(path)
            ?: error("图片读取失败: $path")
        val colors = IntArray(PIXEL_COUNT)
        bitmap.getPixels(colors, 0, SIZE, 0, 0, SIZE, SIZE)
        bitmap.recycle()
        val pixels = ByteArray(PIXEL_COUNT)
        for (i in 0 until PIXEL_COUNT) {
            pixels[i] = if (colors[i] == Color.BLACK) 0xFF.toByte() else 0x00
        }
        return pixels
    }

    /** 直接读取位图（用于界面显示） */
    fun loadBitmap(path: String): Bitmap =
        BitmapFactory.decodeFile(path) ?: error("图片读取失败: $path")

    fun delete(path: String) {
        if (path.isNotBlank()) runCatching { File(path).delete() }
    }

    companion object {
        const val SIZE = 256
        const val PIXEL_COUNT = SIZE * SIZE
    }
}
