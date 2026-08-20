package com.charactym.app

import android.app.Application
import android.content.Context
import com.charactym.app.data.BatchImportManager
import com.charactym.app.data.DataTransferManager
import com.charactym.app.data.GlyphBitmapStore
import com.charactym.app.data.GlyphRepository
import com.charactym.app.data.local.CharactymDatabase

/**
 * 应用入口：持有全应用共享的数据库与仓库实例（简单手工依赖注入，不引入额外框架）。
 */
class CharactymApp : Application() {

    val database: CharactymDatabase by lazy { CharactymDatabase.get(this) }

    val glyphRepository: GlyphRepository by lazy {
        GlyphRepository(database.glyphDao(), GlyphBitmapStore(this))
    }

    val dataTransferManager: DataTransferManager by lazy {
        DataTransferManager(this, database.glyphDao())
    }

    val batchImportManager: BatchImportManager by lazy {
        BatchImportManager(this, glyphRepository)
    }

    companion object {
        fun from(context: Context): CharactymApp =
            context.applicationContext as CharactymApp
    }
}
