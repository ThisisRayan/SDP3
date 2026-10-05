package com.example.sdp3.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface AdminProfileDao {
    @Query("SELECT * FROM admin_profiles WHERE phone = :phone LIMIT 1")
    fun getAdminProfileFlow(phone: String): Flow<AdminProfile?>

    @Query("SELECT * FROM admin_profiles WHERE phone = :phone LIMIT 1")
    suspend fun getAdminProfile(phone: String): AdminProfile?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAdminProfile(adminProfile: AdminProfile)

    @Query("SELECT EXISTS(SELECT 1 FROM admin_profiles WHERE phone = :phone)")
    suspend fun adminExists(phone: String): Boolean
}
