package com.example.sdp3.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "admin_profiles")
data class AdminProfile(
    @PrimaryKey(autoGenerate = true)
    val adminId: Int = 0,
    val name: String,
    val adminAddress: String,
    val phone: String // Keep phone for login identification
)
