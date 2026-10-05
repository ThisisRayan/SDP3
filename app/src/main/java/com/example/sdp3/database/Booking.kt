package com.example.sdp3.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "bookings")
data class Booking(
    @PrimaryKey
    val id: String,
    val userPhone: String,
    val stationName: String,
    val startTime: Long,
    val date: String,
    val status: String, // "COMPLETED", "CANCELLED", "EXPIRED"
    val amountPaid: Double,
    val refundAmount: Double = 0.0
)
