package com.locqar.locker.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Immutable event log for all operations.
 * Serves as the audit trail and outbox for future cloud sync.
 */
@Entity(tableName = "event_log")
data class EventLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val eventType: String,         // See EventType constants
    val severity: String = EventSeverity.INFO,
    val source: String,            // "SYSTEM", "ADMIN", "KIOSK", "TECH_TOOL", "DAEMON"
    val doorId: Long? = null,
    val lockNumber: Int? = null,
    val stationNumber: Int? = null,
    val message: String,
    val details: String? = null,   // JSON details
    val synced: Boolean = false    // For future outbox pattern
)

object EventType {
    const val DOOR_OPENED = "DOOR_OPENED"
    const val DOOR_CLOSED = "DOOR_CLOSED"
    const val DOOR_OPEN_FAILED = "DOOR_OPEN_FAILED"
    const val DOOR_OPEN_NOT_CONFIRMED = "DOOR_OPEN_NOT_CONFIRMED"
    const val DOOR_LEFT_OPEN = "DOOR_LEFT_OPEN"
    const val DOOR_ENABLED = "DOOR_ENABLED"
    const val DOOR_DISABLED = "DOOR_DISABLED"
    const val DOOR_STATE_CHANGED = "DOOR_STATE_CHANGED"
    const val PARCEL_DROPPED = "PARCEL_DROPPED"
    const val PARCEL_PICKED_UP = "PARCEL_PICKED_UP"
    const val PARCEL_RECALLED = "PARCEL_RECALLED"
    const val CODE_CREATED = "CODE_CREATED"
    const val CODE_USED = "CODE_USED"
    const val CODE_EXPIRED = "CODE_EXPIRED"
    const val CODE_INVALID = "CODE_INVALID"
    const val STATION_ONLINE = "STATION_ONLINE"
    const val STATION_OFFLINE = "STATION_OFFLINE"
    const val USB_CONNECTED = "USB_CONNECTED"
    const val USB_DISCONNECTED = "USB_DISCONNECTED"
    const val POLL_ERROR = "POLL_ERROR"
    const val ADMIN_LOGIN = "ADMIN_LOGIN"
    const val ADMIN_ACTION = "ADMIN_ACTION"
    const val COMMISSIONING = "COMMISSIONING"
    const val CONFIG_EXPORT = "CONFIG_EXPORT"
    const val CONFIG_IMPORT = "CONFIG_IMPORT"
    const val SYSTEM_START = "SYSTEM_START"
    const val SYSTEM_STOP = "SYSTEM_STOP"
    const val INCIDENT_CREATED = "INCIDENT_CREATED"
    const val TEST_EXECUTED = "TEST_EXECUTED"
}

object EventSeverity {
    const val INFO = "INFO"
    const val WARNING = "WARNING"
    const val ERROR = "ERROR"
    const val CRITICAL = "CRITICAL"
}
