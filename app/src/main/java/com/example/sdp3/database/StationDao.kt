package com.example.sdp3.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface StationDao {
    @Query("SELECT * FROM stations")
    fun getAllStationsFlow(): Flow<List<Station>>

    @Query("SELECT * FROM stations")
    suspend fun getAllStations(): List<Station>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStation(station: Station)

    @Update
    suspend fun updateStation(station: Station)

    @Query("SELECT * FROM stations WHERE name = :name LIMIT 1")
    suspend fun getStationByName(name: String): Station?

    @Query("DELETE FROM stations WHERE id = :stationId")
    suspend fun deleteStation(stationId: Int)
}
