package com.locqar.locker.data.db.dao

import androidx.room.*
import com.locqar.locker.data.db.entity.AccessCodeEntity
import com.locqar.locker.data.db.entity.CodeStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface AccessCodeDao {

    @Query("SELECT * FROM access_codes ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<AccessCodeEntity>>

    @Query("SELECT * FROM access_codes WHERE code = :code AND status = '${CodeStatus.ACTIVE}' LIMIT 1")
    suspend fun findActiveByCode(code: String): AccessCodeEntity?

    @Query("SELECT * FROM access_codes WHERE doorId = :doorId AND status = '${CodeStatus.ACTIVE}'")
    suspend fun findActiveByDoor(doorId: Long): List<AccessCodeEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(code: AccessCodeEntity): Long

    @Query("UPDATE access_codes SET status = '${CodeStatus.USED}', usedAt = :now WHERE id = :id")
    suspend fun markUsed(id: Long, now: Long = System.currentTimeMillis())

    @Query("UPDATE access_codes SET status = '${CodeStatus.EXPIRED}' WHERE status = '${CodeStatus.ACTIVE}' AND expiresAt > 0 AND expiresAt < :now")
    suspend fun expireCodes(now: Long = System.currentTimeMillis()): Int

    @Query("UPDATE access_codes SET status = '${CodeStatus.REVOKED}' WHERE doorId = :doorId AND status = '${CodeStatus.ACTIVE}'")
    suspend fun revokeByDoor(doorId: Long)

    @Delete
    suspend fun delete(code: AccessCodeEntity)

    @Query("DELETE FROM access_codes")
    suspend fun deleteAll()
}
