package com.example.lifttracker.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [LiftEntry::class], version = 1, exportSchema = false)
abstract class LiftDatabase : RoomDatabase() {
    abstract fun liftDao(): LiftDao

    companion object {
        @Volatile private var INSTANCE: LiftDatabase? = null

        fun getDatabase(context: Context): LiftDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    LiftDatabase::class.java,
                    "lift_database"
                ).build().also { INSTANCE = it }
            }
    }
}