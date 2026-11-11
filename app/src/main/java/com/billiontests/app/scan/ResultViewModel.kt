package com.billiontests.app.scan

import com.billiontests.app.model.ImageAnalysisResponse
import com.billiontests.app.network.RetrofitInstance
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.billiontests.app.R
import com.billiontests.app.model.MissionAnalysisResponse
import com.google.gson.Gson
import okhttp3.MultipartBody
import okhttp3.RequestBody
import java.io.File
import kotlinx.coroutines.launch
import okhttp3.MediaType

//class ResultViewModel : ViewModel() {
//
//    companion object {
//        private const val TAG = "ResultViewModel"
//    }
//
//    private var _resultMutableLiveData = MutableLiveData<Result<ImageAnalysisResponse?>>()
//    val resultLiveData: LiveData<Result<ImageAnalysisResponse?>> = _resultMutableLiveData
//
//    private var _missionMutableLiveData = MutableLiveData<Result<MissionAnalysisResponse?>>()
//    val missiontLiveData: LiveData<Result<MissionAnalysisResponse?>> = _missionMutableLiveData
//
//    fun uploadImage(imagePath: String) {
//        viewModelScope.launch {
//
//            try {
//                // Prepare the file and create the request body
//                val file = File(imagePath)
//                val mediaType = MediaType.parse("multipart/form-data")
//                val requestBody = RequestBody.create(mediaType, file)
//                val imagePart = MultipartBody.Part.createFormData("file", file.name, requestBody)
//
//                // Make the API request
//                val response = RetrofitInstance.api.uploadImage(imagePart)
//
//                if (response.isSuccessful) {
//                    _resultMutableLiveData.value =
//                        Result.success(response.body())  // Return the response body if successful
//                } else {
//                    val error = response.errorBody()?.string()
//                    Log.e(TAG, "failed to get result: $error")
//                    _resultMutableLiveData.value =
//                        Result.failure(Throwable(error))  // Return the response body if successful
//                    // Return null if the request was unsuccessful
//                }
//            } catch (e: Exception) {
//                Log.e(TAG, "failed to get result: $e")
//                _resultMutableLiveData.value =
//                    Result.failure(Throwable("Failed to get Result"))  // Return the response body if successful
//
//                // Return null if the request was unsuccessful
//            }
//        }
//
//    }
//
//    fun uploadImageMission(imagePath: String) {
//        viewModelScope.launch {
//
//            try {
//                // Prepare the file and create the request body
//                val file = File(imagePath)
//                val mediaType = MediaType.parse("multipart/form-data")
//                val requestBody = RequestBody.create(mediaType, file)
//                val imagePart = MultipartBody.Part.createFormData("file", file.name, requestBody)
//
//                // Make the API request
//                val response = RetrofitInstance.api.analyzeMission(imagePart)
//
//                if (response.isSuccessful) {
//                    _missionMutableLiveData.value =
//                        Result.success(response.body())  // Return the response body if successful
//                } else {
//                    val error = response.errorBody()?.string()
//                    Log.e(TAG, "failed to get result: $error")
//                    _missionMutableLiveData.value =
//                        Result.failure(Throwable(error))  // Return the response body if successful
//                    // Return null if the request was unsuccessful
//                }
//            } catch (e: Exception) {
//                Log.e(TAG, "failed to get result: $e")
//                _missionMutableLiveData.value =
//                    Result.failure(Throwable("Failed to get Result"))  // Return the response body if successful
//
//                // Return null if the request was unsuccessful
//            }
//        }
//
//    }
//
//}