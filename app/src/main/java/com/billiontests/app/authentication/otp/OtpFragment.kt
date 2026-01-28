package com.billiontests.app.authentication.otp

import androidx.fragment.app.viewModels
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.billiontests.app.databinding.FragmentOtpBinding
import com.billiontests.app.home.BottomHomeActivity
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity.MODE_PRIVATE
import androidx.navigation.fragment.findNavController
import com.billiontests.app.utils.PreferenceUtils
import com.google.firebase.FirebaseException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthMissingActivityForRecaptchaException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.auth.User
import com.google.firebase.ktx.Firebase
import java.util.concurrent.TimeUnit

class OtpFragment : Fragment() {

    companion object {
        fun newInstance() = OtpFragment()
        private const val TAG = "OtpVerificationFragment"
    }

    private lateinit var resendToken: PhoneAuthProvider.ForceResendingToken
    private lateinit var storedVerificationId: String
    private lateinit var auth: FirebaseAuth

    private lateinit var phoneNumber: String

    private lateinit var binding: FragmentOtpBinding
    private val viewModel: OtpViewModel by viewModels()
    val db = FirebaseFirestore.getInstance()

    private var enteredOtp: String = ""
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        auth = Firebase.auth
    }


    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentOtpBinding.inflate(inflater)
        binding.continueButton.setOnClickListener {
//            val otp = enteredOtp
            val otp = binding.otpEditText.text.toString().trim()
            if (otp.isEmpty()) {
                Toast.makeText(requireContext(), "Please Enter OTP", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (otp.length < 6) {
                Toast.makeText(requireContext(), "Otp should have 6 digit", Toast.LENGTH_SHORT)
                    .show()
                return@setOnClickListener
            }
            binding.continueButton.visibility = View.INVISIBLE
            binding.progressBar.visibility = View.VISIBLE
            Toast.makeText(requireContext(), "Please wait ...", Toast.LENGTH_LONG).show()
            verifyCode(otp)

        }

//        binding.otpView.setOtpCompletionListener {
//            it?.let {
//                enteredOtp = it
//            }
//        }
        phoneNumber = requireArguments().getString("phone") ?: ""
        if (phoneNumber.isNotEmpty()) {
            binding.phoneNumberTv.text = "+91 $phoneNumber"
            sendVerificationCode(phoneNumber)
        }

        binding.phoneNumberTv.setOnClickListener {
            findNavController().navigate(OtpFragmentDirections.actionOtpFragmentToAuthenticationFragment())
        }

        binding.privacyTv.setOnClickListener {
            val phoneNumber =
                "+917982764035" // Replace with the actual phone number in international format
            val url = "https://api.whatsapp.com/send?phone=$phoneNumber"

            try {
                val intent = Intent(Intent.ACTION_VIEW)
                intent.setPackage("com.whatsapp") // Ensure the intent will only open WhatsApp
                intent.data = Uri.parse(url)
                startActivity(intent)
            } catch (e: Exception) {
                e.printStackTrace()
                // Handle error if WhatsApp is not installed or the number is incorrect
                Toast.makeText(
                    requireContext(),
                    "WhatsApp is not installed on your device",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
        return binding.root
    }

    private fun goToHome() {
        startActivity(Intent(requireActivity(), BottomHomeActivity::class.java))
        requireActivity().finish()
    }

    private fun goToSignUp() {
        findNavController().navigate(OtpFragmentDirections.actionOtpFragmentToSignUpFragment(false))
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
                    sharedPreferences.edit().putString(PreferenceUtils.PHONE_NUMBER, phoneNumber)
                        .apply()
                    if (document != null) {
                        val user = document.toObject(com.billiontests.app.model.User::class.java)
                        Log.d("Firestore", "User data: $user")
                        if (user != null) {
                            sharedPreferences.edit().putBoolean(PreferenceUtils.IS_SIGNED_UP, true)
                                .apply()
                            sharedPreferences.edit().putString(PreferenceUtils.USER_NAME, user.name)
                                .apply()
                            sharedPreferences.edit()
                                .putString(PreferenceUtils.AGE, user.age.toString()).apply()
                            sharedPreferences.edit().putString(PreferenceUtils.GENDER, user.gender)
                                .apply()
                            goToHome()
                        } else {
                            goToSignUp()
                        }
                    } else {
                        Log.d("Firestore", "No such document")
                        goToSignUp()
                    }
                }
                .addOnFailureListener { e ->
                    sharedPreferences.edit().putString(PreferenceUtils.PHONE_NUMBER, phoneNumber)
                        .apply()
                    Log.w("Firestore", "Error getting user data", e)
                    goToSignUp()
                }
        }
    }


    private fun sendVerificationCode(phoneNumber: String) {
        val options = PhoneAuthOptions.newBuilder(auth)
            .setPhoneNumber("+91$phoneNumber") // Phone number to verify
            .setTimeout(60L, TimeUnit.SECONDS) // Timeout and unit
            .setActivity(requireActivity()) // Activity (for callback binding)
            .setCallbacks(callbacks) // OnVerificationStateChangedCallbacks
            .build()
        PhoneAuthProvider.verifyPhoneNumber(options)
    }

    private val callbacks = object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {

        override fun onVerificationCompleted(credential: PhoneAuthCredential) {
            // This callback will be invoked in two situations:
            // 1 - Instant verification. In some cases the phone number can be instantly
            //     verified without needing to send or enter a verification code.
            // 2 - Auto-retrieval. On some devices Google Play services can automatically
            //     detect the incoming verification SMS and perform verification without
            //     user action.
            Log.e(TAG, "onVerificationCompleted:$credential")
            signInWithPhoneAuthCredential(credential)
        }

        override fun onVerificationFailed(e: FirebaseException) {
            // This callback is invoked in an invalid request for verification is made,
            // for instance if the the phone number format is not valid.
            Log.w(TAG, "onVerificationFailed", e)

            when (e) {
                is FirebaseAuthInvalidCredentialsException -> {
                    // Invalid request
                    Log.e(TAG, "firebase credential exception: $e")
                    Toast.makeText(
                        requireContext(),
                        "Invalid Request! Try Again",
                        Toast.LENGTH_SHORT
                    ).show()
                }

                is FirebaseTooManyRequestsException -> {
                    // The SMS quota for the project has been exceeded
                    Log.e(TAG, "Firebase quota exception: $e")
                    Toast.makeText(
                        requireContext(),
                        "SMS Quota Exceeded. Try again Later",
                        Toast.LENGTH_SHORT
                    ).show()
                }

                is FirebaseAuthMissingActivityForRecaptchaException -> {
                    // reCAPTCHA verification attempted with null Activity
                    Log.e(TAG, "Firebase verification exception: $e")
                    Toast.makeText(
                        requireContext(),
                        "Verification Failed! Try Again",
                        Toast.LENGTH_SHORT
                    ).show()
                }

                else -> {
                    Log.e(TAG, "Firebase verification exception: $e")
                    Toast.makeText(
                        requireContext(),
                        "Verification Failed! Try Again",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }

            findNavController().navigate(OtpFragmentDirections.actionOtpFragmentToAuthenticationFragment())

            // Show a message and update the UI
        }

        override fun onCodeSent(
            verificationId: String,
            token: PhoneAuthProvider.ForceResendingToken,
        ) {
            // The SMS verification code has been sent to the provided phone number, we
            // now need to ask the user to enter the code and then construct a credential
            // by combining the code with a verification ID.
            Log.d(TAG, "onCodeSent:$verificationId")

            // Save verification ID and resending token so we can use them later
            storedVerificationId = verificationId
            resendToken = token
            binding.otpEditText.isEnabled = true
            binding.continueButton.visibility = View.VISIBLE
            binding.progressBar.visibility = View.GONE
            binding.textInputLayout.isEnabled = true
            binding.phoneNumberTv.isEnabled = true
            Toast.makeText(requireContext(), "Please Enter OTP", Toast.LENGTH_SHORT).show()
        }
    }

    private fun verifyCode(code: String) {
        val credential = PhoneAuthProvider.getCredential(storedVerificationId, code)
        signInWithPhoneAuthCredential(credential)
    }

    private fun signInWithPhoneAuthCredential(credential: PhoneAuthCredential) {
        auth.signInWithCredential(credential)
            .addOnCompleteListener(requireActivity()) { task ->
                if (task.isSuccessful) {
                    // Sign in success, update UI with the signed-in user's information
                    Log.d(TAG, "signInWithCredential:success")
                    val user = task.result?.user
                    getUserData(user)
                } else {
                    // Sign in failed, display a message and update the UI
                    Log.w(TAG, "signInWithCredential:failure", task.exception)
                    if (task.exception is FirebaseAuthInvalidCredentialsException) {
                        // The verification code entered was invalid
                        Log.e(TAG, "Invalid verification code")
                        Toast.makeText(
                            requireContext(),
                            "Invalid Code! Try Again",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    // Update UI
                    binding.continueButton.visibility = View.VISIBLE
                    binding.otpEditText.isEnabled = true
                    binding.progressBar.visibility = View.GONE
                }
            }
    }
}