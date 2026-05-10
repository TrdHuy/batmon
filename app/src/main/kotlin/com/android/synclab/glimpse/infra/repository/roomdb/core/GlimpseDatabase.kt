package com.android.synclab.glimpse.infra.repository.roomdb.core

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.android.synclab.glimpse.utils.LogCompat

@Database(
    entities = [ControllerProfileEntity::class],
    version = 2,
    exportSchema = false
)
abstract class GlimpseDatabase : RoomDatabase() {
    abstract fun controllerProfileDao(): ControllerProfileDao

    companion object {
        private const val DATABASE_NAME = "glimpse.db"

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE controller_profile " +
                            "ADD COLUMN live_battery_overlay_enabled INTEGER NOT NULL DEFAULT 0"
                )
            }
        }

        fun create(context: Context): GlimpseDatabase {
            val databasePath = context.getDatabasePath(DATABASE_NAME).absolutePath
            LogCompat.i("GlimpseDatabase create path=$databasePath")
            return Room.databaseBuilder(
                context,
                GlimpseDatabase::class.java,
                DATABASE_NAME
            ).addMigrations(MIGRATION_1_2).build()
        }
    }
}
