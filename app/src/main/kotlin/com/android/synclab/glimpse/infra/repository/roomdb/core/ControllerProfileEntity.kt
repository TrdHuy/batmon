package com.android.synclab.glimpse.infra.repository.roomdb.core

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "controller_profile")
data class ControllerProfileEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,
    @ColumnInfo(name = "device_name")
    val deviceName: String,
    @ColumnInfo(name = "lightbar_color")
    val lightbarColor: Int,
    @ColumnInfo(name = "live_battery_overlay_enabled")
    val liveBatteryOverlayEnabled: Boolean = false
)
