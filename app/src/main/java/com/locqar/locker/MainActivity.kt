package com.locqar.locker

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.core.content.ContextCompat
import androidx.navigation.compose.rememberNavController
import com.locqar.locker.data.db.entity.EventType
import com.locqar.locker.data.db.entity.SettingsKeys
import com.locqar.locker.hardware.service.LockerDaemonService
import com.locqar.locker.ui.navigation.AppNavHost
import com.locqar.locker.ui.theme.LocQarTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private var daemonService: LockerDaemonService? = null
    private var bound = false

    private val daemonState = mutableStateOf<LockerDaemonService?>(null)

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as LockerDaemonService.DaemonBinder
            daemonService = binder.service
            daemonState.value = binder.service
            bound = true

            // Initialize from settings
            CoroutineScope(Dispatchers.IO).launch {
                val repo = (application as LocQarApp).repository
                val station = repo.getSettingInt(SettingsKeys.STATION_NUMBER).let { if (it == 0) 1 else it }
                val demoMode = repo.getSettingBool(SettingsKeys.DEMO_MODE)
                val pollInterval = repo.getSettingLong(SettingsKeys.IDLE_POLL_INTERVAL_MS).let { if (it == 0L) 5000L else it }

                binder.service.setStationNumber(station)
                binder.service.setDemoMode(demoMode)
                binder.service.setPollInterval(pollInterval)

                if (demoMode) {
                    binder.service.startPolling()
                }

                repo.logEvent(EventType.SYSTEM_START, "SYSTEM", "App started. Station: $station, Demo: $demoMode")
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            daemonService = null
            daemonState.value = null
            bound = false
        }
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* Notification permission result - app works either way */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Keep screen on for kiosk mode
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // Request notification permission for Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        // Start and bind to daemon service
        val serviceIntent = Intent(this, LockerDaemonService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
        bindService(serviceIntent, connection, Context.BIND_AUTO_CREATE)

        setContent {
            LocQarTheme {
                val navController = rememberNavController()
                val daemon by daemonState

                AppNavHost(
                    navController = navController,
                    daemon = daemon
                )
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (bound) {
            unbindService(connection)
            bound = false
        }
    }
}
