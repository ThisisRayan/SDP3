package com.example.sdp3.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface TopUpRequestDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRequest(request: TopUpRequest)

    @Update
    suspend fun updateRequest(request: TopUpRequest)

    @Query("SELECT * FROM top_up_requests WHERE userPhone = :phone ORDER BY date DESC")
    fun getRequestsByUser(phone: String): Flow<List<TopUpRequest>>

    @Query("SELECT * FROM top_up_requests ORDER BY date DESC")
    fun getAllRequests(): Flow<List<TopUpRequest>>

    @Query("SELECT * FROM top_up_requests WHERE status = 'PENDING' ORDER BY date DESC")
    fun getPendingRequests(): Flow<List<TopUpRequest>>
}
