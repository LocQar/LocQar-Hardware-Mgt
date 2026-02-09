package com.locqar.locker.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "boards")
data class BoardEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val stationNumber: Int,
    val maxDoors: Int = 12,
    val label: String = "Station $stationNumber",
    val isOnline: Boolean = false,
    val lastPollTime: Long = 0,
    val lastStateBits: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
