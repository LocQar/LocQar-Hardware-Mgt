package com.locqar.locker.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Incidents: escalated issues requiring attention.
 */
@Entity(tableName = "incidents")
data class IncidentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val type: String,           // DOOR_LEFT_OPEN, OPEN_FAILED, STATION_OFFLINE, etc.
    val doorId: Long? = null,
    val lockNumber: Int? = null,
    val stationNumber: Int? = null,
    val message: String,
    val resolved: Boolean = false,
    val resolvedAt: Long = 0,
    val resolvedBy: String? = null,
    val resolution: String? = null
)
