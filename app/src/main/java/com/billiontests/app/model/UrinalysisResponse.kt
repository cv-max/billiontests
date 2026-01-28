package com.billiontests.app.model

import com.google.gson.annotations.SerializedName


data class UrinalysisResponse(
    @SerializedName("color_values")
    val colorValues: List<List<Int>>
)
