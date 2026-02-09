package com.locqar.locker

import android.app.Application
import com.locqar.locker.data.db.LockerDatabase
import com.locqar.locker.data.repository.LockerRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class LocQarApp : Application() {

    val database: LockerDatabase by lazy { LockerDatabase.getInstance(this) }
    val repository: LockerRepository by lazy { LockerRepository(database) }

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        instance = this

        // Initialize default settings
        appScope.launch {
            repository.initializeDefaults()
        }
    }

    companion object {
        lateinit var instance: LocQarApp
            private set
    }
}
