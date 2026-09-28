package com.example.liftingapp.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [Athlete::class, LiftEntry::class], version = 2, exportSchema = false)
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
                )
                    // The schema changed (new table + new columns), so the version went 1 -> 2.
                    // While developing, it's fine to wipe old test data on a version bump.
                    // Before real athletes use the app, replace this with a proper Migration.
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                    .also { INSTANCE = it }
            }
    }
}
