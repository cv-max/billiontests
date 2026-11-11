package com.billiontests.app.authentication

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.billiontests.app.databinding.FragmentAuthMethodBinding
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase

/**
 * Entry screen that lets the user pick how to authenticate: phone OTP (existing flow)
 * or email/password (new flow). If a user is already authenticated but hasn't finished
 * the profile step, resume straight at SignUp.
 */
class AuthMethodFragment : Fragment() {

    private lateinit var binding: FragmentAuthMethodBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Already authenticated (e.g. account created but profile not completed) -> resume profile.
        if (Firebase.auth.currentUser != null) {
            findNavController().navigate(
                AuthMethodFragmentDirections.actionAuthMethodFragmentToSignUpFragment(false)
            )
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentAuthMethodBinding.inflate(inflater)

        binding.phoneButton.setOnClickListener {
//            findNavController().navigate(
//                AuthMethodFragmentDirections.actionAuthMethodFragmentToAuthenticationFragment()
//            )
            Toast.makeText(requireContext(), "Coming Soon", Toast.LENGTH_SHORT).show()
        }

        binding.emailButton.setOnClickListener {
            findNavController().navigate(
                AuthMethodFragmentDirections.actionAuthMethodFragmentToEmailAuthFragment()
            )
        }

        binding.privacyTv.setOnClickListener {
            findNavController().navigate(
                AuthMethodFragmentDirections.actionAuthMethodFragmentToWebViewFragment(
                    "https://www.freeprivacypolicy.com/live/5033be46-bb8c-4cac-9fa2-ef4c109302d2"
                )
            )
        }

        return binding.root
    }
}
