package com.billiontests.app.testguide

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Bundle
import android.os.CountDownTimer
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import com.billiontests.app.R
import com.billiontests.app.database.ScanDatabase
import com.billiontests.app.databinding.FragmentTestGuideBinding
import com.billiontests.app.model.Patient
import com.billiontests.app.scan.CameraXActivity
import com.billiontests.app.utils.PATIENT_SELECTED
import com.billiontests.app.utils.PreferenceUtils
import com.billiontests.app.utils.SELECTED_VALUE
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.textfield.TextInputLayout
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class TestGuideFragment : Fragment() {

    companion object {
        fun newInstance() = TestGuideFragment()
        private const val TAG = "TestGuideFragment"
    }

    private val viewModel: TestGuideViewModel by viewModels()

    private lateinit var binding: FragmentTestGuideBinding

    private val db = FirebaseFirestore.getInstance()
    private lateinit var scanDatabase: ScanDatabase

    private lateinit var player: ExoPlayer

    private var timer: CountDownTimer? = null

    private val existingPatients = mutableListOf<Patient>()
    private var selectedPatient: Patient? = null

    /** The currently shown patient dialog, so repeated taps can't stack duplicates. */
    private var patientDialog: AlertDialog? = null

    /** Guards the scan flow so rapid taps don't fire multiple async gates / dialogs. */
    private var scanFlowInProgress = false

    val stripType = listOf("Mission", "Roche")
    val stripTypeUi = listOf("10 pads", "11 pads")

    private var selectedValue: String = ""
    private var maxScans: Long = 0L
    private var uid: String = ""

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentTestGuideBinding.inflate(inflater)

        scanDatabase = ScanDatabase.getDatabase(requireContext())
        selectedPatient = arguments?.getParcelable("patient")
        maxScans = FirebaseRemoteConfig.getInstance().getLong("max_scans")
        uid = FirebaseAuth.getInstance().currentUser?.uid ?: ""
        fetchPatientsFromDatabase()

        binding.playAgainButton.setOnClickListener {
            if (::player.isInitialized) {
                player.seekTo(0)
                // Prepare the player.
                player.prepare()

                player.play()
            }
        }
        binding.startScanButton.setOnClickListener {
            if (scanFlowInProgress) return@setOnClickListener
            scanFlowInProgress = true

            binding.startScanButton.visibility = View.GONE
            binding.scanProgressBar.visibility = View.VISIBLE
            binding.startScanButton.isEnabled = false

            if (::player.isInitialized) {
                player.release()
            }

            goToScan()
        }

        binding.startForceScanButton.setOnClickListener {
            showForceScanDialog()
        }

        val adapter = ArrayAdapter(
            requireContext(),
            R.layout.strip_list_item,
            stripTypeUi
        )
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinner.adapter = adapter

        selectedValue =
            PreferenceUtils.getString(requireContext(), PreferenceUtils.SELECTED_ROACH, stripType[0])

        Log.e(TAG, "Selected: $selectedValue")
        binding.textViewStripLbl.text = stripTypeUi[stripType.indexOf(selectedValue)] + " Strip"
        binding.spinner.setSelection(stripType.indexOf(selectedValue))

        binding.spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: AdapterView<*>,
                view: View?,
                position: Int,
                id: Long
            ) {
                selectedValue = stripType[position]
                Log.e("TAG", "Selected: $selectedValue")
                PreferenceUtils.putString(
                    requireContext(),
                    PreferenceUtils.SELECTED_ROACH,
                    selectedValue
                )
            }

            override fun onNothingSelected(parent: AdapterView<*>) {}
        }

        return binding.root
    }

    private fun showForceScanDialog() {
        val builder = AlertDialog.Builder(requireContext())
            .setTitle("Timer is still Running!")
            .setMessage("Please wait to get accurate test result")
            .setCancelable(false)
            .setPositiveButton("Start Now") { _, _ ->
                // Restart the scanning process
                if (timer != null) {
                    timer?.cancel()
                    timer = null
                }
                goToScan()
            }
            .setNegativeButton("Wait") { dialog, _ ->
                // Close the activity
                dialog.dismiss()
            }
        builder?.show()
    }


    private fun showPatientDialog(
        existingPatients: List<Patient>,
        onPatientSelected: (Patient) -> Unit
    ) {
        // Don't stack a second dialog if one is already open (rapid taps).
        if (patientDialog?.isShowing == true) return

        val dialogView = layoutInflater.inflate(R.layout.dialog_patient_details, null)

        val newPatientContainer = dialogView.findViewById<View>(R.id.newPatientContainer)

        val nameInputLayout = dialogView.findViewById<TextInputLayout>(R.id.nameInputLayout)
        val mobileInputLayout = dialogView.findViewById<TextInputLayout>(R.id.mobileInputLayout)
        val ageInputLayout = dialogView.findViewById<TextInputLayout>(R.id.ageInputLayout)
        val referredByInputLayout =
            dialogView.findViewById<TextInputLayout>(R.id.referredByInputLayout)

        val nameEt =
            dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.nameEt)
        val mobileEt =
            dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.mobileEt)
        val ageEt =
            dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.ageEt)
        val referredByEt =
            dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.referredByEt)

        val cancelButton =
            dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.cancelButton)
        val continueButton =
            dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.continueButton)

        val patientDropdown = dialogView.findViewById<AutoCompleteTextView>(R.id.patientDropdown)
        val patientMenuLayout = dialogView.findViewById<TextInputLayout>(R.id.patientMenuLayout)
        val genderChipGroup = dialogView.findViewById<ChipGroup>(R.id.genderChipGroup)

        val createNewLabel = "＋  Create New Patient"
        val patientOptions = mutableListOf<String>()
        patientOptions.addAll(existingPatients.map { "${it.name}  ·  ${it.mobileNo}" })
        patientOptions.add(createNewLabel)

        val createNewIndex = patientOptions.size - 1
        var selectedPosition = if (existingPatients.isEmpty()) createNewIndex else 0

        patientDropdown.setAdapter(
            ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, patientOptions)
        )
        patientDropdown.setText(patientOptions[selectedPosition], false)

        fun clearErrors() {
            nameInputLayout.error = null
            mobileInputLayout.error = null
            ageInputLayout.error = null
            referredByInputLayout.error = null
            patientMenuLayout.error = null
        }

        fun updateNewPatientFormVisibility() {
            newPatientContainer.visibility =
                if (selectedPosition == createNewIndex) View.VISIBLE else View.GONE
        }

        updateNewPatientFormVisibility()

        patientDropdown.setOnItemClickListener { _, _, position, _ ->
            selectedPosition = position
            clearErrors()
            updateNewPatientFormVisibility()
        }


        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .create()

        patientDialog = dialog
        dialog.setOnDismissListener {
            patientDialog = null
            scanFlowInProgress = false
        }

        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        cancelButton.setOnClickListener {
            dialog.dismiss()
        }

        continueButton.setOnClickListener {
            clearErrors()


            if (selectedPosition != createNewIndex) {
                onPatientSelected(existingPatients[selectedPosition])
                dialog.dismiss()
                return@setOnClickListener
            }

            val patientName = nameEt.text.toString().trim()
            val mobileNo = mobileEt.text.toString().trim()
            val ageText = ageEt.text.toString().trim()
            val referredBy = referredByEt.text.toString().trim()

            val selectedGender = dialogView.findViewById<Chip>(genderChipGroup.checkedChipId)
                ?.text
                ?.toString()
                .orEmpty()

            if (patientName.isEmpty()) {
                nameInputLayout.error = "Enter patient name"
                return@setOnClickListener
            }


            if (ageText.isEmpty()) {
                ageInputLayout.error = "Enter age"
                return@setOnClickListener
            }

            val age = ageText.toIntOrNull()
            if (age == null || age <= 0) {
                ageInputLayout.error = "Enter valid age"
                return@setOnClickListener
            }

            if (selectedGender.isEmpty()) {
                Toast.makeText(requireContext(), "Select gender", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (referredBy.isEmpty()) {
                referredByInputLayout.error = "Enter referred by"
                return@setOnClickListener
            }

            val newPatient = Patient(
                name = patientName,
                mobileNo = mobileNo,
                gender = selectedGender,
                age = age,
                referredBy = referredBy
            )
            continueButton.isEnabled = false

            lifecycleScope.launch {
                try {
                    withContext(Dispatchers.IO) {
                        scanDatabase.patientDao().insertPatient(newPatient)
                    }

                    val existingIndex = this@TestGuideFragment.existingPatients.indexOfFirst {
                        it.mobileNo == newPatient.mobileNo
                    }

                    if (existingIndex >= 0) {
                        this@TestGuideFragment.existingPatients[existingIndex] = newPatient
                    } else {
                        this@TestGuideFragment.existingPatients.add(newPatient)
                    }

                    Log.d(TAG, "Patient saved: $newPatient")
                    onPatientSelected(newPatient)
                    dialog.dismiss()
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to save patient", e)
                    Toast.makeText(
                        requireContext(),
                        "Failed to save patient. Please try again.",
                        Toast.LENGTH_SHORT
                    ).show()
                    continueButton.isEnabled = true
                }
            }
//            onPatientSelected(newPatient)
//            dialog.dismiss()
        }

        // The "Create New Patient" form (newPatientContainer) is GONE until the user picks it from
        // the dropdown, so the dialog is short at show-time and only overflows LATER. A one-shot
        // measure can't catch that. Instead, on every layout pass, bound the ScrollView to the
        // visible area (screen - system bars - the card's 20dp margins) whenever its content is
        // taller than that; otherwise let it wrap. That makes it scroll the moment the form expands.
        val scrollView = dialogView.findViewById<View>(R.id.dialogScroll)
        scrollView.viewTreeObserver.addOnGlobalLayoutListener {
            val density = resources.displayMetrics.density
            val screenH = resources.displayMetrics.heightPixels
            val bars = ViewCompat.getRootWindowInsets(scrollView)
                ?.getInsets(WindowInsetsCompat.Type.systemBars())
            val margins = (40 * density).toInt() // 20dp top + 20dp bottom on the card
            val target = screenH - (bars?.top ?: 0) - (bars?.bottom ?: 0) - margins

            val contentHeight = (scrollView as android.widget.ScrollView)
                .getChildAt(0)?.height ?: 0

            val desired = if (contentHeight > target && target > 0) {
                target
            } else {
                ViewGroup.LayoutParams.WRAP_CONTENT
            }
            // Only write when it actually changes, so we don't loop on our own relayout.
            if (scrollView.layoutParams.height != desired) {
                scrollView.layoutParams = scrollView.layoutParams.apply { height = desired }
            }
        }

        dialog.show()
    }

    override fun onResume() {
        super.onResume()
        try {
            val rawVideoUri =
                Uri.parse("android.resource://" + "com.billiontests.app" + "/" + R.raw.how_to_use)

            val mediaItem = MediaItem.fromUri(rawVideoUri)
            player = ExoPlayer.Builder(requireContext()).build()
            binding.playerView.player = player
            player.setMediaItem(mediaItem)
            player.prepare()

            player.play()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun fetchPatientsFromDatabase() {
        lifecycleScope.launch {
            val patients = withContext(Dispatchers.IO) {
                scanDatabase.patientDao().getAllPatients()
            }

            existingPatients.clear()
            existingPatients.addAll(patients)

            Log.d(TAG, "Patients fetched from DB: ${existingPatients.size}")
        }
    }

    private fun goToScan() {
        // Scan limit gate: block only when we positively know the user is over the
        // Remote Config limit. Any missing config / unauthenticated / read failure
        // falls through to the normal scan flow so existing behavior is preserved.

        if (maxScans <= 0L || uid.isEmpty()) {
            launchScan()
            return
        }

        // Progress bar stays visible (set on the button click) through this async read; the
        // terminal points below restore the idle UI once the flow resolves.
        db.collection("users").document(uid).get()
            .addOnSuccessListener { document ->
                if (!isAdded) return@addOnSuccessListener
                val scanCount = document.getLong("scanCount") ?: 0L
                if (scanCount >= maxScans) {
                    showScanLimitReachedDialog()
                } else {
                    launchScan()
                }
            }
            .addOnFailureListener {
                // Fail open: never block a scan because the limit could not be read.
                if (isAdded) launchScan()
            }
    }

    /** Hide the in-flight progress bar and restore the Start button to its idle state. */
    private fun setScanIdleUi() {
        binding.scanProgressBar.visibility = View.GONE
        binding.startScanButton.visibility = View.VISIBLE
        binding.startScanButton.isEnabled = true
    }

    private fun showScanLimitReachedDialog() {
        scanFlowInProgress = false
        setScanIdleUi()
        AlertDialog.Builder(requireContext())
            .setTitle("Scan limit reached")
            .setMessage("You have used all your available scans.")
            .setCancelable(true)
            .setPositiveButton("OK") { dialog, _ -> dialog.dismiss() }
            .show()
    }

    private fun launchScan() {
        // Async gate resolved — stop showing the in-flight progress bar.
        setScanIdleUi()

        selectedPatient?.let {
            scanFlowInProgress = false
            startActivity(
                Intent(requireActivity(), CameraXActivity::class.java).putExtra(
                    SELECTED_VALUE, selectedValue
                ).putExtra(PATIENT_SELECTED, selectedPatient)
            )
        } ?: run {
            showPatientDialog(existingPatients) { selectedPatient ->
                Log.d(TAG, "Selected patient: $selectedPatient")
                Toast.makeText(
                    requireContext(),
                    "Selected patient: ${selectedPatient.name}",
                    Toast.LENGTH_SHORT
                ).show()


                binding.playerView.visibility = View.VISIBLE
                binding.progressBar.visibility = View.GONE
                binding.playAgainButton.visibility = View.VISIBLE
                binding.instructionTv.text = "How To Scan"
                binding.startScanButton.isEnabled = true
                binding.startForceScanButton.visibility = View.GONE
                binding.startScanButton.visibility = View.VISIBLE
                startActivity(
                    Intent(requireActivity(), CameraXActivity::class.java).putExtra(
                        SELECTED_VALUE, selectedValue
                    ).putExtra(PATIENT_SELECTED, selectedPatient)
                )
            }
        }
    }

    private fun isInternetAvailable(): Boolean {
        val connectivityManager =
            requireContext().getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val activeNetwork = connectivityManager.activeNetwork
        val networkCapabilities = connectivityManager.getNetworkCapabilities(activeNetwork)
        return networkCapabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
    }

    private fun showNoInternetDialog() {
        val dialogBuilder = AlertDialog.Builder(requireContext())
            .setTitle("No Internet Connection")
            .setMessage("Internet is not available. Please check your connection and try again.")
            .setCancelable(false)
            .setPositiveButton("Retry") { dialog, _ ->
                dialog.dismiss()
                // Optionally, you can trigger a retry action here or observe the network state again
                if (!isInternetAvailable()) {
                    // If internet is still not connected, show the dialog again
                    showNoInternetDialog()
                } else {
                    goToScan()
                }
            }

        val alertDialog = dialogBuilder.create()
        alertDialog.show()
    }

    override fun onPause() {
        super.onPause()
        if (::player.isInitialized) {
            player.release()
        }
    }

    override fun onStop() {
        super.onStop()
        if (::player.isInitialized) {
            player.release()
        }
    }
}