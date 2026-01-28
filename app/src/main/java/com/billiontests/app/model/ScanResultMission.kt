package com.billiontests.app.model

import android.os.Parcelable
import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.parcelize.Parcelize
import java.util.*

@Parcelize
@Entity(tableName = "scan_results")
data class ScanResultMission(
    @PrimaryKey
    var timeStamp:Long = System.currentTimeMillis(),
    val scanId: String,
    val date: String,
    val status: String,
    val scannedImage:String="",
    val BIL: String = "",
    val BLO: String = "",
    val GLU: String = "",
    val KET: String = "",
    val LEU: String = "",
    val NIT: String = "",
    val PRO: String = "",
    val SG: String = "",
    val UBG: String= "",
    val pH: String= "",
    var isUploaded: Boolean = false,
    var isImageUploaded: Boolean = false
): Parcelable

fun ScanResultMission.toMissionAnalysisResponse(): MissionAnalysisResponse {
    return MissionAnalysisResponse(
        BIL = this.BIL,
        BLO = this.BLO,
        GLU = this.GLU,
        KET = this.KET,
        LEU = this.LEU,
        NIT = this.NIT,
        PRO = this.PRO,
        SG = this.SG,
        URO = this.UBG,
        pH = this.pH
    )
}

// Convert ScanResult object to a HashMap
fun ScanResultMission.toHashMap(): HashMap<String, Any> {
    return hashMapOf(
        "scanId" to scanId,
        "date" to date,
        "status" to status,
        "scannedImage" to scannedImage,
        "BIL" to BIL,
        "BLO" to BLO,
        "GLU" to GLU,
        "KET" to KET,
        "LEU" to LEU,
        "NIT" to NIT,
        "PRO" to PRO,
        "SG" to SG,
        "UBG" to UBG,
        "pH" to pH
    )
}
