package com.android.synclab.glimpse.infra.repository.roomdb.core

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.android.synclab.glimpse.utils.LogCompat

@Database(
    entities = [ControllerProfileEntity::class],
    version = 1,
    exportSchema = false
)
abstract class GlimpseDatabase : RoomDatabase() {
    abstract fun controllerProfileDao(): ControllerProfileDao

    companion object {
        private const val DATABASE_NAME = "glimpse.db"

        fun create(context: Context): GlimpseDatabase {
            val databasePath = context.getDatabasePath(DATABASE_NAME).absolutePath
            LogCompat.i("GlimpseDatabase create path=$databasePath")
            return Room.databaseBuilder(
                context,
                GlimpseDatabase::class.java,
                DATABASE_NAME
            ).build()
        }
    }
}
