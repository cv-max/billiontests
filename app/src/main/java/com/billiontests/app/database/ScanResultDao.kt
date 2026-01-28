package com.billiontests.app.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.billiontests.app.model.ScanResult

@Dao
interface ScanResultDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertScanResult(scanResult: ScanResult)

    @Query("SELECT * FROM scan_results")
    suspend fun getAllScanResults(): List<ScanResult>

    @Update
    suspend fun updateScanResult(scanResult: ScanResult)

    @Query("SELECT * FROM scan_results WHERE status = :status ORDER BY date DESC")
    suspend fun getScanResultsByStatus(status: String): List<ScanResult>
}
