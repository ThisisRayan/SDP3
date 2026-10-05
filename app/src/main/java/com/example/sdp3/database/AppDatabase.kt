package com.example.sdp3.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [UserProfile::class, Trip::class, Station::class, AdminProfile::class, Booking::class, Report::class, TopUpRequest::class], version = 22, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun userProfileDao(): UserProfileDao
    abstract fun tripDao(): TripDao
    abstract fun stationDao(): StationDao
    abstract fun adminProfileDao(): AdminProfileDao
    abstract fun bookingDao(): BookingDao
    abstract fun reportDao(): ReportDao
    abstract fun topUpDao(): TopUpRequestDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "sdp3_database"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
