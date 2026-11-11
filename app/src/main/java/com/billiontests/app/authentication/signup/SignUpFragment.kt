package com.billiontests.app.authentication.signup

import android.app.DatePickerDialog
import android.content.Intent
import androidx.fragment.app.viewModels
import android.os.Bundle
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.RadioButton
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity.MODE_PRIVATE
import com.billiontests.app.R
import com.billiontests.app.databinding.FragmentSignUpBinding
import com.billiontests.app.home.BottomHomeActivity
import com.billiontests.app.model.User
import com.billiontests.app.utils.PreferenceUtils
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import java.util.Calendar

class SignUpFragment : Fragment() {

    companion object {
        fun newInstance() = SignUpFragment()
        private const val TAG = "SignUpFragment"
    }

    private lateinit var binding: FragmentSignUpBinding
    private val viewModel: SignUpViewModel by viewModels()
    private val db = FirebaseFirestore.getInstance()

    private var selectedDOB: String? = null
    private var selectedAge: Int = 0
    private var selectedGender: String = "Male"
    private var isProfile = false

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {

        binding = FragmentSignUpBinding.inflate(inflater, container, false)

        isProfile = requireArguments().getBoolean("isProfile", false)

        if (isProfile) {
            binding.profileHeadTv.text = "Update Your\nProfile"
            val user = FirebaseAuth.getInstance().currentUser
            user?.let {
                binding.btnSubmit.visibility = View.GONE
                binding.signupProgressbar.visibility = View.VISIBLE
                getUserData(user)
            }
        }
        binding.btnSelectDOB.setOnClickListener {
            showDatePickerDialog()
        }

        // Handle Submit Button click
        binding.btnSubmit.setOnClickListener {
            val name = binding.textInputName.text.toString()
            if (name.isEmpty()) {
                Toast.makeText(requireContext(), "Enter Name", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val email = binding.textInputEmail.text.toString()
            if (email.isEmpty()) {
                Toast.makeText(requireContext(), "Enter Email", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                Toast.makeText(requireContext(), "Enter Valid Email", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            selectedGender = getSelectedGender()
            if (selectedGender.isEmpty()) {
                Toast.makeText(requireContext(), "Select Gender", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (selectedDOB.isNullOrEmpty()) {
                Toast.makeText(requireContext(), "Select Date of Birth", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (selectedAge < 1) {
                Toast.makeText(requireContext(), "Age must be 1 year", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            saveUserData(name,email, selectedDOB!!, selectedAge!!, selectedGender!!)
            // Now you can submit name, selectedDOB, selectedAge, and selectedGender to Firebase
        }

        // Prefill email for email/password sign-ups so the user doesn't type it twice.
        // Phone users have no account email, so this is a no-op for them.
        if (!isProfile && binding.textInputEmail.text.isNullOrEmpty()) {
            FirebaseAuth.getInstance().currentUser?.email?.let { accountEmail ->
                if (accountEmail.isNotEmpty()) {
                    binding.textInputEmail.setText(accountEmail)
                }
            }
        }

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

//        saveUserData("harsha","28/03/1996",28,"male")
    }

    private fun saveUserData(name: String, email: String, dob: String, age: Int, gender: String) {

        binding.btnSubmit.visibility = View.GONE
        binding.signupProgressbar.visibility = View.VISIBLE
        val sharedPreferences =
            requireContext().getSharedPreferences(PreferenceUtils.PREFERENCE_NAME, MODE_PRIVATE)

        val phoneNumber = sharedPreferences.getString(PreferenceUtils.PHONE_NUMBER, "") ?: ""
        val user = User(name, dob, age, gender, phoneNumber,email)

        // Get current user's UID from FirebaseAuth
        val currentUser = FirebaseAuth.getInstance().currentUser
        val uid = currentUser?.uid

        if (uid != null) {
            // Add user data to Firestore under the "users" collection with UID as the document key.
            val docRef = db.collection("users").document(uid)
            val writeTask = if (isProfile) {
                // Profile update: merge so the scan counter (and any server-only fields) survive.
                docRef.set(user, SetOptions.merge())
            } else {
                // New sign-up: store profile and initialize the scan counter used by the scan limit.
                val data = hashMapOf(
                    "name" to name,
                    "dob" to dob,
                    "age" to age,
                    "gender" to gender,
                    "phoneNumber" to phoneNumber,
                    "email" to email,
                    "scanCount" to 0L
                )
                docRef.set(data)
            }
            writeTask
                .addOnSuccessListener {
                    // Handle successful write
                    Log.d("Firestore", "User data added successfully")
                    sharedPreferences.edit().putBoolean(PreferenceUtils.IS_SIGNED_UP, true).apply()
                    sharedPreferences.edit().putString(PreferenceUtils.USER_NAME, name).apply()
                    sharedPreferences.edit().putString(PreferenceUtils.AGE, age.toString()).apply()
                    sharedPreferences.edit().putString(PreferenceUtils.GENDER, gender).apply()
                    goToHome()
                }
                .addOnFailureListener { e ->
                    // Handle failure
                    Log.e("Firestore", "Error adding user data", e)
                    sharedPreferences.edit().putBoolean(PreferenceUtils.IS_SIGNED_UP, false).apply()
                    goToHome()
                }
        } else {
            sharedPreferences.edit().putBoolean(PreferenceUtils.IS_SIGNED_UP, false).apply()
            Log.e("Firestore", "User not authenticated")
            goToHome()
        }
    }

    private fun getUserData(user: FirebaseUser?) {
        val sharedPreferences =
            requireContext().getSharedPreferences(PreferenceUtils.PREFERENCE_NAME, MODE_PRIVATE)
        val currentUser = user
        val uid = currentUser?.uid

        if (uid != null) {
            db.collection("users").document(uid)
                .get()
                .addOnSuccessListener { document ->
                    binding.btnSubmit.visibility = View.VISIBLE
                    binding.signupProgressbar.visibility = View.GONE
                    if (document != null) {
                        val user = document.toObject(com.billiontests.app.model.User::class.java)
                        Log.d("Firestore", "User data: $user")
                        if (user != null) {
                            updateUi(user)
                        } else {
                            Toast.makeText(requireContext(),"Failed to Fetch data. Try again later", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        Toast.makeText(requireContext(),"Failed to Fetch data. Try again later", Toast.LENGTH_SHORT).show()
                        Log.d("Firestore", "No such document")
                    }
                }
                .addOnFailureListener { e ->
                    binding.btnSubmit.visibility = View.VISIBLE
                    binding.signupProgressbar.visibility = View.GONE
                    Log.w("Firestore", "Error getting user data", e)
                    Toast.makeText(requireContext(),"Failed to Fetch data. Try again later", Toast.LENGTH_SHORT).show()
                }
        }
    }

    private fun updateUi(user: User) {
        selectedDOB = user.dob
        selectedAge = user.age
        selectedGender = user.gender

        binding.textInputName.setText(user.name)
        binding.dobEditText.setText(user.dob)
        val gender = user.gender
        if (gender.lowercase() == "male") {
            binding.radioMale.isChecked = true
        } else if (gender.lowercase() == "female") {
            binding.radioFemale.isChecked = true
        } else {
            binding.radioOther.isChecked = true
        }
    }

    private fun goToHome() {
        if(isProfile.not()) {
            startActivity(Intent(requireActivity(), BottomHomeActivity::class.java))
            requireActivity().finish()
        }else{
            binding.btnSubmit.visibility = View.VISIBLE
            binding.signupProgressbar.visibility = View.GONE
            Toast.makeText(requireContext(),"Profile Info Updated", Toast.LENGTH_LONG).show()
        }
    }

    private fun showDatePickerDialog() {
        val calendar = Calendar.getInstance()
        val year = calendar.get(Calendar.YEAR)
        val month = calendar.get(Calendar.MONTH)
        val day = calendar.get(Calendar.DAY_OF_MONTH)

        val datePickerDialog = DatePickerDialog(
            requireContext(),
            { _, selectedYear, selectedMonth, selectedDayOfMonth ->
                selectedDOB = "$selectedDayOfMonth/${selectedMonth + 1}/$selectedYear"

                Log.e(TAG, "selected dob: $selectedDOB")
                binding.dobEditText.setText(selectedDOB)
                calculateAge(selectedYear, selectedMonth, selectedDayOfMonth)
            }, year, month, day
        )
        datePickerDialog.show()
    }

    private fun calculateAge(year: Int, month: Int, day: Int) {
        val today = Calendar.getInstance()

        var age = today.get(Calendar.YEAR) - year

        // Adjust for if the birthday hasn't occurred yet this year
        if (today.get(Calendar.MONTH) < month || (today.get(Calendar.MONTH) == month && today.get(
                Calendar.DAY_OF_MONTH
            ) < day)
        ) {
            age--
        }

        selectedAge = age
    }

    private fun getSelectedGender(): String {
        val selectedId = binding.radioGroupGender.checkedRadioButtonId
        val radioButton = view?.findViewById<RadioButton>(selectedId)
        return radioButton?.text.toString()
    }
}