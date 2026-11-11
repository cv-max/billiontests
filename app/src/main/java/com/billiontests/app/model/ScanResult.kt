package com.billiontests.app.model

import android.os.Parcelable
import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.parcelize.Parcelize

@Parcelize
@Entity(tableName = "scan_results")
data class ScanResult(
    @PrimaryKey
    var timeStamp: Long = System.currentTimeMillis(),
    val scanId: String,
    val date: String,
    val status: String,
    val scannedImage: String = "",
    val BIL: String = "",
    val Color: String = "",
    val ERY: String = "",
    val BLO: String = "",
    val GLU: String = "",
    val KET: String = "",
    val LEU: String = "",
    val NIT: String = "",
    val PRO: String = "",
    val SG: String = "",
    val UBG: String = "",
    val pH: String = "",
    var isUploaded: Boolean = false,
    var isImageUploaded: Boolean = false,
    var isMission: Boolean = false,
    var pdfPath: String = "",
    val patient: Patient,
    var volume: String = "",
    var colorM: String = "",
    var appearance: String = ""
) : Parcelable

fun ScanResult.toImageAnalysisResponse(): ImageAnalysisResponse {
    return ImageAnalysisResponse(
        BIL = this.BIL,
        Color = this.Color,
        ERY = this.ERY,
        GLU = this.GLU,
        KET = this.KET,
        LEU = this.LEU,
        NIT = this.NIT,
        PRO = this.PRO,
        SG = this.SG,
        UBG = this.UBG,
        pH = this.pH
    )
}

fun ScanResult.toMissionAnalysisResponse(): MissionAnalysisResponse {
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
fun ScanResult.toHashMap(): HashMap<String, Any> {
    return hashMapOf(
        "scanId" to scanId,
        "date" to date,
        "status" to status,
        "scannedImage" to scannedImage,
        "BIL" to BIL,
        "Color" to Color,
        "BLO" to BLO,
        "ERY" to ERY,
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

@Parcelize
data class ScanMetaData(
    val volume: String = "",
    val color: String = "",
    val appearance: String = ""
) : Parcelable
