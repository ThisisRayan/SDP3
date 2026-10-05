package com.example.sdp3.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "top_up_requests")
data class TopUpRequest(
    @PrimaryKey
    val id: String,
    val userPhone: String,
    val userName: String,
    val transactionId: String,
    val amount: Double,
    val date: String,
    val status: String = "PENDING", // "PENDING", "APPROVED", "REJECTED"
    val rejectionReason: String? = null
)
