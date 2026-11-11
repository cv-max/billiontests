package com.billiontests.app.authentication.email

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.util.Patterns
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity.MODE_PRIVATE
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.billiontests.app.R
import com.billiontests.app.databinding.FragmentEmailAuthBinding
import com.billiontests.app.home.BottomHomeActivity
import com.billiontests.app.model.User
import com.billiontests.app.utils.PreferenceUtils
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.ktx.Firebase

/**
 * Email + password authentication. A single screen toggles between Login and Sign Up.
 * On Sign Up a Firebase account is created and the user is sent to the profile screen.
 * On Login the existing profile routing (same as the OTP flow) decides Home vs. profile.
 */
class EmailAuthFragment : Fragment() {

    companion object {
        private const val TAG = "EmailAuthFragment"
    }

    private lateinit var binding: FragmentEmailAuthBinding
    private lateinit var auth: FirebaseAuth
    private val db = FirebaseFirestore.getInstance()

    private var isLoginMode = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        auth = Firebase.auth
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentEmailAuthBinding.inflate(inflater)

        applyMode()

        binding.toggleModeTv.setOnClickListener {
            isLoginMode = !isLoginMode
            applyMode()
        }

        binding.forgotPasswordTv.setOnClickListener {
            sendPasswordReset()
        }

        binding.submitButton.setOnClickListener {
            val email = binding.emailEditText.text.toString().trim()
            val password = binding.passwordEditText.text.toString()

            if (email.isEmpty() || !Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                Toast.makeText(requireContext(), "Enter a valid email", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (password.length < 6) {
                Toast.makeText(
                    requireContext(),
                    "Password must be at least 6 characters",
                    Toast.LENGTH_SHORT
                ).show()
                return@setOnClickListener
            }

            if (isLoginMode) login(email, password) else signUp(email, password)
        }

        return binding.root
    }

    private fun applyMode() {
        val title = if (isLoginMode) getString(R.string.login) else getString(R.string.sign_up)
        binding.titleTv.text = title
        binding.submitButton.text = title
        binding.toggleModeTv.text = getString(
            if (isLoginMode) R.string.prompt_create_account else R.string.prompt_have_account
        )
        binding.forgotPasswordTv.visibility = if (isLoginMode) View.VISIBLE else View.GONE
    }

    private fun setLoading(loading: Boolean) {
        binding.progressBar.visibility = if (loading) View.VISIBLE else View.GONE
        binding.submitButton.visibility = if (loading) View.INVISIBLE else View.VISIBLE
        binding.toggleModeTv.isEnabled = !loading
        binding.forgotPasswordTv.isEnabled = !loading
    }

    private fun signUp(email: String, password: String) {
        setLoading(true)
        auth.createUserWithEmailAndPassword(email, password)
            .addOnCompleteListener(requireActivity()) { task ->
                if (!isAdded) return@addOnCompleteListener
                if (task.isSuccessful) {
                    Log.d(TAG, "createUserWithEmail:success")
                    // Hard block: send a verification link and sign the user straight back out.
                    // They can only proceed by verifying and then logging in.
                    val user = auth.currentUser
                    user?.sendEmailVerification()
                        ?.addOnCompleteListener { verifyTask ->
                            if (!isAdded) return@addOnCompleteListener
                            auth.signOut()
                            setLoading(false)
                            if (verifyTask.isSuccessful) {
                                switchToLoginMode()
                                showVerificationSentDialog(email)
                            } else {
                                Log.w(TAG, "sendEmailVerification:failure", verifyTask.exception)
                                Toast.makeText(
                                    requireContext(),
                                    "Account created, but the verification email failed to send. " +
                                        "Log in to resend it.",
                                    Toast.LENGTH_LONG
                                ).show()
                                switchToLoginMode()
                            }
                        } ?: run {
                        auth.signOut()
                        setLoading(false)
                        switchToLoginMode()
                    }
                } else {
                    setLoading(false)
                    Log.w(TAG, "createUserWithEmail:failure", task.exception)
                    Toast.makeText(
                        requireContext(),
                        task.exception?.localizedMessage ?: "Sign up failed. Try again",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
    }

    private fun login(email: String, password: String) {
        setLoading(true)
        auth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener(requireActivity()) { task ->
                if (!isAdded) return@addOnCompleteListener
                if (task.isSuccessful) {
                    Log.d(TAG, "signInWithEmail:success")
                    // Refresh verification state from the server before deciding.
                    val user = auth.currentUser
                    user?.reload()?.addOnCompleteListener {
                        if (!isAdded) return@addOnCompleteListener
                        if (auth.currentUser?.isEmailVerified == true) {
                            routeAfterLogin()
                        } else {
                            // Hard block: not verified -> sign out and prompt to verify/resend.
                            auth.signOut()
                            setLoading(false)
                            showVerifyEmailRequiredDialog(email, password)
                        }
                    } ?: run {
                        setLoading(false)
                        showVerifyEmailRequiredDialog(email, password)
                    }
                } else {
                    setLoading(false)
                    Log.w(TAG, "signInWithEmail:failure", task.exception)
                    Toast.makeText(
                        requireContext(),
                        task.exception?.localizedMessage ?: "Login failed. Check your credentials",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
    }

    private fun switchToLoginMode() {
        isLoginMode = true
        applyMode()
        binding.passwordEditText.text?.clear()
    }

    private fun showVerificationSentDialog(email: String) {
        AlertDialog.Builder(requireContext())
            .setTitle("Verify your email")
            .setMessage("We've sent a verification link to $email. Please verify it, then log in.")
            .setCancelable(false)
            .setPositiveButton("OK") { dialog, _ -> dialog.dismiss() }
            .show()
    }

    private fun showVerifyEmailRequiredDialog(email: String, password: String) {
        AlertDialog.Builder(requireContext())
            .setTitle("Email not verified")
            .setMessage("Please verify your email before logging in. Check your inbox for the link.")
            .setCancelable(true)
            .setPositiveButton("Resend link") { dialog, _ ->
                resendVerificationEmail(email, password)
                dialog.dismiss()
            }
            .setNegativeButton("OK") { dialog, _ -> dialog.dismiss() }
            .show()
    }

    /** Re-authenticates silently with the entered credentials just to resend the link, then signs out. */
    private fun resendVerificationEmail(email: String, password: String) {
        auth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener { task ->
                if (!isAdded) return@addOnCompleteListener
                if (task.isSuccessful) {
                    auth.currentUser?.sendEmailVerification()
                    auth.signOut()
                    Toast.makeText(
                        requireContext(),
                        "Verification email resent to $email",
                        Toast.LENGTH_SHORT
                    ).show()
                } else {
                    Toast.makeText(
                        requireContext(),
                        "Could not resend. Check your credentials and try again.",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
    }

    /**
     * Mirrors the OTP flow's post-auth routing: if the user already has a profile go Home,
     * otherwise send them to the profile screen to finish signing up.
     */
    private fun routeAfterLogin() {
        val sharedPreferences =
            requireContext().getSharedPreferences(PreferenceUtils.PREFERENCE_NAME, MODE_PRIVATE)
        val uid = auth.currentUser?.uid
        if (uid == null) {
            setLoading(false)
            goToSignUp()
            return
        }
        db.collection("users").document(uid).get()
            .addOnSuccessListener { document ->
                if (!isAdded) return@addOnSuccessListener
                setLoading(false)
                val user = document?.toObject(User::class.java)
                if (user != null) {
                    sharedPreferences.edit().putBoolean(PreferenceUtils.IS_SIGNED_UP, true).apply()
                    sharedPreferences.edit().putString(PreferenceUtils.USER_NAME, user.name).apply()
                    sharedPreferences.edit().putString(PreferenceUtils.AGE, user.age.toString())
                        .apply()
                    sharedPreferences.edit().putString(PreferenceUtils.GENDER, user.gender).apply()
                    goToHome()
                } else {
                    goToSignUp()
                }
            }
            .addOnFailureListener { e ->
                if (!isAdded) return@addOnFailureListener
                setLoading(false)
                Log.w(TAG, "Error getting user data", e)
                goToSignUp()
            }
    }

    private fun sendPasswordReset() {
        val email = binding.emailEditText.text.toString().trim()
        if (email.isEmpty() || !Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            Toast.makeText(
                requireContext(),
                "Enter your email to reset password",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        setLoading(true)
        auth.sendPasswordResetEmail(email)
            .addOnCompleteListener { task ->
                if (!isAdded) return@addOnCompleteListener
                setLoading(false)
                val msg = if (task.isSuccessful) "Password reset email sent"
                else task.exception?.localizedMessage ?: "Could not send reset email"
                Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
            }
    }

    private fun goToHome() {
        startActivity(Intent(requireActivity(), BottomHomeActivity::class.java))
        requireActivity().finish()
    }

    private fun goToSignUp() {
        findNavController().navigate(
            EmailAuthFragmentDirections.actionEmailAuthFragmentToSignUpFragment(false)
        )
    }
}
