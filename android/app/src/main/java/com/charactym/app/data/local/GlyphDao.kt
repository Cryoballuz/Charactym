package com.charactym.app.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface GlyphDao {

    @Insert
    suspend fun insert(glyph: Glyph): Long

    @Update
    suspend fun update(glyph: Glyph)

    @Delete
    suspend fun delete(glyph: Glyph)

    @Query("SELECT * FROM glyphs WHERE id = :id")
    suspend fun getById(id: Long): Glyph?

    @Query("SELECT * FROM glyphs WHERE id = :id")
    fun observeById(id: Long): Flow<Glyph?>

    @Query("SELECT * FROM glyphs ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<Glyph>>

    /**
     * 一个搜索词同时模糊匹配汉字与备注。
     */
    @Query(
        "SELECT * FROM glyphs WHERE hanzi LIKE '%' || :query || '%' " +
            "OR note LIKE '%' || :query || '%' ORDER BY createdAt DESC",
    )
    fun search(query: String): Flow<List<Glyph>>

    @Query("SELECT COUNT(*) FROM glyphs")
    suspend fun count(): Int

    /** 某汉字已对应的文字数量（保存前提示用） */
    @Query("SELECT COUNT(*) FROM glyphs WHERE hanzi = :hanzi")
    suspend fun countByHanzi(hanzi: String): Int

    /** 查询映射字在集合中的全部文字，按编号升序（映射页用） */
    @Query("SELECT * FROM glyphs WHERE hanzi IN (:chars) ORDER BY id ASC")
    fun observeByHanziIn(chars: List<String>): Flow<List<Glyph>>

    /** 按 id 批量读取（多选导出用） */
    @Query("SELECT * FROM glyphs WHERE id IN (:ids)")
    suspend fun getByIds(ids: List<Long>): List<Glyph>

    /** 一次性读取全部（备份用），按录入时间新→旧 */
    @Query("SELECT * FROM glyphs ORDER BY createdAt DESC")
    suspend fun getAllOnce(): List<Glyph>

    /** 全部编号升序（用于分配最小可用编号） */
    @Query("SELECT id FROM glyphs ORDER BY id ASC")
    suspend fun getAllIds(): List<Long>

    /** 清空全部（恢复备份前使用） */
    @Query("DELETE FROM glyphs")
    suspend fun deleteAll()
}
