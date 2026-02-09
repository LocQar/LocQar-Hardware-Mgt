package com.locqar.locker.ui.screens.kiosk.recall

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.locqar.locker.LocQarApp
import com.locqar.locker.data.db.entity.*
import com.locqar.locker.hardware.codec.WinnsenCodec
import com.locqar.locker.hardware.controller.*
import com.locqar.locker.hardware.service.LockerDaemonService
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

data class StoredParcel(
    val doorId: Long,
    val doorLabel: String,
    val lockNumber: Int,
    val code: String?,
    val recipientInfo: String?
)

enum class RecallState {
    LIST_PARCELS,
    OPENING_DOOR,
    DOOR_OPEN,
    WAITING_CLOSE,
    COMPLETE,
    ERROR
}

class RecallViewModel(app: Application) : AndroidViewModel(app) {

    private val repository = (app as LocQarApp).repository

    var daemon: LockerDaemonService? = null

    private val _state = MutableStateFlow(RecallState.LIST_PARCELS)
    val state: StateFlow<RecallState> = _state.asStateFlow()

    private val _parcels = MutableStateFlow<List<StoredParcel>>(emptyList())
    val parcels: StateFlow<List<StoredParcel>> = _parcels.asStateFlow()

    private val _message = MutableStateFlow("Select a parcel to recall")
    val message: StateFlow<String> = _message.asStateFlow()

    private val _selectedDoorLabel = MutableStateFlow("")
    val selectedDoorLabel: StateFlow<String> = _selectedDoorLabel.asStateFlow()

    private var currentDoor: DoorEntity? = null
    private var closeWaitJob: Job? = null

    fun loadParcels() {
        viewModelScope.launch {
            val doors = repository.getAllDoors().filter { it.logicalState == LogicalState.OCCUPIED }
            _parcels.value = doors.map { door ->
                val codes = repository.findActiveCode(door.id.toString()) // simplified
                StoredParcel(
                    doorId = door.id,
                    doorLabel = door.doorLabel,
                    lockNumber = door.lockNumber,
                    code = null,
                    recipientInfo = null
                )
            }
        }
    }

    fun recallParcel(doorId: Long) {
        viewModelScope.launch {
            val door = repository.getDoor(doorId) ?: return@launch
            currentDoor = door
            _selectedDoorLabel.value = door.doorLabel
            _state.value = RecallState.OPENING_DOOR
            _message.value = "Opening door ${door.doorLabel}..."

            val d = daemon ?: run {
                _state.value = RecallState.ERROR
                _message.value = "System error"
                return@launch
            }

            val result = d.safeOpenDoor(door.lockNumber)
            when (result) {
                is SafeOpenResult.Confirmed -> {
                    _state.value = RecallState.DOOR_OPEN
                    _message.value = "Door ${door.doorLabel} is OPEN.\nRemove the parcel and close the door."
                    startWaitingForClose(door)
                }
                is SafeOpenResult.OpenNotConfirmed -> {
                    _state.value = RecallState.DOOR_OPEN
                    _message.value = "Door ${door.doorLabel} should be open.\nRemove the parcel and close the door."
                    startWaitingForClose(door)
                }
                is SafeOpenResult.OpenFailed -> {
                    _state.value = RecallState.ERROR
                    _message.value = "Failed to open door: ${result.reason}"
                }
                is SafeOpenResult.NotConnected -> {
                    _state.value = RecallState.ERROR
                    _message.value = "System unavailable"
                }
            }
        }
    }

    private fun startWaitingForClose(door: DoorEntity) {
        closeWaitJob?.cancel()
        closeWaitJob = viewModelScope.launch {
            _state.value = RecallState.WAITING_CLOSE
            val deadline = System.currentTimeMillis() + 120_000

            while (System.currentTimeMillis() < deadline) {
                val d = daemon ?: break
                val poll = d.pollOnce()
                if (poll is PollResult.Success) {
                    if (!WinnsenCodec.isLockOpen(poll.response.stateBits, door.lockNumber)) {
                        completeRecall()
                        return@launch
                    }
                }
                delay(500)
            }
        }
    }

    private suspend fun completeRecall() {
        val door = currentDoor ?: return

        repository.updateLogicalState(door.id, LogicalState.AVAILABLE)
        repository.updateDoorLastUsed(door.id)
        repository.revokeCodesByDoor(door.id)

        repository.logEvent(
            EventType.PARCEL_RECALLED, "KIOSK",
            "Parcel recalled from door ${door.doorLabel}",
            doorId = door.id, lockNumber = door.lockNumber
        )

        _state.value = RecallState.COMPLETE
        _message.value = "Parcel recalled from door ${door.doorLabel}."
    }

    fun reset() {
        closeWaitJob?.cancel()
        _state.value = RecallState.LIST_PARCELS
        _message.value = "Select a parcel to recall"
        _selectedDoorLabel.value = ""
        currentDoor = null
        loadParcels()
    }

    override fun onCleared() {
        super.onCleared()
        closeWaitJob?.cancel()
    }
}
