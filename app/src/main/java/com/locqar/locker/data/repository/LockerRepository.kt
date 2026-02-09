package com.locqar.locker.data.repository

import com.locqar.locker.data.db.LockerDatabase
import com.locqar.locker.data.db.entity.*
import kotlinx.coroutines.flow.Flow
import java.security.MessageDigest

/**
 * Central repository for all database operations.
 * Business logic layer between ViewModels and DAOs.
 */
class LockerRepository(private val db: LockerDatabase) {

    // DAOs
    private val boardDao = db.boardDao()
    private val doorDao = db.doorDao()
    private val accessCodeDao = db.accessCodeDao()
    private val eventLogDao = db.eventLogDao()
    private val incidentDao = db.incidentDao()
    private val settingsDao = db.settingsDao()

    // ===== Board =====
    fun observeBoards(): Flow<List<BoardEntity>> = boardDao.observeAll()
    suspend fun getBoard(station: Int) = boardDao.getByStation(station)
    suspend fun getFirstBoard() = boardDao.getFirst()
    suspend fun saveBoard(board: BoardEntity) = boardDao.upsert(board)
    suspend fun updateBoardPollState(station: Int, online: Boolean, pollTime: Long, stateBits: Int) =
        boardDao.updatePollState(station, online, pollTime, stateBits)

    // ===== Doors =====
    fun observeDoors(): Flow<List<DoorEntity>> = doorDao.observeAll()
    suspend fun getAllDoors() = doorDao.getAll()
    suspend fun getDoor(doorId: Long) = doorDao.getById(doorId)
    suspend fun getDoorByLabel(label: String) = doorDao.getByLabel(label)
    suspend fun getDoorByLock(boardId: Long, lockNumber: Int) = doorDao.getByLockNumber(boardId, lockNumber)
    suspend fun saveDoor(door: DoorEntity) = doorDao.upsert(door)
    suspend fun saveDoors(doors: List<DoorEntity>) = doorDao.upsertAll(doors)
    suspend fun updateDoor(door: DoorEntity) = doorDao.update(door)

    suspend fun updatePhysicalState(boardId: Long, lockNumber: Int, state: String) =
        doorDao.updatePhysicalState(boardId, lockNumber, state)
    suspend fun updateLogicalState(doorId: Long, state: String) =
        doorDao.updateLogicalState(doorId, state)
    suspend fun setDoorEnabled(doorId: Long, enabled: Boolean) =
        doorDao.setEnabled(doorId, enabled)
    suspend fun setDoorError(doorId: Long, error: String) =
        doorDao.setLastError(doorId, error)
    suspend fun updateDoorLastUsed(doorId: Long) =
        doorDao.updateLastUsed(doorId)

    suspend fun reserveDoor(doorId: Long, durationMs: Long, reservedBy: String): Boolean {
        doorDao.expireReservations()
        val until = System.currentTimeMillis() + durationMs
        return doorDao.atomicReserve(doorId, until, reservedBy) > 0
    }

    suspend fun clearReservation(doorId: Long) = doorDao.clearReservation(doorId)
    suspend fun expireReservations() = doorDao.expireReservations()
    suspend fun findBestAvailableDoor() = doorDao.findBestAvailableDoor()
    suspend fun deleteAllDoors() = doorDao.deleteAll()

    // ===== Access Codes =====
    fun observeAccessCodes(): Flow<List<AccessCodeEntity>> = accessCodeDao.observeAll()
    suspend fun findActiveCode(code: String) = accessCodeDao.findActiveByCode(code)
    suspend fun createAccessCode(code: AccessCodeEntity) = accessCodeDao.upsert(code)
    suspend fun markCodeUsed(id: Long) = accessCodeDao.markUsed(id)
    suspend fun expireCodes() = accessCodeDao.expireCodes()
    suspend fun revokeCodesByDoor(doorId: Long) = accessCodeDao.revokeByDoor(doorId)

    // ===== Event Log =====
    fun observeRecentEvents(limit: Int = 100): Flow<List<EventLogEntity>> = eventLogDao.observeRecent(limit)
    suspend fun getAllEvents() = eventLogDao.getAll()
    suspend fun getEventsByDoor(doorId: Long, limit: Int = 50) = eventLogDao.getByDoor(doorId, limit)

    suspend fun logEvent(
        eventType: String,
        source: String,
        message: String,
        severity: String = EventSeverity.INFO,
        doorId: Long? = null,
        lockNumber: Int? = null,
        stationNumber: Int? = null,
        details: String? = null
    ): Long {
        return eventLogDao.insert(
            EventLogEntity(
                eventType = eventType,
                severity = severity,
                source = source,
                doorId = doorId,
                lockNumber = lockNumber,
                stationNumber = stationNumber,
                message = message,
                details = details
            )
        )
    }

    // ===== Incidents =====
    fun observeIncidents(): Flow<List<IncidentEntity>> = incidentDao.observeAll()
    fun observeUnresolvedIncidents(): Flow<List<IncidentEntity>> = incidentDao.observeUnresolved()
    suspend fun getUnresolvedIncidents() = incidentDao.getUnresolved()
    suspend fun getUnresolvedForDoor(doorId: Long) = incidentDao.getUnresolvedForDoor(doorId)

    suspend fun createIncident(
        type: String,
        message: String,
        doorId: Long? = null,
        lockNumber: Int? = null,
        stationNumber: Int? = null
    ): Long {
        val id = incidentDao.insert(
            IncidentEntity(
                type = type,
                doorId = doorId,
                lockNumber = lockNumber,
                stationNumber = stationNumber,
                message = message
            )
        )
        logEvent(
            EventType.INCIDENT_CREATED, "SYSTEM", message,
            EventSeverity.ERROR, doorId, lockNumber, stationNumber
        )
        return id
    }

    suspend fun resolveIncident(id: Long, by: String = "ADMIN", resolution: String = "") =
        incidentDao.resolve(id, by, resolution)

    // ===== Settings =====
    fun observeSettings(): Flow<List<SettingsEntity>> = settingsDao.observeAll()

    suspend fun getSetting(key: String): String {
        return settingsDao.getValue(key) ?: SettingsKeys.DEFAULTS[key] ?: ""
    }

    suspend fun getSettingInt(key: String): Int = getSetting(key).toIntOrNull() ?: 0
    suspend fun getSettingLong(key: String): Long = getSetting(key).toLongOrNull() ?: 0L
    suspend fun getSettingBool(key: String): Boolean = getSetting(key).toBooleanStrictOrNull() ?: false

    suspend fun setSetting(key: String, value: String) {
        settingsDao.upsert(SettingsEntity(key, value))
    }

    suspend fun getAllSettings(): Map<String, String> {
        val stored = settingsDao.getAll().associate { it.key to it.value }
        return SettingsKeys.DEFAULTS + stored
    }

    suspend fun initializeDefaults() {
        val existing = settingsDao.getAll().map { it.key }.toSet()
        val missing = SettingsKeys.DEFAULTS.filter { it.key !in existing }
        if (missing.isNotEmpty()) {
            settingsDao.upsertAll(missing.map { (k, v) -> SettingsEntity(k, v) })
        }
    }

    // ===== Admin Password =====
    suspend fun setAdminPassword(plaintext: String) {
        val hash = hashPassword(plaintext)
        setSetting(SettingsKeys.ADMIN_PASSWORD_HASH, hash)
        setSetting(SettingsKeys.ADMIN_PASSWORD_CHANGED, "true")
    }

    suspend fun verifyAdminPassword(plaintext: String): Boolean {
        val storedHash = getSetting(SettingsKeys.ADMIN_PASSWORD_HASH)
        if (storedHash.isBlank()) return plaintext == "admin" // default
        return hashPassword(plaintext) == storedHash
    }

    suspend fun isAdminPasswordChanged(): Boolean = getSettingBool(SettingsKeys.ADMIN_PASSWORD_CHANGED)

    private fun hashPassword(plaintext: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        val bytes = md.digest(plaintext.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
