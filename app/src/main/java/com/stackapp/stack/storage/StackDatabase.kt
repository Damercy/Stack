package com.stackapp.stack.storage

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [StackStateEntity::class],
    version = 3,
    exportSchema = true,
)
abstract class StackDatabase : RoomDatabase() {
    abstract fun stackStateDao(): StackStateDao

    companion object {
        @Volatile private var instance: StackDatabase? = null

        fun get(context: Context): StackDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    StackDatabase::class.java,
                    "stack.db",
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .build()
                    .also { instance = it }
            }

        fun resetForTests() {
            instance?.close()
            instance = null
        }

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE stack_state ADD COLUMN todayDayKey TEXT NOT NULL DEFAULT ''")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE stack_state ADD COLUMN countryCode TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE stack_state ADD COLUMN onboardingComplete INTEGER NOT NULL DEFAULT 0")
            }
        }
    }
}
