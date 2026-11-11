package com.billiontests.app.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.billiontests.app.model.Patient

@Dao
interface PatientDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPatient(patient: Patient)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPatients(patients: List<Patient>)

    @Query("SELECT * FROM patient ORDER BY name ASC")
    suspend fun getAllPatients(): List<Patient>

    @Query("SELECT * FROM patient WHERE mobileNo = :mobileNo LIMIT 1")
    suspend fun getPatientByMobileNo(mobileNo: String): Patient?

    @Query("""
        SELECT * FROM patient 
        WHERE name LIKE '%' || :query || '%' 
        OR mobileNo LIKE '%' || :query || '%' 
        ORDER BY name ASC
    """)
    suspend fun searchPatients(query: String): List<Patient>

    @Update
    suspend fun updatePatient(patient: Patient)

    @Delete
    suspend fun deletePatient(patient: Patient)

    @Query("DELETE FROM patient WHERE mobileNo = :mobileNo")
    suspend fun deletePatientByMobileNo(mobileNo: String)

    @Query("DELETE FROM patient")
    suspend fun deleteAllPatients()
}