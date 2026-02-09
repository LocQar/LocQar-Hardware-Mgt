package com.locqar.locker.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Represents a physical door with its lock mapping and logical state.
 */
@Entity(
    tableName = "doors",
    indices = [
        Index(value = ["boardId", "lockNumber"], unique = true),
        Index(value = ["doorLabel"], unique = true)
    ]
)
data class DoorEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val boardId: Long,
    val lockNumber: Int,          // Physical lock number 1-12
    val doorLabel: String,        // User-facing label (e.g., "1", "2", "A1")
    val enabled: Boolean = true,  // Software enable/disable
    val physicalState: String = PhysicalState.UNKNOWN, // OPEN, CLOSED, UNKNOWN
    val logicalState: String = LogicalState.AVAILABLE,  // AVAILABLE, RESERVED, OCCUPIED, OUT_OF_SERVICE
    val reservedUntil: Long = 0,  // Reservation expiry timestamp
    val reservedBy: String? = null,
    val lastUsedAt: Long = 0,
    val lastError: String? = null,
    val lastErrorAt: Long = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

object PhysicalState {
    const val OPEN = "OPEN"
    const val CLOSED = "CLOSED"
    const val UNKNOWN = "UNKNOWN"
}

object LogicalState {
    const val AVAILABLE = "AVAILABLE"
    const val RESERVED = "RESERVED"
    const val OCCUPIED = "OCCUPIED"
    const val OUT_OF_SERVICE = "OUT_OF_SERVICE"
}
