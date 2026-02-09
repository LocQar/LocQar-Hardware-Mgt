package com.locqar.locker.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.locqar.locker.data.db.dao.*
import com.locqar.locker.data.db.entity.*

@Database(
    entities = [
        BoardEntity::class,
        DoorEntity::class,
        AccessCodeEntity::class,
        EventLogEntity::class,
        IncidentEntity::class,
        SettingsEntity::class
    ],
    version = 1,
    exportSchema = true
)
abstract class LockerDatabase : RoomDatabase() {

    abstract fun boardDao(): BoardDao
    abstract fun doorDao(): DoorDao
    abstract fun accessCodeDao(): AccessCodeDao
    abstract fun eventLogDao(): EventLogDao
    abstract fun incidentDao(): IncidentDao
    abstract fun settingsDao(): SettingsDao

    companion object {
        @Volatile
        private var INSTANCE: LockerDatabase? = null

        fun getInstance(context: Context): LockerDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    LockerDatabase::class.java,
                    "locqar_locker.db"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
