package com.billiontests.app.model

import android.os.Parcelable
import com.google.gson.annotations.Expose
import com.google.gson.annotations.SerializedName
import kotlinx.parcelize.Parcelize

@Parcelize
data class MissionAnalysisResponse(
    @SerializedName("BIL")
    @Expose
    val BIL: String = "",
    @SerializedName("BLO")
    @Expose
    val BLO: String = "",
    @SerializedName("GLU")
    @Expose
    val GLU: String = "",
    @SerializedName("KET")
    @Expose
    val KET: String = "",
    @SerializedName("LEU")
    @Expose
    val LEU: String = "",
    @SerializedName("NIT")
    @Expose
    val NIT: String = "",
    @SerializedName("PRO")
    @Expose
    val PRO: String = "",
    @SerializedName("SG")
    @Expose
    val SG: String = "",
    @SerializedName("URO")
    @Expose
    val URO: String= "",
    @SerializedName("pH")
    @Expose
    val pH: String= ""

): Parcelable {
    override fun toString(): String {
        return "MissionAnalysisResponse(BIL='$BIL', BLO='$BLO', GLU='$GLU', KET='$KET', LEU='$LEU', NIT='$NIT', PRO='$PRO', SG='$SG', URO='$URO', pH='$pH')"
    }
}