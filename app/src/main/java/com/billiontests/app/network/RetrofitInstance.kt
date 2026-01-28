package com.billiontests.app.network

import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object RetrofitInstance {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)   // connection timeout
        .readTimeout(45, TimeUnit.SECONDS)      // read timeout
        .writeTimeout(45, TimeUnit.SECONDS)     // write timeout
        .build()

    val api: UrinalysisApiService by lazy {
        Retrofit.Builder()
            .baseUrl("http://20.198.243.237:3000/")  // Base URL of the Flask server
            .addConverterFactory(GsonConverterFactory.create())
            .client(client)
            .build()
            .create(UrinalysisApiService::class.java)
    }
}
