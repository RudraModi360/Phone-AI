package com.example.clock.database

import android.content.Context
import androidx.room.*
import com.example.clock.ClockEvent
import kotlinx.coroutines.flow.Flow

@Dao
interface ClockDao {
    @Query("SELECT * FROM clock_events ORDER BY startTime ASC")
    fun getAllEventsFlow(): Flow<List<ClockEvent>>

    @Query("SELECT * FROM clock_events")
    suspend fun getAllEvents(): List<ClockEvent>

    @Query("SELECT * FROM clock_events WHERE id = :id")
    suspend fun getEventById(id: String): ClockEvent?

    @Query("SELECT * FROM clock_events WHERE type = :type")
    suspend fun getEventsByType(type: String): List<ClockEvent>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEvent(event: ClockEvent)

    @Update
    suspend fun updateEvent(event: ClockEvent)

    @Delete
    suspend fun deleteEvent(event: ClockEvent)

    @Query("DELETE FROM clock_events WHERE id = :id")
    suspend fun deleteEventById(id: String)
}

@Database(entities = [ClockEvent::class], version = 1, exportSchema = false)
abstract class ClockDatabase : RoomDatabase() {
    abstract fun clockDao(): ClockDao

    companion object {
        @Volatile
        private var INSTANCE: ClockDatabase? = null

        fun getDatabase(context: Context): ClockDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    ClockDatabase::class.java,
                    "clock_management_db"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
