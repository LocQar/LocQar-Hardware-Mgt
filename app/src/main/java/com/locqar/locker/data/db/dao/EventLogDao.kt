package com.locqar.locker.data.db.dao

import androidx.room.*
import com.locqar.locker.data.db.entity.EventLogEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface EventLogDao {

    @Query("SELECT * FROM event_log ORDER BY timestamp DESC LIMIT :limit")
    fun observeRecent(limit: Int = 100): Flow<List<EventLogEntity>>

    @Query("SELECT * FROM event_log ORDER BY timestamp DESC LIMIT :limit OFFSET :offset")
    suspend fun getPage(limit: Int, offset: Int): List<EventLogEntity>

    @Query("SELECT * FROM event_log WHERE doorId = :doorId ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getByDoor(doorId: Long, limit: Int = 50): List<EventLogEntity>

    @Query("SELECT * FROM event_log WHERE eventType = :type ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getByType(type: String, limit: Int = 50): List<EventLogEntity>

    @Query("SELECT * FROM event_log ORDER BY timestamp DESC")
    suspend fun getAll(): List<EventLogEntity>

    @Query("SELECT * FROM event_log WHERE synced = 0 ORDER BY timestamp ASC")
    suspend fun getUnsynced(): List<EventLogEntity>

    @Insert
    suspend fun insert(event: EventLogEntity): Long

    @Query("UPDATE event_log SET synced = 1 WHERE id IN (:ids)")
    suspend fun markSynced(ids: List<Long>)

    @Query("DELETE FROM event_log WHERE timestamp < :before")
    suspend fun deleteOlderThan(before: Long): Int

    @Query("DELETE FROM event_log")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM event_log")
    suspend fun count(): Int
}
