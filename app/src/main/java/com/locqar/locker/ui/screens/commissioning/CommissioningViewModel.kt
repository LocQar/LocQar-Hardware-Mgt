package com.locqar.locker.ui.screens.commissioning

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

data class DoorMapping(
    val lockNumber: Int,
    val doorLabel: String?,  // null = skipped/disabled
    val verified: Boolean = false
)

enum class WizardStep {
    STATION_SETUP,
    DOOR_MAPPING,
    DOOR_VERIFICATION,
    KIOSK_SETTINGS,
    ADMIN_SECURITY,
    SUMMARY
}

class CommissioningViewModel(app: Application) : AndroidViewModel(app) {

    private val repository = (app as LocQarApp).repository

    var daemon: LockerDaemonService? = null

    private val _currentStep = MutableStateFlow(WizardStep.STATION_SETUP)
    val currentStep: StateFlow<WizardStep> = _currentStep.asStateFlow()

    private val _stationNumber = MutableStateFlow(1)
    val stationNumber: StateFlow<Int> = _stationNumber.asStateFlow()

    private val _stationOnline = MutableStateFlow(false)
    val stationOnline: StateFlow<Boolean> = _stationOnline.asStateFlow()

    private val _statusMessage = MutableStateFlow("")
    val statusMessage: StateFlow<String> = _statusMessage.asStateFlow()

    // Door mapping state
    private val _currentMappingLock = MutableStateFlow(1)
    val currentMappingLock: StateFlow<Int> = _currentMappingLock.asStateFlow()

    private val _mappings = MutableStateFlow<List<DoorMapping>>(emptyList())
    val mappings: StateFlow<List<DoorMapping>> = _mappings.asStateFlow()

    private val _assignedLabels = MutableStateFlow<Set<String>>(emptySet())
    val assignedLabels: StateFlow<Set<String>> = _assignedLabels.asStateFlow()

    private val _waitingForDoorClose = MutableStateFlow(false)
    val waitingForDoorClose: StateFlow<Boolean> = _waitingForDoorClose.asStateFlow()

    private val _doorIsOpen = MutableStateFlow(false)
    val doorIsOpen: StateFlow<Boolean> = _doorIsOpen.asStateFlow()

    // Settings
    private val _lockerName = MutableStateFlow("LocQar Locker")
    val lockerName: StateFlow<String> = _lockerName.asStateFlow()

    private val _helpPhone = MutableStateFlow("")
    val helpPhone: StateFlow<String> = _helpPhone.asStateFlow()

    private val _idlePollInterval = MutableStateFlow(5000)
    val idlePollInterval: StateFlow<Int> = _idlePollInterval.asStateFlow()

    private val _openConfirmSeconds = MutableStateFlow(5)
    val openConfirmSeconds: StateFlow<Int> = _openConfirmSeconds.asStateFlow()

    private val _openTooLongSeconds = MutableStateFlow(60)
    val openTooLongSeconds: StateFlow<Int> = _openTooLongSeconds.asStateFlow()

    private val _adminPassword = MutableStateFlow("")
    private val _adminPasswordConfirm = MutableStateFlow("")
    private val _passwordError = MutableStateFlow<String?>(null)
    val passwordError: StateFlow<String?> = _passwordError.asStateFlow()

    fun setStationNumber(num: Int) {
        _stationNumber.value = num
    }

    fun setLockerName(name: String) { _lockerName.value = name }
    fun setHelpPhone(phone: String) { _helpPhone.value = phone }
    fun setIdlePollInterval(ms: Int) { _idlePollInterval.value = ms }
    fun setOpenConfirmSeconds(s: Int) { _openConfirmSeconds.value = s }
    fun setOpenTooLongSeconds(s: Int) { _openTooLongSeconds.value = s }
    fun setAdminPassword(pw: String) { _adminPassword.value = pw }
    fun setAdminPasswordConfirm(pw: String) { _adminPasswordConfirm.value = pw }

    fun testPoll() {
        viewModelScope.launch {
            val d = daemon ?: return@launch
            d.setStationNumber(_stationNumber.value)
            _statusMessage.value = "Polling station ${_stationNumber.value}..."
            val result = d.pollOnce()
            when (result) {
                is PollResult.Success -> {
                    _stationOnline.value = true
                    _statusMessage.value = "Station online! Bits: 0x${"%04X".format(result.response.stateBits)}"
                }
                is PollResult.Timeout -> {
                    _stationOnline.value = false
                    _statusMessage.value = "Station offline (timeout)"
                }
                is PollResult.NotConnected -> {
                    _stationOnline.value = false
                    _statusMessage.value = "USB not connected"
                }
                is PollResult.Error -> {
                    _stationOnline.value = false
                    _statusMessage.value = "Error: ${result.message}"
                }
            }
        }
    }

    fun nextStep() {
        val steps = WizardStep.entries
        val idx = steps.indexOf(_currentStep.value)
        if (idx < steps.size - 1) {
            _currentStep.value = steps[idx + 1]
        }
    }

    fun previousStep() {
        val steps = WizardStep.entries
        val idx = steps.indexOf(_currentStep.value)
        if (idx > 0) {
            _currentStep.value = steps[idx - 1]
        }
    }

    fun goToStep(step: WizardStep) {
        _currentStep.value = step
    }

    /**
     * Start door mapping for the current lock.
     * Safe opens the lock and waits for the installer to identify which door opened.
     */
    fun safeOpenCurrentLock() {
        viewModelScope.launch {
            val d = daemon ?: return@launch
            val lock = _currentMappingLock.value
            _statusMessage.value = "Opening lock $lock..."
            _doorIsOpen.value = false

            val result = d.safeOpenDoor(lock)
            when (result) {
                is SafeOpenResult.Confirmed -> {
                    _doorIsOpen.value = true
                    _statusMessage.value = "Lock $lock is OPEN. Tap the door label that opened."
                }
                is SafeOpenResult.OpenNotConfirmed -> {
                    _doorIsOpen.value = true // Might still be open
                    _statusMessage.value = "Lock $lock: open sent but not confirmed. Check which door opened."
                }
                is SafeOpenResult.OpenFailed -> {
                    _statusMessage.value = "Lock $lock open failed: ${result.reason}"
                }
                is SafeOpenResult.NotConnected -> {
                    _statusMessage.value = "Not connected"
                }
            }
        }
    }

    /**
     * Assign a door label to the current lock.
     */
    fun assignLabel(doorLabel: String) {
        if (doorLabel in _assignedLabels.value) {
            _statusMessage.value = "Label '$doorLabel' already assigned!"
            return
        }

        val lock = _currentMappingLock.value
        val newMapping = DoorMapping(lock, doorLabel)
        val updated = _mappings.value.toMutableList()
        updated.removeAll { it.lockNumber == lock }
        updated.add(newMapping)
        _mappings.value = updated

        _assignedLabels.value = _assignedLabels.value + doorLabel
        _statusMessage.value = "Lock $lock mapped to door '$doorLabel'. Close the door."
        _waitingForDoorClose.value = true

        // Start polling for door close
        viewModelScope.launch {
            val d = daemon ?: return@launch
            val timeout = System.currentTimeMillis() + 30000
            while (System.currentTimeMillis() < timeout) {
                val poll = d.pollOnce()
                if (poll is PollResult.Success) {
                    if (!WinnsenCodec.isLockOpen(poll.response.stateBits, lock)) {
                        _waitingForDoorClose.value = false
                        _doorIsOpen.value = false
                        advanceToNextLock()
                        return@launch
                    }
                }
                delay(500)
            }
            _waitingForDoorClose.value = false
            _statusMessage.value = "Door not closed within timeout. Moving on."
            advanceToNextLock()
        }
    }

    fun skipCurrentLock() {
        val lock = _currentMappingLock.value
        val updated = _mappings.value.toMutableList()
        updated.removeAll { it.lockNumber == lock }
        updated.add(DoorMapping(lock, null)) // skipped
        _mappings.value = updated
        advanceToNextLock()
    }

    fun undoLastMapping() {
        val updated = _mappings.value.toMutableList()
        if (updated.isNotEmpty()) {
            val last = updated.removeLast()
            _mappings.value = updated
            if (last.doorLabel != null) {
                _assignedLabels.value = _assignedLabels.value - last.doorLabel
            }
            _currentMappingLock.value = last.lockNumber
            _statusMessage.value = "Undid mapping for lock ${last.lockNumber}"
        }
    }

    private fun advanceToNextLock() {
        val next = _currentMappingLock.value + 1
        if (next <= WinnsenCodec.MAX_DOORS) {
            _currentMappingLock.value = next
            _statusMessage.value = "Ready for lock $next"
        } else {
            _statusMessage.value = "All locks mapped! Review below."
        }
    }

    /**
     * Verify a specific door by label: open it and confirm the correct physical door opens.
     */
    fun verifyDoorByLabel(label: String) {
        viewModelScope.launch {
            val d = daemon ?: return@launch
            val mapping = _mappings.value.find { it.doorLabel == label }
            if (mapping == null) {
                _statusMessage.value = "No mapping for label '$label'"
                return@launch
            }
            _statusMessage.value = "Verifying door '$label' (lock ${mapping.lockNumber})..."
            val result = d.safeOpenDoor(mapping.lockNumber)
            val updated = _mappings.value.toMutableList()
            val idx = updated.indexOfFirst { it.lockNumber == mapping.lockNumber }
            when (result) {
                is SafeOpenResult.Confirmed -> {
                    if (idx >= 0) updated[idx] = updated[idx].copy(verified = true)
                    _statusMessage.value = "Door '$label' verified OK"
                }
                else -> {
                    _statusMessage.value = "Door '$label' verification issue"
                }
            }
            _mappings.value = updated
        }
    }

    fun validateAdminPassword(): Boolean {
        val pw = _adminPassword.value
        val confirm = _adminPasswordConfirm.value
        return when {
            pw.length < 6 -> { _passwordError.value = "Password must be at least 6 characters"; false }
            pw != confirm -> { _passwordError.value = "Passwords don't match"; false }
            pw == "admin" -> { _passwordError.value = "Cannot use default password"; false }
            else -> { _passwordError.value = null; true }
        }
    }

    /**
     * Save all commissioning data.
     */
    fun finishCommissioning() {
        viewModelScope.launch {
            try {
                // Save board
                val boardId = repository.saveBoard(
                    BoardEntity(stationNumber = _stationNumber.value, maxDoors = WinnsenCodec.MAX_DOORS)
                )

                // Save door mappings
                repository.deleteAllDoors()
                val board = repository.getBoard(_stationNumber.value) ?: return@launch
                _mappings.value.filter { it.doorLabel != null }.forEach { mapping ->
                    repository.saveDoor(
                        DoorEntity(
                            boardId = board.id,
                            lockNumber = mapping.lockNumber,
                            doorLabel = mapping.doorLabel!!,
                            enabled = true
                        )
                    )
                }

                // Save settings
                repository.setSetting(SettingsKeys.LOCKER_NAME, _lockerName.value)
                repository.setSetting(SettingsKeys.HELP_PHONE, _helpPhone.value)
                repository.setSetting(SettingsKeys.IDLE_POLL_INTERVAL_MS, _idlePollInterval.value.toString())
                repository.setSetting(SettingsKeys.OPEN_CONFIRM_SECONDS, _openConfirmSeconds.value.toString())
                repository.setSetting(SettingsKeys.OPEN_TOO_LONG_SECONDS, _openTooLongSeconds.value.toString())
                repository.setSetting(SettingsKeys.STATION_NUMBER, _stationNumber.value.toString())

                // Save admin password
                if (_adminPassword.value.isNotBlank()) {
                    repository.setAdminPassword(_adminPassword.value)
                }

                repository.setSetting(SettingsKeys.COMMISSIONING_COMPLETE, "true")

                repository.logEvent(
                    EventType.COMMISSIONING, "ADMIN",
                    "Commissioning completed. Station: ${_stationNumber.value}, Doors: ${_mappings.value.count { it.doorLabel != null }}",
                    stationNumber = _stationNumber.value
                )

                _statusMessage.value = "Commissioning complete!"
            } catch (e: Exception) {
                _statusMessage.value = "Error saving: ${e.message}"
            }
        }
    }

    fun exportConfig() {
        viewModelScope.launch {
            val uri = ExportUtil.exportConfig(getApplication(), repository)
            _statusMessage.value = if (uri != null) "Config exported" else "Export failed"
        }
    }

    fun importConfig(jsonStr: String) {
        viewModelScope.launch {
            val result = ExportUtil.importConfig(repository, jsonStr)
            _statusMessage.value = when (result) {
                is com.locqar.locker.util.ImportResult.Success ->
                    "Imported: ${result.doorsImported} doors, ${result.settingsImported} settings"
                is com.locqar.locker.util.ImportResult.Error -> result.message
            }
        }
    }
}
