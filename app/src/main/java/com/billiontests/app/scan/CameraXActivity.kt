package com.billiontests.app.scan


import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Vibrator
import android.util.Log
import android.util.Size
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.annotation.OptIn
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.AspectRatio
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.TransformExperimental
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.exifinterface.media.ExifInterface
import androidx.lifecycle.Observer
import androidx.lifecycle.lifecycleScope
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkRequest
import com.billiontests.app.R
import com.billiontests.app.databinding.ActivityCameraXactivityBinding
import com.billiontests.app.model.HorizontalRegion
import com.billiontests.app.model.ImageAnalysisResponse
import com.billiontests.app.model.MissionAnalysisResponse
import com.billiontests.app.model.Patient
import com.billiontests.app.model.QrRegion
import com.billiontests.app.model.ScanMetaData
import com.billiontests.app.model.VerticalRegion
import com.billiontests.app.network.NetworkConnectionLiveData
import com.billiontests.app.utils.CameraInfo
import com.billiontests.app.utils.LogcatToFile
import com.billiontests.app.utils.MISSION_RESPONSE
import com.billiontests.app.utils.PATIENT_SELECTED
import com.billiontests.app.utils.PreferenceUtils
import com.billiontests.app.utils.RealPathUtils
import com.billiontests.app.utils.SCAN_METADATA
import com.billiontests.app.utils.SELECTED_VALUE
import com.billiontests.app.utils.extractPoints
import com.billiontests.app.utils.getCurrentDateString
import com.billiontests.app.utils.getNV21
import com.billiontests.app.utils.imageProxyToBitmap
import com.billiontests.app.worker.UploadWorker
import com.google.android.material.button.MaterialButton
import com.google.gson.Gson
import com.king.wechat.qrcode.WeChatQRCodeDetector
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.billiontests.app.analysis.CardGeometry
import org.opencv.OpenCV
import org.opencv.android.Utils
import org.opencv.core.Mat
import org.opencv.core.Point
import org.opencv.imgproc.Imgproc
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.Objects
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors


class CameraXActivity : AppCompatActivity()
//    FileDecodeQueue.DecodeCallback
{
    private lateinit var cameraManager: CameraManager
    private lateinit var cameraProvider: ProcessCameraProvider
    private var camera: Camera? = null
    private var imageCapture: ImageCapture? = null
    private var imageAnalysis: ImageAnalysis? = null
    private lateinit var cameraExecutor: ExecutorService

    //    private val qrCodeDetector = com.ocean.star.wechatscan.WeChatQRCodeDetector()
    private var selectedCamId = ""
    private var qrResult = ""
    private var qrRect: Rect? = null
    private lateinit var targetResolution: Size
    private val REQUIRED_PERMISSIONS =
        mutableListOf(
            android.Manifest.permission.CAMERA
        ).apply {
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
                add(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        }.toTypedArray()

    private val REQUEST_READ_MEDIA_IMAGES = 100

    private var capturedImagePath = ""
    private var previewPahth = ""
    private var isFullHd = false
    private lateinit var vibrator: Vibrator

    private var cropCenter = false

    private var x1 = 0
    private var x2 = 0
    private var x3 = 0
    private var x4 = 0

    private var y1 = 0
    private var y2 = 0
    private var y3 = 0
    private var y4 = 0

    private var currentExposure: Int = 0
    private var maxExposure = 0
    private var FOCAL_LENGTH_PX = 0.0f

    private var folderName = SimpleDateFormat(
        "yyyy_MM_dd_HH_mm_ss",
        Locale.US
    ).format(System.currentTimeMillis())

    private val logFolderName = SimpleDateFormat(
        "yyyy_MM_dd_HH_mm_ss",
        Locale.US
    ).format(System.currentTimeMillis()) + "_logs"
    private var isFlashOn = false


    private val handler = Handler(Looper.getMainLooper())
    private var isQrCodeDetected = false
    private var startTime: Long = 0

    private var builder: AlertDialog.Builder? = null

    private val viewModel: ResultViewModel by viewModels()

    private var LOW_LIGHT_THRESHOLD = 70.0
    private var UPPER_LIGHT_THRESHOLD = 150.0

    private var minDistance = 15
    private var maxDistance = 25

    private var selectedValue = "Mission"

    private var selectedPatient: Patient? = null
    private var scanMetaData: ScanMetaData? = null

    companion object {
        private const val TAG = "CameraXActivity"
        private const val TIMER_TAG = "TIMER_TAG"
        private var DEFAULT_ZOOM =
            1.0f // Change this to the desired default zoom level

        // Adjust based on environment
    }

    private var allowCapture = true
    private var capturingImage = false

    private var qrAtBottom = false
    private var allowDistanceCapture = true
    private var allMarkersDetected = false

    private lateinit var binding: ActivityCameraXactivityBinding

    private var alertDialog: AlertDialog? = null
    private lateinit var customLayout: View

    private lateinit var networkConnectionLiveData: NetworkConnectionLiveData

//    private var isInternetAvailable = false

    private lateinit var imagePickerLauncher: ActivityResultLauncher<String>

    private var resolutions = mutableSetOf<Size>()
    private lateinit var selectedResolution: Size
    private var camerasInfoList: ArrayList<CameraInfo> = arrayListOf()

    @ExperimentalGetImage
    private val activityResultLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        )
        { permissions ->
            // Handle Permission granted/rejected
            var permissionGranted = true
            permissions.entries.forEach {
                if (it.key in REQUIRED_PERMISSIONS && !it.value)
                    permissionGranted = false
            }
            if (!permissionGranted) {
                Toast.makeText(
                    baseContext,
                    "Permission request denied",
                    Toast.LENGTH_SHORT
                ).show()
            } else {
//                startCamera()
                showScanMetaDataDialogBeforeStartCamera()
            }
        }

    private val prefs by lazy {
        getSharedPreferences("my_prefs", Context.MODE_PRIVATE)
    }


    @OptIn(ExperimentalGetImage::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityCameraXactivityBinding.bind(
            LayoutInflater.from(this).inflate(R.layout.activity_camera_xactivity, null)
        )
        ViewCompat.setOnApplyWindowInsetsListener(binding.container) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())

            // Apply top padding to the container to avoid status bar overlap
            v.updatePadding(top = systemBars.top)
            insets
        }
        val fileName = "$logFolderName.txt"

        OpenCV.initOpenCV()
        WeChatQRCodeDetector.init(this)
        setContentView(binding.root)

        selectedValue = intent.getStringExtra(SELECTED_VALUE) ?: "Mission"
        selectedPatient = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(PATIENT_SELECTED, Patient::class.java)
        } else {
            intent.getParcelableExtra(PATIENT_SELECTED)
        }

        networkConnectionLiveData = NetworkConnectionLiveData(this)

        val file = File(
            outputAppDirectory,
            fileName
        )
        folderName = SimpleDateFormat(
            "yyyy_MM_dd_HH_mm_ss",
            Locale.US
        ).format(System.currentTimeMillis())

        PreferenceUtils.putString(this, PreferenceUtils.LAST_FOLDER_NAME, folderName)
        LogcatToFile.startLogging(file.absolutePath)

        cameraManager = getSystemService(CAMERA_SERVICE) as CameraManager
        getSupportedResulutions()
        resolutions.forEach {
            Log.e(TAG, "resolution: ${it.width} x ${it.height}")
            val aspect = aspectRatio(it.width, it.height)
            val resolution = getMegaPixelFromSize(it)
            resolution?.let { reso ->
                val res = reso.toDouble()
                Log.e(TAG, "onCreated: resolution: $resolution, aspect ratio: $aspect")
                if (aspect != 1 && (res >= 2.0)) {
                    if (::selectedResolution.isInitialized.not() || isHigherResolution(
                            it,
                            selectedResolution
                        )
                    ) {
                        selectedResolution = it
                        Log.e(TAG, "onCreated: selected resolution: $selectedResolution")
                    }
                }
            }
        }
        vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator

        binding.captureButton.setOnClickListener {
            if (!allMarkersDetected) {
                Toast.makeText(
                    this,
                    "Align the card so all 4 corner markers are visible",
                    Toast.LENGTH_SHORT
                ).show()
                return@setOnClickListener
            }
            binding.captureButton.isEnabled = false
            binding.captureButton.text = "Please wait"
            capturingImage = true
            takePhoto()
        }

        if (allPermissionsGranted()) {
//            startCamera()
            showScanMetaDataDialogBeforeStartCamera()
        } else {
            requestPermissions()
        }

        val points = ArrayList<Mat>()
        // Initialize the launcher
        imagePickerLauncher =
            registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
                uri?.let {
                    val savedUri = it

                    capturedImagePath =
                        it.let { RealPathUtils.getRealPath(this@CameraXActivity, it) }!!
                    val msg = "Photo capture succeeded: $capturedImagePath"
                    Log.e(TAG, msg)
                    val imgBitmap = createBitmapFromPath(capturedImagePath)
                    imgBitmap?.let {
                        runOnUiThread {
                            binding.captureButton.isEnabled = false
                            binding.captureButton.setText("Please wait")
                            binding.detectProgressView.isVisible = true
                            closeCamera()
                        }
                        Log.e(TIMER_TAG, "onImageSaved: ${System.currentTimeMillis()}")
                        Handler(Looper.getMainLooper()).postDelayed(Runnable {
                            lifecycleScope.launch {
                                try {
                                    var results = mutableListOf<String>()
                                    var retryCount = 0 // Initialize the retry counter

                                    // Retry loop for detecting and decoding QR code
                                    while (retryCount < 4) {
                                        results =
                                            WeChatQRCodeDetector.detectAndDecode(imgBitmap, points)
                                        Log.e(TAG, "result: $results")

                                        // Break out of the loop if results are not empty
                                        if (!results.isNullOrEmpty()) {
                                            break
                                        }

                                        retryCount++ // Increment the retry count if results are empty or null
                                        Log.e(TAG, "Retry attempt: $retryCount")
                                    }

                                    if (results.isEmpty() && retryCount >= 4) {
                                        // If retries exhausted, enable the button and show a message
                                        binding.captureButton.isEnabled = true
                                        binding.captureButton.text = "Capture"
                                        binding.detectProgressView.isVisible = false
                                        createCustomDialog(
                                            getString(R.string.scan_qr_not_found),
                                            null,
                                            "Capture",
                                            "Cancel"
                                        )
                                    } else {
                                        closeCamera()
                                        points.forEach { mat ->
                                            // Scan result points (rectangle of QR code)
                                            Log.d(TAG, "point0: ${mat[0, 0][0]}, ${mat[0, 1][0]}")
                                            Log.d(TAG, "point1: ${mat[1, 0][0]}, ${mat[1, 1][0]}")
                                            Log.d(TAG, "point2: ${mat[2, 0][0]}, ${mat[2, 1][0]}")
                                            Log.d(TAG, "point3: ${mat[3, 0][0]}, ${mat[3, 1][0]}")
                                            x1 = mat[0, 0][0].toInt()
                                            x2 = mat[1, 0][0].toInt()
                                            x3 = mat[2, 0][0].toInt()
                                            x4 = mat[3, 0][0].toInt()

                                            y1 = mat[0, 1][0].toInt()
                                            y2 = mat[1, 1][0].toInt()
                                            y3 = mat[2, 1][0].toInt()
                                            y4 = mat[3, 1][0].toInt()
                                        }
                                        Log.e(
                                            TIMER_TAG,
                                            "qr detected: ${System.currentTimeMillis()}"
                                        )

//                                        val imagePath = saveBitmapToFile(imgBitmap)
                                        val rotate =
                                            if (imgBitmap.width > imgBitmap.height) 270f else 180f
                                        val imagePath = saveBitmapFast(
                                            bitmap = imgBitmap,
                                            outputDir = outputAppDirectory,
                                            saveOriginalAlso = true,                   // true only if you really need both
                                            rotateDegrees = rotate,
                                            format = Bitmap.CompressFormat.JPEG,        // ← fastest common choice
                                            quality = 100
                                        )
                                        if (imagePath != "") {
                                            previewPahth = imagePath
                                            performImageUpload(imagePath)
                                        } else {
                                            runOnUiThread {
                                                binding.captureButton.isEnabled = true
                                                binding.captureButton.text = "Capture"
                                                binding.detectProgressView.isVisible = false
                                                createCustomDialog(
                                                    getString(R.string.scan_image_save_failed),
                                                    null,
                                                    "Capture",
                                                    "Cancel"
                                                )
                                            }
                                        }
                                    }
                                } catch (e: RuntimeException) {
                                    e.printStackTrace()
                                    runOnUiThread {
                                        binding.captureButton.isEnabled = true
                                        binding.captureButton.text = "Capture"
                                        binding.detectProgressView.isVisible = false
                                        createCustomDialog(
                                            getString(R.string.scan_qr_not_found),
                                            null,
                                            "Capture",
                                            "Cancel"
                                        )
                                    }
                                }
                            }


                        }, 500)
                    } ?: let {
                        Log.e(TAG, "image bitmap is null")
                        createCustomDialog(
                            getString(R.string.scan_image_prepare_failed),
                            null,
                            "Capture",
                            "Cancel"
                        )

                    }
                }
            }

        binding.pickGalleryButton.setOnClickListener {
            imagePickerLauncher.launch("image/*")

        }

        minDistance = prefs.getInt("minDistance", 14)
        maxDistance = prefs.getInt("maxDistance", 20)
        binding.settingIv.setOnClickListener {
            // From Activity
            LumaBottomSheetDialogFragment(
                previousLower = minDistance.toString(),
                previousUpper = maxDistance.toString()
            ) { lower, upper ->
                // Save new values
                Log.e("LUMA", "Lower: $lower, Upper: $upper")
                minDistance = lower.toInt()
                maxDistance = upper.toInt()
                runOnUiThread {
                    prefs.edit { putInt("minDistance", minDistance) }
                    prefs.edit { putInt("maxDistance", maxDistance) }
                    closeCamera()
//                cameraExecutor.shutdown()
                    startCamera()
                }
            }.show(supportFragmentManager, "LumaBottomSheet")

        }
        viewModel.resultLiveData.observe(this, Observer {
            it?.let { urinalysisResponse ->
                Log.e(TIMER_TAG, "result received")
                urinalysisResponse.onSuccess { response ->
                    response?.let {
//                        val colorValues = response.colorValues
//                        for (color in colorValues) {
//                            Log.d("UrinalysisActivity", "Color: $color")
                        saveAnalysisResultToAppDir(response)
                        goToResult(previewPahth, it, null)
//                        }
                    } ?: runOnUiThread {
                        binding.captureButton.isEnabled = true
                        binding.captureButton.text = "Capture"
                        binding.detectProgressView.isVisible = false
//                      startCamera()
                        createCustomDialog(
                            getString(R.string.analysis_unexpected_error),
                            null,
                            "Try Again",
                            "Cancel"
                        )
                    }
                }

                urinalysisResponse.onFailure { throwable ->
                    saveAnalysisResultToAppDir(throwable)
                    runOnUiThread {
                        binding.captureButton.isEnabled = true
                        binding.captureButton.text = "Capture"
                        binding.detectProgressView.isVisible = false
                        createCustomDialog(
                            (throwable as? LocalAnalysisException)?.message
                                ?: getString(R.string.analysis_unexpected_error),
                            null,
                            "Try Again",
                            "Cancel"
                        )
                    }
                }

            }
        })

        viewModel.missiontLiveData.observe(this, Observer {
            it?.let { urinalysisResponse ->
                Log.e(TIMER_TAG, "result received")
                urinalysisResponse.onSuccess { response ->
                    response?.let {

                        saveAnalysisMissionResultToAppDir(response)
                        goToResult(previewPahth, null, it)
                    } ?: runOnUiThread {
                        binding.captureButton.isEnabled = true
                        binding.captureButton.text = "Capture"
                        binding.detectProgressView.isVisible = false
                        createCustomDialog(
                            getString(R.string.analysis_unexpected_error),
                            null,
                            "Try Again",
                            "Cancel"
                        )
                    }
                }

                urinalysisResponse.onFailure { throwable ->
                    saveAnalysisResultToAppDir(throwable)
                    runOnUiThread {
                        binding.captureButton.isEnabled = true
                        binding.captureButton.text = "Capture"
                        binding.detectProgressView.isVisible = false
                        createCustomDialog(
                            (throwable as? LocalAnalysisException)?.message
                                ?: getString(R.string.analysis_unexpected_error),
                            null,
                            "Try Again",
                            "Cancel"
                        )
//                        startCamera()
                    }
                }

            }
        })
//        binding.zoomSwitch.setOnCheckedChangeListener { buttonView, isChecked ->
//            DEFAULT_ZOOM = if (isChecked) 2f else 2.5f
//            buttonView.text = if (isChecked) "2X" else "2.5X"
//            closeCamera()
//            startCamera()
//        }
        cameraExecutor = Executors.newSingleThreadExecutor()
    }

    fun isHigherResolution(a: Size, b: Size): Boolean {
        val pixelsA = a.width.toLong() * a.height
        val pixelsB = b.width.toLong() * b.height
        return pixelsA > pixelsB
    }

    fun saveAnalysisResultToAppDir(response: Throwable) {
        // Serialize plain strings, not Throwable internals accessed through reflection.
        // Saving diagnostics must never prevent the failure dialog from opening.
        runCatching {
            val jsonString = Gson().toJson(
                mapOf(
                    "message" to response.message.orEmpty(),
                    "type" to response.javaClass.name,
                    "stackTrace" to Log.getStackTraceString(response)
                )
            )
            val file = File(outputAppDirectory, "analysis_failed_result_${System.currentTimeMillis()}.txt")
            file.writeText(jsonString)
            Log.d(TAG, "Saved analysis failure at: ${file.absolutePath}")
        }.onFailure { error ->
            Log.e(TAG, "Could not save analysis failure details", error)
        }
    }


    fun saveAnalysisResultToAppDir(response: ImageAnalysisResponse) {
        // Convert the object to JSON string
        val jsonString = Gson().toJson(response)

        // Get the output directory
        val dir = outputAppDirectory

        // Create a .txt file (you can use timestamp to make each file unique)
        val file = File(dir, "analysis_result_${System.currentTimeMillis()}.txt")

        // Write JSON into file
        file.writeText(jsonString)

        println("Saved JSON file at: ${file.absolutePath}")
    }

    fun saveAnalysisMissionResultToAppDir(response: MissionAnalysisResponse) {
        // Convert the object to JSON string
        val jsonString = Gson().toJson(response)

        // Get the output directory
        val dir = outputAppDirectory

        // Create a .txt file (you can use timestamp to make each file unique)
        val file = File(dir, "analysis_result_${System.currentTimeMillis()}.txt")

        // Write JSON into file
        file.writeText(jsonString)

        println("Saved JSON file at: ${file.absolutePath}")
    }


    override fun onResume() {
        super.onResume()
//        networkConnectionLiveData.observe(this, Observer { isConnected ->
//            Log.e("Internet", "is internet available: $isConnected")
//            isInternetAvailable = isConnected
//            if (isConnected.not()) {
//                showNoInternetDialog()
//            }
//        })
    }

    override fun onDestroy() {
        super.onDestroy()
        closeCamera()
        cameraExecutor.shutdown()
    }


    @OptIn(ExperimentalCamera2Interop::class)
    fun selectExternalOrBestCamera(provider: ProcessCameraProvider): CameraSelector? {
        val cam2Infos = provider.availableCameraInfos.map {
            Camera2CameraInfo.from(it)
        }.sortedByDescending {
            // HARDWARE_LEVEL is Int type, with the order of:
            // LEGACY < LIMITED < FULL < LEVEL_3 < EXTERNAL
            it.getCameraCharacteristic(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL)
        }

        return when {
            cam2Infos.isNotEmpty() -> {
                CameraSelector.Builder()
                    .addCameraFilter {
                        it.filter { camInfo ->
                            // cam2Infos[0] is either EXTERNAL or best built-in camera
                            val thisCamId = Camera2CameraInfo.from(camInfo).cameraId
                            selectedCamId = thisCamId
                            thisCamId == cam2Infos[0].cameraId
                        }
                    }.build()
            }

            else -> null
        }
    }


    private fun getSupportedResulutions() {
        try {
            for (cameraId in cameraManager.cameraIdList) {
                val characteristics = cameraManager.getCameraCharacteristics(
                    cameraId
                )

                val physicalSize =
                    characteristics.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE)
                sensorWidthMm = physicalSize?.width ?: 0.0f
                sensorWidthPx =
                    characteristics.get(CameraCharacteristics.SENSOR_INFO_PIXEL_ARRAY_SIZE)?.width
                        ?: 0

                val focalLengths =
                    characteristics.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)

                if (focalLengths != null && focalLengths.isNotEmpty()) {
                    val lensFacing = characteristics.get(CameraCharacteristics.LENS_FACING)
                    val focalLength = focalLengths[0]
                    camerasInfoList.add(
                        CameraInfo(
                            focalLength = focalLength,
                            lensFacing = lensFacing
                        )
                    )
                }

                Log.e(TAG, "focal length: $FOCAL_LENGTH_PX")
                // We don't use a front facing camera in this sample.
                val facing = characteristics.get(CameraCharacteristics.LENS_FACING)
                val yourMinFocus = characteristics.get(
                    CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS
                )
                Objects.requireNonNull(yourMinFocus)
                val yourMaxFocus = characteristics.get(
                    CameraCharacteristics.LENS_INFO_HYPERFOCAL_DISTANCE
                )

                Log.d(
                    TAG,
                    "onCreate: yourMaxFocus $yourMaxFocus"
                )
                for (focus in yourMinFocus!!) {
                    Log.d(
                        TAG,
                        "onCreate: focus $focus"
                    )
                }
                if (facing != null && facing == CameraCharacteristics.LENS_FACING_FRONT) {
                    continue
                }
                val map = characteristics.get(
                    CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP
                )
                Objects.requireNonNull(map)
                Log.d(
                    TAG, "bindPreview: " +
                            characteristics.get<Int>(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL)
                )
                val sizes = map!!.getOutputSizes(
                    SurfaceTexture::class.java
                )
                val screenWidth = resources.displayMetrics.widthPixels
                val screenHeight = resources.displayMetrics.heightPixels
                Log.d(
                    TAG,
                    "onCreate: screen width $screenWidth"
                )
                Log.d(
                    TAG,
                    "onCreate: screen Height $screenHeight"
                )

                resolutions.addAll(sizes)
                resolutions.forEach {
                    Log.e(TAG, "resolution: $it\n")
                }
            }
        } catch (e: CameraAccessException) {
            e.printStackTrace()
        }
    }

    fun computeFocalLengthPx(characteristics: CameraCharacteristics): Double {
        val focalLengths = characteristics.get(
            CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS
        )
        val sensorSize = characteristics.get(
            CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE
        )
        val activeArray = characteristics.get(
            CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE
        )

        require(!(focalLengths == null || sensorSize == null || activeArray == null)) {
            "Missing camera characteristics"
        }

        val fMm = focalLengths[0]                      // e.g. 4.2 mm
        val sensorWidthMm = sensorSize.width          // e.g. ~5.8 mm
        val imageWidthPx = activeArray.width().toFloat() // e.g. 4032 px

        val fPx = fMm / sensorWidthMm * imageWidthPx  // ≈ thousands of pixels

        return fPx.toDouble()
    }

    private fun getMegaPixelFromSize(size: Size): String? {
        val str = ((size.width * size.height).toFloat() / 1000000L).toString() + ""
        Log.d(
            TAG,
            "getMegaPixelFromSize: str $str"
        )
        return if (str.contains(".")) {
            str.substring(0, str.lastIndexOf(".") + 2)
        } else {
            str
        }
    }

    private fun aspectRatio(widthPixels: Int, heightPixels: Int): Int {
        val previewRatio = Math.max(
            widthPixels.toDouble(),
            heightPixels.toDouble()
        ) / Math.min(widthPixels.toDouble(), heightPixels.toDouble())
        return if (Math.abs(previewRatio - 1.33) <= Math.abs(previewRatio - 1.78)) {
            AspectRatio.RATIO_4_3
        } else AspectRatio.RATIO_16_9
    }

    private fun rotateBitmapIfRequired(bitmap: Bitmap, imagePath: String): Bitmap {
        val exif = ExifInterface(imagePath)
        val orientation =
            exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)

        Log.e(TAG, "orientation: $orientation")
        return when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> rotateBitmap(bitmap, 90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> rotateBitmap(bitmap, 180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> rotateBitmap(bitmap, 270f)
            else -> bitmap
        }
    }

    private fun rotateBitmap(bitmap: Bitmap, degrees: Float): Bitmap {
        val matrix = Matrix()
        matrix.postRotate(degrees)
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    fun getCameraPhotoOrientation(context: Context, imageUri: Uri?, imagePath: String): Int {
        var rotate = 0
        try {
            context.contentResolver.notifyChange(imageUri!!, null)
            val imageFile = File(imagePath)

            val exif = ExifInterface(imageFile.absolutePath)
            val orientation = exif.getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )

            when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_270 -> rotate = 270
                ExifInterface.ORIENTATION_ROTATE_180 -> rotate = 180
                ExifInterface.ORIENTATION_ROTATE_90 -> rotate = 90
            }
            Log.e(TAG, "Exif orientation: $orientation")
            Log.e(TAG, "Rotate value: $rotate")
        } catch (e: java.lang.Exception) {
            e.printStackTrace()
        }
        return rotate
    }

    private fun saveBitmapToFile(bitmap: Bitmap): String {
        Log.e(TIMER_TAG, "saving bitmap to file")
        return try {
            val name = SimpleDateFormat(
                "yyyyMMddHHmmss",
                Locale.US
            ).format(System.currentTimeMillis()) + ".PNG"

            // Save the original image
            val originalFileName = "scanned_image_$name"
            val originalFile = File(outputAppDirectory, originalFileName)

            if (!originalFile.parentFile.exists()) {
                originalFile.parentFile.mkdirs()
            }
            val originalFos = FileOutputStream(originalFile)
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, originalFos)
            originalFos.close()

            // Log the original file path
            val originalPath = originalFile.absolutePath
            Log.e(TIMER_TAG, "saved original scan file path: $originalPath")

//            val rBitmap = rotateBitmapIfRequired(bitmap,originalPath)
            Log.e(TIMER_TAG, "captured bitmap width: ${bitmap.width}, height: ${bitmap.height}")
            var rotatedBitmap: Bitmap? = null
            rotatedBitmap = if (bitmap.width > bitmap.height) {
                rotateBitmap(bitmap, 270f)
            } else {
                rotateBitmap(bitmap, 180f)
            }

            // Save the rotated image
            val rotatedFileName = "scanned_image_rotated_$name"
            val rotatedFile = File(outputAppDirectory, rotatedFileName)

            val rotatedFos = FileOutputStream(rotatedFile)
            rotatedBitmap.compress(Bitmap.CompressFormat.PNG, 100, rotatedFos)
            rotatedFos.close()

            // Log the rotated file path
            val rotatedPath = rotatedFile.absolutePath
            Log.e(TAG, "saved rotated scan file path: $rotatedPath")
            Log.e(TIMER_TAG, "saved rotated scan file path: $rotatedPath")

            return rotatedPath // Return the original image path, or return the rotatedPath if preferred
        } catch (e: IOException) {
            Log.e(TAG, "failed to save scanned bitmap to file: ${e.message}")
            ""
        } catch (e: Exception) {
            Log.e(TAG, "failed to save scanned bitmap to file: ${e.message}")
            ""
        }
    }


    private val NAME_FMT = SimpleDateFormat("yyyyMMddHHmmss", Locale.US)

    suspend fun saveBitmapFast(
        bitmap: Bitmap,
        outputDir: File,
        saveOriginalAlso: Boolean = false,
        rotateDegrees: Float? = null,            // e.g. 270f or 180f; null = no rotation
        format: Bitmap.CompressFormat = Bitmap.CompressFormat.JPEG,
        quality: Int = 90                        // for JPEG/WEBP lossy
    ): String = withContext(Dispatchers.IO) {
        val ts = NAME_FMT.format(System.currentTimeMillis())

        // Ensure dir
        if (!outputDir.exists()) outputDir.mkdirs()

        // If image is fully opaque, tell encoder (helps PNG/WEBP)
        if (!bitmap.hasAlpha()) bitmap.setHasAlpha(false)

        val rotatedBmp: Bitmap? = rotateDegrees?.let { deg ->
            val m = Matrix().apply { postRotate(deg) }
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, m, /*filter*/ false)
        }

        try {
            var rotatedPath = ""

            if (saveOriginalAlso) {
                // Compress ONCE to memory for the original, then write to disk
                val origBytes =
                    ByteArrayOutputStream(bitmap.byteCount.coerceAtMost(4 * 1024 * 1024)).use { baos ->
                        bitmap.compress(format, quality, baos)
                        baos.toByteArray()
                    }
                val originalFile = File(outputDir, "scanned_image_${ts}.${extFor(format)}")
                BufferedOutputStream(FileOutputStream(originalFile), 64 * 1024).use {
                    it.write(
                        origBytes
                    )
                }
                // Log path if needed
            }

            val toSave = rotatedBmp ?: bitmap
            val rotatedFile = File(outputDir, "scanned_image_rotated_${ts}.${extFor(format)}")
            BufferedOutputStream(FileOutputStream(rotatedFile), 64 * 1024).use { bos ->
                toSave.compress(format, quality, bos)
            }
            rotatedPath = rotatedFile.absolutePath

            rotatedPath
        } catch (e: Exception) {
            android.util.Log.e("SaveBitmap", "Failed to save: ${e.message}", e)
            ""
        } finally {
            // Recycle temporary rotated bitmap if created
            if (rotatedBmp != null && rotatedBmp != bitmap && !rotatedBmp.isRecycled) {
                rotatedBmp.recycle()
            }
        }
    }

    private fun extFor(format: Bitmap.CompressFormat): String = when (format) {
        Bitmap.CompressFormat.JPEG -> "jpg"
        Bitmap.CompressFormat.PNG -> "png"
        Bitmap.CompressFormat.WEBP, @Suppress("DEPRECATION") Bitmap.CompressFormat.WEBP_LOSSLESS, @Suppress(
            "DEPRECATION"
        ) Bitmap.CompressFormat.WEBP_LOSSY -> "webp"

        else -> "img"
    }

    @OptIn(ExperimentalGetImage::class)
    private fun showScanMetaDataDialogBeforeStartCamera() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_scan_metadata, null)

        val volumeEt =
            dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.volumeEt)
        val colorEt =
            dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.colorEt)
        val appearanceEt =
            dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.appearanceEt)

        val skipButton =
            dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.skipButton)
        val addButton =
            dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.addButton)

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(false)
            .create()

        skipButton.setOnClickListener {
            scanMetaData = null
            dialog.dismiss()
            startCamera()
        }

        addButton.setOnClickListener {
            val volume = volumeEt.text.toString().trim()
            val color = colorEt.text.toString().trim()
            val appearance = appearanceEt.text.toString().trim()

            scanMetaData = ScanMetaData(
                volume = volume,
                color = color,
                appearance = appearance
            )

            Log.d(TAG, "Scan metadata: $scanMetaData")
            dialog.dismiss()
            startCamera()
        }

        dialog.show()
    }

    @OptIn(TransformExperimental::class)
    @ExperimentalGetImage
    private fun startCamera() {

        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        startTime = System.currentTimeMillis()

        // Start the delayed actions

        cameraProviderFuture.addListener({
            // Used to bind the lifecycle of cameras to the lifecycle owner
            cameraProvider = cameraProviderFuture.get()

            val resolution = selectedResolution
            val aspectRatio = aspectRatio(resolution.width, resolution.height)

            Log.e(TAG, "selected resolution to start camera: ${getMegaPixelFromSize(resolution)}")
            Log.e(TAG, "aspect ratio: $aspectRatio")

            val resolutionSelector = ResolutionSelector.Builder().setResolutionStrategy(
                ResolutionStrategy(
                    resolution,
                    ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER
                )
            ).build()

            // Preview
            val preview = Preview.Builder()
                .setTargetAspectRatio(AspectRatio.RATIO_16_9)
//                .setResolutionSelector(resolutionSelector)
                .build()
                .also {
                    it.setSurfaceProvider(binding.viewFinder.surfaceProvider)
                }

            imageCapture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                .setTargetAspectRatio(AspectRatio.RATIO_16_9)
//                .setResolutionSelector(resolutionSelector)
                .build()

            Log.e(TAG, "no. of resolutions: ${resolutions.size}")
//            Log.e(TAG, "selected resolution for analysis: $resolution")

            imageAnalysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageRotationEnabled(true)
//                .setResolutionSelector(resolutionSelector)
                .build()


            val points = ArrayList<Mat>()
            imageAnalysis?.setAnalyzer(cameraExecutor) { imageProxy ->

                val buffer = imageProxy.planes[0].buffer
                val data = ByteArray(buffer.remaining())
                buffer.get(data)

                // Compute average luminance
                val luma = data.map { it.toInt() and 0xFF }.average()

//                allowCapture =  (luma > LOW_LIGHT_THRESHOLD) && (luma < UPPER_LIGHT_THRESHOLD)
//                Log.e(TAG, "luma : $luma, ")
                // insert your code here.
                imageProxy.image?.let {
                    val nv21 = it.getNV21()
                    val width = it.width
                    val height = it.height
                    val bitmap = imageProxyToBitmap(nv21, width, height)

                    if(capturingImage){
                        Log.e(TAG,"capturing image...")
                        return@let
                    }
                    if (bitmap != null) {

                        // All 4 ArUco markers must be in frame or the captured image cannot be
                        // rectified (see UrinalysisAnalyzer). Gate capture on this per frame.
                        allMarkersDetected = detectArucoCount(bitmap) == 4

                        try {
                            val results = WeChatQRCodeDetector.detectAndDecode(bitmap, points)

                            isQrCodeDetected = results.isNullOrEmpty().not()

                            runOnUiThread {
//                                if(isQrCodeDetected) {
//                                    var distance = computeQrDistance(points[0])
//                                    Log.e(TAG,"distance from device : ${distance*100} cm")
//
//                                }
                                if (results.isNullOrEmpty().not()) {

                                    Log.e(TAG, "QR code detected")
                                    for (i in results.indices) {
                                        val points = points
                                        val qrText = results[i]
                                        val cornersMat = points[i]

                                        Log.e(TAG, "qr text : $qrText")
                                        Log.e(TAG, "corners : $cornersMat")
                                        val corners = extractPoints(cornersMat)   // List<Point>

                                        val (qrWidthPx, qrHeightPx) = qrSizeFromCorners(corners)
                                        Log.e(
                                            "My TAG ==>",
                                            "Wechat WidthPx = $qrWidthPx, HeightPx = $qrHeightPx"
                                        )

                                        Log.e(TAG, "corners : $corners")
                                        val region = classifyQrPosition(
                                            corners,
                                            bitmap.width,
                                            bitmap.height
                                        )

                                        Log.e(
                                            TAG,
                                            "QR '$qrText' is at ${region.vertical} - ${region.horizontal}"
                                        )
                                        allowCapture =
                                            (region.vertical == VerticalRegion.BOTTOM && region.horizontal == HorizontalRegion.CENTER) ||
                                                    (region.vertical == VerticalRegion.TOP && region.horizontal == HorizontalRegion.CENTER)

                                        qrAtBottom = region.vertical == VerticalRegion.BOTTOM
                                    }
                                } else {
                                    Log.e(TAG, "QR code not detected")
                                    binding.distanceTv.text = " "
                                }

                                binding.captureHeaderTv.text = if (!allMarkersDetected) {
                                    "Align the card so all 4 corner markers are visible"
                                } else {
                                    getString(R.string.capture_test_card_instruction)
                                }

                                binding.captureButton.isVisible =
                                    results.isNullOrEmpty()
                                        .not() && allowDistanceCapture && allMarkersDetected

                            }
                        } catch (e: RuntimeException) {
                            e.printStackTrace()
                        }
                    } else {
                        Log.e(TAG, "bitmap is null")
                    }
                } ?: Log.e(TAG, "image is null")
                imageProxy.close()
                //<======================================================================================== Detection Code ==================================>

            }

            try {
                // Unbind use cases before rebinding
                cameraProvider.unbindAll()

                val selector = CameraSelector.DEFAULT_BACK_CAMERA
                // Bind use cases to camera
                camera =
                    cameraProvider.bindToLifecycle(
                        this, selector!!, preview, imageCapture, imageAnalysis
                    )
                val exposureState = camera?.cameraInfo?.exposureState
                maxExposure = exposureState?.exposureCompensationRange?.upper ?: 0
                currentExposure = exposureState?.exposureCompensationIndex ?: 0

                Log.e(TAG, "max exposure: $maxExposure , current exposure: $currentExposure")
                scheduleShowDialog()
                Log.e(TAG, "starting camera ...")
            } catch (exc: Exception) {
                Log.e(TAG, "Use case binding failed", exc)
            }

        }, ContextCompat.getMainExecutor(this))

    }

    /** Number of ArUco markers visible in [bitmap] (0..4). Used to gate capture. */
    private fun detectArucoCount(bitmap: Bitmap): Int {
        val rgba = Mat()
        val gray = Mat()
        return try {
            Utils.bitmapToMat(bitmap, rgba)
            Imgproc.cvtColor(rgba, gray, Imgproc.COLOR_RGBA2GRAY)
            CardGeometry.markerCenters(gray).size
        } catch (e: Exception) {
            Log.e(TAG, "aruco detect failed: ${e.message}")
            0
        } finally {
            rgba.release()
            gray.release()
        }
    }

    fun qrSizeFromCorners(corners: List<Point>): Pair<Double, Double> {
        val p0 = corners[0]
        val p1 = corners[1]
        val p2 = corners[2]
        val p3 = corners[3]

        fun dist(a: Point, b: Point): Double {
            val dx = a.x - b.x
            val dy = a.y - b.y
            return Math.hypot(dx, dy)
        }

        // Width edges: p0→p1 and p3→p2
        val width1 = dist(p0, p1)
        val width2 = dist(p3, p2)
        val width = (width1 + width2) / 2.0

        // Height edges: p1→p2 and p0→p3
        val height1 = dist(p1, p2)
        val height2 = dist(p0, p3)
        val height = (height1 + height2) / 2.0

        return width to height
    }

    private var sensorWidthMm = 0.0f
    private var sensorWidthPx = 0

    @SuppressLint("DefaultLocale")
    private fun calculateDistance(width: Int, camerasInfoList: ArrayList<CameraInfo>) {

        camerasInfoList.forEachIndexed { index, cameraInfo ->

            if (index == 0) {
                Log.e("My TAG ==>", "Start")
            }
            if (cameraInfo.lensFacing == CameraCharacteristics.LENS_FACING_BACK) {
                val focalLength = cameraInfo.focalLength

                val realQRSize = 19
                val distance =
                    String.format("%.2f", (((realQRSize * focalLength) / width) * 10).minus(1.5))
                Log.d(
                    "My TAG ==>",
                    "Distance of QR is $distance CM according to camera lens facing ${cameraInfo.lensFacing} $width"
                )
                if (index == 0) {
                    binding.distanceTv.text = "$distance CM"
                    allowDistanceCapture =
                        distance.toDouble() >= minDistance && distance.toDouble() <= maxDistance
                }
            }
        }
    }

    private fun classifyQrPosition(
        corners: List<Point>,
        imgWidth: Int,
        imgHeight: Int
    ): QrRegion {

        // 1. QR center point
        val cx = corners.map { it.x }.average()
        val cy = corners.map { it.y }.average()

        // 2. Vertical region
        val vRegion = when {
            cy < imgHeight / 3.0 -> VerticalRegion.TOP
            cy > imgHeight * 2 / 3.0 -> VerticalRegion.BOTTOM
            else -> VerticalRegion.CENTER
        }

        // 3. Horizontal region
        val hRegion = when {
            cx < imgWidth / 3.0 -> HorizontalRegion.LEFT
            cx > imgWidth * 2 / 3.0 -> HorizontalRegion.RIGHT
            else -> HorizontalRegion.CENTER
        }

        return QrRegion(vRegion, hRegion)
    }


    private fun goToResult(
        imagePath: String,
        imageAnalysisResponse: ImageAnalysisResponse?,
        missionAnalysisResponse: MissionAnalysisResponse?
    ) {
        Log.e(TAG, "image analysis response: ${imageAnalysisResponse.toString()}")
        Log.e(TAG, "mission analysis response: ${missionAnalysisResponse.toString()}")
        startActivity(
            Intent(this, ResultActivity::class.java)
                .putExtra("path", imagePath)
                .putExtra(
                    if (selectedValue == "Mission") MISSION_RESPONSE else "response",
                    if (selectedValue == "Mission") missionAnalysisResponse else imageAnalysisResponse
                )
                .putExtra("addToDb", true)
                .putExtra(SELECTED_VALUE, selectedValue)
                .putExtra(PATIENT_SELECTED, selectedPatient)
                .putExtra(SCAN_METADATA, scanMetaData)
        )
        finish()
    }


    private fun takePhoto() {
        val points = ArrayList<Mat>()
        // Get a stable reference of the modifiable image capture use case
        val imageCapture = imageCapture ?: return


        val fileName = SimpleDateFormat("yyyy_MM_dd_HH_mm_ss", Locale.US).format(
            System.currentTimeMillis()
        ) + ".PNG"

        val file = File(
            outputAppDirectory,
            fileName
        )

        // Create output options object which contains file + metadata
        val outputOptions = ImageCapture.OutputFileOptions
            .Builder(
                file
            )
            .build()

        // Set up image capture listener, which is triggered after photo has
        // been taken

        imageCapture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
                override fun onError(exc: ImageCaptureException) {
                    Log.e(TAG, "Photo capture failed: ${exc.message}", exc)
                    capturingImage = false
                    createCustomDialog(getString(R.string.scan_capture_failed), null, "Try Again", "Cancel")
                }

                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    val savedUri = output.savedUri
                    capturedImagePath =
                        savedUri?.let { RealPathUtils.getRealPath(this@CameraXActivity, it) } ?: ""
                    val msg = "Photo capture succeeded: $capturedImagePath"
                    Log.e(TAG, msg)
                    if (capturedImagePath.isNotEmpty()) {
                        val imgBitmap = createBitmapFromPath(capturedImagePath)
                        imgBitmap?.let {
                            runOnUiThread {
                                binding.captureButton.isEnabled = false
                                binding.captureButton.setText("Please wait")
                                binding.detectProgressView.isVisible = true
                                capturingImage = false
                                closeCamera()
                            }
                            Log.e(TIMER_TAG, "onImageSaved: ${System.currentTimeMillis()}")
                            Handler(Looper.getMainLooper()).postDelayed(Runnable {
                                lifecycleScope.launch {
                                    try {
                                        var results = mutableListOf<String>()
                                        var retryCount = 0 // Initialize the retry counter

                                        // Retry loop for detecting and decoding QR code
                                        while (retryCount < 4) {
                                            results =
                                                WeChatQRCodeDetector.detectAndDecode(
                                                    imgBitmap,
                                                    points
                                                )
                                            Log.e(TAG, "result: $results")

                                            // Break out of the loop if results are not empty
                                            if (!results.isNullOrEmpty()) {
                                                break
                                            }

                                            retryCount++ // Increment the retry count if results are empty or null
                                            Log.e(TAG, "Retry attempt: $retryCount")
                                        }

                                        if (results.isEmpty() && retryCount >= 4) {
                                            // If retries exhausted, enable the button and show a message
                                            binding.captureButton.isEnabled = true
                                            binding.captureButton.text = "Capture"
                                            binding.detectProgressView.isVisible = false
                                            createCustomDialog(
                                                getString(R.string.scan_qr_not_found),
                                                null,
                                                "Capture",
                                                "Cancel"
                                            )
                                        } else {
                                            closeCamera()
                                            points.forEach { mat ->
                                                // Scan result points (rectangle of QR code)
                                                Log.d(
                                                    TAG,
                                                    "point0: ${mat[0, 0][0]}, ${mat[0, 1][0]}"
                                                )
                                                Log.d(
                                                    TAG,
                                                    "point1: ${mat[1, 0][0]}, ${mat[1, 1][0]}"
                                                )
                                                Log.d(
                                                    TAG,
                                                    "point2: ${mat[2, 0][0]}, ${mat[2, 1][0]}"
                                                )
                                                Log.d(
                                                    TAG,
                                                    "point3: ${mat[3, 0][0]}, ${mat[3, 1][0]}"
                                                )
                                                x1 = mat[0, 0][0].toInt()
                                                x2 = mat[1, 0][0].toInt()
                                                x3 = mat[2, 0][0].toInt()
                                                x4 = mat[3, 0][0].toInt()

                                                y1 = mat[0, 1][0].toInt()
                                                y2 = mat[1, 1][0].toInt()
                                                y3 = mat[2, 1][0].toInt()
                                                y4 = mat[3, 1][0].toInt()
                                            }
                                            Log.e(
                                                TIMER_TAG,
                                                "qr detected: ${System.currentTimeMillis()}"
                                            )

//                                        val imagePath = saveBitmapToFile(imgBitmap)
                                            val rotate =
                                                if (imgBitmap.width > imgBitmap.height) 270f else if (qrAtBottom) 180f else 0f
                                            val imagePath = saveBitmapFast(
                                                bitmap = imgBitmap,
                                                outputDir = outputAppDirectory,
                                                saveOriginalAlso = true,                   // true only if you really need both
                                                rotateDegrees = rotate,
                                                format = Bitmap.CompressFormat.JPEG,        // ← fastest common choice
                                                quality = 100
                                            )
                                            if (imagePath != "") {
                                                previewPahth = imagePath
                                                performImageUpload(imagePath)
                                            } else {
                                                runOnUiThread {
                                                    binding.captureButton.isEnabled = true
                                                    binding.captureButton.text = "Capture"
                                                    binding.detectProgressView.isVisible = false
                                                    createCustomDialog(
                                                        getString(R.string.scan_image_save_failed),
                                                        null,
                                                        "Capture",
                                                        "Cancel"
                                                    )
                                                }
                                            }
                                        }
                                    } catch (e: RuntimeException) {
                                        e.printStackTrace()
                                        runOnUiThread {
                                            binding.captureButton.isEnabled = true
                                            binding.captureButton.text = "Capture"
                                            binding.detectProgressView.isVisible = false
                                            createCustomDialog(
                                                getString(R.string.scan_qr_not_found),
                                                null,
                                                "Capture",
                                                "Cancel"
                                            )
                                        }
                                    }
                                }


                            }, 500)
                        } ?: run {
                            Log.e(TAG, "image bitmap is null")
                            runOnUiThread {
                                createCustomDialog(
                                    getString(R.string.scan_image_prepare_failed),
                                    null,
                                    "Capture",
                                    "Cancel"
                                )
                            }

                        }
                    } else {
                        runOnUiThread {
                            capturingImage = false
                            createCustomDialog(
                                getString(R.string.scan_image_prepare_failed),
                                null,
                                "Capture",
                                "Cancel"
                            )
                        }
                    }
                }
            }
        )
    }

    private fun showCaptureUi() {
        binding.captureButton.isEnabled = true
        binding.captureButton.text = "Capture"
        binding.detectProgressView.isVisible = false
    }

    private fun performImageUpload(imagePath: String) {
        lifecycleScope.launch {
            Log.d(TIMER_TAG, "Uploading image: $imagePath , to $selectedValue")
            if (selectedValue == "Mission") {
                viewModel.uploadImageMission(imagePath)
            } else {
                viewModel.uploadImage(imagePath)
            }
        }
    }


    private fun createBitmapFromPath(imagePath: String): Bitmap? {
        return try {
            val bitmap = BitmapFactory.decodeFile(imagePath)
            if (bitmap != null) {
                Log.d(TAG, "Bitmap created from path: $imagePath")
            } else {
                Log.e(TAG, "Failed to create Bitmap from path: $imagePath")
            }
            bitmap
        } catch (e: Exception) {
            Log.e(TAG, "Error creating bitmap from path: ${e.message}")
            null
        }
    }

    @OptIn(ExperimentalGetImage::class)
    private fun requestPermissions() {
        activityResultLauncher.launch(REQUIRED_PERMISSIONS)
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(
            baseContext, it
        ) == PackageManager.PERMISSION_GRANTED
    }

    private val outputDirectory: File
        get() {
            val mediaDir = externalMediaDirs.firstOrNull()?.let {
                File(it, resources.getString(R.string.app_name)).apply { mkdirs() }
            }
            return if (mediaDir != null && mediaDir.exists())
                mediaDir else filesDir
        }

    private val outputAppDirectory: File
        get() {
            val mediaDir = externalMediaDirs.firstOrNull()?.let { externalDir ->
                val appDir =
                    File(externalDir, resources.getString(R.string.app_name)).apply { mkdirs() }
                // Create a subdirectory inside the app-named directory
                File(appDir, folderName).apply { mkdirs() }
            }
            return if (mediaDir != null && mediaDir.exists())
                mediaDir else filesDir
        }

    private val outputLogDirectory: File
        get() {
            val mediaDir = externalMediaDirs.firstOrNull()?.let { externalDir ->
                val appDir =
                    File(externalDir, resources.getString(R.string.app_name)).apply { mkdirs() }
                // Create a subdirectory inside the app-named directory
                File(appDir, logFolderName).apply { mkdirs() }
            }
            return if (mediaDir != null && mediaDir.exists())
                mediaDir else filesDir
        }


    private fun closeCamera() {
        cameraProvider.unbindAll()
    }

    private fun scheduleFlashOn() {
        handler.postDelayed({
            if (!isQrCodeDetected) {
                Log.e(TAG, "flash on")
                camera?.cameraControl?.enableTorch(true) // Turn on the flash
            }
        }, 3000) // 3 seconds
    }

    private fun scheduleZoomIn() {
        handler.postDelayed({
            if (!isQrCodeDetected) {
                Log.e(TAG, "zoom 3x")
                camera?.cameraControl?.setZoomRatio(3.0f) // Zoom in to 3x
            }
        }, 6000) // 6 seconds
    }

    private fun scheduleShowCaptureButton() {
        handler.postDelayed({
            if (!isQrCodeDetected) {
                binding.captureButton.visibility = View.VISIBLE
            }
        }, 20000)
    }

    private fun scheduleShowDialog() {
        handler.postDelayed({
            if (!isQrCodeDetected && alertDialog == null) {
                // Close the camera preview
                closeCamera()
                // Show a dialog
                if (isFinishing.not()) {
                    showRescanQuitDialog()
                }
            }
        }, 300000) // 8 seconds
    }

    @OptIn(ExperimentalGetImage::class)
    private fun showRescanQuitDialog() {
        builder = AlertDialog.Builder(this)
            .setTitle("QR Code Not Detected")
            .setMessage("Would you like to rescan or quit?")
            .setCancelable(false)
            .setPositiveButton("Rescan") { _, _ ->
                // Restart the scanning process
                builder = null
                startCamera()
            }
            .setNegativeButton("Quit") { _, _ ->
                builder = null
                finish() // Close the activity
            }
        builder?.show()
    }

    // Method to create the custom dialog
    @OptIn(ExperimentalGetImage::class)
    fun createCustomDialog(
        title: String,
        description: String?,
        okButtonText: String,
        cancelButtonText: String
    ) {

        if (alertDialog != null) {
            return
        }
        if (builder != null) {
            return
        }
        closeCamera()
        val builder = AlertDialog.Builder(this)
        customLayout = LayoutInflater.from(this).inflate(R.layout.custom_dialog_layout, null)
        builder.setView(customLayout)
        builder.setCancelable(false)

        alertDialog = builder.create()

        // Set up the views inside the custom layout
        val titleView = customLayout.findViewById<TextView>(R.id.dialog_title_tv)
        val descriptionView = customLayout.findViewById<TextView>(R.id.dialog_des_tv)
        val okButton = customLayout.findViewById<MaterialButton>(R.id.ok_button)
        val cancelButton = customLayout.findViewById<MaterialButton>(R.id.cancel_button)

        // Set the title
        Log.e(TAG, "Showing error dialog: $title")
        titleView.text = title

        // Set the description or hide it if it's empty
        if (description.isNullOrEmpty()) {
            descriptionView.visibility = View.GONE
        } else {
            descriptionView.text = description
            descriptionView.visibility = View.VISIBLE
        }

        // Set the button texts
        okButton.text = okButtonText
        cancelButton.text = cancelButtonText

        // Set up button click listeners (you can modify these actions as needed)
        okButton.setOnClickListener {
            // Handle OK button click
            alertDialog?.dismiss() // Dismiss the dialog when OK is clicked
            alertDialog = null
            runOnUiThread {
                binding.captureButton.visibility = View.VISIBLE
                binding.captureButton.isEnabled = true
                binding.captureButton.text = "Capture"
                startCamera()
            }
        }

        cancelButton.setOnClickListener {
            // Handle Cancel button click
//            startUploadWork(capturedImagePath, title)
            Handler(Looper.getMainLooper()).postDelayed({
                alertDialog?.dismiss()
                alertDialog = null
                finish()// Dismiss the dialog when Cancel is clicked
            }, 2000)

        }

        alertDialog?.show()
    }

    private fun startUploadWork(imageUri: String, reason: String) {
        // Prepare input data
        val inputData = Data.Builder()
            .putString("imageUri", imageUri)
            .putString("reason", reason)
            .putString("timestamp", getCurrentDateString())
            .build()

        // Create the work request
        val uploadWorkRequest: WorkRequest = OneTimeWorkRequestBuilder<UploadWorker>()
            .setInputData(inputData)
            .build()

        // Enqueue the work request
        WorkManager.getInstance(this).enqueue(uploadWorkRequest)
    }

    // Method to update the dialog content
    fun updateDialogContent(
        newTitle: String,
        newDescription: String?,
        newOkButtonText: String,
        newCancelButtonText: String
    ) {
        if (::customLayout.isInitialized) {
            val titleView = customLayout.findViewById<TextView>(R.id.dialog_title_tv)
            val descriptionView = customLayout.findViewById<TextView>(R.id.dialog_des_tv)
            val okButton = customLayout.findViewById<MaterialButton>(R.id.ok_button)
            val cancelButton = customLayout.findViewById<MaterialButton>(R.id.cancel_button)

            // Update the title
            titleView.text = newTitle

            // Update the description or hide it if it's empty
            if (newDescription.isNullOrEmpty()) {
                descriptionView.visibility = View.GONE
            } else {
                descriptionView.text = newDescription
                descriptionView.visibility = View.VISIBLE
            }

            // Update the button texts
            okButton.text = newOkButtonText
            cancelButton.text = newCancelButtonText
        }
    }

    override fun onBackPressed() {
        super.onBackPressed()
        finish()
    }

    private fun isInternetAvailable(): Boolean {
        val connectivityManager =
            getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val activeNetwork = connectivityManager.activeNetwork
        val networkCapabilities = connectivityManager.getNetworkCapabilities(activeNetwork)
        return networkCapabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
    }

    private fun showNoInternetDialog() {
        val dialogBuilder = AlertDialog.Builder(this)
            .setTitle("No Internet Connection")
            .setMessage("Internet is not available. Please check your connection and try again.")
            .setCancelable(false)
            .setPositiveButton("Retry") { dialog, _ ->
                dialog.dismiss()
                // Optionally, you can trigger a retry action here or observe the network state again
                if (!isInternetAvailable()) {
                    // If internet is still not connected, show the dialog again
                    showNoInternetDialog()
                }
            }.setNegativeButton("Close App") { dialog, _ ->
                dialog.dismiss()
                finish()
            }

        val alertDialog = dialogBuilder.create()
        alertDialog.show()
    }
}
