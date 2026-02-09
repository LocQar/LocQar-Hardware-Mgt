package com.locqar.locker.ui.screens.kiosk.dropoff

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

enum class DropoffState {
    COURIER_LOGIN,
    PARCEL_DETAILS,
    ASSIGNING_DOOR,
    OPENING_DOOR,
    DOOR_OPEN,
    WAITING_CLOSE,
    COMPLETE,
    NO_DOORS_AVAILABLE,
    ERROR
}

class DropoffViewModel(app: Application) : AndroidViewModel(app) {

    private val repository = (app as LocQarApp).repository

    var daemon: LockerDaemonService? = null

    private val _state = MutableStateFlow(DropoffState.COURIER_LOGIN)
    val state: StateFlow<DropoffState> = _state.asStateFlow()

    private val _courierCode = MutableStateFlow("")
    val courierCode: StateFlow<String> = _courierCode.asStateFlow()

    private val _recipientName = MutableStateFlow("")
    val recipientName: StateFlow<String> = _recipientName.asStateFlow()

    private val _recipientPhone = MutableStateFlow("")
    val recipientPhone: StateFlow<String> = _recipientPhone.asStateFlow()

    private val _trackingNumber = MutableStateFlow("")
    val trackingNumber: StateFlow<String> = _trackingNumber.asStateFlow()

    private val _assignedDoorLabel = MutableStateFlow("")
    val assignedDoorLabel: StateFlow<String> = _assignedDoorLabel.asStateFlow()

    private val _pickupCode = MutableStateFlow("")
    val pickupCode: StateFlow<String> = _pickupCode.asStateFlow()

    private val _message = MutableStateFlow("Enter courier code")
    val message: StateFlow<String> = _message.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private var currentDoor: DoorEntity? = null
    private var closeWaitJob: Job? = null
    private var courierLoggedIn = false

    fun setCourierCode(c: String) { _courierCode.value = c }
    fun setRecipientName(n: String) { _recipientName.value = n }
    fun setRecipientPhone(p: String) { _recipientPhone.value = p }
    fun setTrackingNumber(t: String) { _trackingNumber.value = t }

    fun courierLogin() {
        viewModelScope.launch {
            val code = _courierCode.value.trim()
            if (code.isBlank()) {
                _errorMessage.value = "Enter courier code"
                return@launch
            }

            // Local placeholder: accept any non-empty code
            // In production, validate against courier database
            courierLoggedIn = true
            _state.value = DropoffState.PARCEL_DETAILS
            _message.value = "Enter parcel details"
            _errorMessage.value = null

            repository.logEvent(
                EventType.ADMIN_LOGIN, "KIOSK",
                "Courier login: $code"
            )
        }
    }

    fun submitParcelDetails() {
        viewModelScope.launch {
            if (_recipientName.value.isBlank()) {
                _errorMessage.value = "Recipient name required"
                return@launch
            }

            _errorMessage.value = null
            _state.value = DropoffState.ASSIGNING_DOOR
            _message.value = "Finding available door..."

            // Expire old reservations
            repository.expireReservations()

            // Find best door
            val door = repository.findBestAvailableDoor()
            if (door == null) {
                _state.value = DropoffState.NO_DOORS_AVAILABLE
                _message.value = "No doors available. Please try later."
                return@launch
            }

            // Reserve atomically (2 minutes)
            val reservationMs = repository.getSettingLong(SettingsKeys.RESERVATION_TIMEOUT_SECONDS) * 1000
            val reserved = repository.reserveDoor(door.id, reservationMs, "COURIER")
            if (!reserved) {
                _state.value = DropoffState.NO_DOORS_AVAILABLE
                _message.value = "Door reservation conflict. Please try again."
                return@launch
            }

            currentDoor = door
            _assignedDoorLabel.value = door.doorLabel
            _state.value = DropoffState.OPENING_DOOR
            _message.value = "Opening door ${door.doorLabel}..."

            openDoor(door)
        }
    }

    private suspend fun openDoor(door: DoorEntity) {
        val d = daemon ?: run {
            _state.value = DropoffState.ERROR
            _errorMessage.value = "System error"
            return
        }

        val result = d.safeOpenDoor(door.lockNumber)
        when (result) {
            is SafeOpenResult.Confirmed -> {
                _state.value = DropoffState.DOOR_OPEN
                _message.value = "Door ${door.doorLabel} is OPEN.\nPlace the parcel inside and close the door."
                repository.logEvent(
                    EventType.DOOR_OPENED, "KIOSK",
                    "Drop-off door opened: ${door.doorLabel}",
                    doorId = door.id, lockNumber = door.lockNumber
                )
                startWaitingForClose(door)
            }
            is SafeOpenResult.OpenNotConfirmed -> {
                _state.value = DropoffState.DOOR_OPEN
                _message.value = "Door ${door.doorLabel} should be open.\nPlace the parcel inside and close the door."
                startWaitingForClose(door)
            }
            is SafeOpenResult.OpenFailed -> {
                repository.clearReservation(door.id)
                _state.value = DropoffState.ERROR
                _errorMessage.value = "Failed to open door. Please try again."
                repository.logEvent(
                    EventType.DOOR_OPEN_FAILED, "KIOSK",
                    "Drop-off door open failed: ${door.doorLabel}",
                    severity = EventSeverity.ERROR,
                    doorId = door.id, lockNumber = door.lockNumber
                )
            }
            is SafeOpenResult.NotConnected -> {
                repository.clearReservation(door.id)
                _state.value = DropoffState.ERROR
                _errorMessage.value = "System unavailable"
            }
        }
    }

    private fun startWaitingForClose(door: DoorEntity) {
        closeWaitJob?.cancel()
        closeWaitJob = viewModelScope.launch {
            _state.value = DropoffState.WAITING_CLOSE
            val timeout = 120_000L
            val deadline = System.currentTimeMillis() + timeout

            while (System.currentTimeMillis() < deadline) {
                val d = daemon ?: break
                val poll = d.pollOnce()
                if (poll is PollResult.Success) {
                    if (!WinnsenCodec.isLockOpen(poll.response.stateBits, door.lockNumber)) {
                        completeDropoff()
                        return@launch
                    }
                }
                delay(500)
            }

            _message.value = "Please close the door."
        }
    }

    private suspend fun completeDropoff() {
        val door = currentDoor ?: return

        // Mark OCCUPIED
        repository.updateLogicalState(door.id, LogicalState.OCCUPIED)
        repository.updateDoorLastUsed(door.id)

        // Generate pickup code
        val code = generatePickupCode()
        repository.createAccessCode(
            AccessCodeEntity(
                code = code,
                doorId = door.id,
                type = CodeType.PICKUP,
                parcelId = _trackingNumber.value.ifBlank { null },
                createdBy = "COURIER:${_courierCode.value}"
            )
        )

        _pickupCode.value = code
        _state.value = DropoffState.COMPLETE
        _message.value = "Parcel stored in door ${door.doorLabel}.\nPickup code: $code"

        repository.logEvent(
            EventType.PARCEL_DROPPED, "KIOSK",
            "Parcel dropped in door ${door.doorLabel}. Code: $code. Recipient: ${_recipientName.value}",
            doorId = door.id, lockNumber = door.lockNumber
        )
    }

    private fun generatePickupCode(): String {
        val chars = "0123456789"
        return (1..6).map { chars.random() }.joinToString("")
    }

    fun dropoffAnother() {
        closeWaitJob?.cancel()
        currentDoor = null
        _recipientName.value = ""
        _recipientPhone.value = ""
        _trackingNumber.value = ""
        _assignedDoorLabel.value = ""
        _pickupCode.value = ""
        _errorMessage.value = null
        _state.value = DropoffState.PARCEL_DETAILS
        _message.value = "Enter parcel details"
    }

    fun reset() {
        closeWaitJob?.cancel()
        _state.value = DropoffState.COURIER_LOGIN
        _courierCode.value = ""
        _recipientName.value = ""
        _recipientPhone.value = ""
        _trackingNumber.value = ""
        _assignedDoorLabel.value = ""
        _pickupCode.value = ""
        _message.value = "Enter courier code"
        _errorMessage.value = null
        currentDoor = null
        courierLoggedIn = false
    }

    override fun onCleared() {
        super.onCleared()
        closeWaitJob?.cancel()
    }
}
