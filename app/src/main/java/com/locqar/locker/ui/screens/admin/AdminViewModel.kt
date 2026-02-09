package com.locqar.locker.ui.screens.admin

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.locqar.locker.LocQarApp
import com.locqar.locker.data.db.entity.*
import com.locqar.locker.hardware.codec.WinnsenCodec
import com.locqar.locker.hardware.controller.*
import com.locqar.locker.hardware.service.LockerDaemonService
import com.locqar.locker.util.ExportUtil
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

data class DoorUiState(
    val id: Long = 0,
    val doorLabel: String = "",
    val lockNumber: Int = 0,
    val physicalState: String = PhysicalState.UNKNOWN,
    val logicalState: String = LogicalState.AVAILABLE,
    val enabled: Boolean = true,
    val lastUsedAt: Long = 0,
    val lastError: String? = null,
    val lastErrorAt: Long = 0,
    val openSinceMs: Long? = null  // How long the door has been open
)

class AdminViewModel(app: Application) : AndroidViewModel(app) {

    private val repository = (app as LocQarApp).repository

    var daemon: LockerDaemonService? = null
        set(value) {
            field = value
            if (value != null) {
                collectDaemonState()
            }
        }

    private val _doors = MutableStateFlow<List<DoorUiState>>(emptyList())
    val doors: StateFlow<List<DoorUiState>> = _doors.asStateFlow()

    private val _isOnline = MutableStateFlow(false)
    val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()

    private val _statusMessage = MutableStateFlow("")
    val statusMessage: StateFlow<String> = _statusMessage.asStateFlow()

    private val _incidents = MutableStateFlow<List<IncidentEntity>>(emptyList())
    val incidents: StateFlow<List<IncidentEntity>> = _incidents.asStateFlow()

    private val _openDoorBanner = MutableStateFlow<String?>(null)
    val openDoorBanner: StateFlow<String?> = _openDoorBanner.asStateFlow()

    private val _openDoorFullscreen = MutableStateFlow<String?>(null)
    val openDoorFullscreen: StateFlow<String?> = _openDoorFullscreen.asStateFlow()

    // Track when doors were first seen open
    private val doorOpenStartTimes = mutableMapOf<Long, Long>()
    private var openMonitorJob: Job? = null

    init {
        // Observe doors from DB
        viewModelScope.launch {
            repository.observeDoors().collect { doorEntities ->
                updateDoorUiStates(doorEntities)
            }
        }
        // Observe incidents
        viewModelScope.launch {
            repository.observeUnresolvedIncidents().collect { _incidents.value = it }
        }
    }

    private fun collectDaemonState() {
        viewModelScope.launch {
            daemon?.isStationOnline?.collect { _isOnline.value = it }
        }
        viewModelScope.launch {
            daemon?.doorStates?.collect { hwStates ->
                updatePhysicalStates(hwStates)
            }
        }
        startOpenDoorMonitor()
    }

    private suspend fun updatePhysicalStates(hwStates: Map<Int, Boolean>) {
        val board = repository.getFirstBoard() ?: return
        for ((lock, isOpen) in hwStates) {
            val state = if (isOpen) PhysicalState.OPEN else PhysicalState.CLOSED
            repository.updatePhysicalState(board.id, lock, state)
        }
        repository.updateBoardPollState(
            board.stationNumber, true, System.currentTimeMillis(),
            hwStates.entries.fold(0) { acc, (lock, open) ->
                if (open) acc or (1 shl (lock - 1)) else acc
            }
        )
    }

    private fun updateDoorUiStates(entities: List<DoorEntity>) {
        val now = System.currentTimeMillis()
        _doors.value = entities.map { door ->
            val openSince = if (door.physicalState == PhysicalState.OPEN) {
                doorOpenStartTimes.getOrPut(door.id) { now }
                now - doorOpenStartTimes[door.id]!!
            } else {
                doorOpenStartTimes.remove(door.id)
                null
            }
            DoorUiState(
                id = door.id,
                doorLabel = door.doorLabel,
                lockNumber = door.lockNumber,
                physicalState = door.physicalState,
                logicalState = door.logicalState,
                enabled = door.enabled,
                lastUsedAt = door.lastUsedAt,
                lastError = door.lastError,
                lastErrorAt = door.lastErrorAt,
                openSinceMs = openSince
            )
        }
    }

    private fun startOpenDoorMonitor() {
        openMonitorJob?.cancel()
        openMonitorJob = viewModelScope.launch {
            while (isActive) {
                delay(1000)
                val bannerThresholdMs = repository.getSettingInt(SettingsKeys.OPEN_REMINDER_BANNER_SECONDS) * 1000L
                val fullscreenThresholdMs = repository.getSettingInt(SettingsKeys.OPEN_REMINDER_FULLSCREEN_SECONDS) * 1000L
                val incidentThresholdMs = repository.getSettingInt(SettingsKeys.OPEN_INCIDENT_SECONDS) * 1000L
                val disableOnIncident = repository.getSettingBool(SettingsKeys.DISABLE_DOOR_ON_INCIDENT)

                var bannerMsg: String? = null
                var fullscreenMsg: String? = null

                for (door in _doors.value) {
                    val openMs = door.openSinceMs ?: continue
                    if (openMs > incidentThresholdMs && incidentThresholdMs > 0) {
                        // Check if incident already exists
                        val existing = repository.getUnresolvedForDoor(door.id)
                        if (existing.none { it.type == "DOOR_LEFT_OPEN" }) {
                            repository.createIncident(
                                "DOOR_LEFT_OPEN",
                                "Door '${door.doorLabel}' left open for ${openMs / 1000}s",
                                doorId = door.id, lockNumber = door.lockNumber
                            )
                            if (disableOnIncident) {
                                repository.setDoorEnabled(door.id, false)
                                repository.updateLogicalState(door.id, LogicalState.OUT_OF_SERVICE)
                            }
                        }
                        fullscreenMsg = "DOOR '${door.doorLabel}' LEFT OPEN!"
                    } else if (openMs > fullscreenThresholdMs && fullscreenThresholdMs > 0) {
                        fullscreenMsg = "Please close door '${door.doorLabel}'"
                    } else if (openMs > bannerThresholdMs && bannerThresholdMs > 0) {
                        bannerMsg = "Door '${door.doorLabel}' is open"
                    }
                }

                _openDoorBanner.value = bannerMsg
                _openDoorFullscreen.value = fullscreenMsg
            }
        }
    }

    fun openDoor(doorId: Long) {
        viewModelScope.launch {
            val door = repository.getDoor(doorId) ?: return@launch
            val d = daemon ?: return@launch
            val result = d.safeOpenDoor(door.lockNumber)
            _statusMessage.value = when (result) {
                is SafeOpenResult.Confirmed -> "Door '${door.doorLabel}' opened"
                is SafeOpenResult.OpenNotConfirmed -> "Door '${door.doorLabel}' open not confirmed"
                is SafeOpenResult.OpenFailed -> "Door '${door.doorLabel}' failed: ${result.reason}"
                is SafeOpenResult.NotConnected -> "Not connected"
            }
            repository.logEvent(
                EventType.DOOR_OPENED, "ADMIN", _statusMessage.value,
                doorId = doorId, lockNumber = door.lockNumber
            )
        }
    }

    fun toggleDoorEnabled(doorId: Long) {
        viewModelScope.launch {
            val door = repository.getDoor(doorId) ?: return@launch
            val newEnabled = !door.enabled
            repository.setDoorEnabled(doorId, newEnabled)
            if (!newEnabled) {
                repository.updateLogicalState(doorId, LogicalState.OUT_OF_SERVICE)
            } else {
                if (door.logicalState == LogicalState.OUT_OF_SERVICE) {
                    repository.updateLogicalState(doorId, LogicalState.AVAILABLE)
                }
            }
            repository.logEvent(
                if (newEnabled) EventType.DOOR_ENABLED else EventType.DOOR_DISABLED,
                "ADMIN", "Door '${door.doorLabel}' ${if (newEnabled) "enabled" else "disabled"}",
                doorId = doorId
            )
        }
    }

    fun clearReservation(doorId: Long) {
        viewModelScope.launch {
            repository.clearReservation(doorId)
            repository.updateLogicalState(doorId, LogicalState.AVAILABLE)
            _statusMessage.value = "Reservation cleared"
            repository.logEvent(EventType.ADMIN_ACTION, "ADMIN", "Reservation cleared", doorId = doorId)
        }
    }

    fun forceSetAvailable(doorId: Long) {
        viewModelScope.launch {
            repository.clearReservation(doorId)
            repository.updateLogicalState(doorId, LogicalState.AVAILABLE)
            repository.setDoorEnabled(doorId, true)
            _statusMessage.value = "Door set to AVAILABLE"
            repository.logEvent(EventType.ADMIN_ACTION, "ADMIN", "Force set available", doorId = doorId)
        }
    }

    fun pollNow() {
        viewModelScope.launch {
            daemon?.pollOnce()
        }
    }

    fun exportLogs() {
        viewModelScope.launch {
            val events = repository.getAllEvents()
            val uri = ExportUtil.exportLogsCsv(getApplication(), events)
            _statusMessage.value = if (uri != null) "Logs exported" else "Export failed"
        }
    }

    fun resolveIncident(incidentId: Long) {
        viewModelScope.launch {
            repository.resolveIncident(incidentId, "ADMIN", "Resolved by admin")
        }
    }

    fun dismissBanner() {
        _openDoorBanner.value = null
    }

    fun dismissFullscreen() {
        _openDoorFullscreen.value = null
    }

    override fun onCleared() {
        super.onCleared()
        openMonitorJob?.cancel()
    }
}
