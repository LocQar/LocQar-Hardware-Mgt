package com.locqar.locker.data.db.dao

import androidx.room.*
import com.locqar.locker.data.db.entity.IncidentEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface IncidentDao {

    @Query("SELECT * FROM incidents ORDER BY timestamp DESC")
    fun observeAll(): Flow<List<IncidentEntity>>

    @Query("SELECT * FROM incidents WHERE resolved = 0 ORDER BY timestamp DESC")
    fun observeUnresolved(): Flow<List<IncidentEntity>>

    @Query("SELECT * FROM incidents WHERE resolved = 0")
    suspend fun getUnresolved(): List<IncidentEntity>

    @Query("SELECT * FROM incidents WHERE doorId = :doorId AND resolved = 0")
    suspend fun getUnresolvedForDoor(doorId: Long): List<IncidentEntity>

    @Insert
    suspend fun insert(incident: IncidentEntity): Long

    @Query("UPDATE incidents SET resolved = 1, resolvedAt = :now, resolvedBy = :by, resolution = :resolution WHERE id = :id")
    suspend fun resolve(id: Long, by: String = "SYSTEM", resolution: String = "", now: Long = System.currentTimeMillis())

    @Query("DELETE FROM incidents")
    suspend fun deleteAll()
}
