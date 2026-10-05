package com.example.sdp3.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface UserProfileDao {
    @Query("SELECT * FROM user_profiles LIMIT 1")
    fun getUserProfile(): Flow<UserProfile?>

    @Query("SELECT * FROM user_profiles WHERE phone = :phoneNumber LIMIT 1")
    suspend fun getUserByPhone(phoneNumber: String): UserProfile?

    @Query("SELECT * FROM user_profiles WHERE phone = :phoneNumber LIMIT 1")
    fun getUserByPhoneFlow(phoneNumber: String): Flow<UserProfile?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProfile(profile: UserProfile)

    @Update
    suspend fun updateProfile(profile: UserProfile)

    @Delete
    suspend fun deleteProfile(profile: UserProfile)

    @Query("DELETE FROM user_profiles")
    suspend fun deleteAllProfiles()

    @Query("SELECT * FROM user_profiles")
    fun getAllProfilesFlow(): Flow<List<UserProfile>>

    @Query("SELECT * FROM user_profiles")
    suspend fun getAllProfiles(): List<UserProfile>
}
