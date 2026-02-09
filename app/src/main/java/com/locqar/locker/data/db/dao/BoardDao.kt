package com.locqar.locker.data.db.dao

import androidx.room.*
import com.locqar.locker.data.db.entity.BoardEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BoardDao {

    @Query("SELECT * FROM boards ORDER BY stationNumber")
    fun observeAll(): Flow<List<BoardEntity>>

    @Query("SELECT * FROM boards WHERE stationNumber = :stationNumber LIMIT 1")
    suspend fun getByStation(stationNumber: Int): BoardEntity?

    @Query("SELECT * FROM boards LIMIT 1")
    suspend fun getFirst(): BoardEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(board: BoardEntity): Long

    @Update
    suspend fun update(board: BoardEntity)

    @Query("UPDATE boards SET isOnline = :online, lastPollTime = :pollTime, lastStateBits = :stateBits, updatedAt = :now WHERE stationNumber = :station")
    suspend fun updatePollState(station: Int, online: Boolean, pollTime: Long, stateBits: Int, now: Long = System.currentTimeMillis())

    @Delete
    suspend fun delete(board: BoardEntity)

    @Query("DELETE FROM boards")
    suspend fun deleteAll()
}
