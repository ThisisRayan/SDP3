package com.example.sdp3.database

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "user_profiles",
    indices = [Index(value = ["phone"], unique = true)]
)
data class UserProfile(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val name: String,
    val age: Int,
    val phone: String,
    val hometown: String,
    val upazila: String,
    val detailedAddress: String,
    val balance: Double = 60.0,
    val pendingFine: Double = 0.0
)
