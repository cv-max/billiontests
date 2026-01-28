package com.billiontests.app.model

data class PatientInfo(
    val name: String,
    val ageYears: String,
    val gender: String,
    val reference: String = "Self",
    val reportId: String,
    val sampleType: String = "Urine",
    val collectedOn: String,   // e.g. "17/09/2024, 12:50:12 PM"
    val reportedOn: String     // e.g. "17/09/2024, 12:52:12 PM"
)
