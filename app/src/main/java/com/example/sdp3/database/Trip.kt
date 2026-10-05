package com.example.sdp3.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "trips")
data class Trip(
    @PrimaryKey
    val id: String,
    val userPhone: String,
    val userName: String,
    val startStation: String = "",
    val endStation: String = "",
    val stationLocation: String,
    val durationSeconds: Long,
    val cost: Double,
    val date: String,
    val startLat: Double,
    val startLon: Double,
    val endLat: Double,
    val endLon: Double,
    val distanceKm: Double,
    val routeJson: String = "",
    val fine: Double = 0.0,
    val wasBooked: Boolean = false
)
