package com.example.liftingapp.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [Athlete::class, LiftEntry::class, Workout::class, WorkoutExercise::class],
    version = 3,
    exportSchema = false
)
abstract class LiftDatabase : RoomDatabase() {
    abstract fun liftDao(): LiftDao
    abstract fun workoutDao(): WorkoutDao

    companion object {
        @Volatile private var INSTANCE: LiftDatabase? = null

        fun getDatabase(context: Context): LiftDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    LiftDatabase::class.java,
                    "lift_database"
                )
                    // 2 -> 3 adds the workout tables and athlete phone/email, and keeps every
                    // athlete and logged set (the beta is in use, so no more wiping on upgrade).
                    .addMigrations(MIGRATION_2_3)
                    // Only databases older than version 2 (pre-beta test builds) have no
                    // migration path, so only those are still wiped and rebuilt.
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .addCallback(PremadeWorkoutSeeder)
                    .build()
                    .also { INSTANCE = it }
            }
    }
}

/**
 * Version 2 -> 3: adds the Workouts tab's tables, optional athlete phone and email, and the
 * premade workouts. Nothing existing is touched. The SQL matches what Room generates for
 * Workout, WorkoutExercise and Athlete; Room checks it against the entities after migrating
 * and refuses to open the database if they differ.
 */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `athletes` ADD COLUMN `phone` TEXT")
        db.execSQL("ALTER TABLE `athletes` ADD COLUMN `email` TEXT")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `workouts` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`name` TEXT NOT NULL, " +
                "`notes` TEXT NOT NULL, " +
                "`isPremade` INTEGER NOT NULL, " +
                "`createdAt` INTEGER NOT NULL, " +
                "`updatedAt` INTEGER NOT NULL)"
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `workout_exercises` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`workoutId` INTEGER NOT NULL, " +
                "`position` INTEGER NOT NULL, " +
                "`name` TEXT NOT NULL, " +
                "`sets` INTEGER NOT NULL, " +
                "`reps` TEXT NOT NULL, " +
                "`load` TEXT NOT NULL, " +
                "`notes` TEXT NOT NULL, " +
                "FOREIGN KEY(`workoutId`) REFERENCES `workouts`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_workout_exercises_workoutId` ON `workout_exercises` (`workoutId`)"
        )
        insertPremadeWorkouts(db)
    }
}

/**
 * Puts the premade workouts in a database that doesn't have them yet: on a fresh install
 * (onCreate), and after an old pre-beta database is wiped and rebuilt (seeded on the next open,
 * once the new tables exist). Existing installs get them from MIGRATION_2_3 instead.
 */
private object PremadeWorkoutSeeder : RoomDatabase.Callback() {
    @Volatile private var seedOnOpen = false

    override fun onCreate(db: SupportSQLiteDatabase) = insertPremadeWorkouts(db)

    override fun onDestructiveMigration(db: SupportSQLiteDatabase) {
        seedOnOpen = true
    }

    override fun onOpen(db: SupportSQLiteDatabase) {
        if (!seedOnOpen) return
        seedOnOpen = false
        val empty = db.query("SELECT COUNT(*) FROM `workouts`").use { it.moveToFirst() && it.getLong(0) == 0L }
        if (!empty) return
        db.beginTransaction()
        try {
            insertPremadeWorkouts(db)
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }
}

/** Raw inserts (no DAO yet at this point). Each workout's exercises keep the list order. */
private fun insertPremadeWorkouts(db: SupportSQLiteDatabase, now: Long = System.currentTimeMillis()) {
    PREMADE_WORKOUTS.forEach { premade ->
        val workoutId = db.insert(
            "workouts",
            SQLiteDatabase.CONFLICT_ABORT,
            ContentValues().apply {
                put("name", premade.name)
                put("notes", premade.notes)
                put("isPremade", 1)
                put("createdAt", now)
                put("updatedAt", now)
            }
        )
        premade.exercises.forEachIndexed { index, exercise ->
            db.insert(
                "workout_exercises",
                SQLiteDatabase.CONFLICT_ABORT,
                ContentValues().apply {
                    put("workoutId", workoutId)
                    put("position", index)
                    put("name", exercise.name)
                    put("sets", exercise.sets)
                    put("reps", exercise.reps)
                    put("load", exercise.load)
                    put("notes", exercise.notes)
                }
            )
        }
    }
}
