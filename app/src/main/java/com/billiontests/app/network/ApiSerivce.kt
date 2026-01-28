package com.billiontests.app.network

import com.billiontests.app.model.ImageAnalysisResponse
import com.billiontests.app.model.MissionAnalysisResponse
import com.billiontests.app.model.UrinalysisResponse
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import okhttp3.MultipartBody
import retrofit2.Response

interface UrinalysisApiService {
    @Multipart
    @POST("api/analyze")
    suspend fun uploadImage(
        @Part image: MultipartBody.Part
    ): Response<ImageAnalysisResponse>

    @Multipart
    @POST("api/analyze_mission")
    suspend fun analyzeMission(
        @Part image: MultipartBody.Part
    ): Response<MissionAnalysisResponse>
}


