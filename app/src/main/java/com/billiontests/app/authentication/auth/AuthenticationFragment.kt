package com.billiontests.app.authentication.auth

import androidx.fragment.app.viewModels
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.billiontests.app.databinding.FragmentAuthenticationBinding
import android.widget.Toast
import androidx.navigation.fragment.findNavController
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth

class AuthenticationFragment : Fragment() {

    private lateinit var binding: FragmentAuthenticationBinding

    companion object {
        fun newInstance() = AuthenticationFragment()
        private const val TAG = "AuthenticationFragment"
    }

    private val viewModel: AuthenticationViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize Firebase Auth
        val user = Firebase.auth.currentUser
        if (user != null) {
            findNavController().navigate(
                AuthenticationFragmentDirections.actionAuthenticationFragmentToSignUpFragment(
                    false
                )
            )
        }
    }


    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {

        binding = FragmentAuthenticationBinding.inflate(inflater)

        binding.continueButton.setOnClickListener {
            val phoneNumber = binding.phoneNumberEditText.text.toString().trim()
            if (phoneNumber.isEmpty()) {
                Toast.makeText(requireContext(), "Please Enter Phone Number", Toast.LENGTH_SHORT)
                    .show()
                return@setOnClickListener
            }

            if (phoneNumber.length < 10) {
                Toast.makeText(
                    requireContext(),
                    "Phone Number Should have 10 digits",
                    Toast.LENGTH_SHORT
                ).show()
                return@setOnClickListener
            }
            goToHome(phoneNumber)
        }

        binding.privacyTv.setOnClickListener {
            findNavController().navigate(
                AuthenticationFragmentDirections.actionAuthenticationFragmentToWebViewFragment2(
                    "https://www.freeprivacypolicy.com/live/5033be46-bb8c-4cac-9fa2-ef4c109302d2"
                )
            )
        }
        return binding.root
    }

    private fun goToHome(phoneNumber: String) {
        findNavController().navigate(
            AuthenticationFragmentDirections.actionAuthenticationFragmentToOtpFragment(
                phoneNumber
            )
        )
    }


}