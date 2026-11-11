package com.billiontests.app.home.ui.profile

import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity.MODE_PRIVATE
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.RecyclerView
import com.billiontests.app.MainActivity
import com.billiontests.app.R
import com.billiontests.app.database.ScanDatabase
import com.billiontests.app.databinding.FragmentProfileBinding
import com.billiontests.app.model.Patient
import com.billiontests.app.utils.PreferenceUtils
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

class ProfileFragment : Fragment(), ProfileAdapter.OnProfileItemClickListener {

    companion object {
        fun newInstance() = ProfileFragment()
        private const val TAG = "ProfileFragment"
    }

    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var scanDatabase: ScanDatabase
    private val existingPatients = mutableListOf<Patient>()

    private val viewModel: ProfileViewModel by viewModels()
    private val profileList = listOf(
        "Edit Profile",
        "Add patient",
        "Write a review",
        "Privacy Policy",
        "Terms Of Use",
        "FAQ",
        "About",
        "Logout",
    )
    val stripType = listOf("Mission", "Roche")
    val stripTypeUi = listOf("10 pads", "11 pads")

    private var selectedValue: String = ""
    private lateinit var recyclerView: RecyclerView
    private lateinit var profileAdapter: ProfileAdapter
    private lateinit var binding: FragmentProfileBinding

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentProfileBinding.inflate(inflater)
        sharedPreferences =
            requireContext().getSharedPreferences(PreferenceUtils.PREFERENCE_NAME, MODE_PRIVATE)

        scanDatabase = ScanDatabase.getDatabase(requireContext())
        fetchPatientsFromDatabase()

        val name = sharedPreferences.getString(PreferenceUtils.USER_NAME, "") ?: ""
        val age = sharedPreferences.getString(PreferenceUtils.AGE, "") ?: ""
        val gender = sharedPreferences.getString(PreferenceUtils.GENDER, "") ?: ""

        val lastTestTaken = sharedPreferences.getString(PreferenceUtils.LAST_TEST_TIME, "") ?: ""
        if (lastTestTaken.isNotEmpty()) {
            binding.lastTestTakenTv.text = lastTestTaken
        }
        if (name.isEmpty().not()) {
            binding.profileUserNameTv.text = "Hello\n$name"
        }
        if (age.isNotEmpty()) {
            binding.ageTv.text =
                age.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() } + "Yr"
        }

        if (gender.isNotEmpty()) {
            binding.genderTv.text = gender.replaceFirstChar {
                if (it.isLowerCase()) it.titlecase(
                    Locale.getDefault()
                ) else it.toString()
            }
        }
        binding.appVersionTv.text = getString(R.string.version_format, getAppVersionName())

        recyclerView = binding.profileRecyclerView

        profileAdapter = ProfileAdapter(profileList, this)
        recyclerView.adapter = profileAdapter

        binding.takeTestNowButton.setOnClickListener {
            findNavController().navigate(
                ProfileFragmentDirections.actionNavigationProfileToTestGuideFragment2(
                    null
                )
            )
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

    override fun onProfileItemClick(item: String, position: Int) {
        when (position) {
            0 -> {
                findNavController().navigate(
                    ProfileFragmentDirections.actionNavigationProfileToSignUpFragment2(
                        true
                    )
                )
            }

            1 -> {
                showPatientDialog(existingPatients, { selectedPatient ->
                    selectedPatient?.let {
                        Log.d(TAG, "Selected patient: $selectedPatient")
                        findNavController().navigate(
                            ProfileFragmentDirections.actionNavigationProfileToTestGuideFragment2(
                                selectedPatient
                            )
                        )
                    }
                })
            }

            3, 4 -> {
                var url = ""
                url = if (position == 3) {
                    "https://www.freeprivacypolicy.com/live/5033be46-bb8c-4cac-9fa2-ef4c109302d2"
                } else {
                    "https://www.freeprivacypolicy.com/live/fcb86250-aac4-4241-9da9-8b1b228cb49f"
                }
                findNavController().navigate(
                    ProfileFragmentDirections.actionNavigationProfileToWebViewFragment(
                        url
                    )
                )
            }

            6 -> {
                findNavController().navigate(ProfileFragmentDirections.actionNavigationProfileToAboutFragment())
            }

            7 -> {
                showLogoutDialog()
            }

            else -> {
                Toast.makeText(requireContext(), "Coming Soon", Toast.LENGTH_SHORT).show()
            }
        }

    }

    private fun getAppVersionName(): String {
        return try {
            val packageInfo =
                requireContext().packageManager.getPackageInfo(requireContext().packageName, 0)
            packageInfo.versionName ?: ""
        } catch (e: PackageManager.NameNotFoundException) {
            ""
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

//    private fun showPatientDialog(
//        existingPatients: List<Patient>,
//        onPatientSelected: (Patient) -> Unit
//    ) {
//        val dialogView = layoutInflater.inflate(R.layout.dialog_patient_details, null)
//
//        val patientSpinner = dialogView.findViewById<android.widget.Spinner>(R.id.patientSpinner)
//        val newPatientContainer = dialogView.findViewById<View>(R.id.newPatientContainer)
//
//        val nameInputLayout = dialogView.findViewById<TextInputLayout>(R.id.nameInputLayout)
//        val mobileInputLayout = dialogView.findViewById<TextInputLayout>(R.id.mobileInputLayout)
//        val ageInputLayout = dialogView.findViewById<TextInputLayout>(R.id.ageInputLayout)
//        val referredByInputLayout =
//            dialogView.findViewById<TextInputLayout>(R.id.referredByInputLayout)
//
//        val nameEt =
//            dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.nameEt)
//        val mobileEt =
//            dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.mobileEt)
//        val ageEt =
//            dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.ageEt)
//        val referredByEt =
//            dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.referredByEt)
//
//        val genderRadioGroup =
//            dialogView.findViewById<android.widget.RadioGroup>(R.id.genderRadioGroup)
//        val cancelButton =
//            dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.cancelButton)
//        val continueButton =
//            dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.continueButton)
//
//        val patientOptions = mutableListOf("Create New Patient")
//        patientOptions.addAll(existingPatients.map { it.name })
//
//        val patientAdapter = ArrayAdapter(
//            requireContext(),
//            android.R.layout.simple_spinner_item,
//            patientOptions
//        )
//        patientAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
//        patientSpinner.adapter = patientAdapter
//
//        fun clearErrors() {
//            nameInputLayout.error = null
//            mobileInputLayout.error = null
//            ageInputLayout.error = null
//            referredByInputLayout.error = null
//        }
//
//        fun updateNewPatientFormVisibility(position: Int) {
//            newPatientContainer.visibility = if (position == 0) {
//                View.VISIBLE
//            } else {
//                View.GONE
//            }
//        }
//
//        patientSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
//            override fun onItemSelected(
//                parent: AdapterView<*>,
//                view: View?,
//                position: Int,
//                id: Long
//            ) {
//                clearErrors()
//                updateNewPatientFormVisibility(position)
//            }
//
//            override fun onNothingSelected(parent: AdapterView<*>) = Unit
//        }
//
//        val dialog = AlertDialog.Builder(requireContext())
//            .setView(dialogView)
//            .create()
//
//        cancelButton.setOnClickListener {
//            dialog.dismiss()
//        }
//
//        continueButton.setOnClickListener {
//            clearErrors()
//
//            val selectedPosition = patientSpinner.selectedItemPosition
//
//            if (selectedPosition > 0) {
//                val selectedPatient = existingPatients[selectedPosition - 1]
//                onPatientSelected(selectedPatient)
//                dialog.dismiss()
//                return@setOnClickListener
//            }
//
//            val patientName = nameEt.text.toString().trim()
//            val mobileNo = mobileEt.text.toString().trim()
//            val ageText = ageEt.text.toString().trim()
//            val referredBy = referredByEt.text.toString().trim()
//
//            val selectedGenderId = genderRadioGroup.checkedRadioButtonId
//            val selectedGender = dialogView.findViewById<RadioButton>(selectedGenderId)
//                ?.text
//                ?.toString()
//                .orEmpty()
//
//            if (patientName.isEmpty()) {
//                nameInputLayout.error = "Enter patient name"
//                return@setOnClickListener
//            }
//
//            if (mobileNo.isEmpty()) {
//                mobileInputLayout.error = "Enter mobile no"
//                return@setOnClickListener
//            }
//
//            if (mobileNo.length < 10) {
//                mobileInputLayout.error = "Enter valid mobile no"
//                return@setOnClickListener
//            }
//
//            if (ageText.isEmpty()) {
//                ageInputLayout.error = "Enter age"
//                return@setOnClickListener
//            }
//
//            val age = ageText.toIntOrNull()
//            if (age == null || age <= 0) {
//                ageInputLayout.error = "Enter valid age"
//                return@setOnClickListener
//            }
//
//            if (selectedGender.isEmpty()) {
//                Toast.makeText(requireContext(), "Select gender", Toast.LENGTH_SHORT).show()
//                return@setOnClickListener
//            }
//
//            if (referredBy.isEmpty()) {
//                referredByInputLayout.error = "Enter referred by"
//                return@setOnClickListener
//            }
//
//            val newPatient = Patient(
//                name = patientName,
//                mobileNo = mobileNo,
//                gender = selectedGender,
//                age = age,
//                referredBy = referredBy
//            )
//
//
//            lifecycleScope.launch {
//                try {
//                    withContext(Dispatchers.IO) {
//                        scanDatabase.patientDao().insertPatient(newPatient)
//                    }
//
//                    val existingIndex = this@ProfileFragment.existingPatients.indexOfFirst {
//                        it.mobileNo == newPatient.mobileNo
//                    }
//
//                    if (existingIndex >= 0) {
//                        this@ProfileFragment.existingPatients[existingIndex] = newPatient
//                    } else {
//                        this@ProfileFragment.existingPatients.add(newPatient)
//                    }
//
//                    Log.d(TAG, "Patient saved: $newPatient")
//                    onPatientSelected(newPatient)
//                    dialog.dismiss()
//                } catch (e: Exception) {
//                    Log.e(TAG, "Failed to save patient", e)
//                    Toast.makeText(
//                        requireContext(),
//                        "Failed to save patient. Please try again.",
//                        Toast.LENGTH_SHORT
//                    ).show()
//                }
//            }
////            onPatientSelected(newPatient)
////            dialog.dismiss()
//        }
//
//        dialog.show()
//    }

    private fun showPatientDialog(
        existingPatients: List<Patient>,
        onPatientSelected: (Patient?) -> Unit
    ) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_patient_details, null)

        val patientDropdown = dialogView.findViewById<AutoCompleteTextView>(R.id.patientDropdown)
        val patientMenuLayout = dialogView.findViewById<TextInputLayout>(R.id.patientMenuLayout)
        val newPatientContainer = dialogView.findViewById<View>(R.id.newPatientContainer)

        val nameInputLayout = dialogView.findViewById<TextInputLayout>(R.id.nameInputLayout)
        val mobileInputLayout = dialogView.findViewById<TextInputLayout>(R.id.mobileInputLayout)
        val ageInputLayout = dialogView.findViewById<TextInputLayout>(R.id.ageInputLayout)
        val referredByInputLayout =
            dialogView.findViewById<TextInputLayout>(R.id.referredByInputLayout)

        val nameEt = dialogView.findViewById<TextInputEditText>(R.id.nameEt)
        val mobileEt = dialogView.findViewById<TextInputEditText>(R.id.mobileEt)
        val ageEt = dialogView.findViewById<TextInputEditText>(R.id.ageEt)
        val referredByEt = dialogView.findViewById<TextInputEditText>(R.id.referredByEt)

        val genderChipGroup = dialogView.findViewById<ChipGroup>(R.id.genderChipGroup)
        val cancelButton = dialogView.findViewById<MaterialButton>(R.id.cancelButton)
        val continueButton = dialogView.findViewById<MaterialButton>(R.id.continueButton)

        val patientOptions = mutableListOf("＋  Create New Patient")
        patientOptions.addAll(existingPatients.map { "${it.name}  ·  ${it.mobileNo}" })

        var selectedPosition = 0

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
                if (selectedPosition == 0) View.VISIBLE else View.GONE
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

        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        cancelButton.setOnClickListener {
            dialog.dismiss()
        }

        continueButton.setOnClickListener {
            clearErrors()

            if (selectedPosition > 0) {
                onPatientSelected(existingPatients[selectedPosition - 1])
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
                nameEt.requestFocus()
                return@setOnClickListener
            }

            if (ageText.isEmpty()) {
                ageInputLayout.error = "Enter age"
                ageEt.requestFocus()
                return@setOnClickListener
            }

            val age = ageText.toIntOrNull()
            if (age == null || age <= 0) {
                ageInputLayout.error = "Enter valid age"
                ageEt.requestFocus()
                return@setOnClickListener
            }

            if (selectedGender.isEmpty()) {
                Toast.makeText(requireContext(), "Select gender", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (referredBy.isEmpty()) {
                referredByInputLayout.error = "Enter referred by"
                referredByEt.requestFocus()
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

                    val existingIndex = this@ProfileFragment.existingPatients.indexOfFirst {
                        it.mobileNo == newPatient.mobileNo
                    }

                    if (existingIndex >= 0) {
                        this@ProfileFragment.existingPatients[existingIndex] = newPatient
                    } else {
                        this@ProfileFragment.existingPatients.add(newPatient)
                    }

                    Log.d(TAG, "Patient saved: $newPatient")
                    onPatientSelected(null)
                    Toast.makeText(
                        requireContext(),
                        "Patient saved successfully",
                        Toast.LENGTH_SHORT
                    ).show()
                    dialog.dismiss()
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to save patient", e)
                    continueButton.isEnabled = true
                    Toast.makeText(
                        requireContext(),
                        "Failed to save patient. Please try again.",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }

        dialog.show()
    }

    private fun showLogoutDialog() {
        // Build the AlertDialog
        val builder = AlertDialog.Builder(requireContext())
        builder.setTitle("Logout")
        builder.setMessage("Are you sure you want to continue?")

        // Add the Yes button
        builder.setPositiveButton("Yes") { dialog, _ ->
            // Handle the Yes click
            dialog.dismiss()
            Firebase.auth.signOut()
            startActivity(Intent(requireActivity(), MainActivity::class.java))
            requireActivity().finish()
        }

        // Add the No button
        builder.setNegativeButton("No") { dialog, _ ->
            // Handle the No click
            dialog.dismiss()
        }

        // Create and show the AlertDialog
        val dialog: AlertDialog = builder.create()
        dialog.show()
    }
}