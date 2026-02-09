package com.locqar.locker.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Key-value settings store in Room.
 */
@Entity(tableName = "settings")
data class SettingsEntity(
    @PrimaryKey val key: String,
    val value: String,
    val updatedAt: Long = System.currentTimeMillis()
)

object SettingsKeys {
    const val LOCKER_NAME = "locker_name"
    const val HELP_PHONE = "help_phone"
    const val IDLE_POLL_INTERVAL_MS = "idle_poll_interval_ms"
    const val ACTIVE_POLL_INTERVAL_MS = "active_poll_interval_ms"
    const val OPEN_CONFIRM_SECONDS = "open_confirm_seconds"
    const val OPEN_TOO_LONG_SECONDS = "open_too_long_seconds"
    const val OPEN_REMINDER_BANNER_SECONDS = "open_reminder_banner_seconds"
    const val OPEN_REMINDER_FULLSCREEN_SECONDS = "open_reminder_fullscreen_seconds"
    const val OPEN_INCIDENT_SECONDS = "open_incident_seconds"
    const val DISABLE_DOOR_ON_INCIDENT = "disable_door_on_incident"
    const val ADMIN_PASSWORD_HASH = "admin_password_hash"
    const val ADMIN_PASSWORD_CHANGED = "admin_password_changed"
    const val COMMISSIONING_COMPLETE = "commissioning_complete"
    const val DEMO_MODE = "demo_mode"
    const val RESERVATION_TIMEOUT_SECONDS = "reservation_timeout_seconds"
    const val STATION_NUMBER = "station_number"

    // Defaults
    val DEFAULTS = mapOf(
        LOCKER_NAME to "LocQar Locker",
        HELP_PHONE to "",
        IDLE_POLL_INTERVAL_MS to "5000",
        ACTIVE_POLL_INTERVAL_MS to "500",
        OPEN_CONFIRM_SECONDS to "5",
        OPEN_TOO_LONG_SECONDS to "60",
        OPEN_REMINDER_BANNER_SECONDS to "10",
        OPEN_REMINDER_FULLSCREEN_SECONDS to "30",
        OPEN_INCIDENT_SECONDS to "60",
        DISABLE_DOOR_ON_INCIDENT to "false",
        ADMIN_PASSWORD_HASH to "",
        ADMIN_PASSWORD_CHANGED to "false",
        COMMISSIONING_COMPLETE to "false",
        DEMO_MODE to "false",
        RESERVATION_TIMEOUT_SECONDS to "120",
        STATION_NUMBER to "1"
    )
}
