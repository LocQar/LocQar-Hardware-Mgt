package com.locqar.locker.data.db.dao

import androidx.room.*
import com.locqar.locker.data.db.entity.SettingsEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SettingsDao {

    @Query("SELECT * FROM settings WHERE `key` = :key LIMIT 1")
    suspend fun get(key: String): SettingsEntity?

    @Query("SELECT value FROM settings WHERE `key` = :key LIMIT 1")
    suspend fun getValue(key: String): String?

    @Query("SELECT * FROM settings")
    fun observeAll(): Flow<List<SettingsEntity>>

    @Query("SELECT * FROM settings")
    suspend fun getAll(): List<SettingsEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(setting: SettingsEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(settings: List<SettingsEntity>)

    @Query("DELETE FROM settings WHERE `key` = :key")
    suspend fun delete(key: String)

    @Query("DELETE FROM settings")
    suspend fun deleteAll()
}
