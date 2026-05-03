package com.android.synclab.glimpse.infra.repository.roomdb.core

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface ControllerProfileDao {
    @Query("SELECT * FROM controller_profile WHERE id = :id LIMIT 1")
    fun getById(id: String): ControllerProfileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsert(profile: ControllerProfileEntity)

    @Query("DELETE FROM controller_profile WHERE id = :id")
    fun deleteById(id: String)
}
