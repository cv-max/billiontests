package com.billiontests.app.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.annotation.OptIn
import androidx.core.app.NotificationCompat
import androidx.media3.common.util.UnstableApi
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.billiontests.app.database.ScanDatabase
import com.billiontests.app.model.ScanResult
import com.billiontests.app.model.toHashMap
import com.billiontests.app.scan.ResultActivity
import com.billiontests.app.utils.convertMillisToDate
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.io.File
import java.util.*

class UploadWorker(context: Context, workerParams: WorkerParameters) :
    CoroutineWorker(context, workerParams) {

    companion object {
        const val NOTIFICATION_ID = 1
        const val CHANNEL_ID = "upload_channel"
        private const val TAG = "UploadWorker"
    }


    override suspend fun doWork(): Result {
        // Get input data (image URI and other params)
        val imageUriString = inputData.getString("imageUri") ?: return Result.failure()
        val reason = inputData.getString("reason") ?: "No reason"
        val timestamp = inputData.getString("timestamp") ?: ""
        val scanResultStr = inputData.getString("scanResult") ?: ""

        var scanResult: ScanResult? = null

        if (scanResultStr.isNotEmpty()) {
            scanResult = Gson().fromJson(scanResultStr, ScanResult::class.java)
        }

        // Reference to Firebase Storage
        val storageReference = FirebaseStorage.getInstance().reference
        val firestore = FirebaseFirestore.getInstance()
        val dateStr = convertMillisToDate(System.currentTimeMillis(),"dd-MM-yyyy")

        // Unique file name for the image (to avoid overwriting files)
        val fileName = "images/$dateStr/${UUID.randomUUID()}.jpg"
        val imageRef = storageReference.child(fileName)

        return try {

            val file = Uri.fromFile(File(imageUriString))
            var downloadUrl:Uri? = null
            var imageUrl = ""
            if(scanResult != null) {
                if(scanResult.isImageUploaded.not()) {
                    // Upload the image to Firebase Storage using a coroutine
                    imageRef.putFile(file).await()
                    Log.e(TAG, "image uploaded successfully")
                    // Get the image download URL
                    downloadUrl = imageRef.downloadUrl.await()
                    imageUrl = downloadUrl.toString()
                }
            }else{
                imageRef.putFile(file).await()
                Log.e(TAG, "image uploaded successfully")
                // Get the image download URL
                downloadUrl = imageRef.downloadUrl.await()
                imageUrl = downloadUrl.toString()
            }

            Log.e(TAG, "downloaded url : $imageUrl")

            val deviceInfo = hashMapOf(
                "manufacturer" to Build.MANUFACTURER,
                "model" to Build.MODEL,
                "osVersion" to Build.VERSION.RELEASE,
                "sdkInt" to Build.VERSION.SDK_INT.toString()
            )

            scanResult?.let {
                downloadUrl?.let {
                    scanResult.isImageUploaded = true
                    updateDatabase(scanResult, applicationContext)
                }
                var scanData = it.toHashMap()
                val data = hashMapOf(
                    "imageUrl" to imageUrl,
                    "reason" to reason,
                    "timestamp" to timestamp,
                    "device_info" to deviceInfo,
                    "scan_data" to scanData
                )
                try {
                    // Store data in Fire store using a coroutine
                    val docRef = firestore.collection("$dateStr")
                        .add(data)
                        .await()
                    Log.e(TAG, "data uploaded successfully ${docRef.id}")
                    scanResult.isUploaded = true
                    updateDatabase(scanResult, applicationContext)
                    Result.success()
                } catch (e: Exception) {
                    Log.e(TAG, "data upload failed ${e.message}")
                    Result.retry()
                }
            } ?: let {
                // Prepare data to store in Fire store
                val data = hashMapOf(
                    "imageUrl" to imageUrl,
                    "reason" to reason,
                    "timestamp" to timestamp,
                    "device_info" to deviceInfo
                )

                try {
                    // Store data in Fire store using a coroutine
                    val docRef = firestore.collection("$dateStr")
                        .add(data)
                        .await()
                    Log.e(TAG, "data uploaded successfully ${docRef.id}")
                    Result.success()
                } catch (e: Exception) {
                    Log.e(TAG, "data upload failed ${e.message}")
                    Result.retry()
                }
            }
        } catch (e: Exception) {
            println("Exception during upload: ${e.message}")
            Result.retry() // Retry the work if an exception occurred
        }
    }

    private fun updateDatabase(scanResult: ScanResult, context: Context) {
        val scanDatabase = ScanDatabase.getDatabase(context)
        val scanResultDao = scanDatabase.scanResultDao()

        try {
            CoroutineScope(Dispatchers.IO).launch {
                scanResultDao.updateScanResult(scanResult)
                Log.e(TAG, "scan result added to db")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save to db")
        }
    }
}

