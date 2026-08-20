package com.charactym.app.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 一个人造文字记录。
 * 一张 256×256 黑白字图 + 映射字（可选，0 或 1 个字符）+ 备注。
 */
@Entity(
    tableName = "glyphs",
    indices = [Index(value = ["hanzi"])],
)
data class Glyph(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** 映射字：0 或 1 个 Unicode 码点字符（可为空；允许非汉字） */
    val hanzi: String,
    /** 备注（含义说明等），可为空字符串 */
    val note: String = "",
    /** 256×256 PNG 文件的绝对路径（应用私有目录内） */
    val pngPath: String = "",
    /** 录入时间戳（毫秒） */
    val createdAt: Long,
    /** 最后修改时间戳（毫秒） */
    val updatedAt: Long,
)
