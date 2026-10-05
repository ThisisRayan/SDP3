package com.example.sdp3.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "stations")
data class Station(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val totalCycles: Int,
    val availableCycles: Int,
    val status: String = "Open" // "Open", "Closed", "Maintenance"
) {
    val isOpen: Boolean get() = status == "Open"
}
