package com.example.sdp3.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "reports")
data class Report(
    @PrimaryKey
    val id: String,
    val referenceId: String, // Trip ID or Booking ID
    val type: String, // "TRIP" or "BOOKING"
    val userName: String,
    val userPhone: String,
    val title: String,
    val description: String,
    val date: String,
    val status: String = "PENDING" // "PENDING", "SOLVED"
)
