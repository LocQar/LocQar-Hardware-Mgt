package com.locqar.locker.ui.screens.kiosk.pickup

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

enum class PickupState {
    ENTER_CODE,
    OPENING_DOOR,
    DOOR_OPEN,
    WAITING_CLOSE,
    COMPLETE,
    ERROR
}

class PickupViewModel(app: Application) : AndroidViewModel(app) {

    private val repository = (app as LocQarApp).repository

    var daemon: LockerDaemonService? = null

    private val _state = MutableStateFlow(PickupState.ENTER_CODE)
    val state: StateFlow<PickupState> = _state.asStateFlow()

    private val _code = MutableStateFlow("")
    val code: StateFlow<String> = _code.asStateFlow()

    private val _doorLabel = MutableStateFlow("")
    val doorLabel: StateFlow<String> = _doorLabel.asStateFlow()

    private val _message = MutableStateFlow("Enter your pickup code")
    val message: StateFlow<String> = _message.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private var currentDoor: DoorEntity? = null
    private var currentCode: AccessCodeEntity? = null
    private var closeWaitJob: Job? = null

    fun setCode(c: String) {
        _code.value = c
        _errorMessage.value = null
    }

    fun submitCode() {
        viewModelScope.launch {
            val codeStr = _code.value.trim()
            if (codeStr.isBlank()) {
                _errorMessage.value = "Please enter a code"
                return@launch
            }

            // Expire old codes first
            repository.expireCodes()

            val codeEntity = repository.findActiveCode(codeStr)
            if (codeEntity == null) {
                _errorMessage.value = "Invalid or expired pickup code"
                repository.logEvent(
                    EventType.CODE_INVALID, "KIOSK",
                    "Invalid pickup code attempted: $codeStr",
                    severity = EventSeverity.WARNING
                )
                return@launch
            }

            val door = repository.getDoor(codeEntity.doorId)
            if (door == null) {
                _errorMessage.value = "Door not found for this code"
                return@launch
            }

            if (!door.enabled) {
                _errorMessage.value = "This door is currently out of service"
                return@launch
            }

            currentDoor = door
            currentCode = codeEntity
            _doorLabel.value = door.doorLabel
            _state.value = PickupState.OPENING_DOOR
            _message.value = "Opening door ${door.doorLabel}..."

            openDoor(door)
        }
    }

    private suspend fun openDoor(door: DoorEntity) {
        val d = daemon ?: run {
            _state.value = PickupState.ERROR
            _errorMessage.value = "System error: hardware not available"
            return
        }

        val result = d.safeOpenDoor(door.lockNumber)
        when (result) {
            is SafeOpenResult.Confirmed -> {
                _state.value = PickupState.DOOR_OPEN
                _message.value = "Door ${door.doorLabel} is OPEN.\nPlease collect your parcel and close the door."
                repository.logEvent(
                    EventType.DOOR_OPENED, "KIOSK",
                    "Pickup door opened: ${door.doorLabel}",
                    doorId = door.id, lockNumber = door.lockNumber
                )
                startWaitingForClose(door)
            }
            is SafeOpenResult.OpenNotConfirmed -> {
                _state.value = PickupState.DOOR_OPEN
                _message.value = "Door ${door.doorLabel} should be open.\nPlease collect your parcel and close the door."
                startWaitingForClose(door)
            }
            is SafeOpenResult.OpenFailed -> {
                _state.value = PickupState.ERROR
                _errorMessage.value = "Failed to open door. Please try again or call for help."
                repository.logEvent(
                    EventType.DOOR_OPEN_FAILED, "KIOSK",
                    "Pickup door open failed: ${door.doorLabel} - ${result.reason}",
                    severity = EventSeverity.ERROR,
                    doorId = door.id, lockNumber = door.lockNumber
                )
            }
            is SafeOpenResult.NotConnected -> {
                _state.value = PickupState.ERROR
                _errorMessage.value = "System temporarily unavailable. Please try again."
            }
        }
    }

    private fun startWaitingForClose(door: DoorEntity) {
        closeWaitJob?.cancel()
        closeWaitJob = viewModelScope.launch {
            _state.value = PickupState.WAITING_CLOSE
            val timeout = 120_000L // 2 minute max wait
            val deadline = System.currentTimeMillis() + timeout

            while (System.currentTimeMillis() < deadline) {
                val d = daemon ?: break
                val poll = d.pollOnce()
                if (poll is PollResult.Success) {
                    if (!WinnsenCodec.isLockOpen(poll.response.stateBits, door.lockNumber)) {
                        // Door closed
                        completePickup()
                        return@launch
                    }
                }
                delay(500) // Active polling
            }

            // Timeout - door still open
            _message.value = "Please close the door."
        }
    }

    private suspend fun completePickup() {
        val door = currentDoor ?: return
        val code = currentCode ?: return

        // Mark code as used
        repository.markCodeUsed(code.id)

        // Set door to AVAILABLE
        repository.updateLogicalState(door.id, LogicalState.AVAILABLE)
        repository.updateDoorLastUsed(door.id)

        repository.logEvent(
            EventType.PARCEL_PICKED_UP, "KIOSK",
            "Parcel picked up from door ${door.doorLabel}",
            doorId = door.id, lockNumber = door.lockNumber
        )

        _state.value = PickupState.COMPLETE
        _message.value = "Pickup complete!\nThank you."
    }

    fun reset() {
        closeWaitJob?.cancel()
        _state.value = PickupState.ENTER_CODE
        _code.value = ""
        _doorLabel.value = ""
        _message.value = "Enter your pickup code"
        _errorMessage.value = null
        currentDoor = null
        currentCode = null
    }

    override fun onCleared() {
        super.onCleared()
        closeWaitJob?.cancel()
    }
}
