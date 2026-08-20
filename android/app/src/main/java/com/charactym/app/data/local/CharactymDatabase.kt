package com.charactym.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [Glyph::class],
    version = 1,
    exportSchema = false,
)
abstract class CharactymDatabase : RoomDatabase() {

    abstract fun glyphDao(): GlyphDao

    companion object {
        private const val DB_NAME = "charactym.db"

        @Volatile
        private var instance: CharactymDatabase? = null

        fun get(context: Context): CharactymDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    CharactymDatabase::class.java,
                    DB_NAME,
                )
                    // 显式迁移策略：将来修改表结构时，在 MIGRATIONS 中登记对应版本的
                    // Migration（例如从 1→2），保证升级应用时用户数据不丢失。
                    .addMigrations(*MIGRATIONS)
                    .build()
                    .also { instance = it }
            }

        /** 数据库迁移列表（当前 v1 无需迁移；升级示例见注释） */
        private val MIGRATIONS = emptyArray<androidx.room.migration.Migration>()
        /*
         * 将来从 v1 升级到 v2 时：
         * private val MIGRATIONS = arrayOf(
         *   object : Migration(1, 2) {
         *     override fun migrate(db: SupportSQLiteDatabase) { db.execSQL("ALTER TABLE ...") }
         *   },
         * )
         * 同时把 @Database 的 version 改为 2。
         */
    }
}
