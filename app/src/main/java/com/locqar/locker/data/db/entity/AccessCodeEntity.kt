package com.locqar.locker.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Local placeholder for access codes (pickup codes).
 * In production, this would sync with a cloud backend.
 */
@Entity(
    tableName = "access_codes",
    indices = [Index(value = ["code"], unique = true)]
)
data class AccessCodeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val code: String,             // 6-digit code or similar
    val doorId: Long,             // Which door this code opens
    val type: String = CodeType.PICKUP,  // PICKUP, COURIER, ADMIN
    val status: String = CodeStatus.ACTIVE, // ACTIVE, USED, EXPIRED, REVOKED
    val createdAt: Long = System.currentTimeMillis(),
    val expiresAt: Long = 0,      // 0 = no expiry
    val usedAt: Long = 0,
    val parcelId: String? = null,  // Optional reference
    val createdBy: String? = null
)

object CodeType {
    const val PICKUP = "PICKUP"
    const val COURIER = "COURIER"
    const val ADMIN = "ADMIN"
}

object CodeStatus {
    const val ACTIVE = "ACTIVE"
    const val USED = "USED"
    const val EXPIRED = "EXPIRED"
    const val REVOKED = "REVOKED"
}
