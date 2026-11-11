package com.billiontests.app.model

import android.os.Parcelable
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.google.gson.Gson
import kotlinx.parcelize.Parcelize
import androidx.room.TypeConverter

@Entity(tableName = "patient")
@Parcelize
data class Patient(
    val name: String,
    @PrimaryKey
    val mobileNo: String,
    val gender: String,
    val age: Int,
    val referredBy: String
): Parcelable
class TypeConverter {

    private val gson = Gson()

    @TypeConverter
    fun fromPatient(patient: Patient?): String? {
        return patient?.let { gson.toJson(it) }
    }

    @TypeConverter
    fun toPatient(patientJson: String?): Patient? {
        return patientJson?.let {
            gson.fromJson(it, Patient::class.java)
        }
    }
}