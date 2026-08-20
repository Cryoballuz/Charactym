package com.charactym.app.data

import kotlinx.serialization.Serializable

/** 备份文件 manifest.json：记录格式版本等信息 */
@Serializable
data class BackupManifest(
    val formatVersion: Int = FORMAT_VERSION,
    val appVersion: String = "1.1.0",
    val glyphCount: Int,
    val createdAt: Long,
) {
    companion object {
        const val FORMAT_VERSION = 1
    }
}

/** 备份文件 glyphs.json 中的一条文字（不含图片路径，图片单独存放在 images/ 下） */
@Serializable
data class BackupGlyph(
    val id: Long,
    val hanzi: String,
    val note: String,
    val createdAt: Long,
    val updatedAt: Long,
)
