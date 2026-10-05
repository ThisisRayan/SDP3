package com.example.sdp3.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface BookingDao {
    @Query("SELECT * FROM bookings WHERE userPhone = :phone ORDER BY startTime DESC")
    fun getBookingsByUser(phone: String): Flow<List<Booking>>

    @Query("SELECT * FROM bookings ORDER BY startTime DESC")
    fun getAllBookings(): Flow<List<Booking>>

    @Query("SELECT * FROM bookings WHERE id = :id")
    suspend fun getBookingById(id: String): Booking?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBooking(booking: Booking)

    @Update
    suspend fun updateBooking(booking: Booking)

    @Query("DELETE FROM bookings WHERE userPhone = :phone AND status != 'PENDING'")
    suspend fun deleteFinishedBookingsByUser(phone: String)

    @Query("DELETE FROM bookings")
    suspend fun deleteAllBookings()

    @Query("SELECT id FROM bookings ORDER BY startTime DESC LIMIT 1")
    suspend fun getLastBookingId(): String?
}
