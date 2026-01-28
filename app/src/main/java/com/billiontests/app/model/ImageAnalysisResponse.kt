package com.billiontests.app.model

import android.os.Parcelable
import com.google.gson.annotations.Expose
import com.google.gson.annotations.SerializedName
import kotlinx.parcelize.Parcelize

@Parcelize
data class ImageAnalysisResponse(
    @SerializedName("BIL")
    @Expose
    val BIL: String = "",
    @SerializedName("Color")
    @Expose
    val Color: String = "",
    @SerializedName("ERY/Hb")
    @Expose
    val ERY: String = "",
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
    @SerializedName("UBG")
    @Expose
    val UBG: String= "",
    @SerializedName("pH")
    @Expose
    val pH: String= ""

): Parcelable {
    override fun toString(): String {
        return "ImageAnalysisResponse(BIL='$BIL', Color='$Color', ERY='$ERY', GLU='$GLU', KET='$KET', LEU='$LEU', NIT='$NIT', PRO='$PRO', SG='$SG', UBG='$UBG', pH='$pH')"
    }
}

