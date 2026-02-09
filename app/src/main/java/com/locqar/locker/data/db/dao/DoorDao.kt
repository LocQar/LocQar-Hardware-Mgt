package com.locqar.locker.data.db.dao

import androidx.room.*
import com.locqar.locker.data.db.entity.DoorEntity
import com.locqar.locker.data.db.entity.LogicalState
import com.locqar.locker.data.db.entity.PhysicalState
import kotlinx.coroutines.flow.Flow

@Dao
interface DoorDao {

    @Query("SELECT * FROM doors ORDER BY CAST(doorLabel AS INTEGER)")
    fun observeAll(): Flow<List<DoorEntity>>

    @Query("SELECT * FROM doors ORDER BY CAST(doorLabel AS INTEGER)")
    suspend fun getAll(): List<DoorEntity>

    @Query("SELECT * FROM doors WHERE id = :doorId")
    suspend fun getById(doorId: Long): DoorEntity?

    @Query("SELECT * FROM doors WHERE doorLabel = :label LIMIT 1")
    suspend fun getByLabel(label: String): DoorEntity?

    @Query("SELECT * FROM doors WHERE lockNumber = :lockNumber AND boardId = :boardId LIMIT 1")
    suspend fun getByLockNumber(boardId: Long, lockNumber: Int): DoorEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(door: DoorEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(doors: List<DoorEntity>)

    @Update
    suspend fun update(door: DoorEntity)

    @Query("UPDATE doors SET physicalState = :state, updatedAt = :now WHERE lockNumber = :lockNumber AND boardId = :boardId")
    suspend fun updatePhysicalState(boardId: Long, lockNumber: Int, state: String, now: Long = System.currentTimeMillis())

    @Query("UPDATE doors SET logicalState = :state, updatedAt = :now WHERE id = :doorId")
    suspend fun updateLogicalState(doorId: Long, state: String, now: Long = System.currentTimeMillis())

    @Query("UPDATE doors SET enabled = :enabled, updatedAt = :now WHERE id = :doorId")
    suspend fun setEnabled(doorId: Long, enabled: Boolean, now: Long = System.currentTimeMillis())

    @Query("UPDATE doors SET lastError = :error, lastErrorAt = :now, updatedAt = :now WHERE id = :doorId")
    suspend fun setLastError(doorId: Long, error: String, now: Long = System.currentTimeMillis())

    @Query("UPDATE doors SET lastUsedAt = :now, updatedAt = :now WHERE id = :doorId")
    suspend fun updateLastUsed(doorId: Long, now: Long = System.currentTimeMillis())

    /**
     * Atomic reservation: only reserve if currently AVAILABLE and CLOSED.
     * Returns number of rows affected (1 = success, 0 = failed).
     */
    @Query("""
        UPDATE doors SET
            logicalState = '${LogicalState.RESERVED}',
            reservedUntil = :reservedUntil,
            reservedBy = :reservedBy,
            updatedAt = :now
        WHERE id = :doorId
            AND logicalState = '${LogicalState.AVAILABLE}'
            AND physicalState = '${PhysicalState.CLOSED}'
            AND enabled = 1
    """)
    suspend fun atomicReserve(doorId: Long, reservedUntil: Long, reservedBy: String, now: Long = System.currentTimeMillis()): Int

    @Query("UPDATE doors SET logicalState = '${LogicalState.AVAILABLE}', reservedUntil = 0, reservedBy = null, updatedAt = :now WHERE id = :doorId")
    suspend fun clearReservation(doorId: Long, now: Long = System.currentTimeMillis())

    /**
     * Expire all reservations that have passed their deadline.
     */
    @Query("UPDATE doors SET logicalState = '${LogicalState.AVAILABLE}', reservedUntil = 0, reservedBy = null, updatedAt = :now WHERE logicalState = '${LogicalState.RESERVED}' AND reservedUntil > 0 AND reservedUntil < :now")
    suspend fun expireReservations(now: Long = System.currentTimeMillis()): Int

    /**
     * Find best door for assignment: enabled, CLOSED, AVAILABLE, least recently used.
     */
    @Query("""
        SELECT * FROM doors
        WHERE enabled = 1
            AND physicalState = '${PhysicalState.CLOSED}'
            AND logicalState = '${LogicalState.AVAILABLE}'
        ORDER BY lastUsedAt ASC
        LIMIT 1
    """)
    suspend fun findBestAvailableDoor(): DoorEntity?

    @Delete
    suspend fun delete(door: DoorEntity)

    @Query("DELETE FROM doors")
    suspend fun deleteAll()
}
