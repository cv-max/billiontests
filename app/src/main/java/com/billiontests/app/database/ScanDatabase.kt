package com.billiontests.app.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.billiontests.app.model.Patient
import com.billiontests.app.model.ScanResult
import com.billiontests.app.model.TypeConverter
import org.opencv.utils.Converters

@Database(entities = [ScanResult::class, Patient::class], version = 11, exportSchema = false)
@TypeConverters(TypeConverter::class)
abstract class ScanDatabase : RoomDatabase() {
    abstract fun scanResultDao(): ScanResultDao
    abstract fun patientDao(): PatientDao
    companion object {
        @Volatile
        private var INSTANCE: ScanDatabase? = null

        fun getDatabase(context: Context): ScanDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    ScanDatabase::class.java,
                    "bt_database"
                ).fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
