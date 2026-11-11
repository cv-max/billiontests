package com.billiontests.app.scan

import android.app.Application
import android.util.Log
import com.billiontests.app.R
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.billiontests.app.analysis.CardConstants
import com.billiontests.app.analysis.UrinalysisAnalyzer
import com.billiontests.app.analysis.toImageAnalysisResponse
import com.billiontests.app.analysis.toMissionAnalysisResponse
import com.billiontests.app.model.ImageAnalysisResponse
import com.billiontests.app.model.MissionAnalysisResponse
import com.billiontests.app.network.RetrofitInstance
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody
import java.io.File

/** Only these failures contain messages intended for display to the user. */
class LocalAnalysisException(message: String, cause: Throwable? = null) : Exception(message, cause)

class ResultViewModel(app: Application) : AndroidViewModel(app) {

    companion object {
        private const val TAG = "ResultViewModel"

        /**
         * Analyse on-device instead of calling the Flask server.
         * Wire this to Firebase Remote Config ("use_local_inference") for a kill switch.
         */
        @JvmStatic
        var useLocalAnalysis: Boolean = true
    }

    private val _resultMutableLiveData = MutableLiveData<Result<ImageAnalysisResponse?>>()
    val resultLiveData: LiveData<Result<ImageAnalysisResponse?>> = _resultMutableLiveData

    private val _missionMutableLiveData = MutableLiveData<Result<MissionAnalysisResponse?>>()
    val missiontLiveData: LiveData<Result<MissionAnalysisResponse?>> = _missionMutableLiveData

    /** Non-fatal capture-quality warnings from the last local run (lighting, marker count). */
    private val _qualityIssues = MutableLiveData<List<String>>()
    val qualityIssues: LiveData<List<String>> = _qualityIssues

    fun uploadImage(imagePath: String) {
        viewModelScope.launch {
//            if (useLocalAnalysis) {
                _resultMutableLiveData.value = analyzeLocally(
                    imagePath, CardConstants.STANDARD
                ) { it.toImageAnalysisResponse() }
//            } else {
//                _resultMutableLiveData.value = uploadRemote(imagePath)
//            }
        }
    }

    fun uploadImageMission(imagePath: String) {
        viewModelScope.launch {
            if (useLocalAnalysis) {
                _missionMutableLiveData.value = analyzeLocally(
                    imagePath, CardConstants.MISSION
                ) { it.toMissionAnalysisResponse() }
            } else {
                _missionMutableLiveData.value = uploadRemoteMission(imagePath)
            }
        }
    }

    private suspend fun <T> analyzeLocally(
        imagePath: String,
        cardType: CardConstants.CardType,
        map: (Map<String, String>) -> T,
    ): Result<T?> {
        val outcome = withContext(Dispatchers.Default) {
            runCatching {
                UrinalysisAnalyzer.analyze(getApplication(), imagePath, cardType)
            }
        }.getOrElse { e ->
            Log.e(TAG, "local analysis crashed", e)
            return Result.failure(LocalAnalysisException(getApplication<Application>().getString(R.string.analysis_unexpected_error), e))
        }

        return when (outcome) {
            is UrinalysisAnalyzer.Outcome.Success -> {
                _qualityIssues.value = outcome.qualityIssues
                Log.d(TAG, "local result: ${outcome.values} issues=${outcome.qualityIssues}")
                Result.success(map(outcome.values))
            }
            is UrinalysisAnalyzer.Outcome.Failure -> {
                _qualityIssues.value = outcome.qualityIssues
                Log.e(TAG, "local analysis failed: ${outcome.reason} ${outcome.qualityIssues}")
                Result.failure(LocalAnalysisException(outcome.reason))
            }
        }
    }

    // ---- server path, kept as the fallback behind useLocalAnalysis ----

    private fun partFor(imagePath: String): MultipartBody.Part {
        val file = File(imagePath)
        val body = RequestBody.create(MediaType.parse("multipart/form-data"), file)
        return MultipartBody.Part.createFormData("file", file.name, body)
    }

    private suspend fun uploadRemote(imagePath: String): Result<ImageAnalysisResponse?> = try {
        val response = RetrofitInstance.api.uploadImage(partFor(imagePath))
        if (response.isSuccessful) Result.success(response.body())
        else Result.failure(Throwable(response.errorBody()?.string()))
    } catch (e: Exception) {
        Log.e(TAG, "failed to get result: $e")
        Result.failure(Throwable("Failed to get Result"))
    }

    private suspend fun uploadRemoteMission(imagePath: String): Result<MissionAnalysisResponse?> = try {
        val response = RetrofitInstance.api.analyzeMission(partFor(imagePath))
        if (response.isSuccessful) Result.success(response.body())
        else Result.failure(Throwable(response.errorBody()?.string()))
    } catch (e: Exception) {
        Log.e(TAG, "failed to get result: $e")
        Result.failure(Throwable("Failed to get Result"))
    }
}
