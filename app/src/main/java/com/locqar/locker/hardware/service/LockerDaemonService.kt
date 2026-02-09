package com.locqar.locker.hardware.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.locqar.locker.MainActivity
import com.locqar.locker.R
import com.locqar.locker.hardware.codec.WinnsenCodec
import com.locqar.locker.hardware.controller.*
import com.locqar.locker.hardware.demo.DemoLockerController
import com.locqar.locker.hardware.serial.SerialManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

/**
 * Foreground service that owns the serial connection and manages polling.
 * Provides the LockerController to the rest of the app via binding.
 */
class LockerDaemonService : Service() {

    companion object {
        const val CHANNEL_ID = "locker_daemon"
        const val NOTIFICATION_ID = 1001
        const val ACTION_STOP = "com.locqar.locker.STOP_DAEMON"
    }

    inner class DaemonBinder : Binder() {
        val service: LockerDaemonService get() = this@LockerDaemonService
    }

    private val binder = DaemonBinder()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    lateinit var serialManager: SerialManager
        private set

    private var _controller: LockerController? = null
    val controller: LockerController get() = _controller!!

    private var _demoMode = MutableStateFlow(false)
    val demoMode: StateFlow<Boolean> = _demoMode.asStateFlow()

    // Polling state
    private var pollingJob: Job? = null
    private val _pollIntervalMs = MutableStateFlow(5000L)
    val pollIntervalMs: StateFlow<Long> = _pollIntervalMs.asStateFlow()

    private val _stationNumber = MutableStateFlow(1)
    val stationNumber: StateFlow<Int> = _stationNumber.asStateFlow()

    private val _lastPollResult = MutableStateFlow<PollResult?>(null)
    val lastPollResult: StateFlow<PollResult?> = _lastPollResult.asStateFlow()

    private val _doorStates = MutableStateFlow<Map<Int, Boolean>>(emptyMap())
    val doorStates: StateFlow<Map<Int, Boolean>> = _doorStates.asStateFlow()

    private val _isStationOnline = MutableStateFlow(false)
    val isStationOnline: StateFlow<Boolean> = _isStationOnline.asStateFlow()

    private val _isUsbConnected = MutableStateFlow(false)
    val isUsbConnected: StateFlow<Boolean> = _isUsbConnected.asStateFlow()

    private var consecutiveTimeouts = 0

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
        serialManager = SerialManager(applicationContext)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onDestroy() {
        scope.cancel()
        pollingJob?.cancel()
        serialManager.destroy()
        super.onDestroy()
    }

    fun setDemoMode(enabled: Boolean) {
        _demoMode.value = enabled
        if (enabled) {
            _controller = DemoLockerController()
            _isStationOnline.value = true
            _isUsbConnected.value = true
        } else {
            _controller = LockerControllerImpl(serialManager)
            _isStationOnline.value = serialManager.isConnected
            _isUsbConnected.value = serialManager.isConnected
        }
    }

    fun connectSerial(): Boolean {
        if (_demoMode.value) return true
        val result = serialManager.connect()
        if (result) {
            _controller = LockerControllerImpl(serialManager)
            _isStationOnline.value = true
            _isUsbConnected.value = true
            consecutiveTimeouts = 0
        }
        return result
    }

    fun disconnectSerial() {
        stopPolling()
        serialManager.disconnect()
        _isStationOnline.value = false
        _isUsbConnected.value = false
    }

    fun setStationNumber(station: Int) {
        _stationNumber.value = station
    }

    fun setPollInterval(intervalMs: Long) {
        _pollIntervalMs.value = intervalMs
    }

    /**
     * Start background polling at the configured interval.
     */
    fun startPolling() {
        pollingJob?.cancel()
        pollingJob = scope.launch {
            while (isActive) {
                pollOnce()
                delay(_pollIntervalMs.value)
            }
        }
    }

    fun stopPolling() {
        pollingJob?.cancel()
        pollingJob = null
    }

    /**
     * Execute a single poll and update state flows.
     */
    suspend fun pollOnce(): PollResult {
        val ctrl = _controller ?: return PollResult.NotConnected
        val result = ctrl.pollStation(_stationNumber.value)
        _lastPollResult.value = result

        when (result) {
            is PollResult.Success -> {
                consecutiveTimeouts = 0
                _isStationOnline.value = true
                _doorStates.value = result.response.doorStates
            }
            is PollResult.Timeout -> {
                consecutiveTimeouts++
                if (consecutiveTimeouts >= 3) {
                    _isStationOnline.value = false
                }
            }
            is PollResult.NotConnected -> {
                _isStationOnline.value = false
            }
            is PollResult.Error -> {
                consecutiveTimeouts++
                if (consecutiveTimeouts >= 3) {
                    _isStationOnline.value = false
                }
            }
        }
        return result
    }

    /**
     * Open a door with the current station.
     */
    suspend fun openDoor(lock: Int): OpenResult {
        val ctrl = _controller ?: return OpenResult.NotConnected
        return ctrl.openDoor(_stationNumber.value, lock)
    }

    /**
     * Safe open a door: open + confirm via polling.
     */
    suspend fun safeOpenDoor(lock: Int, confirmTimeoutMs: Long = 5000L): SafeOpenResult {
        val ctrl = _controller ?: return SafeOpenResult.NotConnected
        return ctrl.safeOpenDoor(_stationNumber.value, lock, confirmTimeoutMs)
    }

    /**
     * Poll rapidly (active polling) and emit updates.
     */
    fun startActivePoll(intervalMs: Long = 500L): Job {
        return scope.launch {
            while (isActive) {
                pollOnce()
                delay(intervalMs)
            }
        }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notification_channel_daemon),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Locker control service"
            setShowBadge(false)
        }
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        val stopIntent = PendingIntent.getService(
            this, 1,
            Intent(this, LockerDaemonService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_daemon_title))
            .setContentText(getString(R.string.notification_daemon_text))
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentIntent(pendingIntent)
            .addAction(android.R.drawable.ic_delete, "Stop", stopIntent)
            .setOngoing(true)
            .build()
    }
}
