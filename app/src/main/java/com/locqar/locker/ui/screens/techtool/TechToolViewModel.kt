package com.locqar.locker.ui.screens.techtool

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.locqar.locker.LocQarApp
import com.locqar.locker.data.db.entity.EventSeverity
import com.locqar.locker.data.db.entity.EventType
import com.locqar.locker.hardware.codec.WinnsenCodec
import com.locqar.locker.hardware.controller.*
import com.locqar.locker.hardware.service.LockerDaemonService
import com.locqar.locker.util.ExportUtil
import com.locqar.locker.util.TestLockResult
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

class TechToolViewModel(app: Application) : AndroidViewModel(app) {

    private val repository = (app as LocQarApp).repository

    var daemon: LockerDaemonService? = null
        set(value) {
            field = value
            if (value != null) {
                viewModelScope.launch {
                    value.isUsbConnected.collect { _usbConnected.value = it }
                }
                viewModelScope.launch {
                    value.doorStates.collect { _doorStates.value = it }
                }
                viewModelScope.launch {
                    value.isStationOnline.collect { _stationOnline.value = it }
                }
            }
        }

    private val _usbConnected = MutableStateFlow(false)
    val usbConnected: StateFlow<Boolean> = _usbConnected.asStateFlow()

    private val _stationNumber = MutableStateFlow(1)
    val stationNumber: StateFlow<Int> = _stationNumber.asStateFlow()

    private val _doorStates = MutableStateFlow<Map<Int, Boolean>>(emptyMap())
    val doorStates: StateFlow<Map<Int, Boolean>> = _doorStates.asStateFlow()

    private val _stationOnline = MutableStateFlow(false)
    val stationOnline: StateFlow<Boolean> = _stationOnline.asStateFlow()

    private val _livePolling = MutableStateFlow(false)
    val livePolling: StateFlow<Boolean> = _livePolling.asStateFlow()

    private val _statusMessage = MutableStateFlow("")
    val statusMessage: StateFlow<String> = _statusMessage.asStateFlow()

    private val _testResults = MutableStateFlow<List<TestLockResult>>(emptyList())
    val testResults: StateFlow<List<TestLockResult>> = _testResults.asStateFlow()

    private val _testRunning = MutableStateFlow(false)
    val testRunning: StateFlow<Boolean> = _testRunning.asStateFlow()

    private val _lastPollHex = MutableStateFlow("")
    val lastPollHex: StateFlow<String> = _lastPollHex.asStateFlow()

    private var livePollJob: Job? = null

    fun setStationNumber(station: Int) {
        _stationNumber.value = station
        daemon?.setStationNumber(station)
    }

    fun connectUsb() {
        viewModelScope.launch(Dispatchers.IO) {
            val d = daemon ?: return@launch
            val result = d.connectSerial()
            _statusMessage.value = if (result) "USB connected" else "USB connection failed: ${d.serialManager.errorMessage.value}"
            _usbConnected.value = d.serialManager.isConnected || d.demoMode.value
        }
    }

    fun disconnectUsb() {
        daemon?.disconnectSerial()
        _usbConnected.value = false
        _statusMessage.value = "USB disconnected"
    }

    fun pollNow() {
        viewModelScope.launch {
            val d = daemon ?: return@launch
            _statusMessage.value = "Polling station ${_stationNumber.value}..."
            val result = d.pollOnce()
            when (result) {
                is PollResult.Success -> {
                    _statusMessage.value = "Poll OK: bits=0x${"%04X".format(result.response.stateBits)}"
                    _lastPollHex.value = "State bits: 0x${"%04X".format(result.response.stateBits)}"
                }
                is PollResult.Timeout -> _statusMessage.value = "Poll timeout"
                is PollResult.NotConnected -> _statusMessage.value = "Not connected"
                is PollResult.Error -> _statusMessage.value = "Poll error: ${result.message}"
            }
        }
    }

    fun toggleLivePoll() {
        if (_livePolling.value) {
            stopLivePoll()
        } else {
            startLivePoll()
        }
    }

    private fun startLivePoll() {
        _livePolling.value = true
        livePollJob = viewModelScope.launch {
            while (isActive) {
                val d = daemon ?: break
                d.pollOnce()
                delay(2000)
            }
        }
    }

    private fun stopLivePoll() {
        livePollJob?.cancel()
        livePollJob = null
        _livePolling.value = false
    }

    fun openLock(lock: Int) {
        viewModelScope.launch {
            val d = daemon ?: return@launch
            _statusMessage.value = "Opening lock $lock..."
            val result = d.openDoor(lock)
            _statusMessage.value = when (result) {
                is OpenResult.Success -> "Lock $lock opened successfully"
                is OpenResult.Failed -> "Lock $lock open FAILED (status=00)"
                is OpenResult.Timeout -> "Lock $lock open timeout"
                is OpenResult.NotConnected -> "Not connected"
                is OpenResult.Error -> "Lock $lock error: ${result.message}"
            }
            repository.logEvent(
                EventType.TEST_EXECUTED, "TECH_TOOL",
                "Manual open lock $lock: ${_statusMessage.value}",
                lockNumber = lock, stationNumber = _stationNumber.value
            )
        }
    }

    fun safeOpenLock(lock: Int) {
        viewModelScope.launch {
            val d = daemon ?: return@launch
            _statusMessage.value = "Safe opening lock $lock..."
            val result = d.safeOpenDoor(lock)
            _statusMessage.value = when (result) {
                is SafeOpenResult.Confirmed -> "Lock $lock: opened & confirmed"
                is SafeOpenResult.OpenNotConfirmed -> "Lock $lock: opened but NOT confirmed (check wiring)"
                is SafeOpenResult.OpenFailed -> "Lock $lock: open failed - ${result.reason}"
                is SafeOpenResult.NotConnected -> "Not connected"
            }
            repository.logEvent(
                EventType.TEST_EXECUTED, "TECH_TOOL",
                "Safe open lock $lock: ${_statusMessage.value}",
                lockNumber = lock, stationNumber = _stationNumber.value
            )
        }
    }

    fun testAllLocks(confirmOpen: Boolean, waitClose: Boolean) {
        if (_testRunning.value) return
        _testRunning.value = true
        _testResults.value = emptyList()

        viewModelScope.launch {
            val d = daemon ?: run { _testRunning.value = false; return@launch }
            val results = mutableListOf<TestLockResult>()

            for (lock in 1..WinnsenCodec.MAX_DOORS) {
                _statusMessage.value = "Testing lock $lock of ${WinnsenCodec.MAX_DOORS}..."
                val start = System.currentTimeMillis()

                try {
                    if (confirmOpen) {
                        val result = d.safeOpenDoor(lock)
                        val duration = System.currentTimeMillis() - start
                        val testResult = when (result) {
                            is SafeOpenResult.Confirmed -> TestLockResult(
                                lock, true, true, true,
                                closedAfterTest = false, durationMs = duration
                            )
                            is SafeOpenResult.OpenNotConfirmed -> TestLockResult(
                                lock, true, true, false,
                                errorMessage = "Open not confirmed", durationMs = duration
                            )
                            is SafeOpenResult.OpenFailed -> TestLockResult(
                                lock, true, false, false,
                                errorMessage = result.reason, durationMs = duration
                            )
                            is SafeOpenResult.NotConnected -> TestLockResult(
                                lock, false, false, false,
                                errorMessage = "Not connected", durationMs = duration
                            )
                        }

                        var finalResult = testResult
                        if (waitClose && testResult.openConfirmed) {
                            _statusMessage.value = "Lock $lock: waiting for close..."
                            val closed = waitForClose(d, lock, 30000)
                            finalResult = testResult.copy(closedAfterTest = closed)
                        }
                        results.add(finalResult)
                    } else {
                        val openResult = d.openDoor(lock)
                        val duration = System.currentTimeMillis() - start
                        results.add(
                            when (openResult) {
                                is OpenResult.Success -> TestLockResult(lock, true, true, durationMs = duration)
                                is OpenResult.Failed -> TestLockResult(lock, true, false, errorMessage = "status=00", durationMs = duration)
                                is OpenResult.Timeout -> TestLockResult(lock, true, false, errorMessage = "Timeout", durationMs = duration)
                                is OpenResult.NotConnected -> TestLockResult(lock, false, false, errorMessage = "Not connected", durationMs = duration)
                                is OpenResult.Error -> TestLockResult(lock, true, false, errorMessage = openResult.message, durationMs = duration)
                            }
                        )
                    }
                } catch (e: Exception) {
                    results.add(TestLockResult(lock, false, false, errorMessage = e.message, durationMs = System.currentTimeMillis() - start))
                }

                _testResults.value = results.toList()
                delay(500) // Brief pause between locks
            }

            _statusMessage.value = "Test complete: ${results.count { it.openSuccess }}/${WinnsenCodec.MAX_DOORS} passed"
            _testRunning.value = false

            repository.logEvent(
                EventType.TEST_EXECUTED, "TECH_TOOL",
                "Test all: ${results.count { it.openSuccess }}/${WinnsenCodec.MAX_DOORS} passed",
                stationNumber = _stationNumber.value
            )
        }
    }

    private suspend fun waitForClose(daemon: LockerDaemonService, lock: Int, timeoutMs: Long): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            val result = daemon.pollOnce()
            if (result is PollResult.Success) {
                if (!WinnsenCodec.isLockOpen(result.response.stateBits, lock)) {
                    return true
                }
            }
            delay(500)
        }
        return false
    }

    fun exportTestReport(): Uri? {
        return ExportUtil.exportTestReport(
            getApplication(),
            _stationNumber.value,
            WinnsenCodec.MAX_DOORS,
            _testResults.value
        )
    }

    fun exportLogs() {
        viewModelScope.launch {
            val events = repository.getAllEvents()
            val uri = ExportUtil.exportLogsCsv(getApplication(), events)
            _statusMessage.value = if (uri != null) "Logs exported to Downloads/LocQarLocker" else "Export failed"
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopLivePoll()
    }
}
