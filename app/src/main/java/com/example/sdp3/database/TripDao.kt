package com.example.sdp3.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface TripDao {
    @Query("SELECT * FROM trips ORDER BY id DESC")
    fun getAllTrips(): Flow<List<Trip>>

    @Query("SELECT * FROM trips WHERE userPhone = :phone ORDER BY id DESC")
    fun getTripsByUser(phone: String): Flow<List<Trip>>

    @Query("SELECT * FROM trips ORDER BY id DESC")
    suspend fun getAllTripsList(): List<Trip>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTrip(trip: Trip)

    @Query("DELETE FROM trips WHERE userPhone = :phone")
    suspend fun deleteTripsByUser(phone: String)

    @Query("DELETE FROM trips")
    suspend fun deleteAllTrips()

    @Query("SELECT id FROM trips ORDER BY id DESC LIMIT 1")
    suspend fun getLastTripId(): String?
}
