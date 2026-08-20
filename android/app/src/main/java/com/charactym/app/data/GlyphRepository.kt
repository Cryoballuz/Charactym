package com.charactym.app.data

import android.graphics.Bitmap
import com.charactym.app.data.local.Glyph
import com.charactym.app.data.local.GlyphDao
import kotlinx.coroutines.flow.Flow

/**
 * 文字库的对外数据接口：负责把数据库记录与图片文件组织成"一条字"。
 */
class GlyphRepository(
    private val dao: GlyphDao,
    private val store: GlyphBitmapStore,
) {

    /** 全部记录，按录入时间新→旧 */
    fun observeAll(): Flow<List<Glyph>> = dao.observeAll()

    /** 一个关键词同时模糊搜索汉字与备注 */
    fun search(query: String): Flow<List<Glyph>> = dao.search(query.trim())

    suspend fun getById(id: Long): Glyph? = dao.getById(id)

    /** 实时观察某条记录（详情页使用，编辑后自动刷新） */
    fun observeGlyph(id: Long): Flow<Glyph?> = dao.observeById(id)

    suspend fun count(): Int = dao.count()

    /** 某汉字已对应的文字数量 */
    suspend fun countByHanzi(hanzi: String): Int = dao.countByHanzi(hanzi)

    /** 查询映射字在集合中的全部文字（映射页用，编号升序） */
    fun observeByHanziIn(chars: List<String>): Flow<List<Glyph>> = dao.observeByHanziIn(chars)

    /** 新增一条字：先分配最小可用编号（删除后的空缺会被复用），再存 PNG，最后回填图片路径 */
    suspend fun insert(hanzi: String, note: String, pixels: ByteArray): Long {
        val now = System.currentTimeMillis()
        val id = nextFreeId()
        dao.insert(
            Glyph(id = id, hanzi = hanzi, note = note, pngPath = "", createdAt = now, updatedAt = now),
        )
        val path = store.savePixels(id, pixels)
        val saved = dao.getById(id) ?: error("刚写入的记录读不出来")
        dao.update(saved.copy(pngPath = path))
        return id
    }

    /** 最小可用编号：从 1 开始找第一个空缺，保证编号连续 */
    private suspend fun nextFreeId(): Long {
        val ids = dao.getAllIds()
        var expected = 1L
        for (id in ids) {
            if (id > expected) return expected
            expected = id + 1
        }
        return expected
    }

    /** 更新一条字；pixels 为 null 表示不重画图片 */
    suspend fun update(id: Long, hanzi: String, note: String, pixels: ByteArray? = null) {
        val old = dao.getById(id) ?: return
        val path = if (pixels != null) store.savePixels(id, pixels) else old.pngPath
        dao.update(
            old.copy(
                hanzi = hanzi,
                note = note,
                pngPath = path,
                updatedAt = System.currentTimeMillis(),
            ),
        )
    }

    /** 删除记录与对应图片文件 */
    suspend fun delete(id: Long) {
        val glyph = dao.getById(id) ?: return
        store.delete(glyph.pngPath)
        dao.delete(glyph)
    }

    /** 读回一条字的画布数据（256×256 字节数组），不存在返回 null */
    suspend fun loadPixels(id: Long): ByteArray? {
        val glyph = dao.getById(id) ?: return null
        return store.loadPixels(glyph.pngPath)
    }

    /** 读回一条字的位图（用于界面显示），不存在返回 null */
    suspend fun loadBitmap(id: Long): Bitmap? {
        val glyph = dao.getById(id) ?: return null
        return store.loadBitmap(glyph.pngPath)
    }
}
