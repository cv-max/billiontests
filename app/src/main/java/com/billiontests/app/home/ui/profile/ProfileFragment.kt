package com.billiontests.app.home.ui.profile

import com.billiontests.app.MainActivity
import androidx.fragment.app.viewModels
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.billiontests.app.databinding.FragmentProfileBinding
import android.content.Intent
import android.content.SharedPreferences
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity.MODE_PRIVATE
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.RecyclerView
import com.billiontests.app.utils.PreferenceUtils
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import java.util.Locale

class ProfileFragment : Fragment(), ProfileAdapter.OnProfileItemClickListener {

    companion object {
        fun newInstance() = ProfileFragment()
    }

    private lateinit var sharedPreferences: SharedPreferences
    private val viewModel: ProfileViewModel by viewModels()
    private val profileList = listOf(
        "Edit Profile",
        "Write a review",
        "Privacy Policy",
        "Terms Of Use",
        "FAQ",
        "About",
        "Logout",
    )
    private lateinit var recyclerView: RecyclerView
    private lateinit var profileAdapter: ProfileAdapter
    private lateinit var binding: FragmentProfileBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // TODO: Use the ViewModel
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentProfileBinding.inflate(inflater)
        sharedPreferences =
            requireContext().getSharedPreferences(PreferenceUtils.PREFERENCE_NAME, MODE_PRIVATE)
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
        recyclerView = binding.profileRecyclerView

        profileAdapter = ProfileAdapter(profileList, this)
        recyclerView.adapter = profileAdapter

        binding.takeTestNowButton.setOnClickListener {
            findNavController().navigate(ProfileFragmentDirections.actionNavigationProfileToTestGuideFragment2())
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

            2, 3 -> {
                var url = ""
                url = if (position == 2) {
                    "https://www.freeprivacypolicy.com/live/5033be46-bb8c-4cac-9fa2-ef4c109302d2"
                } else {
                    "https://www.freeprivacypolicy.com/live/fcb86250-aac4-4241-9da9-8b1b228cb49f"
                }
                findNavController().navigate(ProfileFragmentDirections.actionNavigationProfileToWebViewFragment(url))
            }

            5 -> {
                findNavController().navigate(ProfileFragmentDirections.actionNavigationProfileToAboutFragment())
            }

            6 -> {
                showLogoutDialog()
            }

            else -> {
                Toast.makeText(requireContext(), "Coming Soon", Toast.LENGTH_SHORT).show()
            }
        }

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