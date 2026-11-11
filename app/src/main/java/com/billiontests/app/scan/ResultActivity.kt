package com.billiontests.app.scan

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.billiontests.app.R
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkRequest
import com.airbnb.lottie.LottieAnimationView
import com.billiontests.app.database.ScanDatabase
import com.billiontests.app.database.ScanResultDao
import com.billiontests.app.databinding.ActivityResultBinding
import com.billiontests.app.home.BottomHomeActivity
import com.billiontests.app.model.ImageAnalysisResponse
import com.billiontests.app.model.MissionAnalysisResponse
import com.billiontests.app.model.PatientInfo
import com.billiontests.app.model.ScanResult
import com.billiontests.app.model.UrineTestResult
import com.billiontests.app.utils.MISSION_RESPONSE
import com.billiontests.app.utils.PreferenceUtils
import com.billiontests.app.utils.SELECTED_VALUE
import com.billiontests.app.utils.convertMillisToDate
import com.billiontests.app.utils.generateScanId
import com.billiontests.app.utils.generateTimeBasedUniqueScanId
import com.billiontests.app.utils.generateUrineReportPdf
import com.billiontests.app.utils.getCurrentDateString
import com.billiontests.app.utils.toUrineTestResults
import com.billiontests.app.worker.UploadWorker
import com.google.android.material.button.MaterialButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import androidx.core.net.toUri
import com.billiontests.app.model.Patient
import com.billiontests.app.model.ScanMetaData
import com.billiontests.app.utils.PATIENT_SELECTED
import com.billiontests.app.utils.SCAN_METADATA
import com.billiontests.app.utils.convertDateFormat

class ResultActivity : AppCompatActivity(), UrineTestAdapter.OnDetailsButtonClickListener {

    companion object {
        private const val TAG = "ResultActivity"
    }

    private val viewModel: ResultViewModel by viewModels()
    private var isCorrect: Boolean = false
    private lateinit var scanDatabase: ScanDatabase
    private lateinit var scanResultDao: ScanResultDao
    private lateinit var binding: ActivityResultBinding
    private var imageAnalysisResponse: ImageAnalysisResponse? = null
    private var missionAnalysisResponse: MissionAnalysisResponse? = null
    private var scanId = ""
    private var scanResult: ScanResult? = null
    private var id = ""
    private var selectedValue = "Mission"
    private var directoryPath = ""
    private var selectedPatient: Patient? = null
    private var scanMetaData : ScanMetaData? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_result)
        binding = ActivityResultBinding.inflate(layoutInflater)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // Assuming you have a ScanDatabase instance:
        scanDatabase = ScanDatabase.getDatabase(this)
        scanResultDao = scanDatabase.scanResultDao()


        val addToDb = intent.getBooleanExtra("addToDb", false)
        scanResult = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra("result", ScanResult::class.java)
        } else {
            intent.getParcelableExtra<ScanResult>("result")
        }

        selectedPatient = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(PATIENT_SELECTED, Patient::class.java)
        }else{
            intent.getParcelableExtra(PATIENT_SELECTED)
        }

        scanMetaData = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(SCAN_METADATA, ScanMetaData::class.java)
        }else{
            intent.getParcelableExtra(SCAN_METADATA)
        }
        //assigning existing id
        scanResult?.let {
            id = it.scanId
        }

        selectedValue = intent.getStringExtra(SELECTED_VALUE) ?: "Mission"

        val path = intent.getStringExtra("path")
        imageAnalysisResponse = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra("response", ImageAnalysisResponse::class.java)
        } else {
            intent.getParcelableExtra("response")
        }

        missionAnalysisResponse = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(MISSION_RESPONSE, MissionAnalysisResponse::class.java)
        } else {
            intent.getParcelableExtra(MISSION_RESPONSE)
        }


        var resultList =
            if (selectedValue == "Mission") missionAnalysisResponse?.toUrineTestResults() else imageAnalysisResponse?.toUrineTestResults()

        isCorrect = resultList != null
        if (isCorrect) {
            resultList?.forEach {
                Log.d(TAG, "Urine test Result: ${it.parameter} ${it.result}")
            }
        }
        scanId = generateTimeBasedUniqueScanId(generateScanId())
        Log.e(TAG, "path: $path")
        if (id.isNotEmpty()) {
            scanId = id
        }
        Log.e(TAG, "scanId: $scanId")
        val tvReportId = findViewById<TextView>(R.id.tvReportId)
        tvReportId.text = "Report no. : $scanId"

        path?.let {
            directoryPath = File(path).parent ?: ""
            val lottieView = findViewById<LottieAnimationView>(R.id.loader_verify)
            lottieView.setAnimation(if (isCorrect) R.raw.correct_animation else R.raw.wrong_animation)
            lottieView.repeatCount = 1
            lottieView.playAnimation()

            val dateTimeTv = findViewById<TextView>(R.id.textView_test_time)
            dateTimeTv.text = if (scanResult?.date.isNullOrEmpty().not()) convertDateFormat(
                scanResult?.date ?: getCurrentDateString("dd/MM/yyyy"), "dd/MM/yyyy"
            ) else getCurrentDateString("dd/MM/yyyy")

            if (addToDb) {
                if (selectedValue == "Mission") {
                    missionAnalysisResponse?.let {
                        lifecycleScope.launch {
                            addScanToDbMission(path, it, resultList ?: emptyList())
                        }
                    } ?: let {
                        Log.e(TAG, "mission analysis response is null")
                    }
                } else {
                    imageAnalysisResponse?.let {
                        lifecycleScope.launch {
                            addScanToDb(path, it, resultList ?: emptyList())
                        }
                    } ?: let {
                        Log.e(TAG, "image analysis response is null")
                    }
                }
            }
        }

        //Loading result to recyclerview
        setUpRecyclerView(resultList)

        resultList?.let {
            setUpClickListeners(resultList, addToDb)
        }
    }

    private fun setUpRecyclerView(resultList: List<UrineTestResult>?) {
        var adapter =
            resultList?.toList()?.let { UrineTestAdapter(it, this) }
                ?: UrineTestAdapter(emptyList(), this@ResultActivity)
        var recyclerView = findViewById<RecyclerView>(R.id.recycler_view_results)
        recyclerView.layoutManager = GridLayoutManager(this, 2)
        recyclerView.adapter = adapter
        adapter.notifyDataSetChanged()
    }

    private fun setUpClickListeners(resultList: List<UrineTestResult>, addtoDb: Boolean) {

        val startNewTestButton = findViewById<Button>(R.id.end_button)
        startNewTestButton.isVisible = addtoDb
        startNewTestButton.setOnClickListener {
            startActivity(
                Intent(
                    this,
                    BottomHomeActivity::class.java
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            )

            finish()
        }

        val shareButton = findViewById<MaterialButton>(R.id.button_share_result)
        val viewReportButton = findViewById<MaterialButton>(R.id.view_report_button)

        val sharedPreferences =
            getSharedPreferences(PreferenceUtils.PREFERENCE_NAME, MODE_PRIVATE)
        val name = sharedPreferences.getString(PreferenceUtils.USER_NAME, "") ?: ""
        val age = sharedPreferences.getString(PreferenceUtils.AGE, "") ?: ""
        val gender = sharedPreferences.getString(PreferenceUtils.GENDER, "") ?: ""
        shareButton.setOnClickListener {
            val uri = generateUrineReportUri(name, age, gender, resultList)
            val share = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(share, "Share report"))

        }
        
        viewReportButton.setOnClickListener { 
            val uri = generateUrineReportUri(name, age, gender, resultList)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/pdf")
                flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
            }
            startActivity(Intent.createChooser(intent, "View Report"))
        }
    }

    private fun generateUrineReportUri(
        name: String,
        age: String,
        gender: String,
        resultList: List<UrineTestResult>
    ): Uri {
        var pdfPath = ""
        scanResult?.let {
            pdfPath = it.pdfPath
        }
        val uri = if (pdfPath.isNotEmpty()) pdfPath.toUri() else generateUrineReportPdf(
            context = this,
            patient = PatientInfo(
                name = selectedPatient?.name?: name,
                ageYears = selectedPatient?.age?.toString() ?: age,
                gender = selectedPatient?.gender ?: gender,
                reportId = scanId,
                collectedOn = scanResult?.date ?: getCurrentDateString(),
                reportedOn = scanResult?.date ?: getCurrentDateString()
            ),
            results = resultList, // from your adapter source
            fileName = "BillionTests_Urine_Report_${scanResult?.scanId}. .pdf",
            logoResId = R.mipmap.ic_launcher_round,
            isMission = selectedValue == "Mission",
            directoryPath = directoryPath,
            volume = scanMetaData?.volume ?: "",
            colorM = scanMetaData?.color ?: "",
            appearance = scanMetaData?.appearance ?: ""
        )

        if (pdfPath.isEmpty()) {
            scanResult?.let {
                try {
                    it.pdfPath = uri.toString()
                    CoroutineScope(Dispatchers.IO).launch {
                        scanResultDao.updateScanResult(it)
                        Log.e(TAG, "scan result added to db")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to save to db")
                }
            }
        }
        return uri
    }

    private fun showCustomInputDialog(onTextSaved: (String) -> Unit) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_custom_input, null)
        val editText = dialogView.findViewById<EditText>(R.id.editTextInput)
        val btnSave = dialogView.findViewById<Button>(R.id.btnSave)

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()

        btnSave.setOnClickListener {
            val inputText = editText.text.toString().trim()
            if (inputText.isNotEmpty()) {
                onTextSaved(inputText)   // 🔹 return user input via callback
                dialog.dismiss()
            } else {
                editText.error = "Please enter some text"
            }
        }

        dialog.show()
    }


    private suspend fun addScanToDb(
        imagePath: String,
        imageAnalysisResponse: ImageAnalysisResponse,
        resultList: List<UrineTestResult>
    ) {
        val sharedPreferences = getSharedPreferences(PreferenceUtils.PREFERENCE_NAME, MODE_PRIVATE)
        val currentTime = System.currentTimeMillis()
        val lastTestTime = convertMillisToDate(currentTime)
        sharedPreferences.edit().putString(PreferenceUtils.LAST_TEST_TIME, lastTestTime).apply()

        scanResult = ScanResult(
            System.currentTimeMillis(),
            scanId,
            getCurrentDateString("dd/MM/yyyy"),
            if (isCorrect) "success" else "failed",
            imagePath,
            imageAnalysisResponse.BIL,
            imageAnalysisResponse.Color,
            imageAnalysisResponse.ERY,
            "",
            imageAnalysisResponse.GLU,
            imageAnalysisResponse.KET,
            imageAnalysisResponse.LEU,
            imageAnalysisResponse.NIT,
            imageAnalysisResponse.PRO,
            imageAnalysisResponse.SG,
            imageAnalysisResponse.UBG,
            imageAnalysisResponse.pH,
            false,
            false,
            false,
            patient = selectedPatient!!,
            volume = scanMetaData?.volume ?: "",
            colorM = scanMetaData?.color ?: "",
            appearance = scanMetaData?.appearance ?: ""
        )

        val name = PreferenceUtils.getString(this, PreferenceUtils.USER_NAME, "")
        val age = PreferenceUtils.getString(this, PreferenceUtils.AGE, "")
        val gender = PreferenceUtils.getString(this, PreferenceUtils.GENDER, "")

        val uri = generateUrineReportPdf(
            context = this,
            patient = PatientInfo(
                name = selectedPatient?.name?: name,
                ageYears = selectedPatient?.age?.toString() ?: age,
                gender = selectedPatient?.gender ?: gender,
                reportId = scanId,
                collectedOn = scanResult?.date ?: getCurrentDateString(),
                reportedOn = scanResult?.date ?: getCurrentDateString()
            ),
            results = resultList, // from your adapter source
            fileName = "BillionTests_Urine_Report_$scanId.pdf",
            logoResId = R.mipmap.ic_launcher_round,
            isMission = selectedValue == "Mission",
            directoryPath = directoryPath,
            volume = scanMetaData?.volume ?: "",
            colorM = scanMetaData?.color ?: "",
            appearance = scanMetaData?.appearance ?: ""
        )
        scanResult?.pdfPath = uri.toString()
        scanResult?.let {
            try {
                CoroutineScope(Dispatchers.IO).launch {
                    scanResultDao.insertScanResult(it)
                    Log.e(TAG, "scan result added to db")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to save to db")
            }
            if (isCorrect.not()) {
                startUploadWork(imagePath, "Failed to get Result from cv server", null)
            } else {
                incrementScanCount()
                startUploadWork(imagePath, "Success", scanResult)
            }
        }
    }

    private suspend fun addScanToDbMission(
        imagePath: String,
        imageAnalysisResponse: MissionAnalysisResponse,
        resultList: List<UrineTestResult>
    ) {
        val sharedPreferences = getSharedPreferences(PreferenceUtils.PREFERENCE_NAME, MODE_PRIVATE)
        val currentTime = System.currentTimeMillis()
        val lastTestTime = convertMillisToDate(currentTime)
        sharedPreferences.edit().putString(PreferenceUtils.LAST_TEST_TIME, lastTestTime).apply()

        scanResult = ScanResult(
            System.currentTimeMillis(),
            scanId,
            getCurrentDateString("dd/MM/yyyy"),
            if (isCorrect) "success" else "failed",
            imagePath,
            imageAnalysisResponse.BIL,
            "",
            "",
            imageAnalysisResponse.BLO,
            imageAnalysisResponse.GLU,
            imageAnalysisResponse.KET,
            imageAnalysisResponse.LEU,
            imageAnalysisResponse.NIT,
            imageAnalysisResponse.PRO,
            imageAnalysisResponse.SG,
            imageAnalysisResponse.URO,
            imageAnalysisResponse.pH,
            false,
            false,
            true,
            patient = selectedPatient!!,
            volume = scanMetaData?.volume ?: "",
            colorM = scanMetaData?.color ?: "",
            appearance = scanMetaData?.appearance ?: ""
        )
        val name = PreferenceUtils.getString(this, PreferenceUtils.USER_NAME, "")
        val age = PreferenceUtils.getString(this, PreferenceUtils.AGE, "")
        val gender = PreferenceUtils.getString(this, PreferenceUtils.GENDER, "")

        val uri = generateUrineReportPdf(
            context = this,
            patient = PatientInfo(
                name = selectedPatient?.name?: name,
                ageYears = selectedPatient?.age?.toString() ?: age,
                gender = selectedPatient?.gender ?: gender,
                reportId = scanId,
                collectedOn = scanResult?.date ?: getCurrentDateString(),
                reportedOn = scanResult?.date ?: getCurrentDateString()
            ),
            results = resultList, // from your adapter source
            fileName = "BillionTests_Urine_Report_$scanId.pdf",
            logoResId = R.mipmap.ic_launcher_round,
            isMission = selectedValue == "Mission",
            directoryPath = directoryPath,
            volume = scanMetaData?.volume ?: "",
            colorM = scanMetaData?.color ?: "",
            appearance = scanMetaData?.appearance ?: ""
        )
        scanResult?.pdfPath = uri.toString()
        scanResult?.let {
            try {
                CoroutineScope(Dispatchers.IO).launch {
                    scanResultDao.insertScanResult(it)
                    Log.e(TAG, "scan result added to db")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to save to db")
            }
            if (isCorrect.not()) {
                startUploadWork(imagePath, "Failed to get Result from cv server", null)
            } else {
                incrementScanCount()
                startUploadWork(imagePath, "Success", scanResult)
            }
        }
    }

    // Atomically bump the per-user lifetime scan counter used for the scan limit.
    // Uses set(merge) + increment so it works even if the field/doc doesn't exist yet,
    // and never touches other user fields. Failures are logged only (best-effort).
    private fun incrementScanCount() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        FirebaseFirestore.getInstance().collection("users").document(uid)
            .set(mapOf("scanCount" to FieldValue.increment(1)), SetOptions.merge())
            .addOnFailureListener { e -> Log.e(TAG, "Failed to increment scanCount", e) }
    }

    private fun startUploadWork(imageUri: String, reason: String, scanResult: ScanResult?) {
        // Prepare input data
        val inputData = Data.Builder()
            .putString("imageUri", imageUri)
            .putString("reason", reason)
            .putString("timestamp", getCurrentDateString())
            .putString("scanResult", scanResult?.let { Gson().toJson(it) } ?: "")
            .build()

        // Create the work request
        val uploadWorkRequest: WorkRequest = OneTimeWorkRequestBuilder<UploadWorker>()
            .setInputData(inputData)
            .build()

        // Enqueue the work request
        WorkManager.getInstance(this).enqueue(uploadWorkRequest)
    }

    override fun onDetailsButtonClick(urineTestResult: UrineTestResult) {

        var detailsBottomSheet = TestDetailsBottomSheetFragment.newInstance(urineTestResult)
        detailsBottomSheet.show(supportFragmentManager, "TestDetailsBottomSheetFragment")
    }


}